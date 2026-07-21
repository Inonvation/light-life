package com.inonvation.lightlife.data.water

import android.content.Context

/**
 * 喝水提醒业务管理器。
 */
class WaterReminderManager(private val context: Context) {
    
    val store = WaterReminderStore(context)
    private val calendarManager = CalendarReminderManager(context)
    
    // ── 定时提醒 ──
    
    /**
     * 启用/禁用单个时间点
     */
    fun toggleTimeSlot(slotId: String, enabled: Boolean): Boolean {
        if (!calendarManager.hasCalendarPermission()) return false
        
        if (enabled) {
            val slot = store.getTimeSlots().find { it.id == slotId } ?: return false
            val eventId = calendarManager.enableTimeSlot(slot)
            return eventId != null
        } else {
            calendarManager.disableTimeSlot(slotId)
            return true
        }
    }
    
    /**
     * 更新时间点
     */
    fun updateTimeSlot(slotId: String, hour: Int, minute: Int) {
        store.updateTimeSlot(slotId, hour = hour, minute = minute)
        val slot = store.getTimeSlots().find { it.id == slotId }
        if (slot != null && slot.enabled) {
            calendarManager.updateTimeSlot(slot)
        }
    }
    
    /**
     * 添加时间点
     */
    fun addTimeSlot(hour: Int, minute: Int, label: String = ""): ReminderTimeSlot {
        val id = System.currentTimeMillis().toString()
        val slot = ReminderTimeSlot(id, hour, minute, true, label)
        store.addTimeSlot(slot)
        calendarManager.enableTimeSlot(slot)
        return slot
    }
    
    /**
     * 删除时间点
     */
    fun removeTimeSlot(slotId: String) {
        calendarManager.disableTimeSlot(slotId)
        store.removeTimeSlot(slotId)
    }
    
    // ── 间隔提醒 ──
    
    /**
     * 启用间隔提醒
     */
    fun enableIntervalReminder(): Boolean {
        if (!calendarManager.hasCalendarPermission()) return false
        
        val eventIds = calendarManager.createIntervalReminders(
            intervalMinutes = store.getIntervalMinutes(),
            quietStartHour = store.getQuietStartHour(),
            quietEndHour = store.getQuietEndHour()
        )
        
        return eventIds.isNotEmpty()
    }
    
    /**
     * 禁用间隔提醒
     */
    fun disableIntervalReminder() {
        calendarManager.deleteIntervalReminders()
    }
    
    /**
     * 更新间隔设置
     */
    fun updateInterval(minutes: Int) {
        store.setIntervalMinutes(minutes)
    }
    
    /**
     * 更新免打扰时段
     */
    fun updateQuietTime(startHour: Int, endHour: Int) {
        store.setQuietStartHour(startHour)
        store.setQuietEndHour(endHour)
    }
    
    // ── 通用 ──
    
    /**
     * 获取当前提醒模式
     */
    fun getReminderMode(): ReminderMode = store.getReminderMode()
    
    /**
     * 切换提醒模式
     */
    fun switchMode(mode: ReminderMode) {
        // 先禁用当前模式的提醒
        when (store.getReminderMode()) {
            ReminderMode.FIXED_TIME -> {
                // 禁用所有时间点
                store.getTimeSlots().filter { it.enabled }.forEach { slot ->
                    calendarManager.disableTimeSlot(slot.id)
                }
            }
            ReminderMode.INTERVAL -> {
                calendarManager.deleteIntervalReminders()
            }
        }
        
        // 设置新模式
        store.setReminderMode(mode)
    }
    
    /**
     * 清除所有提醒
     */
    fun clearAllReminders(): Boolean {
        val result = calendarManager.deleteAllEventsAndCalendar()
        if (result) {
            // 重置所有时间点为禁用
            val slots = store.getTimeSlots().map { it.copy(enabled = false) }
            store.setTimeSlots(slots)
        }
        return result
    }
    
    /**
     * 检查是否有日历权限
     */
    fun hasCalendarPermission(): Boolean = calendarManager.hasCalendarPermission()
    
    /**
     * 获取所有时间点
     */
    fun getTimeSlots(): List<ReminderTimeSlot> = store.getTimeSlots()
    
    /**
     * 获取间隔分钟数
     */
    fun getIntervalMinutes(): Int = store.getIntervalMinutes()
    
    /**
     * 获取免打扰时段
     */
    fun getQuietTime(): Pair<Int, Int> = Pair(store.getQuietStartHour(), store.getQuietEndHour())
    
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
            cupSizeMl = store.getCupSizeMl(),
            dailyGoalMl = store.getDailyGoalMl()
        )
    }
    
    /**
     * 获取杯子容量
     */
    fun getCupSizeMl(): Int = store.getCupSizeMl()
    
    /**
     * 设置杯子容量
     */
    fun setCupSizeMl(ml: Int) {
        store.setCupSizeMl(ml)
    }
    
    /**
     * 获取每日喝水目标
     */
    fun getDailyGoalMl(): Int = store.getDailyGoalMl()
    
    /**
     * 设置每日喝水目标
     */
    fun setDailyGoalMl(ml: Int) {
        store.setDailyGoalMl(ml)
    }
    
    data class TodayStats(
        val totalMl: Int,
        val drinkCount: Int,
        val cupSizeMl: Int,
        val dailyGoalMl: Int
    ) {
        fun formatTotal(): String {
            return if (totalMl >= 1000) {
                String.format("%.1fL", totalMl / 1000.0)
            } else {
                "${totalMl}ml"
            }
        }
        
        fun formatGoal(): String {
            return if (dailyGoalMl >= 1000) {
                String.format("%.1fL", dailyGoalMl / 1000.0)
            } else {
                "${dailyGoalMl}ml"
            }
        }
        
        fun progressPercent(): Float {
            return if (dailyGoalMl > 0) {
                (totalMl.toFloat() / dailyGoalMl).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }
}
