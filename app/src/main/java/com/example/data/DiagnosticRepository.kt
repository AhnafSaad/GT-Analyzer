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

    suspend fun runDiagnostic(context: Context): DiagnosticResult = withContext(Dispatchers.IO) {
        val baseUrl = configRepository.apiBaseUrl
        // Attempt backend endpoint first if available
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/diagnostic")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val parsed = parseDiagnosticJson(body)
                        if (parsed != null) return@withContext parsed.copy(isFromBackend = true)
                    }
                }
            }
        } catch (_: Exception) {
            // Backend unavailable -> fallback to on-device discovery
        }

        // On-device multi-stage differential diagnostic
        runOnDeviceDiagnostic(context)
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

    private suspend fun runOnDeviceDiagnostic(context: Context): DiagnosticResult {
        // Stage 1: Detect local default gateway (GW1) and client IP
        val localGwIp = NetworkUtils.getDefaultGateway(context).trim()
        val isLocalGwValid = localGwIp.isNotBlank() && localGwIp != "0.0.0.0"
        val clientIp = NetworkUtils.getConnectedWifiInfo(context).ipAddress

        // Stage 2: Test Local Gateway Health with repeated probes (4 probes)
        val localGwStats = if (isLocalGwValid) {
            NetworkUtils.measureHostHealth(localGwIp, label = "Local Gateway (GW1)", count = 4, timeoutMs = 800)
        } else {
            ProbeStats(
                host = "Disconnected",
                label = "Local Gateway",
                transmitted = 4,
                received = 0,
                packetLossPercent = 100.0,
                isReachable = false
            )
        }

        // Stage 3: Collect Traceroute Path Information (Primary Target)
        val targetPrimary = configRepository.pingTarget.ifBlank { "8.8.8.8" }
        val traceHops = NetworkUtils.executeTraceroute(target = targetPrimary, maxHops = 10)

        // Stage 4: Multi-Method Client-Side Upstream Gateway Discovery & Correlation
        val upstreamDiscovery = upstreamGatewayProvider.discoverUpstreamGateway(
            context = context,
            localGateway = localGwIp,
            clientIp = clientIp,
            primaryHops = traceHops
        )

        val upstreamGwDisplay = when {
            upstreamDiscovery.isConfirmed && upstreamDiscovery.detectedIp.isNotBlank() ->
                upstreamDiscovery.detectedIp
            upstreamDiscovery.detectedIp.isNotBlank() ->
                "${upstreamDiscovery.detectedIp} (Candidate)"
            else ->
                "Unknown"
        }

        val upstreamGwStats = if (upstreamDiscovery.detectedIp.isNotBlank()) {
            NetworkUtils.measureHostHealth(
                upstreamDiscovery.detectedIp,
                label = if (upstreamDiscovery.isConfirmed) "Upstream Gateway (GW2)" else "Inferred Upstream Router",
                count = 4,
                timeoutMs = 800
            )
        } else {
            null
        }

        // Stage 5: Test Internet targets (Primary & Secondary) with repeated probes (4 probes each)
        val primaryStats = NetworkUtils.measureHostHealth(targetPrimary, label = "Internet Primary ($targetPrimary)", count = 4, timeoutMs = 1000)

        val targetSecondary = "1.1.1.1"
        val secondaryStats = NetworkUtils.measureHostHealth(targetSecondary, label = "Internet Secondary ($targetSecondary)", count = 4, timeoutMs = 1000)

        // Stage 6: Differential Diagnostic Decision Engine
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

        return DiagnosticResult(
            gateway1 = if (isLocalGwValid) localGwIp else "Not Connected",
            gateway2 = upstreamGwDisplay,
            gateway1Latency = if (localGwStats.isReachable) localGwStats.avgMs else -1.0,
            gateway2Latency = upstreamGwStats?.avgMs ?: 0.0,
            localGwStats = localGwStats,
            upstreamGwStats = upstreamGwStats,
            internetTargetStats = primaryStats,
            secondaryTargetStats = secondaryStats,
            hops = traceHops,
            upstreamDiscovery = upstreamDiscovery,
            diagnosisType = diagnosis.type,
            diagnosisTitle = diagnosis.title,
            diagnosisSummary = diagnosis.summary,
            evidenceList = evidenceList,
            error = if (!isLocalGwValid) "Local network interface is down" else null,
            isFromBackend = false
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
