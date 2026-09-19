package com.example.ui.screens

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.model.DiagnosticMode
import com.example.model.DiagnosisType
import com.example.model.DiagnosticEvidenceItem
import com.example.model.DiagnosticHop
import com.example.model.DiagnosticResult
import com.example.model.DiagnosticWorkflowNode
import com.example.model.UpstreamConfidence
import com.example.model.UpstreamDetectionMethod
import com.example.model.UpstreamDiscoveryResult
import com.example.ui.components.CardContainer
import com.example.ui.components.PrimaryButton
import com.example.ui.components.StatusTag
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticScreen(
    result: DiagnosticResult?,
    isLoading: Boolean,
    progressPercent: Int = 0,
    deviceIp: String,
    language: AppLanguage,
    onRunDiagnostic: (DiagnosticMode) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        contentPadding = PaddingValues(top = AppSpacing.md, bottom = 100.dp)
    ) {
        if (result != null) {
            // 1. Infographic Header & Mode Actions
            item {
                InfographicHeaderCard(
                    isLoading = isLoading,
                    progressPercent = progressPercent,
                    language = language,
                    onRunDiagnostic = onRunDiagnostic
                )
            }

            // 2. Intelligent Troubleshooting Note (Warning / Info Banner)
            // Strictly displayed only AFTER the scan completes or immediately halts on router failure
            val isScanCompleted = !isLoading || result.isSkippedDueToRouterFailure || progressPercent >= 100
            if (!isLoading && isScanCompleted) {
                item {
                    TroubleshootingNoteCard(
                        result = result,
                        language = language
                    )
                }
            }

            // Download Report Button (Strictly shown when FULL diagnostic scan finishes or halts on router failure)
            val isScanFinished = !isLoading && result.mode == DiagnosticMode.FULL && (
                result.isSkippedDueToRouterFailure ||
                result.nodes.any { it.iconType == "internet" || it.title == "ইন্টারনেট" } ||
                progressPercent >= 100
            )
            if (isScanFinished) {
                item {
                    DownloadReportCard(
                        result = result,
                        language = language
                    )
                }
            }

            // 3. Network Workflow Diagram: The Dynamic Workflow
            item {
                SimplifiedWorkflowDiagram(
                    result = result,
                    deviceIp = deviceIp,
                    language = language
                )
            }

            // Bottom Download Report Button for convenience after reviewing nodes
            if (isScanFinished) {
                item {
                    DownloadReportCard(
                        result = result,
                        language = language
                    )
                }
            }
        } else {
            // Empty / Initial State: Diagnostic Run Card with Quick and Full modes
            item {
                InitialInfographicPreviewCard(
                    deviceIp = deviceIp,
                    language = language,
                    onRunDiagnostic = onRunDiagnostic,
                    isLoading = isLoading,
                    progressPercent = progressPercent
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Intelligent Troubleshooting Notes Card (Warning / Info Banner)
// Strict Hierarchical Evaluation Logic:
// Step 1: Router Loss >= 3% OR ping failed -> Problem with Home Router
// Step 2: Router Good (< 3%) BUT Upstream Loss >= 3% OR ping failed -> Problem Router to Next Device
// Step 3: Router & Upstream Good (< 3%) BUT Internet Loss >= 3% OR ping failed -> Internet Issue (Contact ISP)
// Step 4: ALL Targets Good (< 3% loss) -> Network Completely Normal
// -------------------------------------------------------------------------
@Composable
fun TroubleshootingNoteCard(
    result: DiagnosticResult,
    language: AppLanguage
) {
    // 1. Step 1 (Router): Local default gateway health check
    val routerLoss = result.localGwStats.packetLossPercent
    val isRouterReachable = result.localGwStats.isReachable &&
            result.gateway1.isNotBlank() &&
            result.gateway1 != "0.0.0.0" &&
            !result.gateway1.equals("Not Connected", ignoreCase = true) &&
            !result.gateway1.equals("Unknown", ignoreCase = true) &&
            !(result.localGwStats.transmitted > 0 && result.localGwStats.received == 0)
    val isRouterProblematic = !isRouterReachable || routerLoss >= 3.0 || result.isSkippedDueToRouterFailure

    // 2. First Point of Failure Check for Middle Nodes (Node 3 to N with partial packet loss strictly >3.0% and <100.0%)
    val middleNodes = if (result.nodes.isNotEmpty()) {
        result.nodes.filter { it.stepNumber >= 3 && it.iconType != "internet" && it.title != "ইন্টারনেট" }
    } else {
        emptyList()
    }
    val firstFailingMiddleNode = middleNodes.firstOrNull { node ->
        node.packetLossPercent > 3.0 && node.packetLossPercent < 100.0
    }

    // Upstream statistics for metric pill inspection
    val upstreamStats = result.upstreamGwStats
    val upstreamLoss = upstreamStats?.packetLossPercent ?: 0.0
    val isUpstreamProblematic = firstFailingMiddleNode != null || if (upstreamStats != null) {
        !upstreamStats.isReachable || upstreamLoss > 3.0 || (upstreamStats.transmitted > 0 && upstreamStats.received == 0)
    } else {
        false
    }

    // 3. Step 3 (Internet): Internet targets (Primary 8.8.8.8 and Secondary 1.1.1.1)
    val primaryReachable = result.internetTargetStats.isReachable && !(result.internetTargetStats.transmitted > 0 && result.internetTargetStats.received == 0)
    val primaryLoss = result.internetTargetStats.packetLossPercent
    val secondaryReachable = result.secondaryTargetStats.isReachable && !(result.secondaryTargetStats.transmitted > 0 && result.secondaryTargetStats.received == 0)
    val secondaryLoss = result.secondaryTargetStats.packetLossPercent

    val isInternetReachable = primaryReachable || secondaryReachable
    val internetLoss = if (primaryReachable) {
        primaryLoss
    } else if (secondaryReachable) {
        secondaryLoss
    } else {
        100.0
    }
    val isInternetProblematic = !isInternetReachable || internetLoss >= 3.0

    // Strict hierarchical selection inside UI rendering
    val dynamicMessageText: String?
    val stringResId: Int
    val isSuccess: Boolean
    val bannerIcon: ImageVector
    val bannerStatusTag: String
    val bannerStatusLevel: String
    val stepLabel: String

    if (isRouterProblematic) {
        // Step 1: Router Packet Loss >= 3% OR ping failed completely
        stringResId = R.string.troubleshooting_router_issue
        dynamicMessageText = null
        isSuccess = false
        bannerIcon = Icons.Default.Router
        bannerStatusTag = if (language == AppLanguage.BN) "রাউটার সমস্যা" else "Router Issue"
        bannerStatusLevel = "red"
        stepLabel = if (language == AppLanguage.BN) "ধাপ ১: ডিভাইস ➔ হোম রাউটার" else "Step 1: Device ➔ Home Router"
    } else if (firstFailingMiddleNode != null) {
        // First Point of Failure: Middle node has partial packet loss (>3.0% and <100.0%)
        stringResId = 0
        dynamicMessageText = if (firstFailingMiddleNode.stepNumber == 3 || firstFailingMiddleNode.title.contains("পরবর্তি")) {
            if (language == AppLanguage.BN) {
                "আপনার হোম ওয়াইফাই রাউটারের পরের ডিভাইসে সমস্যা।"
            } else {
                "Problem with the device next to your home router."
            }
        } else {
            if (language == AppLanguage.BN) {
                "${firstFailingMiddleNode.title} এ সমস্যা।"
            } else {
                "Problem at ${firstFailingMiddleNode.title}."
            }
        }
        isSuccess = false
        bannerIcon = Icons.Default.Warning
        bannerStatusTag = if (language == AppLanguage.BN) "সমস্যা" else "Issue"
        bannerStatusLevel = "red"
        stepLabel = if (language == AppLanguage.BN) "ধাপ ${firstFailingMiddleNode.stepNumber}: ${firstFailingMiddleNode.title}" else "Step ${firstFailingMiddleNode.stepNumber}: ${firstFailingMiddleNode.title}"
    } else if (isInternetProblematic) {
        // Final Node: Internet Packet Loss >= 3% OR ping failed completely
        stringResId = R.string.troubleshooting_internet_issue
        dynamicMessageText = null
        isSuccess = false
        bannerIcon = Icons.Default.Cloud
        bannerStatusTag = if (language == AppLanguage.BN) "আইএসপি সমস্যা" else "ISP Issue"
        bannerStatusLevel = "red"
        stepLabel = if (language == AppLanguage.BN) "ধাপ ৩: ইন্টারনেট / আইএসপি" else "Step 3: Internet / ISP"
    } else {
        // Success: Home Router passed and Final Internet Node passed (middle ICMP blocks do NOT fail overall network)
        stringResId = R.string.troubleshooting_success
        dynamicMessageText = null
        isSuccess = true
        bannerIcon = Icons.Default.CheckCircle
        bannerStatusTag = if (language == AppLanguage.BN) "স্বাভাবিক" else "Normal"
        bannerStatusLevel = "green"
        stepLabel = if (language == AppLanguage.BN) "সংযোগ সম্পূর্ণ স্বাভাবিক" else "All Targets Normal"
    }

    // Resolve localized strings dynamically from Android String Resources or dynamic first point of failure text
    val context = LocalContext.current
    val messageText = dynamicMessageText ?: remember(stringResId, language) {
        try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale(language.code))
            val localizedContext = context.createConfigurationContext(config)
            localizedContext.resources.getString(stringResId)
        } catch (e: Exception) {
            context.getString(stringResId)
        }
    }

    val titleText = remember(language) {
        try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale(language.code))
            val localizedContext = context.createConfigurationContext(config)
            localizedContext.resources.getString(R.string.troubleshooting_title)
        } catch (e: Exception) {
            context.getString(R.string.troubleshooting_title)
        }
    }

    val accentColor = if (isSuccess) AppColors.green else AppColors.red
    val accentBg = if (isSuccess) AppColors.greenSoft else AppColors.redSoft
    val accentBorder = if (isSuccess) AppColors.greenSoftBorder else AppColors.redSoftBorder

    CardContainer(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("troubleshooting_note_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Title, Step Subtitle, and Status Tag
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
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = titleText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.ink
                        )
                        Text(
                            text = stepLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.inkMuted
                        )
                    }
                }

                StatusTag(
                    text = bannerStatusTag,
                    level = bannerStatusLevel
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Message Banner Box
            Surface(
                color = accentBg,
                shape = RoundedCornerShape(AppRadius.md),
                border = BorderStroke(1.dp, accentBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("troubleshooting_message_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = bannerIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = messageText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.ink,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry Metric Chips for Transparent Loss Inspection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TroubleshootingMetricPill(
                    modifier = Modifier.weight(1f),
                    label = Translations.tr("homeRouter", language),
                    value = if (isRouterReachable) "${"%.1f".format(routerLoss)}%" else "100%",
                    isProblem = isRouterProblematic
                )

                TroubleshootingMetricPill(
                    modifier = Modifier.weight(1f),
                    label = Translations.tr("upstreamGateway", language),
                    value = if (upstreamStats != null) {
                        if (upstreamStats.isReachable) "${"%.1f".format(upstreamLoss)}%" else "100%"
                    } else {
                        "N/A"
                    },
                    isProblem = isUpstreamProblematic
                )

                TroubleshootingMetricPill(
                    modifier = Modifier.weight(1f),
                    label = Translations.tr("internet", language),
                    value = if (isInternetReachable) "${"%.1f".format(internetLoss)}%" else "100%",
                    isProblem = isInternetProblematic
                )
            }
        }
    }
}

