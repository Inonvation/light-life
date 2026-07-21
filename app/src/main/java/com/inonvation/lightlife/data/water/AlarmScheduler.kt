package com.inonvation.lightlife.data.water

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.inonvation.lightlife.receiver.ReminderAlarmReceiver
import java.time.LocalTime
import java.time.ZoneId

/**
 * 喝水提醒闹钟调度器。
 * 使用 setAlarmClock() 设置精确闹钟（最高优先级）。
 */
class AlarmScheduler(private val context: Context) {
    
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val store = WaterReminderStore(context)
    
    companion object {
        private const val REQUEST_CODE = 2001
    }
    
    /**
     * 检查是否有精确闹钟权限
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    /**
     * 设置下一个提醒闹钟
     * @return 下次提醒的时间戳（毫秒）
     */
    fun scheduleNextReminder(): Long {
        val intervalMinutes = store.getIntervalMinutes()
        val nextTriggerTime = calculateNextTriggerTime(intervalMinutes)
        
        scheduleAlarm(nextTriggerTime)
        store.setLastReminderTime(nextTriggerTime)
        
        return nextTriggerTime
    }
    
    /**
     * 取消提醒闹钟
     */
    fun cancelReminder() {
        val pendingIntent = createPendingIntent()
        alarmManager.cancel(pendingIntent)
    }
    
    /**
     * 计算下次触发时间
     * 会跳过免打扰时段
     */
    private fun calculateNextTriggerTime(intervalMinutes: Int): Long {
        val now = java.time.LocalDateTime.now()
        var nextTime = now.plusMinutes(intervalMinutes.toLong())
        
        // 检查是否在免打扰时段内
        while (store.isQuietTime(nextTime.toLocalTime())) {
            // 跳到下一个整点
            nextTime = nextTime.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        }
        
        return nextTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    
    /**
     * 设置精确闹钟
     */
    private fun scheduleAlarm(triggerAtMillis: Long) {
        val pendingIntent = createPendingIntent()
        
        // 使用 setAlarmClock() - 最高优先级，等同于系统闹钟
        val showIntent = PendingIntent.getActivity(
            context, REQUEST_CODE,
            Intent(context, com.inonvation.lightlife.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }
    
    /**
     * 创建 PendingIntent
     */
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    /**
     * 重新调度所有提醒（开机后调用）
     */
    fun rescheduleIfNeeded() {
        if (!store.isEnabled()) return
        
        val lastReminderTime = store.getLastReminderTime()
        val now = System.currentTimeMillis()
        
        // 如果上次提醒时间已过，设置新的提醒
        if (lastReminderTime <= now) {
            scheduleNextReminder()
        }
    }
}
