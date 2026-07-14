package com.example.devicecontrol.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.ui.AppUiState
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.theme.AppColors
import com.example.devicecontrol.ui.theme.CardShapes
import com.example.devicecontrol.ui.theme.LogColors
import com.example.devicecontrol.ui.theme.Spacings
import com.example.devicecontrol.ui.theme.TimelineColors

private data class TaskPhase(val key: String, val label: String)

private val PHASES = listOf(
    TaskPhase("signin", "签到"),
    TaskPhase("browse", "浏览任务"),
    TaskPhase("tasklist", "任务列表"),
    TaskPhase("app_video", "APP 视频广告"),
    TaskPhase("ali_video", "支付宝视频广告"),
)

private fun phaseToIndex(phase: String): Int = when (phase) {
    "none", "start" -> 0
    "signin_done" -> 1
    "browse_done" -> 2
    "tasks_done" -> 3
    "app_videos_done" -> 4
    "ali_videos_done", "complete" -> 5
    else -> -1
}

@Composable
fun PointsTaskScreen(state: AppUiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    var logExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var dialogSuppressChecked by remember { mutableStateOf(state.suppressPointsTaskWarning) }

    LaunchedEffect(state.pointsLogs.size) {
        if (state.pointsLogs.isNotEmpty() && logExpanded) {
            listState.animateScrollToItem(state.pointsLogs.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        PageTitle("积分任务", "自动化刷积分")
        Spacer(Modifier.height(Spacings.xl))

        // Log panel
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("执行日志", style = MaterialTheme.typography.labelLarge)
                Row {
                    if (state.pointsLogs.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.clearPointsLogs() },
                            modifier = Modifier.height(28.dp),
                        ) { Text("清空", style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.width(4.dp))
                    }
                    OutlinedButton(
                        onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); logExpanded = !logExpanded },
                        modifier = Modifier.height(28.dp)
                    ) { Text(if (logExpanded) "折叠" else "展开", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = LogColors.background.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    val displayLogs = if (logExpanded) state.pointsLogs else state.pointsLogs.takeLast(3)
                    if (displayLogs.isEmpty()) {
                        item {
                            Text(
                                text = "等待执行任务...",
                                color = LogColors.info,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.alpha(0.6f)
                            )
                        }
                    } else {
                        items(displayLogs) { line ->
                            Text(
                                text = line,
                                color = LogColors.default,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                maxLines = if (logExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        
        Spacer(Modifier.height(Spacings.md))

// Step timeline
        val currentPhaseIndex = state.pointsProgress?.let { phaseToIndex(it.phase) } ?: if (state.runningPointsTask) 0 else -1
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShapes.cardCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                PHASES.forEachIndexed { index, phase ->
                    val status = when {
                        currentPhaseIndex < 0 -> "pending"
                        index < currentPhaseIndex -> "completed"
                        index == currentPhaseIndex && state.runningPointsTask -> "active"
                        index == currentPhaseIndex && !state.runningPointsTask -> "pending"
                        else -> "pending"
                    }
                    PhaseRow(phase = phase, status = status, progress = if (status == "active") state.pointsProgress else null)
                    if (index < PHASES.lastIndex) {
                        val lineColor by animateColorAsState(
                            targetValue = when (status) {
                                "completed" -> TimelineColors.completed
                                else -> TimelineColors.pending
                            },
                            label = "lineColor"
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 11.dp)
                                .width(2.dp)
                                .height(12.dp)
                                .background(lineColor, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacings.md))

      
// Bottom action buttons
        if (state.runningPointsTask) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (state.pointsTaskPaused) vm.resumePointsTask() else vm.pausePointsTask()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.pointsTaskPaused) AppColors.resume else AppColors.pause,
                        contentColor = AppColors.white
                    )
                ) {
                    Text(if (state.pointsTaskPaused) "继续" else "暂停")
                }
                OutlinedButton(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.stopPointsTask()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.stop)
                ) {
                    Text("结束")
                }
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (state.suppressPointsTaskWarning) {
                        val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                        vm.startPointsTask(ua)
                    } else {
                        vm.showPointsTaskWarning()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.runningPointsTask,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.start, contentColor = AppColors.white)
            ) {
                Text("开始执行自动化任务")
            }
        }
    }

    if (state.showPointsTaskWarning) {
        AlertDialog(
            onDismissRequest = { vm.dismissPointsTaskWarning() },
            title = { Text("提示（必读！防止积分清零及封号风险！！！）") },
            text = {
                Column {
                    Text("执行自动化任务前请注意：")
                    Spacer(Modifier.height(8.dp))
                    Text("• 任务持续失败时，重启 APP 再试。仍失败需在官方 APP 手动观看一条广告")
                    Spacer(Modifier.height(4.dp))
                    Text("• 尽量避免多账号在同一设备、同一网络 IP 下执行刷积分任务")
                    Spacer(Modifier.height(4.dp))
                    Text("• 即使在不同设备的不同账户下，也尽量避免同时执行刷积分任务")
                    Spacer(Modifier.height(4.dp))
                    Text("• 建议在 WiFi 稳定的环境下执行任务")
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = dialogSuppressChecked,
                            onCheckedChange = { dialogSuppressChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = AppColors.start)
                        )
                        Text("不再提示", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        if (dialogSuppressChecked != state.suppressPointsTaskWarning) {
                            vm.toggleSuppressPointsTaskWarning()
                        }
                        if (dialogSuppressChecked) {
                            android.widget.Toast.makeText(ctx, "已关闭任务前提示，后续可在设置中重新开启", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                        vm.startPointsTask(ua)
                        vm.dismissPointsTaskWarning()
                    }) {
                        Text("开始执行")
                    }
                }
            },
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun PhaseRow(phase: TaskPhase, status: String, progress: com.example.devicecontrol.ui.PointsProgress?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val indicatorColor by animateColorAsState(
            targetValue = when (status) {
                "completed" -> TimelineColors.completed
                "active" -> TimelineColors.active
                else -> TimelineColors.pending
            },
            label = "indicatorColor"
        )
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "pulseAlpha"
        )

        Box(
            modifier = Modifier
                .size(24.dp)
                .then(if (status == "active") Modifier.background(TimelineColors.activeSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (status == "completed") {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = TimelineColors.completed,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(indicatorColor, CircleShape)
                        .then(if (status == "active") Modifier.alpha(pulseAlpha) else Modifier)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = phase.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (status == "active") FontWeight.SemiBold else FontWeight.Normal,
            color = when (status) {
                "completed" -> MaterialTheme.colorScheme.onSurface
                "active" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )

        if (status == "active" && progress != null) {
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}