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

@Composable
fun DiagnosticScreen(
    result: DiagnosticResult?,
    isLoading: Boolean,
    deviceIp: String,
    language: AppLanguage,
    onRunDiagnostic: () -> Unit
) {
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

            // 2. Network Workflow Diagram: The 4 Main Status Cards
            // (1. Your Device -> 2. Home Router -> 3. Upstream Gateway -> 4. Internet)
            item {
                SimplifiedWorkflowDiagram(
                    result = result,
                    deviceIp = deviceIp,
                    language = language
                )
            }
        } else {
            // Empty / Initial State: Diagnostic Run Card
            item {
                InitialInfographicPreviewCard(
                    deviceIp = deviceIp,
                    language = language,
                    onRunDiagnostic = onRunDiagnostic,
                    isLoading = isLoading
                )
            }
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
// Discovered PPPoE Gateway Card (Pure Client-Side Discovery)
// -------------------------------------------------------------------------
@Composable
private fun DiscoveredPppoeGatewayCard(
    result: DiagnosticResult,
    language: AppLanguage
) {
    val upstream = result.upstreamDiscovery
    val pppoeIp = upstream.pppoeGateway ?: upstream.detectedIp.takeIf { it.isNotBlank() && it != "*" && !it.equals("Unknown", ignoreCase = true) } ?: return

    val methodLabel = when (upstream.method) {
        UpstreamDetectionMethod.UPNP_IGD_WAN_SERVICE -> Translations.tr("upnpStrategy", language)
        UpstreamDetectionMethod.TTL_HOP2_TRACEROUTE -> Translations.tr("tracerouteHop2Strategy", language)
        else -> if (upstream.discoveryStrategyUsed.isNotBlank()) upstream.discoveryStrategyUsed else "Client-side Discovery"
    }

    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppColors.primarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = AppColors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = Translations.tr("discoveredPppoeGateway", language),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                    Text(
                        text = "PPPoE Upstream Gateway Isolated",
                        fontSize = 11.sp,
                        color = AppColors.inkMuted
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(AppRadius.sm),
                color = AppColors.greenSoft
            ) {
                Text(
                    text = "Discovered",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.green,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main IP Display Highlight
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.sm),
            color = AppColors.surfaceAlt
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gateway IP (ISP Uplink)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.inkMuted
                        )
                        Text(
                            text = pppoeIp,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppColors.primary
                        )
                    }

                    if (result.gateway2Latency > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Latency",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.inkMuted
                            )
                            Text(
                                text = "${"%.1f".format(result.gateway2Latency)} ms",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.green
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.border)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Strategy & Method info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Translations.tr("pppoeDiscoveryMethod", language),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.inkMuted
                    )
                    Text(
                        text = methodLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                }

                // WAN External IP if available
                if (!upstream.upnpExternalIp.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translations.tr("wanExternalIp", language),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.inkMuted
                        )
                        Text(
                            text = upstream.upnpExternalIp,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.inkSoft
                        )
                    }
                }

                // WAN Status if available
                if (!upstream.upnpWanStatus.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translations.tr("wanStatus", language),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.inkMuted
                        )
                        Text(
                            text = upstream.upnpWanStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.green
                        )
                    }
                }
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
// Simplified Linear Workflow Diagram
// Device -> Local Gateway -> Upstream Gateway -> Internet
// -------------------------------------------------------------------------
@Composable
fun SimplifiedWorkflowDiagram(
    result: DiagnosticResult,
    deviceIp: String,
    language: AppLanguage
) {
    CardContainer(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = Translations.tr("workflowDiagram", language),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.ink
        )
        Text(
            text = Translations.tr("workflowSubtitle", language),
            fontSize = 11.5.sp,
            color = AppColors.inkMuted
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Device (Hop 0)
        WorkflowHopCard(
            stepNumber = 1,
            title = Translations.tr("yourDevice", language),
            icon = Icons.Default.Smartphone,
            ip = deviceIp.ifBlank { "127.0.0.1" },
            latencyText = "0.0 ms",
            packetLossText = "0.0%",
            statusText = Translations.tr("statusGood", language),
            statusLevel = "green"
        )

        WorkflowArrowConnector()

        // 2. Local Gateway (Hop 1)
        val isLocalGwValid = result.gateway1.isNotBlank() &&
                result.gateway1 != "0.0.0.0" &&
                !result.gateway1.equals("Not Connected", ignoreCase = true) &&
                !result.gateway1.equals("Unknown", ignoreCase = true)
        val localLoss = result.localGwStats.packetLossPercent
        val localLatency = if (result.localGwStats.isReachable) result.localGwStats.avgMs else result.gateway1Latency
        val localStatusText = when {
            !isLocalGwValid -> Translations.tr("statusProblem", language)
            result.localGwStats.isReachable && localLoss < 5.0 -> Translations.tr("statusGood", language)
            result.localGwStats.isReachable -> Translations.tr("statusProblem", language)
            localLatency > 0 -> Translations.tr("statusGood", language)
            else -> Translations.tr("statusProblem", language)
        }
        val localStatusLevel = if (localStatusText == Translations.tr("statusGood", language)) "green" else "red"

        WorkflowHopCard(
            stepNumber = 2,
            title = Translations.tr("homeRouter", language),
            icon = Icons.Default.Router,
            ip = if (isLocalGwValid) result.gateway1 else Translations.tr("statusUnknown", language),
            latencyText = if (isLocalGwValid && (result.localGwStats.isReachable || localLatency > 0)) "${"%.1f".format(localLatency)} ms" else "Timeout",
            packetLossText = if (isLocalGwValid) "${"%.1f".format(localLoss)}%" else "100.0%",
            statusText = localStatusText,
            statusLevel = localStatusLevel
        )

        WorkflowArrowConnector()

        // 3. Upstream Gateway (Hop 2)
        val isUpstreamConfirmed = (!result.gateway2.isBlank() &&
                !result.gateway2.equals("Unknown", ignoreCase = true) &&
                result.gateway2 != "*") ||
                (result.upstreamDiscovery.detectedIp.isNotBlank() &&
                 !result.upstreamDiscovery.detectedIp.equals("Unknown", ignoreCase = true) &&
                 result.upstreamDiscovery.detectedIp != "*")
        val upstreamIp = if (isUpstreamConfirmed) {
            if (!result.gateway2.isBlank() && !result.gateway2.equals("Unknown", ignoreCase = true) && result.gateway2 != "*") {
                result.gateway2
            } else {
                result.upstreamDiscovery.detectedIp
            }
        } else {
            Translations.tr("statusUnknown", language)
        }
        val upstreamStats = result.upstreamGwStats
        val upstreamLoss = upstreamStats?.packetLossPercent ?: 0.0
        val upstreamLatency = if (upstreamStats != null && upstreamStats.isReachable) {
            upstreamStats.avgMs
        } else if (result.gateway2Latency > 0) {
            result.gateway2Latency
        } else {
            0.0
        }

        val upstreamStatusText = when {
            !isUpstreamConfirmed -> Translations.tr("statusUnknown", language)
            upstreamStats != null && !upstreamStats.isReachable -> Translations.tr("statusProblem", language)
            upstreamLoss > 5.0 -> Translations.tr("statusProblem", language)
            upstreamLatency > 0 -> Translations.tr("statusGood", language)
            isUpstreamConfirmed -> Translations.tr("statusGood", language)
            else -> Translations.tr("statusUnknown", language)
        }
        val upstreamStatusLevel = when (upstreamStatusText) {
            Translations.tr("statusGood", language) -> "green"
            Translations.tr("statusProblem", language) -> "red"
            else -> "yellow"
        }

        WorkflowHopCard(
            stepNumber = 3,
            title = Translations.tr("upstreamGateway", language),
            icon = Icons.Default.Public,
            ip = upstreamIp,
            latencyText = if (isUpstreamConfirmed && upstreamLatency > 0) "${"%.1f".format(upstreamLatency)} ms" else if (isUpstreamConfirmed) "< 5 ms" else Translations.tr("statusUnknown", language),
            packetLossText = if (isUpstreamConfirmed && upstreamStats != null) "${"%.1f".format(upstreamLoss)}%" else if (isUpstreamConfirmed) "0.0%" else Translations.tr("statusUnknown", language),
            statusText = upstreamStatusText,
            statusLevel = upstreamStatusLevel
        )

        WorkflowArrowConnector()

        // 4. Internet
        val targetStats = result.internetTargetStats
        val internetReachable = targetStats.isReachable || result.secondaryTargetStats.isReachable
        val internetLoss = targetStats.packetLossPercent
        val internetLatency = if (targetStats.isReachable) targetStats.avgMs else result.secondaryTargetStats.avgMs
        val internetStatusText = when {
            internetReachable && internetLoss < 5.0 -> Translations.tr("statusGood", language)
            internetReachable -> Translations.tr("statusProblem", language)
            else -> Translations.tr("statusProblem", language)
        }
        val internetStatusLevel = if (internetStatusText == Translations.tr("statusGood", language)) "green" else "red"

        WorkflowHopCard(
            stepNumber = 4,
            title = Translations.tr("internet", language),
            icon = Icons.Default.Cloud,
            ip = if (targetStats.host.isNotBlank()) targetStats.host else "8.8.8.8",
            latencyText = if (internetReachable) "${"%.1f".format(internetLatency)} ms" else "Timeout",
            packetLossText = "${"%.1f".format(internetLoss)}%",
            statusText = internetStatusText,
            statusLevel = internetStatusLevel
        )
    }
}

