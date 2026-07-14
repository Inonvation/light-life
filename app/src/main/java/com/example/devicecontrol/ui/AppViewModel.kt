package com.example.devicecontrol.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.devicecontrol.data.AppRepository
import com.example.devicecontrol.data.BalanceData
import com.example.devicecontrol.data.DeviceItem
import com.example.devicecontrol.data.OrderHistoryItem
import com.example.devicecontrol.data.PointsTaskRunner
import com.example.devicecontrol.data.PointsTaskStateStore
import com.example.devicecontrol.data.TaskLogStore
import com.example.devicecontrol.data.PointsStatsStore
import com.example.devicecontrol.data.UnlockResult
import com.example.devicecontrol.data.BackupManager
import com.example.devicecontrol.ui.theme.ThemeMode
import com.example.devicecontrol.ui.theme.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.devicecontrol.data.UnlockException
import com.example.devicecontrol.data.DiagnosisResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

enum class DeviceTab { Control, Points, Me }

data class DeviceShortcutRequest(
    val goodsId: String?,
    val id: String?,
    val goodsName: String?,
)

data class PointsProgress(
    val phase: String,
    val step: Int,
    val total: Int,
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
)

data class AppUiState(
    val currentTab: DeviceTab = DeviceTab.Control,
    val hasToken: Boolean = false,
    val phone: String = "",
    val code: String = "",
    val phoneError: String? = null,
    val sendingCode: Boolean = false,
    val loggingIn: Boolean = false,
    val loadingDevices: Boolean = false,
    val loadingBalance: Boolean = false,
    val unlocking: Boolean = false,
    val runningPointsTask: Boolean = false,
    val pointsTaskPaused: Boolean = false,
    val pointsLogs: List<LogEntry> = emptyList(),
    val pointsProgress: PointsProgress? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val todayWaterCount: Int = 0,
    val todayWaterAmount: String = "0.00",
    val totalWaterCount: Int = 0,
    val totalPointsEarned: Int = 0,
    val totalPointsDeducted: String = "0.00",
    val devices: List<DeviceItem> = emptyList(),
    val balance: BalanceData? = null,
    val orderHistory: List<OrderHistoryItem> = emptyList(),
    val unlockStatus: String? = null,
    val unlockFlowState: UnlockFlowState = UnlockFlowState.Idle,
    val unlockElapsedSeconds: Int = 0,
    val orderDetail: UnlockResult? = null,
    val showOrderHistory: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val tokenDialogText: String? = null,
    val archivedLogs: List<Pair<String, String>> = emptyList(),
    val showArchivedLogs: Boolean = false,
    val hapticEnabled: Boolean = true,
    val logCompactEnabled: Boolean = true,
    val toastMessage: String? = null,
    val errorMessage: String? = null,
    val appVersion: String = "",
    val showPointsTaskWarning: Boolean = false,
    val showSettings: Boolean = false,
    val userAgent: String = "",
    val deviceInfoDialogText: String? = null,
)

