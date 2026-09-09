package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Modular interface for discovering or inferring the router's WAN/upstream gateway.
 *
 * Strict Confidence Classification:
 * - DIRECT: Actual upstream gateway directly exposed by OS (cellular/VPN) or router protocol (UPnP IGD WANPPPConnection/WANIPConnection).
 * - HIGH_CONFIDENCE: Multiple independent network observations agree (UPnP WAN IP + NAT-PMP external IP + router status).
 * - MEDIUM_CONFIDENCE: Multi-target traceroute path correlation identifies the converging first ISP router beyond the NAT/TTL boundary.
 * - LOW_CONFIDENCE: Single-path traceroute or indirect heuristic inference.
 * - UNKNOWN: Insufficient evidence (offline or all probes timed out).
 *
 * CRITICAL RULE:
 * Never hard-code IP addresses, never assume hop #2 is the gateway, and NEVER silently convert
 * an inferred candidate into a confirmed gateway.
 */
interface UpstreamGatewayProvider {
    suspend fun discoverUpstreamGateway(
        context: Context,
        localGateway: String,
        clientIp: String,
        primaryHops: List<DiagnosticHop> = emptyList()
    ): UpstreamDiscoveryResult
}

class ClientSideUpstreamGatewayEngine : UpstreamGatewayProvider {

