package com.inonvation.lightlife.ui.points

import android.content.Context
import com.inonvation.lightlife.data.PointsStatsStore
import com.inonvation.lightlife.data.PointsTaskRunner
import com.inonvation.lightlife.data.PointsTaskStateStore
import com.inonvation.lightlife.data.TaskLogStore
import com.inonvation.lightlife.service.TaskForegroundService
import com.inonvation.lightlife.service.TaskServiceState
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.LogEntry
import com.inonvation.lightlife.ui.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    private val pointsStatsStore: PointsStatsStore?,
    private val refreshBalance: suspend () -> Unit,
    private val showToast: (String) -> Unit,
) {
    private var observing = false
    private var pointsTaskJob: Job? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    fun startPointsTask(userAgent: String) {
        if (state.value.runningPointsTask) return
        state.update { it.copy(runningPointsTask = true, pointsLogs = listOf(LogEntry("", "准备执行自动化任务", LogLevel.INFO)), userAgent = userAgent) }
        taskStateStore?.setUserAgent(userAgent)
        if (state.value.backgroundTaskEnabled) {
            observeServiceState()
            TaskForegroundService.start(context, userAgent)
        } else {
            startPointsTaskDirect(userAgent)
        }
    }

    private fun startPointsTaskDirect(userAgent: String) {
        pointsTaskJob?.cancel()
        pointsTaskJob = scope.launch {
            pointsTaskRunner.cancelled = false
            runCatching {
                pointsTaskRunner.run(userAgent) { line -> appendPointLog(line) }
            }.onSuccess {
                appendPointLog("任务流程结束")
                pointsStatsStore?.let { state.update { s -> s.copy(totalPointsDeducted = it.getTotalDeductedAmount()) } }
                refreshBalance()
                saveLog()
            }.onFailure { e ->
                if (e is CancellationException) return@launch
                appendPointLog(if (e is com.inonvation.lightlife.data.TaskCancelledException) "任务已终止" else "任务失败：${e.message ?: "未知错误"}")
                saveLog()
            }
            state.update { it.copy(runningPointsTask = false) }
            syncTodayTaskStateFromPrefs()
        }
    }

    fun pausePointsTask() {
        if (state.value.backgroundTaskEnabled) TaskForegroundService.pause(context)
        TaskServiceState.update { it.copy(isPaused = true) }
        appendPointLog("任务已暂停")
    }

    fun resumePointsTask() {
        if (state.value.backgroundTaskEnabled) TaskForegroundService.resume(context)
        TaskServiceState.update { it.copy(isPaused = false) }
        appendPointLog("任务已继续")
    }

    fun stopPointsTask() {
        if (state.value.backgroundTaskEnabled) {
            TaskForegroundService.stop(context)
            TaskServiceState.reset()
        } else {
            pointsTaskJob?.cancel()
            pointsTaskJob = null
            pointsTaskRunner.cancelled = true
        }
        TaskServiceState.update { it.copy(isRunning = false, isPaused = false) }
        state.update { it.copy(runningPointsTask = false, pointsTaskPaused = false) }
        syncTodayTaskStateFromPrefs()
        appendPointLog("用户已结束任务")
        saveLog()
    }

    fun clearPointsLogs() {
        state.update { it.copy(pointsLogs = emptyList()) }
        syncTodayTaskStateFromPrefs()
    }

    private fun observeServiceState() {
        if (observing) return
        observing = true
        scope.launch {
            var startedReceived = false
            TaskServiceState.state.collect { s ->
                if (!startedReceived) { startedReceived = true; return@collect }
                if (s.logs.isNotEmpty()) {
                    val lastLog = s.logs.last()
                    val now = timeFmt.format(Date())
                    val level = resolveLogLevel(lastLog)
                    state.update { st ->
                        val existing = st.pointsLogs.map { it.message }
                        val newLogs = s.logs.filter { it !in existing }
                        if (newLogs.isEmpty()) return@update st
                        st.copy(pointsLogs = (st.pointsLogs + newLogs.map { LogEntry(now, it, level) }).takeLast(500))
                    }
                }
                if (!s.isRunning && state.value.runningPointsTask) {
                    state.update { it.copy(runningPointsTask = false, pointsTaskPaused = false) }
                    saveLog()
                    refreshBalance()
                    syncTodayTaskStateFromPrefs()
                }
                if (s.isPaused != state.value.pointsTaskPaused) {
                    state.update { it.copy(pointsTaskPaused = s.isPaused) }
                }
            }
        }
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
        val aliTask = count("alipay_video_task")
        val adt = count("ad_task")
        val adDone = done("ad_task_done")
        val otherDone = done("other_task_done")
        val homeDone = done("home_page_done")
        val appDone = done("app_video_done")
        val aliTaskDone = done("alipay_video_task_done")
        val all = done("signin_done") && app >= 20 && ali >= 50 && aliTask >= 10 && adt >= 10 && adDone && otherDone && homeDone && appDone && aliTaskDone
        state.update { it.copy(
            signInDone = done("signin_done"),
            taskListDone = done("tasklist_done"),
            appVideoCount = app,
            alipayVideoCount = ali,
            alipayVideoTaskCount = aliTask,
            adTaskCount = adt,
            adTaskDone = adDone,
            otherTaskDone = otherDone,
            homePageDone = homeDone,
            appVideoDone = appDone,
            alipayVideoTaskDone = aliTaskDone,
            todayAllDone = all
        ) }
    }

    fun clearAdVideoState() {
        context.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE).edit().clear().apply()
        syncTodayTaskStateFromPrefs()
    }

    fun isServiceRunning(): Boolean = TaskServiceState.snapshot().isRunning

    fun connectToRunningService() {
        if (!isServiceRunning()) return
        observeServiceState()
        state.update { it.copy(runningPointsTask = true) }
        val existingLogs = TaskServiceState.snapshot().logs
        if (existingLogs.isEmpty()) return
        val now = timeFmt.format(Date())
        val entries = existingLogs.map { msg -> LogEntry(now, msg, resolveLogLevel(msg)) }
        state.update { st -> st.copy(pointsLogs = (st.pointsLogs + entries).takeLast(500)) }
    }

    private fun appendPointLog(line: String) {
        state.update { st ->
            st.copy(pointsLogs = (st.pointsLogs + LogEntry(timeFmt.format(Date()), line, resolveLogLevel(line))).takeLast(500))
        }
    }

    private fun saveLog() {
        val fullLog = state.value.pointsLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
        logStore?.save(fullLog)
    }

    companion object {
        private fun resolveLogLevel(msg: String): LogLevel = when {
            msg.contains("✗") || msg.contains("失败") || msg.contains("异常") -> LogLevel.ERROR
            msg.contains("✓") || msg.contains("获得") || msg.contains("累计") || msg.contains("全部完成") -> LogLevel.SUCCESS
            msg.contains("─") || msg.contains("已用完") || msg.contains("暂停") || msg.contains("终止") -> LogLevel.WARN
            else -> LogLevel.INFO
        }
    }
}
