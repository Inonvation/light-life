package com.inonvation.lightlife.data

import com.squareup.moshi.Json

data class ApiEnvelope<T>(
    val code: Int? = null,
    val msg: String? = null,
    val message: String? = null,
    val data: T? = null,
) {
    fun requireData(): T {
        if (data != null) return data
        val rawMsg = message ?: msg ?: "接口未返回 data"
        if (TokenExpiredException.isTokenExpired(code, rawMsg)) {
            throw TokenExpiredException(rawMsg)
        }
        error(rawMsg)
    }
}

data class EmptyData(
    val ignored: String? = null,
)

data class LoginData(
    val token: String? = null,
)

data class DeviceItem(
    val goodsId: String? = null,
    val goodsName: String = "",
    val id: String? = null,
)

data class BalanceData(
    val tokenCoin: String? = null,
    @Json(name = "integral") val integral: String? = null,
    val integralAmount: String? = null,
) {
    val ticketText: String
        get() = tokenCoin?.toDoubleOrNull()?.let { "%.2f".format(it / 100.0) } ?: "-"

    val pointsText: String get() = integral ?: "-"
}

data class SkuData(val skuId: String? = null)

data class ImeiData(
    val imei: String? = null,
)

data class UnlockData(
    val msgId: String? = null,
    val orderNo: String? = null,
)

data class SyncData(
    val workStatus: Int? = null,
    val identify: String? = null,
)

data class AfterPayCreatingData(
    val orderId: String? = null,
)

data class OrderDetailData(
    val tradeOrderItem: List<TradeOrderItem> = emptyList(),
    val promotionList: List<PromotionItem> = emptyList(),
)

data class TradeOrderItem(
    val originPrice: String? = null,
)

data class PromotionItem(
    val promotionType: Int? = null,
    val discountAmount: String? = null,
)

data class UnlockResult(
    val orderNo: String,
    val orderId: String,
    val originPrice: String,
    val ticketCost: String,
    val integralCost: String,
    val otherPromotions: List<PromotionItem>,
    val completedAt: Long,
)



data class OrderHistoryItem(
    val orderNo: String,
    val orderId: String,
    val goodsName: String,
    val originPrice: String,
    val ticketCost: String,
    val integralCost: String,
    val otherPromotions: List<PromotionItem>,
    val completedAt: Long,
) {
    fun toUnlockResult(): UnlockResult = UnlockResult(
        orderNo = orderNo,
        orderId = orderId,
        originPrice = originPrice,
        ticketCost = ticketCost,
        integralCost = integralCost,
        otherPromotions = otherPromotions,
        completedAt = completedAt,
    )
}

class UnlockException(
    message: String,
    val diagnosis: DiagnosisResult,
    cause: Throwable? = null,
) : Exception(message, cause)

class TokenExpiredException(
    message: String = "登录已失效，请重新登录",
    cause: Throwable? = null,
) : Exception(message, cause) {
    companion object {
        fun isTokenExpired(code: Int?, message: String?): Boolean {
            if (code == 401 || code == 403) return true
            val msg = message?.lowercase() ?: return false
            if (msg.contains("未登录") || msg.contains("未登陆")) return true
            if (msg.contains("请先登录") || msg.contains("请先登陆")) return true
            if (msg.contains("token") && (msg.contains("过期") || msg.contains("无效")
                        || msg.contains("expired") || msg.contains("invalid"))) return true
            return false
        }
    }
}
