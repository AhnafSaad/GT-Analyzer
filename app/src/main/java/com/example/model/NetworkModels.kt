package com.example.model

data class WifiInfoData(
    val ssid: String = "Unknown SSID",
    val bssid: String = "00:00:00:00:00:00",
    val rssiDbm: Int = -60,
    val frequencyMhz: Int = 5240,
    val band: String = "5 GHz",
    val linkSpeedMbps: Int = 300,
    val ipAddress: String = "192.168.1.100",
    val gatewayIp: String = "192.168.1.1",
    val isConnected: Boolean = true
)

data class PingMetrics(
    val currentPingMs: Double = 24.0,
    val jitterMs: Double = 3.5,
    val packetLossPercent: Double = 0.0,
    val pingHistory: List<Double> = listOf(24.0, 26.0, 23.0, 25.0, 24.0),
    val dbmHistory: List<Int> = listOf(-62, -61, -60, -60, -62)
)

data class GlobalThresholds(
    val pingGoodMax: Double = 50.0,
    val pingFairMax: Double = 100.0,
    val jitterGoodMax: Double = 10.0,
    val jitterFairMax: Double = 30.0,
    val packetLossGoodMax: Double = 1.0,
    val packetLossFairMax: Double = 5.0,
    val dbmGoodMin: Int = -65,
    val dbmFairMin: Int = -85,
    val dnsSmoothMax: Double = 80.0,
    val dnsPlayableMax: Double = 200.0,
    val gameSmoothMax: Double = 60.0,
    val gamePlayableMax: Double = 120.0
)

data class GameHostResult(
    val host: String,
    val pingMs: Double,
    val status: String // "green", "yellow", "red"
)

data class GameServer(
    val id: String,
    val name: String,
    val hosts: List<String>,
    val hostResults: List<GameHostResult> = emptyList(),
    val bestPingMs: Double = 0.0,
    val status: String = "green",
    val isExpanded: Boolean = false
)

data class DnsResolver(
    val id: String,
    val name: String,
    val url: String,
    val pingMs: Double = 0.0,
    val status: String = "green",
    val recentPings: List<Double> = emptyList()
)

data class NearbyWifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val band: String,
    val capabilities: String,
    val signalBars: Int // 1 to 4
)

data class DiagnosticHop(
    val hop: Int,
    val ip: String,
    val ms: Double
)

enum class DiagnosisType {
    HEALTHY,
    LOCAL_LAN_ISSUE,
    ROUTER_TO_ISP_ISSUE,
    ISP_BACKBONE_ISSUE,
    DESTINATION_ISSUE,
    DISCONNECTED
}

data class ProbeStats(
    val host: String,
    val label: String = "",
    val transmitted: Int = 0,
    val received: Int = 0,
    val packetLossPercent: Double = 0.0,
    val minMs: Double = 0.0,
    val avgMs: Double = 0.0,
    val maxMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val isReachable: Boolean = false
)

enum class UpstreamConfidence {
    DIRECT,
    HIGH_CONFIDENCE,
    MEDIUM_CONFIDENCE,
    LOW_CONFIDENCE,
    UNKNOWN
}

enum class UpstreamDetectionMethod {
    UPNP_IGD_WAN_SERVICE,
    TTL_HOP2_TRACEROUTE,
    OS_ROUTING_TABLE,
    NAT_PMP_OR_PCP,
    MULTI_TARGET_TRACEROUTE_CORRELATION,
    TTL_BOUNDARY_INFERENCE,
    INDIRECT_HEURISTIC,
    NONE
}

data class UpstreamDiscoveryResult(
    val detectedIp: String = "",
    val isConfirmed: Boolean = false, // true = confirmed actual gateway; false = candidate inferred upstream router
    val confidence: UpstreamConfidence = UpstreamConfidence.UNKNOWN,
    val method: UpstreamDetectionMethod = UpstreamDetectionMethod.NONE,
    val localIp: String = "",
    val localGateway: String = "",
    val tracerouteFirstHop: String = "",
    val candidateHops: List<String> = emptyList(),
    val reasoningEvidence: List<String> = emptyList(),
    val rootStatus: String = "Non-Root (Standard Android SELinux)",
    val ipv4Gateway: String = "",
    val ipv6Gateway: String? = null,
    // Explicit UPnP & 2nd-Hop PPPoE Discovery Metadata
    val upnpWanStatus: String? = null,
    val upnpExternalIp: String? = null,
    val upnpDefaultGateway: String? = null,
    val pppoeGateway: String? = null,
    val discoveryStrategyUsed: String = ""
)

data class DiagnosticEvidenceItem(
    val title: String,
    val detail: String,
    val passed: Boolean,
    val isWarning: Boolean = false
)

data class DiagnosticResult(
    val gateway1: String = "192.168.0.1",
    val gateway2: String = "Unknown",
    val gateway2Latency: Double = 0.0,
    val gateway1Latency: Double = 0.0,
    val localGwStats: ProbeStats = ProbeStats(host = gateway1, label = "Local Gateway"),
    val upstreamGwStats: ProbeStats? = null,
    val internetTargetStats: ProbeStats = ProbeStats(host = "8.8.8.8", label = "Internet (Primary)"),
    val secondaryTargetStats: ProbeStats = ProbeStats(host = "1.1.1.1", label = "Internet (Secondary)"),
    val hops: List<DiagnosticHop> = emptyList(),
    val upstreamDiscovery: UpstreamDiscoveryResult = UpstreamDiscoveryResult(),
    val diagnosisType: DiagnosisType = DiagnosisType.HEALTHY,
    val diagnosisTitle: String = "Normal Connection",
    val diagnosisSummary: String = "All measured network segments are responsive with low latency and 0% packet loss.",
    val evidenceList: List<DiagnosticEvidenceItem> = emptyList(),
    val error: String? = null,
    val isFromBackend: Boolean = false
)

enum class SpeedPhase {
    IDLE, PING, DOWNLOAD, UPLOAD, FINISHED
}

data class SpeedTestState(
    val isRunning: Boolean = false,
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val progress: Float = 0f,
    val currentMbps: Double = 0.0,
    val downloadSpeedMbps: Double = 0.0,
    val uploadSpeedMbps: Double = 0.0,
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0
)
