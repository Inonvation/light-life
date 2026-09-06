package com.inonvation.lightlife.data.qzxy

import com.inonvation.lightlife.data.HttpClientProvider
import com.inonvation.lightlife.data.MoshiProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * 趣智校园业务封装：登录态、钱包、设备详情、洗澡订单全流程。
 * 独立 Retrofit 实例：baseUrl 不同，且趣智认证走参数不走 Header，不需要胖乖生活的 HeaderInterceptor。
 */
class QzxyRepository(private val authStore: QzxyAuthStore) {

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val api: QzxyApi = Retrofit.Builder()
        .baseUrl(QzxyApiConfig.BASE_URL)
        .client(HttpClientProvider.client.newBuilder().addInterceptor(logging).build())
        .addConverterFactory(MoshiConverterFactory.create(MoshiProvider.instance))
        .build()
        .create(QzxyApi::class.java)

    // ── 会话 ──

    fun session(): QzxySession? = authStore.readSession()
    fun readBoundDevice(): QzxyBoundDevice? = authStore.readBoundDevice()
    fun saveBoundDevice(device: QzxyBoundDevice) = authStore.saveBoundDevice(device)
    fun logout() = authStore.clearSession()

    private fun requireSession(): QzxySession = session() ?: error("请先登录趣智校园")

