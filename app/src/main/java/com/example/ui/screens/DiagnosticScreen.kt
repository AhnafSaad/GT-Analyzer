package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.model.DiagnosisType
import com.example.model.DiagnosticEvidenceItem
import com.example.model.DiagnosticHop
import com.example.model.DiagnosticResult
import com.example.model.UpstreamConfidence
import com.example.model.UpstreamDetectionMethod
import com.example.model.UpstreamDiscoveryResult
import com.example.ui.components.CardContainer
import com.example.ui.components.PrimaryButton
import com.example.ui.components.StatusTag
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import kotlin.math.max

private enum class TopologyNodeKey {
    CLIENT,
    GATEWAY_1,
    GATEWAY_2,
    INTERNET
}

@Composable
fun DiagnosticScreen(
    result: DiagnosticResult?,
    isLoading: Boolean,
    deviceIp: String,
    backendUrl: String,
    language: AppLanguage,
    onRunDiagnostic: () -> Unit,
    onOpenServerConfig: () -> Unit
) {
    var selectedNode by remember { mutableStateOf(TopologyNodeKey.GATEWAY_1) }
    var isTtlGuideExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        contentPadding = PaddingValues(top = AppSpacing.md, bottom = 100.dp)
    ) {
        if (result != null) {
            // 1. Infographic Header & Re-run Action
            item {
                InfographicHeaderCard(
                    isLoading = isLoading,
                    language = language,
                    onRunDiagnostic = onRunDiagnostic
                )
            }

            // Backend Connectivity Notice
            item {
                BackendNoticePill(
                    isFromBackend = result.isFromBackend,
                    backendUrl = backendUrl,
                    language = language,
                    onOpenServerConfig = onOpenServerConfig
                )
            }

            // 2. Differential Diagnostic Verdict Card
            item {
                DifferentialDiagnosisCard(
                    result = result,
                    language = language
                )
            }

            // 3. Evidence & Multi-Probe Measurements Card
            item {
                DiagnosticEvidenceCard(
                    result = result,
                    language = language
                )
            }

            // 4. Upstream Gateway Inspection & Debug Card
            item {
                UpstreamGatewayDebugCard(
                    result = result,
                    deviceIp = deviceIp,
                    language = language
                )
            }

            // 5. Route Health Score Gauge Card
            item {
                val g1 = result.gateway1Latency
                val g1Loss = result.localGwStats.packetLossPercent
                val targetLat = result.internetTargetStats.avgMs
                val targetLoss = result.internetTargetStats.packetLossPercent

                val healthScore = when (result.diagnosisType) {
                    DiagnosisType.HEALTHY -> {
                        val penalty = (max(0.0, g1 - 3.0) * 1.5) + (max(0.0, targetLat - 40.0) * 0.5)
                        (98 - penalty.toInt()).coerceIn(85, 99)
                    }
                    DiagnosisType.LOCAL_LAN_ISSUE -> {
                        (65 - (g1Loss * 0.4).toInt() - (max(0.0, g1 - 10.0) * 0.5).toInt()).coerceIn(20, 65)
                    }
                    DiagnosisType.ROUTER_TO_ISP_ISSUE -> 30
                    DiagnosisType.ISP_BACKBONE_ISSUE -> {
                        (75 - (targetLoss * 0.5).toInt() - (max(0.0, targetLat - 80.0) * 0.3).toInt()).coerceIn(40, 75)
                    }
                    DiagnosisType.DESTINATION_ISSUE -> 70
                    DiagnosisType.DISCONNECTED -> 10
                }

                RouteHealthInfographicCard(
                    score = healthScore,
                    gateway1Latency = g1,
                    gateway2Latency = result.gateway2Latency,
                    language = language
                )
            }

            // 5. Interactive Topology Map (Visual Infographic Diagram)
            item {
                InteractiveTopologyMap(
                    result = result,
                    deviceIp = deviceIp,
                    selectedNode = selectedNode,
                    onSelectNode = { selectedNode = it },
                    language = language
                )
            }

            // 6. Latency Distribution Stack Bar Infographic
            item {
                LatencyDistributionInfographicCard(
                    result = result,
                    language = language
                )
            }

            // 7. Selected Node Inspector Card
            item {
                NodeInspectorCard(
                    node = selectedNode,
                    result = result,
                    deviceIp = deviceIp,
                    language = language
                )
            }

            // 8. Traceroute Path Hops Card
            if (result.hops.isNotEmpty()) {
                item {
                    TracerouteHopsCard(
                        hops = result.hops,
                        language = language
                    )
                }
            }
        } else {
            // Empty / Initial State: EXACTLY 1 Diagnostic Run Card
            item {
                InitialInfographicPreviewCard(
                    deviceIp = deviceIp,
                    language = language,
                    onRunDiagnostic = onRunDiagnostic,
                    isLoading = isLoading
                )
            }
        }

        // 6. TTL Packet Journey Step-by-Step Infographic (Collapsible)
        item {
            TtlPacketJourneyInfographic(
                isExpanded = isTtlGuideExpanded,
                onToggleExpand = { isTtlGuideExpanded = !isTtlGuideExpanded },
                language = language
            )
        }
    }
}

