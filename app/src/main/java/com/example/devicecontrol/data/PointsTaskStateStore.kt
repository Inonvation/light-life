package com.example.devicecontrol.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PointsTaskStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("points_task_state", Context.MODE_PRIVATE)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    private fun savedDate(): String = prefs.getString("run_date", "") ?: ""
    fun getRunDate(): String = savedDate()
    private fun isToday(): Boolean = savedDate() == today()

    fun getAppVideoCount(): Int = if (isToday()) prefs.getInt("app_video", 0) else 0
    fun setAppVideoCount(n: Int) { prefs.edit().putInt("app_video", n).putString("run_date", today()).apply() }

    fun getAlipayVideoCount(): Int = if (isToday()) prefs.getInt("alipay_video", 0) else 0
    fun setAlipayVideoCount(n: Int) { prefs.edit().putInt("alipay_video", n).putString("run_date", today()).apply() }



    fun isLogCompactEnabled(): Boolean = prefs.getBoolean("log_compact", true)
    fun setLogCompactEnabled(v: Boolean) { prefs.edit().putBoolean("log_compact", v).apply() }

    fun isHapticEnabled(): Boolean = prefs.getBoolean("haptic_enabled", true)
    fun setHapticEnabled(v: Boolean) { prefs.edit().putBoolean("haptic_enabled", v).apply() }

    fun isAutoCleanLogsEnabled(): Boolean = prefs.getBoolean("auto_clean_logs", false)
    fun setAutoCleanLogsEnabled(v: Boolean) { prefs.edit().putBoolean("auto_clean_logs", v).apply() }

    fun getPhase(): String {
        if (!isToday()) {
            reset()
            return "none"
        }
        return prefs.getString("phase", "none") ?: "none"
    }
    fun setPhase(p: String) { prefs.edit().putString("phase", p).putString("run_date", today()).apply() }

    fun getUserAgent(): String = prefs.getString("user_agent", "") ?: ""
    fun setUserAgent(ua: String) { prefs.edit().putString("user_agent", ua).apply() }

    fun reset() {
        val editor = prefs.edit()
        // 只删除任务运行状态，保留用户偏好设置
        listOf("run_date", "app_video", "alipay_video", "task_codes", "phase").forEach { editor.remove(it) }
        editor.apply()
    }
}