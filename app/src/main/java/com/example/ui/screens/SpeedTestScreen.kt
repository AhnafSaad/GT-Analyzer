package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.model.SpeedPhase
import com.example.model.SpeedTestState
import com.example.ui.components.CardContainer
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SpeedGauge
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing

@Composable
fun SpeedTestScreen(
    state: SpeedTestState,
    language: AppLanguage,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit
) {
    val phaseLabel = when (state.phase) {
        SpeedPhase.IDLE -> Translations.tr("startTest", language)
        SpeedPhase.PING -> "${Translations.tr("ping", language)}..."
        SpeedPhase.DOWNLOAD -> "${Translations.tr("download", language)}..."
        SpeedPhase.UPLOAD -> "${Translations.tr("upload", language)}..."
        SpeedPhase.FINISHED -> "Complete"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        contentPadding = PaddingValues(top = AppSpacing.md, bottom = 100.dp)
    ) {
        // Speed Gauge Card
        item {
            CardContainer {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SpeedGauge(
                        currentMbps = state.currentMbps,
                        label = phaseLabel,
                        maxExpectedMbps = 100.0
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.isRunning) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(AppRadius.pill)),
                                color = AppColors.primary,
                                trackColor = AppColors.border
                            )
                            Text(
                                text = "${(state.progress * 100).toInt()}% • $phaseLabel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    PrimaryButton(
                        text = if (state.isRunning) Translations.tr("stopTest", language) else Translations.tr("startTest", language),
                        onClick = if (state.isRunning) onStopTest else onStartTest
                    )
                }
            }
        }

        // Live Results Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                SpeedMetricCard(
                    title = Translations.tr("download", language),
                    speedMbps = state.downloadSpeedMbps,
                    icon = Icons.Default.ArrowDownward,
                    iconColor = AppColors.primary,
                    modifier = Modifier.weight(1f)
                )

                SpeedMetricCard(
                    title = Translations.tr("upload", language),
                    speedMbps = state.uploadSpeedMbps,
                    icon = Icons.Default.ArrowUpward,
                    iconColor = AppColors.green,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Ping & Jitter Summary
        item {
            CardContainer {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = Translations.tr("ping", language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.inkMuted
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${"%.1f".format(state.pingMs)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppColors.ink
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = Translations.tr("ms", language),
                                fontSize = 11.sp,
                                color = AppColors.inkSoft
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(AppColors.border)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = Translations.tr("jitter", language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.inkMuted
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${"%.1f".format(state.jitterMs)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppColors.ink
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = Translations.tr("ms", language),
                                fontSize = 11.sp,
                                color = AppColors.inkSoft
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedMetricCard(
    title: String,
    speedMbps: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    CardContainer(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.inkSoft
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${"%.1f".format(speedMbps)}",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.ink
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "Mbps",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.inkMuted,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}
