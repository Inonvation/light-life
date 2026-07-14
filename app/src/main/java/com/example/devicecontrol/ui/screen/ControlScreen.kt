package com.example.devicecontrol.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.devicecontrol.ui.AppUiState
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.UnlockFlowState
import com.example.devicecontrol.ui.pinDeviceShortcut
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.devicecontrol.ui.theme.CardShapes
import com.example.devicecontrol.ui.theme.Spacings

@Composable
fun ControlScreen(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardVisible = true }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Spacer(Modifier.height(Spacings.sm))

        // 统计卡片带进入动画
        AnimatedVisibility(
            visible = cardVisible,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500, delayMillis = 100), initialOffsetY = { it / 2 })
        ) {
            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("今日喝水统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("喝水次数", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.todayWaterCount} 次", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("抵扣金额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${state.todayWaterAmount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("总共开水次数", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.totalWaterCount} 次", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacings.xxl + Spacings.xs))
        if (!state.hasToken) { EmptyText("请先到「我的」页面登录获取权限"); return@Column }
        if (state.loadingDevices) { LoadingText("正在查询历史设备") }
        else if (state.devices.isEmpty() && state.unlockFlowState is UnlockFlowState.Idle) { EmptyText("暂无历史设备") }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(state.devices) { device ->
                    DeviceRow(name = device.goodsName.ifBlank { "未命名设备" }, enabled = !state.unlocking, onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.unlock(device) }, onAddShortcut = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); pinDeviceShortcut(context, device) })
                }
                // 解锁状态卡片 —— 在设备列表下方
                if (state.unlockFlowState !is UnlockFlowState.Idle) {
                    item(key = "unlock_status") {
                        Spacer(Modifier.height(8.dp))
                        when (val flow = state.unlockFlowState) {
                            is UnlockFlowState.PreChecking -> PreCheckingCard()
                            is UnlockFlowState.Working -> WorkingCard(step = flow.step, elapsed = state.unlockElapsedSeconds)
                            is UnlockFlowState.Success -> SuccessCard(result = flow.result, onDismiss = { vm.dismissUnlockFlow() })
                            is UnlockFlowState.Failed -> FailedCard(message = flow.message, step = flow.step, rawError = flow.rawError, suggestions = flow.suggestions, onDismiss = { vm.dismissUnlockFlow() })
                            is UnlockFlowState.Idle -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreCheckingCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("正在检测设备状态...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkingCard(step: String, elapsed: Int) {
    val t = rememberInfiniteTransition(label = "pulse")
    val alpha by t.animateFloat(initialValue = 0.6f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse), label = "alpha")
    Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("设备工作中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            if (elapsed > 0) { Spacer(Modifier.height(4.dp)); Text("已运行 $elapsed 秒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
private fun SuccessCard(result: com.example.devicecontrol.data.UnlockResult, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("开机成功", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("订单原价", style = MaterialTheme.typography.bodySmall, color = Color(0xFF558B2F)); Text(result.originPrice, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32)) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("花费小票", style = MaterialTheme.typography.bodySmall, color = Color(0xFF558B2F)); Text(result.ticketCost, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32)) }
            if (result.integralCost != "-") { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("积分抵扣", style = MaterialTheme.typography.bodySmall, color = Color(0xFF558B2F)); Text(result.integralCost, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32)) } }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("关闭", color = Color(0xFF4CAF50)) }
        }
    }
}

@Composable
private fun FailedCard(message: String, step: String, rawError: String, suggestions: List<String>, onDismiss: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Error, null, tint = Color(0xFFE65100), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("开机失败", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFBF360C))
            }
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4E342E), fontWeight = FontWeight.Medium)
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                suggestions.forEach { s ->
                    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Text("• ", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8D6E63))
                        Text(s, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8D6E63))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (expanded) "收起详情" else "查看原始错误信息", style = MaterialTheme.typography.bodySmall)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("失败步骤", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA1887F)); Text(step, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6D4C41)) }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("原始错误", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA1887F)); Text(rawError, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6D4C41)) }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("关闭", color = Color(0xFFE65100)) }
        }
    }
}
