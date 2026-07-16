package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.data.UnlockResult
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.onSuccessContainerColor
import com.inonvation.lightlife.ui.theme.onWarningContainerColor
import com.inonvation.lightlife.ui.theme.successContainerColor
import com.inonvation.lightlife.ui.theme.warningContainerColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PreCheckingCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("正在检测设备状态...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun WorkingCard(step: String, elapsed: Int, onDismiss: (() -> Unit)? = null) {
    var showStopButton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        showStopButton = true
    }
    val t = rememberInfiniteTransition(label = "pulse")
    val progress by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val containerColor = lerp(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primaryContainer.copy(
            red = (MaterialTheme.colorScheme.primaryContainer.red * 1.2f).coerceAtMost(1f),
            green = (MaterialTheme.colorScheme.primaryContainer.green * 1.2f).coerceAtMost(1f),
            blue = (MaterialTheme.colorScheme.primaryContainer.blue * 1.2f).coerceAtMost(1f)
        ),
        progress
    )
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + progress * 0.45f)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("设备工作中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = showStopButton && onDismiss != null) {
                    IconButton(
                        onClick = { onDismiss?.invoke() },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭动画", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            if (elapsed > 0) {
                Spacer(Modifier.height(4.dp))
                Text("已运行 $elapsed 秒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
internal fun SuccessCard(result: UnlockResult, onDismiss: () -> Unit) {
    var showDetail by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = successContainerColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = onSuccessContainerColor(), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("开机成功", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = onSuccessContainerColor())
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("订单原价", style = MaterialTheme.typography.bodySmall, color = onSuccessContainerColor().copy(alpha = 0.8f))
                Text(result.originPrice, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = onSuccessContainerColor())
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("花费小票", style = MaterialTheme.typography.bodySmall, color = onSuccessContainerColor().copy(alpha = 0.8f))
                Text(result.ticketCost, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = onSuccessContainerColor())
            }
            if (result.integralCost != "-") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("积分抵扣", style = MaterialTheme.typography.bodySmall, color = onSuccessContainerColor().copy(alpha = 0.8f))
                    Text(result.integralCost, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = onSuccessContainerColor())
                }
            }
            if (result.otherPromotions.isNotEmpty()) {
                result.otherPromotions.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("其他优惠", style = MaterialTheme.typography.bodySmall, color = onSuccessContainerColor().copy(alpha = 0.8f))
                        Text(p.discountAmount ?: "-", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = onSuccessContainerColor())
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { showDetail = !showDetail }, modifier = Modifier.align(Alignment.End)) {
                Icon(
                    if (showDetail) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = onSuccessContainerColor()
                )
                Spacer(Modifier.width(4.dp))
                Text(if (showDetail) "收起详情" else "更多详情", style = MaterialTheme.typography.bodySmall, color = onSuccessContainerColor())
            }
            AnimatedVisibility(visible = showDetail, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("订单号", style = MaterialTheme.typography.labelSmall, color = onSuccessContainerColor().copy(alpha = 0.6f))
                        Text(result.orderNo, style = MaterialTheme.typography.bodySmall, color = onSuccessContainerColor())
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("订单 ID", style = MaterialTheme.typography.labelSmall, color = onSuccessContainerColor().copy(alpha = 0.6f))
                        Text(result.orderId, style = MaterialTheme.typography.bodySmall, color = onSuccessContainerColor())
                    }
                    if (result.completedAt > 0) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("完成时间", style = MaterialTheme.typography.labelSmall, color = onSuccessContainerColor().copy(alpha = 0.6f))
                            Text(dateFormat.format(Date(result.completedAt)), style = MaterialTheme.typography.bodySmall, color = onSuccessContainerColor())
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭", color = onSuccessContainerColor())
            }
        }
    }
}

@Composable
internal fun FailedCard(message: String, step: String, rawError: String, suggestions: List<String>, onDismiss: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = warningContainerColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Error, null, tint = onWarningContainerColor(), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("开机失败", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = onWarningContainerColor())
            }
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = onWarningContainerColor(), fontWeight = FontWeight.Medium)
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                suggestions.forEach { s ->
                    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Text("• ", style = MaterialTheme.typography.bodySmall, color = onWarningContainerColor().copy(alpha = 0.8f))
                        Text(s, style = MaterialTheme.typography.bodySmall, color = onWarningContainerColor().copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (expanded) "收起详情" else "查看原始错误信息", style = MaterialTheme.typography.bodySmall)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("失败步骤", style = MaterialTheme.typography.labelSmall, color = onWarningContainerColor().copy(alpha = 0.6f))
                        Text(step, style = MaterialTheme.typography.bodySmall, color = onWarningContainerColor())
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("原始错误", style = MaterialTheme.typography.labelSmall, color = onWarningContainerColor().copy(alpha = 0.6f))
                        Text(rawError, style = MaterialTheme.typography.bodySmall, color = onWarningContainerColor())
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭", color = onWarningContainerColor())
            }
        }
    }
}
