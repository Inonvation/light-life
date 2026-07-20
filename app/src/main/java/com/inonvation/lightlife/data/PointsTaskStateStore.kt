package com.inonvation.lightlife.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PointsTaskStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("points_task_state", Context.MODE_PRIVATE)

    // 执行开始时冻结日期，所有读写基于此日期而非实时 today()，避免跨天污染
    private var sessionDate: String? = null

    fun setSessionDate(date: String) { sessionDate = date }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    private fun effectiveDate(): String = sessionDate ?: today()
    private fun savedDate(): String = prefs.getString("run_date", "") ?: ""
    fun getRunDate(): String = savedDate()
    private fun isActiveDay(): Boolean = savedDate() == effectiveDate()

    fun getAppVideoCount(): Int = if (isActiveDay()) prefs.getInt("app_video", 0) else 0
    fun setAppVideoCount(n: Int) { prefs.edit().putInt("app_video", n).putString("run_date", effectiveDate()).apply() }

    fun getAlipayVideoCount(): Int = if (isActiveDay()) prefs.getInt("alipay_video", 0) else 0
    fun setAlipayVideoCount(n: Int) { prefs.edit().putInt("alipay_video", n).putString("run_date", effectiveDate()).apply() }

    fun isSignInDone(): Boolean = isActiveDay() && prefs.getBoolean("signin_done", false)
    fun setSignInDone(v: Boolean) { prefs.edit().putBoolean("signin_done", v).putString("run_date", effectiveDate()).apply() }

    fun isTaskListDone(): Boolean = isActiveDay() && prefs.getBoolean("tasklist_done", false)
    fun setTaskListDone(v: Boolean) { prefs.edit().putBoolean("tasklist_done", v).putString("run_date", effectiveDate()).apply() }

    fun isAdTaskDone(): Boolean = isActiveDay() && prefs.getBoolean("ad_task_done", false)
    fun setAdTaskDone(v: Boolean) { prefs.edit().putBoolean("ad_task_done", v).putString("run_date", effectiveDate()).apply() }

    fun isOtherTaskDone(): Boolean = isActiveDay() && prefs.getBoolean("other_task_done", false)
    fun setOtherTaskDone(v: Boolean) { prefs.edit().putBoolean("other_task_done", v).putString("run_date", effectiveDate()).apply() }

    fun isHomePageDone(): Boolean = isActiveDay() && prefs.getBoolean("home_page_done", false)
    fun setHomePageDone(v: Boolean) { prefs.edit().putBoolean("home_page_done", v).putString("run_date", effectiveDate()).apply() }

    fun getAppVideoDone(): Boolean = isActiveDay() && prefs.getBoolean("app_video_done", false)
    fun setAppVideoDone(v: Boolean) { prefs.edit().putBoolean("app_video_done", v).putString("run_date", effectiveDate()).apply() }

    fun getAlipayVideoTaskCount(): Int = if (isActiveDay()) prefs.getInt("alipay_video_task", 0) else 0
    fun setAlipayVideoTaskCount(n: Int) { prefs.edit().putInt("alipay_video_task", n).putString("run_date", effectiveDate()).apply() }

    fun isAlipayVideoTaskDone(): Boolean = isActiveDay() && prefs.getBoolean("alipay_video_task_done", false)
    fun setAlipayVideoTaskDone(v: Boolean) { prefs.edit().putBoolean("alipay_video_task_done", v).putString("run_date", effectiveDate()).apply() }

    fun isHapticEnabled(): Boolean = prefs.getBoolean("haptic_enabled", true)
    fun setHapticEnabled(v: Boolean) { prefs.edit().putBoolean("haptic_enabled", v).apply() }

    fun isAutoCleanLogsEnabled(): Boolean = prefs.getBoolean("auto_clean_logs", false)
    fun setAutoCleanLogsEnabled(v: Boolean) { prefs.edit().putBoolean("auto_clean_logs", v).apply() }

    fun isAutoStartTaskEnabled(): Boolean = prefs.getBoolean("auto_start_task", false)
    fun setAutoStartTaskEnabled(v: Boolean) { prefs.edit().putBoolean("auto_start_task", v).apply() }

    fun isBackgroundTaskEnabled(): Boolean = prefs.getBoolean("background_task", true)
    fun setBackgroundTaskEnabled(v: Boolean) { prefs.edit().putBoolean("background_task", v).apply() }

    fun isRandomDelayEnabled(): Boolean = prefs.getBoolean("random_delay", false)
    fun setRandomDelayEnabled(v: Boolean) { prefs.edit().putBoolean("random_delay", v).apply() }



    fun isSafeModeEnabled(): Boolean = prefs.getBoolean("safe_mode", false)
    fun setSafeModeEnabled(v: Boolean) { prefs.edit().putBoolean("safe_mode", v).apply() }

    fun isSimpleModeEnabled(): Boolean = prefs.getBoolean("simple_mode", false)
    fun setSimpleModeEnabled(v: Boolean) { prefs.edit().putBoolean("simple_mode", v).apply() }

    fun isBackupPrivacySafe(): Boolean = prefs.getBoolean("backup_privacy_safe", false)
    fun setBackupPrivacySafe(v: Boolean) { prefs.edit().putBoolean("backup_privacy_safe", v).apply() }

    fun getPhase(): String {
        if (!isActiveDay()) {
            reset()
            return "none"
        }
        return prefs.getString("phase", "none") ?: "none"
    }
    fun setPhase(p: String) { prefs.edit().putString("phase", p).putString("run_date", effectiveDate()).apply() }

    fun getUserAgent(): String = prefs.getString("user_agent", "") ?: ""
    fun setUserAgent(ua: String) { prefs.edit().putString("user_agent", ua).apply() }

    fun getCompletedTaskCodes(): Set<String> = prefs.getStringSet("task_codes", emptySet()) ?: emptySet()
    fun addCompletedTaskCode(code: String) {
        val codes = prefs.getStringSet("task_codes", emptySet())?.toMutableSet() ?: mutableSetOf()
        codes.add(code)
        prefs.edit().putStringSet("task_codes", codes).putString("run_date", effectiveDate()).apply()
    }

    fun reset() {
        val editor = prefs.edit()
        listOf("run_date", "app_video", "alipay_video", "alipay_video_task", "signin_done", "tasklist_done", "ad_task_done", "other_task_done", "home_page_done", "app_video_done", "alipay_video_task_done", "task_codes", "phase").forEach { editor.remove(it) }
        editor.apply()
    }
}
