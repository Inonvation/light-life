package com.inonvation.lightlife.data.qzxy

/**
 * 趣智校园平台接口配置。
 * 接口逆向自 linyu 项目（https://github.com/yehu-imei/linyu，MIT 协议），
 * 其默认值仅在金华职业技术大学（projectId=905）验证过，换学校可能需要调整。
 */
object QzxyApiConfig {
    const val BASE_URL = "https://v3-api.china-qzxy.cn/"
    const val VERSION = "6.5.24"
    const val PHONE_SYSTEM = "android"

    /** 热水器 BLE 广播名过滤关键词（不区分大小写），不同学校厂商可能不同 */
    const val BLE_NAME_FILTER = "KLCXKJ-Water"

    /** 单次 BLE 扫描时长 */
    const val SCAN_DURATION_MS = 8_000L

    /** 开阀后轮询订单号：接口不直接返回 orderNo，需轮询 queryUsing 获取 */
    const val ORDER_POLL_INTERVAL_MS = 800L
    const val ORDER_POLL_MAX_ATTEMPTS = 10

    /** 开阀确认轮询（linyu 验证过的节奏：最多 8 次，间隔 700ms） */
    const val OPEN_CONFIRM_INTERVAL_MS = 700L
    const val OPEN_CONFIRM_MAX_ATTEMPTS = 8

    /** 扫描补名：超时兜底（超时后设备先以广播名进列表，名字查到再原地更新） */
    const val ENRICH_REVEAL_TIMEOUT_MS = 3_000L

    /** 关阀结果确认轮询 */
    const val CLOSE_CONFIRM_INTERVAL_MS = 1_500L
    const val CLOSE_CONFIRM_MAX_ATTEMPTS = 5
}
