package com.inonvation.lightlife.ui.qzxy.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shower
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.inonvation.lightlife.data.qzxy.QzxyNearbyDevice
import com.inonvation.lightlife.data.qzxy.QzxySettleResult
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.qzxy.QzxyShowerState
import com.inonvation.lightlife.ui.qzxy.QzxyUiState
import com.inonvation.lightlife.ui.theme.AppColors
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings
import com.inonvation.lightlife.ui.theme.successContainerColor

/**
 * 主页"淋浴"区块（趣智校园），插在开水区块与签到区块之间。
 * 绑定设备后以绑定设备的操作为主（开始洗澡大按钮），
 * 扫描/手输 MAC 收进"更换设备"次要入口。
 */
@Composable
fun QzxyShowerSection(state: AppUiState, vm: AppViewModel, haptic: HapticFeedback) {
    val q = state.qzxy
    if (!q.loggedIn) {
        QzxyGuestCard(state = state, vm = vm, haptic = haptic)
    } else {
        QzxyLoggedInCard(state = state, vm = vm, haptic = haptic)
    }

    if (q.showLoginSheet) {
        QzxyLoginSheet(qzxy = q, vm = vm)
    }
    if (q.showManualMacDialog) {
        QzxyManualMacDialog(qzxy = q, vm = vm)
    }
    if (q.showLogoutConfirm) {
        QzxyLogoutConfirmDialog(qzxy = q, vm = vm)
    }
}

/** 退出趣智登录二次确认；洗澡中追加计费警告 */
@Composable
private fun QzxyLogoutConfirmDialog(qzxy: QzxyUiState, vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = { vm.qzxyDismissLogoutConfirm() },
        title = { Text("确认退出趣智登录", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("退出后需要重新输入手机号和密码才能使用淋浴功能，已绑定的设备会保留。")
                if (qzxy.showerFlow is QzxyShowerState.Running || qzxy.showerFlow is QzxyShowerState.Starting) {
                    Spacer(Modifier.height(Spacings.sm))
                    Text(
                        "注意：当前有进行中的洗澡订单，退出后设备仍会继续出水计费，且无法在 App 内结束本次使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.qzxyDismissLogoutConfirm()
                vm.qzxyLogout()
            }) { Text("退出") }
        },
        dismissButton = {
            TextButton(onClick = { vm.qzxyDismissLogoutConfirm() }) { Text("取消") }
        },
    )
}

@Composable
private fun QzxyGuestCard(state: AppUiState, vm: AppViewModel, haptic: HapticFeedback) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacings.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Shower,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(Spacings.md))
            Text("淋浴 · 趣智校园", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "控制校园热水器（趣智校园平台），与开水功能互不影响",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacings.md))
            Button(
                onClick = {
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.qzxyShowLogin()
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("连接趣智校园")
            }
        }
    }
}

@Composable
private fun QzxyLoggedInCard(state: AppUiState, vm: AppViewModel, haptic: HapticFeedback) {
    val q = state.qzxy
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) vm.qzxyStartScan() else vm.qzxyOnScanPermissionDenied()
    }

    val walletText = q.wallet?.money?.toDoubleOrNull()?.let { "钱包 ¥%.2f".format(it) } ?: "钱包 -"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacings.lg)) {
            // ── 头部 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("淋浴 · 趣智校园", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(walletText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { vm.qzxyRefreshWallet() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "刷新钱包",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── 洗澡流程卡 ──
            when (val flow = q.showerFlow) {
                is QzxyShowerState.Idle -> {}
                is QzxyShowerState.Starting -> QzxyBusyCard(
                    title = "正在开启",
                    step = flow.step,
                    elapsedSeconds = q.elapsedSeconds,
                )
                is QzxyShowerState.Running -> QzxyRunningCard(
                    flow = flow,
                    elapsedSeconds = q.elapsedSeconds,
                    haptic = haptic,
                    hapticEnabled = state.hapticEnabled,
                    onStop = { vm.qzxyStopShower() },
                )
                is QzxyShowerState.Stopping -> QzxyBusyCard(
                    title = "正在结束",
                    step = flow.step,
                    elapsedSeconds = q.elapsedSeconds,
                )
                is QzxyShowerState.Done -> QzxySettleCard(
                    result = flow.result,
                    onDismiss = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.qzxyDismissShowerFlow()
                    },
                )
                is QzxyShowerState.Failed -> QzxyFailedCard(
                    flow = flow,
                    canRetryStop = q.activeOrder != null,
                    onRetry = { vm.qzxyStopShower() },
                    onDismiss = { vm.qzxyDismissShowerFlow() },
                )
            }

            // ── 绑定设备（主操作） ──
            val bound = q.boundDevice
            if (bound != null) {
                QzxyBoundDeviceBlock(q = q, vm = vm, haptic = haptic, hapticEnabled = state.hapticEnabled)
            }

            // ── 设备选择（未绑定，或展开"更换设备"）──
            if (bound == null || q.showDevicePicker) {
                if (bound != null) {
                    Spacer(Modifier.height(Spacings.sm))
                    HorizontalDivider()
                    Spacer(Modifier.height(Spacings.sm))
                }
                QzxyDevicePickerSection(q = q, vm = vm, haptic = haptic, hapticEnabled = state.hapticEnabled, permissionLauncher = permissionLauncher, context = context)
            }

        }
    }
}

