package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Devices
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.UnlockFlowState
import com.inonvation.lightlife.ui.pinDeviceShortcut
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.delay
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.HeaderGradients
import com.inonvation.lightlife.ui.theme.StatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardVisible = true }

    val refreshState = rememberPullToRefreshState()

    // 下拉刷新完毕后，图标至少停留 400ms
    var forceShowRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(state.loadingDevices) {
        if (state.loadingDevices) {
            forceShowRefresh = true
        } else if (forceShowRefresh) {
            delay(400)
            forceShowRefresh = false
        }
    }

    PullToRefreshBox(
        isRefreshing = state.loadingDevices || forceShowRefresh,
        onRefresh = {
            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.refreshDevices()
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = state.loadingDevices || forceShowRefresh,
                state = refreshState,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
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
                    RollingStatCard(
                        icon = Icons.Outlined.LocalDrink,
                        label = "今日喝水",
                        text = "${state.todayWaterCount} 次",
                        accentColor = StatColors.waterCount,
                        modifier = Modifier.weight(1f)
                    )
                    RollingStatCard(
                        icon = Icons.Outlined.AttachMoney,
                        label = "抵扣金额",
                        text = "¥${state.todayWaterAmount}",
                        accentColor = StatColors.waterAmount,
                        modifier = Modifier.weight(1f)
                    )
                    RollingStatCard(
                        icon = Icons.Outlined.BarChart,
                        label = "累计",
                        text = "${state.totalWaterCount} 次",
                        accentColor = StatColors.totalWater,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 设备列表标题 ──
        if (state.hasToken) {
            item {
                AnimatedVisibility(
                    visible = cardVisible,
                    enter = fadeIn(tween(400, delayMillis = 200))
                ) {
                    Text(
                        text = "我的设备",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
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
        if (state.unlockFlowState !is UnlockFlowState.Idle && !state.unlockFlowHidden) {
            item(key = "unlock_status") {
                Spacer(Modifier.height(8.dp))
                when (val flow = state.unlockFlowState) {
                    is UnlockFlowState.PreChecking -> PreCheckingCard()
                    is UnlockFlowState.Working -> WorkingCard(step = flow.step, elapsed = state.unlockElapsedSeconds, onDismiss = { vm.dismissUnlockAnimation() })
                    is UnlockFlowState.Success -> SuccessCard(result = flow.result, onDismiss = { vm.dismissUnlockFlow() })
                    is UnlockFlowState.Failed -> FailedCard(message = flow.message, step = flow.step, rawError = flow.rawError, suggestions = flow.suggestions, onDismiss = { vm.dismissUnlockFlow() })
                    is UnlockFlowState.Idle -> {}
                }
            }
        }
    }
    }
}
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