// -------------------------------------------------------------------------
// 1. Infographic Header Card
// -------------------------------------------------------------------------
@Composable
private fun InfographicHeaderCard(
    isLoading: Boolean,
    language: AppLanguage,
    onRunDiagnostic: () -> Unit
) {
    CardContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AppColors.primary, AppColors.gradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = Translations.tr("networkTopology", language),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                    Text(
                        text = if (language == AppLanguage.BN)
                            "ডুয়াল গেটওয়ে ও টিটিএল হপ ম্যাপিং"
                        else
                            "Dual Gateway & TTL Hop Visualization",
                        fontSize = 12.sp,
                        color = AppColors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            PrimaryButton(
                text = if (isLoading)
                    Translations.tr("running", language)
                else
                    Translations.tr("runAgain", language),
                onClick = onRunDiagnostic,
                isLoading = isLoading
            )
        }
    }
}

// -------------------------------------------------------------------------
// Backend Notice Pill
// -------------------------------------------------------------------------
@Composable
private fun BackendNoticePill(
    isFromBackend: Boolean,
    backendUrl: String,
    language: AppLanguage,
    onOpenServerConfig: () -> Unit
) {
    Surface(
        color = if (isFromBackend) AppColors.greenSoft else AppColors.yellowSoft,
        shape = RoundedCornerShape(AppRadius.md),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isFromBackend) AppColors.greenSoftBorder else AppColors.yellowSoftBorder,
                RoundedCornerShape(AppRadius.md)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isFromBackend)
                    "Backend active: $backendUrl"
                else
                    Translations.tr("backendNotice", language),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isFromBackend) AppColors.green else AppColors.ink,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Config",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onOpenServerConfig() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// -------------------------------------------------------------------------
// 2. Route Health Infographic Card (HUD Gauge)
// -------------------------------------------------------------------------
@Composable
private fun RouteHealthInfographicCard(
    score: Int,
    gateway1Latency: Double,
    gateway2Latency: Double,
    language: AppLanguage
) {
    val healthColor = when {
        score >= 90 -> AppColors.green
        score >= 75 -> AppColors.yellow
        else -> AppColors.red
    }
    val healthBg = when {
        score >= 90 -> AppColors.greenSoft
        score >= 75 -> AppColors.yellowSoft
        else -> AppColors.redSoft
    }
    val healthLabel = when {
        score >= 90 -> Translations.tr("excellent", language)
        score >= 75 -> Translations.tr("good", language)
        else -> Translations.tr("poor", language)
    }

    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = Translations.tr("healthScore", language),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.ink
                )
                Text(
                    text = Translations.tr("routeQuality", language),
                    fontSize = 11.sp,
                    color = AppColors.inkMuted
                )
            }

            StatusTag(text = healthLabel, level = if (score >= 90) "green" else if (score >= 75) "yellow" else "red")
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Circular Meter
            Box(
                modifier = Modifier.size(92.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // Track
                    drawArc(
                        color = Color(0xFFECEAF1),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Arc
                    val sweep = (score / 100f) * 260f
                    drawArc(
                        color = healthColor,
                        startAngle = 140f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$score",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.ink
                    )
                    Text(
                        text = "/100",
                        fontSize = 10.sp,
                        color = AppColors.inkMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stat Columns
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChipRow(
                    label = Translations.tr("homeRouter", language),
                    value = "${"%.1f".format(gateway1Latency)} ms",
                    accentColor = AppColors.green
                )

                MetricChipRow(
                    label = Translations.tr("ispRouter", language),
                    value = "${"%.1f".format(gateway2Latency)} ms",
                    accentColor = AppColors.blue
                )

                val ispOverhead = max(0.1, gateway2Latency - gateway1Latency)
                MetricChipRow(
                    label = if (language == AppLanguage.BN) "আইএসপি ট্রানজিট" else "ISP Overhead",
                    value = "+${"%.1f".format(ispOverhead)} ms",
                    accentColor = AppColors.primary
                )
            }
        }
    }
}

@Composable
private fun MetricChipRow(label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(accentColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.inkSoft
            )
        }
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}

