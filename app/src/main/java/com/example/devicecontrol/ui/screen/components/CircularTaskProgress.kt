package com.example.devicecontrol.ui.screen.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.ui.PointsProgress
import kotlinx.coroutines.delay

private val TrackColor = Color(0xFF2A2A2A)
private val RunningGreen = Color(0xFF4CAF50)
private val RunningGreenLight = Color(0xFF81C784)
private val SuccessGreen = Color(0xFF4CAF50)
private val FailOrange = Color(0xFFE6A817)
private val IdleGray = Color(0xFF666666)

/**
 * 环形进度指示器 — 120dp 大号
 *
 * @param progress 0f..1f 总体进度（当前阶段步数/总步数）
 * @param running 是否正在运行
 * @param finished 是否已完成
 * @param allDone 是否全部成功（否则部分失败）
 * @param phaseLabel 当前阶段名
 * @param stepText 步骤文字，如 "2 / 5"
 */
@Composable
fun CircularTaskProgress(
    progress: Float,
    running: Boolean,
    finished: Boolean,
    allDone: Boolean,
    phaseLabel: String,
    stepText: String,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "circularProgress"
    )

    val arcColor = when {
        finished && allDone -> SuccessGreen
        finished -> FailOrange
        running -> RunningGreen
        else -> IdleGray
    }

    val arcColorLight = when {
        finished && allDone -> RunningGreenLight
        finished -> FailOrange.copy(alpha = 0.6f)
        running -> RunningGreenLight
        else -> IdleGray.copy(alpha = 0.5f)
    }

    // 运行中呼吸脉冲
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    // 运行中缓慢旋转的装饰弧
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000), RepeatMode.Restart),
        label = "rotation"
    )

    // 已用时间
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        if (running) {
            elapsedSeconds = 0
            while (true) { delay(1000); elapsedSeconds++ }
        }
    }

    Box(modifier = modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // 底部轨道
            drawArc(
                color = TrackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 前景进度弧
            if (animatedProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(arcColor, arcColorLight, arcColor)
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // 运行中：呼吸光晕 + 旋转装饰弧
            if (running) {
                // 外圈光晕
                drawArc(
                    color = arcColor.copy(alpha = pulseAlpha),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth + 8.dp.toPx(), cap = StrokeCap.Round)
                )
                // 旋转装饰弧（顶部一小段）
                drawArc(
                    color = arcColorLight.copy(alpha = 0.5f),
                    startAngle = rotationAngle,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 中心文字
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (running && phaseLabel.isNotEmpty()) {
                Text(
                    phaseLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = arcColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                if (stepText.isNotEmpty()) {
                    Text(
                        stepText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
            } else if (finished) {
                Text(
                    if (allDone) "✓" else "!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = arcColor,
                    fontSize = 28.sp,
                )
            } else {
                Text(
                    "0%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IdleGray,
                )
            }
        }
    }

    // 运行中：底部显示已用时间
    if (running) {
        Spacer(Modifier.height(6.dp))
        Text(
            formatElapsed(elapsedSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}分${s}秒" else "${s}秒"
}
