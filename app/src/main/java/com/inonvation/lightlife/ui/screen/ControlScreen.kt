package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.data.DeviceItem
import com.inonvation.lightlife.data.QuickLink
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.UnlockFlowState
import com.inonvation.lightlife.ui.pinDeviceShortcut
import com.inonvation.lightlife.ui.pinQuickLinkShortcut
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.delay
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.HeaderGradients
import com.inonvation.lightlife.ui.theme.StatColors
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(state: AppUiState, vm: AppViewModel, onPickIcon: ((Int) -> Unit)? = null) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var cardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardVisible = true }

    // 成功/失败详情弹窗
    var showDetailDialog by remember { mutableStateOf(false) }
    val successResult = (state.unlockFlowState as? UnlockFlowState.Success)?.result
    val failedState = state.unlockFlowState as? UnlockFlowState.Failed

    // 缓存最近一次解锁的设备，供失败重试使用
    var lastUnlockedDevice by remember { mutableStateOf<DeviceItem?>(null) }

    // 30秒自动关闭解锁状态
    LaunchedEffect(state.unlockFlowState) {
        if (state.unlockFlowState is UnlockFlowState.Success || state.unlockFlowState is UnlockFlowState.Failed) {
            kotlinx.coroutines.delay(30_000)
            vm.dismissUnlockFlow()
        }
    }

    val refreshState = rememberPullToRefreshState()
    val isRefreshing = rememberMinRefreshDuration(state.loadingDevices)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.refreshDevices()
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
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
                        label = "今日积分",
                        text = "${state.todayPointsEarned}",
                        accentColor = StatColors.totalWater,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 快捷链接 ──
        if (state.hasToken && state.quickLinksEnabled) {
            item {
                QuickLinksSection(state = state, vm = vm, onPickIcon = onPickIcon, cardVisible = cardVisible, haptic = haptic, context = context)
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
            // 积分抵扣开关
            item {
                AnimatedVisibility(
                    visible = cardVisible,
                    enter = fadeIn(tween(400, delayMillis = 300))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("使用积分抵扣", style = MaterialTheme.typography.bodyMedium)
                            Text("关闭后开水将不消耗积分", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.usePointsForUnlock,
                            onCheckedChange = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleUsePointsForUnlock()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
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
                val isThisDevice = device.goodsName.ifBlank { device.id } == state.unlockingDeviceId
                val isUnlockingElsewhere = state.unlocking && !isThisDevice

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    DeviceCard(
                        name = device.goodsName.ifBlank { "未命名设备" },
                        enabled = !state.unlocking || isThisDevice,
                        isUnlocking = isThisDevice && state.unlocking,
                        onUnlock = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            lastUnlockedDevice = device
                            vm.unlock(device)
                        },
                        onAddShortcut = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pinDeviceShortcut(context, device)
                        },
                        isOtherLocked = isUnlockingElsewhere,
                    )

                    // 内联解锁状态
                    if (isThisDevice && state.unlockFlowState !is UnlockFlowState.Idle && !state.unlockFlowHidden) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(tween(300)),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            InlineUnlockStatus(
                                flowState = state.unlockFlowState,
                                elapsedSeconds = state.unlockElapsedSeconds,
                                onShowDetail = { showDetailDialog = true },
                            )
                        }
                    }
                }
            }
        }
    }
    }

    // 成功/失败详情弹窗
    if (successResult != null && showDetailDialog) {
        val dateFormat = remember { java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA) }
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = { Text("开水成功", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    DetailRow("订单原价", "¥${successResult.originPrice}")
                    DetailRow("花费小票", successResult.ticketCost)
                    if (successResult.integralCost != "-") DetailRow("积分抵扣", successResult.integralCost)
                    successResult.otherPromotions.forEach { p ->
                        DetailRow("其他优惠", p.discountAmount ?: "-")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow("订单号", successResult.orderNo)
                    DetailRow("订单 ID", successResult.orderId)
                    if (successResult.completedAt > 0) DetailRow("完成时间", dateFormat.format(java.util.Date(successResult.completedAt)))
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false; vm.dismissUnlockFlow() }) {
                    Text("关闭")
                }
            },
        )
    }
    if (failedState != null && showDetailDialog) {
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = { Text("开水失败", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(failedState.message, style = MaterialTheme.typography.bodyMedium)
                    if (failedState.suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        failedState.suggestions.forEach { s ->
                            Text("• $s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DetailRow("失败步骤", failedState.step)
                    DetailRow("原始错误", failedState.rawError)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDetailDialog = false }) {
                        Text("关闭")
                    }
                    if (lastUnlockedDevice != null) {
                        Button(
                            onClick = {
                                showDetailDialog = false
                                vm.dismissUnlockFlow()
                                vm.unlock(lastUnlockedDevice!!)
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) { Text("重试") }
                    }
                }
            },
        )
    }
}
@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    text = "LightLife",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodySmall,
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
    isUnlocking: Boolean,
    onUnlock: () -> Unit,
    onAddShortcut: () -> Unit,
    isOtherLocked: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(
            containerColor = if (isOtherLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOtherLocked) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Devices,
                        contentDescription = "设备图标",
                        modifier = Modifier.size(20.dp),
                        tint = if (isOtherLocked) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isOtherLocked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (isOtherLocked) {
                        Text(
                            text = "占用中…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            if (!isOtherLocked) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onAddShortcut,
                        enabled = enabled,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加到桌面", modifier = Modifier.size(18.dp))
                    }

                    Button(
                        onClick = onUnlock,
                        enabled = enabled,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            if (isUnlocking) Icons.Filled.CheckCircle else Icons.Outlined.LocalDrink,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isUnlocking) "开水中…" else "开水",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SortableQuickLinkCard(
    name: String,
    url: String,
    iconUri: String = "",
    isSorting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hasLink = url.isNotBlank()

    // 排序模式下的脉动动画
    val pulse by if (isSorting) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAnim",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val dashColor = if (isSorting) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent

    Card(
        modifier = modifier
            .scale(pulse)
            .then(
                if (isSorting) {
                    Modifier.drawBehind {
                            val rect = Rect(Offset.Zero, size)
                            val path = Path().apply {
                                addRoundRect(RoundRect(
                                    rect = rect,
                                    cornerRadius = CornerRadius(12.dp.toPx()),
                                ))
                            }
                            drawPath(
                                path = path,
                                color = dashColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                                        phase = 0f,
                                    ),
                                ),
                            )
                        }
                } else Modifier
            )
            .then(
                if (!isSorting && hasLink) Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ) else Modifier.clickable(enabled = !isSorting, onClick = onClick)
            ),
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(
            containerColor = if (hasLink) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasLink) 1.dp else 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasLink) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasLink && iconUri.isNotBlank()) {
                        // 显示自定义图标
                        val iconBitmap = remember(iconUri) {
                            try {
                                val file = java.io.File(iconUri)
                                if (file.exists()) {
                                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                } else null
                            } catch (_: Exception) { null }
                        }
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = name,
                                modifier = Modifier.size(24.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    } else if (hasLink) {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = "添加", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (hasLink) name.ifBlank { "快捷方式" } else "添加",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasLink) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 排序模式提示
            if (isSorting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = "拖动排序", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun QuickLinkCard(
    name: String,
    url: String,
    iconUri: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SortableQuickLinkCard(
        name = name,
        url = url,
        iconUri = iconUri,
        isSorting = false,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun QuickLinksSection(
    state: AppUiState,
    vm: AppViewModel,
    onPickIcon: ((Int) -> Unit)?,
    cardVisible: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: android.content.Context,
) {
    AnimatedVisibility(
        visible = cardVisible,
        enter = fadeIn(tween(400, delayMillis = 150))
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            val isSorting = remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "快捷链接",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (isSorting.value) {
                    IconButton(
                        onClick = { isSorting.value = false },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "完成", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                } else {
                    IconButton(
                        onClick = { isSorting.value = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = "排序", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val configuredLinks = state.quickLinks.filter { it.url.isNotBlank() }
            val displayLinks = if (configuredLinks.size < 3) {
                configuredLinks + List(3 - configuredLinks.size) { QuickLink() }
            } else {
                configuredLinks
            }
            val rowCount = (displayLinks.size + 2) / 3
            var draggedIndex by remember { mutableIntStateOf(-1) }
            var dragOffsetX by remember { mutableFloatStateOf(0f) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            var contextMenuIndex by remember { mutableIntStateOf(-1) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (row in 0 until rowCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        for (col in 0 until 3) {
                            val displayIdx = row * 3 + col
                            if (displayIdx < displayLinks.size) {
                                val link = displayLinks[displayIdx]
                                val hasLink = link.url.isNotBlank()
                                val realIndex = if (hasLink) state.quickLinks.indexOf(link) else -1
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .zIndex(if (draggedIndex == realIndex) 1f else 0f)
                                        .graphicsLayer {
                                            translationX = if (draggedIndex == realIndex) dragOffsetX else 0f
                                            translationY = if (draggedIndex == realIndex) dragOffsetY else 0f
                                            scaleX = if (draggedIndex == realIndex) 1.05f else 1f
                                            scaleY = if (draggedIndex == realIndex) 1.05f else 1f
                                        }
                                        .then(
                                            if (isSorting.value && hasLink) {
                                                Modifier.pointerInput(realIndex) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { draggedIndex = realIndex },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetX += dragAmount.x
                                                            dragOffsetY += dragAmount.y
                                                            val thresholdY = with(change) { 80.dp.toPx() }
                                                            val thresholdX = with(change) { 55.dp.toPx() }
                                                            val rowsMoved = (dragOffsetY / thresholdY).toInt()
                                                            if (rowsMoved != 0) {
                                                                val target = (realIndex + rowsMoved * 3).coerceIn(0, state.quickLinks.size - 1)
                                                                if (target != realIndex) {
                                                                    vm.swapQuickLinks(realIndex, target)
                                                                    draggedIndex = -1
                                                                    dragOffsetX = 0f
                                                                    dragOffsetY = 0f
                                                                }
                                                            }
                                                            val colsMoved = (dragOffsetX / thresholdX).toInt()
                                                            if (colsMoved != 0) {
                                                                val currentRow = realIndex / 3
                                                                val target = (realIndex + colsMoved).coerceIn(currentRow * 3, ((currentRow + 1) * 3 - 1).coerceAtMost(state.quickLinks.size - 1))
                                                                if (target != realIndex) {
                                                                    vm.swapQuickLinks(realIndex, target)
                                                                    draggedIndex = -1
                                                                    dragOffsetX = 0f
                                                                    dragOffsetY = 0f
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = { draggedIndex = -1; dragOffsetX = 0f; dragOffsetY = 0f },
                                                        onDragCancel = { draggedIndex = -1; dragOffsetX = 0f; dragOffsetY = 0f },
                                                    )
                                                }
                                            } else Modifier
                                        ),
                                ) {
                                    SortableQuickLinkCard(
                                        name = link.name,
                                        url = link.url,
                                        iconUri = link.iconUri,
                                        isSorting = isSorting.value,
                                        onLongClick = {
                                            if (!isSorting.value && hasLink) {
                                                contextMenuIndex = realIndex
                                            }
                                        },
                                        onClick = {
                                            if (!isSorting.value && hasLink) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                                    if (link.packageName.isNotBlank()) {
                                                        intent.setPackage(link.packageName)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: android.content.ActivityNotFoundException) {
                                                    try {
                                                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                                        context.startActivity(fallbackIntent)
                                                    } catch (e2: Exception) {
                                                        android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else if (!isSorting.value) {
                                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                vm.showQuickLinksSettings()
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    if (hasLink) {
                                        DropdownMenu(
                                            expanded = contextMenuIndex == realIndex,
                                            onDismissRequest = { contextMenuIndex = -1 },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("添加快捷方式到桌面", style = MaterialTheme.typography.bodyMedium) },
                                                onClick = {
                                                    contextMenuIndex = -1
                                                    pinQuickLinkShortcut(context, link, realIndex, vm.getQuickLinkStore())
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("设置图标", style = MaterialTheme.typography.bodyMedium) },
                                                onClick = {
                                                    contextMenuIndex = -1
                                                    onPickIcon?.invoke(realIndex)
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                            )
                                            if (link.iconUri.isNotBlank()) {
                                                DropdownMenuItem(
                                                    text = { Text("移除图标", style = MaterialTheme.typography.bodyMedium) },
                                                    onClick = {
                                                        contextMenuIndex = -1
                                                        vm.removeQuickLinkIcon(realIndex)
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                QuickLinkCard(
                                    name = "添加",
                                    url = "",
                                    onClick = {
                                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.showQuickLinksSettings()
                                    },
                                    modifier = Modifier.width(100.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (configuredLinks.isEmpty()) {
                    QuickLinkCard(
                        name = "管理",
                        url = "",
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.showQuickLinksSettings()
                        },
                        modifier = Modifier.width(100.dp),
                    )
                }
            }
        }
    }
}
