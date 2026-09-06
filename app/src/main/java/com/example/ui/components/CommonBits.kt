package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.getStatusColors

@Composable
fun CardContainer(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppColors.surface,
    elevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = elevation, shape = RoundedCornerShape(AppRadius.lg), spotColor = AppColors.ink)
            .border(1.dp, AppColors.border, RoundedCornerShape(AppRadius.lg)),
        shape = RoundedCornerShape(AppRadius.lg),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            content = content
        )
    }
}

@Composable
fun StatusTag(
    text: String,
    level: String, // "green", "yellow", "red"
    modifier: Modifier = Modifier
) {
    val palette = getStatusColors(level)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.soft)
            .border(1.dp, palette.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.main)
            )
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.main
            )
        }
    }
}

@Composable
fun VerdictBadge(
    title: String,
    scoreText: String,
    level: String,
    modifier: Modifier = Modifier
) {
    val palette = getStatusColors(level)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(palette.soft)
            .border(1.5.dp, palette.border, RoundedCornerShape(AppRadius.md))
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(palette.main)
            )
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = palette.main
            )
        }

        Text(
            text = scoreText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.main
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val gradient = Brush.linearGradient(
        colors = listOf(AppColors.gradientStart, AppColors.gradientEnd)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = if (enabled) 6.dp else 0.dp,
                shape = RoundedCornerShape(AppRadius.pill),
                spotColor = AppColors.primary
            )
            .clip(RoundedCornerShape(AppRadius.pill))
            .background(if (enabled) gradient else Brush.linearGradient(listOf(AppColors.inkMuted, AppColors.inkMuted)))
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
