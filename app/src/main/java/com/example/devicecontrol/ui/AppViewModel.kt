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
    val pointsLogs: List<String> = emptyList(),
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
    val suppressPointsTaskWarning: Boolean = false,
    val showSettings: Boolean = false,
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
    private val _state = MutableStateFlow(
        AppUiState(
            hasToken = repository.localToken() != null,
            orderHistory = repository.orderHistory(),
            appVersion = appVersion,
        ),
    )
    val state: StateFlow<AppUiState> = _state

    init {
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
        runCatching {
            _state.update { it.copy(unlocking = true, unlockStatus = "准备解锁") }
            repository.unlockDevice(device) { step ->
                _state.update { it.copy(unlockStatus = step) }
            }
        }.onSuccess { result ->
            _state.update {
                it.copy(
                    unlocking = false,
                    unlockStatus = null,
                    orderDetail = result,
                    orderHistory = repository.orderHistory(),
                )
            }
            showToast("解锁成功！订单原价：${result.originPrice}，花费小票：${result.ticketCost}")
            // 累计积分抵扣金额
            if (result.integralCost != "-") {
                pointsStatsStore?.addDeducted(result.integralCost)
                refreshPointsStats()
            }
            refreshBalance()
        }.onFailure {
            _state.update { it.copy(unlocking = false, unlockStatus = null) }
            showError(it.message ?: "解锁失败")
        }
    }


        fun startPointsTask(userAgent: String) = viewModelScope.launch {
        if (state.value.runningPointsTask) return@launch
        pointsTaskRunner.paused = false
        pointsTaskRunner.cancelled = false
        runCatching {
            _state.update {
                it.copy(
                    runningPointsTask = true,
                    pointsLogs = listOf("准备执行自动化任务"),
                    pointsProgress = null,
                )
            }
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
            val line = state.value.pointsLogs.findLast { it.contains("本次获得") && it.contains("积分") }
            if (line != null) {
                val regex = Regex("本次获得\\s*(\\d+)")
                regex.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
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
        logStore?.save(state.value.pointsLogs.joinToString("\n"))
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
            totalPointsEarned = 0,
            totalPointsDeducted = "0.00",
            logCompactEnabled = true,
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

    fun toggleHaptic() { _state.update { it.copy(hapticEnabled = !it.hapticEnabled) } }

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
            suppressPointsTaskWarning = s.suppressPointsTaskWarning,
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
        var restoredOrderCount = 0
        var restoredLogCount = 0
        // 恢复 Token（直接写入 TokenStore）
        backup.data.token?.takeIf { it.isNotBlank() }?.let { repository.saveToken(it) }
        // 恢复订单历史
        backup.data.orderHistory?.let { orders ->
            val ctx = backupManager!!.getContext()
            val prefs = ctx.getSharedPreferences("order_history", android.content.Context.MODE_PRIVATE)
            val moshi = com.squareup.moshi.Moshi.Builder()
                .add(com.example.devicecontrol.data.LenientStringJsonAdapter())
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, OrderHistoryItem::class.java)
            val adapter = moshi.adapter<List<OrderHistoryItem>>(listType)
            prefs.edit().putString("orders", adapter.toJson(orders.take(50))).apply()
            restoredOrderCount = orders.take(50).size
        }
        // 恢复积分统计
        backup.data.pointsStats?.let { stats ->
            pointsStatsStore?.let { store ->
                val ctx = backupManager!!.getContext()
                val prefs = ctx.getSharedPreferences("points_stats", android.content.Context.MODE_PRIVATE)
                prefs.edit().putInt("total_earned", stats.totalEarned).putString("total_deducted", stats.totalDeducted).apply()
            }
        }
        // 恢复任务日志
        backup.data.taskLogs?.let { logs ->
            val ctx = backupManager!!.getContext()
            val logDir = java.io.File(ctx.filesDir, "task_logs").also { it.mkdirs() }
            logDir.listFiles()?.forEach { it.delete() }
            for (log in logs) {
                java.io.File(logDir, log.name).writeText(log.content, Charsets.UTF_8)
                restoredLogCount++
            }
        }
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
        backup.data.hapticEnabled?.let { enabled -> _state.update { s -> s.copy(hapticEnabled = enabled) } }
        backup.data.logCompactEnabled?.let { compact ->
            taskStateStore?.setLogCompactEnabled(compact)
            _state.update { s -> s.copy(logCompactEnabled = compact) }
        }
        backup.data.suppressPointsTaskWarning?.let { suppressed -> _state.update { s -> s.copy(suppressPointsTaskWarning = suppressed) } }
        refreshTodayWater()
        showToast("已恢复 " + restoredOrderCount + " 条订单、" + restoredLogCount + " 条执行日志")
    }

    companion object {
        val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
    }

    private fun appendPointLog(line: String) {
        _state.update { state ->
            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
            state.copy(pointsLogs = (state.pointsLogs + "[$now] $line").takeLast(500))
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

    fun toggleSuppressPointsTaskWarning() {
        _state.update { it.copy(suppressPointsTaskWarning = !it.suppressPointsTaskWarning) }
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
