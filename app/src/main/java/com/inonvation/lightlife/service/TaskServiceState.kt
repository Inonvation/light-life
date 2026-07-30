package com.inonvation.lightlife.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TaskServiceStatus(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentStep: String = "",
    /** APP视频已完成次数 (0-20) */
    val appVideoProgress: Int = 0,
    /** 支付宝视频已完成次数 (0-50) */
    val alipayVideoProgress: Int = 0,
    /** 看广告已完成次数 (0-10) */
    val adTaskProgress: Int = 0,
    /** 总积分 (最后获取到的余额) */
    val totalBalance: Int? = null,
    val todayEarned: Int = 0,
    val logs: List<String> = emptyList(),
)

/** Service 与 UI 层共享的任务状态。Service 写入，ViewModel collect。 */
object TaskServiceState {
    private val _state = MutableStateFlow(TaskServiceStatus())
    val state: StateFlow<TaskServiceStatus> = _state.asStateFlow()

    fun update(transform: (TaskServiceStatus) -> TaskServiceStatus) {
        _state.update(transform)
    }

    fun snapshot(): TaskServiceStatus = _state.value

    fun reset() {
        _state.update { TaskServiceStatus() }
    }
}
