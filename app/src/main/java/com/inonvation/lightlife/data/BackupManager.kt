package com.inonvation.lightlife.data

import android.content.Context
import com.squareup.moshi.Types
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
    val colorTheme: String? = null,
    val hapticEnabled: Boolean? = null,
    val userAgent: String? = null,
    val simpleModeEnabled: Boolean? = null,
    val orderHistory: List<OrderHistoryItem>? = null,
    val pointsStats: PointsStatsPayload? = null,
    val taskLogs: List<TaskLogPayload>? = null,
    val dailyTaskState: DailyTaskStatePayload? = null,
    val adVideoState: AdVideoStatePayload? = null,
    val quickLinks: Map<String, *>? = null,
    val quickLinksIcons: Map<String, String>? = null,
)

data class PointsStatsPayload(
    val totalDeducted: String = "0.00",
)

data class RestoreCounts(val orders: Int = 0, val logs: Int = 0)

data class TaskLogPayload(
    val name: String = "",
    val content: String = "",
)

data class AdVideoStatePayload(
    val signinDone: Boolean = false,
    val signinDoneDate: String = "",
    val tasklistDone: Boolean = false,
    val tasklistDoneDate: String = "",
    val appVideo: Int = 0,
    val appVideoDate: String = "",
    val alipayVideo: Int = 0,
    val alipayVideoDate: String = "",
    val adTask: Int = 0,
    val adTaskDate: String = "",
    val alipayVideoTask: Int = 0,
    val alipayVideoTaskDate: String = "",
    val homePageCount: Int = 0,
    val homePageCountDate: String = "",
)

data class DailyTaskStatePayload(
    val phase: String = "none",
    val signInDone: Boolean = false,
    val taskListDone: Boolean = false,
    val appVideoCount: Int = 0,
    val alipayVideoCount: Int = 0,
    val adTaskCount: Int = 0,
    val alipayVideoTaskCount: Int = 0,
    val homePageCount: Int = 0,
    val runDate: String = "",
)

/**
 * 管理 App 数据的备份与恢复。
 * 备份文件为 JSON 格式，统一后缀 .lif（LightLife Backup）。
 */
class BackupManager(private val context: Context) {


