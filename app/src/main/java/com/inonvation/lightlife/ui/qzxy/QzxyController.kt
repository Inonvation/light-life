package com.inonvation.lightlife.ui.qzxy

import com.inonvation.lightlife.data.qzxy.QzxyActiveShower
import com.inonvation.lightlife.data.qzxy.QzxyApiConfig
import com.inonvation.lightlife.data.qzxy.QzxyApiException
import com.inonvation.lightlife.data.qzxy.QzxyBluetoothScanner
import com.inonvation.lightlife.data.qzxy.QzxyBoundDevice
import com.inonvation.lightlife.data.qzxy.QzxyDeviceInfo
import com.inonvation.lightlife.data.qzxy.QzxyNearbyDevice
import com.inonvation.lightlife.data.qzxy.QzxyRepository
import com.inonvation.lightlife.data.qzxy.QzxySessionExpiredException
import com.inonvation.lightlife.data.qzxy.QzxySettleResult
import com.inonvation.lightlife.ui.AppUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * 趣智校园控制器，仿照 AuthController 的委托模式挂进 AppViewModel。
 * 管登录、设备绑定、蓝牙扫描、洗澡状态机；胖乖生活平台的登录与数据不受影响。
 *
 * 交互模型：绑定设备是主操作对象（查询到详情即绑定并持久化），
 * 扫描/手输 MAC 是"更换设备"里的次要入口。
 */
