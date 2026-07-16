package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.LogLevel
import com.inonvation.lightlife.ui.theme.AppColors
import com.inonvation.lightlife.ui.theme.LogColors
import kotlinx.coroutines.delay

@Composable
fun PointsTaskScreen(state: AppUiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var dialogSuppressChecked by remember { mutableStateOf(false) }
    val prefs = remember { ctx.getSharedPreferences("points_task_state", android.content.Context.MODE_PRIVATE) }
    var suppressWarning by remember { mutableStateOf(prefs.getBoolean("suppress_warning", false)) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { contentVisible = true }

    // 读取本地任务状态用于展示
    LaunchedEffect(state.runningPointsTask, state.pointsLogs.size) {
        vm.syncTodayTaskStateFromPrefs()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400), initialOffsetY = { -it / 4 })
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("积分任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (state.runningPointsTask) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val pa by androidx.compose.animation.core.rememberInfiniteTransition(label = "dot")
                                    .animateFloat(0.3f, 1f, androidx.compose.animation.core.infiniteRepeatable(
                                        androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse
                                    ), label = "dotA")
                                Box(
                                    Modifier.size(8.dp).alpha(pa).background(Color(0xFF4CAF50).copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(4.dp).background(Color(0xFF4CAF50), CircleShape))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("执行中", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                            }
                        } else {
                            val lastLog = state.pointsLogs.findLast {
                                it.message.startsWith("总积分：") || it.message.startsWith("任务前积分：")
                            }?.message
                            if (lastLog != null) {
                                Text(lastLog, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (!state.runningPointsTask && state.pointsLogs.isNotEmpty()) {
                        val summary = state.pointsLogs.findLast {
                            it.message.startsWith("所有任务均已完成") || it.message.startsWith("任务失败") || it.message.startsWith("任务已终止")
                        }?.message
                        if (summary != null) {
                            // 有 405 时不再重复显示原始错误文本，友好提示会代替
                            val has405 = state.pointsLogs.any { it.level == LogLevel.ERROR && it.message.contains("405") }
                            if (!has405) {
                                Spacer(Modifier.height(4.dp))
                                val color = when {
                                    summary.contains("已完成") -> Color(0xFF4CAF50)
                                    summary.contains("失败") || summary.contains("终止") -> Color(0xFFE6A817)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(summary, style = MaterialTheme.typography.bodySmall, color = color)
                            }
                        }
                    }
                    // 错误提示
                    if (!state.runningPointsTask && state.pointsLogs.any { it.level == LogLevel.ERROR && it.message.contains("405") }) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "服务器接口暂时不可用（HTTP 405），可能是临时波动或被风控限制。\n请稍后再试，无需反复重试。",
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    // 今日任务状态
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusTag("签到", state.signInDone)
                        StatusTag("看广告 ${state.adTaskCount}/10", state.adTaskDone)
                        StatusTag("APP视频", state.appVideoCount >= 20)
                        StatusTag("其他", state.otherTaskDone)
                        StatusTag("支付宝 ${state.alipayVideoCount}/50", state.alipayVideoCount >= 50)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(400, delayMillis = 200)) + slideInVertically(tween(400, delayMillis = 200), initialOffsetY = { it / 4 }),
            modifier = Modifier.weight(1f)
        ) {
            LogPanelInline(
                logs = state.pointsLogs,
                onClear = { vm.clearPointsLogs() },
            )
        }

        Spacer(Modifier.height(14.dp))

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(350, delayMillis = 300)) + slideInVertically(tween(350, delayMillis = 300), initialOffsetY = { it / 3 })
        ) {
            if (state.runningPointsTask) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Button(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.pointsTaskPaused) vm.resumePointsTask() else vm.pausePointsTask()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text(if (state.pointsTaskPaused) "继续" else "暂停") }
                    OutlinedButton(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.stopPointsTask()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("停止") }
                }
            } else {
                androidx.compose.material3.Button(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (suppressWarning) {
                            val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                            vm.startPointsTask(ua)
                        } else vm.showPointsTaskWarning()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = AppColors.start,
                        contentColor = AppColors.white
                    )
                ) {
                    Text("开始执行自动化任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (state.showPointsTaskWarning) {
        WarningDialog(
            onDismiss = { vm.dismissPointsTaskWarning() },
            onConfirm = {
                if (dialogSuppressChecked) {
                    suppressWarning = true
                    prefs.edit().putBoolean("suppress_warning", true).apply()
                    android.widget.Toast.makeText(ctx, "后续不再弹出，可在设置中查看提示", android.widget.Toast.LENGTH_SHORT).show()
                }
                val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                vm.startPointsTask(ua)
                vm.dismissPointsTaskWarning()
            },
            suppressChecked = dialogSuppressChecked,
            onSuppressChange = { dialogSuppressChecked = it },
        )
    }
}

