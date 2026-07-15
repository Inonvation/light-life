package com.example.devicecontrol.ui.screen.components

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.ui.LogEntry
import com.example.devicecontrol.ui.LogLevel
import com.example.devicecontrol.ui.theme.LogColors

@Composable
fun LogPanel(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    val panelColor = LogColors.background

    Column(modifier = modifier) {
        // 标题栏
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("执行日志", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            if (logs.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClear()
                    },
                    modifier = Modifier.height(32.dp)
                ) { Text("清空", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))

        // 日志容器
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Surface(
                Modifier.fillMaxSize(),
                color = panelColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column {
                    // 仿终端标题栏：三个小圆点
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(Color(0xFFE06C75), CircleShape))
                        Box(Modifier.size(8.dp).background(Color(0xFFE5C07B), CircleShape))
                        Box(Modifier.size(8.dp).background(Color(0xFF6FCF97), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "log",
                            style = MaterialTheme.typography.labelSmall,
                            color = LogColors.timestamp,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    // 日志列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (logs.isEmpty()) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Info, null,
                                        tint = LogColors.info.copy(alpha = 0.35f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "等待执行任务...",
                                        color = LogColors.info.copy(alpha = 0.35f),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            items(logs, key = { "${it.timestamp}_${it.id}" }) { entry ->
                                LogRow(entry)
                            }
                        }
                    }
                }
            }

            // 顶部渐变遮罩
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Brush.verticalGradient(listOf(panelColor, Color.Transparent)))
            )
            // 底部渐变遮罩
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, panelColor)))
            )
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.SUCCESS -> LogColors.success
        LogLevel.ERROR -> LogColors.error
        LogLevel.WARN -> LogColors.warn
        LogLevel.INFO -> LogColors.info
    }

    val leftBorder = when {
        entry.message.contains("✗") || entry.level == LogLevel.ERROR -> levelColor
        entry.message.contains("✓") || entry.message.startsWith("  └") -> levelColor
        entry.message.startsWith("▶") -> levelColor
        entry.level == LogLevel.SUCCESS -> levelColor
        else -> Color.Transparent
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧色条
        if (leftBorder != Color.Transparent) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(17.dp)
                    .background(leftBorder, RoundedCornerShape(1.5.dp))
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Spacer(Modifier.width(9.dp))
        }

        // 时间戳
        if (entry.timestamp.isNotEmpty()) {
            Text(
                entry.timestamp,
                color = LogColors.timestamp,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                modifier = Modifier.width(46.dp)
            )
            Spacer(Modifier.width(4.dp))
        }

        // 日志内容 — 高亮关键词
        val message = entry.message.trimStart()
        val isHighlight = entry.level == LogLevel.SUCCESS || entry.level == LogLevel.ERROR
        Text(
            message,
            color = levelColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            fontSize = if (isHighlight) 11.5.sp else 11.sp,
            fontWeight = if (isHighlight) FontWeight.Medium else FontWeight.Normal,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
