package com.inonvation.lightlife.data.qzxy

/**
 * 趣智校园平台接口配置。
 * 接口逆向自 linyu 项目（https://github.com/yehu-imei/linyu，MIT 协议）与 JUWP-schedule 的真机验证，
 * 蓝牙款协议细节见 docs/qzxy-bt-protocol.md。
 */
object QzxyApiConfig {
    const val BASE_URL = "https://v3-api.china-qzxy.cn/"

    /** 跟着官方客户端版本走（JUWP-schedule 2026-09-27 真机验证值）；服务端若按版本卡人，先动这里 */
    const val VERSION = "6.5.28"
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

    // ── 蓝牙直控（communicationTypeId=0 的设备，流程对齐 JUWP-schedule 真机验证版）──

    /** GATT 建链 + 服务发现超时；候选地址依次尝试 */
    const val BT_CONNECT_TIMEOUT_MS = 15_000L

    /** 单帧等待超时（查询设备 / 开阀确认 / 停阀 / 采集 / 清除） */
    const val BT_FRAME_TIMEOUT_MS = 5_000L

    /** 停阀后轮询设备状态：先查再等，间隔 300ms。预算 12 秒——实测状态 6「结算中」要五秒以上才变 3 */
    const val BT_STOP_POLL_INTERVAL_MS = 300L
    const val BT_STOP_POLL_BUDGET_MS = 12_000L

    /** 轮询里连续读不到状态的次数上限：区分「设备还在结算」与「设备不理人了」 */
    const val BT_STOP_POLL_MAX_FAILURES = 4

    /** 清除命令每条候选之后回读状态前的等待 */
    const val BT_CLEAR_VERIFY_DELAY_MS = 300L

    /** 日常流程最多试几条清除候选（调试用不着——本版本不做调试区，全表试意义不大） */
    const val BT_CLEAR_TRIAL_LIMIT = 3
}
