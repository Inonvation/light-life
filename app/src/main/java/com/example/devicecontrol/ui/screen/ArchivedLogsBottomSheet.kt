package com.example.devicecontrol.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.ui.AppUiState
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.theme.LogColors
import com.example.devicecontrol.ui.theme.Spacings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedLogsBottomSheet(state: AppUiState, vm: AppViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedIndex by remember { mutableStateOf(-1) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    // 确认清除所有日志对话框
    if (showConfirmClearDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要清除所有历史执行日志吗？此操作不可撤销。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirmClearDialog = false
                        vm.clearArchivedLogs()
                        vm.dismissArchivedLogs()
                    }
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showConfirmClearDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { vm.dismissArchivedLogs() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "历史执行日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacings.xs)) {
                    TextButton(onClick = { showConfirmClearDialog = true }) {
                        Text("清除所有", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { vm.dismissArchivedLogs() }) {
                        Text("关闭", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(Spacings.sm))

            if (state.archivedLogs.isEmpty()) {
                // Empty state
                Text(
                    "暂无历史日志",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                )
            } else {
                // Date-grouped log list
                val groupedLogs = remember(state.archivedLogs) {
                    groupLogsByDate(state.archivedLogs)
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp)
                ) {
                    groupedLogs.forEach { (dateHeader, logs) ->
                        // Sticky date header
                        item(key = "header_$dateHeader") {
                            DateSectionHeader(dateHeader)
                        }
                        // Log entries for this date
                        itemsIndexed(logs, key = { _, (name, _) -> name }) { _, (name, content) ->
                            val isExpanded = expandedIndex == state.archivedLogs.indexOfFirst { it.first == name }
                            ArchivedLogCard(
                                name = name,
                                content = content,
                                isExpanded = isExpanded,
                                onClick = {
                                    expandedIndex = if (isExpanded) -1 else state.archivedLogs.indexOfFirst { it.first == name }
                                },
                                onDelete = { vm.deleteArchivedLog(name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSectionHeader(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(Spacings.sm))
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ArchivedLogCard(
    name: String,
    content: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条执行日志吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    val logTime = remember(name) {
        try {
            val timePart = name.removePrefix("run_").removeSuffix(".txt")
            val parts = timePart.split("_")
            if (parts.size == 2) {
                "${parts[0].substring(0, 2)}-${parts[0].substring(2)} ${parts[1].substring(0, 2)}:${parts[1].substring(2, 4)}:${parts[1].substring(4)}"
            } else name
        } catch (_: Exception) { name }
    }

    val hasError = content.contains("失败") || content.contains("错误") || content.contains("异常")
    val hasSuccess = content.contains("完成") || content.contains("获得") || content.contains("成功")
    val statusColor = when {
        hasError -> LogColors.error
        hasSuccess -> LogColors.success
        else -> LogColors.info
    }

    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircleOutline,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Spacings.sm))
                Text(
                    text = logTime,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                // Delete icon button
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Expand/collapse arrow
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded content (same style as order history)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(Spacings.sm))
                    Column(
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(content))
                            android.widget.Toast.makeText(context, "日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        val lines = content.split("\n")
                        lines.forEach { line ->
                            LogLine(line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(line: String) {
    val color = when {
        line.contains("成功") || line.contains("完成") || line.contains("获得") -> LogColors.success
        line.contains("失败") || line.contains("错误") || line.contains("异常") -> LogColors.error
        else -> LogColors.info
    }

    Row(
        modifier = Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = line,
            color = color,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
private fun groupLogsByDate(logs: List<Pair<String, String>>): List<Pair<String, List<Pair<String, String>>>> {
    val today = java.text.SimpleDateFormat("MMdd", java.util.Locale.CHINA).format(java.util.Date())
    val calendar = java.util.Calendar.getInstance()
    calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterday = java.text.SimpleDateFormat("MMdd", java.util.Locale.CHINA).format(calendar.time)

    val grouped = linkedMapOf<String, MutableList<Pair<String, String>>>()

    for (log in logs) {
        val name = log.first
        val dateHeader = try {
            val timePart = name.removePrefix("run_").removeSuffix(".txt")
            val datePart = timePart.split("_").firstOrNull() ?: ""
            val mm = datePart.substring(0, 2)
            val dd = datePart.substring(2, 4)
            val mmdd = "$mm$dd"
            when (mmdd) {
                today -> "今天"
                yesterday -> "昨天"
                else -> "${mm.toInt()}月${dd.toInt()}日"
            }
        } catch (_: Exception) { "未知日期" }
        grouped.getOrPut(dateHeader) { mutableListOf() }.add(log)
    }

    return grouped.entries.map { (key, value) -> key to value.toList() }
}
