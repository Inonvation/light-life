package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
 * 最终版单页主界面：账户状态行 / 开水 / 洗澡 / 快捷方式 自上而下排列。
 * 开水与洗澡使用同一套卡片骨架（设备行 / 状态区 / 按钮行），
 * 进行中、结算、失败都在原卡状态区原地切换，不弹窗、不插新卡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleScreen(state: AppUiState, vm: AppViewModel, onPickIcon: ((Int) -> Unit)? = null) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedDevice: DeviceItem? by remember { mutableStateOf(state.devices.firstOrNull()) }
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showWaterDetail by remember { mutableStateOf(false) }

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
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("LightLife", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.balance?.pointsText ?: "-",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "分",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { vm.showSettings() }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                }
            }
        },
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Spacings.md),
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

                // 账户状态行：小票 + 累计开水 + 签到按钮
                item { AccountStripRow(state, vm, haptic) }

                // 开水
                item { SectionLabel("开水") }
                item {
                    WaterCard(
                        state = state,
                        vm = vm,
                        selectedDevice = selectedDevice,
                        onSelectDevice = { selectedDevice = it },
                        onShowDetail = { showWaterDetail = true },
                        onPickDevice = { showDeviceSheet = true },
                        haptic = haptic,
                        context = ctx,
                    )
                }

                // 洗澡
                item { SectionLabel("洗澡") }
                item { QzxyShowerSection(state = state, vm = vm, haptic = haptic) }

                // 快捷方式
                if (state.quickLinksEnabled) {
                    item { SectionLabel("快捷方式") }
                    item { QuickLinksSection(state, vm, onPickIcon, cardVisible = true, haptic = haptic, context = ctx) }
                }

                item {
                    Text(
                        text = "LightLife v${state.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacings.lg),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // 开水设备选择弹层
    if (showDeviceSheet) {
        WaterDeviceSheet(
            state = state,
            selectedDevice = selectedDevice,
            onSelectDevice = {
                selectedDevice = it
                showDeviceSheet = false
            },
            onDismiss = { showDeviceSheet = false },
            haptic = haptic,
            context = ctx,
        )
    }

    // 开水成功详情弹窗（点"订单详情"查看）
    val successResult = (state.unlockFlowState as? UnlockFlowState.Success)?.result
    if (successResult != null && showWaterDetail) {
        AlertDialog(
            onDismissRequest = { showWaterDetail = false },
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
                TextButton(onClick = { showWaterDetail = false }) {
                    Text("关闭")
                }
            },
        )
    }
    val failedState = state.unlockFlowState as? UnlockFlowState.Failed
    if (failedState != null && showWaterDetail) {
        AlertDialog(
            onDismissRequest = { showWaterDetail = false },
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
                TextButton(onClick = { showWaterDetail = false }) {
                    Text("关闭")
                }
            },
        )
    }
}

