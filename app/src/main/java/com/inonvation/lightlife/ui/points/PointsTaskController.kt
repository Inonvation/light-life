package com.inonvation.lightlife.ui.points

import android.content.Context
import com.inonvation.lightlife.data.DebugLogStore
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
    private val debugLogStore: DebugLogStore?,
    private val pointsStatsStore: PointsStatsStore?,
    private val refreshBalance: suspend () -> Unit,
    private val showToast: (String) -> Unit,
) {
    private var observing = false
    private var pointsTaskJob: Job? = null

    /** 启动积分任务 */
    fun startPointsTask(userAgent: String) {
        if (state.value.runningPointsTask) return

        state.update {
            it.copy(
                runningPointsTask = true,
                pointsLogs = listOf(LogEntry("", "准备执行自动化任务", LogLevel.INFO)),
                userAgent = userAgent,
            )
        }
        taskStateStore?.setUserAgent(userAgent)

        if (state.value.backgroundTaskEnabled) {
            // ── 前台 Service 路径（退后台继续跑）──
            observeServiceState()
            TaskForegroundService.start(context, userAgent)
        } else {
            // ── 直接协程路径（不保活）──
            startPointsTaskDirect(userAgent)
        }
    }

    /** 直接在当前进程的协程中运行任务（不保活） */
    private fun startPointsTaskDirect(userAgent: String) {
        pointsTaskJob?.cancel()
        pointsTaskJob = scope.launch {
            pointsTaskRunner.cancelled = false
            runCatching {
                pointsTaskRunner.run(userAgent) { line ->
                    appendPointLog(line)
                }
            }.onSuccess {
                appendPointLog("任务流程结束")
                pointsStatsStore?.let {
                    state.update { s -> s.copy(totalPointsDeducted = it.getTotalDeductedAmount()) }
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
                if (e is com.inonvation.lightlife.data.TaskCancelledException) {
                    appendPointLog("任务已终止")
                } else {
                    appendPointLog("任务失败：$errMsg")
                }
                val failLog = state.value.pointsLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
                logStore?.save(failLog)
            }
            state.update { it.copy(runningPointsTask = false) }
            syncTodayTaskStateFromPrefs()
        }
    }

    fun pausePointsTask() {
        if (state.value.backgroundTaskEnabled) {
            TaskForegroundService.pause(context)
        }
        TaskServiceState.update { it.copy(isPaused = true) }
        appendPointLog("任务已暂停")
    }

    fun resumePointsTask() {
        if (state.value.backgroundTaskEnabled) {
            TaskForegroundService.resume(context)
        }
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
        val fullLog = state.value.pointsLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
        logStore?.save(fullLog)
    }

    fun clearPointsLogs() {
        state.update { it.copy(pointsLogs = emptyList()) }
        syncTodayTaskStateFromPrefs()
    }

    /** 开始观察 TaskServiceState，将 Service 状态同步到 UI */
    private fun observeServiceState() {
        if (observing) return
        observing = true
        scope.launch {
            // 跳过第一个值（TaskServiceStatus() 初始默认值，不是真实运行状态）
            var startedReceived = false
            TaskServiceState.state.collect { s ->
                if (!startedReceived) {
                    startedReceived = true
                    return@collect
                }

                // 把 Service 的日志同步到 UI
                if (s.logs.isNotEmpty()) {
                    val lastLog = s.logs.last()
                    val now = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
                    val level = when {
                        lastLog.contains("✗") || lastLog.contains("失败") || lastLog.contains("异常") -> LogLevel.ERROR
                        lastLog.contains("✓") || lastLog.contains("获得") || lastLog.contains("累计") || lastLog.contains("全部完成") -> LogLevel.SUCCESS
                        lastLog.contains("─") || lastLog.contains("已用完") -> LogLevel.WARN
                        lastLog.contains("暂停") || lastLog.contains("终止") -> LogLevel.WARN
                        else -> LogLevel.INFO
                    }
                    state.update { st ->
                        // 只在有新日志时才追加
                        val existing = st.pointsLogs.map { it.message }
                        val newLogs = s.logs.filter { it !in existing }
                        if (newLogs.isEmpty()) return@update st
                        st.copy(pointsLogs = (st.pointsLogs + newLogs.map { LogEntry(now, it, level) }).takeLast(500))
                    }
                }

                // 同步运行状态
                if (!s.isRunning && state.value.runningPointsTask) {
                    state.update { it.copy(runningPointsTask = false, pointsTaskPaused = false) }
                    // 任务完成，保存日志
                    val fullLog = state.value.pointsLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
                    logStore?.save(fullLog)
                    refreshBalance()
                    syncTodayTaskStateFromPrefs()
                    if (state.value.autoCleanLogsEnabled) {
                        logStore?.clearToday()
                        taskStateStore?.reset()
                    }
                }

                // 同步暂停状态
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

    /** 检查 Service 当前是否正在运行 */
    fun isServiceRunning(): Boolean = TaskServiceState.snapshot().isRunning

    /** 连接到已在运行的后台 Service，同步状态到 UI */
    fun connectToRunningService() {
        if (isServiceRunning()) {
            observeServiceState()
            state.update { it.copy(runningPointsTask = true) }
            // 同步已有的日志
            val existingLogs = TaskServiceState.snapshot().logs
            if (existingLogs.isNotEmpty()) {
                val now = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
                val entries = existingLogs.map { msg ->
                    val level = when {
                        msg.contains("✗") || msg.contains("失败") || msg.contains("异常") -> LogLevel.ERROR
                        msg.contains("✓") || msg.contains("获得") || msg.contains("累计") || msg.contains("全部完成") -> LogLevel.SUCCESS
                        msg.contains("─") || msg.contains("已用完") -> LogLevel.WARN
                        msg.contains("暂停") || msg.contains("终止") -> LogLevel.WARN
                        else -> LogLevel.INFO
                    }
                    LogEntry(now, msg, level)
                }
                state.update { st -> st.copy(pointsLogs = (st.pointsLogs + entries).takeLast(500)) }
            }
        }
    }

    private fun appendPointLog(line: String) {
        state.update { st ->
            val now = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
            val level = when {
                line.contains("✗") || line.contains("失败") || line.contains("异常") -> LogLevel.ERROR
                line.contains("✓") || line.contains("获得") || line.contains("累计") || line.contains("全部完成") -> LogLevel.SUCCESS
                line.contains("─") || line.contains("已用完") -> LogLevel.WARN
                line.contains("暂停") || line.contains("终止") -> LogLevel.WARN
                else -> LogLevel.INFO
            }
            st.copy(pointsLogs = (st.pointsLogs + LogEntry(now, line, level)).takeLast(500))
        }
        syncTodayTaskStateFromPrefs()
    }
}
