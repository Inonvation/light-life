package com.inonvation.lightlife.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.data.DeviceItem
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.UnlockFlowState
import com.inonvation.lightlife.ui.pinDeviceShortcut
import com.inonvation.lightlife.ui.qzxy.screen.QzxyShowerSection
import com.inonvation.lightlife.ui.theme.AppColors
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings

/**
 * 最终版单页主界面：统计 / 快捷方式 / 开水 / 签到 自上而下排列。
 */
@Composable
fun SimpleScreen(state: AppUiState, vm: AppViewModel, onPickIcon: ((Int) -> Unit)? = null) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedDevice: DeviceItem? by remember { mutableStateOf(state.devices.firstOrNull()) }
    var showDetailDialog by remember { mutableStateOf(false) }
    val successResult = (state.unlockFlowState as? UnlockFlowState.Success)?.result
    val failedState = state.unlockFlowState as? UnlockFlowState.Failed

    LaunchedEffect(state.devices) {
        if (selectedDevice == null || state.devices.none { it.id == selectedDevice!!.id }) {
            selectedDevice = state.devices.firstOrNull()
        }
    }

    val pullRefreshState = rememberPullToRefreshState()
    val isRefreshing = state.loadingBalance || state.loadingDevices

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LightLife", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.hasToken) "已登录" else "未登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.showSettings() }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置")
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (state.hasToken) {
                    vm.refreshDevices()
                    vm.refreshBalance()
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Spacings.md)
            ) {
                if (!state.hasToken) {
                    item {
                        Spacer(Modifier.height(Spacings.sm))
                        LoginCard(
                            state = state,
                            onUpdatePhone = { vm.updatePhone(it) },
                            onUpdateCode = { vm.updateCode(it) },
                            onSendCode = { vm.sendCode() },
                            onLogin = { vm.login() },
                            onToggleTokenLogin = { vm.toggleTokenLogin() },
                            onUpdateTokenLoginInput = { vm.updateTokenLoginInput(it) },
                            onToggleTokenLoginVisibility = { vm.toggleTokenLoginVisibility() },
                            onLoginWithToken = { vm.loginWithToken() },
                            haptic = haptic,
                        )
                    }
                    return@LazyColumn
                }

                // 积分余额卡
                item { StatsCard(state, vm, haptic) }

                // 快捷方式区
                // 快捷方式区（设置中可关闭）
                if (state.quickLinksEnabled) {
                    item { QuickLinksSection(state, vm, onPickIcon, cardVisible = true, haptic = haptic, context = ctx) }
                }

                // 开水区
                item {
                    WaterCard(
                        state = state,
                        vm = vm,
                        selectedDevice = selectedDevice,
                        onSelectDevice = { selectedDevice = it },
                        onShowDetail = { showDetailDialog = true },
                        haptic = haptic,
                        context = ctx,
                    )
                }

                // 淋浴区（趣智校园）
                item { QzxyShowerSection(state = state, vm = vm, haptic = haptic) }

                // 签到区
                item { SignInCard(state, vm, haptic) }

                item {
                    Text(
                        text = "LightLife v${state.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacings.xl),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // 开水成功/失败详情弹窗
    if (successResult != null && showDetailDialog) {
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
                    if (failedState.step != "未知") {
                        Text("失败步骤：${failedState.step}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    failedState.suggestions.forEach { s ->
                        Text("• $s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false; vm.dismissUnlockFlow() }) {
                    Text("关闭")
                }
            },
        )
    }
}

@Composable
private fun StatsCard(state: AppUiState, vm: AppViewModel, haptic: HapticFeedback) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Spacings.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("积分余额", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("点击刷新获取最新数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.refreshBalance() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新余额", modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(Spacings.md))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("当前积分", state.balance?.pointsText ?: "-")
                StatColumn("可抵扣金额", state.balance?.integralAmount?.let { "¥$it" } ?: "-")
                StatColumn("剩余小票", state.balance?.ticketText?.let { "¥$it" } ?: "-")
            }
            Spacer(Modifier.height(Spacings.md))
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.showOrderHistory()
                        }
                    )
                    .padding(top = Spacings.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "累计开水 ${state.totalWaterCount} 次",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (state.orderHistory.isEmpty()) "暂无订单" else "共 ${state.orderHistory.size} 笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        RollingDigits(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun WaterCard(
    state: AppUiState,
    vm: AppViewModel,
    selectedDevice: DeviceItem?,
    onSelectDevice: (DeviceItem) -> Unit,
    onShowDetail: () -> Unit,
    haptic: HapticFeedback,
    context: android.content.Context,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Spacings.lg)) {
            Text("开水", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))

            if (state.devices.isEmpty()) {
                Text("暂无设备，请先刷新", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.refreshDevices() }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新设备")
                }
            } else {
                state.devices.forEach { device ->
                    val isSelected = device.id == selectedDevice?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSelectDevice(device) },
                                onLongClick = {
                                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pinDeviceShortcut(context, device)
                                }
                            )
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Devices,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            device.goodsName.ifBlank { "未命名设备" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "已选择", tint = AppColors.runningIndicator, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pinDeviceShortcut(context, device)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "添加到桌面",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.sm))
            HorizontalDivider()
            Spacer(Modifier.height(Spacings.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("使用积分抵扣", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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

            Spacer(Modifier.height(Spacings.md))

            Button(
                onClick = {
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val device = selectedDevice
                    if (device != null) vm.unlock(device)
                },
                enabled = selectedDevice != null && !state.unlocking,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (state.unlocking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(Spacings.sm))
                }
                Text(if (state.unlocking) "开水进行中…" else "开水", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            // 解锁流程内联状态
            if (state.unlockFlowState !is UnlockFlowState.Idle) {
                Spacer(Modifier.height(Spacings.sm))
                InlineUnlockStatus(
                    flowState = state.unlockFlowState,
                    elapsedSeconds = state.unlockElapsedSeconds,
                    onShowDetail = onShowDetail,
                )
            }
        }
    }
}

@Composable
private fun SignInCard(state: AppUiState, vm: AppViewModel, haptic: HapticFeedback) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacings.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(
                    if (state.signInDoneToday) AppColors.runningIndicator.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (state.signInDoneToday) AppColors.runningIndicator else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(Spacings.md))
            Column(modifier = Modifier.weight(1f)) {
                Text("每日签到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.signInDoneToday) "今日已签到" else "今日还未签到",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.signInDoneToday) AppColors.runningIndicator else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.signInNow()
                },
                enabled = !state.signInDoneToday && !state.signingIn,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.signInDoneToday) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    contentColor = if (state.signInDoneToday) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (state.signingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (state.signingIn) "签到中…" else if (state.signInDoneToday) "已签到" else "立即签到")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
