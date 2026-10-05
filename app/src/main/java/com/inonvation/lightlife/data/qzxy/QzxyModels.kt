package com.inonvation.lightlife.data.qzxy

// ── 统一响应包 ──
// 趣智所有接口返回 {success, errorCode, errorMessage, msg, data}，
// 与胖乖生活平台的 {code, msg, data} 不同，故独立建包不共用 ApiEnvelope。

data class QzxyEnvelope<T>(
    val success: Boolean? = null,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
    val msg: String? = null,
    val data: T? = null,
) {
    val displayMessage: String? get() = errorMessage ?: msg

    /**
     * 会话失效判定：趣智不支持多设备同时在线，被挤号或过期时
     * 服务端返回的文案含"失效/过期/请重新登录"等关键词。
     */
    fun isSessionExpired(): Boolean {
        val message = displayMessage ?: return false
        return SESSION_EXPIRED_KEYWORDS.any { message.contains(it, ignoreCase = true) }
    }

    fun throwIfFailed(): QzxyEnvelope<T> {
        if (isSessionExpired() || errorCode == 401 || errorCode == 403) {
            throw QzxySessionExpiredException(displayMessage ?: "趣智校园登录已失效，请重新登录")
        }
        val failed = (errorCode != null && errorCode != 0) || success == false
        if (failed) throw QzxyApiException(displayMessage ?: "请求失败", errorCode)
        return this
    }

    private companion object {
        val SESSION_EXPIRED_KEYWORDS = listOf("失效", "过期", "未登录", "未登陆", "请重新登录", "token")
    }
}

class QzxySessionExpiredException(
    message: String = "趣智校园登录已失效，请重新登录",
) : Exception(message)

class QzxyApiException(
    message: String,
    val errorCode: Int? = null,
) : Exception(message)

// ── 登录 ──

data class QzxyLoginData(
    val userId: Long? = null,
    val loginCode: String? = null,
    val userAccount: QzxyUserAccount? = null,
)

data class QzxyUserAccount(
    val accountId: Long? = null,
    val name: String? = null,
    val projectId: Long? = null,
    val accountRealMoney: Double? = null,
)

/**
 * 趣智会话凭证。认证参数不走 Header，而是随 GET 的 Query 或 POST 的 Form 传递。
 * 注意历史遗留设计：POST 请求需同时传 telephone 和 telPhone（值相同）。
 */
data class QzxySession(
    val loginCode: String,
    val userId: String,
    val accountId: String,
    val projectId: String,
    val telephone: String,
    val userName: String,
) {
    fun queryFields(): Map<String, String> = buildMap {
        put("loginCode", loginCode)
        put("userId", userId)
        put("accountId", accountId)
        put("projectId", projectId)
        put("telephone", telephone)
        put("phoneSystem", QzxyApiConfig.PHONE_SYSTEM)
        put("version", QzxyApiConfig.VERSION)
    }

    fun authFields(): Map<String, String> = queryFields() + mapOf("telPhone" to telephone)
}

// ── 钱包 ──

data class QzxyWalletData(
    val accountRealMoney: Double? = null,
    val accountGivenMoney: Double? = null,
    val money: String? = null,
)

// ── 设备 ──

data class QzxyDeviceInfo(
    val deviceId: Long? = null,
    val deviceName: String? = null,
    val snCode: String? = null,
    val macAddress: String? = null,
    val withholdMoney: Double? = null,
    val onlineStatusId: Int? = null,
    /** 设备通信类型，官方抓包中蓝牙款为 0（开阀需手机直连蓝牙，服务器无法远程下发） */
    val communicationTypeId: Int? = null,
) {
    /** 官方名称形如"热水器-学生公寓-1号楼-3层-301"，去掉前缀和连接符便于阅读 */
    val displayName: String
        get() = (deviceName ?: "")
            .replace(Regex("^热水[器表]-"), "")
            .replace(Regex("^洗手台\\d*-"), "")
            .replace(Regex("-\\d+层-"), "-")
            .replace("-", " ")
            .trim()
            .ifBlank { "未命名设备" }

    /** 1 = 在线，0 = 离线；开阀命令由服务器下发到设备，离线时无法控制 */
    val onlineText: String?
        get() = when (onlineStatusId) {
            1 -> "在线"
            0 -> "离线"
            null -> null
            else -> "状态未知"
        }

    /** 0 = 蓝牙款（本版本不支持远程开阀），其他值视为联网款 */
    val communicationText: String?
        get() = when (communicationTypeId) {
            0 -> "蓝牙款"
            null -> null
            else -> null
        }
}

data class QzxyNearbyDevice(
    val mac: String,
    /** BLE 广播的硬件名（KLCXKJ-Water-xxxx），查询到详情后用 displayName */
    val name: String,
    val rssi: Int,
    /** 按 MAC 查询到的服务器端设备详情，扫描后自动补充 */
    val info: QzxyDeviceInfo? = null,
    /** 正在查询设备名 */
    val nameLoading: Boolean = false,
) {
    val displayName: String get() = info?.displayName ?: name

    val signalText: String
        get() = when {
            rssi >= -70 -> "信号强"
            rssi >= -85 -> "信号中"
            else -> "信号弱"
        }
}

