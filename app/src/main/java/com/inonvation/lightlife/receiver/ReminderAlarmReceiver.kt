package com.inonvation.lightlife.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.inonvation.lightlife.MainActivity
import com.inonvation.lightlife.R
import com.inonvation.lightlife.data.water.AlarmScheduler
import com.inonvation.lightlife.data.water.WaterReminderStore

/**
 * 喝水提醒闹钟广播接收器。
 * 接收 AlarmManager 触发的闹钟，显示喝水提醒通知。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val CHANNEL_ID = "water_reminder"
        private const val NOTIFICATION_ID = 3001
        private const val ACTION_DRINK_WATER = "com.inonvation.lightlife.action.DRINK_WATER"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DRINK_WATER -> {
                // 用户点击了"已喝水"按钮
                handleDrinkWater(context)
            }
            else -> {
                // 闹钟触发，显示提醒通知
                showReminderNotification(context)
                
                // 设置下一个提醒
                val scheduler = AlarmScheduler(context)
                scheduler.scheduleNextReminder()
            }
        }
    }
    
    /**
     * 显示喝水提醒通知
     */
    private fun showReminderNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 创建通知渠道
        createNotificationChannel(notificationManager)
        
        // 创建"已喝水"动作
        val drinkIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_DRINK_WATER
        }
        val drinkPendingIntent = PendingIntent.getBroadcast(
            context, 0, drinkIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 打开应用的 PendingIntent
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 获取提醒语
        val reminderMessage = getReminderMessage()
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("喝水提醒")
            .setContentText(reminderMessage)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .addAction(R.mipmap.ic_launcher, "已喝水", drinkPendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 处理用户点击"已喝水"
     */
    private fun handleDrinkWater(context: Context) {
        val store = WaterReminderStore(context)
        val cupSizeMl = store.getCupSizeMl()
        
        // 记录喝水
        store.recordDrink(cupSizeMl)
        
        // 取消当前通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        
        // 设置下一个提醒
        val scheduler = AlarmScheduler(context)
        scheduler.scheduleNextReminder()
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "喝水提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "定时提醒您喝水"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * 根据当前时间获取提醒语
     */
    private fun getReminderMessage(): String {
        val hour = java.time.LocalTime.now().hour
        
        val messages = when {
            hour in 6..9 -> listOf(
                "早安！起床喝杯水，开启元气满满的一天",
                "早上好！喝杯温水，唤醒身体",
                "新的一天，从一杯水开始"
            )
            hour in 10..11 -> listOf(
                "工作间隙，喝杯水休息一下",
                "别忘了喝水哦，保持专注",
                "喝杯水，补充能量"
            )
            hour in 12..13 -> listOf(
                "午饭后喝杯水，帮助消化",
                "午后时光，来杯水吧",
                "吃饭后记得喝水哦"
            )
            hour in 14..17 -> listOf(
                "下午茶时间，喝杯水提神",
                "工作辛苦了，喝口水休息下",
                "保持水分，提高效率",
                "别忘了喝水，保持活力"
            )
            hour in 18..20 -> listOf(
                "傍晚时分，喝杯水放松一下",
                "晚餐前喝杯水，有益健康",
                "结束一天工作，喝杯水吧"
            )
            hour in 21..22 -> listOf(
                "睡前喝杯水，但不要太多哦",
                "晚安前喝杯水，做个好梦",
                "适量饮水，安然入睡"
            )
            else -> listOf(
                "该喝水啦！",
                "别忘了喝水哦",
                "喝水时间到！"
            )
        }
        
        return messages.random()
    }
}
