package com.inonvation.lightlife.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 订单金额计算工具。
 * 金额一律用 BigDecimal 精确运算，避免 Double 浮点误差，
 * 例如 0.12 - 0.11 用 Double 会得到 0.009999999999999995，
 * 再经 %.2f 舍入可能显示成 0.00。
 */
fun calculateActualCost(result: UnlockResult): String {
    val origin = result.originPrice.toBigDecimalOrNull()
    if (origin == null) return result.originPrice
    val integral = result.integralCost.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val ticket = result.ticketCost.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val other = result.otherPromotions
        .mapNotNull { it.discountAmount?.toBigDecimalOrNull() }
        .fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
    val cost = origin.subtract(integral).subtract(ticket).subtract(other)
        .coerceAtLeast(BigDecimal.ZERO)
    return cost.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
