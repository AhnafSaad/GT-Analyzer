package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.model.GlobalThresholds
import com.example.model.PingMetrics
import com.example.model.WifiInfoData
import com.example.ui.components.CardContainer
import com.example.ui.components.LiveLineChart
import com.example.ui.components.SignalGauge
import com.example.ui.components.VerdictBadge
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing

@Composable
fun DashboardScreen(
    wifiInfo: WifiInfoData,
    pingMetrics: PingMetrics,
    thresholds: GlobalThresholds,
    language: AppLanguage,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = granted
        if (granted) onRefresh()
    }

    // Determine verdict
    val dbmOk = wifiInfo.rssiDbm >= thresholds.dbmGoodMin
    val pingOk = pingMetrics.currentPingMs <= thresholds.pingGoodMax
    val jitterOk = pingMetrics.jitterMs <= thresholds.jitterGoodMax
    val lossOk = pingMetrics.packetLossPercent <= thresholds.packetLossGoodMax

    val (verdictTitleKey, verdictLevel) = when {
        dbmOk && pingOk && jitterOk && lossOk -> Pair("excellent", "green")
        (wifiInfo.rssiDbm >= thresholds.dbmFairMin) && (pingMetrics.currentPingMs <= thresholds.pingFairMax) -> Pair("good", "green")
        pingMetrics.currentPingMs <= thresholds.pingFairMax * 1.5 -> Pair("fair", "yellow")
        else -> Pair("poor", "red")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        contentPadding = PaddingValues(top = AppSpacing.md, bottom = 100.dp)
    ) {
        // Location permission notice if needed
        if (!hasLocationPermission) {
            item {
                Surface(
                    color = AppColors.yellowSoft,
                    shape = RoundedCornerShape(AppRadius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppColors.yellowSoftBorder, RoundedCornerShape(AppRadius.md))
                ) {
                    Row(
                        modifier = Modifier.padding(AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = AppColors.yellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Translations.tr("locationPermissionRequired", language),
                                fontSize = 12.sp,
                                color = AppColors.ink,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppRadius.pill))
                                .background(AppColors.primary)
                                .clickable { permissionLauncher.launch(permissionsToRequest) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = Translations.tr("grantPermission", language),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.white
                            )
                        }
                    }
                }
            }
        }

        // 1. Signal Gauge & Verdict Section
        item {
            CardContainer {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SignalGauge(
                        dbm = wifiInfo.rssiDbm,
                        label = Translations.tr("signalStrength", language)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    VerdictBadge(
                        title = "${Translations.tr("verdict", language)}: ${Translations.tr(verdictTitleKey, language)}",
                        scoreText = "${wifiInfo.rssiDbm} dBm • ${"%.1f".format(pingMetrics.currentPingMs)} ms",
                        level = verdictLevel
                    )
                }
            }
        }

        // 2. Connected WiFi Details Card
        item {
            CardContainer {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppColors.primarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = AppColors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Translations.tr("connectedWifi", language),
                            fontSize = 11.sp,
                            color = AppColors.inkMuted,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = wifiInfo.ssid,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.ink
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AppColors.greenSoft)
                            .border(1.dp, AppColors.greenSoftBorder, RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = wifiInfo.band,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.green
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Network details grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricChip(
                        label = Translations.tr("linkSpeed", language),
                        value = "${wifiInfo.linkSpeedMbps} Mbps"
                    )
                    MetricChip(
                        label = Translations.tr("frequency", language),
                        value = "${wifiInfo.frequencyMhz} MHz"
                    )
                    MetricChip(
                        label = Translations.tr("localGateway", language),
                        value = wifiInfo.gatewayIp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricChip(
                        label = Translations.tr("ipAddress", language),
                        value = wifiInfo.ipAddress
                    )
                    MetricChip(
                        label = "BSSID",
                        value = wifiInfo.bssid
                    )
                    MetricChip(
                        label = Translations.tr("signalStrength", language),
                        value = "${wifiInfo.rssiDbm} dBm"
                    )
                }
            }
        }

        // 3. Live Metrics Row (Ping, Jitter, Packet Loss)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                MetricMiniCard(
                    title = Translations.tr("ping", language),
                    value = "${"%.1f".format(pingMetrics.currentPingMs)}",
                    unit = Translations.tr("ms", language),
                    status = if (pingMetrics.currentPingMs <= thresholds.pingGoodMax) "green" else if (pingMetrics.currentPingMs <= thresholds.pingFairMax) "yellow" else "red",
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = Translations.tr("jitter", language),
                    value = "${"%.1f".format(pingMetrics.jitterMs)}",
                    unit = Translations.tr("ms", language),
                    status = if (pingMetrics.jitterMs <= thresholds.jitterGoodMax) "green" else if (pingMetrics.jitterMs <= thresholds.jitterFairMax) "yellow" else "red",
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = Translations.tr("packetLoss", language),
                    value = "${"%.1f".format(pingMetrics.packetLossPercent)}",
                    unit = "%",
                    status = if (pingMetrics.packetLossPercent <= thresholds.packetLossGoodMax) "green" else "red",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Live Line Chart (Ping & dBm History)
        item {
            LiveLineChart(
                title = "${Translations.tr("ping", language)} ${Translations.tr("history", language)}",
                data = pingMetrics.pingHistory,
                unit = Translations.tr("ms", language),
                lineColor = AppColors.primary
            )
        }

        item {
            LiveLineChart(
                title = "${Translations.tr("signalStrength", language)} ${Translations.tr("history", language)}",
                data = pingMetrics.dbmHistory.map { it.toDouble() },
                unit = "dBm",
                lineColor = AppColors.green
            )
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = AppColors.inkMuted, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 13.sp, color = AppColors.ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    unit: String,
    status: String,
    modifier: Modifier = Modifier
) {
    val levelColor = when (status) {
        "green" -> AppColors.green
        "yellow" -> AppColors.yellow
        else -> AppColors.red
    }

    Surface(
        shape = RoundedCornerShape(AppRadius.md),
        color = AppColors.surface,
        modifier = modifier
            .border(1.dp, AppColors.border, RoundedCornerShape(AppRadius.md))
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(levelColor)
                )
                Text(text = title, fontSize = 11.sp, color = AppColors.inkMuted, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.ink)
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = unit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.inkSoft, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}
