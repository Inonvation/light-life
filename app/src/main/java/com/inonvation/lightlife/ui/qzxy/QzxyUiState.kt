package com.inonvation.lightlife.ui.qzxy

import com.inonvation.lightlife.data.qzxy.QzxyActiveShower
import com.inonvation.lightlife.data.qzxy.QzxyBoundDevice
import com.inonvation.lightlife.data.qzxy.QzxyDeviceInfo
import com.inonvation.lightlife.data.qzxy.QzxyNearbyDevice
import com.inonvation.lightlife.data.qzxy.QzxySettleResult
import com.inonvation.lightlife.data.qzxy.QzxyWalletData

/**
 * 趣智校园模块的全部 UI 状态，作为整体挂进 AppUiState（val qzxy），
 * 与缺渴平台的状态互不交叉。
 */
data class QzxyUiState(
    // ── 登录 ──
    val loggedIn: Boolean = false,
    val userName: String = "",
    /** 登录账号（脱敏后，如 139****7302），设置页展示用 */
    val accountPhone: String = "",
    val showLoginSheet: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val phone: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loggingIn: Boolean = false,
    val loginError: String? = null,

    // ── 绑定设备（主操作对象）──
    val boundDevice: QzxyBoundDevice? = null,
    /** 绑定设备的最新详情（在线状态、预扣金额），打开 App 时静默刷新 */
    val selectedDevice: QzxyDeviceInfo? = null,
    val queryingMac: String? = null,

    // ── 更换设备（次要入口，展开后可见）──
    val showDevicePicker: Boolean = false,
    val scanning: Boolean = false,
    val nearbyDevices: List<QzxyNearbyDevice> = emptyList(),
    val showManualMacDialog: Boolean = false,
    val manualMacInput: String = "",

    // ── 洗澡流程 ──
    val showerFlow: QzxyShowerState = QzxyShowerState.Idle,
    val elapsedSeconds: Int = 0,
    val activeOrder: QzxyActiveShower? = null,

    // ── 钱包 ──
    val wallet: QzxyWalletData? = null,
    val loadingWallet: Boolean = false,
)

/**
 * 一次洗澡的状态机，仿照 UnlockFlowState 的写法。
 * 会话失效不进状态机：直接清空趣智状态并弹出重新登录（见 QzxyController）。
 */
sealed class QzxyShowerState {
    data object Idle : QzxyShowerState()

    /** 开阀流程：发送指令 → 确认开阀 → 轮询订单号 */
    data class Starting(val step: String) : QzxyShowerState()

    data class Running(
        val deviceName: String,
        val withholdMoney: String,
        /** 闲置自动关停倒计时（秒），接口未返回时为 null */
        val autoCloseSecondsLeft: Int? = null,
    ) : QzxyShowerState()

    /** 关阀流程：发送指令 → 确认关阀 → 查询消费 */
    data class Stopping(val step: String) : QzxyShowerState()

    data class Done(val result: QzxySettleResult) : QzxyShowerState()

    data class Failed(
        val message: String,
        val step: String,
        val rawError: String,
    ) : QzxyShowerState()
}