/** 当前绑定的设备：查询到设备详情即绑定并持久化，作为主页淋浴区的主操作对象 */
data class QzxyBoundDevice(
    val mac: String,
    val snCode: String,
    val name: String,
)

// ── 订单 ──

/** queryUsing 响应：orderNo 为 null 表示无进行中订单 */
data class QzxyOrderStatus(
    val orderNo: String? = null,
    val state: Int? = null,
    val snCode: String? = null,
    val isOwner: Boolean? = null,
)

/** 开阀结果确认响应，autoDisConTime 为闲置自动关停秒数（如 600 = 10 分钟） */
data class QzxyDownRateResult(
    val orderNo: String? = null,
    val autoDisConTime: Int? = null,
    val state: Int? = null,
    val result: Int? = null,
    val preDeductMoney: Double? = null,
    val snCode: String? = null,
)

/** 关阀结果确认响应：消费金额可能以数字或字符串形式返回，字段名因学校而异 */
data class QzxyCloseOrderResult(
    val orderNo: String? = null,
    val state: Int? = null,
    val status: Int? = null,
    val result: Int? = null,
    val consumeMoney: Double? = null,
    val consumeMoneyStr: String? = null,
    val consumeTime: String? = null,
    val deviceSnCode: String? = null,
)

data class QzxyConsumeResult(
    val orderNo: String? = null,
    val consumeMoney: Double? = null,
    val consumeMoneyStr: String? = null,
    val consumeTime: String? = null,
)

/** 一次进行中的洗澡订单，控制开始/停止的关键凭据 */
data class QzxyActiveShower(
    val mac: String,
    val snCode: String,
    /** 蓝牙款下单响应不带回订单号，订单号要等结算上传后才有，故可空 */
    val orderNo: String? = null,
    val deviceName: String,
    val preDeduct: Double? = null,
    val autoCloseSeconds: Int? = null,
    /** true = 设备上已有自己的进行中订单，本次是恢复而非新开 */
    val resumed: Boolean = false,
    // ── 蓝牙直控专用 ──
    /** GATT 实际连上的地址，结束使用时优先用它重连（广播地址与登记地址首字节可能不同） */
    val btAddress: String? = null,
    val btRandomNumber: String? = null,
    val btProtocolType: String? = null,
) {
    /** 蓝牙款会话：结束走"停阀 → 采集数据 + 上传结算"而非 closeOrder */
    val isBtSession: Boolean get() = btRandomNumber != null
}

/** 蓝牙款下单响应：downData 是要经手机蓝牙发给设备的开阀指令 */
data class QzxyBtRateOrderData(
    val accountId: Long? = null,
    val realMoney: String? = null,
    val givenMoney: String? = null,
    val preDeductMoney: String? = null,
    val useCount: Int? = null,
    val downData: String? = null,
    val rate: Double? = null,
    val minTime: Int? = null,
    val minMoney: Int? = null,
    val chargeMethod: Int? = null,
    val minChargeUnit: Int? = null,
    val autoDisConTime: Int? = null,
    val consumeDate: String? = null,
    val liquidOrderNo: String? = null,
    val orderNo: String? = null,
    val preDeductMoneySend: String? = null,
)

/** 蓝牙款结算上传响应：订单号与本次消费从这里来；金额单位为厘（0.04 元返回 "40"），展示前除以 1000 */
data class QzxyBtUploadData(
    val consumeTime: String? = null,
    val preDeductMoney: String? = null,
    val preDeductMoneyAfter: String? = null,
    val consumeMoney: String? = null,
    val orderAccountId: Long? = null,
    val clData: String? = null,
    val createTime: String? = null,
    val orderNo: String? = null,
    val deviceSnCode: String? = null,
)

/** 键盘使用码：无网设备（蓝牙款）在热水器键盘上输入此码即可开水 */
data class QzxyUseCodeData(
    val useCode: String? = null,
    val useCodeStatus: Int? = null,
    val useCodeRandom: String? = null,
    val useCodeStartTime: String? = null,
    val useCodeAutoCloseConfigDescription: String? = null,
)

data class QzxyStopResult(
    val consumeMoney: Double? = null,
    val consumeTime: String? = null,
    /** 结算成功但设备侧有遗留时的补充说明（如记录未清除），由控制器以提示呈现 */
    val note: String? = null,
)

/** 结算卡片展示数据（由控制器组装：设备名 + 时长 + 金额文案） */
data class QzxySettleResult(
    val deviceName: String,
    val elapsedSeconds: Int,
    val consumeMoneyText: String,
    val consumeTime: String? = null,
)
