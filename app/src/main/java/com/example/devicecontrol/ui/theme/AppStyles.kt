package com.example.devicecontrol.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
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
    val xxxl = 32.dp
}


// Card corner radius
object CardShapes {
    val cardCorner = RoundedCornerShape(12.dp)
    val headerCorner = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    val chipCorner = RoundedCornerShape(20.dp)
    val smallCardCorner = RoundedCornerShape(10.dp)
}


// Button colors
object AppColors {
    val tabControl = Color(0xFF2196F3)
    val tabPoints = Color(0xFF4CAF50)
    val tabMe = Color(0xFF9C27B0)
    val start = Color(0xFF222222)
    val pause = Color(0xFFE6A817)
    val resume = Color(0xFF4CAF50)
    val stop = Color(0xFFD32F2F)
    val clear = Color(0xFF455A64)
    val white = Color.White
}

// Header gradient — green theme matching the app identity
object HeaderGradients {
    val lightStart = Color(0xFF4E6E5D)
    val lightEnd = Color(0xFF3A5A4A)
    val darkStart = Color(0xFF2D4A3A)
    val darkEnd = Color(0xFF1E3528)
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
    val default = Color(0xFFB7F7C1)     // legacy light green (kept for compat)
    // Timestamp color (dimmed)
    val timestamp = Color(0xFF7A8A99)
}


// Timeline colors for PointsTaskScreen
object TimelineColors {
    val completed = Color(0xFF4CAF50)
    val active = Color(0xFF2196F3)
    val pending = Color(0xFF9E9E9E)
    val activeSurface = Color(0xFF2196F3).copy(alpha = 0.12f)
}
