package com.example.devicecontrol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceErrorDiagnosisTest {

    @Test
    fun diagnose_integralRisk_returnsCertificationAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(33001, "积分风控拦截", "积分风控检查")
        assertEquals("积分使用权限未开通", result.primaryReason)
        assertEquals("积分风控检查", result.step)
        assertTrue(result.suggestions.any { it.contains("使用积分") })
        assertTrue(result.suggestions.any { it.contains("实名认证") })
    }

    @Test
    fun diagnose_authRequired_returnsAuthAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(null, "需要认证", "启动解锁")
        assertEquals("需要完成实名认证", result.primaryReason)
        assertEquals("启动解锁", result.step)
        assertTrue(result.suggestions.any { it.contains("实名认证") })
    }

    @Test
    fun diagnose_locationRisk_returnsLocationAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(33003, "位置风控拦截", "位置风控检查")
        assertEquals("位置风控拦截", result.primaryReason)
        assertTrue(result.suggestions.any { it.contains("GPS") })
    }

    @Test
    fun diagnose_deviceOffline_returnsDeviceAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(null, "设备离线不可用", "设备预检")
        assertEquals("设备可能离线或断电", result.primaryReason)
        assertTrue(result.suggestions.any { it.contains("电源") })
    }

    @Test
    fun diagnose_deviceBusy_returnsBusyMessage() {
        val result = DeviceErrorDiagnosis.diagnose(null, "设备使用中", "设备预检")
        assertEquals("设备正在被其他用户使用", result.primaryReason)
    }

    @Test
    fun diagnose_quotaExhausted_returnsQuotaAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(null, "今日次数已用完", "启动解锁")
        assertEquals("今日可用次数已用完", result.primaryReason)
        assertTrue(result.suggestions.any { it.contains("明天") })
    }

    @Test
    fun diagnose_networkTimeout_returnsNetworkAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(null, "connection timeout", "设备状态轮询")
        assertEquals("网络连接异常", result.primaryReason)
        assertTrue(result.suggestions.any { it.contains("网络") })
    }

    @Test
    fun diagnose_serverError_returnsServerAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(null, "HTTP 500 error", "启动解锁")
        assertEquals("服务端异常", result.primaryReason)
        assertTrue(result.suggestions.any { it.contains("服务器") })
    }

    @Test
    fun diagnose_tokenExpired_returnsLoginAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(401, "token invalid", "获取 SKU")
        assertEquals("登录凭证已过期", result.primaryReason)
        assertTrue(result.suggestions.any { it.contains("重新登录") })
    }

    @Test
    fun diagnose_pointsInsufficient_returnsPointsAdvice() {
        val result = DeviceErrorDiagnosis.diagnose(null, "积分不足", "启动解锁")
        assertEquals("积分余额不足", result.primaryReason)
        assertTrue(result.suggestions.any { it.contains("积分任务") })
    }

    @Test
    fun diagnose_unknownError_returnsFallbackWithCode() {
        val result = DeviceErrorDiagnosis.diagnose(9999, "some unknown error", "某个步骤")
        assertTrue(result.primaryReason.contains("9999"))
        assertEquals("某个步骤", result.step)
        assertTrue(result.rawError.contains("9999"))
        assertTrue(result.rawError.contains("some unknown error"))
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun diagnose_nullCodeAndMessage_returnsMinimalFallback() {
        val result = DeviceErrorDiagnosis.diagnose(null, null, "测试步骤")
        assertEquals("未知错误（code: -1）", result.primaryReason)
        assertEquals("无详细错误信息", result.rawError)
    }

    @Test
    fun diagnose_emptyMessage_returnsMinimalFallback() {
        val result = DeviceErrorDiagnosis.diagnose(null, "", "测试步骤")
        assertEquals("未知错误（code: -1）", result.primaryReason)
    }

    @Test
    fun diagnose_rawError_containsAllParts() {
        val result = DeviceErrorDiagnosis.diagnose(404, "not found", "查询订单详情")
        assertTrue(result.rawError.contains("错误码: 404"))
        assertTrue(result.rawError.contains("错误信息: not found"))
    }

    @Test
    fun diagnose_rawError_onlyCode_noMessage() {
        val result = DeviceErrorDiagnosis.diagnose(500, null, "启动解锁")
        assertTrue(result.rawError.contains("错误码: 500"))
        assertTrue(!result.rawError.contains("错误信息"))
    }

    @Test
    fun diagnose_rawError_onlyMessage_noCode() {
        val result = DeviceErrorDiagnosis.diagnose(null, "something broke", "开通后付")
        assertTrue(result.rawError.contains("错误信息: something broke"))
        assertTrue(!result.rawError.contains("错误码"))
    }
}
