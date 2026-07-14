package com.example.devicecontrol.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PointsTaskStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("points_task_state", Context.MODE_PRIVATE)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    private fun savedDate(): String = prefs.getString("run_date", "") ?: ""
    private fun isToday(): Boolean = savedDate() == today()

    fun getAppVideoCount(): Int = if (isToday()) prefs.getInt("app_video", 0) else 0
    fun setAppVideoCount(n: Int) { prefs.edit().putInt("app_video", n).putString("run_date", today()).apply() }

    fun getAlipayVideoCount(): Int = if (isToday()) prefs.getInt("alipay_video", 0) else 0
    fun setAlipayVideoCount(n: Int) { prefs.edit().putInt("alipay_video", n).putString("run_date", today()).apply() }

    fun getCompletedTaskCodes(): Set<String> =
        if (isToday()) prefs.getStringSet("task_codes", emptySet()) ?: emptySet()
        else emptySet()
    fun addCompletedTaskCode(code: String) {
        val s = getCompletedTaskCodes().toMutableSet().apply { add(code) }
        prefs.edit().putStringSet("task_codes", s).putString("run_date", today()).apply()
    }

    fun isAllCompleted(): Boolean = getAppVideoCount() >= 20 && getAlipayVideoCount() >= 50

    fun isLogCompactEnabled(): Boolean = prefs.getBoolean("log_compact", true)
    fun setLogCompactEnabled(v: Boolean) { prefs.edit().putBoolean("log_compact", v).apply() }

    fun reset() { prefs.edit().clear().apply() }
}