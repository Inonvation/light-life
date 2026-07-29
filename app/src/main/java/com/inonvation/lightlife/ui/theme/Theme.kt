package com.inonvation.lightlife.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color

// ── 绿色（默认） ──
private val GreenLight = lightColorScheme(
    primary = Color(0xFF222222),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0EB),
    onPrimaryContainer = Color(0xFF2D4A3A),
    secondary = Color(0xFF4E6E5D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E8DA),
    onSecondaryContainer = Color(0xFF2D4A3A),
    background = Color(0xFFFAFAF8),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0EC),
    onSurface = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF6F6F68),
    outline = Color(0xFFE0E0DA),
)

private val GreenDark = darkColorScheme(
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

// ── 粉色 ──
private val PinkLight = lightColorScheme(
    primary = Color(0xFF5C3A3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5EAEC),
    onPrimaryContainer = Color(0xFF4A2E2E),
    secondary = Color(0xFFB5838D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E2E6),
    onSecondaryContainer = Color(0xFF4A2E2E),
    background = Color(0xFFFCF8F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFF5EEF0),
    onSurface = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF706068),
    outline = Color(0xFFE2DADE),
)

private val PinkDark = darkColorScheme(
    primary = Color(0xFFE8D0D0),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF6E4A52),
    onPrimaryContainer = Color(0xFFD4B0B8),
    secondary = Color(0xFFD4A8B0),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF6E4A52),
    onSecondaryContainer = Color(0xFFD4B0B8),
    background = Color(0xFF1E1818),
    surface = Color(0xFF261E1E),
    surfaceVariant = Color(0xFF2E2424),
    onSurface = Color(0xFFE3D8D8),
    onSurfaceVariant = Color(0xFFA09094),
    outline = Color(0xFF3E3030),
)

// ── 黄色 ──
private val YellowLight = lightColorScheme(
    primary = Color(0xFF4A3F28),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5F0E4),
    onPrimaryContainer = Color(0xFF3A3220),
    secondary = Color(0xFFC4A36E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E8D6),
    onSecondaryContainer = Color(0xFF3A3220),
    background = Color(0xFFFCFAF5),
    surface = Color.White,
    surfaceVariant = Color(0xFFF5F0E6),
    onSurface = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF706858),
    outline = Color(0xFFE2DCD0),
)

private val YellowDark = darkColorScheme(
    primary = Color(0xFFE8DCC8),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF6E5D3E),
    onPrimaryContainer = Color(0xFFD4C8B0),
    secondary = Color(0xFFD4B88C),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF6E5D3E),
    onSecondaryContainer = Color(0xFFD4C8B0),
    background = Color(0xFF1E1C16),
    surface = Color(0xFF26241E),
    surfaceVariant = Color(0xFF2E2C22),
    onSurface = Color(0xFFE3DEC8),
    onSurfaceVariant = Color(0xFFA09886),
    outline = Color(0xFF3E3A2E),
)

// ── 蓝色 ──
private val BlueLight = lightColorScheme(
    primary = Color(0xFF2A3A42),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EEF2),
    onPrimaryContainer = Color(0xFF222E34),
    secondary = Color(0xFF6E8B98),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6EC),
    onSecondaryContainer = Color(0xFF222E34),
    background = Color(0xFFF5F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFECF0F4),
    onSurface = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF60707A),
    outline = Color(0xFFD8E0E6),
)

private val BlueDark = darkColorScheme(
    primary = Color(0xFFC8D8E0),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF3A505A),
    onPrimaryContainer = Color(0xFFB0C8D4),
    secondary = Color(0xFF88A8B8),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF3A505A),
    onSecondaryContainer = Color(0xFFB0C8D4),
    background = Color(0xFF141A1E),
    surface = Color(0xFF1A2226),
    surfaceVariant = Color(0xFF222A30),
    onSurface = Color(0xFFD8DEE0),
    onSurfaceVariant = Color(0xFF8898A0),
    outline = Color(0xFF323E44),
)

// ── 棕色 ──
private val BrownLight = lightColorScheme(
    primary = Color(0xFF3D352E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0ECE6),
    onPrimaryContainer = Color(0xFF322A24),
    secondary = Color(0xFF8B7D6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E0D6),
    onSecondaryContainer = Color(0xFF322A24),
    background = Color(0xFFF8F6F2),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0ECE4),
    onSurface = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF706A5E),
    outline = Color(0xFFE0DAD0),
)

private val BrownDark = darkColorScheme(
    primary = Color(0xFFD8D0C8),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF4A3D30),
    onPrimaryContainer = Color(0xFFC8BEB0),
    secondary = Color(0xFFA89A88),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF4A3D30),
    onSecondaryContainer = Color(0xFFC8BEB0),
    background = Color(0xFF1A1814),
    surface = Color(0xFF22201C),
    surfaceVariant = Color(0xFF2A2822),
    onSurface = Color(0xFFE0D8CC),
    onSurfaceVariant = Color(0xFF9A947E),
    outline = Color(0xFF3C3830),
)

@Composable
fun colorSchemeForTheme(colorTheme: ColorTheme, darkTheme: Boolean) = when (colorTheme) {
    ColorTheme.GREEN -> if (darkTheme) GreenDark else GreenLight
    ColorTheme.PINK -> if (darkTheme) PinkDark else PinkLight
    ColorTheme.YELLOW -> if (darkTheme) YellowDark else YellowLight
    ColorTheme.BLUE -> if (darkTheme) BlueDark else BlueLight
    ColorTheme.BROWN -> if (darkTheme) BrownDark else BrownLight
}

@Composable
fun DeviceControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.GREEN,
    content: @Composable () -> Unit,
) {
    val colorScheme = colorSchemeForTheme(colorTheme, darkTheme)
    LaunchedEffect(colorTheme) {
        HeaderGradients.updateForTheme(colorTheme)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
