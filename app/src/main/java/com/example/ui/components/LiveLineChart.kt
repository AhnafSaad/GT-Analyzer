package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val minVal = cleanData.minOrNull() ?: 0.0
    val maxVal = (cleanData.maxOrNull() ?: 100.0).coerceAtLeast(minVal + 10.0)
    val avgVal = cleanData.average()

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
                    text = "Max: ${"%.1f".format(maxVal)} $unit",
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
            val valRange = (maxVal - minVal).toFloat().coerceAtLeast(1f)

            val linePath = Path()
            val fillPath = Path()

            val points = cleanData.mapIndexed { idx, v ->
                val x = idx * stepX
                val normalized = ((v - minVal).toFloat() / valRange).coerceIn(0f, 1f)
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
                        lineColor.copy(alpha = 0.25f),
                        lineColor.copy(alpha = 0.02f)
                    )
                )
            )

            // Draw line
            drawPath(
                path = linePath,
                color = lineColor,
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
                color = lineColor,
                radius = 3.dp.toPx(),
                center = lastPoint
            )
        }
    }
}