@Composable
private fun TroubleshootingMetricPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isProblem: Boolean
) {
    val pillColor = if (isProblem) AppColors.red else AppColors.green
    val pillBg = if (isProblem) AppColors.redSoft else AppColors.greenSoft

    Surface(
        modifier = modifier,
        color = pillBg,
        shape = RoundedCornerShape(AppRadius.sm)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.inkMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = pillColor
            )
        }
    }
}

/**
 * Formats diagnostic result into standard terminal ping format.
 */
fun generateDiagnosticReportText(result: DiagnosticResult): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val now = dateFormat.format(Date())
    val sb = StringBuilder()

    sb.appendLine("=========================================")
    sb.appendLine("YT Analyzer Network Diagnostic Report")
    sb.appendLine("Timestamp: $now")
    sb.appendLine("=========================================")
    sb.appendLine()

    val scannedNodes = if (result.nodes.isNotEmpty()) {
        result.nodes.filter { !it.isSkipped }
    } else {
        emptyList()
    }

    scannedNodes.forEach { node ->
        val nodeStatus = when {
            node.isIcmpBlocked || node.statusText.contains("ICMP") -> "ICMP বন্ধ আছে"
            node.statusLevel == "red" || node.statusText == "সমস্যা" || node.packetLossPercent > 3.0 -> "সমস্যা"
            else -> "ভালো"
        }

        val totalTransmitted = if (node.transmitted > 0) node.transmitted else if (result.mode == DiagnosticMode.FULL) 20 else 5
        val received = if (node.isIcmpBlocked || !node.isReachable) 0 else {
            if (node.received in 0..totalTransmitted && node.transmitted > 0) {
                node.received
            } else {
                Math.round(totalTransmitted.toDouble() * (100.0 - node.packetLossPercent) / 100.0).toInt().coerceIn(0, totalTransmitted)
            }
        }
        val lossText = if (node.packetLossPercent % 1.0 == 0.0) {
            "${node.packetLossPercent.toInt()}%"
        } else {
            "${"%.1f".format(Locale.US, node.packetLossPercent)}%"
        }

        val minRtt = if (node.isIcmpBlocked || !node.isReachable) "0.0" else "%.1f".format(Locale.US, if (node.minMs > 0.0) node.minMs else (node.latencyMs ?: 0.0))
        val avgRtt = if (node.isIcmpBlocked || !node.isReachable) "0.0" else "%.1f".format(Locale.US, node.latencyMs ?: 0.0)
        val maxRtt = if (node.isIcmpBlocked || !node.isReachable) "0.0" else "%.1f".format(Locale.US, if (node.maxMs > 0.0) node.maxMs else (node.latencyMs ?: 0.0))

        sb.appendLine("Node Name: ${node.title}")
        sb.appendLine("IP Address: ${node.ip}")
        sb.appendLine("Status: $nodeStatus")
        sb.appendLine("--- ping statistics ---")
        sb.appendLine("$totalTransmitted packets transmitted, $received received, $lossText packet loss")
        sb.appendLine("rtt min/avg/max = $minRtt/$avgRtt/$maxRtt ms")
        sb.appendLine("-----------------------------------------")
    }

    return sb.toString()
}

