package com.inonvation.lightlife.data

import android.content.Context

class PointsTaskStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("points_task_state", Context.MODE_PRIVATE)

    // ── 设置开关 ──

    fun isHapticEnabled(): Boolean = prefs.getBoolean("haptic_enabled", true)
    fun setHapticEnabled(v: Boolean) { prefs.edit().putBoolean("haptic_enabled", v).apply() }

    fun isAutoStartTaskEnabled(): Boolean = prefs.getBoolean("auto_start_task", false)
    fun setAutoStartTaskEnabled(v: Boolean) { prefs.edit().putBoolean("auto_start_task", v).apply() }

    fun isBackgroundTaskEnabled(): Boolean = prefs.getBoolean("background_task", true)
    fun setBackgroundTaskEnabled(v: Boolean) { prefs.edit().putBoolean("background_task", v).apply() }

    fun isRandomDelayEnabled(): Boolean = prefs.getBoolean("random_delay", false)
    fun setRandomDelayEnabled(v: Boolean) { prefs.edit().putBoolean("random_delay", v).apply() }

    fun isUsePointsForUnlockEnabled(): Boolean = prefs.getBoolean("use_points_unlock", true)
    fun setUsePointsForUnlockEnabled(v: Boolean) { prefs.edit().putBoolean("use_points_unlock", v).apply() }

    fun isSafeModeEnabled(): Boolean = prefs.getBoolean("safe_mode", false)
    fun setSafeModeEnabled(v: Boolean) { prefs.edit().putBoolean("safe_mode", v).apply() }

    fun isSimpleModeEnabled(): Boolean = prefs.getBoolean("simple_mode", false)
    fun setSimpleModeEnabled(v: Boolean) { prefs.edit().putBoolean("simple_mode", v).apply() }

    fun isBackupPrivacySafe(): Boolean = prefs.getBoolean("backup_privacy_safe", true)
    fun setBackupPrivacySafe(v: Boolean) { prefs.edit().putBoolean("backup_privacy_safe", v).apply() }

    fun isWaterReminderEnabled(): Boolean = prefs.getBoolean("water_reminder_enabled", true)
    fun setWaterReminderEnabled(v: Boolean) { prefs.edit().putBoolean("water_reminder_enabled", v).apply() }

    fun getLogStyle(): String = prefs.getString("log_style", "BUBBLE") ?: "BUBBLE"
    fun setLogStyle(v: String) { prefs.edit().putString("log_style", v).apply() }

    fun getUserAgent(): String = prefs.getString("user_agent", "") ?: ""
    fun setUserAgent(ua: String) { prefs.edit().putString("user_agent", ua).apply() }

    /**
     * 清除账号与运行相关状态（如 UserAgent），保留用户偏好设置。
     * 用于登出、清除日志等场景，避免误清"自动执行""随机延迟"等用户设置。
     */
    fun reset() {
        prefs.edit().remove("user_agent").apply()
    }
}
