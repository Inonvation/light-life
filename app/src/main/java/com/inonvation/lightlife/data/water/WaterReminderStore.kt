package com.inonvation.lightlife.data.water

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalTime

/**
 * 喝水提醒配置存储。
 * 使用 SharedPreferences 存储提醒配置和喝水记录。
 */
class WaterReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("water_reminder", Context.MODE_PRIVATE)
    
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val waterLogAdapter = moshi.adapter(WaterLog::class.java)
    
    /** 喝水记录 */
    data class WaterLog(
        val timestamp: Long = System.currentTimeMillis(),
        val amountMl: Int = 200
    )
    
    /** 提醒功能是否启用 */
    fun isEnabled(): Boolean = prefs.getBoolean("reminder_enabled", false)
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("reminder_enabled", enabled).apply()
    }
    
    /** 获取提醒间隔（分钟） */
    fun getIntervalMinutes(): Int = prefs.getInt("interval_minutes", 60)
    fun setIntervalMinutes(minutes: Int) {
        prefs.edit().putInt("interval_minutes", minutes.coerceIn(1, 180)).apply()
    }
    
    /** 获取杯子容量（毫升） */
    fun getCupSizeMl(): Int = prefs.getInt("cup_size_ml", 200)
    fun setCupSizeMl(ml: Int) {
        prefs.edit().putInt("cup_size_ml", ml.coerceIn(50, 1000)).apply()
    }
    
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
    fun isQuietTime(time: LocalTime): Boolean {
        val start = getQuietStartHour()
        val end = getQuietEndHour()
        val hour = time.hour
        
        return if (start <= end) {
            // 同一天内，如 1:00 - 7:00
            hour in start until end
        } else {
            // 跨天，如 22:00 - 7:00
            hour >= start || hour < end
        }
    }
    
    /** 获取今日喝水总量（毫升） */
    fun getTodayTotalMl(): Int {
        val today = getTodayDateString()
        val savedDate = prefs.getString("last_drink_date", null)
        
        if (savedDate != today) {
            // 新的一天，重置统计
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
        logs.add(0, log) // 添加到开头
        if (logs.size > 100) {
            logs.removeAt(logs.size - 1) // 保留最近100条
        }
        
        val json = moshi.adapter<List<WaterLog>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, WaterLog::class.java)
        ).toJson(logs)
        
        prefs.edit().putString("water_logs", json).apply()
    }
    
    /** 获取最近的喝水记录 */
    fun getRecentLogs(): List<WaterLog> {
        val json = prefs.getString("water_logs", null) ?: return emptyList()
        return runCatching {
            moshi.adapter<List<WaterLog>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, WaterLog::class.java)
            ).fromJson(json)
        }.getOrNull() ?: emptyList()
    }
    
    /** 获取日历事件ID */
    fun getCalendarEventId(): Long? {
        val id = prefs.getLong("calendar_event_id", -1)
        return if (id == -1L) null else id
    }
    
    /** 设置日历事件ID */
    fun setCalendarEventId(eventId: Long?) {
        prefs.edit().putLong("calendar_event_id", eventId ?: -1).apply()
    }
    
    /** 获取上次提醒时间 */
    fun getLastReminderTime(): Long = prefs.getLong("last_reminder_time", 0)
    fun setLastReminderTime(time: Long) {
        prefs.edit().putLong("last_reminder_time", time).apply()
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
}
