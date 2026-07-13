package com.example.devicecontrol.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ===== 间距常量 =====
object Spacings {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

// ===== 卡片圆角 =====
object CardShapes {
    val cardCorner = RoundedCornerShape(12.dp)
}

// ===== 按钮颜色值 =====
object AppColors {
    val start = Color(0xFF222222)
    val pause = Color(0xFFE6A817)
    val resume = Color(0xFF4CAF50)
    val stop = Color(0xFFD32F2F)
    val clear = Color(0xFF455A64)
    val white = Color.White
}

// ===== 日志颜色 =====
object LogColors {
    val background = Color(0xFF1A1C1E)
    val info = Color(0xFFB8C7D1)
    val success = Color(0xFF6FCF97)
    val error = Color(0xFFE06C75)
    val default = Color(0xFFB7F7C1)
}