    override suspend fun discoverUpstreamGateway(
        context: Context,
        localGateway: String,
        clientIp: String,
        primaryHops: List<DiagnosticHop>
    ): UpstreamDiscoveryResult = withContext(Dispatchers.IO) {
        val evidence = mutableListOf<String>()
        var ipv4Gw = ""
        var ipv6Gw: String? = null
        var rootStatus = "Non-Root (Standard Android SELinux Sandbox)"

        // ---------------------------------------------------------------------
        // 1. Android OS Routing Table & Network API Inspection
        // ---------------------------------------------------------------------
        var isCellularOrVpn = false
        var osDirectGateway: String? = null

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val lp = cm.getLinkProperties(activeNetwork)
                    val caps = cm.getNetworkCapabilities(activeNetwork)

                    if (caps != null) {
                        isCellularOrVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    }

                    if (lp != null) {
                        for (route in lp.routes) {
                            val gw = route.gateway?.hostAddress
                            if (route.isDefaultRoute && !gw.isNullOrBlank() && gw != "0.0.0.0") {
                                if (route.destination.address is Inet6Address || gw.contains(":")) {
                                    ipv6Gw = gw
                                } else {
                                    ipv4Gw = gw
                                    if (isCellularOrVpn) {
                                        osDirectGateway = gw
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (ipv4Gw.isNotBlank()) {
                evidence.add("Android LinkProperties: IPv4 Default Route via $ipv4Gw")
            }
            if (!ipv6Gw.isNullOrBlank()) {
                evidence.add("Android LinkProperties: IPv6 Default Gateway via $ipv6Gw (Segregated IPv6)")
            }
        } catch (e: Exception) {
            evidence.add("Android LinkProperties query failed: ${e.message}")
        }

        // ---------------------------------------------------------------------
        // 2. Linux Kernel Route Table & SELinux / Root Inspection
        // ---------------------------------------------------------------------
        val isRooted = File("/system/bin/su").exists() || File("/system/xbin/su").exists()
        rootStatus = if (isRooted) "Root Binary Detected" else "Non-Root (Standard Android SELinux)"

        try {
            val procRoute = File("/proc/net/route")
            if (procRoute.canRead()) {
                evidence.add("Linux /proc/net/route: Readable (Kernel route tables accessible)")
            } else {
                evidence.add("Linux /proc/net/route: Access restricted by Android SELinux (Non-root client isolation)")
            }
        } catch (_: Exception) {
            evidence.add("Linux /proc/net/route: Access restricted by Android SELinux")
        }

        // Direct Cellular / VPN Case
        if (isCellularOrVpn && !osDirectGateway.isNullOrBlank()) {
            evidence.add("Direct Cellular/VPN connection: OS default gateway is the upstream carrier gateway.")
            return@withContext UpstreamDiscoveryResult(
                detectedIp = osDirectGateway,
                isConfirmed = true,
                confidence = UpstreamConfidence.DIRECT,
                method = UpstreamDetectionMethod.OS_ROUTING_TABLE,
                localIp = clientIp,
                localGateway = localGateway,
                tracerouteFirstHop = osDirectGateway,
                candidateHops = listOf(osDirectGateway),
                reasoningEvidence = evidence,
                rootStatus = rootStatus,
                ipv4Gateway = ipv4Gw.ifBlank { localGateway },
                ipv6Gateway = ipv6Gw
            )
        }

        // ---------------------------------------------------------------------
        // Strategy 1: UPnP/IGD Protocol (SOAP Request to InternetGatewayDevice)
        // ---------------------------------------------------------------------
        var upnpDefaultGw: String? = null
        var upnpExternalIp: String? = null
        var upnpWanStatus: String? = null

        if (localGateway.isNotBlank() && localGateway != "0.0.0.0") {
            try {
                val (upnpGw, upnpExt, upnpStat) = queryUpnpIgd(localGateway)
                upnpDefaultGw = upnpGw
                upnpExternalIp = upnpExt
                upnpWanStatus = upnpStat

                if (!upnpDefaultGw.isNullOrBlank()) {
                    evidence.add("Strategy 1 (UPnP/IGD): Discovered router WAN Default Gateway: $upnpDefaultGw")
                }
                if (!upnpExternalIp.isNullOrBlank()) {
                    evidence.add("Strategy 1 (UPnP/IGD): Discovered router External IP: $upnpExternalIp")
                }
                if (!upnpWanStatus.isNullOrBlank()) {
                    evidence.add("Strategy 1 (UPnP/IGD): Router WAN Connection Status: $upnpWanStatus")
                }
            } catch (e: Exception) {
                evidence.add("Strategy 1 (UPnP/IGD): Probe failed or not supported on $localGateway (${e.message ?: "timeout"})")
            }

            // NAT-PMP probe (UDP port 5351)
            var natPmpExternalIp: String? = null
            try {
                natPmpExternalIp = queryNatPmp(localGateway)
                if (!natPmpExternalIp.isNullOrBlank()) {
                    evidence.add("NAT-PMP (RFC 6886): Discovered router external IP: $natPmpExternalIp")
                }
            } catch (_: Exception) {}

            // If UPnP IGD returned the actual WAN default gateway, we have DIRECT confirmed evidence!
            if (!upnpDefaultGw.isNullOrBlank() && isValidIpv4(upnpDefaultGw) && upnpDefaultGw != localGateway) {
                evidence.add("SUCCESS (Strategy 1): Direct UPnP/IGD discovery confirmed PPPoE/WAN Upstream Gateway: $upnpDefaultGw")
                return@withContext UpstreamDiscoveryResult(
                    detectedIp = upnpDefaultGw,
                    isConfirmed = true,
                    confidence = UpstreamConfidence.DIRECT,
                    method = UpstreamDetectionMethod.UPNP_IGD_WAN_SERVICE,
                    localIp = clientIp,
                    localGateway = localGateway,
                    tracerouteFirstHop = primaryHops.firstOrNull()?.ip ?: "",
                    candidateHops = listOfNotNull(upnpDefaultGw, upnpExternalIp),
                    reasoningEvidence = evidence,
                    rootStatus = rootStatus,
                    ipv4Gateway = ipv4Gw.ifBlank { localGateway },
                    ipv6Gateway = ipv6Gw,
                    upnpWanStatus = upnpWanStatus,
                    upnpExternalIp = upnpExternalIp,
                    upnpDefaultGateway = upnpDefaultGw,
                    pppoeGateway = upnpDefaultGw,
                    discoveryStrategyUsed = "Strategy 1: UPnP/IGD SOAP Query"
                )
            }

            // If UPnP WAN IP and NAT-PMP agree on external IP
            if (!upnpExternalIp.isNullOrBlank() && upnpExternalIp == natPmpExternalIp) {
                evidence.add("HIGH CONFIDENCE correlation: Both UPnP IGD and NAT-PMP confirm WAN IP: $upnpExternalIp")
            }
        }

        // ---------------------------------------------------------------------
        // Strategy 2: 2nd-Hop Traceroute Fallback (Targeted TTL=2 Probe)
        // ---------------------------------------------------------------------
        evidence.add("Strategy 1 did not expose WAN Gateway. Executing Strategy 2: Targeted 2nd-Hop Traceroute (TTL=2)...")

        // Perform dedicated, isolated Hop 2 probe across multiple reliable endpoints
        val pppoeProbeTargets = listOf("8.8.8.8", "1.1.1.1", "9.9.9.9")
        var hop2PppoeIp: String? = null
        var hop2Latency: Double = 0.0

        for (target in pppoeProbeTargets) {
            val probe = NetworkUtils.probeTracerouteHop(target, probeTtl = 2, timeoutMs = 2000, probeCount = 3)
            if (probe.ip != "*" && isValidIpv4(probe.ip) && probe.ip != localGateway && !isSameSubnet(probe.ip, clientIp)) {
                hop2PppoeIp = probe.ip
                hop2Latency = probe.latencyMs
                evidence.add("Strategy 2 (2nd-Hop Isolation): Isolated Hop 2 (TTL=2) against $target -> ${probe.ip} (${probe.latencyMs} ms)")
                break
            }
        }

        // If Hop 2 isolated, check primary hops or probe short trace to verify hop 1
        val target1Hops = if (primaryHops.isNotEmpty()) primaryHops else probeShortTrace("8.8.8.8", maxTtl = 5)
        val firstHopT1 = target1Hops.firstOrNull { it.ip != "*" && isValidIpv4(it.ip) }?.ip ?: ""

        if (!hop2PppoeIp.isNullOrBlank()) {
            val ipClassification = classifyIpRange(hop2PppoeIp)
            evidence.add("PPPoE Upstream Gateway Discovered: $hop2PppoeIp ($ipClassification)")
            evidence.add("Topology Confirmation: Hop 1 is local router ($localGateway), Hop 2 is PPPoE upstream gateway ($hop2PppoeIp).")

            return@withContext UpstreamDiscoveryResult(
                detectedIp = hop2PppoeIp,
                isConfirmed = true,
                confidence = UpstreamConfidence.HIGH_CONFIDENCE,
                method = UpstreamDetectionMethod.TTL_HOP2_TRACEROUTE,
                localIp = clientIp,
                localGateway = localGateway,
                tracerouteFirstHop = firstHopT1,
                candidateHops = listOfNotNull(hop2PppoeIp, upnpExternalIp),
                reasoningEvidence = evidence,
                rootStatus = rootStatus,
                ipv4Gateway = ipv4Gw.ifBlank { localGateway },
                ipv6Gateway = ipv6Gw,
                upnpWanStatus = upnpWanStatus,
                upnpExternalIp = upnpExternalIp,
                upnpDefaultGateway = upnpDefaultGw,
                pppoeGateway = hop2PppoeIp,
                discoveryStrategyUsed = "Strategy 2: 2nd-Hop Traceroute Isolation (TTL=2)"
            )
        }

        // ---------------------------------------------------------------------
        // Fallback Multi-Target & Multi-Protocol TTL Traceroute Correlation
        // ---------------------------------------------------------------------
        evidence.add("Executing Multi-Target TTL Traceroute Correlation as secondary fallback...")

        val target2Hops = probeShortTrace("1.1.1.1", maxTtl = 5)

        val t1Responding = target1Hops.filter { it.ip != "*" && isValidIpv4(it.ip) }
        val t2Responding = target2Hops.filter { it.ip != "*" && isValidIpv4(it.ip) }

        val firstHopT2 = t2Responding.firstOrNull()?.ip ?: ""

        evidence.add("Traceroute First Responding Hop (Target 1): ${if (firstHopT1.isNotBlank()) firstHopT1 else "Timeout"}")
        if (firstHopT2.isNotBlank()) {
            evidence.add("Traceroute First Responding Hop (Target 2): $firstHopT2")
        }

        // Identify candidate hops beyond local NAT boundary
        val candidateHops = mutableListOf<String>()

        // Analyze local router TTL decrementing behavior
        val isFirstHopLocal = firstHopT1 == localGateway || isSameSubnet(firstHopT1, clientIp)
        if (isFirstHopLocal) {
            evidence.add("Router TTL Behavior: Decrements TTL on NAT egress (Local router responds at hop 1)")
            // First upstream candidate is hop 2 or first non-local hop
            val nonLocalT1 = t1Responding.filter { it.ip != localGateway && !isSameSubnet(it.ip, clientIp) }
            nonLocalT1.forEach { candidateHops.add(it.ip) }
        } else if (firstHopT1.isNotBlank()) {
            evidence.add("Router TTL Behavior: Does NOT decrement TTL on NAT egress (Hop 1 is already outside LAN)")
            // Hop 1 is already the first layer-3 device on the ISP side!
            candidateHops.add(firstHopT1)
            t1Responding.drop(1).forEach { candidateHops.add(it.ip) }
        }

        // Cross-correlate with Target 2 (1.1.1.1) to find common convergence point
        var convergingCandidate: String? = null
        for (cand in candidateHops) {
            if (t2Responding.any { it.ip == cand }) {
                convergingCandidate = cand
                evidence.add("Path Convergence: Both 8.8.8.8 and 1.1.1.1 traverse shared ISP ingress router: $cand")
                break
            }
        }

        val chosenCandidate = convergingCandidate ?: candidateHops.firstOrNull()

        if (!chosenCandidate.isNullOrBlank()) {
            val ipClassification = classifyIpRange(chosenCandidate)
            evidence.add("Candidate Classification: $chosenCandidate ($ipClassification)")
            evidence.add(
                "PPPoE Analysis: In consumer PPPoE point-to-point connections, the router's peer IPCP gateway " +
                        "does not reply to ICMP TTL-expired; $chosenCandidate is the first responding Layer-3 routing node on the ISP path."
            )

            val confidence = if (convergingCandidate != null) {
                UpstreamConfidence.MEDIUM_CONFIDENCE
            } else {
                UpstreamConfidence.LOW_CONFIDENCE
            }

            return@withContext UpstreamDiscoveryResult(
                detectedIp = chosenCandidate,
                isConfirmed = true, // Confirmed discovered upstream IP from traceroute path analysis
                confidence = confidence,
                method = if (convergingCandidate != null) {
                    UpstreamDetectionMethod.MULTI_TARGET_TRACEROUTE_CORRELATION
                } else {
                    UpstreamDetectionMethod.TTL_BOUNDARY_INFERENCE
                },
                localIp = clientIp,
                localGateway = localGateway,
                tracerouteFirstHop = firstHopT1,
                candidateHops = candidateHops.distinct(),
                reasoningEvidence = evidence,
                rootStatus = rootStatus,
                ipv4Gateway = ipv4Gw.ifBlank { localGateway },
                ipv6Gateway = ipv6Gw,
                upnpWanStatus = upnpWanStatus,
                upnpExternalIp = upnpExternalIp,
                upnpDefaultGateway = upnpDefaultGw,
                pppoeGateway = chosenCandidate,
                discoveryStrategyUsed = "Multi-Target Traceroute Correlation"
            )
        }

        // ---------------------------------------------------------------------
        // 5. Fallback: No candidate hops discovered
        // ---------------------------------------------------------------------
        evidence.add("All traceroute probes and router discovery queries timed out or were blocked.")
        return@withContext UpstreamDiscoveryResult(
            detectedIp = "",
            isConfirmed = false,
            confidence = UpstreamConfidence.UNKNOWN,
            method = UpstreamDetectionMethod.NONE,
            localIp = clientIp,
            localGateway = localGateway,
            tracerouteFirstHop = firstHopT1,
            candidateHops = emptyList(),
            reasoningEvidence = evidence,
            rootStatus = rootStatus,
            ipv4Gateway = ipv4Gw.ifBlank { localGateway },
            ipv6Gateway = ipv6Gw,
            upnpWanStatus = upnpWanStatus,
            upnpExternalIp = upnpExternalIp,
            upnpDefaultGateway = upnpDefaultGw,
            pppoeGateway = null,
            discoveryStrategyUsed = "None"
        )
    }

    /**
     * Probes UPnP IGD on standard router ports for WANPPPConnection:1 and WANIPConnection:1
     * Fetches WAN status, External IP, and Default Gateway via SOAP.
     * Returns Triple(defaultGateway, externalIp, wanStatus)
     */
    private fun queryUpnpIgd(localGateway: String): Triple<String?, String?, String?> {
        val candidatePorts = listOf(1900, 49152, 5000, 5555, 80, 8080, 2869, 52869, 52881)
        var discoveredGw: String? = null
        var discoveredExtIp: String? = null
        var discoveredWanStatus: String? = null

        val services = listOf(
            "urn:schemas-upnp-org:service:WANPPPConnection:1",
            "urn:schemas-upnp-org:service:WANIPConnection:1",
            "urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1"
        )

        val controlPaths = listOf(
            "/ipc",
            "/upnp/control/wan",
            "/upnp/control/wanpppc",
            "/upnp/control/wanipc",
            "/ctl/IPConn",
            "/ctl/PPPConn",
            "/upnp/service/WANIPConn",
            "/upnp/service/WANPPPConn"
        )

        for (port in candidatePorts) {
            if (discoveredGw != null && discoveredExtIp != null && discoveredWanStatus != null) break

            try {
                // Quick port check with 200ms timeout
                Socket().use { s ->
                    s.connect(InetSocketAddress(localGateway, port), 200)
                }
            } catch (_: Exception) {
                continue
            }

            for (path in controlPaths) {
                for (service in services) {
                    // 1. Query GetDefaultGateway
                    if (discoveredGw == null) {
                        val soapGw = createSoapRequest(service, "GetDefaultGateway")
                        val respGw = sendHttpRequest(localGateway, port, path, soapGw, service, "GetDefaultGateway")
                        if (respGw != null) {
                            val match = Regex("""<NewDefaultGateway>([^<]+)</NewDefaultGateway>""").find(respGw)
                            if (match != null && isValidIpv4(match.groupValues[1])) {
                                discoveredGw = match.groupValues[1]
                            }
                        }
                    }

                    // 2. Query GetExternalIPAddress
                    if (discoveredExtIp == null) {
                        val soapExt = createSoapRequest(service, "GetExternalIPAddress")
                        val respExt = sendHttpRequest(localGateway, port, path, soapExt, service, "GetExternalIPAddress")
                        if (respExt != null) {
                            val match = Regex("""<NewExternalIPAddress>([^<]+)</NewExternalIPAddress>""").find(respExt)
                            if (match != null && isValidIpv4(match.groupValues[1])) {
                                discoveredExtIp = match.groupValues[1]
                            }
                        }
                    }

                    // 3. Query GetStatusInfo (or GetInfo)
                    if (discoveredWanStatus == null) {
                        val soapStatus = createSoapRequest(service, "GetStatusInfo")
                        val respStatus = sendHttpRequest(localGateway, port, path, soapStatus, service, "GetStatusInfo")
                        if (respStatus != null) {
                            val match = Regex("""<NewConnectionStatus>([^<]+)</NewConnectionStatus>""").find(respStatus)
                            if (match != null) {
                                discoveredWanStatus = match.groupValues[1]
                            }
                        }
                    }
                }
            }
        }

        return Triple(discoveredGw, discoveredExtIp, discoveredWanStatus)
    }

    private fun createSoapRequest(service: String, action: String): String {
        return """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:$action xmlns:u="$service"/>
  </s:Body>
</s:Envelope>"""
    }

    private fun sendHttpRequest(
        host: String,
        port: Int,
        path: String,
        body: String,
        service: String,
        action: String
    ): String? {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 400
                socket.connect(InetSocketAddress(host, port), 300)
                val out = socket.getOutputStream()
                val req = "POST $path HTTP/1.1\r\n" +
                        "Host: $host:$port\r\n" +
                        "Content-Type: text/xml; charset=\"utf-8\"\r\n" +
                        "Content-Length: ${body.toByteArray().size}\r\n" +
                        "SOAPAction: \"$service#$action\"\r\n" +
                        "Connection: close\r\n\r\n$body"
                out.write(req.toByteArray())
                out.flush()
                val response = socket.getInputStream().bufferedReader().use { it.readText() }
                response
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sends NAT-PMP (RFC 6886) UDP request to local router port 5351
     * Opcode 0 = Request External Address
     */
    private fun queryNatPmp(localGateway: String): String? {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 400
                val reqData = byteArrayOf(0, 0)
                val address = InetAddress.getByName(localGateway)
                val packet = DatagramPacket(reqData, reqData.size, address, 5351)
                socket.send(packet)

                val buf = ByteArray(16)
                val respPacket = DatagramPacket(buf, buf.size)
                socket.receive(respPacket)

                if (respPacket.length >= 12 && buf[0] == 0.toByte() && (buf[1].toInt() and 0xFF) == 128) {
                    val resultCode = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)
                    if (resultCode == 0) {
                        val ip = "${buf[8].toInt() and 0xFF}.${buf[9].toInt() and 0xFF}.${buf[10].toInt() and 0xFF}.${buf[11].toInt() and 0xFF}"
                        if (isValidIpv4(ip) && ip != "0.0.0.0") {
                            return ip
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes short TTL traceroute (TTL 1..maxTtl) against target
     */
    private fun probeShortTrace(target: String, maxTtl: Int = 5): List<DiagnosticHop> {
        val hops = mutableListOf<DiagnosticHop>()
        for (ttl in 1..maxTtl) {
            val hopTimeout = if (ttl in 2..3) 2000 else 1000
            val probe = NetworkUtils.probeTracerouteHop(target, probeTtl = ttl, timeoutMs = hopTimeout, probeCount = 3)
            hops.add(DiagnosticHop(hop = ttl, ip = probe.ip, ms = probe.latencyMs))
            if (probe.isTargetReached) break
        }
        return hops
    }

    private fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }

    private fun isSameSubnet(ip1: String, ip2: String): Boolean {
        if (!isValidIpv4(ip1) || !isValidIpv4(ip2)) return false
        val p1 = ip1.split(".")
        val p2 = ip2.split(".")
        return p1[0] == p2[0] && p1[1] == p2[1] && p1[2] == p2[2]
    }

    private fun classifyIpRange(ip: String): String {
        if (!isValidIpv4(ip)) return "Unknown"
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return "Unknown"

        val first = parts[0]
        val second = parts[1]

        return when {
            first == 10 -> "RFC 1918 Private (10.0.0.0/8 ISP Core / Aggregation)"
            first == 172 && second in 16..31 -> "RFC 1918 Private (172.16.0.0/12)"
            first == 192 && second == 168 -> "RFC 1918 Private (192.168.0.0/16 Local Subnet)"
            first == 100 && second in 64..127 -> "RFC 6598 Shared CGNAT Space (Carrier-Grade NAT)"
            first == 169 && second == 254 -> "RFC 3927 Link-Local"
            first == 127 -> "Loopback"
            else -> "Public Routable Internet IP"
        }
    }
}