    private val moshi = MoshiProvider.instance

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
        colorTheme: String,
        hapticEnabled: Boolean,
        userAgent: String,
        simpleModeEnabled: Boolean,
        taskStateStore: PointsTaskStateStore? = null,
        quickLinkStore: QuickLinkStore? = null,
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
                colorTheme = colorTheme,
                hapticEnabled = hapticEnabled,
                userAgent = userAgent,
                simpleModeEnabled = simpleModeEnabled,
                orderHistory = orderHistory.ifEmpty { null },
                pointsStats = pointsStats?.let {
                    PointsStatsPayload(
                        totalDeducted = it.getTotalDeductedAmount(),
                    )
                },
                taskLogs = logs?.ifEmpty { null },
                dailyTaskState = run {
                    val adPrefs = context.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE)
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())
                    val savedDate = adPrefs.getString("signin_done_date", "") ?: ""
                    if (savedDate == today) {
                        DailyTaskStatePayload(
                            phase = "none",
                            signInDone = adPrefs.getBoolean("signin_done", false),
                            taskListDone = adPrefs.getBoolean("tasklist_done", false),
                            appVideoCount = adPrefs.getInt("app_video", 0),
                            alipayVideoCount = adPrefs.getInt("alipay_video", 0),
                            adTaskCount = adPrefs.getInt("ad_task", 0),
                            alipayVideoTaskCount = adPrefs.getInt("alipay_video_task", 0),
                            homePageCount = adPrefs.getInt("home_page_count", 0),
                            runDate = today,
                        )
                    } else null
                },
                adVideoState = run {
                    val adPrefs = context.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE)
                    val hasData = adPrefs.all.isNotEmpty()
                    if (hasData) {
                        AdVideoStatePayload(
                            signinDone = adPrefs.getBoolean("signin_done", false),
                            signinDoneDate = adPrefs.getString("signin_done_date", "") ?: "",
                            tasklistDone = adPrefs.getBoolean("tasklist_done", false),
                            tasklistDoneDate = adPrefs.getString("tasklist_done_date", "") ?: "",
                            appVideo = adPrefs.getInt("app_video", 0),
                            appVideoDate = adPrefs.getString("app_video_date", "") ?: "",
                            alipayVideo = adPrefs.getInt("alipay_video", 0),
                            alipayVideoDate = adPrefs.getString("alipay_video_date", "") ?: "",
                            adTask = adPrefs.getInt("ad_task", 0),
                            adTaskDate = adPrefs.getString("ad_task_date", "") ?: "",
                            alipayVideoTask = adPrefs.getInt("alipay_video_task", 0),
                            alipayVideoTaskDate = adPrefs.getString("alipay_video_task_date", "") ?: "",
                            homePageCount = adPrefs.getInt("home_page_count", 0),
                            homePageCountDate = adPrefs.getString("home_page_count_date", "") ?: "",
                        )
                    } else null
                },
                quickLinks = quickLinkStore?.exportData(),
                quickLinksIcons = quickLinkStore?.exportIcons()?.ifEmpty { null },
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
    fun fromJson(json: String, logError: ((String) -> Unit)? = null): BackupData? {
        val parsed = try {
            backupAdapter.fromJson(json) ?: run {
                logError?.invoke("备份文件无法解析为有效 JSON 结构")
                return null
            }
        } catch (e: Exception) {
            logError?.invoke("备份文件 JSON 解析失败：${e.message}")
            return null
        }
        // 验证备份版本号（向前兼容：接受小于等于当前版本的备份）
        if (parsed.backupVersion > BACKUP_VERSION) {
            logError?.invoke("备份版本过高：文件版本 ${parsed.backupVersion}，当前支持版本 $BACKUP_VERSION")
            return null
        }
        // 验证 data 字段至少有一项备份内容（覆盖所有字段）
        val d = parsed.data
        if (d.token == null && d.themeMode == null && d.colorTheme == null
            && d.hapticEnabled == null
            && d.userAgent == null && d.simpleModeEnabled == null
            && d.orderHistory == null && d.pointsStats == null && d.taskLogs == null
            && d.adVideoState == null && d.dailyTaskState == null
            && d.quickLinks == null) {
            logError?.invoke("备份文件内容为空，未包含任何有效数据")
            return null
        }
        return parsed
    }

    /**
     * 将备份数据恢复到各 Store。
     * 返回恢复摘要信息。
     */
    fun restore(backup: BackupData, quickLinkStore: QuickLinkStore? = null): RestoreCounts {
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
                .putString("total_deducted", stats.totalDeducted)
                .apply()
        }

        payload.taskLogs?.let { logs ->
            val logDir = File(context.filesDir, "task_logs").also { it.mkdirs() }
            // 恢复前先把现有日志移到临时目录，恢复失败时回滚，避免直接清空丢失数据
            val backupDir = File(context.filesDir, "task_logs_restore_backup").also { it.mkdirs() }
            val existingFiles = logDir.listFiles()?.toList() ?: emptyList()
            existingFiles.forEach { it.renameTo(File(backupDir, it.name)) }
            try {
                for (log in logs) {
                    File(logDir, log.name).writeText(log.content, Charsets.UTF_8)
                    logCount++
                }
                // 恢复成功，删除临时备份
                backupDir.listFiles()?.forEach { it.delete() }
                backupDir.delete()
            } catch (e: Exception) {
                // 恢复失败，回滚：清空半恢复的日志，把原日志移回
                logDir.listFiles()?.forEach { it.delete() }
                backupDir.listFiles()?.forEach { it.renameTo(File(logDir, it.name)) }
                backupDir.delete()
                throw e
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
        payload.userAgent?.let { if (it.isNotBlank()) taskPrefs.edit().putString("user_agent", it).apply() }
        payload.simpleModeEnabled?.let { taskPrefs.edit().putBoolean("simple_mode", it).apply() }

        // 恢复 Token（需要通过 TokenStore 写入 EncryptedSharedPreferences）
        payload.token?.let { token ->
            if (token.isNotBlank()) {
                TokenStore(context).saveToken(token)
            }
        }

        payload.adVideoState?.let { avs ->
            val adPrefs = context.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE)
            val editor = adPrefs.edit()
            editor.putTaskState("signin_done", avs.signinDone, "signin_done_date", avs.signinDoneDate)
            editor.putTaskState("tasklist_done", avs.tasklistDone, "tasklist_done_date", avs.tasklistDoneDate)
            editor.putTaskCount("app_video", avs.appVideo, avs.appVideoDate)
            editor.putTaskCount("alipay_video", avs.alipayVideo, avs.alipayVideoDate)
            editor.putTaskCount("ad_task", avs.adTask, avs.adTaskDate)
            editor.putTaskCount("alipay_video_task", avs.alipayVideoTask, avs.alipayVideoTaskDate)
            editor.putTaskCount("home_page_count", avs.homePageCount, avs.homePageCountDate)
            editor.apply()
        }

        payload.dailyTaskState?.let { daily ->
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())
            if (daily.runDate == today && daily.phase != "none") {
                val adPrefs = context.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE)
                val editor = adPrefs.edit()
                editor.putTaskState("signin_done", daily.signInDone, "signin_done_date", daily.runDate)
                editor.putTaskState("tasklist_done", daily.taskListDone, "tasklist_done_date", daily.runDate)
                editor.putTaskCount("app_video", daily.appVideoCount, daily.runDate)
                editor.putTaskCount("alipay_video", daily.alipayVideoCount, daily.runDate)
                editor.putTaskCount("ad_task", daily.adTaskCount, daily.runDate)
                editor.putTaskCount("alipay_video_task", daily.alipayVideoTaskCount, daily.runDate)
                editor.putTaskCount("home_page_count", daily.homePageCount, daily.runDate)
                editor.apply()
            }
        }

        // 恢复快捷链接
        payload.quickLinks?.let { data ->
            @Suppress("UNCHECKED_CAST")
            quickLinkStore?.importData(data as Map<String, *>)
        }
        payload.quickLinksIcons?.let { icons ->
            quickLinkStore?.importIcons(icons)
        }

        return RestoreCounts(orderCount, logCount)
    }

    /** 写入一个布尔任务状态及其日期 */
    private fun android.content.SharedPreferences.Editor.putTaskState(
        key: String, value: Boolean, dateKey: String, date: String
    ): android.content.SharedPreferences.Editor {
        putBoolean(key, value)
        putString(dateKey, date)
        return this
    }

    /** 写入一个计数控件及其日期 */
    private fun android.content.SharedPreferences.Editor.putTaskCount(
        key: String, count: Int, date: String
    ): android.content.SharedPreferences.Editor {
        putInt(key, count)
        putString("${key}_date", date)
        return this
    }

    companion object {
        const val BACKUP_VERSION = 1
    }
}