// -------------------------------------------------------------------------
// 3. Interactive Topology Map (Visual Infographic Diagram)
// -------------------------------------------------------------------------
@Composable
private fun InteractiveTopologyMap(
    result: DiagnosticResult,
    deviceIp: String,
    selectedNode: TopologyNodeKey,
    onSelectNode: (TopologyNodeKey) -> Unit,
    language: AppLanguage
) {
    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Translations.tr("networkTopology", language),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.ink
            )
            Text(
                text = Translations.tr("tapNodeHint", language),
                fontSize = 10.5.sp,
                color = AppColors.inkMuted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Node 0: Client Device
        InfographicNodeRow(
            nodeKey = TopologyNodeKey.CLIENT,
            isSelected = selectedNode == TopologyNodeKey.CLIENT,
            icon = Icons.Default.Smartphone,
            iconBg = Color(0xFFF1F5F9),
            iconColor = Color(0xFF475569),
            badgeTitle = Translations.tr("yourDevice", language),
            ipSubtitle = deviceIp,
            metaText = "Local Interface (Hop 0)",
            latencyBadge = null,
            onClick = { onSelectNode(TopologyNodeKey.CLIENT) }
        )

        // Animated Link 1: WiFi
        val wifiLatency = if (result.localGwStats.isReachable) result.localGwStats.avgMs else result.gateway1Latency
        val wifiLoss = result.localGwStats.packetLossPercent
        InfographicAnimatedLink(
            tag = if (wifiLoss > 0) "WiFi Link (${wifiLoss}% loss)" else "WiFi 802.11 Link",
            latencyMs = wifiLatency,
            accentColor = if (wifiLoss == 0.0 && wifiLatency < 20.0) AppColors.green else AppColors.red
        )

        // Node 1: Gateway 1 (Home Router)
        InfographicNodeRow(
            nodeKey = TopologyNodeKey.GATEWAY_1,
            isSelected = selectedNode == TopologyNodeKey.GATEWAY_1,
            icon = Icons.Default.Router,
            iconBg = if (result.localGwStats.isReachable) AppColors.greenSoft else AppColors.redSoft,
            iconColor = if (result.localGwStats.isReachable) AppColors.green else AppColors.red,
            badgeTitle = "${Translations.tr("homeRouter", language)} (GW1)",
            ipSubtitle = result.gateway1,
            metaText = if (result.localGwStats.isReachable) "Default Gateway • ${result.localGwStats.received}/${result.localGwStats.transmitted} replies" else "Unreachable (100% loss)",
            latencyBadge = if (result.localGwStats.isReachable) "${"%.1f".format(result.localGwStats.avgMs)} ms" else "Offline",
            onClick = { onSelectNode(TopologyNodeKey.GATEWAY_1) }
        )

        // Animated Link 2: Fiber/WAN
        val isGw2Known = !result.gateway2.isBlank() && !result.gateway2.equals("Unknown", ignoreCase = true) && result.gateway2 != "*"
        val wanLatency = if (isGw2Known && result.gateway2Latency > 0) {
            max(0.5, result.gateway2Latency - result.gateway1Latency)
        } else if (result.internetTargetStats.isReachable) {
            max(0.5, result.internetTargetStats.avgMs - result.gateway1Latency)
        } else {
            0.0
        }
        InfographicAnimatedLink(
            tag = "Carrier Uplink / WAN",
            latencyMs = wanLatency,
            accentColor = if (result.internetTargetStats.isReachable) AppColors.blue else AppColors.red
        )

        // Node 2: Gateway 2 (ISP Upstream Gateway)
        val gw2Title = "${Translations.tr("upstreamGateway", language)} (GW2)"
        val gw2Ip = if (!isGw2Known) {
            if (language == AppLanguage.BN) "অজানা (ল্যান রাউটারে লুকানো)" else "Unknown (Not exposed on LAN)"
        } else {
            result.gateway2
        }
        val gw2Meta = if (!isGw2Known) {
            "WAN next-hop private to router"
        } else {
            "Carrier Edge Gateway"
        }
        val gw2LatencyBadge = if (isGw2Known && result.gateway2Latency > 0) "${"%.1f".format(result.gateway2Latency)} ms" else "Unknown"

        InfographicNodeRow(
            nodeKey = TopologyNodeKey.GATEWAY_2,
            isSelected = selectedNode == TopologyNodeKey.GATEWAY_2,
            icon = Icons.Default.Public,
            iconBg = if (!isGw2Known) Color(0xFFF1F5F9) else AppColors.blueSoft,
            iconColor = if (!isGw2Known) Color(0xFF64748B) else AppColors.blue,
            badgeTitle = gw2Title,
            ipSubtitle = gw2Ip,
            metaText = gw2Meta,
            latencyBadge = gw2LatencyBadge,
            onClick = { onSelectNode(TopologyNodeKey.GATEWAY_2) }
        )

        // Animated Link 3: IXP / Global Backbone
        InfographicAnimatedLink(
            tag = "Global Transit / IXP",
            latencyMs = if (result.internetTargetStats.isReachable) max(1.0, result.internetTargetStats.avgMs) else 0.0,
            accentColor = if (result.internetTargetStats.isReachable) AppColors.primary else AppColors.red
        )

        // Node 3: Internet Destination
        InfographicNodeRow(
            nodeKey = TopologyNodeKey.INTERNET,
            isSelected = selectedNode == TopologyNodeKey.INTERNET,
            icon = Icons.Default.Cloud,
            iconBg = if (result.internetTargetStats.isReachable) AppColors.primarySoft else AppColors.redSoft,
            iconColor = if (result.internetTargetStats.isReachable) AppColors.primary else AppColors.red,
            badgeTitle = Translations.tr("internet", language),
            ipSubtitle = "${result.internetTargetStats.host} (Google DNS)",
            metaText = if (result.internetTargetStats.isReachable) "Target Reachable • Loss: ${result.internetTargetStats.packetLossPercent}%" else "Target Unreachable (100% loss)",
            latencyBadge = if (result.internetTargetStats.isReachable) "${"%.1f".format(result.internetTargetStats.avgMs)} ms" else "Timeout",
            onClick = { onSelectNode(TopologyNodeKey.INTERNET) }
        )
    }
}