class QzxyController(
    private val state: StateFlow<AppUiState>,
    private val updateQzxy: ((QzxyUiState) -> QzxyUiState) -> Unit,
    private val scope: CoroutineScope,
    private val repository: QzxyRepository,
    private val scanner: QzxyBluetoothScanner,
    private val showToast: (String) -> Unit,
    private val showError: (String) -> Unit,
) {
    private val scanResults = ConcurrentHashMap<String, QzxyNearbyDevice>()
    private val enrichingMacs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val revealedMacs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val enrichSemaphore = Semaphore(4)
    private var timerJob: Job? = null
    private var scanTimeoutJob: Job? = null

    // ── 会话恢复 ──

    fun restoreSession() {
        val session = repository.session()
        if (session == null) {
            updateQzxy { QzxyUiState() }
            return
        }
        updateQzxy {
            it.copy(
                loggedIn = true,
                userName = session.userName,
                accountPhone = maskPhone(session.telephone),
                boundDevice = repository.readBoundDevice(),
            )
        }
        refreshWallet()
        restoreActiveOrder()
        refreshBoundDeviceInfo()
    }

    /** App 冷启动或重新登录后，检查绑定设备是否还有进行中的订单（含使用码启动的场景） */
    private fun restoreActiveOrder() = scope.launch {
        if (state.value.qzxy.showerFlow !is QzxyShowerState.Idle) return@launch
        val bound = state.value.qzxy.boundDevice ?: return@launch
        if (bound.snCode.isBlank()) return@launch
        val status = runCatching { repository.queryUsing(bound.snCode) }.getOrNull()
            ?: return@launch
        val orderNo = status?.orderNo?.takeIf { it.isNotBlank() } ?: return@launch
        val active = QzxyActiveShower(
            mac = bound.mac,
            snCode = bound.snCode,
            orderNo = orderNo,
            deviceName = bound.name.ifBlank { "热水器" },
        )
        updateQzxy {
            it.copy(
                activeOrder = active,
                showerFlow = QzxyShowerState.Running(deviceName = active.deviceName, withholdMoney = "-"),
            )
        }
        startTimer()
        showToast("检测到进行中的洗澡订单，已恢复")
    }

    /** 静默刷新绑定设备的在线状态与预扣金额，失败不打扰 */
    private fun refreshBoundDeviceInfo() = scope.launch {
        val bound = state.value.qzxy.boundDevice ?: return@launch
        if (bound.mac.isBlank()) return@launch
        runCatching { repository.deviceInfo(bound.mac) }
            .onSuccess { info -> updateQzxy { it.copy(selectedDevice = info) } }
            .onFailure { e -> if (e is QzxySessionExpiredException) handleSessionExpired() }
    }

    // ── 登录 ──

    fun showLoginSheet() = updateQzxy { it.copy(showLoginSheet = true, phone = "", password = "", loginError = null) }
    fun dismissLoginSheet() = updateQzxy { it.copy(showLoginSheet = false) }

    fun updatePhone(value: String) = updateQzxy { it.copy(phone = value.filter { c -> c.isDigit() }.take(11)) }
    fun updatePassword(value: String) = updateQzxy { it.copy(password = value) }
    fun togglePasswordVisibility() = updateQzxy { it.copy(passwordVisible = !it.passwordVisible) }

    fun login() = scope.launch {
        val q = state.value.qzxy
        val phone = q.phone.trim()
        val error = when {
            !PHONE_REGEX.matches(phone) -> "请输入正确格式的手机号"
            q.password.isBlank() -> "请输入密码"
            else -> null
        }
        if (error != null) {
            updateQzxy { it.copy(loginError = error) }
            return@launch
        }
        updateQzxy { it.copy(loggingIn = true, loginError = null) }
        runCatching { repository.login(phone, q.password) }
            .onSuccess { session ->
        updateQzxy {
            it.copy(
                loggedIn = true,
                userName = session.userName,
                accountPhone = maskPhone(session.telephone),
                loggingIn = false,
                showLoginSheet = false,
                phone = "",
                password = "",
                boundDevice = repository.readBoundDevice(),
            )
        }
                showToast("趣智校园登录成功")
                refreshWallet()
                restoreActiveOrder()
                refreshBoundDeviceInfo()
            }
            .onFailure { e ->
                updateQzxy { it.copy(loggingIn = false, loginError = e.message ?: "登录失败") }
            }
    }

    fun logout() {
        stopEverything()
        repository.logout()
        updateQzxy {
            // 保留绑定设备，退出后重登无需重新扫描绑定
            QzxyUiState(
                boundDevice = state.value.qzxy.boundDevice,
                selectedDevice = state.value.qzxy.selectedDevice,
            )
        }
        showToast("已退出趣智校园登录")
    }

    fun showLogoutConfirm() = updateQzxy { it.copy(showLogoutConfirm = true) }
    fun dismissLogoutConfirm() = updateQzxy { it.copy(showLogoutConfirm = false) }

    /** 被挤号或会话过期：清空趣智全部状态并引导重新登录 */
    private fun handleSessionExpired() {
        stopEverything()
        repository.logout()
        updateQzxy { QzxyUiState() }
        showError("趣智校园登录已失效，请重新登录")
    }

    private fun stopEverything() {
        stopTimer()
        stopScanInternal()
    }

    // ── 钱包 ──

    fun refreshWallet() = scope.launch {
        if (!state.value.qzxy.loggedIn) return@launch
        updateQzxy { it.copy(loadingWallet = true) }
        runCatching { repository.wallet() }
            .onSuccess { wallet -> updateQzxy { it.copy(wallet = wallet, loadingWallet = false) } }
            .onFailure { e ->
                updateQzxy { it.copy(loadingWallet = false) }
                if (e is QzxySessionExpiredException) handleSessionExpired()
                else showError(e.message ?: "查询钱包失败")
            }
    }

    // ── 绑定设备 ──

    /** 查询设备详情；成功即绑定并收起设备选择器 */
    fun selectDevice(device: QzxyNearbyDevice) = scope.launch {
        val q = state.value.qzxy
        if (q.queryingMac != null) return@launch
        if (q.showerFlow !is QzxyShowerState.Idle) {
            showError("洗澡进行中，暂不能更换设备")
            return@launch
        }
        updateQzxy { it.copy(queryingMac = device.mac, selectedDevice = null) }
        runCatching { repository.deviceInfo(device.mac) }
            .onSuccess { info ->
                val bound = QzxyBoundDevice(
                    mac = info.macAddress?.takeIf { it.isNotBlank() } ?: device.mac,
                    snCode = info.snCode.orEmpty(),
                    name = info.displayName,
                )
                repository.saveBoundDevice(bound)
                updateQzxy {
                    it.copy(
                        queryingMac = null,
                        selectedDevice = info,
                        boundDevice = bound,
                        showDevicePicker = false,
                    )
                }
                showToast("已绑定设备：${bound.name}")
            }
            .onFailure { e ->
                updateQzxy { it.copy(queryingMac = null) }
                if (e is QzxySessionExpiredException) handleSessionExpired()
                else showError(e.message ?: "查询设备信息失败")
            }
    }

    /** 刷新绑定设备的在线状态与预扣金额（用户手动触发） */
    fun refreshSelectedDevice() = scope.launch {
        val q = state.value.qzxy
        if (q.queryingMac != null) return@launch
        val mac = q.selectedDevice?.macAddress?.takeIf { it.isNotBlank() }
            ?: q.boundDevice?.mac?.takeIf { it.isNotBlank() }
            ?: return@launch
        updateQzxy { it.copy(queryingMac = mac) }
        runCatching { repository.deviceInfo(mac) }
            .onSuccess { info -> updateQzxy { it.copy(queryingMac = null, selectedDevice = info) } }
            .onFailure { e ->
                updateQzxy { it.copy(queryingMac = null) }
                if (e is QzxySessionExpiredException) handleSessionExpired()
                else showError(e.message ?: "刷新设备状态失败")
            }
    }

    // ── 更换设备（次要入口）──

    fun setDevicePicker(open: Boolean) {
        if (open) {
            updateQzxy { it.copy(showDevicePicker = true) }
        } else {
            stopScanInternal()
            updateQzxy { it.copy(showDevicePicker = false) }
        }
    }

    // ── 蓝牙扫描 ──

    fun startScan() {
        val q = state.value.qzxy
        if (!q.loggedIn) {
            showError("请先登录趣智校园")
            return
        }
        if (q.scanning) return
        scanResults.clear()
        enrichingMacs.clear()
        revealedMacs.clear()
        updateQzxy { it.copy(scanning = true, nearbyDevices = emptyList()) }
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(QzxyApiConfig.SCAN_DURATION_MS)
            stopScanInternal()
            if (state.value.qzxy.nearbyDevices.isEmpty()) {
                showToast("未发现附近热水器，可尝试手输 MAC 地址")
            }
        }
        scanner.start(
            onFound = { device ->
                // 重复广播只更新信号强度，保留已查到的设备详情
                val existing = scanResults[device.mac]
                val merged = existing?.copy(rssi = device.rssi) ?: device
                scanResults[device.mac] = merged
                if (existing == null) {
                    // 新设备：查到设备名（或超时兜底）才进列表，避免"先出广播名再替换"
                    enrichDevice(device.mac)
                } else if (device.mac in revealedMacs) {
                    publishNearby()
                }
            },
            onError = { message ->
                stopScanInternal()
                showError(message)
            },
        )
    }

    /**
     * 扫描发现新设备后自动按 MAC 查服务器真实设备名。
     * 查到名字（或超时兜底）之前设备不进列表——列表里出现的直接是真名。
     * 多设备并行查询（并发上限 4），蓝牙地址与注册地址在仓库层并行竞速。
     */
    private fun enrichDevice(mac: String) {
        if (!enrichingMacs.add(mac)) return
        scope.launch {
            enrichSemaphore.withPermit {
                val pending = async { runCatching { repository.deviceInfo(mac) }.getOrNull() }
                val info = withTimeoutOrNull(QzxyApiConfig.ENRICH_REVEAL_TIMEOUT_MS) { pending.await() }
                applyEnrichResult(mac, info)
                if (info == null) {
                    // 超时兜底后查询仍在后台继续，拿到名字再原地更新
                    val late = pending.await()
                    if (late != null) applyEnrichResult(mac, late)
                }
            }
            enrichingMacs.remove(mac)
        }
    }

    private fun applyEnrichResult(mac: String, info: QzxyDeviceInfo?) {
        scanResults[mac]?.let { current ->
            scanResults[mac] = current.copy(info = info ?: current.info, nameLoading = false)
        }
        revealedMacs.add(mac)
        publishNearby()
    }

    private fun publishNearby() {
        val sorted = scanResults.values
            .filter { it.mac in revealedMacs }
            .sortedByDescending { it.rssi }
        updateQzxy { it.copy(nearbyDevices = sorted) }
    }

    fun stopScan() = stopScanInternal()

    private fun stopScanInternal() {
        scanTimeoutJob?.cancel()
        scanner.stop()
        updateQzxy { it.copy(scanning = false) }
    }

    fun onScanPermissionDenied() {
        updateQzxy { it.copy(scanning = false) }
        showError("缺少蓝牙权限，无法扫描附近设备")
    }

    // ── 手输 MAC ──

    fun showManualMacDialog() = updateQzxy { it.copy(showManualMacDialog = true, manualMacInput = "") }
    fun dismissManualMacDialog() = updateQzxy { it.copy(showManualMacDialog = false) }
    fun updateManualMac(value: String) = updateQzxy { it.copy(manualMacInput = value.trim().uppercase()) }

    fun submitManualMac() {
        val mac = state.value.qzxy.manualMacInput.trim().uppercase()
        if (!MAC_REGEX.matches(mac)) {
            showError("MAC 地址格式不正确，例如 C4:7F:0E:12:34:56")
            return
        }
        updateQzxy { it.copy(showManualMacDialog = false) }
        selectDevice(QzxyNearbyDevice(mac = mac, name = "手动输入设备", rssi = 0))
    }

    // ── 洗澡状态机 ──

    fun startShower() {
        val q = state.value.qzxy
        if (q.showerFlow !is QzxyShowerState.Idle) return
        val selected = q.selectedDevice
        val bound = q.boundDevice
        val snCode = selected?.snCode?.takeIf { it.isNotBlank() }
            ?: bound?.snCode?.takeIf { it.isNotBlank() }
        if (snCode == null) {
            showError("请先绑定设备")
            return
        }
        launchShower(
            mac = selected?.macAddress?.takeIf { it.isNotBlank() } ?: bound?.mac.orEmpty(),
            snCode = snCode,
            deviceName = selected?.displayName ?: bound?.name ?: "热水器",
            withhold = selected?.withholdMoney,
        )
    }

    private fun launchShower(mac: String, snCode: String, deviceName: String, withhold: Double?) {
        updateQzxy { it.copy(showerFlow = QzxyShowerState.Starting("正在准备…"), elapsedSeconds = 0) }
        startTimer()
        scope.launch {
            runCatching {
                repository.startShower(mac, snCode, deviceName, withhold) { step ->
                    updateQzxy { it.copy(showerFlow = QzxyShowerState.Starting(step)) }
                }
            }.onSuccess { active ->
                val bound = QzxyBoundDevice(
                    mac = active.mac.ifBlank { mac },
                    snCode = active.snCode,
                    name = active.deviceName,
                )
                repository.saveBoundDevice(bound)
                updateQzxy {
                    it.copy(
                        boundDevice = bound,
                        activeOrder = active,
                        showerFlow = QzxyShowerState.Running(
                            deviceName = active.deviceName,
                            withholdMoney = active.preDeduct?.let { money -> "¥%.2f".format(money) } ?: "-",
                            autoCloseSecondsLeft = active.autoCloseSeconds,
                        ),
                    )
                }
                showToast(if (active.resumed) "检测到进行中的订单，已恢复使用" else "已开阀，开始使用")
            }.onFailure { e ->
                stopTimer()
                if (e is QzxySessionExpiredException) {
                    handleSessionExpired()
                    return@launch
                }
                updateQzxy {
                    it.copy(
                        showerFlow = QzxyShowerState.Failed(e.message ?: "启动失败", "启动", rawOf(e)),
                    )
                }
            }
        }
    }

    fun stopShower() {
        val q = state.value.qzxy
        val active = q.activeOrder ?: return
        if (q.showerFlow !is QzxyShowerState.Running && q.showerFlow !is QzxyShowerState.Failed) return
        val elapsed = q.elapsedSeconds
        updateQzxy { it.copy(showerFlow = QzxyShowerState.Stopping("正在关阀…")) }
        scope.launch {
            runCatching {
                repository.stopShower(active) { step ->
                    updateQzxy { it.copy(showerFlow = QzxyShowerState.Stopping(step)) }
                }
            }.onSuccess { stop ->
                stopTimer()
                updateQzxy {
                    it.copy(
                        activeOrder = null,
                        showerFlow = QzxyShowerState.Done(
                            QzxySettleResult(
                                deviceName = active.deviceName,
                                elapsedSeconds = elapsed,
                                consumeMoneyText = stop.consumeMoney?.let { money -> "¥%.2f".format(money) }
                                    ?: "以账单为准",
                                consumeTime = stop.consumeTime,
                            )
                        ),
                    )
                }
                refreshWallet()
            }.onFailure { e ->
                stopTimer()
                if (e is QzxySessionExpiredException) {
                    handleSessionExpired()
                    return@launch
                }
                updateQzxy {
                    it.copy(
                        showerFlow = QzxyShowerState.Failed(e.message ?: "关阀失败", "结束使用", rawOf(e)),
                    )
                }
            }
        }
    }

    fun dismissShowerFlow() {
        stopTimer()
        updateQzxy { it.copy(showerFlow = QzxyShowerState.Idle, elapsedSeconds = 0, activeOrder = null) }
    }

    /** 设备已被外部关闭（闲置自动关停/他人在设备上操作）时，走结算流程收尾 */
    private fun checkExternalClose() = scope.launch {
        val q = state.value.qzxy
        val active = q.activeOrder ?: return@launch
        if (q.showerFlow !is QzxyShowerState.Running) return@launch
        val status = runCatching { repository.queryUsing(active.snCode) }.getOrNull() ?: return@launch
        if (status?.orderNo.isNullOrBlank()) {
            showToast("设备已停止出水（可能已自动关停），正在查询本次消费…")
            stopShower()
        }
    }

    /** 原始报错附上错误码，便于排查学校接口差异 */
    private fun rawOf(e: Throwable): String =
        if (e is QzxyApiException) "errorCode=${e.errorCode ?: "-"}：${e.message}" else e.message.orEmpty()

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val q = state.value.qzxy
                when (val flow = q.showerFlow) {
                    is QzxyShowerState.Starting, is QzxyShowerState.Stopping ->
                        updateQzxy { it.copy(elapsedSeconds = q.elapsedSeconds + 1) }
                    is QzxyShowerState.Running -> {
                        val auto = flow.autoCloseSecondsLeft
                        val newAuto = if (auto != null && auto > 0) auto - 1 else auto
                        if (auto != null && auto > 0 && newAuto == 0) {
                            showToast("设备闲置时间已到，可能已自动关停；请点击「结束使用」查看本次消费")
                        }
                        val newElapsed = q.elapsedSeconds + 1
                        updateQzxy {
                            it.copy(
                                elapsedSeconds = newElapsed,
                                showerFlow = flow.copy(autoCloseSecondsLeft = newAuto),
                            )
                        }
                        // 每 30 秒查一次订单状态，兜底检测设备被外部关闭/自动关停
                        if (newElapsed > 0 && newElapsed % 30 == 0) checkExternalClose()
                    }
                    else -> {
                        stopTimer()
                        return@launch
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private companion object {
        val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
        val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

        fun maskPhone(phone: String): String =
            if (phone.length == 11) phone.take(3) + "****" + phone.takeLast(4) else phone
    }
}
