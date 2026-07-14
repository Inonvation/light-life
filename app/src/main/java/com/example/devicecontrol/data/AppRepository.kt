package com.example.devicecontrol.data

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

class AppRepository(
    private val tokenStore: TokenStore,
    private val orderHistoryStore: OrderHistoryStore,
) {
    private val api: DeviceApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HeaderInterceptor { tokenStore.readToken() })
            .addInterceptor(logging)
            .build()
        val moshi = Moshi.Builder()
            .add(EmptyDataJsonAdapter())
            .add(LenientStringJsonAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()

        api = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeviceApi::class.java)
    }

    fun localToken(): String? = tokenStore.readToken()
    fun saveToken(token: String) = tokenStore.saveToken(token)
    fun clearToken() = tokenStore.clear()

    fun orderHistory(): List<OrderHistoryItem> = orderHistoryStore.list()

    suspend fun sendCode(phone: String) {
        api.sendCode(phone = phone).throwIfFailed()
    }

    suspend fun login(phone: String, code: String): String {
        val token = api.login(phone = phone, verify = code).requireData().token
            ?: error("登录成功但未返回 token")
        tokenStore.saveToken(token)
        return token
    }

    suspend fun queryBalance(): BalanceData {
        val token = requireToken()
        return api.queryBalance(token).requireData()
    }

    suspend fun latestDevices(): List<DeviceItem> {
        val token = requireToken()
        return api.getLatestUsed(token = token).requireData()
    }

    suspend fun unlockDevice(
        device: DeviceItem,
        onStep: suspend (String) -> Unit,
    ): UnlockResult {
        val token = requireToken()
        val goodsId = device.goodsId ?: device.id ?: error("设备缺少 goodsId")

        onStep("正在获取 SKU")
        val skuId = api.goodsid2sku(goodsId = goodsId, token = token).requireData().firstOrNull()?.skuId
            ?: error("未获取到 skuId")


        onStep("正在检测设备状态")
        runCatching {
            api.syncWater(skuId = skuId, token = token)
        }.getOrElse { _ ->
            // 预检失败不阻断流程
        }

        onStep("正在获取 IMEI")
        val imei = api.getImei(goodsId = goodsId, token = token).requireData().imei
            ?: error("未获取到 imei")

        onStep("正在检查积分")
        api.useIntergral(token).throwIfFailed()

        onStep("正在开通后付")
        api.addUserAfterPayChannel(token = token).throwIfFailed()

        onStep("正在检查位置风控")
        api.isCheckLocation(imei = imei, token = token).throwIfFailed()

        onStep("正在启动解锁")
        val unlock = api.unlockWater(
            skuId = skuId,
            promotions = PROMOTIONS,
            token = token,
        ).requireData()

        val orderNo = unlock.orderNo ?: error("未获取到订单号")
        onStep("设备已启动，等待使用结束")

        delay(2000)

        var lastStatus: SyncData
        var pollAttempts = 0
        var everWorked = false
        val maxPollAttempts = 300
        do {
            lastStatus = runCatching {
                api.syncWater(skuId = skuId, token = token).requireData()
            }.getOrElse { e -> throwDiagnosed(e, "设备状态轮询") }
            if (lastStatus.workStatus == 2) everWorked = true
            pollAttempts++
            if (pollAttempts >= maxPollAttempts)
                throwDiagnosed(Exception("设备使用超时（${maxPollAttempts}秒）"), "设备状态轮询")
            onStep("设备工作中，正在等待完成")
            delay(1000)
        } while (lastStatus.workStatus == 2)

        if (!everWorked) {
            throwDiagnosed(Exception("设备未启动或未响应，可能已离线"), "设备状态轮询")
        }

        val finalOrderNo = lastStatus.identify ?: orderNo
        onStep("正在创建后付订单")
        val orderId = api.createAfterPay(orderNo = finalOrderNo, token = token).requireData().orderId
            ?: error("未获取到 orderId")

        onStep("正在查询订单详情")
        val detail = api.orderDetail(orderId = orderId, token = token).requireData()
        val ticketCost = detail.promotionList.firstOrNull { it.promotionType == 4 }?.discountAmount ?: "-"
        val integralCost = detail.promotionList.firstOrNull { it.promotionType == 8 }?.discountAmount ?: "-"
        val otherPromotions = detail.promotionList
            .filter { it.promotionType != 4 && it.promotionType != 8 }
            .map {
                PromotionItem(
                    promotionType = it.promotionType,
                    discountAmount = it.discountAmount,
                )
            }

        val result = UnlockResult(
            orderNo = finalOrderNo,
            orderId = orderId,
            originPrice = detail.tradeOrderItem.firstOrNull()?.originPrice ?: "-",
            ticketCost = ticketCost,
            integralCost = integralCost,
            otherPromotions = otherPromotions,
            completedAt = System.currentTimeMillis(),
        )
        orderHistoryStore.add(
            OrderHistoryItem(
                orderNo = result.orderNo,
                orderId = result.orderId,
                goodsName = device.goodsName.ifBlank { "未命名设备" },
                originPrice = result.originPrice,
                ticketCost = result.ticketCost,
                integralCost = result.integralCost,
                otherPromotions = result.otherPromotions,
                completedAt = result.completedAt,
            ),
        )
        return result
    }


    private fun throwDiagnosed(original: Throwable, step: String): Nothing {
        val code = if (original.message?.matches(Regex("HTTP \\d+.*")) == true) {
            original.message?.substringAfter("HTTP ")?.substringBefore(":")?.trim()?.toIntOrNull()
        } else null
        val diagnosis = DeviceErrorDiagnosis.diagnose(code, original.message, step)
        throw UnlockException(diagnosis.primaryReason, diagnosis, original)
    }

    private fun requireToken(): String = tokenStore.readToken()?.takeIf { it.isNotBlank() }
        ?: error("请先登录")

    private fun ApiEnvelope<*>.throwIfFailed() {
        if (code != null && code != 0 && code != 200) {
            error(message ?: msg ?: "请求失败")
        }
    }

    private companion object {
        const val PROMOTIONS =
            """[{"assetId":"0","oldPromotionId":"","orgId":"0","promotionId":"0","promotionType":"-6"},{"assetId":"0","oldPromotionId":"","orgId":"0","promotionId":"0","promotionType":"-7"},{"assetId":"0","oldPromotionId":"0","orgId":"0","promotionId":"0","promotionType":"8"}]"""
    }
}
