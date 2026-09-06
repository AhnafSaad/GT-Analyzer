package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius

enum class AppTab(val key: String, val icon: ImageVector) {
    DASHBOARD("dashboard", Icons.Default.Bolt),
    DNS("dns", Icons.Default.Dns),
    NEARBY("nearby", Icons.Default.Wifi),
    SPEED("speed", Icons.Default.Speed),
    DIAGNOSTIC("diagnostic", Icons.Default.Hub)
}

@Composable
fun FloatingTabBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .navigationBarsPadding()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(AppRadius.xl), spotColor = AppColors.ink),
        shape = RoundedCornerShape(AppRadius.xl),
        color = AppColors.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                val tabLabel = Translations.tr(tab.key, language)
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) AppColors.primary else AppColors.inkMuted,
                    label = "iconColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) AppColors.primary else AppColors.inkSoft,
                    label = "textColor"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) AppColors.primarySoft else AppColors.surface,
                    label = "bgColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onTabSelected(tab) }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tabLabel,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tabLabel,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