// Visual Node Row in the Infographic
@Composable
private fun InfographicNodeRow(
    nodeKey: TopologyNodeKey,
    isSelected: Boolean,
    icon: ImageVector,
    iconBg: Color,
    iconColor: Color,
    badgeTitle: String,
    ipSubtitle: String,
    metaText: String,
    latencyBadge: String?,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) iconColor else AppColors.border,
        label = "nodeBorder"
    )
    val containerBg by animateColorAsState(
        targetValue = if (isSelected) iconColor.copy(alpha = 0.08f) else AppColors.surfaceAlt,
        label = "nodeBg"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(AppRadius.md))
            .clickable(onClick = onClick),
        color = containerBg,
        shape = RoundedCornerShape(AppRadius.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                        .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = badgeTitle,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.ink
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(iconColor)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "SELECTED",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Text(
                        text = ipSubtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iconColor
                    )
                    Text(
                        text = metaText,
                        fontSize = 10.sp,
                        color = AppColors.inkMuted
                    )
                }
            }

            if (latencyBadge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.sm))
                        .background(iconColor.copy(alpha = 0.15f))
                        .border(1.dp, iconColor.copy(alpha = 0.3f), RoundedCornerShape(AppRadius.sm))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = latencyBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor
                    )
                }
            }
        }
    }
}

