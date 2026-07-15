package com.example.devicecontrol.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

data class BackupData(
    val backupVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "",
    val data: BackupPayload = BackupPayload(),
)

data class BackupPayload(
    val token: String? = null,
    val themeMode: String? = null,
    val hapticEnabled: Boolean? = null,
    val logCompactEnabled: Boolean? = null,
    val autoCleanLogsEnabled: Boolean? = null,
    val userAgent: String? = null,
    val simpleModeEnabled: Boolean? = null,
    val orderHistory: List<OrderHistoryItem>? = null,
    val pointsStats: PointsStatsPayload? = null,
    val taskLogs: List<TaskLogPayload>? = null,
    val dailyTaskState: DailyTaskStatePayload? = null,
)

data class PointsStatsPayload(
    val totalEarned: Int = 0,
    val totalDeducted: String = "0.00",
)

data class RestoreCounts(val orders: Int = 0, val logs: Int = 0)

data class TaskLogPayload(
    val name: String = "",
    val content: String = "",
)

data class DailyTaskStatePayload(
    val phase: String = "none",
    val signInDone: Boolean = false,
    val taskListDone: Boolean = false,
    val appVideoCount: Int = 0,
    val alipayVideoCount: Int = 0,
    val runDate: String = "",
)

/**
 * 管理 App 数据的备份与恢复。
 * 备份文件为 JSON 格式，统一后缀 .lif（LightLife Backup）。
 */
class BackupManager(private val context: Context) {


    private val moshi = Moshi.Builder()
        .add(LenientStringJsonAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    private val backupAdapter = moshi.adapter(BackupData::class.java)

    /**
     * 收集当前所有数据，生成 BackupData 对象。
     */
    fun collect(
        appVersion: String,
        token: String?,
        orderHistory: List<OrderHistoryItem>,
        pointsStats: PointsStatsStore?,
        taskLogStore: TaskLogStore?,
        themeMode: String,
        hapticEnabled: Boolean,
        logCompactEnabled: Boolean,
        autoCleanLogsEnabled: Boolean,
        userAgent: String,
        simpleModeEnabled: Boolean,
        taskStateStore: PointsTaskStateStore? = null,
    ): BackupData {
        val logs = taskLogStore?.let { store ->
            store.listFiles().map { (name, content) ->
                TaskLogPayload(name = name, content = content)
            }
        }
        return BackupData(
            appVersion = appVersion,
            data = BackupPayload(
                token = token,
                themeMode = themeMode,
                hapticEnabled = hapticEnabled,
                logCompactEnabled = logCompactEnabled,
                autoCleanLogsEnabled = autoCleanLogsEnabled,
                userAgent = userAgent,
                simpleModeEnabled = simpleModeEnabled,
                orderHistory = orderHistory.ifEmpty { null },
                pointsStats = pointsStats?.let {
                    PointsStatsPayload(
                        totalEarned = it.getTotalEarned(),
                        totalDeducted = it.getTotalDeductedAmount(),
                    )
                },
                taskLogs = logs?.ifEmpty { null },
                dailyTaskState = taskStateStore?.let {
                    DailyTaskStatePayload(
                        phase = it.getPhase(),
                        signInDone = it.isSignInDone(),
                        taskListDone = it.isTaskListDone(),
                        appVideoCount = it.getAppVideoCount(),
                        alipayVideoCount = it.getAlipayVideoCount(),
                        runDate = it.getRunDate(),
                    )
                },
            ),
        )
    }

    /**
     * 将 BackupData 序列化为 JSON 字符串。
     */
    fun toJson(backup: BackupData): String {
        return backupAdapter.toJson(backup)
    }

    /**
     * 从 JSON 字符串解析 BackupData。
     * 返回 null 表示格式不匹配或解析失败。
     */
    fun fromJson(json: String): BackupData? {
        return runCatching {
            // 先尝试解析顶层结构，看是否为合法 JSON
            val parsed = backupAdapter.fromJson(json) ?: return@runCatching null
            // 验证备份版本号
            if (parsed.backupVersion != BACKUP_VERSION) return@runCatching null
            // 验证 data 字段至少有一项备份内容
            val d = parsed.data
            if (d.token == null && d.themeMode == null && d.hapticEnabled == null
                && d.logCompactEnabled == null
                && d.orderHistory == null && d.pointsStats == null && d.taskLogs == null) {
                return@runCatching null
            }
            parsed
        }.getOrNull()
    }

    /**
     * 将备份数据恢复到各 Store。
     * 返回恢复摘要信息。
     */
    fun restore(backup: BackupData): RestoreCounts {
        val payload = backup.data
        var orderCount = 0
        var logCount = 0

        payload.orderHistory?.let { orders ->
            val orderPrefs = context.getSharedPreferences("order_history", Context.MODE_PRIVATE)
            val listType = Types.newParameterizedType(List::class.java, OrderHistoryItem::class.java)
            val listAdapter = moshi.adapter<List<OrderHistoryItem>>(listType)
            orderPrefs.edit().putString("orders", listAdapter.toJson(orders.take(50))).apply()
            orderCount = orders.take(50).size
        }

        payload.pointsStats?.let { stats ->
            val prefs = context.getSharedPreferences("points_stats", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("total_earned", stats.totalEarned)
                .putString("total_deducted", stats.totalDeducted)
                .apply()
        }

        payload.taskLogs?.let { logs ->
            val logDir = File(context.filesDir, "task_logs").also { it.mkdirs() }
            logDir.listFiles()?.forEach { it.delete() }
            for (log in logs) {
                File(logDir, log.name).writeText(log.content, Charsets.UTF_8)
                logCount++
            }
        }

        // 恢复主题设置
        payload.themeMode?.let { mode ->
            val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("theme_mode", mode).apply()
        }

        // 恢复触感、日志紧凑模式、UserAgent 和每日任务状态
        val taskPrefs = context.getSharedPreferences("points_task_state", Context.MODE_PRIVATE)
        payload.hapticEnabled?.let { taskPrefs.edit().putBoolean("haptic_enabled", it).apply() }
        payload.logCompactEnabled?.let { taskPrefs.edit().putBoolean("log_compact", it).apply() }
        payload.autoCleanLogsEnabled?.let { taskPrefs.edit().putBoolean("auto_clean_logs", it).apply() }
        payload.userAgent?.let { if (it.isNotBlank()) taskPrefs.edit().putString("user_agent", it).apply() }
        payload.simpleModeEnabled?.let { taskPrefs.edit().putBoolean("simple_mode", it).apply() }

        // 恢复 Token（需要通过 TokenStore 写入 EncryptedSharedPreferences）
        payload.token?.let { token ->
            if (token.isNotBlank()) {
                TokenStore(context).saveToken(token)
            }
        }

        payload.dailyTaskState?.let { daily ->
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())
            if (daily.runDate == today && daily.phase != "none") {
                taskPrefs.edit()
                    .putString("phase", daily.phase)
                    .putBoolean("signin_done", daily.signInDone)
                    .putBoolean("tasklist_done", daily.taskListDone)
                    .putInt("app_video", daily.appVideoCount)
                    .putInt("alipay_video", daily.alipayVideoCount)
                    .putString("run_date", daily.runDate)
                    .apply()
            }
        }

        return RestoreCounts(orderCount, logCount)
    }

    companion object {
        const val BACKUP_VERSION = 1
        const val BACKUP_MIME_TYPE = "application/json"
        const val SUGGESTED_EXTENSION = ".lif"
    }
}

