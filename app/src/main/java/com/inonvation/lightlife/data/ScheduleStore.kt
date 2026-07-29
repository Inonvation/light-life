package com.inonvation.lightlife.data

import android.content.Context
import com.squareup.moshi.Types

/**
 * 定时任务配置存储。
 * 使用 SharedPreferences 存储时间段列表和执行历史。
 */
class ScheduleStore(context: Context) {
    private val prefs = context.getSharedPreferences("schedule_config", Context.MODE_PRIVATE)
    
    private val moshi = MoshiProvider.instance
    private val timeSlotListAdapter = moshi.adapter<List<TimeSlot>>(
        Types.newParameterizedType(List::class.java, TimeSlot::class.java)
    )
    
    /** 时间段数据类 */
    data class TimeSlot(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int
    ) {
        /** 格式化显示 */
        fun format(): String {
            return "%02d:%02d - %02d:%02d".format(startHour, startMinute, endHour, endMinute)
        }
        
        /** 转换为分钟数 */
        fun toStartMinutes(): Int = startHour * 60 + startMinute
        fun toEndMinutes(): Int = endHour * 60 + endMinute
    }
    
    /** 定时功能是否启用 */
    fun isEnabled(): Boolean = prefs.getBoolean("schedule_enabled", false)
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("schedule_enabled", enabled).apply()
    }
    
    /** 获取时间段列表 */
    fun getTimeSlots(): List<TimeSlot> {
        val json = prefs.getString("time_slots", null) ?: return emptyList()
        return runCatching { timeSlotListAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }
    
    /** 设置时间段列表 */
    fun setTimeSlots(slots: List<TimeSlot>) {
        val json = timeSlotListAdapter.toJson(slots)
        prefs.edit().putString("time_slots", json).apply()
    }
    
    /** 添加时间段 */
    fun addTimeSlot(slot: TimeSlot) {
        val current = getTimeSlots().toMutableList()
        if (!current.contains(slot)) {
            current.add(slot)
            setTimeSlots(current)
        }
    }
    
    /** 删除时间段 */
    fun removeTimeSlot(slot: TimeSlot) {
        val current = getTimeSlots().toMutableList()
        current.remove(slot)
        setTimeSlots(current)
    }
    
    /** 获取上次执行日期 */
    fun getLastExecutedDate(): String? {
        return prefs.getString("last_executed_date", null)
    }
    
    /** 设置上次执行日期 */
    fun setLastExecutedDate(date: String) {
        prefs.edit().putString("last_executed_date", date).apply()
    }
    
    /** 获取下次计划执行时间（分钟数，从午夜开始） */
    fun getNextScheduledTime(): Int {
        return prefs.getInt("next_scheduled_time", -1)
    }
    
    /** 设置下次计划执行时间 */
    fun setNextScheduledTime(minutes: Int) {
        prefs.edit().putInt("next_scheduled_time", minutes).apply()
    }
    
    /** 重置所有配置 */
    fun reset() {
        prefs.edit().clear().apply()
    }
}