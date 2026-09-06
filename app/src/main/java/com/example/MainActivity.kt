package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AppHeader
import com.example.ui.components.AppTab
import com.example.ui.components.FloatingTabBar
import com.example.ui.components.ServerConfigDialog
import com.example.ui.screens.*
import com.example.ui.theme.AppColors
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()

                val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
                val language by viewModel.language.collectAsStateWithLifecycle()
                val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                val backendConnected by viewModel.backendConnected.collectAsStateWithLifecycle()

                val wifiInfo by viewModel.wifiInfo.collectAsStateWithLifecycle()
                val pingMetrics by viewModel.pingMetrics.collectAsStateWithLifecycle()
                val dnsResolvers by viewModel.dnsResolvers.collectAsStateWithLifecycle()
                val nearbyNetworks by viewModel.nearbyNetworks.collectAsStateWithLifecycle()
                val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
                val speedTestState by viewModel.speedTestState.collectAsStateWithLifecycle()
                val diagnosticResult by viewModel.diagnosticResult.collectAsStateWithLifecycle()
                val isDiagnosticLoading by viewModel.isDiagnosticLoading.collectAsStateWithLifecycle()
                val showServerDialog by viewModel.showServerDialog.collectAsStateWithLifecycle()

                // Automatic Location Permission Popup on App Start
                val context = LocalContext.current
                val locationPermissions = remember {
                    buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }
                    }.toTypedArray()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    // Once user responds to permission popup, reload network information
                    viewModel.refreshAll()
                }

                LaunchedEffect(Unit) {
                    val hasLocationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasLocationPermission) {
                        permissionLauncher.launch(locationPermissions)
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.bg),
                    containerColor = AppColors.bg,
                    topBar = {
                        AppHeader(
                            language = language,
                            onToggleLanguage = { viewModel.toggleLanguage() },
                            onReload = { viewModel.refreshAll() },
                            onOpenServerConfig = { viewModel.openServerDialog() },
                            isRefreshing = isRefreshing,
                            backendConnected = backendConnected
                        )
                    },
                    bottomBar = {
                        FloatingTabBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.selectTab(it) },
                            language = language
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(
                            targetState = currentTab,
                            label = "tabCrossfade"
                        ) { tab ->
                            when (tab) {
                                AppTab.DASHBOARD -> {
                                    DashboardScreen(
                                        wifiInfo = wifiInfo,
                                        pingMetrics = pingMetrics,
                                        thresholds = viewModel.configRepo.thresholds,
                                        language = language,
                                        onRefresh = { viewModel.refreshAll() }
                                    )
                                }
                                AppTab.DNS -> {
                                    DnsCheckScreen(
                                        resolvers = dnsResolvers,
                                        thresholds = viewModel.configRepo.thresholds,
                                        language = language
                                    )
                                }
                                AppTab.NEARBY -> {
                                    NearbyNetworksScreen(
                                        networks = nearbyNetworks,
                                        isScanning = isScanning,
                                        language = language,
                                        onScan = { viewModel.scanNearbyNetworks() }
                                    )
                                }
                                AppTab.SPEED -> {
                                    SpeedTestScreen(
                                        state = speedTestState,
                                        language = language,
                                        onStartTest = { viewModel.startSpeedTest() },
                                        onStopTest = { viewModel.stopSpeedTest() }
                                    )
                                }
                                AppTab.DIAGNOSTIC -> {
                                    DiagnosticScreen(
                                        result = diagnosticResult,
                                        isLoading = isDiagnosticLoading,
                                        deviceIp = wifiInfo.ipAddress,
                                        backendUrl = viewModel.configRepo.apiBaseUrl,
                                        language = language,
                                        onRunDiagnostic = { viewModel.runDiagnostic() },
                                        onOpenServerConfig = { viewModel.openServerDialog() }
                                    )
                                }
                            }
                        }
                    }
                }

                if (showServerDialog) {
                    ServerConfigDialog(
                        currentUrl = viewModel.configRepo.apiBaseUrl,
                        language = language,
                        onSave = { viewModel.saveServerUrl(it) },
                        onDismiss = { viewModel.closeServerDialog() }
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    androidx.compose.material3.Text(text = "Hello $name!")
}