// Animated Connecting Pipeline between nodes
@Composable
private fun InfographicAnimatedLink(
    tag: String,
    latencyMs: Double,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "packetFlow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical pipe with moving packet
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                drawLine(
                    color = accentColor.copy(alpha = 0.35f),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = pathEffect
                )

                // Traveling pulse dot
                val currentY = size.height * phase
                drawCircle(
                    color = accentColor,
                    radius = 4.dp.toPx(),
                    center = Offset(size.width / 2, currentY)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.surfaceAlt)
                .border(0.8.dp, AppColors.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = tag,
                fontSize = 10.sp,
                color = AppColors.inkMuted,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "•",
                fontSize = 9.sp,
                color = AppColors.inkMuted
            )
            Text(
                text = "▼ ${"%.1f".format(latencyMs)} ms",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

// -------------------------------------------------------------------------
// 4. Latency Distribution Stack Bar Infographic Card
// -------------------------------------------------------------------------
@Composable
private fun LatencyDistributionInfographicCard(
    result: DiagnosticResult,
    language: AppLanguage
) {
    CardContainer {
        Text(
            text = Translations.tr("latencyDistribution", language),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.ink
        )
        Text(
            text = if (language == AppLanguage.BN)
                "নেটওয়ার্ক সেগমেন্টভিত্তিক লেটেন্সি বিভাজন"
            else
                "Latency split by network traversal segment",
            fontSize = 11.sp,
            color = AppColors.inkMuted
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Proportions
        val hop1 = max(0.5, if (result.localGwStats.isReachable) result.localGwStats.avgMs else result.gateway1Latency)
        val isGw2Known = !result.gateway2.isBlank() && !result.gateway2.equals("Unknown", ignoreCase = true) && result.gateway2 != "*"
        val hop2 = if (isGw2Known && result.gateway2Latency > 0) {
            max(0.5, result.gateway2Latency - hop1)
        } else {
            max(0.5, (result.internetTargetStats.avgMs - hop1) * 0.4)
        }
        val hop3 = if (result.internetTargetStats.isReachable) {
            max(0.5, result.internetTargetStats.avgMs - hop1 - hop2)
        } else {
            15.0
        }
        val total = hop1 + hop2 + hop3

        val p1 = (hop1 / total).toFloat().coerceIn(0.08f, 0.7f)
        val p2 = (hop2 / total).toFloat().coerceIn(0.12f, 0.7f)
        val p3 = (1f - p1 - p2).coerceAtLeast(0.1f)

        // Stacked Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFE2E8F0))
        ) {
            Box(
                modifier = Modifier
                    .weight(p1)
                    .fillMaxHeight()
                    .background(AppColors.green)
            )
            Box(
                modifier = Modifier
                    .weight(p2)
                    .fillMaxHeight()
                    .background(AppColors.blue)
            )
            Box(
                modifier = Modifier
                    .weight(p3)
                    .fillMaxHeight()
                    .background(AppColors.primary)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DistributionLegendItem(
                color = AppColors.green,
                title = Translations.tr("hop1WiFi", language),
                value = "${"%.1f".format(hop1)} ms (${(p1 * 100).toInt()}%)"
            )
            DistributionLegendItem(
                color = AppColors.blue,
                title = if (isGw2Known) Translations.tr("hop2ISP", language) else "WAN Uplink",
                value = "${"%.1f".format(hop2)} ms (${(p2 * 100).toInt()}%)"
            )
            DistributionLegendItem(
                color = AppColors.primary,
                title = Translations.tr("hop3Cloud", language),
                value = "${"%.1f".format(hop3)} ms (${(p3 * 100).toInt()}%)"
            )
        }
    }
}

@Composable
private fun DistributionLegendItem(
    color: Color,
    title: String,
    value: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = title,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.ink
            )
        }
        Text(
            text = value,
            fontSize = 9.5.sp,
            color = AppColors.inkMuted,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

// -------------------------------------------------------------------------
// 5. Selected Node Inspector Card
// -------------------------------------------------------------------------
@Composable
private fun NodeInspectorCard(
    node: TopologyNodeKey,
    result: DiagnosticResult,
    deviceIp: String,
    language: AppLanguage
) {
    val (title, icon, iconColor, ip, hopNum, roleDesc, pingStr) = when (node) {
        TopologyNodeKey.CLIENT -> Tuple7(
            Translations.tr("yourDevice", language),
            Icons.Default.Smartphone,
            Color(0xFF475569),
            deviceIp,
            "0 (Client)",
            if (language == AppLanguage.BN) "লোকাল অ্যান্ড্রয়েড ইন্টারফেস (wlan0)" else "Local Android Network Interface (wlan0)",
            "0.0 ms (Self)"
        )
        TopologyNodeKey.GATEWAY_1 -> Tuple7(
            Translations.tr("homeRouter", language),
            Icons.Default.Router,
            AppColors.green,
            result.gateway1,
            "1 (Local)",
            if (language == AppLanguage.BN) "হোম ওয়াইফাই রাউটার • NAT ও DHCP সার্ভার" else "Home WiFi Router • Local NAT & DHCP Server",
            "${"%.1f".format(result.gateway1Latency)} ms"
        )
        TopologyNodeKey.GATEWAY_2 -> {
            val isUnknown = result.gateway2.isBlank() || result.gateway2.equals("Unknown", ignoreCase = true) || result.gateway2 == "*"
            Tuple7(
                "${Translations.tr("upstreamGateway", language)} (GW2)",
                Icons.Default.Public,
                if (isUnknown) Color(0xFF64748B) else AppColors.blue,
                if (isUnknown) "Unknown (Not exposed on LAN)" else result.gateway2,
                "WAN Next-Hop",
                if (language == AppLanguage.BN)
                    "গ্রাহক রাউটারগুলো সাধারণ ল্যানে WAN বা PPPoE গেটওয়ে প্রচার করে না। কোনো ট্রিপ বা অনুমান না করে এটি সঠিকভাবে 'Unknown' রাখা হয়েছে।"
                else
                    "Consumer routers do not broadcast their WAN next-hop / PPPoE gateway to LAN clients. It is reported as Unknown rather than guessing traceroute hops.",
                if (!isUnknown && result.gateway2Latency > 0) "${"%.1f".format(result.gateway2Latency)} ms" else "Unknown"
            )
        }
        TopologyNodeKey.INTERNET -> Tuple7(
            Translations.tr("internet", language),
            Icons.Default.Cloud,
            if (result.internetTargetStats.isReachable) AppColors.primary else AppColors.red,
            result.internetTargetStats.host,
            "Target Endpoint",
            if (language == AppLanguage.BN)
                "গ্লোবাল ইন্টারনেট টার্গেট সার্ভার • প্যাকেট লস: ${result.internetTargetStats.packetLossPercent}% (${result.internetTargetStats.received}/${result.internetTargetStats.transmitted} প্রব)"
            else
                "Global Internet Target • Measured Packet Loss: ${result.internetTargetStats.packetLossPercent}% (${result.internetTargetStats.received}/${result.internetTargetStats.transmitted} probes)",
            if (result.internetTargetStats.isReachable) "${"%.1f".format(result.internetTargetStats.avgMs)} ms" else "Timeout"
        )
    }

    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = Translations.tr("nodeDetails", language),
                        fontSize = 11.sp,
                        color = AppColors.inkMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                }
            }

            StatusTag(text = "Hop $hopNum", level = "green")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.border)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = Translations.tr("ipAddress", language),
                    fontSize = 10.5.sp,
                    color = AppColors.inkMuted
                )
                Text(
                    text = ip,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.ink
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Translations.tr("latency", language),
                    fontSize = 10.5.sp,
                    color = AppColors.inkMuted
                )
                Text(
                    text = pingStr,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = iconColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = roleDesc,
            fontSize = 11.5.sp,
            color = AppColors.inkSoft,
            lineHeight = 16.sp
        )
    }
}

