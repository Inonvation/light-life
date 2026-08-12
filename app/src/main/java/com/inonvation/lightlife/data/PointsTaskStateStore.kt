package com.inonvation.lightlife.data

import android.content.Context

/** 运行状态与少量用户偏好存储（签到、开水、主题之外的轻量设置） */
class PointsTaskStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("points_task_state", Context.MODE_PRIVATE)

    // ── 设置开关 ──

    fun isHapticEnabled(): Boolean = prefs.getBoolean("haptic_enabled", true)
    fun setHapticEnabled(v: Boolean) { prefs.edit().putBoolean("haptic_enabled", v).apply() }

    fun isUsePointsForUnlockEnabled(): Boolean = prefs.getBoolean("use_points_unlock", true)
    fun setUsePointsForUnlockEnabled(v: Boolean) { prefs.edit().putBoolean("use_points_unlock", v).apply() }

    /** 打开 App 时自动签到（默认开启） */
    fun isAutoSignInEnabled(): Boolean = prefs.getBoolean("auto_sign_in", true)
    fun setAutoSignInEnabled(v: Boolean) { prefs.edit().putBoolean("auto_sign_in", v).apply() }

    fun getUserAgent(): String = prefs.getString("user_agent", "") ?: ""
    fun setUserAgent(ua: String) { prefs.edit().putString("user_agent", ua).apply() }

    /** 登出时清除账号相关状态，保留用户偏好 */
    fun reset() {
        prefs.edit().remove("user_agent").apply()
    }
}