/** 绑定设备主块：设备信息 + 开始按钮 + 更换设备入口 */
@Composable
private fun QzxyBoundDeviceBlock(
    q: QzxyUiState,
    vm: AppViewModel,
    haptic: HapticFeedback,
    hapticEnabled: Boolean,
) {
    val bound = q.boundDevice ?: return
    val info = q.selectedDevice
    val offline = info?.onlineStatusId == 0
    val snAvailable = info?.snCode?.isNotBlank() == true || bound.snCode.isNotBlank()
    val flowIdle = q.showerFlow is QzxyShowerState.Idle

    Spacer(Modifier.height(Spacings.md))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(Spacings.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(info?.displayName ?: bound.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        bound.mac,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { vm.qzxyRefreshSelectedDevice() },
                    modifier = Modifier.size(32.dp),
                ) {
                    if (q.queryingMac != null && q.queryingMac == info?.macAddress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "刷新设备状态",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacings.xs))
            val statusParts = buildList {
                info?.onlineText?.let { add(it) }
                add(info?.withholdMoney?.let { "预扣 ¥%.2f".format(it) } ?: "预扣 -")
            }
            val statusColor = when {
                offline -> MaterialTheme.colorScheme.error
                info?.onlineStatusId == 1 -> AppColors.runningIndicator
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                statusParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )

            if (!snAvailable) {
                Spacer(Modifier.height(Spacings.xs))
                Text(
                    "设备信息不完整（缺少序列号），请重新扫描或手输 MAC 绑定",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (offline) {
                Spacer(Modifier.height(Spacings.xs))
                Text(
                    "设备当前离线：热水器没有连上趣智服务器（与手机蓝牙无关），开阀命令无法送达。请确认设备已通电、已联网，或稍后点右上角刷新再试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacings.md))
            Button(
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.qzxyStartShower()
                },
                enabled = flowIdle && snAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("开始洗澡", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(Spacings.xs))
            Text(
                if (q.showDevicePicker) "收起更换设备 ▴" else "更换设备 ▾",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = flowIdle) { vm.qzxySetDevicePicker(!q.showDevicePicker) }
                    .padding(vertical = Spacings.xs),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 设备选择区：扫描 + 手输 MAC + 扫描结果列表（未绑定时为主界面，绑定后藏进"更换设备"） */
@Composable
private fun QzxyDevicePickerSection(
    q: QzxyUiState,
    vm: AppViewModel,
    haptic: HapticFeedback,
    hapticEnabled: Boolean,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    context: Context,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (q.boundDevice != null) {
            Text(
                "扫描并点选附近热水器即可换绑",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacings.sm))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (q.scanning) {
                        vm.qzxyStopScan()
                    } else {
                        requestBlePermissionOrScan(context, permissionLauncher::launch) { vm.qzxyStartScan() }
                    }
                },
                enabled = q.showerFlow is QzxyShowerState.Idle,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (q.scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Spacings.sm))
                }
                Text(if (q.scanning) "停止扫描" else "扫描附近设备")
            }
            Spacer(Modifier.width(Spacings.sm))
            OutlinedButton(
                onClick = { vm.qzxyShowManualMacDialog() },
                enabled = q.showerFlow is QzxyShowerState.Idle,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("手输 MAC")
            }
        }

        if (q.nearbyDevices.isEmpty() && !q.scanning) {
            Spacer(Modifier.height(Spacings.sm))
            Text(
                "蓝牙只用于发现设备；控制命令由趣智服务器下发，需要热水器自身联网在线",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        q.nearbyDevices.forEach { device ->
            QzxyDeviceRow(
                device = device,
                querying = q.queryingMac == device.mac,
                selected = q.selectedDevice?.macAddress == device.mac,
                onClick = { vm.qzxySelectDevice(device) },
            )
        }
    }
}

@Composable
private fun QzxyDeviceRow(device: QzxyNearbyDevice, querying: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Devices,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacings.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.displayName, style = MaterialTheme.typography.bodyMedium)
            val subtitle = buildString {
                append(device.signalText)
                append(" · ")
                append(device.rssi)
                append(" dBm")
                device.info?.onlineText?.let { append(" · ").append(it) }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (device.nameLoading) {
                Text(
                    "正在获取设备名…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (querying) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已选择",
                tint = AppColors.runningIndicator,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 开阀/关阀进行中的过渡卡片 */
@Composable
private fun QzxyBusyCard(title: String, step: String, elapsedSeconds: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacings.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(Spacings.sm))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (elapsedSeconds > 0) {
                Text(
                    formatClock(elapsedSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 洗澡中常驻卡片：计时 + 预扣 + 闲置倒计时 + 结束按钮 */
@Composable
private fun QzxyRunningCard(
    flow: QzxyShowerState.Running,
    elapsedSeconds: Int,
    haptic: HapticFeedback,
    hapticEnabled: Boolean,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(containerColor = successContainerColor()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacings.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("洗澡中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                formatClock(elapsedSeconds),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                flow.deviceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "预扣 ${flow.withholdMoney}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            flow.autoCloseSecondsLeft?.let { left ->
                Text(
                    if (left > 0) "闲置自动关停 ${formatClock(left)}" else "已到闲置关停时间，可能已自动关停",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(Spacings.md))
            Button(
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStop()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.stop),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("结束使用", color = AppColors.white, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 结算卡片：本次消费金额 + 时长 */
@Composable
private fun QzxySettleCard(result: QzxySettleResult, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(containerColor = successContainerColor()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacings.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("本次使用结束", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                result.consumeMoneyText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${result.deviceName} · 用时 ${formatDuration(result.elapsedSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.consumeTime?.let {
                Text(
                    "结算时间 $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacings.md))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Text("完成")
            }
        }
    }
}

/** 失败卡片：区分"关阀未确认"（设备可能仍在出水）和"设备离线"（命令没送达）两类场景 */
@Composable
private fun QzxyFailedCard(
    flow: QzxyShowerState.Failed,
    canRetryStop: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val offlineHint = "${flow.message} ${flow.rawError}".let { it.contains("不在线") || it.contains("离线") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacings.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("操作失败", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                flow.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                "失败步骤：${flow.step}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
            )
            if (offlineHint) {
                Text(
                    "「设备不在线」指热水器没连上趣智服务器，与手机蓝牙无关。请确认热水器已通电、已联网，稍后可重试；也可用官方 App 试开同一台设备对比。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                )
            }
            if (canRetryStop) {
                Text(
                    "设备可能仍在出水，建议重试关阀",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (flow.rawError.isNotBlank() && flow.rawError != flow.message) {
                Text(
                    "错误详情：${flow.rawError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacings.md))
            Row {
                if (canRetryStop) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.stop),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("重试关阀", color = AppColors.white)
                    }
                    Spacer(Modifier.width(Spacings.sm))
                }
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun QzxyManualMacDialog(qzxy: QzxyUiState, vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = { vm.qzxyDismissManualMacDialog() },
        title = { Text("手输 MAC 地址", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "热水器机身贴纸或二维码上的 MAC 地址，凯路设备一般以 C4:7F:0E 开头，形如 C4:7F:0E:12:34:56",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacings.md))
                OutlinedTextField(
                    value = qzxy.manualMacInput,
                    onValueChange = { vm.qzxyUpdateManualMac(it) },
                    singleLine = true,
                    placeholder = { Text("C4:7F:0E:12:34:56") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.qzxySubmitManualMac() }) { Text("查询并绑定") }
        },
        dismissButton = {
            TextButton(onClick = { vm.qzxyDismissManualMacDialog() }) { Text("取消") }
        },
    )
}

// ── 蓝牙权限 ──

private fun requiredBlePermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun requestBlePermissionOrScan(
    context: Context,
    launchPermissions: (Array<String>) -> Unit,
    onReady: () -> Unit,
) {
    val all = requiredBlePermissions()
    val missing = all.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
    if (missing.isEmpty()) onReady() else launchPermissions(missing.toTypedArray())
}

// ── 时间格式化 ──

private fun formatClock(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}小时${m}分"
        m > 0 -> "${m}分${s}秒"
        else -> "${s}秒"
    }
}
