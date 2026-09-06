package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppSpacing

@Composable
fun AppHeader(
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    onReload: () -> Unit,
    onOpenServerConfig: () -> Unit,
    isRefreshing: Boolean = false,
    backendConnected: Boolean = false
) {
    Surface(
        color = AppColors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // App Logo & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(AppColors.gradientStart, AppColors.gradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "App Logo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "GT Wifi",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppColors.ink
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Analyzer",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.primary
                            )
                        }

                        // Status dot
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (backendConnected) AppColors.green else AppColors.yellow)
                            )
                            Text(
                                text = if (backendConnected) "Backend Active" else "Local Mode",
                                fontSize = 11.sp,
                                color = AppColors.inkMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Action Buttons: Language toggle, Server config, Refresh
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    // Language Switcher Pill
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = AppColors.primarySoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, AppColors.primarySoftBorder, RoundedCornerShape(999.dp))
                            .clickable { onToggleLanguage() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == AppLanguage.BN) "🇧🇩 বাংলা" else "🇬🇧 EN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.primary
                            )
                        }
                    }

                    // Server Config Icon Button
                    IconButton(
                        onClick = onOpenServerConfig,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = Translations.tr("serverSettings", language),
                            tint = AppColors.inkSoft,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reload Button
                    IconButton(
                        onClick = onReload,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = Translations.tr("reload", language),
                            tint = if (isRefreshing) AppColors.primary else AppColors.inkSoft,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
