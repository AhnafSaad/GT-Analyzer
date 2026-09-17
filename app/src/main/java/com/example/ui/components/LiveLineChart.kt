package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing

@Composable
fun LiveLineChart(
    title: String,
    data: List<Double>,
    unit: String = "ms",
    lineColor: Color = AppColors.primary,
    gradientStartColor: Color = AppColors.primarySoft,
    modifier: Modifier = Modifier
) {
    val cleanData = if (data.isEmpty()) listOf(0.0, 0.0) else data
    val isDbm = unit.contains("dBm", ignoreCase = true) || cleanData.any { it < 0 }

    // Dynamic color resolution based on real-time dBm threshold rules
    val dynamicDbmColor = if (isDbm) {
        val latestVal = cleanData.lastOrNull() ?: -60.0
        when {
            latestVal >= -50.0 -> Color(0xFF10B981) // Green (#10B981)
            latestVal >= -65.0 -> Color(0xFFF59E0B) // Amber / Orange (#F59E0B)
            else -> Color(0xFFEF4444)             // Red (#EF4444)
        }
    } else {
        lineColor
    }

    val effectiveColor = if (isDbm) dynamicDbmColor else lineColor

    val animatedLineColor by animateColorAsState(
        targetValue = effectiveColor,
        animationSpec = tween(durationMillis = 300),
        label = "chartColorAnimation"
    )

    val minVal = if (isDbm) -100.0 else (cleanData.minOrNull() ?: 0.0)
    val maxVal = if (isDbm) -30.0 else (cleanData.maxOrNull() ?: 100.0).coerceAtLeast(minVal + 10.0)
    val avgVal = cleanData.average()
    val displayMax = cleanData.maxOrNull() ?: 0.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.surface)
            .border(1.dp, AppColors.border, RoundedCornerShape(AppRadius.md))
            .padding(AppSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.ink
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Avg: ${"%.1f".format(avgVal)} $unit",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.inkSoft
                )
                Text(
                    text = "Max: ${"%.1f".format(displayMax)} $unit",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.inkMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
        ) {
            val width = size.width
            val height = size.height
            val pointsCount = cleanData.size
            if (pointsCount < 2) return@Canvas

            val stepX = width / (pointsCount - 1).toFloat()

            val linePath = Path()
            val fillPath = Path()

            val points = cleanData.mapIndexed { idx, v ->
                val x = idx * stepX
                val normalized = if (isDbm) {
                    val clampedDbm = v.toFloat().coerceIn(-100f, -30f)
                    ((clampedDbm - (-100f)) / (-30f - (-100f))).coerceIn(0f, 1f)
                } else {
                    val valRange = (maxVal - minVal).toFloat().coerceAtLeast(1f)
                    ((v.toFloat() - minVal.toFloat()) / valRange).coerceIn(0f, 1f)
                }
                val y = height - (normalized * (height - 10.dp.toPx())) - 5.dp.toPx()
                Offset(x, y)
            }

            // Build curved path
            linePath.moveTo(points.first().x, points.first().y)
            fillPath.moveTo(points.first().x, height)
            fillPath.lineTo(points.first().x, points.first().y)

            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val controlX = (p0.x + p1.x) / 2f
                linePath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                fillPath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
            }

            fillPath.lineTo(points.last().x, height)
            fillPath.close()

            // Fill gradient below line
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        animatedLineColor.copy(alpha = 0.28f),
                        animatedLineColor.copy(alpha = 0.02f)
                    )
                )
            )

            // Draw line
            drawPath(
                path = linePath,
                color = animatedLineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw last point dot
            val lastPoint = points.last()
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = lastPoint
            )
            drawCircle(
                color = animatedLineColor,
                radius = 3.dp.toPx(),
                center = lastPoint
            )
        }
    }
}