/**
 * Writes the report string into the target SAF Uri via ContentResolver OutputStream.
 */
suspend fun saveReportToUri(context: android.content.Context, uri: Uri, reportContent: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(reportContent.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun DownloadReportCard(
    result: DiagnosticResult,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val reportContent = generateDiagnosticReportText(result)
                val success = saveReportToUri(context, uri, reportContent)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(
                            context,
                            if (language == AppLanguage.BN) "রিপোর্ট সফলভাবে সংরক্ষণ করা হয়েছে" else "Report saved successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            if (language == AppLanguage.BN) "রিপোর্ট সংরক্ষণে ত্রুটি হয়েছে" else "Failed to save report",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(AppRadius.lg),
                spotColor = AppColors.primary
            )
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(
                Brush.horizontalGradient(
                    listOf(AppColors.gradientStart, AppColors.gradientEnd)
                )
            )
            .clickable {
                val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val defaultFileName = "YT_Analyzer_Report_${timeStamp}.txt"
                createDocumentLauncher.launch(defaultFileName)
            }
            .padding(horizontal = AppSpacing.lg, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = if (language == AppLanguage.BN) "রিপোর্ট ডাউনলোড করুন" else "Download Report",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Text(
                text = if (language == AppLanguage.BN) "রিপোর্ট ডাউনলোড করুন" else "Download Report",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DiagnosticActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(AppRadius.pill),
                spotColor = AppColors.primary
            )
            .clip(RoundedCornerShape(AppRadius.pill))
            .background(
                Brush.linearGradient(
                    listOf(AppColors.gradientStart, AppColors.gradientEnd)
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
    }
}

// -------------------------------------------------------------------------
// 1. Infographic Header Card
// -------------------------------------------------------------------------
@Composable
private fun InfographicHeaderCard(
    isLoading: Boolean,
    progressPercent: Int = 0,
    language: AppLanguage,
    onRunDiagnostic: (DiagnosticMode) -> Unit
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
                            "ডায়নামিক নেটওয়ার্ক পাথ বিশ্লেষণ ও ডায়াগনস্টিক"
                        else
                            "Dynamic Network Path Analysis & Diagnostics",
                        fontSize = 12.sp,
                        color = AppColors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
                // Percentage Progress counter replacing generic loading
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadius.pill))
                        .background(AppColors.surfaceAlt)
                        .border(1.dp, AppColors.primary.copy(alpha = 0.3f), RoundedCornerShape(AppRadius.pill))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = Translations.tr("running", language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.primary
                        )
                        Text(
                            text = "$progressPercent%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppColors.primary
                        )
                    }
                }
            } else {
                // Quick Diagnostic & Full Diagnostic Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DiagnosticActionButton(
                        text = Translations.tr("quickDiagnostic", language),
                        onClick = { onRunDiagnostic(DiagnosticMode.QUICK) },
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticActionButton(
                        text = Translations.tr("fullDiagnostic", language),
                        onClick = { onRunDiagnostic(DiagnosticMode.FULL) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
                                color = resolvePingLatencyColor("", result.gateway2Latency)
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
// 1. Device -> 2. Home Wifi Router -> 3. Upstream Gateway 1 -> 4. Upstream Gateway 2 -> 5. Internet
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

        val nodesToDisplay = if (result.nodes.isNotEmpty()) {
            result.nodes
        } else {
            listOf(
                DiagnosticWorkflowNode(
                    stepNumber = 1,
                    title = "আপনার ডিভাইস",
                    ip = deviceIp.ifBlank { "127.0.0.1" },
                    latencyText = "0.0 ms",
                    latencyMs = 0.0,
                    packetLossPercent = 0.0,
                    packetLossText = "0.0%",
                    isReachable = true,
                    statusText = "স্বাভাবিক",
                    statusLevel = "green",
                    iconType = "device"
                ),
                DiagnosticWorkflowNode(
                    stepNumber = 2,
                    title = "হোম ওয়াইফাই রাউটার",
                    ip = result.gateway1.ifBlank { "192.168.0.1" },
                    latencyText = if (result.localGwStats.isReachable) "${"%.1f".format(result.localGwStats.avgMs)} ms" else "অপেক্ষমান",
                    latencyMs = if (result.localGwStats.isReachable) result.localGwStats.avgMs else null,
                    packetLossPercent = result.localGwStats.packetLossPercent,
                    packetLossText = if (result.localGwStats.isReachable) "${"%.1f".format(result.localGwStats.packetLossPercent)}%" else "0.0%",
                    isReachable = result.localGwStats.isReachable,
                    statusText = if (result.localGwStats.isReachable) "স্বাভাবিক" else "প্রস্তুত",
                    statusLevel = if (result.localGwStats.isReachable) "green" else "gray",
                    iconType = "router"
                ),
                DiagnosticWorkflowNode(
                    stepNumber = 3,
                    title = "পরবর্তি ডিভাইস",
                    ip = "Unknown",
                    latencyText = "অপেক্ষমান",
                    packetLossText = "0.0%",
                    statusText = "প্রস্তুত",
                    statusLevel = "gray",
                    iconType = "device_next"
                ),
                DiagnosticWorkflowNode(
                    stepNumber = 4,
                    title = "আপস্ট্রিম গেটওয়ে ১",
                    ip = "Unknown",
                    latencyText = "অপেক্ষমান",
                    packetLossText = "0.0%",
                    statusText = "প্রস্তুত",
                    statusLevel = "gray",
                    iconType = "gateway"
                ),
                DiagnosticWorkflowNode(
                    stepNumber = 5,
                    title = "আপস্ট্রিম গেটওয়ে ২",
                    ip = "Unknown",
                    latencyText = "অপেক্ষমান",
                    packetLossText = "0.0%",
                    statusText = "প্রস্তুত",
                    statusLevel = "gray",
                    iconType = "gateway"
                ),
                DiagnosticWorkflowNode(
                    stepNumber = 6,
                    title = "ইন্টারনেট",
                    ip = "8.8.8.8",
                    latencyText = "অপেক্ষমান",
                    packetLossText = "0.0%",
                    statusText = "প্রস্তুত",
                    statusLevel = "gray",
                    iconType = "internet"
                )
            )
        }

        nodesToDisplay.forEachIndexed { index, node ->
            if (index > 0) {
                WorkflowArrowConnector()
            }

            val icon = when (node.iconType) {
                "device" -> Icons.Default.Smartphone
                "router" -> Icons.Default.Router
                "device_next" -> Icons.Default.Public
                "gateway" -> Icons.Default.Hub
                "internet" -> Icons.Default.Cloud
                else -> Icons.Default.Router
            }

            val (themeTint, themeBg) = when (node.iconType) {
                "device" -> Color(0xFF0284C7) to Color(0xFFE0F2FE)
                "router" -> Color(0xFF0D9488) to Color(0xFFCCFBF1)
                "device_next" -> Color(0xFF2563EB) to Color(0xFFDBEAFE)
                "gateway" -> Color(0xFF8B5CF6) to Color(0xFFF3E8FF)
                "internet" -> Color(0xFF10B981) to Color(0xFFD1FAE5)
                else -> Color(0xFF64748B) to Color(0xFFF1F5F9)
            }

            val isMiddleNode = node.stepNumber >= 3 && node.iconType != "internet" && node.title != "ইন্টারনেট"
            val hasPartialLoss = isMiddleNode && node.packetLossPercent > 3.0 && node.packetLossPercent < 100.0
            val effectiveStatusText = if (hasPartialLoss) "সমস্যা" else node.statusText
            val effectiveStatusLevel = if (hasPartialLoss) "red" else node.statusLevel

            WorkflowHopCard(
                stepNumber = node.stepNumber,
                title = node.title,
                icon = icon,
                ip = node.ip,
                latencyText = node.latencyText,
                latencyMs = node.latencyMs,
                packetLossText = node.packetLossText,
                statusText = effectiveStatusText,
                statusLevel = effectiveStatusLevel,
                themeTint = themeTint,
                themeBg = themeBg
            )
        }
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

private fun resolvePingLatencyColor(latencyText: String, latencyMs: Double? = null): Color {
    val green = Color(0xFF10B981) // Green (#10B981 / #4CAF50)
    val amber = Color(0xFFF59E0B) // Amber/Orange (#F59E0B)
    val red = Color(0xFFEF4444)   // Red (#EF4444)
    val gray = Color(0xFF94A3B8)

    val lower = latencyText.lowercase().trim()
    if (lower.contains("icmp") || lower.contains("বন্ধ") || lower.contains("block")) {
        return amber
    }
    if (lower.contains("ready") || lower.contains("অপেক্ষমান") || lower.contains("প্রস্তুত")) {
        return gray
    }
    if (lower.contains("timeout") || lower.contains("fail") || lower.contains("problem") || lower.contains("unknown") || lower.contains("অজানা")) {
        return red
    }
    if (lower.startsWith("<")) {
        return green
    }

    val msValue = latencyMs ?: lower.replace("ms", "").trim().toDoubleOrNull()
    return when {
        msValue == null -> red
        msValue <= 30.0 -> green
        msValue <= 80.0 -> amber
        else -> red
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
    statusLevel: String,
    themeTint: Color = AppColors.primary,
    themeBg: Color = AppColors.primarySoft,
    latencyMs: Double? = null
) {
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
            // Top Row: Distinctly Themed Icon + Title + Status Tag
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(themeBg)
                            .border(1.dp, themeTint.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = themeTint,
                            modifier = Modifier.size(18.dp)
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

            // Details Grid: IP Address, Dynamic Ping Latency, Packet Loss
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
                        color = resolvePingLatencyColor(latencyText, latencyMs)
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
                            statusLevel == "amber" || statusText.contains("ICMP") || statusText.contains("বন্ধ") -> Color(0xFFD97706)
                            statusLevel == "gray" -> AppColors.inkMuted
                            packetLossText.contains("100") || packetLossText.contains("Unknown") || packetLossText.contains("অজানা") -> AppColors.red
                            (packetLossText.replace("%", "").trim().toDoubleOrNull() ?: 0.0) > 3.0 -> AppColors.red
                            else -> AppColors.green
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
    onRunDiagnostic: (DiagnosticMode) -> Unit,
    isLoading: Boolean,
    progressPercent: Int = 0
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
                            "ডায়নামিক নেটওয়ার্ক পাথ বিশ্লেষণ ও ডায়াগনস্টিক"
                        else
                            "Dynamic Network Path Analysis & Diagnostics",
                        fontSize = 12.sp,
                        color = AppColors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (language == AppLanguage.BN)
                    "ডিভাইস ➔ হোম ওয়াইফাই রাউটার ➔ আপস্ট্রিম গেটওয়ে ১ ➔ আপস্ট্রিম গেটওয়ে ২ ➔ ইন্টারনেট এর লাইভ পাথ মানচিত্র দেখতে ডায়াগনস্টিক চালান"
                else
                    "Run diagnostic to generate an infographic map of Device ➔ Home Wifi Router ➔ Upstream Gateway 1 ➔ Upstream Gateway 2 ➔ Internet",
                fontSize = 12.sp,
                color = AppColors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5-Node Topology preview sketch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.md))
                    .background(AppColors.surfaceAlt)
                    .border(1.dp, AppColors.border, RoundedCornerShape(AppRadius.md))
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewNodeIcon(Icons.Default.Smartphone, "Device", Color(0xFF0284C7))
                Text("➔", color = AppColors.inkMuted, fontSize = 12.sp)
                PreviewNodeIcon(Icons.Default.Router, "Router", Color(0xFF0D9488))
                Text("➔", color = AppColors.inkMuted, fontSize = 12.sp)
                PreviewNodeIcon(Icons.Default.Public, "GW 1", Color(0xFF6366F1))
                Text("➔", color = AppColors.inkMuted, fontSize = 12.sp)
                PreviewNodeIcon(Icons.Default.Hub, "GW 2", Color(0xFF8B5CF6))
                Text("➔", color = AppColors.inkMuted, fontSize = 12.sp)
                PreviewNodeIcon(Icons.Default.Cloud, "Internet", Color(0xFF10B981))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadius.pill))
                        .background(AppColors.surfaceAlt)
                        .border(1.dp, AppColors.primary.copy(alpha = 0.3f), RoundedCornerShape(AppRadius.pill))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = Translations.tr("running", language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.primary
                        )
                        Text(
                            text = "$progressPercent%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppColors.primary
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DiagnosticActionButton(
                        text = Translations.tr("quickDiagnostic", language),
                        onClick = { onRunDiagnostic(DiagnosticMode.QUICK) },
                        modifier = Modifier.weight(1f)
                    )
                    DiagnosticActionButton(
                        text = Translations.tr("fullDiagnostic", language),
                        onClick = { onRunDiagnostic(DiagnosticMode.FULL) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
