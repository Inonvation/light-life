package com.inonvation.lightlife.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.inonvation.lightlife.data.water.AlarmScheduler

/**
 * 开机广播接收器。
 * 设备重启后重新设置喝水提醒闹钟。
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val scheduler = AlarmScheduler(context)
            scheduler.rescheduleIfNeeded()
        }
    }
}
