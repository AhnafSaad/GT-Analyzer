package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.model.NearbyWifiNetwork
import com.example.ui.components.CardContainer
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing

@Composable
fun NearbyNetworksScreen(
    networks: List<NearbyWifiNetwork>,
    isScanning: Boolean,
    language: AppLanguage,
    onScan: () -> Unit
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
        if (granted) onScan()
    }

    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        contentPadding = PaddingValues(top = AppSpacing.md, bottom = 100.dp)
    ) {
        // Location Permission Banner if not granted
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = Translations.tr("nearby", language),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                    Text(
                        text = "${networks.size} networks discovered",
                        fontSize = 12.sp,
                        color = AppColors.inkMuted
                    )
                }

                Box(modifier = Modifier.width(150.dp)) {
                    PrimaryButton(
                        text = if (isScanning) Translations.tr("scanning", language) else Translations.tr("scanNetworks", language),
                        onClick = {
                            if (!hasLocationPermission) {
                                permissionLauncher.launch(permissionsToRequest)
                            } else {
                                onScan()
                            }
                        },
                        isLoading = isScanning,
                        modifier = Modifier.height(42.dp)
                    )
                }
            }
        }

        if (networks.isEmpty() && !isScanning) {
            item {
                CardContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = AppColors.inkMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.sm))
                        Text(
                            text = Translations.tr("noNetworksFound", language),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.inkSoft
                        )
                    }
                }
            }
        }

        items(networks, key = { it.bssid + it.ssid }) { network ->
            NearbyNetworkItem(network = network)
        }
    }
}

@Composable
private fun NearbyNetworkItem(network: NearbyWifiNetwork) {
    val barColor = when (network.signalBars) {
        4 -> AppColors.green
        3 -> AppColors.green
        2 -> AppColors.yellow
        else -> AppColors.red
    }

    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                modifier = Modifier.weight(1f)
            ) {
                // Signal Bars indicator (1 to 4 bars)
                SignalBarsIndicator(bars = network.signalBars, activeColor = barColor)

                Column {
                    Text(
                        text = network.ssid,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = network.bssid,
                            fontSize = 11.sp,
                            color = AppColors.inkMuted,
                            fontWeight = FontWeight.Medium
                        )
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(AppColors.border))
                        Text(
                            text = "${network.frequencyMhz} MHz",
                            fontSize = 11.sp,
                            color = AppColors.inkSoft,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (network.capabilities == "Open") Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (network.capabilities == "Open") AppColors.inkMuted else AppColors.inkSoft,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = network.capabilities,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.inkSoft
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.primarySoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = network.band,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.primary
                        )
                    }

                    Text(
                        text = "${network.rssiDbm} dBm",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalBarsIndicator(bars: Int, activeColor: Color) {
    Row(
        modifier = Modifier
            .width(24.dp)
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val barHeights = listOf(6.dp, 10.dp, 15.dp, 20.dp)
        for (i in 0 until 4) {
            val isActive = i < bars
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeights[i])
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isActive) activeColor else AppColors.border)
            )
        }
    }
}
