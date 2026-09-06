package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = AppColors.primary,
    onPrimary = AppColors.white,
    primaryContainer = AppColors.primarySoft,
    onPrimaryContainer = AppColors.primary,
    secondary = AppColors.inkSoft,
    onSecondary = AppColors.white,
    background = AppColors.bg,
    onBackground = AppColors.ink,
    surface = AppColors.surface,
    onSurface = AppColors.ink,
    surfaceVariant = AppColors.surfaceAlt,
    onSurfaceVariant = AppColors.inkSoft,
    outline = AppColors.border,
    error = AppColors.red,
    onError = AppColors.white
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