@Composable
private fun LogPanelInline(
    logs: List<com.inonvation.lightlife.ui.LogEntry>,
    onClear: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("执行日志", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            if (logs.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.height(32.dp)
                ) { Text("清空", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Surface(
                Modifier.fillMaxSize(),
                color = LogColors.background,
                shape = RoundedCornerShape(10.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (logs.isEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "等待执行任务...",
                                    color = LogColors.info.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        items(logs, key = { "${it.timestamp}_${it.id}" }) { entry ->
                            val color = when (entry.level) {
                                LogLevel.SUCCESS -> LogColors.success
                                LogLevel.WARN -> LogColors.warn
                                LogLevel.ERROR -> LogColors.error
                                else -> LogColors.info
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                if (entry.timestamp.isNotEmpty()) {
                                    Text(
                                        entry.timestamp, color = LogColors.timestamp,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                                        modifier = Modifier.width(46.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    entry.message.trimStart(), color = color,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall, fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun StatusTag(label: String, done: Boolean) {
    val bgColor = if (done) Color(0xFF4CAF50).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor = if (done) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = if (done) "✓" else "○"
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = Modifier.height(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(icon, fontSize = 10.sp, color = textColor, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 10.sp, color = textColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    suppressChecked: Boolean,
    onSuppressChange: (Boolean) -> Unit,
) {
    var countdown by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        countdown = 5
        while (countdown > 0) { delay(1000); countdown-- }
    }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("提示", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("必读！防止积分清零及封号风险", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.offset(x = 12.dp, y = (-12).dp)) {
                    Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column {
                Text("执行自动化任务前请注意：", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
                Text("• 任务持续失败时，重启 APP 再试。仍失败需在官方 APP 手动观看一条广告"); Spacer(Modifier.height(4.dp))
                Text("• 尽量避免多账号在同一设备、同一网络 IP 下执行刷积分任务"); Spacer(Modifier.height(4.dp))
                Text("• 即使在不同设备的不同账户下，也尽量避免同时执行刷积分任务"); Spacer(Modifier.height(4.dp))
                Text("• 即使在同一固定设备下，也尽量避免每天在同一时间段执行刷积分任务"); Spacer(Modifier.height(4.dp))
                Text("• 建议在 WiFi 稳定的环境下执行任务"); Spacer(Modifier.height(12.dp))
                Text("免责声明", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(4.dp))
                Text("本项目为个人兴趣开发，仅供学习和测试使用。自动化积分功能模拟正常用户操作流程，可能违反相关平台服务条款。"); Spacer(Modifier.height(4.dp))
                Text("• 请自行承担账号、设备、接口变更和平台规则风险")
                Text("• 可能面临账户积分清零、永久无法使用积分甚至封号的风险")
                Text("• 本人概不承担因此产生的任何责任")
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = suppressChecked,
                        onCheckedChange = onSuppressChange,
                        colors = CheckboxDefaults.colors(checkedColor = AppColors.start)
                    )
                    Text("不再提示", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onConfirm, enabled = countdown == 0) {
                    Text(if (countdown > 0) "${countdown}秒后可执行" else "开始执行")
                }
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}