@Composable
private fun WorkflowArrowConnector() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(10.dp)
                    .background(AppColors.border)
            )
            Text(
                text = "▼",
                fontSize = 11.sp,
                color = AppColors.inkMuted
            )
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(10.dp)
                    .background(AppColors.border)
            )
        }
    }
}

@Composable
private fun WorkflowHopCard(
    stepNumber: Int,
    title: String,
    icon: ImageVector,
    ip: String,
    latencyText: String,
    packetLossText: String,
    statusText: String,
    statusLevel: String
) {
    val statusColor = when (statusLevel) {
        "green" -> AppColors.green
        "yellow" -> AppColors.yellow
        else -> AppColors.red
    }
    val statusBg = when (statusLevel) {
        "green" -> AppColors.greenSoft
        "yellow" -> AppColors.yellowSoft
        else -> AppColors.redSoft
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .border(1.dp, AppColors.border, RoundedCornerShape(AppRadius.md)),
        color = AppColors.surfaceAlt,
        shape = RoundedCornerShape(AppRadius.md)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Top Row: Icon + Title + Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(statusBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$stepNumber.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.inkMuted
                        )
                        Text(
                            text = title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.ink
                        )
                    }
                }

                StatusTag(text = statusText, level = statusLevel)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details Grid: IP Address, Ping Latency, Packet Loss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // IP Address
                Column(modifier = Modifier.weight(1.3f)) {
                    Text(
                        text = "IP Address",
                        fontSize = 10.sp,
                        color = AppColors.inkMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = ip,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.ink
                    )
                }

                // Ping Latency
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ping Latency",
                        fontSize = 10.sp,
                        color = AppColors.inkMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = latencyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (latencyText.contains("Timeout") || latencyText.contains("Unknown")) AppColors.inkMuted else AppColors.ink
                    )
                }

                // Packet Loss
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Packet Loss",
                        fontSize = 10.sp,
                        color = AppColors.inkMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = packetLossText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            packetLossText.startsWith("0") -> AppColors.green
                            packetLossText.contains("Unknown") -> AppColors.inkMuted
                            else -> AppColors.red
                        }
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
                        "ডিভাইস প্যাকেট পাঠায় TTL=2 সহ। হোম রাউটার 1 বিয়োগ করে ফরোয়ার্ড করে। ট্রানজিট পাথ রাউটার উত্তর দিলে এটি প্রথম এক্সটার্নাল হপ প্রকাশ করে, তবে এটি রাউটারের WAN গেটওয়ের প্রমাণ নয় (ট্রেসরুট কেবল পাথ অ্যানালাইসিস)।"
                    else
                        "Device emits packet with TTL=2. Home router forwards packet. An responding upstream router reveals the first external hop on the path, used strictly for path analysis rather than assuming it is the WAN gateway.",
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
