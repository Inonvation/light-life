package com.inonvation.lightlife.ui

import com.inonvation.lightlife.data.BalanceData
import com.inonvation.lightlife.data.DeviceItem
import com.inonvation.lightlife.data.OrderHistoryItem
import com.inonvation.lightlife.data.QuickLink
import com.inonvation.lightlife.data.UnlockResult
import com.inonvation.lightlife.ui.theme.ColorTheme
import com.inonvation.lightlife.ui.theme.ThemeMode

enum class DeviceTab { Control, Points, Me }

data class DeviceShortcutRequest(
    val goodsId: String?,
    val id: String?,
    val goodsName: String?,
)

sealed class UnlockFlowState {
    data object Idle : UnlockFlowState()
    data object PreChecking : UnlockFlowState()
    data class Working(val step: String, val elapsedSeconds: Int = 0) : UnlockFlowState()
    data class Success(val result: UnlockResult) : UnlockFlowState()
    data class Failed(
        val message: String,
        val step: String,
        val rawError: String,
        val suggestions: List<String> = emptyList(),
    ) : UnlockFlowState()
}

enum class LogLevel { INFO, SUCCESS, WARN, ERROR }

data class LogEntry(
    val timestamp: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val collapsed: Boolean = false,
    val centered: Boolean = false,
    val id: Long = logIdCounter.getAndIncrement(),
) {
    companion object {
        private val logIdCounter = java.util.concurrent.atomic.AtomicLong(0)
    }
}

data class AppUiState(
    // ── 导航 ──
    val currentTab: DeviceTab = DeviceTab.Control,
    val hasToken: Boolean = false,

    // ── 登录 ──
    val phone: String = "",
    val code: String = "",
    val phoneError: String? = null,
    val sendingCode: Boolean = false,
    val loggingIn: Boolean = false,
    val showTokenLogin: Boolean = false,
    val tokenLoginInput: String = "",
    val tokenLoginVisible: Boolean = false,
    val tokenLoggingIn: Boolean = false,

    // ── 设备 ──
    val loadingDevices: Boolean = false,
    val loadingBalance: Boolean = false,
    val devices: List<DeviceItem> = emptyList(),
    val balance: BalanceData? = null,

    // ── 解锁 ──
    val unlocking: Boolean = false,
    val unlockStatus: String? = null,
    val unlockFlowState: UnlockFlowState = UnlockFlowState.Idle,
    val unlockElapsedSeconds: Int = 0,
    val orderDetail: UnlockResult? = null,
    val usePointsForUnlock: Boolean = true,

    // ── 积分任务 ──
    val runningPointsTask: Boolean = false,
    val pointsTaskPaused: Boolean = false,
    val signInDone: Boolean = false,
    val taskListDone: Boolean = false,
    val appVideoCount: Int = 0,
    val alipayVideoCount: Int = 0,
    val alipayVideoTaskCount: Int = 0,
    val adTaskCount: Int = 0,
    val adTaskDone: Boolean = false,
    val otherTaskDone: Boolean = false,
    val homePageCount: Int = 0,
    val homePageDone: Boolean = false,
    val appVideoDone: Boolean = false,
    val alipayVideoTaskDone: Boolean = false,
    val todayAllDone: Boolean = false,
    val pointsLogs: List<LogEntry> = emptyList(),

    // ── 统计 ──
    val todayWaterCount: Int = 0,
    val todayWaterAmount: String = "0.00",
    val totalWaterCount: Int = 0,
    val totalPointsDeducted: String = "0.00",
    val orderHistory: List<OrderHistoryItem> = emptyList(),

    // ── 解锁动画控制 ──
    val unlockFlowHidden: Boolean = false,

    // ── 设置 ──
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorTheme: ColorTheme = ColorTheme.GREEN,
    val hapticEnabled: Boolean = true,
    val backupPrivacySafe: Boolean = false,
    val simpleModeEnabled: Boolean = false,
    val safeModeEnabled: Boolean = false,
    val debugLogEnabled: Boolean = false,
    val autoStartTaskEnabled: Boolean = false,
    val backgroundTaskEnabled: Boolean = true,
    val randomDelayEnabled: Boolean = false,
    val userAgent: String = "",
    
    // ── 定时任务 ──
    val scheduleEnabled: Boolean = false,
    val scheduleTimeSlots: List<com.inonvation.lightlife.data.ScheduleStore.TimeSlot> = emptyList(),
    val showScheduleSettings: Boolean = false,
    val showScheduleInfoDialog: Boolean = false,
    val showTaskSettings: Boolean = false,

    // ── 弹窗/对话框 ──
    val showSettings: Boolean = false,
    val showLogCenter: Boolean = false,
    val showDataScreen: Boolean = false,
    val showOrderHistory: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val showBackupTokenExpiredDialog: Boolean = false,
    val showPointsTaskWarning: Boolean = false,
    val showClearAllLogsConfirm: Boolean = false,
    val showArchivedLogs: Boolean = false,
    val showDebugLogs: Boolean = false,
    val tokenDialogText: String? = null,
    val deviceInfoDialogText: String? = null,
    val archivedLogs: List<Pair<String, String>> = emptyList(),
    val debugLogs: List<Pair<String, String>> = emptyList(),

    // ── 全局 ──
    val toastMessage: String? = null,
    val errorMessage: String? = null,
    val appVersion: String = "",

    // ── 快捷链接 ──
    val quickLinks: List<QuickLink> = emptyList(),
    val quickLinksEnabled: Boolean = true,
    val showQuickLinksSettings: Boolean = false,
)
