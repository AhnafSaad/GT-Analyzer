package com.example.data

import android.content.Context
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DiagnosticRepository(
    private val configRepository: ConfigRepository,
    private val upstreamGatewayProvider: UpstreamGatewayProvider = ClientSideUpstreamGatewayEngine()
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun runDiagnostic(
        context: Context,
        mode: DiagnosticMode = DiagnosticMode.QUICK,
        onProgress: (Int) -> Unit = {}
    ): DiagnosticResult = withContext(Dispatchers.IO) {
        // On-device multi-stage differential diagnostic
        runOnDeviceDiagnostic(context, mode, onProgress)
    }

    private fun parseDiagnosticJson(jsonStr: String): DiagnosticResult? {
        return try {
            val json = JSONObject(jsonStr)
            val g1 = json.optString("gateway1", "192.168.0.1")
            val g2 = json.optString("gateway2", "Unknown")
            val g1Latency = json.optDouble("gateway1Latency", 1.0)
            val g2Latency = json.optDouble("gateway2Latency", 0.0)
            val err = if (json.has("error") && !json.isNull("error")) json.getString("error") else null

            val hopsList = mutableListOf<DiagnosticHop>()
            if (json.has("hops")) {
                val hopsArr = json.getJSONArray("hops")
                for (i in 0 until hopsArr.length()) {
                    val hObj = hopsArr.getJSONObject(i)
                    hopsList.add(
                        DiagnosticHop(
                            hop = hObj.optInt("hop", i + 1),
                            ip = hObj.optString("ip", if (i == 0) g1 else "*"),
                            ms = hObj.optDouble("ms", if (i == 0) g1Latency else -1.0)
                        )
                    )
                }
            }

            if (hopsList.isEmpty()) {
                hopsList.add(DiagnosticHop(1, g1, g1Latency))
            }

            DiagnosticResult(
                gateway1 = g1,
                gateway2 = g2,
                gateway2Latency = g2Latency,
                gateway1Latency = g1Latency,
                hops = hopsList,
                diagnosisType = DiagnosisType.HEALTHY,
                diagnosisTitle = "Backend Diagnostic Completed",
                diagnosisSummary = "Telemetry collected from backend agent endpoint.",
                evidenceList = listOf(
                    DiagnosticEvidenceItem("Local Gateway", "$g1 ($g1Latency ms)", true),
                    DiagnosticEvidenceItem("Upstream Gateway", g2, true)
                ),
                error = err,
                isFromBackend = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun runOnDeviceDiagnostic(
        context: Context,
        mode: DiagnosticMode = DiagnosticMode.QUICK,
        onProgress: (Int) -> Unit = {}
    ): DiagnosticResult {
        val packetCount = mode.packetCount
        onProgress(5)

        // Stage 1: Detect local default gateway (Home Wifi Router) and client IP
        val localGwIp = NetworkUtils.getDefaultGateway(context).trim()
        val isLocalGwValid = localGwIp.isNotBlank() && localGwIp != "0.0.0.0"
        val clientIp = NetworkUtils.getConnectedWifiInfo(context).ipAddress
        onProgress(15)

        // Stage 2: Test Home Wifi Router Health with mode packet count
        val localGwStats = if (isLocalGwValid) {
            NetworkUtils.measureHostHealth(localGwIp, label = "Home Wifi Router", count = packetCount, timeoutMs = 800)
        } else {
            ProbeStats(
                host = "Disconnected",
                label = "Home Wifi Router",
                transmitted = packetCount,
                received = 0,
                packetLossPercent = 100.0,
                isReachable = false
            )
        }
        onProgress(30)

        // Smart Skip Logic: If ping to "Home Wifi Router" fails (100% loss/unreachable), halt scan immediately and skip nodes 3, 4, and 5
        val isHomeRouterFailed = !isLocalGwValid || !localGwStats.isReachable || localGwStats.packetLossPercent >= 100.0 || (localGwStats.transmitted > 0 && localGwStats.received == 0)
        if (isHomeRouterFailed) {
            onProgress(100)
            val evidenceList = mutableListOf(
                DiagnosticEvidenceItem(
                    title = "Home Wifi Router Link",
                    detail = "Unreachable (100% packet loss to router ${localGwStats.host})",
                    passed = false
                )
            )
            return DiagnosticResult(
                gateway1 = if (isLocalGwValid) localGwIp else "Not Connected",
                gateway2 = "Unknown",
                gateway1Latency = -1.0,
                gateway2Latency = 0.0,
                localGwStats = localGwStats,
                upstreamGwStats = null,
                upstreamGw2Stats = null,
                hops = emptyList(),
                diagnosisType = DiagnosisType.LOCAL_LAN_ISSUE,
                diagnosisTitle = "Home Wifi Router Issue",
                diagnosisSummary = "আপনার ডিভাইস থেকে হোম রাউটারে সমস্যা। এটি ঠিক করে তারপর আবার ডায়াগনস্টিক দিন।",
                evidenceList = evidenceList,
                error = "Home Wifi Router Unreachable",
                isFromBackend = false,
                isSkippedDueToRouterFailure = true,
                mode = mode
            )
        }

        // Stage 3: Collect Traceroute Path Information (Primary Target)
        val targetPrimary = configRepository.pingTarget.ifBlank { "8.8.8.8" }
        val traceHops = NetworkUtils.executeTraceroute(target = targetPrimary, maxHops = 10)
        onProgress(50)

        // Stage 4: Multi-Method Client-Side Upstream Gateway Discovery & Correlation (Upstream Gateway 1)
        val upstreamDiscoveryRaw = upstreamGatewayProvider.discoverUpstreamGateway(
            context = context,
            localGateway = localGwIp,
            clientIp = clientIp,
            primaryHops = traceHops
        )

        val hop2Ip = traceHops.getOrNull(1)?.ip?.takeIf { it != "*" && it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        val detectedUpstream = upstreamDiscoveryRaw.detectedIp.takeIf { it.isNotBlank() && it != "*" && !it.equals("Unknown", ignoreCase = true) }
            ?: hop2Ip
            ?: traceHops.firstOrNull { it.hop > 1 && it.ip != "*" && it.ip.isNotBlank() && !it.ip.equals("Unknown", ignoreCase = true) }?.ip

        val upstreamDiscovery = if (!detectedUpstream.isNullOrBlank()) {
            upstreamDiscoveryRaw.copy(
                detectedIp = detectedUpstream,
                isConfirmed = true
            )
        } else {
            upstreamDiscoveryRaw
        }

        val upstreamGwDisplay = if (upstreamDiscovery.detectedIp.isNotBlank() && upstreamDiscovery.detectedIp != "*") {
            upstreamDiscovery.detectedIp
        } else {
            "Unknown"
        }

        val upstreamGwStats = if (upstreamGwDisplay != "Unknown") {
            NetworkUtils.measureHostHealth(
                upstreamGwDisplay,
                label = "Upstream Gateway 1",
                count = packetCount,
                timeoutMs = 1500
            )
        } else {
            null
        }
        onProgress(70)

        // Stage 5: Discover & Probe Upstream Gateway 2 (Hop 3 or subsequent gateway hop)
        val hop3Ip = traceHops.getOrNull(2)?.ip?.takeIf {
            it != "*" && it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && it != upstreamGwDisplay && it != localGwIp
        } ?: traceHops.firstOrNull {
            it.hop > 2 && it.ip != "*" && it.ip.isNotBlank() && !it.ip.equals("Unknown", ignoreCase = true) && it.ip != upstreamGwDisplay && it.ip != localGwIp
        }?.ip

        val upstreamGw2Stats = if (!hop3Ip.isNullOrBlank()) {
            NetworkUtils.measureHostHealth(
                hop3Ip,
                label = "Upstream Gateway 2",
                count = packetCount,
                timeoutMs = 1500
            )
        } else {
            null
        }
        onProgress(85)

        // Stage 6: Test Internet targets (Primary & Secondary) with repeated probes
        val primaryStats = NetworkUtils.measureHostHealth(targetPrimary, label = "Internet Primary ($targetPrimary)", count = packetCount, timeoutMs = 1000)
        val targetSecondary = "1.1.1.1"
        val secondaryStats = NetworkUtils.measureHostHealth(targetSecondary, label = "Internet Secondary ($targetSecondary)", count = packetCount, timeoutMs = 1000)
        onProgress(95)

        // Stage 7: Differential Diagnostic Decision Engine
        val evidenceList = mutableListOf<DiagnosticEvidenceItem>()
        val diagnosis = evaluateDiagnosis(
            isLocalGwValid = isLocalGwValid,
            localGwStats = localGwStats,
            upstreamDiscovery = upstreamDiscovery,
            upstreamGwStats = upstreamGwStats,
            primaryTargetStats = primaryStats,
            secondaryTargetStats = secondaryStats,
            traceHops = traceHops,
            evidenceList = evidenceList
        )
        onProgress(100)

        return DiagnosticResult(
            gateway1 = if (isLocalGwValid) localGwIp else "Not Connected",
            gateway2 = upstreamGwDisplay,
            gateway1Latency = if (localGwStats.isReachable) localGwStats.avgMs else -1.0,
            gateway2Latency = upstreamGwStats?.avgMs ?: 0.0,
            localGwStats = localGwStats,
            upstreamGwStats = upstreamGwStats,
            upstreamGw2Stats = upstreamGw2Stats,
            internetTargetStats = primaryStats,
            secondaryTargetStats = secondaryStats,
            hops = traceHops,
            upstreamDiscovery = upstreamDiscovery,
            diagnosisType = diagnosis.type,
            diagnosisTitle = diagnosis.title,
            diagnosisSummary = diagnosis.summary,
            evidenceList = evidenceList,
            error = if (!isLocalGwValid) "Local network interface is down" else null,
            isFromBackend = false,
            isSkippedDueToRouterFailure = false,
            mode = mode
        )
    }

    data class DiagnosisResultDetails(
        val type: DiagnosisType,
        val title: String,
        val summary: String
    )

    private fun evaluateDiagnosis(
        isLocalGwValid: Boolean,
        localGwStats: ProbeStats,
        upstreamDiscovery: UpstreamDiscoveryResult,
        upstreamGwStats: ProbeStats?,
        primaryTargetStats: ProbeStats,
        secondaryTargetStats: ProbeStats,
        traceHops: List<DiagnosticHop>,
        evidenceList: MutableList<DiagnosticEvidenceItem>
    ): DiagnosisResultDetails {
        // Evidence 1: Local Gateway Health
        if (!isLocalGwValid || !localGwStats.isReachable || localGwStats.packetLossPercent >= 100.0) {
            evidenceList.add(
                DiagnosticEvidenceItem(
                    title = "Local Gateway Link (LAN)",
                    detail = "Unreachable (100% loss to default gateway ${localGwStats.host})",
                    passed = false
                )
            )
        } else if (localGwStats.packetLossPercent > 20.0 || localGwStats.avgMs > 60.0) {
            evidenceList.add(
                DiagnosticEvidenceItem(
                    title = "Local Gateway Link (LAN)",
                    detail = "Degraded: ${localGwStats.avgMs} ms avg, ${localGwStats.packetLossPercent}% loss (${localGwStats.received}/${localGwStats.transmitted} replies)",
                    passed = false,
                    isWarning = true
                )
            )
        } else {
            evidenceList.add(
                DiagnosticEvidenceItem(
                    title = "Local Gateway Link (LAN)",
                    detail = "Healthy: ${localGwStats.avgMs} ms avg, 0% loss to ${localGwStats.host} (${localGwStats.received}/${localGwStats.transmitted} replies)",
                    passed = true
                )
            )
        }

        // Evidence 2: Upstream Gateway / Inferred Router
        if (upstreamDiscovery.detectedIp.isNotBlank()) {
            val label = if (upstreamDiscovery.isConfirmed) "Upstream Gateway (Confirmed)" else "Inferred Upstream Router (Candidate)"
            val reachableDetail = if (upstreamGwStats != null && upstreamGwStats.isReachable) {
                "${upstreamGwStats.avgMs} ms avg, ${upstreamGwStats.packetLossPercent}% loss [${upstreamDiscovery.confidence}]"
            } else {
                "Discovered via ${upstreamDiscovery.method} [${upstreamDiscovery.confidence}]"
            }
            evidenceList.add(
                DiagnosticEvidenceItem(
                    title = label,
                    detail = "${upstreamDiscovery.detectedIp} - $reachableDetail",
                    passed = upstreamGwStats?.isReachable ?: true,
                    isWarning = !upstreamDiscovery.isConfirmed
                )
            )
        } else {
            evidenceList.add(
                DiagnosticEvidenceItem(
                    title = "Upstream Gateway (WAN)",
                    detail = "Not directly exposed by router; path analysis probes timed out",
                    passed = true,
                    isWarning = true
                )
            )
        }

        // Evidence 3: Primary Internet Target
        val priPass = primaryTargetStats.isReachable && primaryTargetStats.packetLossPercent < 20.0
        evidenceList.add(
            DiagnosticEvidenceItem(
                title = "Internet Target (Primary: ${primaryTargetStats.host})",
                detail = if (primaryTargetStats.isReachable) {
                    "${primaryTargetStats.avgMs} ms avg, ${primaryTargetStats.packetLossPercent}% loss (${primaryTargetStats.received}/${primaryTargetStats.transmitted} replies)"
                } else {
                    "Request timed out (100% loss)"
                },
                passed = priPass,
                isWarning = primaryTargetStats.isReachable && primaryTargetStats.packetLossPercent > 0.0
            )
        )

        // Evidence 4: Secondary Internet Target
        val secPass = secondaryTargetStats.isReachable && secondaryTargetStats.packetLossPercent < 20.0
        evidenceList.add(
            DiagnosticEvidenceItem(
                title = "Internet Target (Secondary: ${secondaryTargetStats.host})",
                detail = if (secondaryTargetStats.isReachable) {
                    "${secondaryTargetStats.avgMs} ms avg, ${secondaryTargetStats.packetLossPercent}% loss (${secondaryTargetStats.received}/${secondaryTargetStats.transmitted} replies)"
                } else {
                    "Request timed out (100% loss)"
                },
                passed = secPass,
                isWarning = secondaryTargetStats.isReachable && secondaryTargetStats.packetLossPercent > 0.0
            )
        )

        // Evidence 5: Traceroute Transit Path Summary
        val respondingHopsCount = traceHops.count { it.ip != "*" && it.ip.isNotBlank() }
        val timedOutHopsCount = traceHops.count { it.ip == "*" || it.ip.isBlank() }
        evidenceList.add(
            DiagnosticEvidenceItem(
                title = "Traceroute Path Discovery",
                detail = "$respondingHopsCount responding hops mapped. ($timedOutHopsCount hops timed out due to normal ICMP rate-limiting)",
                passed = true
            )
        )

        // Differential Diagnosis Decision Tree
        return when {
            // Case 1: Local LAN/Wi-Fi down or failing
            !isLocalGwValid || !localGwStats.isReachable || localGwStats.packetLossPercent >= 75.0 -> {
                DiagnosisResultDetails(
                    type = DiagnosisType.LOCAL_LAN_ISSUE,
                    title = "Local Wi-Fi / Router LAN Problem",
                    summary = "Your device cannot communicate reliably with the local default gateway (${localGwStats.host}). Check Wi-Fi signal, router power, or IP configuration."
                )
            }
            localGwStats.packetLossPercent > 20.0 || localGwStats.avgMs > 80.0 -> {
                DiagnosisResultDetails(
                    type = DiagnosisType.LOCAL_LAN_ISSUE,
                    title = "Local Wi-Fi Link Degradation",
                    summary = "High latency (${localGwStats.avgMs} ms) or packet loss (${localGwStats.packetLossPercent}%) detected on the local Wi-Fi link before reaching your router."
                )
            }

            // Case 2: Router-to-ISP / Upstream problem
            // Local gateway is 100% reachable with low latency, but both Internet targets are completely unreachable
            !primaryTargetStats.isReachable && !secondaryTargetStats.isReachable -> {
                DiagnosisResultDetails(
                    type = DiagnosisType.ROUTER_TO_ISP_ISSUE,
                    title = "Router-to-ISP / Upstream Uplink Problem",
                    summary = "Local gateway (${localGwStats.host}) is responsive (${localGwStats.avgMs} ms, 0% loss), but the router has no Internet connection. The router WAN/PPPoE link or upstream fiber connection to your ISP is down."
                )
            }

            // Case 3: ISP / Backbone / Internet Path problem
            // Local gateway is healthy, but BOTH Internet targets suffer from packet loss or severe latency
            (primaryTargetStats.packetLossPercent > 15.0 && secondaryTargetStats.packetLossPercent > 15.0) ||
            (primaryTargetStats.avgMs > 150.0 && secondaryTargetStats.avgMs > 150.0) -> {
                DiagnosisResultDetails(
                    type = DiagnosisType.ISP_BACKBONE_ISSUE,
                    title = "ISP / Backbone / Internet Path Problem",
                    summary = "Local Wi-Fi and router are operating normally, but multiple Internet endpoints show high packet loss (${primaryTargetStats.packetLossPercent}%) or latency. The issue is in your ISP upstream network or transit routing."
                )
            }

            // Case 4: Destination / Internet-side problem
            // Local gateway is healthy, secondary target is fine, but primary target fails or has high loss
            (!primaryTargetStats.isReachable && secondaryTargetStats.isReachable) ||
            (primaryTargetStats.packetLossPercent > 25.0 && secondaryTargetStats.packetLossPercent == 0.0) -> {
                DiagnosisResultDetails(
                    type = DiagnosisType.DESTINATION_ISSUE,
                    title = "Destination / Internet-Side Problem",
                    summary = "General Internet access is working (verified via ${secondaryTargetStats.host}), but the target server (${primaryTargetStats.host}) is unreachable or dropping packets."
                )
            }

            // Case 5: Healthy
            else -> {
                DiagnosisResultDetails(
                    type = DiagnosisType.HEALTHY,
                    title = "All Network Segments Operational",
                    summary = "Local gateway and external Internet targets are responding with low latency (${primaryTargetStats.avgMs} ms) and zero packet loss."
                )
            }
        }
    }
}
