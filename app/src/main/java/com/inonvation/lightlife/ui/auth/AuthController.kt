package com.inonvation.lightlife.ui.auth

import com.inonvation.lightlife.data.AppRepository
import com.inonvation.lightlife.data.DebugLogStore
import com.inonvation.lightlife.data.PointsStatsStore
import com.inonvation.lightlife.data.PointsTaskStateStore
import com.inonvation.lightlife.data.TaskLogStore
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.DeviceTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthController(
    private val state: MutableStateFlow<AppUiState>,
    private val scope: CoroutineScope,
    private val repository: AppRepository,
    private val taskStateStore: PointsTaskStateStore?,
    private val logStore: TaskLogStore?,
    private val debugLogStore: DebugLogStore?,
    private val pointsStatsStore: PointsStatsStore?,
    private val clearAdVideoState: () -> Unit,
    private val onAuthSuccess: () -> Unit,
    private val showToast: (String) -> Unit,
    private val showError: (String) -> Unit,
) {
    private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
    private var codeSentTimestamp: Long = 0

    fun updatePhone(value: String) {
        state.update { it.copy(phone = value, phoneError = null) }
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && !PHONE_REGEX.matches(trimmed)) {
            state.update { it.copy(phoneError = "请输入正确格式的手机号") }
        }
    }

    fun updateCode(value: String) {
        val filtered = value.filter { it.isDigit() }
        state.update { it.copy(code = filtered) }
    }

    fun toggleTokenLogin() {
        state.update { it.copy(showTokenLogin = !it.showTokenLogin, tokenLoginInput = "", tokenLoginVisible = false) }
    }

    fun updateTokenLoginInput(value: String) {
        state.update { it.copy(tokenLoginInput = value) }
    }

    fun toggleTokenLoginVisibility() {
        state.update { it.copy(tokenLoginVisible = !it.tokenLoginVisible) }
    }

    fun loginWithToken() = scope.launch {
        val token = state.value.tokenLoginInput
        if (token.isBlank()) {
            showError("请输入 Token")
            return@launch
        }
        runCatching {
            state.update { it.copy(tokenLoggingIn = true) }
            repository.saveToken(token)
            repository.validateToken()
        }.onSuccess {
            state.update { it.copy(hasToken = true, tokenLoggingIn = false, showTokenLogin = false, tokenLoginInput = "") }
            showToast("登录成功")
            onAuthSuccess()
        }.onFailure {
            state.update { it.copy(tokenLoggingIn = false) }
            repository.clearToken()
            showError(it.message ?: "Token 无效或已过期")
        }
    }

    fun sendCode() = scope.launch {
        val phone = state.value.phone.trim()
        val elapsed = System.currentTimeMillis() - codeSentTimestamp
        if (elapsed < 60_000) {
            showError("验证码已发送，请${(60 - elapsed / 1000).toInt()}秒后再试")
            return@launch
        }
        if (phone.isBlank() || !PHONE_REGEX.matches(phone)) {
            state.update { it.copy(phoneError = "请输入正确格式的手机号") }
            return@launch
        }
        runCatching {
            state.update { it.copy(sendingCode = true) }
            repository.sendCode(phone)
        }.onSuccess {
            codeSentTimestamp = System.currentTimeMillis()
            showToast("验证码已发送")
        }.onFailure {
            showError(it.message ?: "验证码发送失败")
        }
        state.update { it.copy(sendingCode = false) }
    }

    fun login() = scope.launch {
        val phone = state.value.phone.trim()
        val code = state.value.code.trim()
        if (phone.isBlank() || !PHONE_REGEX.matches(phone)) {
            state.update { it.copy(phoneError = "请输入正确格式的手机号") }
            return@launch
        }
        if (code.isBlank()) {
            showError("请输入验证码")
            return@launch
        }
        runCatching {
            state.update { it.copy(loggingIn = true) }
            repository.login(phone, code)
        }.onSuccess {
            state.update { it.copy(hasToken = true, loggingIn = false, phoneError = null) }
            showToast("登录成功")
            repository.savePhone(phone)
            onAuthSuccess()
        }.onFailure {
            state.update { it.copy(loggingIn = false) }
            showError(it.message ?: "登录失败")
        }
    }

    fun logout() {
        repository.clearToken()
        repository.savePhone("")
        pointsStatsStore?.clearAll()
        repository.clearOrderHistory()
        taskStateStore?.reset()
        clearAdVideoState()
        logStore?.clearAll()
        debugLogStore?.clearAll()
        state.update { it.copy(
            hasToken = false,
            phone = "",
            code = "",
            phoneError = null,
            devices = emptyList(),
            balance = null,
            todayWaterCount = 0,
            todayWaterAmount = "0.00",
            totalWaterCount = 0,
            orderHistory = emptyList(),
            pointsLogs = emptyList(),
            totalPointsDeducted = "0.00",
            showSettings = false,
            currentTab = DeviceTab.Me,
        )}
    }

    fun handleTokenExpired() {
        debugLogStore?.d("VM", "handleTokenExpired: token expired")
        repository.clearToken()
        state.update { it.copy(
            hasToken = false,
            devices = emptyList(),
            balance = null,
            currentTab = DeviceTab.Me,
        )}
        showToast("登录已失效，请重新登录")
    }
}
