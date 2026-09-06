package com.inonvation.lightlife.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inonvation.lightlife.data.ApiConfig
import com.inonvation.lightlife.data.AppRepository
import com.inonvation.lightlife.data.DEFAULT_QUICK_LINKS
import com.inonvation.lightlife.data.DeviceItem
import com.inonvation.lightlife.data.PointsTaskStateStore
import com.inonvation.lightlife.data.QuickLinkStore
import com.inonvation.lightlife.data.SignInRunner
import com.inonvation.lightlife.data.TokenExpiredException
import com.inonvation.lightlife.data.UnlockException
import com.inonvation.lightlife.ui.auth.AuthController
import com.inonvation.lightlife.ui.qzxy.QzxyController
import com.inonvation.lightlife.ui.qzxy.QzxyUiState
import com.inonvation.lightlife.data.qzxy.QzxyBluetoothScanner
import com.inonvation.lightlife.data.qzxy.QzxyNearbyDevice
import com.inonvation.lightlife.data.qzxy.QzxyRepository
import com.inonvation.lightlife.ui.theme.ColorTheme
import com.inonvation.lightlife.ui.theme.ThemeMode
import com.inonvation.lightlife.ui.theme.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class UiEvent {
    data class Toast(val message: String) : UiEvent()
    data class Error(val message: String) : UiEvent()
}

