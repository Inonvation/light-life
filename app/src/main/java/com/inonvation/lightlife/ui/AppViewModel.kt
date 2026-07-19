package com.inonvation.lightlife.ui

import android.content.Context
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inonvation.lightlife.data.AppRepository
import com.inonvation.lightlife.data.BackupManager
import com.inonvation.lightlife.data.DebugLogStore
import com.inonvation.lightlife.data.DeviceItem
import com.inonvation.lightlife.data.OrderHistoryItem
import com.inonvation.lightlife.data.PointsStatsStore
import com.inonvation.lightlife.data.PointsTaskRunner
import com.inonvation.lightlife.data.PointsTaskStateStore
import com.inonvation.lightlife.data.QuickLink
import com.inonvation.lightlife.data.QuickLinkStore
import com.inonvation.lightlife.data.DEFAULT_QUICK_LINKS
import com.inonvation.lightlife.data.TaskLogStore
import com.inonvation.lightlife.data.TokenExpiredException
import com.inonvation.lightlife.data.UnlockException
import com.inonvation.lightlife.ui.auth.AuthController
import com.inonvation.lightlife.ui.backup.BackupController
import com.inonvation.lightlife.ui.points.PointsTaskController
import com.inonvation.lightlife.ui.theme.ColorTheme
import com.inonvation.lightlife.ui.theme.ThemeMode
import com.inonvation.lightlife.ui.theme.ThemePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val context: Context,
    private val repository: AppRepository,
    private val appVersion: String = "",
    private val pointsStatsStore: PointsStatsStore? = null,
    private val taskStateStore: PointsTaskStateStore? = null,
    private val logStore: TaskLogStore? = null,
    private val themePreferences: ThemePreferences? = null,
    private val backupManager: BackupManager? = null,
    private val debugLogStore: DebugLogStore? = null,
    private val quickLinkStore: QuickLinkStore? = null,
) : ViewModel() {

    // ── State ──
    private val _state = MutableStateFlow(
        AppUiState(
            hasToken = repository.localToken() != null,
            phone = repository.readPhone() ?: "",
            orderHistory = repository.orderHistory(),
            appVersion = appVersion,
        ),
    )
    val state: StateFlow<AppUiState> = _state

    // ── 内部工具 ──
    private fun showToast(message: String) { _state.update { it.copy(toastMessage = message) } }
    private fun showError(message: String) { _state.update { it.copy(errorMessage = friendlyErrorMessage(message)) } }

    private fun friendlyErrorMessage(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "unknownhostexception" in lower || "unable to resolve host" in lower -> "网络连接失败，请检查网络设置"
            "connectexception" in lower || "failed to connect" in lower -> "无法连接服务器，请稍后再试"
            "sockettimeoutexception" in lower || "timeout" in lower -> "请求超时，请检查网络后重试"
            "sslhandshakeexception" in lower -> "网络安全验证失败"
            "eofexception" in lower -> "数据传输中断，请重试"
            "请先登录" in raw -> "请先登录"
            else -> raw.ifBlank { "操作失败，请重试" }
        }
    }

    private fun clearAdVideoState() {
        context.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ── Controllers ──
    private val authController: AuthController by lazy {
        AuthController(
            state = _state,
            scope = viewModelScope,
            repository = repository,
            taskStateStore = taskStateStore,
            logStore = logStore,
            debugLogStore = debugLogStore,
            pointsStatsStore = pointsStatsStore,
            clearAdVideoState = ::clearAdVideoState,
            onAuthSuccess = {
                refreshBalance()
                refreshDevices()
                refreshTodayWater()
            },
            showToast = ::showToast,
            showError = ::showError,
        )
    }

    private val pointsTaskRunner = PointsTaskRunner({ repository.localToken() }, context).also { it.setDebugLog(debugLogStore) }

    private val pointsController: PointsTaskController by lazy {
        PointsTaskController(
            state = _state,
            scope = viewModelScope,
            context = context,
            pointsTaskRunner = pointsTaskRunner,
            taskStateStore = taskStateStore,
            logStore = logStore,
            pointsStatsStore = pointsStatsStore,
            refreshBalance = { refreshBalance() },
            showToast = ::showToast,
        )
    }

    private val backupController: BackupController by lazy {
        BackupController(
            state = _state,
            scope = viewModelScope,
            repository = repository,
            backupManager = backupManager,
            pointsStatsStore = pointsStatsStore,
            taskStateStore = taskStateStore,
            logStore = logStore,
            themePreferences = themePreferences,
            debugLogStore = debugLogStore,
            onRestoreFinished = { refreshTodayWater() },
            showToast = ::showToast,
        )
    }

    // ── 快捷方式 ──
    private var pendingShortcutRequest: DeviceShortcutRequest? = null
    private var unlockTimerJob: Job? = null

    // ── Init ──
    init {
        taskStateStore?.let {
            _state.update { s -> s.copy(
                hapticEnabled = it.isHapticEnabled(),
                autoStartTaskEnabled = it.isAutoStartTaskEnabled(),
                simpleModeEnabled = it.isSimpleModeEnabled(),
                safeModeEnabled = it.isSafeModeEnabled(),
                backgroundTaskEnabled = it.isBackgroundTaskEnabled(),
                backupPrivacySafe = it.isBackupPrivacySafe(),
                debugLogEnabled = debugLogStore?.isEnabled() ?: false,
                userAgent = it.getUserAgent(),
            ) }
        }
        themePreferences?.let {
            _state.update { s -> s.copy(
                themeMode = it.getThemeMode(),
                colorTheme = it.getColorTheme(),
            ) }
        }
        pointsStatsStore?.let {
            _state.update { s -> s.copy(
                totalPointsDeducted = it.getTotalDeductedAmount(),
            )}
        }
        // 加载快捷链接
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks(), quickLinksEnabled = it.isEnabled()) }
        }

        if (repository.localToken() != null) {
            refreshDevices()
            refreshBalance()
            refreshTodayWater()

            // 如果后台 Service 已在运行，直接连接
            connectToRunningService()

            // 自动检测：已登录且开启了自动启动时，检查今日任务是否已完成
            if (!state.value.autoStartTaskEnabled || state.value.safeModeEnabled) {
                // 自动检测已关闭，不做任何事
            } else if (state.value.userAgent.isBlank()) {
                android.os.Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "未设置 User-Agent，请在设置中先执行一次任务", Toast.LENGTH_LONG).show()
                }
            } else {
                pointsController.syncTodayTaskStateFromPrefs()
                if (!state.value.todayAllDone) {
                    android.os.Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "已自动开机刷积分任务~", Toast.LENGTH_SHORT).show()
                    }
                    pointsController.startPointsTask(state.value.userAgent)
                } else {
                    android.os.Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "今日积分都刷完了喔，喝杯热水吧~", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun selectTab(tab: DeviceTab) {
        _state.update { it.copy(currentTab = tab) }
        if (tab == DeviceTab.Control && state.value.hasToken && state.value.devices.isEmpty()) {
            refreshDevices()
        }
    }

    fun showSettings() { _state.update { it.copy(showSettings = true) } }
    fun dismissSettings() { _state.update { it.copy(showSettings = false) } }
    fun showLogCenter() { _state.update { it.copy(showLogCenter = true) } }
    fun dismissLogCenter() { _state.update { it.copy(showLogCenter = false) } }

    fun updatePhone(value: String) = authController.updatePhone(value)
    fun updateCode(value: String) = authController.updateCode(value)
    fun toggleTokenLogin() = authController.toggleTokenLogin()
    fun updateTokenLoginInput(value: String) = authController.updateTokenLoginInput(value)
    fun toggleTokenLoginVisibility() = authController.toggleTokenLoginVisibility()
    fun loginWithToken() = authController.loginWithToken()
    fun sendCode() = authController.sendCode()
    fun login() = authController.login()
    fun logout() = authController.logout()

    fun refreshDevices() = viewModelScope.launch {
        if (!state.value.hasToken) return@launch
        runCatching {
            _state.update { it.copy(loadingDevices = true) }
            repository.latestDevices()
        }.onSuccess { devices ->
            _state.update { it.copy(devices = devices, loadingDevices = false) }
            consumePendingShortcut(devices)
        }.onFailure {
            _state.update { it.copy(loadingDevices = false) }
            if (it is TokenExpiredException) { authController.handleTokenExpired(); return@launch }
            showError(it.message ?: "查询历史设备失败")
        }
    }

    fun refreshBalance() = viewModelScope.launch {
        if (!state.value.hasToken) return@launch
        runCatching {
            _state.update { it.copy(loadingBalance = true) }
            repository.queryBalance()
        }.onSuccess { balance ->
            _state.update { it.copy(balance = balance, loadingBalance = false) }
        }.onFailure {
            _state.update { it.copy(loadingBalance = false) }
            if (it is TokenExpiredException) { authController.handleTokenExpired(); return@launch }
            showError(it.message ?: "查询资产失败")
        }
    }

    fun openDeviceShortcut(request: DeviceShortcutRequest) {
        pendingShortcutRequest = request
        _state.update { it.copy(currentTab = DeviceTab.Control) }
        if (!state.value.hasToken) {
            showError("请先登录后再使用桌面设备快捷方式")
            return
        }
        val devices = state.value.devices
        if (devices.isEmpty()) {
            refreshDevices()
        } else {
            consumePendingShortcut(devices)
        }
    }

    private fun consumePendingShortcut(devices: List<DeviceItem>) {
        val request = pendingShortcutRequest ?: return
        val target = devices.firstOrNull { device ->
            (!request.goodsId.isNullOrBlank() && device.goodsId == request.goodsId) ||
                (!request.id.isNullOrBlank() && device.id == request.id) ||
                (!request.goodsName.isNullOrBlank() && device.goodsName == request.goodsName)
        }
        pendingShortcutRequest = null
        if (target == null) {
            showError("未找到对应的历史设备，请刷新设备列表后重试")
            return
        }
        unlock(target)
    }

    fun unlock(device: DeviceItem) = viewModelScope.launch {
        if (state.value.unlocking) return@launch
        _state.update {
            it.copy(unlocking = true, unlockStatus = "准备解锁", unlockFlowState = UnlockFlowState.PreChecking, unlockElapsedSeconds = 0, unlockFlowHidden = false)
        }
        unlockTimerJob?.cancel()
        unlockTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val cur = state.value.unlockFlowState
                if (cur is UnlockFlowState.Working) {
                    _state.update { it.copy(unlockElapsedSeconds = cur.elapsedSeconds + 1) }
                }
            }
        }
        runCatching {
            repository.unlockDevice(device) { step ->
                val isWorking = step.contains("等待完成") || step.contains("设备工作")
                _state.update {
                    it.copy(unlockStatus = step, unlockFlowState = if (isWorking) UnlockFlowState.Working(step, state.value.unlockElapsedSeconds) else UnlockFlowState.Working(step, 0))
                }
            }
        }.onSuccess { result ->
            unlockTimerJob?.cancel()
            _state.update { it.copy(unlocking = false, unlockStatus = null, unlockFlowState = UnlockFlowState.Success(result), unlockElapsedSeconds = 0, unlockFlowHidden = false, orderHistory = repository.orderHistory()) }
            if (result.integralCost != "-") { pointsStatsStore?.addDeducted(result.integralCost); refreshPointsStats() }
            refreshBalance()
        }.onFailure { e ->
            unlockTimerJob?.cancel()
            if (e is TokenExpiredException) {
                _state.update { it.copy(unlocking = false, unlockStatus = null, unlockFlowState = UnlockFlowState.Idle, unlockElapsedSeconds = 0, unlockFlowHidden = false) }
                authController.handleTokenExpired()
                return@launch
            }
            val diag = if (e is UnlockException) e.diagnosis else null
            val failState = if (diag != null) UnlockFlowState.Failed(diag.primaryReason, diag.step, diag.rawError, diag.suggestions)
                else UnlockFlowState.Failed(e.message ?: "未知错误", "未知", e.message ?: "")
            _state.update { it.copy(unlocking = false, unlockStatus = null, unlockFlowState = failState, unlockElapsedSeconds = 0, unlockFlowHidden = false) }
        }
    }

    fun dismissUnlockFlow() {
        unlockTimerJob?.cancel()
        _state.update { it.copy(unlockFlowState = UnlockFlowState.Idle, unlockElapsedSeconds = 0) }
    }

    fun updateQuickLink(index: Int, name: String, url: String, packageName: String = "") {
        quickLinkStore?.updateLink(index, name, url, packageName)
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks()) }
        }
    }

    fun toggleQuickLinksEnabled() {
        val v = !_state.value.quickLinksEnabled
        quickLinkStore?.setEnabled(v)
        _state.update { it.copy(quickLinksEnabled = v) }
    }

    fun showQuickLinksSettings() {
        _state.update { it.copy(showQuickLinksSettings = true) }
    }

    fun dismissQuickLinksSettings() {
        _state.update { it.copy(showQuickLinksSettings = false) }
    }

    fun resetQuickLinksToDefault() {
        DEFAULT_QUICK_LINKS.forEachIndexed { index, link ->
            quickLinkStore?.updateLink(index, link.name, link.url, link.packageName)
        }
        for (i in 3 until 9) {
            quickLinkStore?.updateLink(i, "", "", "")
        }
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks()) }
        }
    }

    fun dismissUnlockAnimation() {
        unlockTimerJob?.cancel()
        _state.update { it.copy(unlockFlowHidden = true, unlockElapsedSeconds = 0) }
    }

    fun startPointsTask(userAgent: String) = pointsController.startPointsTask(userAgent)
    fun pausePointsTask() = pointsController.pausePointsTask()
    fun resumePointsTask() = pointsController.resumePointsTask()
    fun stopPointsTask() = pointsController.stopPointsTask()
    fun clearPointsLogs() = pointsController.clearPointsLogs()
    fun syncTodayTaskStateFromPrefs() = pointsController.syncTodayTaskStateFromPrefs()

    /** 连接到已在运行的后台服务（应用重启后恢复状态） */
    private fun connectToRunningService() {
        pointsController.connectToRunningService()
    }

    fun prepareBackupJson(): String = backupController.prepareBackupJson()
    fun restoreFromBackupJson(json: String) = backupController.restoreFromBackupJson(json)
    fun confirmBackupImportOrdersOnly() = backupController.confirmBackupImportOrdersOnly()
    fun dismissBackupTokenExpiredDialog() = backupController.dismissBackupTokenExpiredDialog()

    fun toggleHaptic() {
        val v = !state.value.hapticEnabled
        taskStateStore?.setHapticEnabled(v)
        _state.update { it.copy(hapticEnabled = v) }
    }

    fun toggleDebugLog() {
        val v = !state.value.debugLogEnabled
        debugLogStore?.setEnabled(v)
        _state.update { it.copy(debugLogEnabled = v) }
        if (v) debugLogStore?.d("VM", "Debug logging enabled")
    }

    fun toggleBackupPrivacySafe() {
        debugLogStore?.d("VM", "toggleBackupPrivacySafe")
        val v = !state.value.backupPrivacySafe
        taskStateStore?.setBackupPrivacySafe(v)
        _state.update { it.copy(backupPrivacySafe = v) }
    }

    fun toggleAutoStartTask() {
        val v = !state.value.autoStartTaskEnabled
        taskStateStore?.setAutoStartTaskEnabled(v)
        _state.update { it.copy(autoStartTaskEnabled = v) }
        showToast(if (v) "已开启启动自动执行" else "已关闭启动自动执行")
    }

    fun toggleBackgroundTask() {
        val v = !state.value.backgroundTaskEnabled
        taskStateStore?.setBackgroundTaskEnabled(v)
        _state.update { it.copy(backgroundTaskEnabled = v) }
        showToast(if (v) "已开启后台刷积分" else "已关闭后台刷积分")
    }

    fun openBatteryOptimizationSettings() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        ).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = android.content.Intent(
                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun toggleSimpleMode() {
        val v = !state.value.simpleModeEnabled
        taskStateStore?.setSimpleModeEnabled(v)
        _state.update { it.copy(simpleModeEnabled = v) }
        if (v) showToast("已切换为简洁模式")
        else showToast("已切换为完整模式")
    }

    fun toggleSafeMode() {
        val v = !state.value.safeModeEnabled
        taskStateStore?.setSafeModeEnabled(v)
        _state.update { it.copy(safeModeEnabled = v) }
        if (v) {
            // 开启保险模式时停止正在运行的积分任务
            if (state.value.runningPointsTask) stopPointsTask()
            showToast("保险模式已开启，积分任务已禁用")
        } else {
            showToast("保险模式已关闭")
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        themePreferences?.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    fun updateColorTheme(theme: ColorTheme) {
        themePreferences?.setColorTheme(theme)
        _state.update { it.copy(colorTheme = theme) }
    }

    fun showDebugLogs() {
        _state.update { it.copy(debugLogs = debugLogStore?.listFiles() ?: emptyList(), showDebugLogs = true) }
    }
    fun dismissDebugLogs() { _state.update { it.copy(showDebugLogs = false) } }
    fun clearDebugLogs() {
        debugLogStore?.clearAll()
        _state.update { it.copy(debugLogs = emptyList()) }
    }
    fun deleteDebugLog(name: String) {
        debugLogStore?.deleteFile(name)
        _state.update { it.copy(debugLogs = it.debugLogs.filter { it.first != name }) }
    }
    fun getDebugLogContent(): String = debugLogStore?.getLatestContent() ?: ""

    fun showArchivedLogs() {
        _state.update { it.copy(archivedLogs = logStore?.listFiles() ?: emptyList(), showArchivedLogs = true) }
    }
    fun dismissArchivedLogs() { _state.update { it.copy(showArchivedLogs = false) } }
    fun clearArchivedLogs() {
        logStore?.clearAll()
        taskStateStore?.reset()
        clearAdVideoState()
        syncTodayTaskStateFromPrefs()
        _state.update { it.copy(archivedLogs = emptyList()) }
    }
    fun deleteArchivedLog(name: String) {
        logStore?.deleteFile(name)
        _state.update { it.copy(archivedLogs = it.archivedLogs.filter { it.first != name }) }
    }

    fun showCurrentToken() {
        val token = repository.localToken()?.takeIf { it.isNotBlank() }
        _state.update { it.copy(tokenDialogText = token ?: "当前未登录，暂无 Token") }
    }
    fun dismissCurrentToken() { _state.update { it.copy(tokenDialogText = null) } }

    fun showCurrentDeviceInfo() {
        val ua = state.value.userAgent
        _state.update { it.copy(deviceInfoDialogText = ua.ifBlank { "暂无设备信息，请先执行一次任务" }) }
    }
    fun dismissCurrentDeviceInfo() { _state.update { it.copy(deviceInfoDialogText = null) } }

    fun showLogoutConfirm() { _state.update { it.copy(showLogoutConfirm = true) } }
    fun dismissLogoutConfirm() { _state.update { it.copy(showLogoutConfirm = false) } }

    fun showPointsTaskWarning() { _state.update { it.copy(showPointsTaskWarning = true) } }
    fun dismissPointsTaskWarning() { _state.update { it.copy(showPointsTaskWarning = false) } }

    fun showOrderHistory() {
        _state.update { it.copy(showOrderHistory = true, orderHistory = repository.orderHistory()) }
    }
    fun dismissOrderHistory() { _state.update { it.copy(showOrderHistory = false) } }
    fun showHistoricalOrder(item: OrderHistoryItem) {
        _state.update { it.copy(orderDetail = item.toUnlockResult(), showOrderHistory = false) }
    }
    fun dismissOrderDetail() { _state.update { it.copy(orderDetail = null) } }

    fun consumeToast() { _state.update { it.copy(toastMessage = null) } }
    fun consumeError() { _state.update { it.copy(errorMessage = null) } }

    fun refreshTodayWater() {
        val todayStart = with(java.util.Calendar.getInstance()) {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            timeInMillis
        }
        val allOrders = repository.orderHistory()
        val todayOrders = allOrders.filter { it.completedAt >= todayStart }
        val count = todayOrders.size
        val amount = todayOrders.mapNotNull { item ->
            val raw = item.integralCost.filter { it.isDigit() || it == '.' || it == '-' }
            raw.toDoubleOrNull()
        }.sum()
        _state.update { it.copy(
            todayWaterCount = count,
            todayWaterAmount = String.format("%.2f", amount),
            totalWaterCount = allOrders.size,
        )}
    }

    fun refreshPointsStats() {
        pointsStatsStore?.let {
            _state.update { s -> s.copy(
                totalPointsDeducted = it.getTotalDeductedAmount(),
            )}
        }
        refreshTodayWater()
    }

    // ── Lifecycle ──
    override fun onCleared() {
        unlockTimerJob?.cancel()
        super.onCleared()
    }
}

class AppViewModelFactory(
    private val context: android.content.Context,
    private val repository: AppRepository,
    private val appVersion: String = "",
    private val pointsStatsStore: PointsStatsStore? = null,
    private val taskStateStore: PointsTaskStateStore? = null,
    private val logStore: TaskLogStore? = null,
    private val themePreferences: ThemePreferences? = null,
    private val backupManager: BackupManager? = null,
    private val debugLogStore: DebugLogStore? = null,
    private val quickLinkStore: QuickLinkStore? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(context, repository, appVersion, pointsStatsStore, taskStateStore, logStore, themePreferences, backupManager, debugLogStore, quickLinkStore) as T
    }
}
