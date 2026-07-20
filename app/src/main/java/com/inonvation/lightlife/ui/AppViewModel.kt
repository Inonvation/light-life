package com.inonvation.lightlife.ui

import android.app.Application
import android.content.Context
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
    application: Application,
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
    private val context: Context = application.applicationContext
    private val unlockMutex = kotlinx.coroutines.sync.Mutex()

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

    // 公开 quickLinkStore 供快捷方式图标使用
    fun getQuickLinkStore(): QuickLinkStore? = quickLinkStore

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
            quickLinkStore = quickLinkStore,
            onRestoreFinished = { refreshTodayWater() },
            showToast = ::showToast,
        )
    }

    // ── 定时任务 ──
    private val scheduleStore = com.inonvation.lightlife.data.ScheduleStore(context)

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
                randomDelayEnabled = it.isRandomDelayEnabled(),
                usePointsForUnlock = it.isUsePointsForUnlockEnabled(),
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
        
        // 加载定时配置
        loadScheduleConfig()

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
                showToast("未设置 User-Agent，请在设置中先执行一次任务")
            } else {
                pointsController.syncTodayTaskStateFromPrefs()
                if (!state.value.todayAllDone) {
                    showToast("已自动开机刷积分任务~")
                    pointsController.startPointsTask(state.value.userAgent)
                } else {
                    showToast("今日积分都刷完了喔，喝杯热水吧~")
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
    fun showDataScreen() { _state.update { it.copy(showDataScreen = true) } }
    fun dismissDataScreen() { _state.update { it.copy(showDataScreen = false) } }
    fun showTaskSettings() { _state.update { it.copy(showTaskSettings = true) } }
    fun dismissTaskSettings() { _state.update { it.copy(showTaskSettings = false) } }

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
        if (!unlockMutex.tryLock()) return@launch
        try {
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
                repository.unlockDevice(device, usePoints = state.value.usePointsForUnlock) { step ->
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
        } finally {
            unlockMutex.unlock()
        }
    }

    fun dismissUnlockFlow() {
        unlockTimerJob?.cancel()
        _state.update { it.copy(unlockFlowState = UnlockFlowState.Idle, unlockElapsedSeconds = 0) }
    }

    fun updateQuickLink(index: Int, name: String, url: String, packageName: String, presetIndex: Int = -1) {
        quickLinkStore?.updateLink(index, name, url, packageName, presetIndex)
        // 选择预设时自动保存预设图标
        if (presetIndex >= 0 && name.isNotBlank()) {
            quickLinkStore?.savePresetIcon(index, presetIndex)
        }
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks()) }
        }
    }

    /** 删除快捷方式：清空该槽位，后续条目前移补位 */
    fun deleteQuickLink(index: Int) {
        val links = _state.value.quickLinks.toMutableList()
        if (index !in links.indices) return
        // 清空该槽位，预设槽保留 presetIndex 以便重置
        val pi = if (index < 3) index else -1
        quickLinkStore?.updateLink(index, "", "", "", pi)
        // 非预设槽位：后面的条目依次前移
        if (index >= 3) {
            for (i in index until links.size - 1) {
                val next = links[i + 1]
                quickLinkStore?.updateLink(i, next.name, next.url, next.packageName, next.presetIndex)
            }
            // 最后一个槽位清空
            quickLinkStore?.updateLink(links.size - 1, "", "", "", -1)
        }
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks()) }
        }
    }

    fun swapQuickLinks(index1: Int, index2: Int) {
        quickLinkStore?.swapLinks(index1, index2)
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
            quickLinkStore?.updateLink(index, link.name, link.url, link.packageName, link.presetIndex)
        }
        for (i in 3 until 9) {
            quickLinkStore?.updateLink(i, "", "", "", -1)
        }
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks()) }
        }
    }

    fun setQuickLinkIcon(index: Int, uri: android.net.Uri) {
        val success = quickLinkStore?.saveIcon(index, uri) ?: false
        if (success) {
            quickLinkStore?.let {
                _state.update { s -> s.copy(quickLinks = it.getLinks()) }
            }
            showToast("图标已设置")
        } else {
            showError("设置图标失败")
        }
    }

    fun removeQuickLinkIcon(index: Int) {
        quickLinkStore?.removeIcon(index)
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks()) }
        }
        showToast("图标已移除")
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

    fun toggleRandomDelay() {
        val v = !state.value.randomDelayEnabled
        taskStateStore?.setRandomDelayEnabled(v)
        _state.update { it.copy(randomDelayEnabled = v) }
        showToast(if (v) "已开启随机延迟" else "已关闭随机延迟")
    }

    fun toggleUsePointsForUnlock() {
        val v = !state.value.usePointsForUnlock
        taskStateStore?.setUsePointsForUnlockEnabled(v)
        _state.update { it.copy(usePointsForUnlock = v) }
        showToast(if (v) "开水将使用积分抵扣" else "开水不使用积分抵扣")
    }
    
    // ── 定时任务 ──
    
    fun toggleScheduleEnabled() {
        val v = !state.value.scheduleEnabled
        scheduleStore.setEnabled(v)
        _state.update { it.copy(scheduleEnabled = v) }
        if (v) {
            scheduleWorkManager()
            showToast("已开启定时任务")
        } else {
            cancelWorkManager()
            showToast("已关闭定时任务")
        }
    }
    
    fun addScheduleTimeSlot(slot: com.inonvation.lightlife.data.ScheduleStore.TimeSlot) {
        scheduleStore.addTimeSlot(slot)
        _state.update { it.copy(scheduleTimeSlots = scheduleStore.getTimeSlots()) }
    }
    
    fun removeScheduleTimeSlot(slot: com.inonvation.lightlife.data.ScheduleStore.TimeSlot) {
        scheduleStore.removeTimeSlot(slot)
        _state.update { it.copy(scheduleTimeSlots = scheduleStore.getTimeSlots()) }
    }
    
    fun showScheduleSettings() {
        _state.update { it.copy(showScheduleSettings = true) }
    }
    
    fun dismissScheduleSettings() {
        _state.update { it.copy(showScheduleSettings = false) }
    }

    fun showScheduleInfoDialog() {
        _state.update { it.copy(showScheduleInfoDialog = true) }
    }

    fun dismissScheduleInfoDialog() {
        _state.update { it.copy(showScheduleInfoDialog = false) }
    }
    
    private fun scheduleWorkManager() {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.inonvation.lightlife.data.ScheduledTaskWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        ).setConstraints(constraints)
            .setInitialDelay(calculateInitialDelay(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            com.inonvation.lightlife.data.ScheduledTaskWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    
    private fun cancelWorkManager() {
        val workManager = androidx.work.WorkManager.getInstance(context)
        workManager.cancelUniqueWork(com.inonvation.lightlife.data.ScheduledTaskWorker.WORK_NAME)
    }
    
    private fun calculateInitialDelay(): Long {
        val timeSlots = scheduleStore.getTimeSlots()
        if (timeSlots.isEmpty()) return 60 * 60 * 1000L // 默认1小时
        
        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        
        // 找到下一个可用的时间段
        val nextSlot = timeSlots.firstOrNull { it.toStartMinutes() > currentMinutes }
            ?: timeSlots.first() // 如果没有，使用第一个时间段（明天）
        
        val targetMinutes = nextSlot.toStartMinutes()
        var delayMinutes = targetMinutes - currentMinutes
        if (delayMinutes <= 0) delayMinutes += 24 * 60 // 跨天
        
        return delayMinutes * 60 * 1000L
    }
    
    fun loadScheduleConfig() {
        _state.update { it.copy(
            scheduleEnabled = scheduleStore.isEnabled(),
            scheduleTimeSlots = scheduleStore.getTimeSlots()
        ) }
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
        _state.update { state -> state.copy(debugLogs = state.debugLogs.filter { log -> log.first != name }) }
    }
    fun getDebugLogContent(): String = debugLogStore?.getLatestContent() ?: ""

    fun showClearAllLogsConfirm() { _state.update { it.copy(showClearAllLogsConfirm = true) } }
    fun dismissClearAllLogsConfirm() { _state.update { it.copy(showClearAllLogsConfirm = false) } }
    fun clearAllLogs() {
        logStore?.clearAll()
        taskStateStore?.reset()
        clearAdVideoState()
        syncTodayTaskStateFromPrefs()
        debugLogStore?.clearAll()
        _state.update { it.copy(archivedLogs = emptyList(), debugLogs = emptyList(), showClearAllLogsConfirm = false) }
        showToast("所有记录和日志已清除")
    }

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
    private val application: Application,
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
        return AppViewModel(application, repository, appVersion, pointsStatsStore, taskStateStore, logStore, themePreferences, backupManager, debugLogStore, quickLinkStore) as T
    }
}
