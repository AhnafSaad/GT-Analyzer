package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppColors {
    val gradientStart = Color(0xFFFF8A3D)
    val gradientEnd = Color(0xFFEA560F)
    val primary = Color(0xFFEA560F)
    val primarySoft = Color(0xFFFFF1E8)
    val primarySoftBorder = Color(0xFFFFD9BC)

    val green = Color(0xFF1B9C5A)
    val greenSoft = Color(0xFFE7F8EF)
    val greenSoftBorder = Color(0xFFBEEAD1)

    val yellow = Color(0xFFC48A0A)
    val yellowSoft = Color(0xFFFFF7E0)
    val yellowSoftBorder = Color(0xFFF5E1A0)

    val red = Color(0xFFE0392F)
    val redSoft = Color(0xFFFDEBEA)
    val redSoftBorder = Color(0xFFF5C2BE)

    val bg = Color(0xFFF6F5F8)
    val surface = Color(0xFFFFFFFF)
    val surfaceAlt = Color(0xFFFBFAFC)
    val border = Color(0xFFECEAF1)

    val ink = Color(0xFF181521)
    val inkSoft = Color(0xFF5B5768)
    val inkMuted = Color(0xFF9C97A8)
    val white = Color(0xFFFFFFFF)

    val blue = Color(0xFF2563EB)
    val blueSoft = Color(0xFFEFF6FF)
    val blueSoftBorder = Color(0xFFBFDBFE)
}

data class StatusPalette(
    val main: Color,
    val soft: Color,
    val border: Color
)

fun getStatusColors(level: String): StatusPalette {
    return when (level.lowercase()) {
        "green", "good", "smooth", "stable" -> StatusPalette(
            main = AppColors.green,
            soft = AppColors.greenSoft,
            border = AppColors.greenSoftBorder
        )
        "yellow", "fair", "playable", "slow" -> StatusPalette(
            main = AppColors.yellow,
            soft = AppColors.yellowSoft,
            border = AppColors.yellowSoftBorder
        )
        else -> StatusPalette(
            main = AppColors.red,
            soft = AppColors.redSoft,
            border = AppColors.redSoftBorder
        )
    }
}

object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val xxxl = 36.dp
}

object AppRadius {
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 26.dp
    val pill = 999.dp
}
