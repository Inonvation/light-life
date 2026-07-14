package com.example.devicecontrol.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val suppressPointsTaskWarning: Boolean? = null,
    val orderHistory: List<OrderHistoryItem>? = null,
    val pointsStats: PointsStatsPayload? = null,
    val taskLogs: List<TaskLogPayload>? = null,
)

data class PointsStatsPayload(
    val totalEarned: Int = 0,
    val totalDeducted: String = "0.00",
)

data class TaskLogPayload(
    val name: String = "",
    val content: String = "",
)

/**
 * 管理 App 数据的备份与恢复。
 * 备份文件为 JSON 格式，统一后缀 .lif（LightLife Backup）。
 */
class BackupManager(private val context: Context) {

    fun getContext(): Context = context

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
        suppressPointsTaskWarning: Boolean,
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
                suppressPointsTaskWarning = suppressPointsTaskWarning,
                orderHistory = orderHistory.ifEmpty { null },
                pointsStats = pointsStats?.let {
                    PointsStatsPayload(
                        totalEarned = it.getTotalEarned(),
                        totalDeducted = it.getTotalDeductedAmount(),
                    )
                },
                taskLogs = logs?.ifEmpty { null },
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
            val parsed = backupAdapter.fromJson(json) ?: return@runCatching null
            if (parsed.backupVersion != 1) return@runCatching null
            parsed
        }.getOrNull()
    }

    /**
     * 将备份数据恢复到各 Store。
     * 返回恢复摘要信息。
     */
    fun restore(
        backup: BackupData,
        tokenStore: TokenStore?,
        orderHistoryStore: OrderHistoryStore?,
        pointsStatsStore: PointsStatsStore?,
        taskLogStore: TaskLogStore?,
        onResult: (message: String) -> Unit,
    ) {
        val payload = backup.data
        val restored = mutableListOf<String>()

        // 恢复 Token
        payload.token?.let { t ->
            if (t.isNotBlank()) {
                tokenStore?.saveToken(t)
                restored.add("Token")
            }
        }

        // 恢复订单历史
        payload.orderHistory?.let { orders ->
            val orderPrefs = context.getSharedPreferences("order_history", Context.MODE_PRIVATE)
            val listType = Types.newParameterizedType(List::class.java, OrderHistoryItem::class.java)
            val listAdapter = moshi.adapter<List<OrderHistoryItem>>(listType)
            orderPrefs.edit().putString("orders", listAdapter.toJson(orders.take(50))).apply()
            restored.add("${orders.size} 笔订单")
        }

        // 恢复积分统计
        payload.pointsStats?.let { stats ->
            val prefs = context.getSharedPreferences("points_stats", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("total_earned", stats.totalEarned)
                .putString("total_deducted", stats.totalDeducted)
                .apply()
            restored.add("积分统计")
        }

        // 恢复任务日志
        payload.taskLogs?.let { logs ->
            val logDir = File(context.filesDir, "task_logs").also { it.mkdirs() }
            logDir.listFiles()?.forEach { it.delete() }
            for (log in logs) {
                val file = File(logDir, log.name)
                file.writeText(log.content, Charsets.UTF_8)
            }
            restored.add("${logs.size} 条执行日志")
        }

        onResult("已恢复：" + restored.joinToString("、"))
    }

    companion object {
        const val BACKUP_MIME_TYPE = "application/json"
        const val SUGGESTED_EXTENSION = ".lif"
    }
}
