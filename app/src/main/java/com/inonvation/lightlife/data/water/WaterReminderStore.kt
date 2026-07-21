package com.inonvation.lightlife.data.water

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * 喝水提醒数据存储。
 * 使用 SharedPreferences 存储提醒配置。
 */
class WaterReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("water_reminder", Context.MODE_PRIVATE)
    
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val waterLogListAdapter = moshi.adapter<List<WaterLog>>(
        Types.newParameterizedType(List::class.java, WaterLog::class.java)
    )
    
    private val timeSlotListAdapter = moshi.adapter<List<ReminderTimeSlot>>(
        Types.newParameterizedType(List::class.java, ReminderTimeSlot::class.java)
    )
    
    private val eventMappingAdapter = moshi.adapter<Map<String, Long>>(
        Types.newParameterizedType(Map::class.java, String::class.java, java.lang.Long::class.java)
    )
    
    // ── 提醒模式 ──
    
    /** 获取提醒模式 */
    fun getReminderMode(): ReminderMode {
        return try {
            ReminderMode.valueOf(prefs.getString("reminder_mode", ReminderMode.FIXED_TIME.name)!!)
        } catch (e: Exception) {
            ReminderMode.FIXED_TIME
        }
    }
    
    /** 设置提醒模式 */
    fun setReminderMode(mode: ReminderMode) {
        prefs.edit().putString("reminder_mode", mode.name).apply()
    }
    
    // ── 定时提醒时间点 ──
    
    /** 获取定时提醒时间点列表 */
    fun getTimeSlots(): List<ReminderTimeSlot> {
        val json = prefs.getString("time_slots", null) ?: return defaultTimeSlots()
        return runCatching { timeSlotListAdapter.fromJson(json) }.getOrNull() ?: defaultTimeSlots()
    }
    
    /** 设置定时提醒时间点列表 */
    fun setTimeSlots(slots: List<ReminderTimeSlot>) {
        val json = timeSlotListAdapter.toJson(slots)
        prefs.edit().putString("time_slots", json).apply()
    }
    
    /** 更新单个时间点 */
    fun updateTimeSlot(slotId: String, enabled: Boolean? = null, hour: Int? = null, minute: Int? = null) {
        val slots = getTimeSlots().toMutableList()
        val index = slots.indexOfFirst { it.id == slotId }
        if (index >= 0) {
            slots[index] = slots[index].copy(
                enabled = enabled ?: slots[index].enabled,
                hour = hour ?: slots[index].hour,
                minute = minute ?: slots[index].minute
            )
            setTimeSlots(slots)
        }
    }
    
    /** 添加时间点 */
    fun addTimeSlot(slot: ReminderTimeSlot) {
        val slots = getTimeSlots().toMutableList()
        slots.add(slot)
        setTimeSlots(slots)
    }
    
    /** 删除时间点 */
    fun removeTimeSlot(slotId: String) {
        val slots = getTimeSlots().toMutableList()
        slots.removeAll { it.id == slotId }
        setTimeSlots(slots)
        
        // 同时删除对应的日历事件ID
        removeEventMapping(slotId)
    }
    
    // ── 间隔提醒配置 ──
    
    /** 获取提醒间隔（分钟） */
    fun getIntervalMinutes(): Int = prefs.getInt("interval_minutes", 60)
    
    /** 设置提醒间隔（分钟） */
    fun setIntervalMinutes(minutes: Int) {
        prefs.edit().putInt("interval_minutes", minutes.coerceIn(15, 180)).apply()
    }
    
    // ── 免打扰时段 ──
    
    /** 获取免打扰开始时间（小时） */
    fun getQuietStartHour(): Int = prefs.getInt("quiet_start_hour", 22)
    fun setQuietStartHour(hour: Int) {
        prefs.edit().putInt("quiet_start_hour", hour.coerceIn(0, 23)).apply()
    }
    
    /** 获取免打扰结束时间（小时） */
    fun getQuietEndHour(): Int = prefs.getInt("quiet_end_hour", 7)
    fun setQuietEndHour(hour: Int) {
        prefs.edit().putInt("quiet_end_hour", hour.coerceIn(0, 23)).apply()
    }
    
    /** 检查指定时间是否在免打扰时段内 */
    fun isQuietTime(hour: Int, minute: Int): Boolean {
        val start = getQuietStartHour()
        val end = getQuietEndHour()
        val timeMinutes = hour * 60 + minute
        val startMinutes = start * 60
        val endMinutes = end * 60
        
        return if (start <= end) {
            timeMinutes in startMinutes until endMinutes
        } else {
            timeMinutes >= startMinutes || timeMinutes < endMinutes
        }
    }
    
    // ── 日历事件映射 ──
    
    /** 获取日历事件映射 (时间点ID -> 日历事件ID) */
    fun getEventMappings(): Map<String, Long> {
        val json = prefs.getString("event_mappings", null) ?: return emptyMap()
        return runCatching { eventMappingAdapter.fromJson(json) ?: emptyMap() }.getOrNull() ?: emptyMap()
    }
    
    /** 设置日历事件映射 */
    fun setEventMappings(mappings: Map<String, Long>) {
        val json = eventMappingAdapter.toJson(mappings)
        prefs.edit().putString("event_mappings", json).apply()
    }
    
    /** 添加事件映射 */
    fun addEventMapping(slotId: String, eventId: Long) {
        val mappings = getEventMappings().toMutableMap()
        mappings[slotId] = eventId
        setEventMappings(mappings)
    }
    
    /** 删除事件映射 */
    fun removeEventMapping(slotId: String) {
        val mappings = getEventMappings().toMutableMap()
        mappings.remove(slotId)
        setEventMappings(mappings)
    }
    
    /** 清除所有事件映射 */
    fun clearEventMappings() {
        prefs.edit().remove("event_mappings").apply()
    }
    
    // ── 间隔提醒事件ID ──
    
    /** 获取间隔提醒的事件ID列表 */
    fun getIntervalEventIds(): List<Long> {
        val json = prefs.getString("interval_event_ids", null) ?: return emptyList()
        return runCatching {
            moshi.adapter<List<Long>>(
                Types.newParameterizedType(List::class.java, java.lang.Long::class.java)
            ).fromJson(json)
        }.getOrNull() ?: emptyList()
    }
    
    /** 设置间隔提醒的事件ID列表 */
    fun setIntervalEventIds(ids: List<Long>) {
        val json = moshi.adapter<List<Long>>(
            Types.newParameterizedType(List::class.java, java.lang.Long::class.java)
        ).toJson(ids)
        prefs.edit().putString("interval_event_ids", json).apply()
    }
    
    // ── 喝水目标 ──
    
    /** 获取每日喝水目标（毫升） */
    fun getDailyGoalMl(): Int = prefs.getInt("daily_goal_ml", 2000)
    
    /** 设置每日喝水目标（毫升） */
    fun setDailyGoalMl(ml: Int) {
        prefs.edit().putInt("daily_goal_ml", ml.coerceIn(500, 5000)).apply()
    }
    
    // ── 杯子容量 ──
    
    /** 获取杯子容量（毫升） */
    fun getCupSizeMl(): Int = prefs.getInt("cup_size_ml", 200)
    fun setCupSizeMl(ml: Int) {
        prefs.edit().putInt("cup_size_ml", ml.coerceIn(50, 1000)).apply()
    }
    
    // ── 喝水记录 ──
    
    /** 获取今日喝水总量（毫升） */
    fun getTodayTotalMl(): Int {
        val today = getTodayDateString()
        val savedDate = prefs.getString("last_drink_date", null)
        
        if (savedDate != today) {
            prefs.edit()
                .putString("last_drink_date", today)
                .putInt("today_total_ml", 0)
                .putInt("today_drink_count", 0)
                .apply()
            return 0
        }
        
        return prefs.getInt("today_total_ml", 0)
    }
    
    /** 获取今日喝水次数 */
    fun getTodayDrinkCount(): Int {
        val today = getTodayDateString()
        val savedDate = prefs.getString("last_drink_date", null)
        
        if (savedDate != today) {
            return 0
        }
        
        return prefs.getInt("today_drink_count", 0)
    }
    
    /** 记录一次喝水 */
    fun recordDrink(amountMl: Int) {
        val today = getTodayDateString()
        val savedDate = prefs.getString("last_drink_date", null)
        
        var totalMl = if (savedDate == today) prefs.getInt("today_total_ml", 0) else 0
        var count = if (savedDate == today) prefs.getInt("today_drink_count", 0) else 0
        
        totalMl += amountMl
        count++
        
        prefs.edit()
            .putString("last_drink_date", today)
            .putInt("today_total_ml", totalMl)
            .putInt("today_drink_count", count)
            .apply()
        
        // 保存喝水记录（最近100条）
        saveWaterLog(WaterLog(amountMl = amountMl))
    }
    
    /** 保存喝水记录 */
    private fun saveWaterLog(log: WaterLog) {
        val logs = getRecentLogs().toMutableList()
        logs.add(0, log)
        if (logs.size > 100) {
            logs.removeAt(logs.size - 1)
        }
        
        val json = waterLogListAdapter.toJson(logs)
        prefs.edit().putString("water_logs", json).apply()
    }
    
    /** 获取最近的喝水记录 */
    fun getRecentLogs(): List<WaterLog> {
        val json = prefs.getString("water_logs", null) ?: return emptyList()
        return runCatching { waterLogListAdapter.fromJson(json) }.getOrNull() ?: emptyList()
    }
    
    /** 获取今日日期字符串 */
    private fun getTodayDateString(): String {
        val now = java.time.LocalDate.now()
        return "${now.year}-${now.monthValue}-${now.dayOfMonth}"
    }
    
    /** 重置所有配置 */
    fun reset() {
        prefs.edit().clear().apply()
    }
    
    /** 默认时间点 */
    companion object {
        fun defaultTimeSlots() = listOf(
            ReminderTimeSlot("1", 7, 0, true, "早起喝水"),
            ReminderTimeSlot("2", 9, 0, true, "上午补水"),
            ReminderTimeSlot("3", 11, 30, true, "午前喝水"),
            ReminderTimeSlot("4", 13, 0, false, "午后补水"),
            ReminderTimeSlot("5", 15, 30, true, "下午茶时间"),
            ReminderTimeSlot("6", 18, 0, true, "傍晚补水"),
            ReminderTimeSlot("7", 21, 0, true, "睡前适量"),
        )
    }
}

/** 提醒模式 */
enum class ReminderMode { FIXED_TIME, INTERVAL }

/** 定时提醒时间点 */
data class ReminderTimeSlot(
    val id: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String = ""
) {
    fun format(): String = "%02d:%02d".format(hour, minute)
}

/** 喝水记录 */
data class WaterLog(
    val timestamp: Long = System.currentTimeMillis(),
    val amountMl: Int = 200
)
