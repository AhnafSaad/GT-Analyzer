package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
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
import androidx.compose.material.icons.filled.Hub
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.model.GlobalThresholds
import com.example.model.PingMetrics
import com.example.model.WifiInfoData
import com.example.ui.components.CardContainer
import com.example.ui.components.LiveLineChart
import com.example.ui.components.PrimaryButton
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
    onRefresh: () -> Unit,
    onNavigateToDiagnostic: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. Initial State Check: Initialize actively with system permission state.
    // If true on launch, the banner will never render.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 2. Reactive Update: Observe lifecycle ON_RESUME so that if permission is granted
    // at startup or via system settings, the banner hides immediately.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Also update if wifiInfo refreshes following an external permission grant
    LaunchedEffect(wifiInfo) {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
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

    // 2. Reactive Update: Immediately update state when user grants permission via the button
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val isGranted = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = isGranted
        if (isGranted) {
            onRefresh()
        }
    }

    // Aggressive Real-Time WiFi RSSI (dBm) State & History
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }
    var currentDbm by remember(wifiInfo.rssiDbm) { mutableIntStateOf(wifiInfo.rssiDbm) }
    val dbmHistory = remember {
        mutableStateListOf<Double>().apply {
            if (pingMetrics.dbmHistory.isNotEmpty()) {
                addAll(pingMetrics.dbmHistory.map { it.toDouble() })
            } else {
                repeat(15) { add(wifiInfo.rssiDbm.toDouble()) }
            }
        }
    }

    // Lifecycle-Aware Aggressive Polling Loop (200ms delay ~ 5 times/sec)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val rawRssi = try {
                    @Suppress("DEPRECATION")
                    wifiManager?.connectionInfo?.rssi
                } catch (e: Exception) {
                    null
                }

                val validRssi = if (rawRssi != null && rawRssi != 0 && rawRssi != -127) {
                    rawRssi
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                    (caps?.transportInfo as? android.net.wifi.WifiInfo)?.rssi?.takeIf { it != 0 && it != -127 }
                } else {
                    null
                }

                if (validRssi != null) {
                    currentDbm = validRssi
                    dbmHistory.add(validRssi.toDouble())
                    if (dbmHistory.size > 20) {
                        dbmHistory.removeAt(0)
                    }
                }
                delay(200L)
            }
        }
    }

    val displayDbm = if (currentDbm != 0 && currentDbm != -127) currentDbm else wifiInfo.rssiDbm

    // Dynamic Overall Status & Color mapping based on real-time dBm threshold rules:
    // 1. Excellent: >= -50 dBm -> Green (#10B981)
    // 2. Fair / Moderate: between -50 dBm and -65 dBm -> Amber / Orange (#F59E0B)
    // 3. Weak / Poor: < -65 dBm -> Red (#EF4444)
    val (statusTitle, statusColor, statusLevel) = when {
        displayDbm >= -50 -> Triple(
            if (language == AppLanguage.BN) "সার্বিক অবস্থা: চমৎকার" else "Overall Status: Excellent",
            Color(0xFF10B981),
            "green"
        )
        displayDbm >= -65 -> Triple(
            if (language == AppLanguage.BN) "সার্বিক অবস্থা: মোটামুটি ভালো" else "Overall Status: Fair",
            Color(0xFFF59E0B),
            "yellow"
        )
        else -> Triple(
            if (language == AppLanguage.BN) "সার্বিক অবস্থা: দুর্বল" else "Overall Status: Weak",
            Color(0xFFEF4444),
            "red"
        )
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

        // 1. Signal Gauge & Verdict Section with Diagnostic Shortcut
        item {
            CardContainer {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SignalGauge(
                        dbm = displayDbm,
                        label = Translations.tr("signalStrength", language)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    VerdictBadge(
                        title = statusTitle,
                        scoreText = "$displayDbm dBm • ${"%.1f".format(pingMetrics.currentPingMs)} ms",
                        level = statusLevel,
                        customColor = statusColor
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PrimaryButton(
                        text = Translations.tr("runDiagnosticShortcut", language),
                        onClick = onNavigateToDiagnostic,
                        modifier = Modifier.fillMaxWidth()
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
                        value = "$displayDbm dBm"
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
                val pingLatency = pingMetrics.currentPingMs
                val pingColor = when {
                    pingLatency <= 30.0 -> Color(0xFF10B981) // Green
                    pingLatency <= 80.0 -> Color(0xFFF59E0B) // Amber/Orange
                    else -> Color(0xFFEF4444)                // Red
                }
                MetricMiniCard(
                    title = Translations.tr("ping", language),
                    value = "${"%.1f".format(pingLatency)}",
                    unit = Translations.tr("ms", language),
                    status = if (pingLatency <= 30.0) "green" else if (pingLatency <= 80.0) "yellow" else "red",
                    valueColor = pingColor,
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
                data = dbmHistory,
                unit = "dBm",
                lineColor = statusColor
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
    modifier: Modifier = Modifier,
    valueColor: Color? = null
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
                Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = valueColor ?: AppColors.ink)
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = unit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.inkSoft, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}
