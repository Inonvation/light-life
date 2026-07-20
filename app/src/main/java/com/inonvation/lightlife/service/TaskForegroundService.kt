package com.inonvation.lightlife.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.inonvation.lightlife.MainActivity
import com.inonvation.lightlife.R
import com.inonvation.lightlife.data.DebugLogStore
import com.inonvation.lightlife.data.PointsTaskRunner
import com.inonvation.lightlife.data.TaskCancelledException
import com.inonvation.lightlife.data.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 在前台运行积分任务的 Service。
 * 用户划掉最近任务时自动停止 (onTaskRemoved)。
 */
class TaskForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var taskJob: Job? = null
    private var currentRunner: PointsTaskRunner? = null

    /** 通知相关 */
    private lateinit var notificationManager: NotificationManager
    private val channelId = "points_task"
    private val completeChannelId = "points_complete"
    private val notificationId = 1001
    private val completeNotificationId = 1002

    /** 自上次更新通知以来的时间，避免过于频繁更新 */
    private var lastNotifyTime = 0L
    private val notifyThrottleMs = 2000L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val userAgent = intent.getStringExtra(EXTRA_USER_AGENT) ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
                // 如果已有任务在跑，忽略重复启动
                if (taskJob?.isActive == true) return START_NOT_STICKY

                val runner = PointsTaskRunner(
                    tokenProvider = {
                        val store = TokenStore(this@TaskForegroundService)
                        store.readToken()
                    },
                    context = this,
                )
                runner.setDebugLog(DebugLogStore(this))
                runner.randomDelay = intent.getBooleanExtra(EXTRA_RANDOM_DELAY, false)
                currentRunner = runner

                TaskServiceState.update { it.copy(isRunning = true, isPaused = false, logs = emptyList()) }
                startForeground(notificationId, buildProgressNotification("准备执行…", 0, 0))

                taskJob = serviceScope.launch {
                    // 注入进度回调
                    runner.onProgress = { stage, current, total ->
                        TaskServiceState.update { s ->
                            val step = progressStepName(stage)
                            s.copy(
                                currentStep = step,
                                appVideoProgress = if (stage == "app_video") current else s.appVideoProgress,
                                alipayVideoProgress = if (stage == "alipay_video") current else s.alipayVideoProgress,
                                adTaskProgress = if (stage == "ad_task") current else s.adTaskProgress,
                            )
                        }
                        updateNotification(stage, current, total)
                    }

                    runCatching {
                        runner.run(userAgent) { line -> handleLogLine(line) }
                    }.onSuccess {
                        val snapshot = TaskServiceState.snapshot()
                        showCompleteNotification(snapshot.totalBalance, snapshot.todayEarned)
                    }.onFailure { e ->
                        if (e is TaskCancelledException) {
                            // 用户主动停止
                        } else {
                            showCompleteNotificationError(e.message ?: "任务异常终止")
                        }
                    }

                    TaskServiceState.update { it.copy(isRunning = false, currentStep = "") }
                    currentRunner = null
                    delay(2000)
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                currentRunner?.cancelled = true
                taskJob?.cancel()
                taskJob = null
                currentRunner = null
                TaskServiceState.reset()
                stopSelf()
            }

            ACTION_PAUSE -> {
                TaskServiceState.update { it.copy(isPaused = true) }
                val notification = buildProgressNotification("已暂停", 0, 0)
                notificationManager.notify(notificationId, notification)
            }

            ACTION_RESUME -> {
                TaskServiceState.update { it.copy(isPaused = false) }
            }
        }
        return START_STICKY
    }

    /** 用户划掉最近任务列表时停止 Service */
    override fun onTaskRemoved(rootIntent: Intent?) {
        currentRunner?.cancelled = true
        taskJob?.cancel()
        taskJob = null
        currentRunner = null
        TaskServiceState.reset()
        // 移除通知
        notificationManager.cancelAll()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── 日志处理 ──

    private fun handleLogLine(line: String) {
        TaskServiceState.update { s ->
            var balance = s.totalBalance
            var earned = s.todayEarned

            // 解析括号里的积分余额 "APP视频：+2（150）"
            val parenIdx = maxOf(line.lastIndexOf('（'), line.lastIndexOf('('))
            if (parenIdx > 0) {
                line.substring(parenIdx + 1).trimEnd('）', ')').toIntOrNull()?.let { balance = it }
            }

            // 解析 +N 累加
            val plusIdx = line.indexOf('+')
            if (plusIdx >= 0) {
                line.substring(plusIdx + 1).takeWhile { it.isDigit() }.toIntOrNull()?.let {
                    if (it > 0) earned = s.todayEarned + it
                }
            }

            // 解析总计行 "总计：150（今日 +10）"
            if (line.startsWith("总计：")) {
                val rest = line.removePrefix("总计：")
                rest.takeWhile { it.isDigit() }.toIntOrNull()?.let { balance = it }
                val plusInRest = rest.indexOf('+')
                if (plusInRest >= 0) {
                    rest.substring(plusInRest + 1).takeWhile { it.isDigit() }.toIntOrNull()?.let { earned = it }
                }
            }

            s.copy(logs = (s.logs + line).takeLast(500), totalBalance = balance, todayEarned = earned)
        }
    }

    // ── 通知 ──

    private fun createNotificationChannels() {
        // 低优先级 — 前台服务常驻通知
        val channel = NotificationChannel(
            channelId, "积分任务", NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "刷积分任务的后台运行通知"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        // 默认优先级 — 任务完成通知
        val completeChannel = NotificationChannel(
            completeChannelId, "积分任务完成", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "刷积分任务完成时的提醒"
        }
        notificationManager.createNotificationChannel(completeChannel)
    }

    private fun buildProgressNotification(step: String, current: Int, total: Int): Notification {
        val contentText = if (total > 0) "$step（$current/$total）" else step
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在刷积分")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(stage: String, current: Int, total: Int) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyTime < notifyThrottleMs) return
        lastNotifyTime = now

        val step = progressStepName(stage)
        val notification = buildProgressNotification(step, current, total)
        notificationManager.notify(notificationId, notification)
    }

    /** 任务完成通知（持久） */
    private fun showCompleteNotification(balance: Int?, earned: Int?) {
        val text = buildString {
            append("积分任务已完成 ✓")
            if (earned != null && earned > 0) append("（今日 +$earned）")
            if (balance != null) append(" 余额：$balance")
        }
        // 更新前台通知
        val progressNotify = NotificationCompat.Builder(this, channelId)
            .setContentTitle("全部完成 ✓")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(false)
            .setSilent(true)
            .build()
        notificationManager.notify(notificationId, progressNotify)

        // 持久完成通知
        val completeNotify = NotificationCompat.Builder(this, completeChannelId)
            .setContentTitle("全部完成")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1,
                    Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        notificationManager.notify(completeNotificationId, completeNotify)
    }

    private fun showCompleteNotificationError(errorMsg: String) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("任务异常终止")
            .setContentText(errorMsg)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(false)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val ACTION_START = "com.inonvation.lightlife.action.START_TASK"
        const val ACTION_STOP = "com.inonvation.lightlife.action.STOP_TASK"
        const val ACTION_PAUSE = "com.inonvation.lightlife.action.PAUSE_TASK"
        const val ACTION_RESUME = "com.inonvation.lightlife.action.RESUME_TASK"
        const val EXTRA_USER_AGENT = "user_agent"
        const val EXTRA_RANDOM_DELAY = "random_delay"

        fun start(context: Context, userAgent: String, randomDelay: Boolean = false) {
            val intent = Intent(context, TaskForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_USER_AGENT, userAgent)
                putExtra(EXTRA_RANDOM_DELAY, randomDelay)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TaskForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, TaskForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, TaskForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
    }

    private fun progressStepName(stage: String): String = when (stage) {
        "signin" -> "签到"
        "app_video" -> "APP视频"
        "alipay_video" -> "支付宝视频"
        "ad_task" -> "看广告"
        "task_list" -> "任务列表"
        "home_page" -> "首页浏览"
        else -> stage
    }
}