class AppViewModel(
    private val repository: AppRepository,
    private val appVersion: String = "",
    private val pointsStatsStore: PointsStatsStore? = null,
    private val taskStateStore: PointsTaskStateStore? = null,
    private val logStore: TaskLogStore? = null,
    private val themePreferences: ThemePreferences? = null,
    private val backupManager: BackupManager? = null,
) : ViewModel() {
    private val pointsTaskRunner = PointsTaskRunner({ repository.localToken() }, taskStateStore)
    private var pendingShortcutRequest: DeviceShortcutRequest? = null
    private var unlockTimerJob: Job? = null
    private val _state = MutableStateFlow(
        AppUiState(
            hasToken = repository.localToken() != null,
            orderHistory = repository.orderHistory(),
            appVersion = appVersion,
        ),
    )
    val state: StateFlow<AppUiState> = _state

    init {
        taskStateStore?.let {
            _state.update { s -> s.copy(
                hapticEnabled = it.isHapticEnabled(),
                logCompactEnabled = it.isLogCompactEnabled(),
                userAgent = it.getUserAgent(),
            ) }
        }
        themePreferences?.let {
            _state.update { s -> s.copy(themeMode = it.getThemeMode()) }
        }
        pointsStatsStore?.let {
            _state.update { s -> s.copy(
                totalPointsEarned = it.getTotalEarned(),
                totalPointsDeducted = it.getTotalDeductedAmount(),
            )}
        }
        if (repository.localToken() != null) {
            refreshDevices()
            refreshBalance()
            refreshTodayWater()
        }
    }

    fun selectTab(tab: DeviceTab) {
        _state.update { it.copy(currentTab = tab) }
        if (tab == DeviceTab.Control && state.value.hasToken && state.value.devices.isEmpty()) {
            refreshDevices()
        }
    }

    fun updatePhone(value: String) {
        _state.update { it.copy(phone = value, phoneError = null) }
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && !PHONE_REGEX.matches(trimmed)) {
            _state.update { it.copy(phoneError = "请输入正确格式的手机号") }
        }
    }

    fun updateCode(value: String) {
        val filtered = value.filter { it.isDigit() }
        _state.update { it.copy(code = filtered) }
    }

    fun sendCode() = viewModelScope.launch {
        val phone = state.value.phone.trim()
        if (phone.isBlank() || !PHONE_REGEX.matches(phone)) {
            _state.update { it.copy(phoneError = "请输入正确格式的手机号") }
            return@launch
        }
        runCatching {
            _state.update { it.copy(sendingCode = true) }
            repository.sendCode(phone)
        }.onSuccess {
            showToast("验证码已发送")
        }.onFailure {
            showError(it.message ?: "验证码发送失败")
        }
        _state.update { it.copy(sendingCode = false) }
    }

    fun login() = viewModelScope.launch {
        val phone = state.value.phone.trim()
        val code = state.value.code.trim()
        if (phone.isBlank() || !PHONE_REGEX.matches(phone)) {
            _state.update { it.copy(phoneError = "请输入正确格式的手机号") }
            return@launch
        }
        if (code.isBlank()) {
            showError("请输入验证码")
            return@launch
        }
        runCatching {
            _state.update { it.copy(loggingIn = true) }
            repository.login(phone, code)
        }.onSuccess {
            _state.update { it.copy(hasToken = true, loggingIn = false, phoneError = null) }
            showToast("登录成功")
            refreshBalance()
            refreshDevices()
            refreshTodayWater()
        }.onFailure {
            _state.update { it.copy(loggingIn = false) }
            showError(it.message ?: "登录失败")
        }
    }

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
            showError(it.message ?: "查询历史设备失败")
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

    fun refreshBalance() = viewModelScope.launch {
        if (!state.value.hasToken) return@launch
        runCatching {
            _state.update { it.copy(loadingBalance = true) }
            repository.queryBalance()
        }.onSuccess { balance ->
            _state.update { it.copy(balance = balance, loadingBalance = false) }
        }.onFailure {
            _state.update { it.copy(loadingBalance = false) }
            showError(it.message ?: "查询资产失败")
        }
    }

    fun unlock(device: DeviceItem) = viewModelScope.launch {
        if (state.value.unlocking) return@launch
        _state.update {
            it.copy(unlocking = true, unlockStatus = "准备解锁", unlockFlowState = UnlockFlowState.PreChecking, unlockElapsedSeconds = 0)
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
            _state.update { it.copy(unlocking = false, unlockStatus = null, unlockFlowState = UnlockFlowState.Success(result), unlockElapsedSeconds = 0, orderDetail = result, orderHistory = repository.orderHistory()) }
            if (result.integralCost != "-") { pointsStatsStore?.addDeducted(result.integralCost); refreshPointsStats() }
            refreshBalance()
        }.onFailure { e ->
            unlockTimerJob?.cancel()
            val diag = if (e is UnlockException) e.diagnosis else null
            val failState = if (diag != null) UnlockFlowState.Failed(diag.primaryReason, diag.step, diag.rawError, diag.suggestions)
                else UnlockFlowState.Failed(e.message ?: "未知错误", "未知", e.message ?: "")
            _state.update { it.copy(unlocking = false, unlockStatus = null, unlockFlowState = failState, unlockElapsedSeconds = 0) }
        }
    }

    fun dismissUnlockFlow() {
        unlockTimerJob?.cancel()
        _state.update { it.copy(unlockFlowState = UnlockFlowState.Idle, unlockElapsedSeconds = 0) }
    }
        fun startPointsTask(userAgent: String) = viewModelScope.launch {
        if (state.value.runningPointsTask) return@launch
        pointsTaskRunner.paused = false
        pointsTaskRunner.cancelled = false
        runCatching {
            _state.update {
                it.copy(
                    runningPointsTask = true,
                    pointsLogs = listOf(LogEntry("", "准备执行自动化任务", LogLevel.INFO)),
                    userAgent = userAgent,
                    pointsProgress = null,
                )
            }
            taskStateStore?.setUserAgent(userAgent)
            pointsTaskRunner.setOnProgress { phase, step, total ->
                _state.update { it.copy(pointsProgress = PointsProgress(phase, step, total)) }
            }
            pointsTaskRunner.run(userAgent) { line ->
                appendPointLog(line)
            }
        }.onSuccess {
            appendPointLog("任务流程结束")
            // 从日志中解析本次获得积分并保存到统计
            val gainedPoints = run {
                val entry = state.value.pointsLogs.findLast { it.message.contains("本次获得") && it.message.contains("积分") }
                if (entry != null) {
                    val regex = Regex("本次获得\\s*(\\d+)")
                    regex.find(entry.message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                } else 0
            }
            if (gainedPoints > 0) {
                pointsStatsStore?.addEarned(gainedPoints)
            }
            pointsStatsStore?.let {
                _state.update { s -> s.copy(
                    totalPointsEarned = it.getTotalEarned(),
                    totalPointsDeducted = it.getTotalDeductedAmount(),
                )}
            }
            refreshBalance()
        }.onFailure { e ->
            val errMsg = e.message ?: "未知错误"
            if (errMsg.contains("用户已取消任务")) {
                appendPointLog("任务已终止")
            } else {
                appendPointLog("任务失败：$errMsg")
            }
        }
        _state.update { it.copy(runningPointsTask = false, pointsProgress = null) }
    }

    fun pausePointsTask() {
        pointsTaskRunner.paused = true
        _state.update { it.copy(pointsTaskPaused = true) }
        appendPointLog("任务已暂停")
    }

    fun resumePointsTask() {
        pointsTaskRunner.paused = false
        _state.update { it.copy(pointsTaskPaused = false) }
        appendPointLog("任务已继续")
    }

    fun stopPointsTask() {
        pointsTaskRunner.cancelled = true
        pointsTaskRunner.paused = false
        _state.update { it.copy(pointsTaskPaused = false, runningPointsTask = false) }
        appendPointLog("用户已结束任务")
        logStore?.save(state.value.pointsLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" })
    }

    fun clearPointsLogs() {
        _state.update { it.copy(pointsLogs = emptyList()) }
    }

        fun showSettings() { _state.update { it.copy(showSettings = true) } }
    fun dismissSettings() { _state.update { it.copy(showSettings = false) } }

    fun logout() {
        repository.clearToken()
        _state.update { it.copy(
            hasToken = false,
            phone = "",
            code = "",
            phoneError = null,
            devices = emptyList(),
            balance = null,
            todayWaterCount = 0,
            todayWaterAmount = "0.00",
            totalWaterCount = 0,
        )}
    }

    fun refreshPointsStats() {
        pointsStatsStore?.let {
            _state.update { s -> s.copy(
                totalPointsEarned = it.getTotalEarned(),
                totalPointsDeducted = it.getTotalDeductedAmount(),
            )}
        }
        refreshTodayWater()
    }

    fun toggleHaptic() {
        val v = !state.value.hapticEnabled
        taskStateStore?.setHapticEnabled(v)
        _state.update { it.copy(hapticEnabled = v) }
    }

    fun toggleLogCompact() {
        val v = !state.value.logCompactEnabled
        taskStateStore?.setLogCompactEnabled(v)
        _state.update { it.copy(logCompactEnabled = v) }
    }

        fun updateThemeMode(mode: ThemeMode) {
        themePreferences?.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }



    fun prepareBackupJson(): String {
        val s = state.value
        val backup = backupManager?.collect(
            appVersion = s.appVersion,
            token = repository.localToken(),
            orderHistory = s.orderHistory,
            pointsStats = pointsStatsStore,
            taskLogStore = logStore,
            themeMode = s.themeMode.name,
            hapticEnabled = s.hapticEnabled,
            logCompactEnabled = s.logCompactEnabled,
            userAgent = s.userAgent,
            taskStateStore = taskStateStore,
        ) ?: return ""
        return backupManager.toJson(backup)
    }

    fun restoreFromBackupJson(json: String) {
        runCatching {
            _restoreFromBackupJson(json)
        }.onFailure { e ->
            showToast("导入失败：" + (e.message ?: "未知错误"))
        }
    }

    private fun _restoreFromBackupJson(json: String) {
        val backup = backupManager?.fromJson(json)
        if (backup == null) {
            showToast("备份文件格式不匹配，请选择有效的 .lif 备份文件")
            return
        }
        // 恢复 Token
        backup.data.token?.takeIf { it.isNotBlank() }?.let { repository.saveToken(it) }
        // 数据部分委托 BackupManager.restore()
        val counts = backupManager?.restore(backup) ?: return
        // 刷新 UI 状态
        _state.update { it.copy(
            hasToken = repository.localToken() != null,
            orderHistory = repository.orderHistory(),
            totalPointsEarned = pointsStatsStore?.getTotalEarned() ?: 0,
            totalPointsDeducted = pointsStatsStore?.getTotalDeductedAmount() ?: "0.00",
        )}
        // 恢复主题模式
        backup.data.themeMode?.let { modeName ->
            try {
                val mode = com.example.devicecontrol.ui.theme.ThemeMode.valueOf(modeName)
                themePreferences?.setThemeMode(mode)
                _state.update { it.copy(themeMode = mode) }
            } catch (_: IllegalArgumentException) {}
        }
        // 恢复设置项
        backup.data.hapticEnabled?.let { enabled ->
            taskStateStore?.setHapticEnabled(enabled)
            _state.update { s -> s.copy(hapticEnabled = enabled) }
        }
        backup.data.logCompactEnabled?.let { compact ->
            taskStateStore?.setLogCompactEnabled(compact)
            _state.update { s -> s.copy(logCompactEnabled = compact) }
        }
        backup.data.userAgent?.let { ua ->
            if (ua.isNotBlank()) {
                taskStateStore?.setUserAgent(ua)
                _state.update { s -> s.copy(userAgent = ua) }
            }
        }
        refreshTodayWater()
        showToast("已恢复 " + counts.orders + " 条订单、" + counts.logs + " 条执行日志")
    }

    override fun onCleared() {
        unlockTimerJob?.cancel()
        super.onCleared()
    }

    companion object {
        val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
    }

    private fun appendPointLog(line: String) {
        _state.update { state ->
            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
            val level = when {
                line.contains("成功") || line.contains("完成") || line.contains("获得") -> LogLevel.SUCCESS
                line.contains("失败") || line.contains("错误") || line.contains("异常") -> LogLevel.ERROR
                line.contains("暂停") || line.contains("终止") || line.contains("警告") -> LogLevel.WARN
                else -> LogLevel.INFO
            }
            state.copy(pointsLogs = (state.pointsLogs + LogEntry(now, line, level)).takeLast(500))
        }
    }

    fun showArchivedLogs() {
        _state.update { it.copy(archivedLogs = logStore?.listFiles() ?: emptyList(), showArchivedLogs = true) }
    }

    fun dismissArchivedLogs() {
        _state.update { it.copy(showArchivedLogs = false) }
    }

    fun clearArchivedLogs() {
        logStore?.clearAll()
        _state.update { it.copy(archivedLogs = emptyList()) }
    }

    fun showCurrentToken() {
        val token = repository.localToken()?.takeIf { it.isNotBlank() }
        _state.update { it.copy(tokenDialogText = token ?: "当前未登录，暂无 Token") }
    }

    fun showCurrentDeviceInfo() {
        val ua = state.value.userAgent
        _state.update { it.copy(deviceInfoDialogText = ua.ifBlank { "暂无设备信息，请先执行一次任务" }) }
    }

    fun dismissCurrentDeviceInfo() {
        _state.update { it.copy(deviceInfoDialogText = null) }
    }

    fun showLogoutConfirm() {
        _state.update { it.copy(showLogoutConfirm = true) }
    }

    fun dismissLogoutConfirm() {
        _state.update { it.copy(showLogoutConfirm = false) }
    }

    fun dismissCurrentToken() {
        _state.update { it.copy(tokenDialogText = null) }
    }
    fun consumeToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun dismissOrderDetail() {
        _state.update { it.copy(orderDetail = null) }
    }

    fun showPointsTaskWarning() {
        _state.update { it.copy(showPointsTaskWarning = true) }
    }

    fun dismissPointsTaskWarning() {
        _state.update { it.copy(showPointsTaskWarning = false) }
    }


    fun showOrderHistory() {
        _state.update { it.copy(showOrderHistory = true, orderHistory = repository.orderHistory()) }
    }

    fun dismissOrderHistory() {
        _state.update { it.copy(showOrderHistory = false) }
    }

    fun showHistoricalOrder(item: OrderHistoryItem) {
        _state.update { it.copy(orderDetail = item.toUnlockResult(), showOrderHistory = false) }
    }



    fun refreshTodayWater() {
        val todayStart = with(java.util.Calendar.getInstance()) {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            timeInMillis
        }
        val todayOrders = repository.orderHistory().filter { it.completedAt >= todayStart }
        val allOrders = repository.orderHistory()
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

    private fun showToast(message: String) {
        _state.update { it.copy(toastMessage = message) }
    }

    private fun showError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }
}

class AppViewModelFactory(
    private val repository: AppRepository,
    private val appVersion: String = "",
    private val pointsStatsStore: PointsStatsStore? = null,
    private val taskStateStore: PointsTaskStateStore? = null,
    private val logStore: TaskLogStore? = null,
    private val themePreferences: ThemePreferences? = null,
    private val backupManager: BackupManager? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(repository, appVersion, pointsStatsStore, taskStateStore, logStore, themePreferences, backupManager) as T
    }
}