// -------------------------------------------------------------------------
// Differential Diagnostic Verdict Card
// -------------------------------------------------------------------------
@Composable
private fun DifferentialDiagnosisCard(
    result: DiagnosticResult,
    language: AppLanguage
) {
    val (statusColor, statusBg, statusLevel) = when (result.diagnosisType) {
        DiagnosisType.HEALTHY -> Triple(AppColors.green, AppColors.greenSoft, "green")
        DiagnosisType.LOCAL_LAN_ISSUE -> Triple(AppColors.red, AppColors.redSoft, "red")
        DiagnosisType.ROUTER_TO_ISP_ISSUE -> Triple(AppColors.red, AppColors.redSoft, "red")
        DiagnosisType.ISP_BACKBONE_ISSUE -> Triple(AppColors.yellow, AppColors.yellowSoft, "yellow")
        DiagnosisType.DESTINATION_ISSUE -> Triple(AppColors.primary, AppColors.primarySoft, "blue")
        DiagnosisType.DISCONNECTED -> Triple(AppColors.red, AppColors.redSoft, "red")
    }

    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Translations.tr("diagnosisVerdict", language),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.inkMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = result.diagnosisTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.ink
                )
            }

            StatusTag(
                text = when (result.diagnosisType) {
                    DiagnosisType.HEALTHY -> Translations.tr("healthy", language)
                    DiagnosisType.LOCAL_LAN_ISSUE -> "LAN Fault"
                    DiagnosisType.ROUTER_TO_ISP_ISSUE -> "WAN Down"
                    DiagnosisType.ISP_BACKBONE_ISSUE -> "Backbone Issue"
                    DiagnosisType.DESTINATION_ISSUE -> "Target Issue"
                    DiagnosisType.DISCONNECTED -> "Offline"
                },
                level = statusLevel
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = statusBg,
            shape = RoundedCornerShape(AppRadius.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (result.diagnosisType) {
                            DiagnosisType.HEALTHY -> Icons.Default.CheckCircle
                            DiagnosisType.LOCAL_LAN_ISSUE -> Icons.Default.Router
                            DiagnosisType.ROUTER_TO_ISP_ISSUE -> Icons.Default.Warning
                            DiagnosisType.ISP_BACKBONE_ISSUE -> Icons.Default.Public
                            DiagnosisType.DESTINATION_ISSUE -> Icons.Default.Cloud
                            DiagnosisType.DISCONNECTED -> Icons.Default.WifiOff
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.diagnosisSummary,
                        fontSize = 12.5.sp,
                        color = AppColors.ink,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Evidence & Multi-Probe Measurements Card
// -------------------------------------------------------------------------
@Composable
private fun DiagnosticEvidenceCard(
    result: DiagnosticResult,
    language: AppLanguage
) {
    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Translations.tr("diagnosisEvidence", language),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.ink
            )

            Text(
                text = "${result.evidenceList.count { it.passed }} / ${result.evidenceList.size} Passed",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            result.evidenceList.forEach { item ->
                EvidenceItemRow(item = item)
            }
        }
    }
}

@Composable
private fun EvidenceItemRow(item: DiagnosticEvidenceItem) {
    val checkColor = if (item.passed) AppColors.green else if (item.isWarning) AppColors.yellow else AppColors.red
    val checkBg = if (item.passed) AppColors.greenSoft else if (item.isWarning) AppColors.yellowSoft else AppColors.redSoft

    Surface(
        color = AppColors.surfaceAlt,
        shape = RoundedCornerShape(AppRadius.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(checkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.passed) Icons.Default.Check else if (item.isWarning) Icons.Default.PriorityHigh else Icons.Default.Close,
                    contentDescription = null,
                    tint = checkColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.ink
                )
                Text(
                    text = item.detail,
                    fontSize = 11.sp,
                    color = AppColors.inkMuted
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Traceroute Path Hops Card
// -------------------------------------------------------------------------
@Composable
private fun TracerouteHopsCard(
    hops: List<DiagnosticHop>,
    language: AppLanguage
) {
    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Translations.tr("tracerouteHops", language),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.ink
            )
            Text(
                text = "${hops.size} hops probed",
                fontSize = 11.sp,
                color = AppColors.inkMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = Translations.tr("icmpRateLimitNote", language),
            fontSize = 10.5.sp,
            color = AppColors.inkMuted,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            hops.forEach { hop ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.surfaceAlt)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hop ${hop.hop}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.primary
                        )
                        Text(
                            text = if (hop.ip == "*") "* (Request timed out)" else hop.ip,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (hop.ip == "*") AppColors.inkMuted else AppColors.ink
                        )
                    }

                    Text(
                        text = if (hop.ms > 0) "${"%.1f".format(hop.ms)} ms" else "*",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hop.ms > 0) AppColors.ink else AppColors.inkMuted
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// 6. TTL Packet Journey Step-by-Step Infographic (Collapsible)
// -------------------------------------------------------------------------
@Composable
private fun TtlPacketJourneyInfographic(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    language: AppLanguage
) {
    CardContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = AppColors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = Translations.tr("packetJourney", language),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.ink
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AppColors.inkMuted,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (isExpanded) 180f else 0f)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.border)
                )

                Text(
                    text = Translations.tr("diagnosticExplain", language),
                    fontSize = 12.5.sp,
                    color = AppColors.inkSoft,
                    lineHeight = 18.sp
                )

                // Step 1
                TtlStepCard(
                    stepNumber = "1",
                    ttlValue = "TTL = 1",
                    targetTitle = Translations.tr("homeRouter", language),
                    description = if (language == AppLanguage.BN)
                        "ডিভাইস প্যাকেট পাঠায় TTL=1 সহ। হোম রাউটার প্যাকেট পেয়ে TTL বিয়োগ করে 0 করে এবং 'ICMP Time Exceeded' বার্তা পাঠিয়ে নিজের আইপি প্রকাশ করে।"
                    else
                        "Device emits packet with TTL=1. Home Router decrements TTL to 0, drops packet, and replies with ICMP Time Exceeded, revealing Gateway 1.",
                    badgeColor = AppColors.green
                )

                // Step 2
                TtlStepCard(
                    stepNumber = "2",
                    ttlValue = "TTL = 2",
                    targetTitle = Translations.tr("ispRouter", language),
                    description = if (language == AppLanguage.BN)
                        "ডিভাইস প্যাকেট পাঠায় TTL=2 সহ। হোম রাউটার 1 বিয়োগ করে ফরোয়ার্ড করে; আইএসপি কোর রাউটার TTL=0 করে ICMP উত্তর পাঠায়।"
                    else
                        "Device emits packet with TTL=2. Home Router forwards with TTL=1. ISP Core Router decrements TTL to 0 and replies, revealing Gateway 2.",
                    badgeColor = AppColors.blue
                )

                // Step 3
                TtlStepCard(
                    stepNumber = "3",
                    ttlValue = "TTL = 64",
                    targetTitle = Translations.tr("internet", language),
                    description = if (language == AppLanguage.BN)
                        "স্বাভাবিক ইন্টারনেট ব্রাউজিং ও গেমিং ডেটা TTL=64 নিয়ে চলে, ফলে সকল হপ অতিক্রম করে সার্ভারে পৌঁছায়।"
                    else
                        "Production traffic travels with TTL=64, traversing all intermediate hops safely to web servers.",
                    badgeColor = AppColors.primary
                )
            }
        }
    }
}

