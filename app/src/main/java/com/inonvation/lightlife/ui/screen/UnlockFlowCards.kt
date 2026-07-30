package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.data.UnlockResult
import com.inonvation.lightlife.ui.UnlockFlowState
import com.inonvation.lightlife.ui.theme.AppColors
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.onSuccessContainerColor
import com.inonvation.lightlife.ui.theme.onWarningContainerColor
import com.inonvation.lightlife.ui.theme.successContainerColor
import com.inonvation.lightlife.ui.theme.warningContainerColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 内联解锁状态组件（嵌入设备卡片下方） ──

@Composable
internal fun InlinePreChecking() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Text("正在检测设备…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun InlineWorking(step: String, elapsed: Int) {
    val totalSeconds = 165
    val remaining = (totalSeconds - elapsed).coerceAtLeast(0)
    val progress = remember(remaining) { remaining / totalSeconds.toFloat() }

    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("正在出水", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text("${remaining} 秒后自动关闭", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (step.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(step, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

@Composable
internal fun InlineSuccess(result: UnlockResult, onShowDetail: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "successIconScale",
    )
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "成功",
                tint = AppColors.runningIndicator,
                modifier = Modifier.size(18.dp).scale(scale)
            )
            Spacer(Modifier.width(8.dp))
            Text("开水成功", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = AppColors.runningIndicator)
            Spacer(Modifier.weight(1f))
            Text(
                "花费 ¥${result.originPrice}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "查看详情",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onShowDetail).padding(vertical = 4.dp)
        )
    }
}

@Composable
internal fun InlineFailed(message: String, onShowDetail: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val offset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shakeOffset",
    )
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Error,
                contentDescription = "失败",
                tint = AppColors.stop,
                modifier = Modifier.size(18.dp).offset(x = offset.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("开水失败", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = AppColors.stop)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            message.ifBlank { "未知错误" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 2,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "查看详情",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onShowDetail).padding(vertical = 4.dp)
        )
    }
}

/** 内联状态容器——在状态间做交叉淡入淡出 */
@Composable
internal fun InlineUnlockStatus(
    flowState: UnlockFlowState,
    elapsedSeconds: Int,
    result: UnlockResult?,
    onDismiss: () -> Unit,
    onShowDetail: () -> Unit,
) {
    AnimatedContent(
        targetState = flowState,
        transitionSpec = {
            fadeIn(spring(stiffness = 300f)) togetherWith fadeOut(spring(stiffness = 300f))
        },
        label = "inlineUnlockStatus",
    ) { state ->
        when (state) {
            is UnlockFlowState.PreChecking -> InlinePreChecking()
            is UnlockFlowState.Working -> InlineWorking(step = state.step, elapsed = elapsedSeconds)
            is UnlockFlowState.Success -> InlineSuccess(result = state.result, onShowDetail = onShowDetail)
            is UnlockFlowState.Failed -> InlineFailed(message = state.message, onShowDetail = onShowDetail)
            is UnlockFlowState.Idle -> {}
        }
    }
}
