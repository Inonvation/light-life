package com.example.devicecontrol.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.devicecontrol.ui.LogEntry
import com.example.devicecontrol.ui.LogLevel
import com.example.devicecontrol.ui.theme.TimelineColors

private data class TaskPhase(val key: String, val label: String)

private val PHASES = listOf(
    TaskPhase("signin", "签到"),
    TaskPhase("tasklist", "任务列表"),
    TaskPhase("app_video", "APP 视频广告"),
    TaskPhase("ali_video", "支付宝视频广告"),
)

private fun phaseToIndex(phase: String): Int = when (phase) {
    "none", "start" -> 0
    "signin_done" -> 1
    "tasks_done" -> 2
    "app_videos_done" -> 3
    "ali_videos_done", "complete" -> 4
    else -> -1
}

@Composable
fun PointsTaskScreen(state: AppUiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    var logExpanded by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current
    var dialogSuppressChecked by remember { mutableStateOf(false) }
    val prefs = remember { ctx.getSharedPreferences("points_task_state", android.content.Context.MODE_PRIVATE) }
    var suppressWarning by remember { mutableStateOf(prefs.getBoolean("suppress_warning", false)) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.pointsLogs.size) {
        if (state.pointsLogs.isNotEmpty() && logExpanded) {
            listState.animateScrollToItem(state.pointsLogs.lastIndex)
        }
    }
    
    LaunchedEffect(Unit) { contentVisible = true }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Spacer(Modifier.height(Spacings.sm))

        // Log panel with enter animation
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400), initialOffsetY = { it / 4 }),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("执行日志", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacings.xs)) {
                        if (state.pointsLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.clearPointsLogs() },
                                modifier = Modifier.height(36.dp),
                            ) { Text("清空", style = MaterialTheme.typography.labelSmall) }
                        }
                        OutlinedButton(
                            onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); logExpanded = !logExpanded },
                            modifier = Modifier.height(36.dp)
                        ) { Text(if (logExpanded) "折叠" else "展开", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Spacer(Modifier.height(Spacings.sm))
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    color = LogColors.background.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        val displayLogs = if (logExpanded) state.pointsLogs else state.pointsLogs.takeLast(3)
                        if (displayLogs.isEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = LogColors.info.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "等待执行任务...",
                                        color = LogColors.info.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else {
                            items(displayLogs, key = { "${it.timestamp}_${it.hashCode()}" }) { entry ->
                                LogEntryRow(entry = entry, maxLines = if (logExpanded) Int.MAX_VALUE else 1)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        
        Spacer(Modifier.height(Spacings.md))

// Step timeline with enter animation
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(500, delayMillis = 150)) + slideInVertically(tween(500, delayMillis = 150), initialOffsetY = { it / 4 })
        ) {
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
        }

        Spacer(Modifier.height(Spacings.md))

      
// Bottom action buttons with enter animation
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(400, delayMillis = 300)) + slideInVertically(tween(400, delayMillis = 300), initialOffsetY = { it / 3 })
        ) {
            if (state.runningPointsTask) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (suppressWarning) {
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
    }

    if (state.showPointsTaskWarning) {
        var countdown by remember { mutableStateOf(5) }
        LaunchedEffect(state.showPointsTaskWarning) {
            countdown = 5
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
        }
        AlertDialog(
            onDismissRequest = { },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text("提示", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("必读！防止积分清零及封号风险！！！", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    IconButton(
                        onClick = { vm.dismissPointsTaskWarning() },
                        modifier = Modifier.offset(x = 12.dp, y = (-12).dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                    }
                }
            },
            text = {
                Column {
                    Text("执行自动化任务前请注意：", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("• 任务持续失败时，重启 APP 再试。仍失败需在官方 APP 手动观看一条广告")
                    Spacer(Modifier.height(4.dp))
                    Text("• 尽量避免多账号在同一设备、同一网络 IP 下执行刷积分任务")
                    Spacer(Modifier.height(4.dp))
                    Text("• 即使在不同设备的不同账户下，也尽量避免同时执行刷积分任务")
                    Spacer(Modifier.height(4.dp))
                    Text("• 即使在同一固定设备下，也尽量避免每天在同一时间段执行刷积分任务")
                    Spacer(Modifier.height(4.dp))
                    Text("• 建议在 WiFi 稳定的环境下执行任务")
                    Spacer(Modifier.height(12.dp))
                    Text("免责声明", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text("本项目为个人兴趣开发，仅供学习和测试使用。自动化积分功能模拟正常用户操作流程，可能违反相关平台服务条款。")
                    Spacer(Modifier.height(4.dp))
                    Text("• 请自行承担账号、设备、接口变更和平台规则风险")
                    Text("• 可能面临账户积分清零、永久无法使用积分甚至封号的风险")
                    Text("• 本人概不承担因此产生的任何责任")
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
                    TextButton(
                        onClick = {
                            if (dialogSuppressChecked) {
                                suppressWarning = true
                                prefs.edit().putBoolean("suppress_warning", true).apply()
                                android.widget.Toast.makeText(ctx, "后续不再弹出，可在设置中查看提示", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                            vm.startPointsTask(ua)
                            vm.dismissPointsTaskWarning()
                        },
                        enabled = countdown == 0
                    ) {
                        Text(if (countdown > 0) "${countdown}秒后可执行" else "开始执行")
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
            animationSpec = tween(300, easing = FastOutSlowInEasing),
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

@Composable
private fun LogEntryRow(entry: LogEntry, maxLines: Int = Int.MAX_VALUE) {
    var expanded by remember { mutableStateOf(false) }
    val levelColor = when (entry.level) {
        LogLevel.SUCCESS -> LogColors.success
        LogLevel.ERROR -> LogColors.error
        LogLevel.WARN -> LogColors.warn
        LogLevel.INFO -> LogColors.info
    }
    val levelIcon = when (entry.level) {
        LogLevel.SUCCESS -> Icons.Outlined.CheckCircleOutline
        LogLevel.ERROR -> Icons.Outlined.ErrorOutline
        LogLevel.WARN -> Icons.Outlined.Warning
        LogLevel.INFO -> Icons.Outlined.Info
    }
    val isCollapsed = entry.collapsed && !expanded
    val displayText = if (isCollapsed) {
        // Show only the label before colon, hide the detail
        val colonIndex = entry.message.indexOf('：')
        if (colonIndex > 0) entry.message.substring(0, colonIndex) else entry.message
    } else {
        entry.message
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (entry.collapsed) Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded } else Modifier
            )
            .padding(vertical = 1.5.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        if (entry.timestamp.isNotEmpty()) {
            Text(
                text = entry.timestamp,
                color = LogColors.timestamp,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 6.dp).width(52.dp)
            )
        }
        // Level icon
        Icon(
            imageVector = levelIcon,
            contentDescription = null,
            tint = levelColor,
            modifier = Modifier.size(13.dp).padding(top = 1.dp)
        )
        Spacer(Modifier.width(5.dp))
        // Message
        Text(
            text = displayText,
            color = levelColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            maxLines = if (isCollapsed) 1 else maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Expand indicator
        if (entry.collapsed) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "折叠" else "展开详情",
                tint = LogColors.timestamp,
                modifier = Modifier.size(14.dp).padding(start = 4.dp)
            )
        }
    }
}