/** 顶栏下的一条账户状态行：小票余额 + 累计开水 + 签到按钮 */
@Composable
private fun AccountStripRow(state: AppUiState, vm: AppViewModel, haptic: HapticFeedback) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ticket = state.balance?.ticketText?.let { "¥$it" } ?: "-"
            Text(
                "小票 $ticket · 累计开水 ${state.totalWaterCount} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.signInNow()
                },
                enabled = !state.signInDoneToday && !state.signingIn,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp),
                colors = if (state.signInDoneToday) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                if (state.signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (state.signingIn) "签到中…" else if (state.signInDoneToday) "已签到" else "签到",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(Spacings.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

private enum class WaterPhase { Idle, Busy, Success, Failed }

/** 开水卡：与洗澡卡统一骨架（设备行 / 状态区 / 按钮行） */
@Composable
private fun WaterCard(
    state: AppUiState,
    vm: AppViewModel,
    selectedDevice: DeviceItem?,
    onSelectDevice: (DeviceItem) -> Unit,
    onShowDetail: () -> Unit,
    onPickDevice: () -> Unit,
    haptic: HapticFeedback,
    context: android.content.Context,
) {
    val flow = state.unlockFlowState
    val phase = when (flow) {
        is UnlockFlowState.Idle -> WaterPhase.Idle
        is UnlockFlowState.Success -> WaterPhase.Success
        is UnlockFlowState.Failed -> WaterPhase.Failed
        else -> WaterPhase.Busy
    }
    val deviceName = selectedDevice?.goodsName?.ifBlank { "未命名设备" } ?: "暂无设备"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacings.lg)) {
            // ── 行1 设备行 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = phase == WaterPhase.Idle && state.devices.isNotEmpty()) { onPickDevice() }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(deviceName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (state.devices.isEmpty()) {
                    Spacer(Modifier.width(Spacings.sm))
                    Text(
                        "刷新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { vm.refreshDevices() },
                    )
                } else if (phase == WaterPhase.Idle) {
                    Spacer(Modifier.width(Spacings.xs))
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "选择设备",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 行2 状态区（原地切换，带过渡动画） ──
            Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 8 })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "waterInfo",
                ) { p ->
                    Column(modifier = Modifier.padding(top = Spacings.md)) {
                        when (p) {
                            WaterPhase.Idle -> Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
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
                                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                                )
                            }
                            WaterPhase.Busy -> Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    formatClock(state.unlockElapsedSeconds),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        when (val f = state.unlockFlowState) {
                                            is UnlockFlowState.PreChecking -> f.step
                                            is UnlockFlowState.Working -> f.step
                                            else -> "正在处理…"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            WaterPhase.Success -> {
                                val r = (state.unlockFlowState as UnlockFlowState.Success).result
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        r.ticketCost.ifBlank { "¥${r.originPrice}" },
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(successSubtitle(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "订单详情 ›",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { onShowDetail() },
                                        )
                                    }
                                }
                            }
                            WaterPhase.Failed -> {
                                val f = state.unlockFlowState as UnlockFlowState.Failed
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(f.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "失败步骤：${f.step}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "查看详情 ›",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { onShowDetail() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 行3 按钮行 ──
            Spacer(Modifier.height(Spacings.md))
            when (phase) {
                WaterPhase.Idle -> Button(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val device = selectedDevice
                        if (device != null) vm.unlock(device)
                    },
                    enabled = selectedDevice != null && !state.unlocking,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("开水", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                WaterPhase.Busy -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("开水进行中…", style = MaterialTheme.typography.titleSmall)
                }
                WaterPhase.Success -> OutlinedButton(
                    onClick = { vm.dismissUnlockFlow() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("完成")
                }
                WaterPhase.Failed -> Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val device = selectedDevice
                            if (device != null) vm.unlock(device)
                        },
                        enabled = selectedDevice != null,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("重试", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(Spacings.sm))
                    OutlinedButton(
                        onClick = { vm.dismissUnlockFlow() },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

/** 开水设备选择弹层：点选切换，长按或点 ＋ 添加到桌面 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WaterDeviceSheet(
    state: AppUiState,
    selectedDevice: DeviceItem?,
    onSelectDevice: (DeviceItem) -> Unit,
    onDismiss: () -> Unit,
    haptic: HapticFeedback,
    context: android.content.Context,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacings.xl)
                .padding(bottom = Spacings.xxl),
        ) {
            Text("选择开水设备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "点选切换设备；长按或点右侧 ＋ 可添加到桌面",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacings.sm),
            )
            Spacer(Modifier.height(Spacings.lg))
            if (state.devices.isEmpty()) {
                Text(
                    "暂无设备，请在主页下拉刷新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.devices.forEach { device ->
                val isSelected = device.id == selectedDevice?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { onSelectDevice(device) },
                            onLongClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pinDeviceShortcut(context, device)
                            },
                        )
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacings.sm))
                    Text(
                        device.goodsName.ifBlank { "未命名设备" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "已选择",
                            tint = AppColors.runningIndicator,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacings.xs))
                    }
                    IconButton(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pinDeviceShortcut(context, device)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "添加到桌面",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun successSubtitle(r: com.inonvation.lightlife.data.UnlockResult): String {
    val parts = buildList {
        if (r.integralCost != "-") add("积分抵扣 ${r.integralCost}")
        r.otherPromotions.forEach { p -> p.discountAmount?.let { add("其他优惠 $it") } }
    }
    return parts.joinToString(" · ").ifBlank { "小票支付" }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatClock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
