package com.example.devicecontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF222222),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0EB),
    onPrimaryContainer = Color(0xFF2D4A3A),
    secondary = Color(0xFF4E6E5D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E8DA),
    onSecondaryContainer = Color(0xFF2D4A3A),
    background = Color(0xFFFAFAF8),
    surface = Color(0xFFFAFAF8),
    surfaceVariant = Color(0xFFF0F0EC),
    onSurface = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF6F6F68),
    outline = Color(0xFFE0E0DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD6D6D6),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF2D4A3A),
    onPrimaryContainer = Color(0xFFB8D8C2),
    secondary = Color(0xFF80B09A),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF2D4A3A),
    onSecondaryContainer = Color(0xFFB8D8C2),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF252525),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFA09F99),
    outline = Color(0xFF3C3C3C),
)

@Composable
fun DeviceControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
