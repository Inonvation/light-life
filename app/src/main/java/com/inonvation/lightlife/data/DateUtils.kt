package com.inonvation.lightlife.data

import java.time.LocalDate

/**
 * 日期工具。统一今日日期字符串格式为 ISO-8601（YYYY-MM-DD）。
 *
 * 注意：[com.inonvation.lightlife.data.water.WaterReminderStore] 历史上使用
 * 无前导零格式（YYYY-M-D），内部自洽，未并入此处以避免老数据对比失效。
 */
object DateUtils {
    /** 今日日期字符串，ISO-8601 格式 YYYY-MM-DD */
    fun today(): String = LocalDate.now().toString()
}
