package com.example.devicecontrol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun balanceData_ticketText_parsesCents() {
        val balance = BalanceData(tokenCoin = "1234", integral = "500", integralAmount = "5.00")
        assertEquals("12.34", balance.ticketText)
        assertEquals("500", balance.pointsText)
    }

    @Test
    fun balanceData_ticketText_nullReturnsDash() {
        val balance = BalanceData()
        assertEquals("-", balance.ticketText)
        assertEquals("-", balance.pointsText)
    }

    @Test
    fun balanceData_ticketText_zeroCents() {
        val balance = BalanceData(tokenCoin = "0")
        assertEquals("0.00", balance.ticketText)
    }

    @Test
    fun balanceData_ticketText_largeValue() {
        val balance = BalanceData(tokenCoin = "9999999")
        assertEquals("99999.99", balance.ticketText)
    }

    @Test
    fun apiEnvelope_requireData_returnsData() {
        val envelope = ApiEnvelope(data = "hello")
        assertEquals("hello", envelope.requireData())
    }

    @Test(expected = RuntimeException::class)
    fun apiEnvelope_requireData_nullData_throws() {
        val envelope = ApiEnvelope<String>(code = 400, msg = "error")
        envelope.requireData()
    }

    @Test(expected = RuntimeException::class)
    fun apiEnvelope_requireData_nullAll_throws() {
        ApiEnvelope<String>().requireData()
    }

    @Test
    fun orderHistoryItem_toUnlockResult_convertsCorrectly() {
        val item = OrderHistoryItem(
            orderNo = "NO001", orderId = "ID001", goodsName = "饮水机",
            originPrice = "10.00", ticketCost = "5.00", integralCost = "2.00",
            otherPromotions = emptyList(), completedAt = 1000L,
        )
        val result = item.toUnlockResult()
        assertEquals("NO001", result.orderNo)
        assertEquals("ID001", result.orderId)
        assertEquals("10.00", result.originPrice)
        assertEquals("5.00", result.ticketCost)
        assertEquals("2.00", result.integralCost)
        assertTrue(result.otherPromotions.isEmpty())
        assertEquals(1000L, result.completedAt)
    }

    @Test
    fun deviceItem_defaults() {
        val item = DeviceItem()
        assertEquals("", item.goodsName)
        assertNull(item.goodsId)
        assertNull(item.id)
    }

    @Test
    fun deviceItem_withValues() {
        val item = DeviceItem(goodsId = "G1", goodsName = "测试设备", id = "D1")
        assertEquals("G1", item.goodsId)
        assertEquals("测试设备", item.goodsName)
        assertEquals("D1", item.id)
    }

    @Test
    fun balanceData_integralAmount_roundTrip() {
        val balance = BalanceData(integralAmount = "12.50")
        assertEquals("12.50", balance.integralAmount)
    }

    @Test
    fun apiEnvelope_withMessage_usesMessageInError() {
        val envelope = ApiEnvelope<String>(message = "something went wrong")
        var thrownMsg = ""
        try {
            envelope.requireData()
        } catch (e: RuntimeException) {
            thrownMsg = e.message ?: ""
        }
        assertEquals("something went wrong", thrownMsg)
    }

    @Test
    fun promotionItem_defaults() {
        val p = PromotionItem()
        assertNull(p.promotionType)
        assertNull(p.discountAmount)
    }
}
