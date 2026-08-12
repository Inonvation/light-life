package com.inonvation.lightlife.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyUtilsTest {

    private fun result(
        originPrice: String = "0.12",
        ticketCost: String = "-",
        integralCost: String = "0.11",
        otherPromotions: List<PromotionItem> = emptyList(),
    ) = UnlockResult(
        orderNo = "NO001",
        orderId = "ID001",
        originPrice = originPrice,
        ticketCost = ticketCost,
        integralCost = integralCost,
        otherPromotions = otherPromotions,
        completedAt = 0L,
    )

    @Test
    fun actualCost_originMinusIntegral_avoidsFloatError() {
        // 回归用例：0.12 - 0.11 用 Double 会得到 0.009999999999999995，必须精确为 0.01
        assertEquals("0.01", calculateActualCost(result()))
    }

    @Test
    fun actualCost_fullyDiscounted_returnsZero() {
        assertEquals("0.00", calculateActualCost(result(integralCost = "0.12")))
    }

    @Test
    fun actualCost_overDiscounted_clampedToZero() {
        assertEquals("0.00", calculateActualCost(result(originPrice = "0.05", integralCost = "0.11")))
    }

    @Test
    fun actualCost_withTicketAndOtherPromotions() {
        val r = result(
            ticketCost = "0.01",
            integralCost = "0.10",
            otherPromotions = listOf(PromotionItem(promotionType = -6, discountAmount = "0.001")),
        )
        // 0.12 - 0.10 - 0.01 - 0.001 = 0.009，四舍五入到 0.01
        assertEquals("0.01", calculateActualCost(r))
    }

    @Test
    fun actualCost_unparsableOrigin_returnsRawString() {
        assertEquals("-", calculateActualCost(result(originPrice = "-")))
    }

    @Test
    fun actualCost_missingDiscounts_treatedAsZero() {
        assertEquals("0.12", calculateActualCost(result(ticketCost = "-", integralCost = "-")))
    }

    @Test
    fun actualCost_integerPrice_noLeadingZero() {
        assertEquals("1.00", calculateActualCost(result(originPrice = "1", integralCost = "0")))
    }
}
