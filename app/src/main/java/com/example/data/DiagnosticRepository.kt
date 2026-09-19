package com.example.data

import android.content.Context
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    private fun toBengaliDigits(num: Int): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return num.toString().map { if (it in '0'..'9') bnDigits[it - '0'] else it }.joinToString("")
    }

    private fun getUpstreamNodeTitle(index: Int): String {
        return "আপস্ট্রিম গেটওয়ে ${toBengaliDigits(index)}"
    }

    private data class ConcurrentProbesResult(
        val routerStats: ProbeStats,
        val node3Result: Pair<DiagnosticWorkflowNode, ProbeStats?>,
        val node4Result: Pair<DiagnosticWorkflowNode, ProbeStats?>,
        val node5Result: Pair<DiagnosticWorkflowNode, ProbeStats?>,
        val dynamicResults: List<Pair<DiagnosticWorkflowNode, ProbeStats?>>,
        val internetStats: ProbeStats
    )

    private suspend fun probeMiddleNode(
        stepNumber: Int,
        title: String,
        ip: String,
        iconType: String,
        packetCount: Int
    ): Pair<DiagnosticWorkflowNode, ProbeStats?> {
        val isIpValid = ip.isNotBlank() && ip != "Unknown" && ip != "*"
        val stats = if (isIpValid) {
            NetworkUtils.measureHostHealth(ip, label = title, count = packetCount, timeoutMs = 800)
        } else {
            null
        }

        val isBlocked = stats == null || !stats.isReachable || stats.packetLossPercent >= 100.0 || (stats.transmitted > 0 && stats.received == 0)
        val node = if (isBlocked) {
            DiagnosticWorkflowNode(
                stepNumber = stepNumber,
                title = title,
                ip = if (isIpValid) ip else "Unknown",
                latencyText = "Timeout",
                latencyMs = null,
                packetLossPercent = 100.0,
                packetLossText = "100.0%",
                transmitted = packetCount,
                received = 0,
                isReachable = false,
                isSkipped = false,
                isIcmpBlocked = true,
                statusText = "ICMP বন্ধ আছে",
                statusLevel = "amber",
                iconType = iconType
            )
        } else if (stats.packetLossPercent > 3.0) {
            DiagnosticWorkflowNode(
                stepNumber = stepNumber,
                title = title,
                ip = ip,
                latencyText = "${"%.1f".format(stats.avgMs)} ms",
                latencyMs = stats.avgMs,
                minMs = stats.minMs,
                maxMs = stats.maxMs,
                packetLossPercent = stats.packetLossPercent,
                packetLossText = "${"%.1f".format(stats.packetLossPercent)}%",
                transmitted = stats.transmitted,
                received = stats.received,
                isReachable = true,
                isSkipped = false,
                isIcmpBlocked = false,
                statusText = "সমস্যা",
                statusLevel = "red",
                iconType = iconType
            )
        } else {
            DiagnosticWorkflowNode(
                stepNumber = stepNumber,
                title = title,
                ip = ip,
                latencyText = "${"%.1f".format(stats.avgMs)} ms",
                latencyMs = stats.avgMs,
                minMs = stats.minMs,
                maxMs = stats.maxMs,
                packetLossPercent = stats.packetLossPercent,
                packetLossText = "${"%.1f".format(stats.packetLossPercent)}%",
                transmitted = stats.transmitted,
                received = stats.received,
                isReachable = true,
                isSkipped = false,
                isIcmpBlocked = false,
                statusText = "স্বাভাবিক",
                statusLevel = "green",
                iconType = iconType
            )
        }
        return Pair(node, stats)
    }

    private suspend fun runOnDeviceDiagnostic(
        context: Context,
        mode: DiagnosticMode = DiagnosticMode.QUICK,
        onProgress: (Int) -> Unit = {}
    ): DiagnosticResult {
        // Dynamic Packet Count: exactly 5 packets for Quick Diagnostic, 20 packets for Full Diagnostic
        val packetCount = if (mode == DiagnosticMode.FULL) 20 else 5
        onProgress(5)

        // Stage 1: Detect client device IP & local default gateway
        val clientIp = NetworkUtils.getConnectedWifiInfo(context).ipAddress
        val localGwIp = NetworkUtils.getDefaultGateway(context).trim()
        val isLocalGwValid = localGwIp.isNotBlank() && localGwIp != "0.0.0.0"

        // Node 1: আপনার ডিভাইস (Device) - always healthy
        val nodeDevice = DiagnosticWorkflowNode(
            stepNumber = 1,
            title = "আপনার ডিভাইস",
            ip = clientIp.ifBlank { "127.0.0.1" },
            latencyText = "0.0 ms",
            latencyMs = 0.0,
            minMs = 0.0,
            maxMs = 0.0,
            packetLossPercent = 0.0,
            packetLossText = "0.0%",
            transmitted = packetCount,
            received = packetCount,
            isReachable = true,
            isSkipped = false,
            isIcmpBlocked = false,
            statusText = "স্বাভাবিক",
            statusLevel = "green",
            iconType = "device"
        )
        onProgress(10)

        // Phase 1 (Discovery): Rapid sequential traceroute (1 packet per TTL) to discover candidate IPs up to 8.8.8.8
        val maxHops = if (mode == DiagnosticMode.FULL) 12 else 6
        val traceHops = NetworkUtils.executeTraceroute(
            target = "8.8.8.8",
            maxHops = maxHops,
            probeCount = 1,
            fastDiscovery = true
        )
        val upstreamDiscoveryRaw = upstreamGatewayProvider.discoverUpstreamGateway(
            context = context,
            localGateway = localGwIp,
            clientIp = clientIp,
            primaryHops = traceHops
        )
        onProgress(25)

        // Extract intermediate hop candidates (excluding local router, client, localhost, 0.0.0.0, and 8.8.8.8)
        val intermediateHops = traceHops.filter { hop ->
            val ip = hop.ip.trim()
            ip != localGwIp && ip != clientIp && ip != "127.0.0.1" && ip != "0.0.0.0" && ip != "8.8.8.8"
        }

        // Candidate for Node 3: পরবর্তি ডিভাইস (Next Device)
        val node3Ip = intermediateHops.getOrNull(0)?.ip?.takeIf { it != "*" && it.isNotBlank() }
            ?: upstreamDiscoveryRaw.pppoeGateway?.takeIf { it.isNotBlank() && it != localGwIp }
            ?: upstreamDiscoveryRaw.detectedIp.takeIf { it.isNotBlank() && it != localGwIp && it != "*" }
            ?: "Unknown"

        // Candidate for Node 4: আপস্ট্রিম গেটওয়ে ১ (Upstream Gateway 1)
        val node4Ip = intermediateHops.getOrNull(1)?.ip?.takeIf { it != "*" && it.isNotBlank() && it != node3Ip }
            ?: upstreamDiscoveryRaw.detectedIp.takeIf { it.isNotBlank() && it != localGwIp && it != node3Ip && it != "*" }
            ?: "Unknown"

        // Candidate for Node 5: আপস্ট্রিম গেটওয়ে ২ (Upstream Gateway 2)
        val node5Ip = intermediateHops.getOrNull(2)?.ip?.takeIf { it != "*" && it.isNotBlank() && it != node3Ip && it != node4Ip }
            ?: "Unknown"

        // Dynamic extra hops candidates for FULL Diagnostic mode
        val dynamicHopCandidates = mutableListOf<Triple<Int, String, String>>() // (stepNumber, title, ip)
        if (mode == DiagnosticMode.FULL && intermediateHops.size > 3) {
            var dynIndex = 3
            val usedIps = mutableSetOf(localGwIp, clientIp, node3Ip, node4Ip, node5Ip, "8.8.8.8")
            for (i in 3 until intermediateHops.size) {
                val hopIp = intermediateHops[i].ip.trim()
                if (hopIp !in usedIps) {
                    usedIps.add(hopIp)
                    val stepNum = 6 + (dynIndex - 3)
                    val title = getUpstreamNodeTitle(dynIndex)
                    dynamicHopCandidates.add(Triple(stepNum, title, hopIp))
                    dynIndex++
                }
            }
        }

        // Phase 2 (Concurrent Heavy Ping): Launch heavy pings simultaneously across all nodes using Coroutines async & awaitAll()
        val completedProbes = AtomicInteger(0)
        val totalProbes = 5 + dynamicHopCandidates.size

        var isRouterHalted = false

        val concurrentResult = coroutineScope {
            val deferredRouter = async(Dispatchers.IO) {
                val stats = if (isLocalGwValid) {
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
                val done = completedProbes.incrementAndGet()
                onProgress(25 + (done * 65 / totalProbes))
                stats
            }

            val deferredNode3 = async(Dispatchers.IO) {
                val res = probeMiddleNode(3, "পরবর্তি ডিভাইস", node3Ip, "device_next", packetCount)
                val done = completedProbes.incrementAndGet()
                onProgress(25 + (done * 65 / totalProbes))
                res
            }

            val deferredNode4 = async(Dispatchers.IO) {
                val res = probeMiddleNode(4, "আপস্ট্রিম গেটওয়ে ১", node4Ip, "gateway", packetCount)
                val done = completedProbes.incrementAndGet()
                onProgress(25 + (done * 65 / totalProbes))
                res
            }

            val deferredNode5 = async(Dispatchers.IO) {
                val res = probeMiddleNode(5, "আপস্ট্রিম গেটওয়ে ২", node5Ip, "gateway", packetCount)
                val done = completedProbes.incrementAndGet()
                onProgress(25 + (done * 65 / totalProbes))
                res
            }

            val deferredDynamics = dynamicHopCandidates.map { (stepNum, title, ip) ->
                async(Dispatchers.IO) {
                    val res = probeMiddleNode(stepNum, title, ip, "gateway", packetCount)
                    val done = completedProbes.incrementAndGet()
                    onProgress(25 + (done * 65 / totalProbes))
                    res
                }
            }

            val deferredInternet = async(Dispatchers.IO) {
                val stats = NetworkUtils.measureHostHealth("8.8.8.8", label = "Internet (8.8.8.8)", count = packetCount, timeoutMs = 1000)
                val done = completedProbes.incrementAndGet()
                onProgress(25 + (done * 65 / totalProbes))
                stats
            }

            // Await router probe first to immediately detect critical router failure
            val routerRes = deferredRouter.await()

            val isFailed = !isLocalGwValid || !routerRes.isReachable || routerRes.packetLossPercent >= 100.0 || (routerRes.transmitted > 0 && routerRes.received == 0)
            if (isFailed) {
                // Instantly force progress to 100% and cancel all other running probe coroutines
                onProgress(100)
                isRouterHalted = true
                deferredNode3.cancel()
                deferredNode4.cancel()
                deferredNode5.cancel()
                deferredDynamics.forEach { it.cancel() }
                deferredInternet.cancel()

                val dummyMiddle = DiagnosticWorkflowNode(
                    stepNumber = 3,
                    title = "পরবর্তি ডিভাইস",
                    ip = "N/A",
                    latencyText = "N/A",
                    packetLossPercent = 0.0,
                    packetLossText = "N/A",
                    transmitted = packetCount,
                    received = 0,
                    statusText = "N/A",
                    statusLevel = "gray",
                    isSkipped = true,
                    iconType = "device_next"
                ) to ProbeStats(host = "N/A", label = "পরবর্তি ডিভাইস", transmitted = packetCount, received = 0, packetLossPercent = 0.0, isReachable = false)

                ConcurrentProbesResult(
                    routerStats = routerRes,
                    node3Result = dummyMiddle,
                    node4Result = dummyMiddle,
                    node5Result = dummyMiddle,
                    dynamicResults = emptyList(),
                    internetStats = ProbeStats(host = "8.8.8.8", label = "Internet", transmitted = packetCount, received = 0, packetLossPercent = 100.0, isReachable = false)
                )
            } else {
                val n3Res = deferredNode3.await()
                val n4Res = deferredNode4.await()
                val n5Res = deferredNode5.await()
                val dynRes = deferredDynamics.awaitAll()
                val netRes = deferredInternet.await()

                ConcurrentProbesResult(
                    routerStats = routerRes,
                    node3Result = n3Res,
                    node4Result = n4Res,
                    node5Result = n5Res,
                    dynamicResults = dynRes,
                    internetStats = netRes
                )
            }
        }

        val finalLocalGwStats = concurrentResult.routerStats
        val localGwStats = finalLocalGwStats
        val (node3, node3Stats) = concurrentResult.node3Result
        val (node4, node4Stats) = concurrentResult.node4Result
        val (node5, node5Stats) = concurrentResult.node5Result
        val dynamicNodes = concurrentResult.dynamicResults.map { it.first }
        val internetStats = concurrentResult.internetStats

        // Rule 5: Home Router Failure [CRITICAL HALT]
        val isHomeRouterFailed = isRouterHalted || !isLocalGwValid || !finalLocalGwStats.isReachable || finalLocalGwStats.packetLossPercent >= 100.0 || (finalLocalGwStats.transmitted > 0 && finalLocalGwStats.received == 0)
        if (isHomeRouterFailed) {
            onProgress(100)
            val nodeRouterFailed = DiagnosticWorkflowNode(
                stepNumber = 2,
                title = "হোম ওয়াইফাই রাউটার",
                ip = if (isLocalGwValid) localGwIp else "Not Connected",
                latencyText = "Timeout",
                latencyMs = null,
                packetLossPercent = 100.0,
                packetLossText = "100.0%",
                transmitted = packetCount,
                received = 0,
                isReachable = false,
                isSkipped = false,
                isIcmpBlocked = false,
                statusText = "সমস্যা",
                statusLevel = "red",
                iconType = "router"
            )
            val node3Skipped = DiagnosticWorkflowNode(
                stepNumber = 3,
                title = "পরবর্তি ডিভাইস",
                ip = "N/A",
                latencyText = "N/A",
                packetLossPercent = 0.0,
                packetLossText = "N/A",
                transmitted = packetCount,
                received = 0,
                statusText = "N/A",
                statusLevel = "gray",
                isSkipped = true,
                iconType = "device_next"
            )
            val node4Skipped = DiagnosticWorkflowNode(
                stepNumber = 4,
                title = "আপস্ট্রিম গেটওয়ে ১",
                ip = "N/A",
                latencyText = "N/A",
                packetLossPercent = 0.0,
                packetLossText = "N/A",
                transmitted = packetCount,
                received = 0,
                statusText = "N/A",
                statusLevel = "gray",
                isSkipped = true,
                iconType = "gateway"
            )
            val node5Skipped = DiagnosticWorkflowNode(
                stepNumber = 5,
                title = "আপস্ট্রিম গেটওয়ে ২",
                ip = "N/A",
                latencyText = "N/A",
                packetLossPercent = 0.0,
                packetLossText = "N/A",
                transmitted = packetCount,
                received = 0,
                statusText = "N/A",
                statusLevel = "gray",
                isSkipped = true,
                iconType = "gateway"
            )
            val nodeInternetSkipped = DiagnosticWorkflowNode(
                stepNumber = 6,
                title = "ইন্টারনেট",
                ip = "8.8.8.8",
                latencyText = "N/A",
                packetLossPercent = 0.0,
                packetLossText = "N/A",
                transmitted = packetCount,
                received = 0,
                statusText = "N/A",
                statusLevel = "gray",
                isSkipped = true,
                iconType = "internet"
            )

            val haltedNodes = listOf(nodeDevice, nodeRouterFailed, node3Skipped, node4Skipped, node5Skipped, nodeInternetSkipped)

            return DiagnosticResult(
                gateway1 = if (isLocalGwValid) localGwIp else "Not Connected",
                gateway2 = "Unknown",
                gateway1Latency = -1.0,
                gateway2Latency = 0.0,
                localGwStats = finalLocalGwStats,
                upstreamGwStats = null,
                upstreamGw2Stats = null,
                internetTargetStats = ProbeStats(host = "8.8.8.8", label = "Internet", transmitted = packetCount, received = 0, packetLossPercent = 100.0, isReachable = false),
                secondaryTargetStats = ProbeStats(host = "1.1.1.1", label = "Internet Secondary", transmitted = packetCount, received = 0, packetLossPercent = 100.0, isReachable = false),
                hops = emptyList(),
                nodes = haltedNodes,
                diagnosisType = DiagnosisType.LOCAL_LAN_ISSUE,
                diagnosisTitle = "হোম ওয়াইফাই রাউটার সমস্যা",
                diagnosisSummary = "আপনার হোম ওয়াইফাই রাউটারে সমস্যা এটা ঠিক করে পুনরায় ডায়াগোন্সটিক চালান।",
                evidenceList = listOf(
                    DiagnosticEvidenceItem(
                        title = "Home Wifi Router Link",
                        detail = "Unreachable (100% packet loss to router ${finalLocalGwStats.host})",
                        passed = false
                    )
                ),
                error = "Home Wifi Router Unreachable",
                isFromBackend = false,
                isSkippedDueToRouterFailure = true,
                mode = mode
            )
        }

        // Node 2 passed
        val nodeRouterSuccess = DiagnosticWorkflowNode(
            stepNumber = 2,
            title = "হোম ওয়াইফাই রাউটার",
            ip = localGwIp,
            latencyText = "${"%.1f".format(localGwStats.avgMs)} ms",
            latencyMs = localGwStats.avgMs,
            minMs = localGwStats.minMs,
            maxMs = localGwStats.maxMs,
            packetLossPercent = localGwStats.packetLossPercent,
            packetLossText = "${"%.1f".format(localGwStats.packetLossPercent)}%",
            transmitted = localGwStats.transmitted,
            received = localGwStats.received,
            isReachable = true,
            isSkipped = false,
            isIcmpBlocked = false,
            statusText = "স্বাভাবিক",
            statusLevel = "green",
            iconType = "router"
        )

        // Final Node: ইন্টারনেট (Internet) - strictly 8.8.8.8
        val finalStepNumber = if (mode == DiagnosticMode.FULL) 6 + dynamicNodes.size else 6
        val isInternetSuccess = internetStats.isReachable && internetStats.packetLossPercent < 100.0 && internetStats.received > 0

        val nodeInternet: DiagnosticWorkflowNode
        val diagnosisTitle: String
        val diagnosisSummary: String
        val diagnosisType: DiagnosisType

        val middleHopsList = mutableListOf(node3, node4, node5)
        middleHopsList.addAll(dynamicNodes)
        val firstFailingMiddleHop = middleHopsList.firstOrNull { it.packetLossPercent > 3.0 && it.packetLossPercent < 100.0 }

        if (isInternetSuccess) {
            // Rule 6: If the Final Node (Internet) succeeds despite middle failures, show Green/Success:
            // "আপনার ইন্টারনেট সংযোগ সম্পূর্ণ স্বাভাবিক আছে।"
            nodeInternet = DiagnosticWorkflowNode(
                stepNumber = finalStepNumber,
                title = "ইন্টারনেট",
                ip = "8.8.8.8",
                latencyText = "${"%.1f".format(internetStats.avgMs)} ms",
                latencyMs = internetStats.avgMs,
                minMs = internetStats.minMs,
                maxMs = internetStats.maxMs,
                packetLossPercent = internetStats.packetLossPercent,
                packetLossText = "${"%.1f".format(internetStats.packetLossPercent)}%",
                transmitted = internetStats.transmitted,
                received = internetStats.received,
                isReachable = true,
                isSkipped = false,
                isIcmpBlocked = false,
                statusText = "স্বাভাবিক",
                statusLevel = "green",
                iconType = "internet"
            )
            if (firstFailingMiddleHop != null) {
                diagnosisTitle = if (firstFailingMiddleHop.stepNumber == 3 || firstFailingMiddleHop.title.contains("পরবর্তি")) {
                    "পরবর্তী ডিভাইসে সমস্যা"
                } else {
                    "${firstFailingMiddleHop.title} এ সমস্যা"
                }
                diagnosisSummary = if (firstFailingMiddleHop.stepNumber == 3 || firstFailingMiddleHop.title.contains("পরবর্তি")) {
                    "আপনার হোম ওয়াইফাই রাউটারের পরের ডিভাইসে সমস্যা।"
                } else {
                    "${firstFailingMiddleHop.title} এ সমস্যা।"
                }
                diagnosisType = DiagnosisType.ROUTER_TO_ISP_ISSUE
            } else {
                diagnosisTitle = "ইন্টারনেট সংযোগ স্বাভাবিক"
                diagnosisSummary = "আপনার ইন্টারনেট সংযোগ সম্পূর্ণ স্বাভাবিক আছে।"
                diagnosisType = DiagnosisType.HEALTHY
            }
        } else {
            // Rule 7: Final Internet Failure (Node 8.8.8.8) - show Red/Error:
            // "আপনার ইন্টারনেট কানেকশনে সমস্যা , ISP প্রোভাইডরের সাথে যোগাযোগ করুন ।"
            nodeInternet = DiagnosticWorkflowNode(
                stepNumber = finalStepNumber,
                title = "ইন্টারনেট",
                ip = "8.8.8.8",
                latencyText = "Timeout",
                latencyMs = null,
                packetLossPercent = 100.0,
                packetLossText = "100.0%",
                transmitted = packetCount,
                received = 0,
                isReachable = false,
                isSkipped = false,
                isIcmpBlocked = false,
                statusText = "সমস্যা",
                statusLevel = "red",
                iconType = "internet"
            )
            if (firstFailingMiddleHop != null) {
                diagnosisTitle = if (firstFailingMiddleHop.stepNumber == 3 || firstFailingMiddleHop.title.contains("পরবর্তি")) {
                    "পরবর্তী ডিভাইসে সমস্যা"
                } else {
                    "${firstFailingMiddleHop.title} এ সমস্যা"
                }
                diagnosisSummary = if (firstFailingMiddleHop.stepNumber == 3 || firstFailingMiddleHop.title.contains("পরবর্তি")) {
                    "আপনার হোম ওয়াইফাই রাউটারের পরের ডিভাইসে সমস্যা।"
                } else {
                    "${firstFailingMiddleHop.title} এ সমস্যা।"
                }
                diagnosisType = DiagnosisType.ROUTER_TO_ISP_ISSUE
            } else {
                diagnosisTitle = "ইন্টারনেট কানেকশনে সমস্যা"
                diagnosisSummary = "আপনার ইন্টারনেট কানেকশনে সমস্যা , ISP প্রোভাইডরের সাথে যোগাযোগ করুন ।"
                diagnosisType = DiagnosisType.DESTINATION_ISSUE
            }
        }
        onProgress(95)

        val allWorkflowNodes = mutableListOf(nodeDevice, nodeRouterSuccess, node3, node4, node5)
        allWorkflowNodes.addAll(dynamicNodes)
        allWorkflowNodes.add(nodeInternet)

        val evidenceList = mutableListOf<DiagnosticEvidenceItem>()
        evidenceList.add(
            DiagnosticEvidenceItem(
                title = "হোম ওয়াইফাই রাউটার",
                detail = "${nodeRouterSuccess.latencyText}, ${nodeRouterSuccess.packetLossText} loss",
                passed = true
            )
        )
        evidenceList.add(
            DiagnosticEvidenceItem(
                title = "ইন্টারনেট (8.8.8.8)",
                detail = if (isInternetSuccess) "${nodeInternet.latencyText}, ${nodeInternet.packetLossText} loss" else "Timeout / 100% loss",
                passed = isInternetSuccess
            )
        )

        val secondaryStats = NetworkUtils.measureHostHealth("1.1.1.1", label = "Internet Secondary (1.1.1.1)", count = packetCount, timeoutMs = 800)
        onProgress(100)

        return DiagnosticResult(
            gateway1 = localGwIp,
            gateway2 = node4Ip,
            gateway1Latency = localGwStats.avgMs,
            gateway2Latency = node4Stats?.avgMs ?: 0.0,
            localGwStats = localGwStats,
            upstreamGwStats = node4Stats,
            upstreamGw2Stats = node5Stats,
            internetTargetStats = internetStats,
            secondaryTargetStats = secondaryStats,
            hops = traceHops,
            nodes = allWorkflowNodes,
            upstreamDiscovery = upstreamDiscoveryRaw,
            diagnosisType = diagnosisType,
            diagnosisTitle = diagnosisTitle,
            diagnosisSummary = diagnosisSummary,
            evidenceList = evidenceList,
            error = if (!isInternetSuccess) "Internet connection failure" else null,
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
