package com.inonvation.lightlife.data.water

import android.content.Context

/**
 * 喝水提醒业务管理器。
 * 协调 WaterReminderStore 和 CalendarReminderManager，提供统一的业务接口。
 */
class WaterReminderManager(private val context: Context) {
    
    val store = WaterReminderStore(context)
    private val calendarManager = CalendarReminderManager(context)
    
    /**
     * 启用喝水提醒
     * @return 是否成功启用（可能因为权限问题失败）
     */
    fun enable(): Boolean {
        if (!calendarManager.hasCalendarPermission()) {
            return false
        }
        
        store.setEnabled(true)
        val eventId = calendarManager.createReminderEvent(
            intervalMinutes = store.getIntervalMinutes(),
            quietStartHour = store.getQuietStartHour(),
            quietEndHour = store.getQuietEndHour()
        )
        
        return eventId != null
    }
    
    /**
     * 禁用喝水提醒
     */
    fun disable() {
        store.setEnabled(false)
        calendarManager.deleteReminderEvent()
    }
    
    /**
     * 更新提醒间隔
     */
    fun updateInterval(minutes: Int) {
        store.setIntervalMinutes(minutes)
        
        // 如果已启用，更新日历事件
        if (store.isEnabled()) {
            calendarManager.updateReminderEvent(
                intervalMinutes = minutes,
                quietStartHour = store.getQuietStartHour(),
                quietEndHour = store.getQuietEndHour()
            )
        }
    }
    
    /**
     * 更新杯子容量
     */
    fun updateCupSize(ml: Int) {
        store.setCupSizeMl(ml)
    }
    
    /**
     * 更新免打扰时段
     */
    fun updateQuietTime(startHour: Int, endHour: Int) {
        store.setQuietStartHour(startHour)
        store.setQuietEndHour(endHour)
        
        // 如果已启用，更新日历事件
        if (store.isEnabled()) {
            calendarManager.updateReminderEvent(
                intervalMinutes = store.getIntervalMinutes(),
                quietStartHour = startHour,
                quietEndHour = endHour
            )
        }
    }
    
    /**
     * 手动记录喝水
     */
    fun recordDrink(amountMl: Int? = null) {
        val amount = amountMl ?: store.getCupSizeMl()
        store.recordDrink(amount)
    }
    
    /**
     * 获取今日喝水统计
     */
    fun getTodayStats(): TodayStats {
        return TodayStats(
            totalMl = store.getTodayTotalMl(),
            drinkCount = store.getTodayDrinkCount(),
            cupSizeMl = store.getCupSizeMl()
        )
    }
    
    /**
     * 获取提醒配置
     */
    fun getConfig(): ReminderConfig {
        return ReminderConfig(
            enabled = store.isEnabled(),
            intervalMinutes = store.getIntervalMinutes(),
            cupSizeMl = store.getCupSizeMl(),
            quietStartHour = store.getQuietStartHour(),
            quietEndHour = store.getQuietEndHour()
        )
    }
    
    /**
     * 检查是否有日历权限
     */
    fun hasCalendarPermission(): Boolean {
        return calendarManager.hasCalendarPermission()
    }
    
    /**
     * 检查日历事件是否存在
     */
    fun isCalendarEventExists(): Boolean {
        return calendarManager.isEventExists()
    }
    
    /**
     * 重置日历 - 删除所有喝水提醒事件和日历账户
     * @return 是否成功删除
     */
    fun resetCalendar(): Boolean {
        val result = calendarManager.deleteAllEventsAndCalendar()
        if (result) {
            store.setEnabled(false)
        }
        return result
    }
    
    /**
     * 今日喝水统计
     */
    data class TodayStats(
        val totalMl: Int,
        val drinkCount: Int,
        val cupSizeMl: Int
    ) {
        /** 格式化显示总量 */
        fun formatTotal(): String {
            return if (totalMl >= 1000) {
                String.format("%.1fL", totalMl / 1000.0)
            } else {
                "${totalMl}ml"
            }
        }
    }
    
    /**
     * 提醒配置
     */
    data class ReminderConfig(
        val enabled: Boolean,
        val intervalMinutes: Int,
        val cupSizeMl: Int,
        val quietStartHour: Int,
        val quietEndHour: Int
    ) {
        /** 格式化间隔显示 */
        fun formatInterval(): String {
            return if (intervalMinutes >= 60) {
                val hours = intervalMinutes / 60
                val minutes = intervalMinutes % 60
                if (minutes == 0) "${hours}小时" else "${hours}小时${minutes}分钟"
            } else {
                "${intervalMinutes}分钟"
            }
        }
        
        /** 格式化免打扰时段显示 */
        fun formatQuietTime(): String {
            return "${String.format("%02d:00", quietStartHour)} - ${String.format("%02d:00", quietEndHour)}"
        }
    }
}
