package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inonvation.lightlife.ui.LogEntry
import com.inonvation.lightlife.ui.LogStyle
import com.inonvation.lightlife.ui.theme.LogColors

private const val MAX_LOG_LINES = 200

/**
 * 统一日志面板入口，根据 logStyle 切换风格
 * @param contentHeight 为空时使用 weight(1f) 自适应；非空时使用固定高度
 */
@Composable
fun LogPanel(
    logStyle: LogStyle,
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    contentHeight: androidx.compose.ui.unit.Dp? = null,
) {
    when (logStyle) {
        LogStyle.TERMINAL -> TerminalLogPanel(logs = logs, onClear = onClear, modifier = modifier, contentHeight = contentHeight)
        LogStyle.BUBBLE -> BubbleLogPanel(logs = logs, onClear = onClear, modifier = modifier, contentHeight = contentHeight)
    }
}

// ══════════════════════════════════════════════════════════════
//  气泡风格（原 LogPanelInline）
// ══════════════════════════════════════════════════════════════

@Composable
private fun BubbleLogPanel(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    contentHeight: androidx.compose.ui.unit.Dp? = null,
) {
    val listState = rememberLazyListState()
    val displayLogs = if (logs.size > MAX_LOG_LINES) logs.takeLast(MAX_LOG_LINES) else logs

    LaunchedEffect(displayLogs.size) {
        if (displayLogs.isNotEmpty()) {
            listState.animateScrollToItem(displayLogs.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
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

        Box(
            if (contentHeight != null) Modifier.fillMaxWidth().height(contentHeight)
            else Modifier.fillMaxWidth().weight(1f)
        ) {
            Surface(
                Modifier.fillMaxSize(),
                color = LogColors.background,
                shape = RoundedCornerShape(10.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (displayLogs.isEmpty()) {
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
                        items(displayLogs, key = { "${it.timestamp}_${it.id}" }) { entry ->
                            val animAlpha = remember { Animatable(0f) }
                            val animSlide = remember { Animatable(20f) }
                            LaunchedEffect(Unit) {
                                animAlpha.animateTo(1f, animationSpec = tween(300))
                                animSlide.animateTo(0f, animationSpec = tween(300))
                            }
                            val levelColor = entry.color
                            val hasPoints = Regex("\\+\\d+").containsMatchIn(entry.message)
                            if (entry.centered) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                        .graphicsLayer {
                                            alpha = animAlpha.value
                                            translationY = animSlide.value
                                        },
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        entry.message,
                                        color = LogColors.warn,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                        .graphicsLayer {
                                            alpha = animAlpha.value
                                            translationY = animSlide.value
                                        },
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = if (hasPoints) Arrangement.End else Arrangement.Start
                                ) {
                                    if (!hasPoints) {
                                        Box(
                                            Modifier
                                                .padding(top = 5.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(levelColor)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = levelColor.copy(alpha = 0.08f),
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            modifier = Modifier.widthIn(max = 280.dp)
                                        ) {
                                            Text(
                                                buildAnnotatedString {
                                                    val text = entry.message.trimStart()
                                                    val regex = Regex("\\+\\d+")
                                                    var lastIndex = 0
                                                    regex.findAll(text).forEach { match ->
                                                        append(text.substring(lastIndex, match.range.first))
                                                        pushStyle(SpanStyle(color = Color(0xFF4CAF50)))
                                                        append(match.value)
                                                        pop()
                                                        lastIndex = match.range.last + 1
                                                    }
                                                    append(text.substring(lastIndex))
                                                },
                                                color = levelColor,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF4FC3F7).copy(alpha = 0.12f),
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            modifier = Modifier.widthIn(max = 280.dp)
                                        ) {
                                            Text(
                                                buildAnnotatedString {
                                                    val text = entry.message.trimStart()
                                                    val regex = Regex("\\+\\d+")
                                                    var lastIndex = 0
                                                    regex.findAll(text).forEach { match ->
                                                        append(text.substring(lastIndex, match.range.first))
                                                        pushStyle(SpanStyle(color = Color(0xFF4CAF50)))
                                                        append(match.value)
                                                        pop()
                                                        lastIndex = match.range.last + 1
                                                    }
                                                    append(text.substring(lastIndex))
                                                },
                                                color = levelColor,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            Modifier
                                                .padding(top = 5.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4FC3F7))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  终端风格（新）
// ══════════════════════════════════════════════════════════════

@Composable
private fun TerminalLogPanel(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    contentHeight: androidx.compose.ui.unit.Dp? = null,
) {
    val listState = rememberLazyListState()
    val displayLogs = if (logs.size > MAX_LOG_LINES) logs.takeLast(MAX_LOG_LINES) else logs

    LaunchedEffect(displayLogs.size) {
        if (displayLogs.isNotEmpty()) {
            listState.animateScrollToItem(displayLogs.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题行
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

        // 日志列表
        Box(
            if (contentHeight != null) Modifier.fillMaxWidth().height(contentHeight)
            else Modifier.fillMaxWidth().weight(1f)
        ) {
            Surface(
                Modifier.fillMaxSize(),
                color = LogColors.background,
                shape = RoundedCornerShape(10.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (displayLogs.isEmpty()) {
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
                        items(displayLogs, key = { "${it.timestamp}_${it.id}" }) { entry ->
                            val animAlpha = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                animAlpha.animateTo(1f, animationSpec = tween(250))
                            }
                            val levelColor = entry.color
                            if (entry.centered) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                        .graphicsLayer { alpha = animAlpha.value },
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        entry.message,
                                        color = LogColors.warn,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                        .graphicsLayer { alpha = animAlpha.value },
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(
                                        Modifier
                                            .padding(top = 5.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(levelColor)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.widthIn(max = 320.dp)) {
                                        Row {
                                            Text(
                                                "[${entry.timestamp}]",
                                                color = LogColors.timestamp,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                buildAnnotatedString {
                                                    val text = entry.message.trimStart()
                                                    val regex = Regex("\\+\\d+")
                                                    var lastIndex = 0
                                                    regex.findAll(text).forEach { match ->
                                                        append(text.substring(lastIndex, match.range.first))
                                                        pushStyle(SpanStyle(color = Color(0xFF4CAF50)))
                                                        append(match.value)
                                                        pop()
                                                        lastIndex = match.range.last + 1
                                                    }
                                                    append(text.substring(lastIndex))
                                                },
                                                color = levelColor,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