class AppViewModel(
    application: Application,
    private val repository: AppRepository,
    private val appVersion: String = "",
    private val taskStateStore: PointsTaskStateStore? = null,
    private val themePreferences: ThemePreferences? = null,
    private val quickLinkStore: QuickLinkStore? = null,
    private val qzxyRepository: QzxyRepository,
) : ViewModel() {
    private val context: Context = application.applicationContext
    private val unlockMutex = kotlinx.coroutines.sync.Mutex()
    private var devicesLoadAttempted = false

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

    // ── 一次性事件通道（Toast / Error）──
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun showToast(message: String) { _events.trySend(UiEvent.Toast(message)) }
    private fun showError(message: String) { _events.trySend(UiEvent.Error(friendlyErrorMessage(message))) }

    private fun friendlyErrorMessage(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "unknownhostexception" in lower || "unable to resolve host" in lower -> "网络连接失败，请检查网络设置"
            "connectexception" in lower || "failed to connect" in lower -> "无法连接服务器，请稍后再试"
            "sockettimeoutexception" in lower || "timeout" in lower -> "请求超时，请检查网络后重试"
            "sslhandshakeexception" in lower -> "网络安全验证失败"
            "eofexception" in lower -> "数据传输中断，请重试"
            "请先登录" in raw -> "请先登录"
            "500" in raw || "502" in raw || "503" in raw || "504" in raw -> "服务器繁忙，请稍后再试"
            " 401 " in raw || " 403 " in raw || "http 401" in lower || "http 403" in lower -> "请求被拒绝，请检查权限"
            else -> raw.ifBlank { "操作失败，请重试" }
        }
    }

    // ── 控制器 ──
    private val authController: AuthController by lazy {
        AuthController(
            state = state,
            updateState = { _state.update(it) },
            scope = viewModelScope,
            repository = repository,
            taskStateStore = taskStateStore,
            onAuthSuccess = {
                refreshBalance()
                refreshDevices()
                refreshTodayWater()
            },
            showToast = ::showToast,
            showError = ::showError,
        )
    }

    private val signInRunner = SignInRunner({ repository.localToken() }, context)

    // ── 淋浴（趣智校园）控制器 ──
    private fun updateQzxy(reduce: (QzxyUiState) -> QzxyUiState) {
        _state.update { it.copy(qzxy = reduce(it.qzxy)) }
    }

    private val qzxyController: QzxyController by lazy {
        QzxyController(
            state = state,
            updateQzxy = ::updateQzxy,
            scope = viewModelScope,
            repository = qzxyRepository,
            scanner = QzxyBluetoothScanner(context),
            showToast = ::showToast,
            showError = ::showError,
        )
    }

    private var pendingShortcutRequest: DeviceShortcutRequest? = null
    private var unlockTimerJob: Job? = null
    private var unlockTimeoutJob: Job? = null

    // ── Init ──
    init {
        taskStateStore?.let {
            _state.update { s -> s.copy(
                hapticEnabled = it.isHapticEnabled(),
                autoSignInEnabled = it.isAutoSignInEnabled(),
                usePointsForUnlock = it.isUsePointsForUnlockEnabled(),
            ) }
        }
        themePreferences?.let {
            _state.update { s -> s.copy(
                themeMode = it.getThemeMode(),
                colorTheme = it.getColorTheme(),
            ) }
        }
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks(), quickLinksEnabled = it.isEnabled()) }
        }
        _state.update { s -> s.copy(signInDoneToday = signInRunner.isSignedInToday()) }
        if (repository.localToken() != null) {
            refreshDevices()
            refreshBalance()
            refreshTodayWater()
        }
        // 打开 App 时自动签到
        autoSignInOnLaunch()
        // 恢复趣智校园登录态与进行中的洗澡订单
        qzxyController.restoreSession()
    }

    // ── 登录 ──
    fun updatePhone(value: String) = authController.updatePhone(value)
    fun updateCode(value: String) = authController.updateCode(value)
    fun toggleTokenLogin() = authController.toggleTokenLogin()
    fun updateTokenLoginInput(value: String) = authController.updateTokenLoginInput(value)
    fun toggleTokenLoginVisibility() = authController.toggleTokenLoginVisibility()
    fun loginWithToken() = authController.loginWithToken()
    fun sendCode() = authController.sendCode()
    fun login() = authController.login()
    fun logout() = authController.logout()

    // ── 签到 ──
    fun autoSignInOnLaunch() {
        if (!state.value.autoSignInEnabled) return
        if (!state.value.hasToken) return
        if (signInRunner.isSignedInToday()) return
        signInNow()
    }

    fun signInNow() = viewModelScope.launch {
        if (!state.value.hasToken) {
            showError("请先登录")
            return@launch
        }
        if (state.value.signingIn) return@launch
        _state.update { it.copy(signingIn = true) }
        runCatching {
            signInRunner.signIn(ApiConfig.USER_AGENT)
        }.onSuccess { result ->
            _state.update { it.copy(signInDoneToday = true, signingIn = false) }
            showToast(result.message)
            refreshBalance()
        }.onFailure { e ->
            _state.update { it.copy(signingIn = false) }
            if (e is TokenExpiredException) {
                authController.handleTokenExpired()
            } else {
                showError(e.message ?: "签到失败")
            }
        }
    }

    // ── 设备 / 余额 ──
    private fun handleApiError(error: Throwable, fallbackMessage: String = "操作失败"): Boolean {
        return if (error is TokenExpiredException) {
            authController.handleTokenExpired()
            true
        } else {
            showError(error.message ?: fallbackMessage)
            false
        }
    }

    fun refreshDevices() = viewModelScope.launch {
        if (!state.value.hasToken) return@launch
        if (state.value.loadingDevices) return@launch
        runCatching {
            _state.update { it.copy(loadingDevices = true) }
            repository.latestDevices()
        }.onSuccess { devices ->
            _state.update { it.copy(devices = devices, loadingDevices = false) }
            devicesLoadAttempted = false
            consumePendingShortcut(devices)
        }.onFailure {
            _state.update { it.copy(loadingDevices = false) }
            if (!handleApiError(it, "查询历史设备失败")) {
                devicesLoadAttempted = true
            }
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
            handleApiError(it, "查询资产失败")
        }
    }

    // ── 解锁 ──
    fun unlock(device: DeviceItem) = viewModelScope.launch {
        if (!unlockMutex.tryLock()) return@launch
        try {
            _state.update {
                it.copy(unlocking = true, unlockingDeviceId = device.goodsName.ifBlank { device.id }, unlockStatus = "准备解锁", unlockFlowState = UnlockFlowState.PreChecking(), unlockElapsedSeconds = 0, unlockFlowHidden = false)
            }
            unlockTimerJob?.cancel()
            unlockTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    val cur = state.value.unlockFlowState
                    if (cur is UnlockFlowState.Working) {
                        _state.update { it.copy(unlockElapsedSeconds = cur.elapsedSeconds + 1) }
                    }
                }
            }
            unlockTimeoutJob?.cancel()
            unlockTimeoutJob = viewModelScope.launch {
                delay(165_000)
                if (state.value.unlockFlowState is UnlockFlowState.Working) {
                    unlockTimerJob?.cancel()
                    _state.update {
                        it.copy(
                            unlocking = false,
                            unlockStatus = null,
                            unlockFlowState = UnlockFlowState.Idle,
                            unlockElapsedSeconds = 0,
                            unlockFlowHidden = false,
                            unlockingDeviceId = null,
                            orderHistory = repository.orderHistory(),
                        )
                    }
                    refreshBalance()
                    refreshDevices()
                    showToast("饮水机已自动关闭并结算")
                }
            }
            runCatching {
                repository.unlockDevice(device, usePoints = state.value.usePointsForUnlock) { step ->
                    val isWorking = step.contains("等待") || step.contains("设备工作") ||
                        step.contains("创建后付") || step.contains("查询订单")
                    _state.update {
                        it.copy(unlockStatus = step, unlockFlowState = if (isWorking) UnlockFlowState.Working(step, state.value.unlockElapsedSeconds) else UnlockFlowState.PreChecking(step))
                    }
                }
            }.onSuccess { result ->
                unlockTimerJob?.cancel()
                unlockTimeoutJob?.cancel()
                _state.update { it.copy(unlocking = false, unlockStatus = null, unlockFlowState = UnlockFlowState.Success(result), unlockElapsedSeconds = 0, unlockFlowHidden = false, orderHistory = repository.orderHistory()) }
                refreshBalance()
            }.onFailure { e ->
                unlockTimerJob?.cancel()
                unlockTimeoutJob?.cancel()
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
        unlockTimeoutJob?.cancel()
        _state.update { it.copy(unlockFlowState = UnlockFlowState.Idle, unlockElapsedSeconds = 0, unlockingDeviceId = null) }
    }

    fun dismissUnlockAnimation() {
        unlockTimerJob?.cancel()
        unlockTimeoutJob?.cancel()
        _state.update { it.copy(unlockFlowHidden = true, unlockElapsedSeconds = 0) }
    }

    fun openDeviceShortcut(request: DeviceShortcutRequest) {
        pendingShortcutRequest = request
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

    // ── 快捷方式 ──
    fun updateQuickLink(index: Int, name: String, url: String, packageName: String, presetIndex: Int = -1) {
        quickLinkStore?.updateLink(index, name, url, packageName, presetIndex)
        if (presetIndex >= 0 && name.isNotBlank()) {
            quickLinkStore?.savePresetIcon(index, presetIndex)
        }
        quickLinkStore?.let {
            _state.update { s -> s.copy(quickLinks = it.getLinks()) }
        }
    }

    fun deleteQuickLink(index: Int) {
        val links = _state.value.quickLinks.toMutableList()
        if (index !in links.indices) return
        val pi = if (index < 3) index else -1
        quickLinkStore?.updateLink(index, "", "", "", pi)
        if (index >= 3) {
            for (i in index until links.size - 1) {
                val next = links[i + 1]
                quickLinkStore?.updateLink(i, next.name, next.url, next.packageName, next.presetIndex)
            }
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

    fun showQuickLinksSettings() { _state.update { it.copy(showQuickLinksSettings = true) } }
    fun dismissQuickLinksSettings() { _state.update { it.copy(showQuickLinksSettings = false) } }

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
    // ── 淋浴（趣智校园）──
    fun qzxyShowLogin() = qzxyController.showLoginSheet()
    fun qzxyDismissLogin() = qzxyController.dismissLoginSheet()
    fun qzxyUpdatePhone(value: String) = qzxyController.updatePhone(value)
    fun qzxyUpdatePassword(value: String) = qzxyController.updatePassword(value)
    fun qzxyTogglePasswordVisible() = qzxyController.togglePasswordVisibility()
    fun qzxyLogin() = qzxyController.login()
    fun qzxyLogout() = qzxyController.logout()
    fun qzxyShowLogoutConfirm() = qzxyController.showLogoutConfirm()
    fun qzxyDismissLogoutConfirm() = qzxyController.dismissLogoutConfirm()
    fun qzxyRefreshWallet() = qzxyController.refreshWallet()
    fun qzxyStartScan() = qzxyController.startScan()
    fun qzxyStopScan() = qzxyController.stopScan()
    fun qzxyOnScanPermissionDenied() = qzxyController.onScanPermissionDenied()
    fun qzxySelectDevice(device: QzxyNearbyDevice) = qzxyController.selectDevice(device)
    fun qzxySetDevicePicker(open: Boolean) = qzxyController.setDevicePicker(open)
    fun qzxyRefreshSelectedDevice() = qzxyController.refreshSelectedDevice()
    fun qzxyShowManualMacDialog() = qzxyController.showManualMacDialog()
    fun qzxyDismissManualMacDialog() = qzxyController.dismissManualMacDialog()
    fun qzxyUpdateManualMac(value: String) = qzxyController.updateManualMac(value)
    fun qzxySubmitManualMac() = qzxyController.submitManualMac()
    fun qzxyStartShower() = qzxyController.startShower()
    fun qzxyStopShower() = qzxyController.stopShower()
    fun qzxyDismissShowerFlow() = qzxyController.dismissShowerFlow()

    // ── 统计 ──
    fun refreshTodayWater() {
        _state.update { it.copy(totalWaterCount = repository.orderHistory().size) }
    }

    fun showOrderHistory() {
        _state.update { it.copy(showOrderHistory = true, orderHistory = repository.orderHistory()) }
    }
    fun dismissOrderHistory() { _state.update { it.copy(showOrderHistory = false) } }
    // ── 设置 ──
    fun showSettings() { _state.update { it.copy(showSettings = true) } }
    fun dismissSettings() { _state.update { it.copy(showSettings = false) } }

    fun updateThemeMode(mode: ThemeMode) {
        themePreferences?.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }
    fun updateColorTheme(theme: ColorTheme) {
        themePreferences?.setColorTheme(theme)
        _state.update { it.copy(colorTheme = theme) }
    }
    fun toggleHaptic() {
        val v = !state.value.hapticEnabled
        taskStateStore?.setHapticEnabled(v)
        _state.update { it.copy(hapticEnabled = v) }
    }
    fun toggleAutoSignIn() {
        val v = !state.value.autoSignInEnabled
        taskStateStore?.setAutoSignInEnabled(v)
        _state.update { it.copy(autoSignInEnabled = v) }
    }
    fun toggleUsePointsForUnlock() {
        val v = !state.value.usePointsForUnlock
        taskStateStore?.setUsePointsForUnlockEnabled(v)
        _state.update { it.copy(usePointsForUnlock = v) }
        showToast(if (v) "开水将使用积分抵扣" else "开水不使用积分抵扣")
    }

    fun showCurrentToken() {
        val token = repository.localToken()?.takeIf { it.isNotBlank() }
        _state.update { it.copy(tokenDialogText = token ?: "当前未登录，暂无 Token") }
    }
    fun dismissCurrentToken() { _state.update { it.copy(tokenDialogText = null) } }

    fun showCurrentDeviceInfo() {
        _state.update { it.copy(deviceInfoDialogText = ApiConfig.USER_AGENT) }
    }
    fun dismissCurrentDeviceInfo() { _state.update { it.copy(deviceInfoDialogText = null) } }

    fun showLogoutConfirm() { _state.update { it.copy(showLogoutConfirm = true) } }
    fun dismissLogoutConfirm() { _state.update { it.copy(showLogoutConfirm = false) } }

    // ── Lifecycle ──
    override fun onCleared() {
        unlockTimerJob?.cancel()
        unlockTimeoutJob?.cancel()
        super.onCleared()
    }
}

class AppViewModelFactory(
    private val application: Application,
    private val repository: AppRepository,
    private val appVersion: String = "",
    private val taskStateStore: PointsTaskStateStore? = null,
    private val themePreferences: ThemePreferences? = null,
    private val quickLinkStore: QuickLinkStore? = null,
    private val qzxyRepository: QzxyRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(application, repository, appVersion, taskStateStore, themePreferences, quickLinkStore, qzxyRepository) as T
    }
}
