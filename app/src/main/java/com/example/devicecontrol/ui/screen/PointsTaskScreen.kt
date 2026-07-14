package com.example.devicecontrol.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.ui.AppUiState
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.theme.AppColors
import com.example.devicecontrol.ui.theme.CardShapes
import com.example.devicecontrol.ui.theme.LogColors
import com.example.devicecontrol.ui.theme.Spacings

@Composable
fun PointsTaskScreen(state: AppUiState, vm: AppViewModel) {
    val ctx = LocalContext.current; val listState = rememberLazyListState(); var logExpanded by remember { mutableStateOf(true) }; val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.pointsLogs.size) { if (state.pointsLogs.isNotEmpty() && logExpanded) listState.animateScrollToItem(state.pointsLogs.lastIndex) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        PageTitle("积分任务", "自动化刷积分"); Spacer(Modifier.height(Spacings.xl))
        Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.runningPointsTask) {
                    if (state.pointsProgress != null) {
                        val p = state.pointsProgress!!
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(p.phase, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${p.step} / ${p.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(Spacings.sm))
                        LinearProgressIndicator(progress = { (p.step.toFloat() / p.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        Text("任务启动中...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacings.sm))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                } else { Text(text = "等待任务执行", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(Spacings.md))
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("执行日志", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { logExpanded = !logExpanded }) { Text(if (logExpanded) "折叠" else "展开") }
            }
            Spacer(Modifier.height(8.dp))
            Surface(modifier = Modifier.fillMaxWidth().weight(1f), color = LogColors.background, shape = RoundedCornerShape(8.dp)) {
                if (logExpanded) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        if (state.pointsLogs.isEmpty()) { item { Text("等待执行任务...", color = Color(0xFFB8C7D1), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } }
                        else { items(state.pointsLogs) { line -> Text(text = line, color = Color(0xFFB7F7C1), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.runningPointsTask) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.pointsTaskPaused) { Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.resumePointsTask() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.resume, contentColor = AppColors.white)) { Text("继续") } }
                else { Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.pausePointsTask() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.pause, contentColor = AppColors.white)) { Text("暂停") } }
                Button(onClick = { vm.stopPointsTask() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.stop, contentColor = AppColors.white)) { Text("结束") }
                Button(onClick = { vm.clearPointsLogs() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.clear, contentColor = AppColors.white)) { Text("清除日志") }
            }
        } else {
            Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx); vm.startPointsTask(ua) }, modifier = Modifier.fillMaxWidth(), enabled = !state.runningPointsTask, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.start, contentColor = AppColors.white)) { Text(if (state.runningPointsTask) "任务执行中" else "开始执行自动化任务") }
        }
    }
}