    /**
     * 统一调用入口：业务错误转 QzxyApiException，
     * HTTP 401/403 与"会话失效"文案统一转 QzxySessionExpiredException。
     */
    private suspend fun <T> call(block: suspend () -> QzxyEnvelope<T>): QzxyEnvelope<T> =
        try {
            block().throwIfFailed()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) throw QzxySessionExpiredException()
            throw e
        }

    /** 吞掉业务失败的调用（确认类请求失败不应阻断流程），但会话失效必须继续上抛 */
    private suspend fun <T> swallowErrors(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: QzxySessionExpiredException) {
            throw e
        } catch (_: Exception) {
            null
        }

    // ── 登录 / 钱包 / 设备 ──

    suspend fun login(telephone: String, password: String): QzxySession {
        val resp = call { api.login(telephone = telephone, password = QzxyPassword.encrypt(password)) }
        val data = resp.data ?: error("登录响应缺少数据")
        val loginCode = data.loginCode?.takeIf { it.isNotBlank() } ?: error("登录响应缺少登录凭证")
        val account = data.userAccount
        val session = QzxySession(
            loginCode = loginCode,
            userId = data.userId?.toString().orEmpty(),
            accountId = account?.accountId?.toString().orEmpty(),
            // projectId 从登录响应读取并存储，适配不同学校（linyu 原项目是硬编码的）
            projectId = account?.projectId?.toString().orEmpty(),
            telephone = telephone,
            userName = account?.name.orEmpty(),
        )
        authStore.saveSession(session)
        return session
    }

    suspend fun wallet(): QzxyWalletData =
        call { api.getWallet(requireSession().queryFields()) }.data ?: error("未获取到钱包信息")

    suspend fun deviceInfo(mac: String): QzxyDeviceInfo {
        val session = requireSession()
        val transformedMac = registeredMacCandidate(mac)
        if (transformedMac == null) {
            return queryDevice(mac, session)
                ?: error("未找到该设备，请确认 MAC 地址是否正确")
        }
        // 蓝牙地址与其注册地址并行查询，谁先查到用谁，把补名延迟压到单次请求
        return coroutineScope {
            val direct = async { queryDevice(mac, session) }
            val transformed = async { queryDevice(transformedMac, session) }
            select<QzxyDeviceInfo?> {
                direct.onAwait { it ?: transformed.await() }
                transformed.onAwait { it ?: direct.await() }
            } ?: error("未找到该设备，请确认 MAC 地址是否正确")
        }
    }

    private suspend fun queryDevice(mac: String, session: QzxySession): QzxyDeviceInfo? =
        call { api.getDeviceInfo(mac, session.queryFields()) }.data

    /** 蓝牙地址（首字节 & 0xC0 == 0xC0）对应的疑似注册地址 */
    private fun registeredMacCandidate(mac: String): String? {
        val first = mac.take(2).toIntOrNull(16) ?: return null
        if (first and 0xC0 != 0xC0) return null
        return "%02X".format(first and 0x3F) + mac.substring(2)
    }

    // ── 订单 ──

    /**
     * 查询设备进行中的订单。
     * errorCode 307 表示设备正被使用，此时 data 可能仍带订单信息，不能当普通错误抛出。
     */
    suspend fun queryUsing(snCode: String): QzxyOrderStatus? {
        val session = requireSession()
        val resp = try {
            api.queryUsing(snCode = snCode, auth = session.authFields())
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) throw QzxySessionExpiredException()
            throw e
        }
        if (resp.isSessionExpired()) throw QzxySessionExpiredException(resp.displayMessage ?: "趣智校园登录已失效，请重新登录")
        if (resp.errorCode == 307) return resp.data ?: QzxyOrderStatus(orderNo = null, isOwner = false)
        resp.throwIfFailed()
        return resp.data ?: QzxyOrderStatus(orderNo = null)
    }

    /**
     * 开始洗澡：查占用 → 开阀 → 确认 → 拿订单号（流程对齐 linyu 验证过的实现）。
     * 1. 先查进行中订单：已有自己的订单就直接恢复，不发开阀命令
     * 2. downRate 开阀（设备离线时此步会收到服务器报错，如实上抛）
     * 3. 轮询 downRateResult 确认开阀（state/result=0 或已返回订单号），避免设备离线时误以为已开
     * 4. 订单号不在开阀响应里时轮询 queryUsing 获取
     */
    suspend fun startShower(
        mac: String,
        snCode: String,
        deviceName: String,
        fallbackWithhold: Double? = null,
        onStep: suspend (String) -> Unit,
    ): QzxyActiveShower {
        val session = requireSession()

        onStep("正在检查设备状态")
        val existing = swallowErrors { queryUsing(snCode) }
        if (existing?.orderNo?.isNotBlank() == true) {
            return QzxyActiveShower(
                mac = mac,
                snCode = snCode,
                orderNo = existing.orderNo,
                deviceName = deviceName,
                preDeduct = fallbackWithhold,
                resumed = true,
            )
        }

        onStep("正在发送开阀指令")
        val down = call { api.downRate(snCode = snCode, auth = session.authFields()) }
        val downInfo = down.data

        onStep("正在确认开阀结果")
        var confirm = downInfo
        fun QzxyDownRateResult.opened(): Boolean =
            state == 0 || result == 0 || orderNo?.isNotBlank() == true
        var opened = confirm?.opened() == true
        var attempts = 0
        while (!opened && attempts < QzxyApiConfig.OPEN_CONFIRM_MAX_ATTEMPTS) {
            delay(QzxyApiConfig.OPEN_CONFIRM_INTERVAL_MS)
            confirm = swallowErrors {
                call { api.downRateResult(snCode = snCode, auth = session.authFields()) }.data
            }
            opened = confirm?.opened() == true
            attempts++
        }
        if (!opened) error("开阀未确认成功，请确认热水器是否已开启")

        onStep("正在获取订单号")
        var orderNo = confirm?.orderNo?.takeIf { it.isNotBlank() }
        var polls = 0
        while (orderNo.isNullOrBlank() && polls < QzxyApiConfig.ORDER_POLL_MAX_ATTEMPTS) {
            onStep("正在获取订单号（${polls + 1}/${QzxyApiConfig.ORDER_POLL_MAX_ATTEMPTS}）")
            delay(QzxyApiConfig.ORDER_POLL_INTERVAL_MS)
            orderNo = swallowErrors { queryUsing(snCode) }?.orderNo?.takeIf { it.isNotBlank() }
            polls++
        }
        val finalOrderNo = orderNo ?: error("设备未返回订单号，请稍后重试")

        return QzxyActiveShower(
            mac = mac,
            snCode = snCode,
            orderNo = finalOrderNo,
            deviceName = deviceName,
            preDeduct = confirm?.preDeductMoney ?: downInfo?.preDeductMoney ?: fallbackWithhold,
            autoCloseSeconds = listOfNotNull(confirm?.autoDisConTime, downInfo?.autoDisConTime).firstOrNull { it > 0 },
        )
    }

    /**
     * 停止洗澡：关阀 → 确认关阀 → 查询本次消费。
     * 若订单已被设备自动关停，关阀指令会报错，此时跳过直接走结算。
     */
    suspend fun stopShower(
        active: QzxyActiveShower,
        onStep: suspend (String) -> Unit,
    ): QzxyStopResult {
        val session = requireSession()

        onStep("正在发送关阀指令")
        try {
            call { api.closeOrder(snCode = active.snCode, orderNo = active.orderNo, auth = session.authFields()) }
        } catch (e: QzxySessionExpiredException) {
            throw e
        } catch (_: Exception) {
            // 订单可能已被设备自动关停，继续走确认/结算
        }

        onStep("正在确认关阀结果")
        var last: QzxyCloseOrderResult? = null
        var attempts = 0
        while (attempts < QzxyApiConfig.CLOSE_CONFIRM_MAX_ATTEMPTS) {
            delay(QzxyApiConfig.CLOSE_CONFIRM_INTERVAL_MS)
            last = swallowErrors {
                call { api.closeOrderResult(snCode = active.snCode, orderNo = active.orderNo, auth = session.authFields()) }.data
            }
            val closed = last?.let { it.state == 0 || it.status == 0 || it.result == 0 } ?: false
            if (closed) break
            attempts++
        }
        val final = last
        val closed = final != null && (final.state == 0 || final.status == 0 || final.result == 0)
        var consumeMoney = final?.consumeMoney ?: final?.consumeMoneyStr?.toDoubleOrNull()
        if (!closed && consumeMoney == null) error("未能确认关阀结果，设备可能仍在出水，请重试")

        onStep("正在查询本次消费")
        var consumeTime = final?.consumeTime
        if (consumeMoney == null) {
            val consume = swallowErrors {
                call { api.consumeOrderResult(snCode = active.snCode, orderNo = active.orderNo, auth = session.authFields()) }.data
            }
            consumeMoney = consume?.consumeMoney ?: consume?.consumeMoneyStr?.toDoubleOrNull()
            consumeTime = consumeTime ?: consume?.consumeTime
        }
        return QzxyStopResult(consumeMoney = consumeMoney, consumeTime = consumeTime)
    }
}