@Composable
private fun TtlStepCard(
    stepNumber: String,
    ttlValue: String,
    targetTitle: String,
    description: String,
    badgeColor: Color
) {
    Surface(
        color = badgeColor.copy(alpha = 0.06f),
        shape = RoundedCornerShape(AppRadius.md),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, badgeColor.copy(alpha = 0.25f), RoundedCornerShape(AppRadius.md))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = targetTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = ttlValue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                fontSize = 11.5.sp,
                color = AppColors.inkSoft,
                lineHeight = 16.5.sp
            )
        }
    }
}

// -------------------------------------------------------------------------
// Initial / Empty State Infographic Preview Card
// -------------------------------------------------------------------------
@Composable
private fun InitialInfographicPreviewCard(
    deviceIp: String,
    language: AppLanguage,
    onRunDiagnostic: () -> Unit,
    isLoading: Boolean
) {
    CardContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AppColors.primary, AppColors.gradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = Translations.tr("networkTopology", language),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                    Text(
                        text = if (language == AppLanguage.BN)
                            "ডুয়াল গেটওয়ে ও টিটিএল হপ ম্যাপিং"
                        else
                            "Dual Gateway & TTL Hop Visualization",
                        fontSize = 12.sp,
                        color = AppColors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (language == AppLanguage.BN)
                    "হোম রাউটার (Gateway 1) এবং আইএসপি ব্যাকবোন (Gateway 2) এর লাইভ রুট মানচিত্র তৈরি করতে ডায়াগনস্টিক চালান"
                else
                    "Run diagnostic to generate an infographic map of Home Router (Gateway 1) and ISP Backbone (Gateway 2)",
                fontSize = 12.sp,
                color = AppColors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Topology preview sketch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.md))
                    .background(AppColors.surfaceAlt)
                    .border(1.dp, AppColors.border, RoundedCornerShape(AppRadius.md))
                    .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewNodeIcon(Icons.Default.Smartphone, "Device", Color(0xFF475569))
                Text("➔", color = AppColors.inkMuted, fontSize = 14.sp)
                PreviewNodeIcon(Icons.Default.Router, "GW 1", AppColors.green)
                Text("➔", color = AppColors.inkMuted, fontSize = 14.sp)
                PreviewNodeIcon(Icons.Default.Public, "GW 2", AppColors.blue)
                Text("➔", color = AppColors.inkMuted, fontSize = 14.sp)
                PreviewNodeIcon(Icons.Default.Cloud, "Cloud", AppColors.primary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = if (isLoading)
                    Translations.tr("running", language)
                else
                    Translations.tr("runDiagnostic", language),
                onClick = onRunDiagnostic,
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun PreviewNodeIcon(icon: ImageVector, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.inkSoft
        )
    }
}

// Simple 7-tuple helper
private data class Tuple7<A, B, C, D, E, F, G>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G
)

