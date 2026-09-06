package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.model.DnsResolver
import com.example.model.GlobalThresholds
import com.example.ui.components.CardContainer
import com.example.ui.components.StatusTag
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing

@Composable
fun DnsCheckScreen(
    resolvers: List<DnsResolver>,
    thresholds: GlobalThresholds,
    language: AppLanguage
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        contentPadding = PaddingValues(top = AppSpacing.md, bottom = 100.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = AppSpacing.xs)) {
                Text(
                    text = Translations.tr("dnsResolvers", language),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.ink
                )
                Text(
                    text = "DNS-over-HTTPS (DoH) parallel resolution & 3-point rolling average",
                    fontSize = 12.sp,
                    color = AppColors.inkMuted
                )
            }
        }

        items(resolvers, key = { it.id }) { resolver ->
            DnsResolverCard(
                resolver = resolver,
                thresholds = thresholds,
                language = language
            )
        }
    }
}

@Composable
private fun DnsResolverCard(
    resolver: DnsResolver,
    thresholds: GlobalThresholds,
    language: AppLanguage
) {
    val ping = resolver.pingMs
    val (statusKey, statusLevel) = when {
        ping <= thresholds.dnsSmoothMax -> Pair("stable", "green")
        ping <= thresholds.dnsPlayableMax -> Pair("slightlySlow", "yellow")
        else -> Pair("unstable", "red")
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
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(AppColors.primarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = AppColors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = resolver.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.ink
                    )
                    Text(
                        text = resolver.url.removePrefix("https://").substringBefore("?"),
                        fontSize = 11.sp,
                        color = AppColors.inkMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                StatusTag(
                    text = Translations.tr(statusKey, language),
                    level = statusLevel
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${"%.1f".format(ping)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.ink
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "ms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.inkMuted
                    )
                }
            }
        }

        if (resolver.recentPings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "3-Point Rolling:",
                    fontSize = 11.sp,
                    color = AppColors.inkMuted,
                    fontWeight = FontWeight.Medium
                )
                resolver.recentPings.takeLast(3).forEach { p ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AppColors.surfaceAlt,
                        modifier = Modifier.border(1.dp, AppColors.border, RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = "${"%.1f".format(p)} ms",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.inkSoft,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
