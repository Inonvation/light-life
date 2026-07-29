package com.inonvation.lightlife.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Spacings
object Spacings {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}


// Card corner radius
object CardShapes {
    val cardCorner = RoundedCornerShape(12.dp)
    val headerCorner = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    val smallCardCorner = RoundedCornerShape(10.dp)
    val dialogCorner = RoundedCornerShape(8.dp)
}

// Standard component heights
object ComponentHeights {
    val button = 48.dp
    val smallButton = 32.dp
}


// Button colors
object AppColors {
    val start = Color(0xFF222222)
    val pause = Color(0xFFE6A817)
    val resume = Color(0xFF4CAF50)
    val stop = Color(0xFFD32F2F)
    val white = Color.White
}

// Header gradient — follows the active color theme
object HeaderGradients {
    val lightStart get() = _lightStart
    val lightEnd get() = _lightEnd
    val darkStart get() = _darkStart
    val darkEnd get() = _darkEnd

    private var _lightStart = Color(0xFF4E6E5D)
    private var _lightEnd = Color(0xFF3A5A4A)
    private var _darkStart = Color(0xFF2D4A3A)
    private var _darkEnd = Color(0xFF1E3528)

    fun updateForTheme(theme: ColorTheme) {
        val (lStart, lEnd, dStart, dEnd) = when (theme) {
            ColorTheme.GREEN -> listOf(
                Color(0xFF4E6E5D), Color(0xFF3A5A4A),
                Color(0xFF2D4A3A), Color(0xFF1E3528),
            )
            ColorTheme.PINK -> listOf(
                Color(0xFFB5838D), Color(0xFF9B6E78),
                Color(0xFF6E4A52), Color(0xFF4A3036),
            )
            ColorTheme.YELLOW -> listOf(
                Color(0xFFC4A36E), Color(0xFFA88855),
                Color(0xFF6E5D3E), Color(0xFF4A3D28),
            )
            ColorTheme.BLUE -> listOf(
                Color(0xFF6E8B98), Color(0xFF55707E),
                Color(0xFF3A505A), Color(0xFF283840),
            )
            ColorTheme.BROWN -> listOf(
                Color(0xFF8B7D6B), Color(0xFF726454),
                Color(0xFF4A3D30), Color(0xFF30281E),
            )
        }
        _lightStart = lStart
        _lightEnd = lEnd
        _darkStart = dStart
        _darkEnd = dEnd
    }
}

// Stat card indicator colors
object StatColors {
    val waterCount = Color(0xFF2E7DBA)
    val waterAmount = Color(0xFFE8A838)
    val totalWater = Color(0xFF4E6E5D)
}


// Log panel colors
object LogColors {
    // Dark background for the log panel (works in both themes)
    val background = Color(0xFF1A1C1E)
    // Level-specific colors
    val info = Color(0xFFB8C7D1)        // light blue-gray
    val success = Color(0xFF6FCF97)     // green
    val warn = Color(0xFFE5C07B)        // warm yellow
    val error = Color(0xFFE06C75)       // red
    // Timestamp color (dimmed)
    val timestamp = Color(0xFF7A8A99)
}


// 语义化颜色扩展，适配暗色模式
@Composable
fun successContainerColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF1B3A1B) else Color(0xFFE8F5E9)
}

@Composable
fun onSuccessContainerColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
}

@Composable
fun warningContainerColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFF3A2A1B) else Color(0xFFFFF3E0)
}

@Composable
fun onWarningContainerColor(): Color {
    return if (isSystemInDarkTheme()) Color(0xFFFFCC80) else Color(0xFFE65100)
}