@Composable
fun UpstreamGatewayDebugCard(
    result: DiagnosticResult,
    deviceIp: String,
    language: AppLanguage
) {
    val discovery = result.upstreamDiscovery
    var isLogExpanded by remember { mutableStateOf(true) }

    val confidenceColor = when (discovery.confidence) {
        UpstreamConfidence.DIRECT -> Color(0xFF10B981)
        UpstreamConfidence.HIGH_CONFIDENCE -> Color(0xFF059669)
        UpstreamConfidence.MEDIUM_CONFIDENCE -> Color(0xFF3B82F6)
        UpstreamConfidence.LOW_CONFIDENCE -> Color(0xFFF59E0B)
        UpstreamConfidence.UNKNOWN -> Color(0xFF6B7280)
    }

    CardContainer(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(confidenceColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = confidenceColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = Translations.tr("upstreamInspection", language),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                    Text(
                        text = "Client-Side Discovery & Routing Path Inference",
                        fontSize = 11.sp,
                        color = AppColors.inkMuted
                    )
                }
            }

            // Confidence Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(confidenceColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = discovery.confidence.name.replace("_", " "),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = confidenceColor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Confirmation Status Banner
        val bannerBg = if (discovery.isConfirmed) {
            Color(0xFF10B981).copy(alpha = 0.12f)
        } else if (discovery.detectedIp.isNotBlank()) {
            Color(0xFF3B82F6).copy(alpha = 0.12f)
        } else {
            Color(0xFF6B7280).copy(alpha = 0.08f)
        }

        val bannerBorder = if (discovery.isConfirmed) {
            Color(0xFF10B981).copy(alpha = 0.35f)
        } else if (discovery.detectedIp.isNotBlank()) {
            Color(0xFF3B82F6).copy(alpha = 0.35f)
        } else {
            Color(0xFF6B7280).copy(alpha = 0.2f)
        }

        val bannerIcon = if (discovery.isConfirmed) {
            Icons.Default.CheckCircle
        } else if (discovery.detectedIp.isNotBlank()) {
            Icons.Default.Hub
        } else {
            Icons.Default.HelpOutline
        }

        val bannerTitle = if (discovery.isConfirmed) {
            "${Translations.tr("confirmedGateway", language)}: ${discovery.detectedIp}"
        } else if (discovery.detectedIp.isNotBlank()) {
            "${Translations.tr("inferredCandidate", language)}: ${discovery.detectedIp}"
        } else {
            Translations.tr("upstreamUnknownNote", language)
        }

        val bannerSubtitle = if (discovery.isConfirmed) {
            "Direct protocol/OS confirmation via ${discovery.method.name}"
        } else if (discovery.detectedIp.isNotBlank()) {
            "Traceroute path candidate. The router's PPPoE peer gateway is not exposed over LAN."
        } else {
            "Router does not broadcast WAN default gateway across LAN DHCP boundary."
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(bannerBg)
                .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = bannerIcon,
                    contentDescription = null,
                    tint = if (discovery.isConfirmed) Color(0xFF10B981) else if (discovery.detectedIp.isNotBlank()) Color(0xFF3B82F6) else Color(0xFF6B7280),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = bannerTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                    Text(
                        text = bannerSubtitle,
                        fontSize = 11.sp,
                        color = AppColors.inkMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Diagnostic Parameters Table
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.surfaceAlt)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DebugParamRow(
                label = Translations.tr("localIpLabel", language),
                value = discovery.localIp.ifBlank { deviceIp },
                highlight = false
            )
            DebugParamRow(
                label = Translations.tr("localGatewayLabel", language),
                value = discovery.localGateway.ifBlank { result.gateway1 },
                highlight = false
            )
            DebugParamRow(
                label = Translations.tr("detectedUpstreamLabel", language),
                value = if (discovery.detectedIp.isNotBlank()) discovery.detectedIp else "Not Detected",
                highlight = discovery.detectedIp.isNotBlank(),
                highlightColor = if (discovery.isConfirmed) Color(0xFF10B981) else Color(0xFF3B82F6)
            )
            DebugParamRow(
                label = Translations.tr("detectionMethodLabel", language),
                value = discovery.method.name,
                highlight = false
            )
            DebugParamRow(
                label = Translations.tr("confidenceLabel", language),
                value = discovery.confidence.name,
                highlight = true,
                highlightColor = confidenceColor
            )
            DebugParamRow(
                label = Translations.tr("tracerouteFirstHopLabel", language),
                value = if (discovery.tracerouteFirstHop.isNotBlank()) discovery.tracerouteFirstHop else "No response (*)",
                highlight = false
            )

            // Candidate Hops list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = Translations.tr("tracerouteCandidatesLabel", language),
                    fontSize = 12.sp,
                    color = AppColors.inkMuted,
                    modifier = Modifier.weight(1f)
                )
                if (discovery.candidateHops.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        discovery.candidateHops.forEach { cand ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AppColors.blue.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = cand,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.blue
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "None discovered",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.inkSoft
                    )
                }
            }

            // Sandbox & OS Route details
            DebugParamRow(
                label = "Android Sandbox",
                value = discovery.rootStatus,
                highlight = false
            )
            if (discovery.ipv6Gateway != null) {
                DebugParamRow(
                    label = "IPv6 Gateway",
                    value = discovery.ipv6Gateway,
                    highlight = false
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Reasoning / Evidence Log (Collapsible / Toggleable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { isLogExpanded = !isLogExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Translations.tr("reasoningEvidenceLabel", language),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.ink
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${discovery.reasoningEvidence.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.primary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AppColors.inkMuted,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (isLogExpanded) 180f else 0f)
            )
        }

        AnimatedVisibility(visible = isLogExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.ink.copy(alpha = 0.04f))
                    .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (discovery.reasoningEvidence.isNotEmpty()) {
                    discovery.reasoningEvidence.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.primary,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = item,
                                fontSize = 11.sp,
                                color = AppColors.ink,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No diagnostic telemetry recorded yet. Run a diagnostic cycle to collect evidence.",
                        fontSize = 11.sp,
                        color = AppColors.inkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugParamRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    highlightColor: Color = AppColors.ink
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = AppColors.inkMuted
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) highlightColor else AppColors.ink
        )
    }
}
