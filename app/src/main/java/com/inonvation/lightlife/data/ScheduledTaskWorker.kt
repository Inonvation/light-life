package com.inonvation.lightlife.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inonvation.lightlife.service.TaskForegroundService
import java.util.Calendar

/**
 * 定时任务 Worker。
 * 每天执行一次，检查当前时间是否在用户设定的时间段内。
 * 如果在时间段内，执行积分任务。
 */
class ScheduledTaskWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "scheduled_points_task"
    }
    
    override suspend fun doWork(): Result {
        val scheduleStore = ScheduleStore(applicationContext)
        
        // 检查定时功能是否启用
        if (!scheduleStore.isEnabled()) {
            return Result.success()
        }
        
        // 检查今天是否已经执行过
        val today = java.time.LocalDate.now().toString()
        if (scheduleStore.getLastExecutedDate() == today) {
            return Result.success()
        }
        
        // 获取时间段列表
        val timeSlots = scheduleStore.getTimeSlots()
        if (timeSlots.isEmpty()) {
            return Result.success()
        }
        
        // 检查当前时间是否在某个时间段内（支持跨天时间段，如 22:00-06:00）
        val currentMinutes = getCurrentMinutes()
        val activeSlot = timeSlots.find { slot ->
            val start = slot.toStartMinutes()
            val end = slot.toEndMinutes()
            if (start <= end) {
                currentMinutes >= start && currentMinutes <= end
            } else {
                // 跨天：start > end，落在 [start,24:00) 或 [00:00,end] 内
                currentMinutes >= start || currentMinutes <= end
            }
        }
        
        if (activeSlot == null) {
            // 当前不在任何时间段内，等待下次调度
            return Result.success()
        }
        
        // 执行积分任务
        return try {
            executePointsTask()
            scheduleStore.setLastExecutedDate(today)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    /** 获取当前时间的分钟数（从午夜开始） */
    private fun getCurrentMinutes(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
    
    /** 执行积分任务 */
    private suspend fun executePointsTask() {
        // 如果已有任务在跑，跳过，避免重复启动覆盖前台服务中的 runner
        if (com.inonvation.lightlife.service.TaskServiceState.snapshot().isRunning) return

        // 这里需要调用现有的任务执行逻辑
        // 由于 PointsTaskRunner 需要 Context 和 tokenProvider，
        // 我们需要通过 TaskForegroundService 来执行
        val context = applicationContext
        val tokenStore = TokenStore(context)
        val token = tokenStore.readToken()
        
        if (token.isNullOrBlank()) {
            // 未登录，跳过执行
            return
        }
        
        // 从 PointsTaskStateStore 获取保存的 UserAgent
        val taskStateStore = PointsTaskStateStore(context)
        val userAgent = taskStateStore.getUserAgent()
        if (userAgent.isBlank()) {
            // 没有保存的 UA，跳过执行
            return
        }
        
        // 启动前台服务执行任务
        TaskForegroundService.start(context, userAgent, true)
    }
}
