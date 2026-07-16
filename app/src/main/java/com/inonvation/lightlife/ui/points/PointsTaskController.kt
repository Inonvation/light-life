package com.inonvation.lightlife.ui.points

import android.content.Context
import com.inonvation.lightlife.data.DebugLogStore
import com.inonvation.lightlife.data.PointsStatsStore
import com.inonvation.lightlife.data.PointsTaskRunner
import com.inonvation.lightlife.data.PointsTaskStateStore
import com.inonvation.lightlife.data.TaskCancelledException
import com.inonvation.lightlife.data.TaskLogStore
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.LogEntry
import com.inonvation.lightlife.ui.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PointsTaskController(
    private val state: MutableStateFlow<AppUiState>,
    private val scope: CoroutineScope,
    private val context: Context,
    private val pointsTaskRunner: PointsTaskRunner,
    private val taskStateStore: PointsTaskStateStore?,
    private val logStore: TaskLogStore?,
    private val debugLogStore: DebugLogStore?,
    private val pointsStatsStore: PointsStatsStore?,
    private val refreshBalance: suspend () -> Unit,
    private val showToast: (String) -> Unit,
) {
    private var pointsTaskJob: Job? = null

    fun startPointsTask(userAgent: String) {
        if (state.value.runningPointsTask) return
        pointsTaskJob?.cancel()
        pointsTaskJob = scope.launch {
            pointsTaskRunner.cancelled = false
            runCatching {
                state.update {
                    it.copy(
                        runningPointsTask = true,
                        pointsLogs = listOf(LogEntry("", "准备执行自动化任务", LogLevel.INFO)),
                        userAgent = userAgent,
                    )
                }
                pointsTaskRunner.run(userAgent) { line ->
                    appendPointLog(line)
                }
            }.onSuccess {
                appendPointLog("任务流程结束")
                pointsStatsStore?.let {
                    state.update { s -> s.copy(
                        totalPointsDeducted = it.getTotalDeductedAmount(),
                    )}
                }
                refreshBalance()
                val fullLog = state.value.pointsLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
                logStore?.save(fullLog)
                if (state.value.autoCleanLogsEnabled) {
                    logStore?.clearToday()
                    taskStateStore?.reset()
                }
            }.onFailure { e ->
                if (e is CancellationException) return@launch
                val errMsg = e.message ?: "未知错误"
                if (e is TaskCancelledException) {
                    appendPointLog("任务已终止")
                } else {
                    appendPointLog("任务失败：$errMsg")
                }
                val failLogContent = state.value.pointsLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
                logStore?.save(failLogContent)
            }
            state.update { it.copy(runningPointsTask = false) }
            syncTodayTaskStateFromPrefs()
        }
    }

    fun pausePointsTask() {
        state.update { it.copy(pointsTaskPaused = true) }
        appendPointLog("任务已暂停")
    }

    fun resumePointsTask() {
        state.update { it.copy(pointsTaskPaused = false) }
        appendPointLog("任务已继续")
    }

    fun stopPointsTask() {
        pointsTaskJob?.cancel()
        pointsTaskJob = null
        pointsTaskRunner.cancelled = true
        state.update { it.copy(pointsTaskPaused = false, runningPointsTask = false) }
        syncTodayTaskStateFromPrefs()
        appendPointLog("用户已结束任务")
        val fullLog = state.value.pointsLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
        logStore?.save(fullLog)
    }

    fun clearPointsLogs() {
        state.update { it.copy(pointsLogs = emptyList()) }
        syncTodayTaskStateFromPrefs()
    }

    fun syncTodayTaskStateFromPrefs() {
        val prefs = context.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        fun done(key: String): Boolean {
            val savedDate = prefs.getString("${key}_date", "") ?: ""
            return savedDate == today && prefs.getBoolean(key, false)
        }
        fun count(key: String): Int {
            val savedDate = prefs.getString("${key}_date", "") ?: ""
            return if (savedDate == today) prefs.getInt(key, 0) else 0
        }
        val app = count("app_video")
        val ali = count("alipay_video")
        val adt = count("ad_task")
        val adDone = done("ad_task_done")
        val otherDone = done("other_task_done")
        val all = done("signin_done") && app >= 20 && ali >= 50 && adt >= 10 && adDone && otherDone
        state.update {
            it.copy(
                signInDone = done("signin_done"),
                taskListDone = done("tasklist_done"),
                appVideoCount = app,
                alipayVideoCount = ali,
                adTaskCount = adt,
                adTaskDone = adDone,
                otherTaskDone = otherDone,
                todayAllDone = all,
            )
        }
    }

    fun clearAdVideoState() {
        context.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE).edit().clear().apply()
        syncTodayTaskStateFromPrefs()
    }

    private fun appendPointLog(line: String) {
        state.update { st ->
            val now = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
            val level = when {
                line.contains("✗") || line.contains("失败") || line.contains("异常") -> LogLevel.ERROR
                line.contains("✓") || line.contains("获得") || line.contains("累计") || line.contains("全部完成") -> LogLevel.SUCCESS
                line.contains("─") || line.contains("已用完") -> LogLevel.WARN
                line.contains("暂停") || line.contains("终止") -> LogLevel.WARN
                line.startsWith("▶") -> LogLevel.INFO
                else -> LogLevel.INFO
            }
            st.copy(pointsLogs = (st.pointsLogs + LogEntry(now, line, level)).takeLast(500))
        }
        syncTodayTaskStateFromPrefs()
    }
}
