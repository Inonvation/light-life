package com.inonvation.lightlife.data.water

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * 日历提醒管理器。
 * 通过 CalendarContract 在系统日历创建重复事件实现喝水提醒。
 * 优点：完全不依赖应用进程，系统保证触发。
 */
class CalendarReminderManager(private val context: Context) {
    
    companion object {
        private const val CALENDAR_ACCOUNT_NAME = "lightlife_water_reminder"
        private const val CALENDAR_DISPLAY_NAME = "喝水提醒"
        private const val EVENT_TITLE = "💧 该喝水啦"
        private const val EVENT_DURATION_MINUTES = 5
    }
    
    /**
     * 检查是否有日历权限
     */
    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 创建喝水提醒日历事件
     * @param intervalMinutes 提醒间隔（分钟）
     * @param quietStartHour 免打扰开始小时
     * @param quietEndHour 免打扰结束小时
     * @return 事件ID，失败返回null
     */
    fun createReminderEvent(intervalMinutes: Int, quietStartHour: Int, quietEndHour: Int): Long? {
        if (!hasCalendarPermission()) return null
        
        // 获取或创建日历账户
        val calId = getOrCreateCalendarId() ?: return null
        
        // 删除旧的提醒事件
        deleteReminderEvent()
        
        // 创建重复事件
        val eventId = insertRepeatingEvent(calId, intervalMinutes, quietStartHour, quietEndHour)
        
        return eventId
    }
    
    /**
     * 删除喝水提醒日历事件
     */
    fun deleteReminderEvent() {
        if (!hasCalendarPermission()) return
        
        val store = WaterReminderStore(context)
        val eventId = store.getCalendarEventId()
        
        if (eventId != null && eventId > 0) {
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(deleteUri, null, null)
            store.setCalendarEventId(null)
        }
    }
    
    /**
     * 获取或创建日历账户
     */
    private fun getOrCreateCalendarId(): Long? {
        val accounts = CalendarContract.Calendars.CONTENT_URI
        
        // 查询是否存在我们的日历
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ?"
        val selectionArgs = arrayOf(CALENDAR_ACCOUNT_NAME)
        
        context.contentResolver.query(accounts, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        
        // 不存在，创建新日历
        return createCalendarAccount()
    }
    
    /**
     * 创建日历账户
     */
    private fun createCalendarAccount(): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF2196F3.toInt()) // 蓝色
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        
        val result = context.contentResolver.insert(uri, values)
        return result?.lastPathSegment?.toLongOrNull()
    }
    
    /**
     * 插入重复事件
     */
    private fun insertRepeatingEvent(calId: Long, intervalMinutes: Int, quietStartHour: Int, quietEndHour: Int): Long? {
        val startMillis = System.currentTimeMillis() + intervalMinutes * 60 * 1000
        
        // 构建活跃小时列表（排除免打扰时段）
        val activeHours = getActiveHours(quietStartHour, quietEndHour)
        
        // 构建 RRULE
        val rrule = when {
            intervalMinutes >= 60 -> {
                // 每N小时，在活跃小时内
                val interval = intervalMinutes / 60
                val filteredHours = activeHours.filterIndexed { index, _ -> index % interval == 0 }
                "FREQ=DAILY;INTERVAL=1;BYHOUR=${filteredHours.joinToString(",")}"
            }
            else -> {
                // 每天，在活跃小时内触发
                "FREQ=DAILY;INTERVAL=1;BYHOUR=${activeHours.joinToString(",")}"
            }
        }
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, EVENT_TITLE)
            put(CalendarContract.Events.DESCRIPTION, "保持水分，健康生活")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DURATION, "PT${EVENT_DURATION_MINUTES}M")
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.RRULE, rrule)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }
        
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = uri?.lastPathSegment?.toLongOrNull()
        
        if (eventId != null) {
            // 添加提醒（到时间就提醒）
            insertReminder(eventId)
            
            // 保存事件ID
            WaterReminderStore(context).setCalendarEventId(eventId)
        }
        
        return eventId
    }
    
    /**
     * 获取活跃小时列表（排除免打扰时段）
     */
    private fun getActiveHours(quietStartHour: Int, quietEndHour: Int): List<Int> {
        val allHours = (0..23).toList()
        
        return if (quietStartHour <= quietEndHour) {
            // 同一天内免打扰，如 1:00 - 7:00
            allHours.filter { it < quietStartHour || it >= quietEndHour }
        } else {
            // 跨天免打扰，如 22:00 - 7:00
            allHours.filter { it < quietEndHour || it >= quietStartHour }
        }
    }
    
    /**
     * 插入事件提醒
     */
    private fun insertReminder(eventId: Long) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0) // 0 = 到时间就提醒
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }
    
    /**
     * 更新提醒事件
     */
    fun updateReminderEvent(intervalMinutes: Int, quietStartHour: Int, quietEndHour: Int): Long? {
        deleteReminderEvent()
        return createReminderEvent(intervalMinutes, quietStartHour, quietEndHour)
    }
    
    /**
     * 检查事件是否存在
     */
    fun isEventExists(): Boolean {
        if (!hasCalendarPermission()) return false
        
        val eventId = WaterReminderStore(context).getCalendarEventId() ?: return false
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(CalendarContract.Events._ID)
        
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            return cursor.count > 0
        }
        
        return false
    }
    
    /**
     * 删除所有喝水提醒日历事件和日历账户
     * @return 是否成功删除
     */
    fun deleteAllEventsAndCalendar(): Boolean {
        if (!hasCalendarPermission()) return false
        
        val calId = getOrCreateCalendarId() ?: return false
        
        // 删除该日历下的所有事件
        val eventsUri = CalendarContract.Events.CONTENT_URI
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calId.toString())
        context.contentResolver.delete(eventsUri, selection, selectionArgs)
        
        // 删除日历账户
        val calUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        context.contentResolver.delete(calUri, null, null)
        
        // 清除存储的事件ID
        WaterReminderStore(context).setCalendarEventId(null)
        
        return true
    }
}
