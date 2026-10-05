package com.inonvation.lightlife.ui.qzxy.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.inonvation.lightlife.data.qzxy.QzxyNearbyDevice
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.qzxy.QzxyShowerState
import com.inonvation.lightlife.ui.qzxy.QzxyUiState
import com.inonvation.lightlife.ui.theme.AppColors
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings

/** 洗澡卡的所有展示阶段，对应状态区与按钮行的内容切换 */
private enum class ShowerPhase { Guest, Unbound, Bound, Starting, Stopping, Running, Settle, Failed }

/**
 * 主页"洗澡"区块（趣智校园），与开水卡统一骨架：
 * 设备行（点设备名换设备）→ 状态区（原地切换）→ 按钮行（主按钮原地换字）。
 * 蓝牙款设备（communicationTypeId == 0）服务器无法远程开阀，只提示不支持。
 */
@Composable
fun QzxyShowerSection(state: AppUiState, vm: AppViewModel, haptic: HapticFeedback) {
    val q = state.qzxy
    val context = LocalContext.current
    // 权限弹窗的回调拿不到发起时的意图，用这个槽把「授权后要做什么」带回来：
    // 扫描和蓝牙款开阀都要权限，谁发起谁填
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (grants.values.all { it }) action?.invoke() else vm.qzxyOnScanPermissionDenied()
    }

    fun requestBleThen(action: () -> Unit) {
        val missing = requiredBlePermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
    var showErrorDetail by remember { mutableStateOf(false) }

    val phase = when {
        !q.loggedIn -> ShowerPhase.Guest
        q.boundDevice == null -> ShowerPhase.Unbound
        else -> when (val f = q.showerFlow) {
            QzxyShowerState.Idle -> ShowerPhase.Bound
            is QzxyShowerState.Starting -> ShowerPhase.Starting
            is QzxyShowerState.Stopping -> ShowerPhase.Stopping
            is QzxyShowerState.Running -> ShowerPhase.Running
            is QzxyShowerState.Done -> ShowerPhase.Settle
            is QzxyShowerState.Failed -> ShowerPhase.Failed
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacings.lg)) {
            // ── 行1 设备行 ──
            QzxyDeviceHeaderRow(q = q, phase = phase, vm = vm)

            // ── 行2 状态区（原地切换，带过渡动画） ──
            Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 8 })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "showerInfo",
                ) { p ->
                    Column(modifier = Modifier.padding(top = Spacings.md)) {
                        when (p) {
                            ShowerPhase.Guest -> QzxyHintInfo("未连接，登录趣智账号后可控制热水器")
                            ShowerPhase.Unbound -> QzxyHintInfo("扫描附近热水器，或输入机身 MAC 地址绑定")
                            ShowerPhase.Bound -> QzxyBoundInfo(q, vm)
                            ShowerPhase.Starting -> QzxyStepInfo((q.showerFlow as? QzxyShowerState.Starting)?.step ?: "正在准备…")
                            ShowerPhase.Stopping -> QzxyStepInfo((q.showerFlow as? QzxyShowerState.Stopping)?.step ?: "正在结束…")
                            ShowerPhase.Running -> QzxyRunningInfo(q)
                            ShowerPhase.Settle -> QzxySettleInfo(q)
                            ShowerPhase.Failed -> QzxyFailedInfo(q, onDetail = { showErrorDetail = true })
                        }
                    }
                }
            }

            // ── 行3 按钮行 ──
            Spacer(Modifier.height(Spacings.md))
            when (phase) {
                ShowerPhase.Guest -> Button(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.qzxyShowLogin()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("连接趣智校园", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                ShowerPhase.Unbound -> Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (q.scanning) vm.qzxyStopScan() else {
                                requestBleThen { vm.qzxyStartScan() }
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (q.scanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(Spacings.sm))
                        }
                        Text(if (q.scanning) "停止扫描" else "扫描附近设备", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(Spacings.sm))
                    OutlinedButton(
                        onClick = { vm.qzxyShowManualMacDialog() },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("手输 MAC")
                    }
                }
                ShowerPhase.Bound -> Button(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // 蓝牙款开阀要直连设备 GATT，缺权限时先请求（手动输 MAC 绑定的用户可能从未扫过描）
                        if (q.selectedDevice?.communicationTypeId == 0) {
                            requestBleThen { vm.qzxyStartShower() }
                        } else {
                            vm.qzxyStartShower()
                        }
                    },
                    enabled = qzxySnAvailable(q),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("开始洗澡", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                ShowerPhase.Starting -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("开启中…", style = MaterialTheme.typography.titleSmall)
                }
                ShowerPhase.Stopping -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("结束中…", style = MaterialTheme.typography.titleSmall)
                }
                ShowerPhase.Running -> OutlinedButton(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.qzxyStopShower()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.stop,
                    ),
                ) {
                    Text("结束使用", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                ShowerPhase.Settle -> OutlinedButton(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.qzxyDismissShowerFlow()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("完成")
                }
                ShowerPhase.Failed -> {
                    val retryingStop = q.activeOrder != null
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (retryingStop) {
                                    vm.qzxyStopShower()
                                } else if (q.selectedDevice?.communicationTypeId == 0) {
                                    requestBleThen { vm.qzxyStartShower() }
                                } else {
                                    vm.qzxyStartShower()
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.stop),
                        ) {
                            Text(
                                if (retryingStop) "重试关阀" else "重试开阀",
                                color = AppColors.white,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.width(Spacings.sm))
                        OutlinedButton(
                            onClick = { vm.qzxyDismissShowerFlow() },
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

    if (q.showLoginSheet) {
        QzxyLoginSheet(qzxy = q, vm = vm)
    }
    // 换设备弹层：点设备行弹出（与开水卡的设备选择交互一致）
    if (q.showDevicePicker && q.loggedIn && q.boundDevice != null) {
        QzxyDeviceSheet(q = q, vm = vm, haptic = haptic, hapticEnabled = state.hapticEnabled, requestBle = ::requestBleThen, context = context)
    }
    if (q.showManualMacDialog) {
        QzxyManualMacDialog(qzxy = q, vm = vm)
    }
    if (q.showLogoutConfirm) {
        QzxyLogoutConfirmDialog(qzxy = q, vm = vm)
    }
    if (showErrorDetail) {
        QzxyErrorDetailDialog(q = q) { showErrorDetail = false }
    }
}

/** 行1 设备行：绑定后点设备名弹出换设备弹层 */
@Composable
private fun QzxyDeviceHeaderRow(q: QzxyUiState, phase: ShowerPhase, vm: AppViewModel) {
    val title = when (phase) {
        ShowerPhase.Guest -> "淋浴 · 趣智校园"
        ShowerPhase.Unbound -> "未绑定设备"
        else -> q.selectedDevice?.displayName ?: q.boundDevice?.name ?: "未命名设备"
    }
    val changeable = phase == ShowerPhase.Bound
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (changeable) Modifier.clickable { vm.qzxySetDevicePicker(true) } else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (changeable) {
            Spacer(Modifier.width(Spacings.xs))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "更换设备",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        when (phase) {
            ShowerPhase.Bound -> {
                val info = q.selectedDevice
                val online = info?.onlineStatusId == 1
                QzxyStatusDot(
                    color = when {
                        info == null -> MaterialTheme.colorScheme.outline
                        online -> AppColors.runningIndicator
                        else -> MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    info?.onlineText ?: "未知状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ShowerPhase.Running, ShowerPhase.Starting, ShowerPhase.Stopping -> {
                QzxyStatusDot(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    "进行中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun QzxyStatusDot(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** 已绑定状态区：钱包 + 设备在线/通信类型与预扣 */
@Composable
private fun QzxyBoundInfo(q: QzxyUiState, vm: AppViewModel) {
    val info = q.selectedDevice
    val offline = info?.onlineStatusId == 0
    val bluetoothDevice = info?.communicationTypeId == 0
    val walletText = q.wallet?.money?.toDoubleOrNull()?.let { "¥%.2f".format(it) } ?: "-"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "钱包 $walletText",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { vm.qzxyRefreshWallet() }, modifier = Modifier.size(28.dp)) {
            if (q.loadingWallet) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "刷新钱包",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val statusLine = buildString {
        append(info?.onlineText ?: "状态未知")
        info?.communicationText?.let { append(" · ").append(it) }
        append(" · 预扣 ")
        append(info?.withholdMoney?.let { "¥%.2f".format(it) } ?: "-")
    }
    Text(
        statusLine,
        style = MaterialTheme.typography.bodySmall,
        color = if (offline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        !qzxySnAvailable(q) -> {
            Spacer(Modifier.height(Spacings.xs))
            Text(
                "设备信息不完整（缺少序列号），请更换设备重新绑定",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        bluetoothDevice -> {
            Spacer(Modifier.height(Spacings.xs))
            Text(
                "蓝牙款设备：开阀指令由手机蓝牙直发热水器，需站在设备旁操作。键盘使用码是备用开水方式。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (q.useCode == null) {
                LaunchedEffect(Unit) { vm.qzxyLoadUseCode() }
                Text(
                    "备用键盘使用码：获取中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "备用键盘使用码 ${q.useCode}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        offline -> {
            Spacer(Modifier.height(Spacings.xs))
            Text(
                "设备离线：热水器未连上趣智服务器（与手机蓝牙无关），请确认通电联网后重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun QzxyHintInfo(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 开阀/关阀进行中：转圈 + 当前步骤 */
@Composable
private fun QzxyStepInfo(step: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(Spacings.sm))
        Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 洗澡中：计时 + 预扣 + 闲置关停倒计时 */
@Composable
private fun QzxyRunningInfo(q: QzxyUiState) {
    val flow = q.showerFlow as? QzxyShowerState.Running ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            formatClock(q.elapsedSeconds),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AppColors.runningIndicator,
        )
        val sub = buildString {
            append("预扣 ")
            append(flow.withholdMoney)
            flow.autoCloseSecondsLeft?.let {
                append(" · 闲置关停 ")
                append(if (it > 0) formatClock(it) else "已到时间")
            }
        }
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 结算：本次消费金额 + 时长 */
@Composable
private fun QzxySettleInfo(q: QzxyUiState) {
    val result = (q.showerFlow as? QzxyShowerState.Done)?.result ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            result.consumeMoneyText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        val line = buildString {
            append("本次消费 · 用时 ")
            append(formatDuration(result.elapsedSeconds))
            append(" · ")
            append(result.deviceName)
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 失败：红字原因 + 详情入口 */
@Composable
private fun QzxyFailedInfo(q: QzxyUiState, onDetail: () -> Unit) {
    val flow = q.showerFlow as? QzxyShowerState.Failed ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(flow.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "失败步骤：${flow.step}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "查看详情 ›",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onDetail() },
            )
        }
    }
}

/** 换设备弹层：扫描 + 手输 MAC + 扫描结果列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QzxyDeviceSheet(
    q: QzxyUiState,
    vm: AppViewModel,
    haptic: HapticFeedback,
    hapticEnabled: Boolean,
    requestBle: (() -> Unit) -> Unit,
    context: Context,
) {
    ModalBottomSheet(onDismissRequest = { vm.qzxySetDevicePicker(false) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacings.xl)
                .padding(bottom = Spacings.xxl),
        ) {
            Text("更换淋浴设备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "扫描并点选附近热水器即可换绑；也可输入机身 MAC 地址",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacings.sm),
            )
            Spacer(Modifier.height(Spacings.lg))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (q.scanning) {
                            vm.qzxyStopScan()
                        } else {
                            requestBle { vm.qzxyStartScan() }
                        }
                    },
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
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("手输 MAC")
                }
            }
            Spacer(Modifier.height(Spacings.sm))
            if (q.scanning && q.nearbyDevices.isEmpty()) {
                Text(
                    "正在扫描附近设备…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            q.nearbyDevices.forEach { device ->
                QzxyDeviceRow(
                    device = device,
                    querying = q.queryingMac == device.mac,
                    selected = q.selectedDevice?.macAddress == device.mac,
                    onClick = {
                        vm.qzxySelectDevice(device)
                        vm.qzxySetDevicePicker(false)
                    },
                )
            }
            if (!q.scanning && q.nearbyDevices.isEmpty()) {
                Text(
                    "未发现设备：请确认热水器通电在线、手机蓝牙已开启并靠近设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QzxyDeviceRow(device: QzxyNearbyDevice, querying: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
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
                device.info?.communicationText?.let { append(" · ").append(it) }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** 失败详情弹窗：长解释收在这里，卡片上只留一行 */
@Composable
private fun QzxyErrorDetailDialog(q: QzxyUiState, onDismiss: () -> Unit) {
    val flow = q.showerFlow as? QzxyShowerState.Failed ?: return
    val offlineHint = "${flow.message} ${flow.rawError}".let { it.contains("不在线") || it.contains("离线") }
    val bluetoothDevice = q.selectedDevice?.communicationTypeId == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("失败详情", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(flow.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacings.sm))
                Text("失败步骤：${flow.step}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (flow.rawError.isNotBlank() && flow.rawError != flow.message) {
                    Spacer(Modifier.height(Spacings.xs))
                    Text("错误详情：${flow.rawError}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (bluetoothDevice) {
                    Spacer(Modifier.height(Spacings.sm))
                    Text(
                        "已确认该设备为蓝牙款：服务器无法远程开阀，本版本暂未支持手机蓝牙直控。请改用官方 App 开启，或在热水器键盘上输入使用码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (offlineHint) {
                    Spacer(Modifier.height(Spacings.sm))
                    Text(
                        "「设备不在线」指热水器没连上趣智服务器，与手机蓝牙无关。请确认热水器已通电、已联网，稍后可重试；也可用官方 App 试开同一台设备对比。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (q.activeOrder != null) {
                    Spacer(Modifier.height(Spacings.sm))
                    Text(
                        "设备可能仍在出水，建议重试关阀。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
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

private fun qzxySnAvailable(q: QzxyUiState): Boolean {
    val info = q.selectedDevice
    return info?.snCode?.isNotBlank() == true || q.boundDevice?.snCode?.isNotBlank() == true
}

// ── 蓝牙权限 ──

private fun requiredBlePermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // Android 11 及以下 BLE 扫描要定位权限，官方要求 FINE 与 COARSE 成对声明和请求
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
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
