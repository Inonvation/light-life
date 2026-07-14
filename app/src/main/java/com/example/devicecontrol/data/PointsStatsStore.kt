package com.example.devicecontrol.data

import android.content.Context

class PointsStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("points_stats", Context.MODE_PRIVATE)

    fun getTotalEarned(): Int = prefs.getInt(KEY_EARNED, 0)

    fun getTotalDeductedAmount(): String = prefs.getString(KEY_DEDUCTED, "0.00") ?: "0.00"

    fun addSession(pointsEarned: Int, deductedAmount: String) {
        prefs.edit()
            .putInt(KEY_EARNED, getTotalEarned() + pointsEarned)
            .putString(KEY_DEDUCTED, deductedAmount)
            .apply()
    }

    fun updateEarned(earned: Int) {
        prefs.edit().putInt(KEY_EARNED, earned).apply()
    }

    private companion object {
        private const val KEY_EARNED = "total_earned"
        private const val KEY_DEDUCTED = "total_deducted"
    }
}
