package com.inonvation.lightlife.data

import android.content.Context

class PointsStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("points_stats", Context.MODE_PRIVATE)

    fun getTotalDeductedAmount(): String = prefs.getString(KEY_DEDUCTED, "0.00") ?: "0.00"

    fun addDeducted(amount: String) {
        val current = getTotalDeductedAmount()
        val newTotal = (current.toDoubleOrNull() ?: 0.0) + (amount.toDoubleOrNull() ?: 0.0)
        prefs.edit()
            .putString(KEY_DEDUCTED, String.format("%.2f", newTotal))
            .apply()
    }

    /** 获取今日获得的积分 */
    fun getTodayEarned(): Int {
        val savedDate = prefs.getString(KEY_EARNED_DATE, "") ?: ""
        val today = java.time.LocalDate.now().toString()
        return if (savedDate == today) prefs.getInt(KEY_EARNED, 0) else 0
    }

    /** 记录今日获得的积分（累加） */
    fun addTodayEarned(points: Int) {
        val today = java.time.LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_EARNED_DATE, "") ?: ""
        val current = if (savedDate == today) prefs.getInt(KEY_EARNED, 0) else 0
        prefs.edit()
            .putString(KEY_EARNED_DATE, today)
            .putInt(KEY_EARNED, current + points)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        private const val KEY_DEDUCTED = "total_deducted"
        private const val KEY_EARNED = "today_earned"
        private const val KEY_EARNED_DATE = "today_earned_date"
    }
}
