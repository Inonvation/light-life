package com.example.devicecontrol.data

import com.squareup.moshi.Json

data class ApiEnvelope<T>(
    val code: Int? = null,
    val msg: String? = null,
    val message: String? = null,
    val data: T? = null,
) {
    fun requireData(): T = data ?: error(message ?: msg ?: "接口未返回 data")
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
    message: String = "Token 已过期",
) : Exception(message) {
    companion object {
        private val EXPIRED_CODES = setOf(10001, 20001, 30001, 40001, 50001)

        fun isTokenExpired(code: Int?, msg: String?): Boolean {
            if (code != null && code in EXPIRED_CODES) return true
            if (msg != null) {
                val lower = msg.lowercase()
                if (lower.contains("token") && (lower.contains("expir") || lower.contains("invalid") || lower.contains("非法"))) return true
            }
            return false
        }
    }
}
