package com.inonvation.lightlife.data.qzxy

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
class QzxyRepository(
    private val appContext: Context,
    private val authStore: QzxyAuthStore,
) {

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
        val orderNo = active.orderNo?.takeIf { it.isNotBlank() } ?: error("缺少订单号，无法关阀")

        onStep("正在发送关阀指令")
        try {
            call { api.closeOrder(snCode = active.snCode, orderNo = orderNo, auth = session.authFields()) }
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
                call { api.closeOrderResult(snCode = active.snCode, orderNo = orderNo, auth = session.authFields()) }.data
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
                call { api.consumeOrderResult(snCode = active.snCode, orderNo = orderNo, auth = session.authFields()) }.data
            }
            consumeMoney = consume?.consumeMoney ?: consume?.consumeMoneyStr?.toDoubleOrNull()
            consumeTime = consumeTime ?: consume?.consumeTime
        }
        return QzxyStopResult(consumeMoney = consumeMoney, consumeTime = consumeTime)
    }

    // ── 蓝牙直控（communicationTypeId=0 的设备，流程对齐 JUWP-schedule 本校真机验证版）──

    @Volatile
    private var btClient: QzxyBtClient? = null

    fun closeBt() {
        btClient?.close()
        btClient = null
    }

    /**
     * GATT 连接候选地址。BLE 广播地址（静态随机，首字节最高两位为 1，实测 C0 开头）
     * 排最前——GATT 只能连到设备广播用的那个地址；服务端登记值（00 开头）连不上，
     * 只在广播形态推不出来时兜底。
     */
    private fun btAddressCandidates(mac: String): List<String> {
        val hex = mac.replace(":", "").replace("-", "").uppercase()
        if (hex.length != 12) return listOf(mac)
        val first = hex.take(2).toIntOrNull(16) ?: return listOf(mac)
        val advertVariant = "%02X".format(first or 0xC0) + hex.drop(2)
        val registeredVariant = "%02X".format(first and 0x3F) + hex.drop(2)
        return if (first and 0xC0 == 0xC0) {
            listOf(hex, registeredVariant)
        } else {
            listOf(advertVariant, hex)
        }
    }

    /**
     * 下单用的设备 MAC：必须是**服务端登记值**。Android 上报的蓝牙地址首字节是 C0，
     * 设备在服务端登记的是 00，后五字节相同——混用会让订单挂到「另一台设备」上，
     * 结算时报「未找到订单」。
     */
    private fun registeredOrderMac(mac: String, info: QzxyDeviceInfo): String {
        val raw = info.macAddress?.takeIf { it.isNotBlank() } ?: mac
        val hex = raw.replace(":", "").replace("-", "").uppercase()
        val first = hex.take(2).toIntOrNull(16) ?: return hex
        return if (first and 0xC0 == 0xC0) "%02X".format(first and 0x3F) + hex.drop(2) else hex
    }

    private suspend fun ensureBtLink(addressCandidates: List<String>): QzxyBtClient {
        btClient?.let { existing ->
            if (addressCandidates.any { existing.isConnectedTo(it) }) return existing
        }
        closeBt()
        return QzxyBtClient.connect(appContext, addressCandidates, QzxyApiConfig.BT_CONNECT_TIMEOUT_MS)
            .also { btClient = it }
    }

    /** 发一帧并解析回包；超时或解不出帧返回 null。回包功能码对不上时丢弃（异步帧插队），同样按超时处理 */
    private suspend fun sendFrame(
        client: QzxyBtClient,
        code: Int,
        payload: ByteArray = byteArrayOf(0x00),
    ): QzxyBtProtocol.BtResponse? {
        val raw = client.request(QzxyBtProtocol.encodeFrame(code, payload), QzxyApiConfig.BT_FRAME_TIMEOUT_MS)
            ?: return null
        val response = QzxyBtProtocol.decodeResponse(raw) ?: return null
        if (response.functionCode != code) {
            Log.w("QzxyBt", "回包功能码不符：期望 %02x，收到 %02x，丢弃".format(code, response.functionCode))
            return null
        }
        val verdict = if (response.success) "成功" else "被拒 ${response.errorCode} ${response.errorText}"
        Log.i("QzxyBt", "回包 %02x：${response.payloadHex}（$verdict）".format(code))
        return response
    }

    /** 安静地读一次设备状态：不写流程文案，供轮询与清除校验用 */
    private suspend fun queryDeviceState(client: QzxyBtClient): QzxyBtProtocol.BtDeviceState? {
        val reply = sendFrame(client, QzxyBtProtocol.CODE_QUERY_DEVICE) ?: return null
        if (!reply.success) return null
        return QzxyBtProtocol.parseDeviceState(reply.payload)
    }

    /**
     * 蓝牙款开始使用：连接 → 读设备状态（非空闲时就地解套）→ 服务端下单 → 蓝牙下发 downData → 设备确认。
     *
     * 两段式开阀：服务端只签发加密的开阀数据，真正让设备出水的是客户端通过蓝牙把它写进去；
     * 下单签名里的 randomNumber 来自蓝牙实时读取，这就是纯 HTTP 方案必然失败的原因。
     */
    suspend fun btStartShower(
        info: QzxyDeviceInfo,
        fallbackName: String,
        fallbackWithhold: Double?,
        onStep: suspend (String) -> Unit,
    ): QzxyActiveShower {
        val session = requireSession()
        val boundMac = info.macAddress?.takeIf { it.isNotBlank() } ?: error("设备缺少 MAC 地址")
        try {
            onStep("正在连接热水器蓝牙")
            val client = ensureBtLink(btAddressCandidates(boundMac))

            onStep("正在读取设备状态")
            var state = queryDeviceState(client)
                ?: error("热水器没有回应（可能已被他人使用或不在身边）")

            // 设备不是空闲态时按状态分流：待采集的旧记录就地代为结算，是本校设备最常见的卡点；
            // 结算中（状态 6）要等几秒；其余状态如实报告
            if (state.deviceState != QzxyBtProtocol.STATE_IDLE) {
                when (state.deviceState) {
                    QzxyBtProtocol.STATE_FINISHED_UNCOLLECTED -> {
                        onStep("设备上有未结算的用水记录，正在代为结算")
                        settleUncollected(client, session, state, onStep)
                        onStep("重新读取设备状态")
                        state = queryDeviceState(client)
                            ?: error("结算后读不到设备状态，请靠近热水器重试")
                        if (state.deviceState != QzxyBtProtocol.STATE_IDLE) {
                            error("设备仍不空闲（${QzxyBtProtocol.stateText(state.deviceState)}），请稍后重试")
                        }
                    }
                    QzxyBtProtocol.STATE_SETTLING ->
                        error("上一条用水记录还在写入，等几秒再点开始")
                    QzxyBtProtocol.STATE_IN_ORDER, QzxyBtProtocol.STATE_CARD_CONSUMING ->
                        error(
                            "设备用水中（可能是你之前的会话或他人在用）。" +
                                "若是你自己的会话，等设备自动关停（约几分钟）后再点开始，这里会自动结算旧记录",
                        )
                    else ->
                        error("设备当前不可开（${QzxyBtProtocol.stateText(state.deviceState)}）")
                }
            }

            onStep("正在创建用水订单")
            val mainType = state.mainType ?: 0
            val subType = state.subType ?: 0
            val deviceId = state.deviceId.toString()
            val form = session.authFields() + mapOf(
                "xfModel" to "0",
                "deviceId" to deviceId,
                "macType" to "%02x%02x".format(mainType, subType),
                "protocolType" to state.protocolType.orEmpty(),
                "randomNumber" to state.randomNumber,
                "smallTypeId" to subType.toString(),
                "macAddress" to registeredOrderMac(boundMac, info),
                "bigTypeId" to mainType.toString(),
                "signature" to QzxyBtProtocol.signature(
                    fields = mapOf(
                        "telephone" to session.telephone,
                        "deviceId" to deviceId,
                        "xfModel" to "0",
                        "randomNumber" to state.randomNumber,
                    ),
                    loginCode = session.loginCode,
                ),
            )
            val resp = call { api.btRateOrder(form) }
            val order = resp.data
            val downData = order?.downData?.takeIf { it.isNotBlank() }?.let { QzxyBtProtocol.hexToBytes(it) }
                ?: error("订单未创建：${resp.displayMessage ?: "服务端未返回开阀数据"}")

            onStep("正在发送开阀指令")
            val opened = sendFrame(client, QzxyBtProtocol.CODE_DOWN_RATE, downData)
                ?: error("热水器没有回应开阀指令，请靠近设备重试")
            if (!opened.success) {
                error("热水器拒绝了开阀指令（错误码 ${opened.errorCode} ${opened.errorText}）")
            }

            return QzxyActiveShower(
                mac = boundMac,
                snCode = info.snCode.orEmpty(),
                orderNo = null,
                deviceName = info.displayName.ifBlank { fallbackName },
                // 下单响应金额单位是厘（0.04 元返回 "40"），展示前换算成元
                preDeduct = order.preDeductMoney?.toDoubleOrNull()?.div(1000.0) ?: fallbackWithhold,
                autoCloseSeconds = order.autoDisConTime?.takeIf { it > 0 },
                btAddress = client.address,
                btRandomNumber = state.randomNumber,
                btProtocolType = state.protocolType.orEmpty(),
            )
        } catch (e: Throwable) {
            closeBt()
            throw e
        }
    }

    /**
     * 蓝牙款结束使用：读状态 → 停阀 → 轮询到「待采集」→ 采集 → 上传结算 → 清除设备记录。
     * 连接已断时按上次连上的地址重连一次；设备空闲说明本地会话是残留，直接收摊。
     */
    suspend fun btStopShower(
        active: QzxyActiveShower,
        onStep: suspend (String) -> Unit,
    ): QzxyStopResult {
        val session = requireSession()
        try {
            onStep("正在连接热水器蓝牙")
            val client = ensureBtLink(listOfNotNull(active.btAddress) + btAddressCandidates(active.mac))

            onStep("正在读取设备状态")
            var state = queryDeviceState(client)
                ?: error("读不到设备状态，请靠近热水器重试")
            var pollFailures = 0

            if (state.deviceState == QzxyBtProtocol.STATE_IDLE) {
                // 没有在放水也没有待结算的记录，本地这条「使用中」是残留，一并收掉
                closeBt()
                return QzxyStopResult(note = "设备当前空闲，本次没有可结算的记录")
            }

            // 设备已在「消费完成，数据待采集」时不能再发停阀指令：实测设备回错误码 1
            // （此处含义是「当前状态不接受这条命令」）。跳过停阀直接采集
            if (state.deviceState != QzxyBtProtocol.STATE_FINISHED_UNCOLLECTED) {
                onStep("正在停止出水")
                val stop = sendFrame(client, QzxyBtProtocol.CODE_END_RATE)
                if (stop != null && !stop.success) {
                    // 被拒不当场退出：设备可能已经结算完，0x22 自然不被接受，由轮询结果决定
                    Log.i("QzxyBt", "停阀指令被拒：${stop.errorCode} ${stop.errorText}")
                }

                onStep("等待设备结算")
                val deadline = SystemClock.elapsedRealtime() + QzxyApiConfig.BT_STOP_POLL_BUDGET_MS
                var lastState = state.deviceState
                while (SystemClock.elapsedRealtime() < deadline) {
                    // 先问再等：设备往往在停阀回包到达时就已经结算完
                    val polled = queryDeviceState(client)
                    if (polled == null) {
                        if (++pollFailures >= QzxyApiConfig.BT_STOP_POLL_MAX_FAILURES) break
                    } else {
                        pollFailures = 0
                        state = polled
                        if (polled.deviceState == QzxyBtProtocol.STATE_FINISHED_UNCOLLECTED) break
                        if (polled.deviceState != lastState) {
                            lastState = polled.deviceState
                            onStep("等待设备结算（${QzxyBtProtocol.stateText(polled.deviceState)}）")
                        }
                    }
                    delay(QzxyApiConfig.BT_STOP_POLL_INTERVAL_MS)
                }
            }

            if (state.deviceState != QzxyBtProtocol.STATE_FINISHED_UNCOLLECTED) {
                if (pollFailures >= QzxyApiConfig.BT_STOP_POLL_MAX_FAILURES) {
                    error("读不到设备状态，蓝牙可能已断开；等几秒再点一次「结束使用」")
                }
                val stillRunning = state.deviceState == QzxyBtProtocol.STATE_IN_ORDER ||
                    state.deviceState == QzxyBtProtocol.STATE_CARD_CONSUMING
                error(
                    if (stillRunning) {
                        "停阀指令没被接受，水可能还在流；再点一次「结束使用」"
                    } else {
                        "设备还没结算完（${QzxyBtProtocol.stateText(state.deviceState)}），过几秒再点一次「结束使用」"
                    },
                )
            }

            val result = settleUncollected(client, session, state, onStep)
            closeBt()
            return result
        } catch (e: Throwable) {
            closeBt()
            throw e
        }
    }

    /**
     * 采集 → 上传结算 → 清除设备记录。要求设备已处于「消费完成，数据待采集」。
     * 结算签名用的随机数与协议版本必须取自**当前轮询结果**——开阀前那份已经过期。
     */
    private suspend fun settleUncollected(
        client: QzxyBtClient,
        session: QzxySession,
        state: QzxyBtProtocol.BtDeviceState,
        onStep: suspend (String) -> Unit,
    ): QzxyStopResult {
        onStep("正在读取本次用水数据")
        val reply = sendFrame(client, QzxyBtProtocol.CODE_COLLECT)
            ?: error("未读取到用水数据，请靠近热水器重试")
        if (!reply.success) {
            error("读取用水数据被拒（错误码 ${reply.errorCode} ${reply.errorText}）")
        }
        val record = reply.payload
        val parsed = QzxyBtProtocol.parseConsumption(record)
        Log.i(
            "QzxyBt",
            parsed?.let {
                "消费记录：消费 ${it.consumeMoney} 厘，序号 ${it.timeIdHex}，${record.size} 字节"
            } ?: "消费记录字段解析失败：${reply.payloadHex}",
        )

        onStep("正在结算")
        val xfData = QzxyBtProtocol.bytesToHex(record)
        val resp = call {
            api.btUploadData(
                session.authFields() + mapOf(
                    "xfData" to xfData,
                    "randomNumber" to state.randomNumber,
                    "protocolType" to state.protocolType.orEmpty(),
                    "signature" to QzxyBtProtocol.signature(
                        fields = mapOf(
                            "loginCode" to session.loginCode,
                            "telephone" to session.telephone,
                            "xfData" to xfData,
                        ),
                        loginCode = session.loginCode,
                    ),
                ),
            )
        }
        val data = resp.data
            ?: error("结算未完成：${resp.displayMessage ?: "服务端未返回结算结果"}，请稍后在账单中核对")

        // 设备端记录要清掉，否则下次开阀会被「消费数据未采集」挡住。
        // 清除参数按候选表试（实测记录摘要排第一），试通哪条记下来下次排最前
        onStep("正在清除设备记录")
        val clDataPlain = QzxyBtProtocol.decryptClData(data.clData)
        val cleared = clearDeviceRecord(client, record, clDataPlain, onStep)
        return QzxyStopResult(
            // 结算接口金额单位是厘（0.04 元返回 "40"），展示前换算成元
            consumeMoney = data.consumeMoney?.toDoubleOrNull()?.div(1000.0),
            consumeTime = data.consumeTime,
            note = if (cleared) null else "设备记录未能清除，下次开水前会先自动补结算",
        )
    }

    /** 逐条试清除候选；判定分两层：设备回包成功 + 回读状态离开「待采集」，只回读会把「拒收」和「收下了但没清」混在一起 */
    private suspend fun clearDeviceRecord(
        client: QzxyBtClient,
        record: ByteArray,
        clDataPlain: ByteArray?,
        onStep: suspend (String) -> Unit,
    ): Boolean {
        val candidates = QzxyBtProtocol.clearCandidates(
            record = record,
            clDataPlain = clDataPlain,
            preferredKey = QzxyBtProtocol.preferredClearKey,
        ).take(QzxyApiConfig.BT_CLEAR_TRIAL_LIMIT)
        for ((index, candidate) in candidates.withIndex()) {
            onStep("正在清除设备记录（${index + 1}/${candidates.size}）")
            val reply = sendFrame(client, candidate.functionCode, candidate.payload)
            if (reply == null || !reply.success) {
                Log.i(
                    "QzxyBt",
                    "清除候选 ${candidate.key} 未生效：${reply?.let { "${it.errorCode} ${it.errorText}" } ?: "设备没有回包"}",
                )
                continue
            }
            delay(QzxyApiConfig.BT_CLEAR_VERIFY_DELAY_MS)
            val after = queryDeviceState(client)
            if (after != null && after.deviceState != QzxyBtProtocol.STATE_FINISHED_UNCOLLECTED) {
                QzxyBtProtocol.preferredClearKey = candidate.key
                Log.i("QzxyBt", "清除成功：${candidate.key}")
                return true
            }
            Log.i("QzxyBt", "清除候选 ${candidate.key} 设备收下了但记录还在")
        }
        return false
    }
}
