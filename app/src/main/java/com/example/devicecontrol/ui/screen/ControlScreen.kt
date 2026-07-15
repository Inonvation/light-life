package com.example.devicecontrol.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.devicecontrol.ui.AppUiState
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.UnlockFlowState
import com.example.devicecontrol.ui.pinDeviceShortcut
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.devicecontrol.ui.theme.CardShapes
import com.example.devicecontrol.ui.theme.HeaderGradients
import com.example.devicecontrol.ui.theme.StatColors
import com.example.devicecontrol.ui.theme.successContainerColor
import com.example.devicecontrol.ui.theme.onSuccessContainerColor
import com.example.devicecontrol.ui.theme.warningContainerColor
import com.example.devicecontrol.ui.theme.onWarningContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardVisible = true }

    PullToRefreshBox(
        isRefreshing = state.loadingDevices,
        onRefresh = { vm.refreshDevices() },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        // ── 品牌渐变头部 ──
        item {
            HeaderSection(cardVisible)
        }

        // ── 数据概览（三张小卡片横排）──
        item {
            AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(tween(500, delayMillis = 100), initialOffsetY = { it / 2 })
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        icon = Icons.Outlined.LocalDrink,
                        label = "今日喝水",
                        value = "${state.todayWaterCount} 次",
                        accentColor = StatColors.waterCount,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Outlined.AttachMoney,
                        label = "抵扣金额",
                        value = "¥${state.todayWaterAmount}",
                        accentColor = StatColors.waterAmount,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Outlined.BarChart,
                        label = "累计",
                        value = "${state.totalWaterCount} 次",
                        accentColor = StatColors.totalWater,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 设备列表标题 + 刷新按钮（同一行）──
        if (state.hasToken) {
            item {
                AnimatedVisibility(
                    visible = cardVisible,
                    enter = fadeIn(tween(400, delayMillis = 200))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "我的设备",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.refreshDevices()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "刷新设备",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ── 设备列表 / 空状态 ──
        if (!state.hasToken) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请先到「我的」页面登录获取权限", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (state.loadingDevices) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("正在查询历史设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (state.devices.isEmpty() && state.unlockFlowState is UnlockFlowState.Idle) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Devices,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("暂无历史设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.devices) { device ->
                DeviceCard(
                    name = device.goodsName.ifBlank { "未命名设备" },
                    enabled = !state.unlocking,
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.unlock(device)
                    },
                    onAddShortcut = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        pinDeviceShortcut(context, device)
                    }
                )
            }
        }

        // ── 解锁状态卡片 ──
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

// ═══════════════════════════════════════════════
//  品牌渐变头部 - 随时间动态问候
// ═══════════════════════════════════════════════
@Composable
private fun HeaderSection(visible: Boolean) {
    val isDark = isSystemInDarkTheme()
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            if (isDark) HeaderGradients.darkStart else HeaderGradients.lightStart,
            if (isDark) HeaderGradients.darkEnd else HeaderGradients.lightEnd,
        )
    )

    // 获取当前小时
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    
    // 根据时段生成问候语
    val greeting = remember(hour) {
        when (hour) {
            in 5..7 -> "早~喝杯温水 (｡◕‿◕｡)"
            in 8..10 -> "早安，新的一天从喝水开始 (◠‿◠)☀️"
            in 11..13 -> "喝口水，歇一下 (◕‿◕)✨"
            in 14..16 -> "下午茶时间，来杯水吧 (•̀ᴗ•́)و✧"
            in 17..19 -> "傍晚了，记得补水哦 (｡◕‿◕｡)💧"
            in 20..22 -> "睡前喝点水哦 (◠‿◠)🌙"
            in 23..23, in 0..4 -> "晚安，该睡觉啦 (◕‿◕)💤"
            else -> "多喝水，身体棒棒的！(•̀ᴗ•́)و✧"
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400), initialOffsetY = { -it / 3 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient, CardShapes.headerCorner)
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Column {
                Text(
                    text = "LightLife",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
//  数据概览小卡片
// ═══════════════════════════════════════════════
@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accentColor
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════
//  设备卡片
// ═══════════════════════════════════════════════
@Composable
private fun DeviceCard(
    name: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onAddShortcut: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            TextButton(
                onClick = onAddShortcut,
                enabled = enabled
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "添加到桌面", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("桌面", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════
//  解锁流程状态卡片
// ═══════════════════════════════════════════════
@Composable
private fun PreCheckingCard() {
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
private fun WorkingCard(step: String, elapsed: Int) {
    val t = rememberInfiniteTransition(label = "pulse")
    val alpha by t.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("设备工作中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
private fun SuccessCard(result: com.example.devicecontrol.data.UnlockResult, onDismiss: () -> Unit) {
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
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭", color = onSuccessContainerColor())
            }
        }
    }
}

@Composable
private fun FailedCard(message: String, step: String, rawError: String, suggestions: List<String>, onDismiss: () -> Unit) {
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
