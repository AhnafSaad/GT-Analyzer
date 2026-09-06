package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SignalGauge(
    dbm: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    // Normal WiFi dBm ranges from -30 (best) to -90 (worst)
    // Normalized 0.0 (worst) to 1.0 (best)
    val clampedDbm = dbm.coerceIn(-95, -30)
    val fraction = ((clampedDbm - (-95)).toFloat() / ((-30) - (-95))).coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600),
        label = "gaugeAnimation"
    )

    val gaugeColor = when {
        clampedDbm >= -65 -> AppColors.green
        clampedDbm >= -80 -> AppColors.yellow
        else -> AppColors.red
    }

    Box(
        modifier = modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            val startAngle = 145f
            val totalSweep = 250f

            // 1. Background Arc Track
            drawArc(
                color = AppColors.border,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius + strokeWidth / 2f, center.y - radius + strokeWidth / 2f),
                size = Size(diameter - strokeWidth, diameter - strokeWidth)
            )

            // 2. Active Progress Arc
            val activeSweep = totalSweep * animatedFraction
            if (activeSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to AppColors.gradientStart,
                        0.5f to AppColors.gradientEnd,
                        1.0f to gaugeColor
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius + strokeWidth / 2f, center.y - radius + strokeWidth / 2f),
                    size = Size(diameter - strokeWidth, diameter - strokeWidth)
                )
            }

            // 3. Pointer dot at arc end
            val currentAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
            val pointerRadius = radius - strokeWidth / 2f
            val dotX = center.x + pointerRadius * cos(currentAngleRad).toFloat()
            val dotY = center.y + pointerRadius * sin(currentAngleRad).toFloat()

            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(dotX, dotY)
            )
            drawCircle(
                color = gaugeColor,
                radius = 5.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$clampedDbm",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.ink
            )
            Text(
                text = "dBm",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.inkMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.inkSoft
            )
        }
    }
}

@Composable
fun SpeedGauge(
    currentMbps: Double,
    label: String,
    maxExpectedMbps: Double = 100.0,
    modifier: Modifier = Modifier
) {
    val fraction = (currentMbps / maxExpectedMbps).coerceIn(0.0, 1.0).toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 200),
        label = "speedGaugeAnim"
    )

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            val startAngle = 145f
            val totalSweep = 250f

            // Track
            drawArc(
                color = AppColors.border,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius + strokeWidth / 2f, center.y - radius + strokeWidth / 2f),
                size = Size(diameter - strokeWidth, diameter - strokeWidth)
            )

            // Progress
            val activeSweep = totalSweep * animatedFraction
            if (activeSweep > 0.5f) {
                drawArc(
                    brush = Brush.horizontalGradient(
                        listOf(AppColors.gradientStart, AppColors.primary, AppColors.green)
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius + strokeWidth / 2f, center.y - radius + strokeWidth / 2f),
                    size = Size(diameter - strokeWidth, diameter - strokeWidth)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "%.1f".format(currentMbps),
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.ink
            )
            Text(
                text = "Mbps",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.inkSoft
            )
        }
    }
}
