package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.theme.LogColors
import com.inonvation.lightlife.ui.theme.Spacings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedLogsBottomSheet(state: AppUiState, vm: AppViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var expandedIndex by remember { mutableStateOf(-1) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    // 内部控制：下滑关闭时先播完退场动画再更新状态，避免两层问题
    LaunchedEffect(state.showArchivedLogs) {
        if (!state.showArchivedLogs) sheetState.hide()
    }
    if (!sheetState.isVisible && !state.showArchivedLogs) return

    if (showConfirmClearDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要清除所有任务记录吗？\n\n注意：清除记录会同时重置当天的积分任务进度（签到、视频观看等），下次执行任务时将从头开始。\n\n此操作不可撤销。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirmClearDialog = false
                        vm.clearArchivedLogs()
                        scope.launch { sheetState.hide(); vm.dismissArchivedLogs() }
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
        onDismissRequest = { scope.launch { sheetState.hide(); vm.dismissArchivedLogs() } },
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
                    "任务记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacings.xs)) {
                    if (state.archivedLogs.isNotEmpty()) {
                        TextButton(onClick = { showConfirmClearDialog = true }) {
                            Text("清除所有", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = { scope.launch { sheetState.hide(); vm.dismissArchivedLogs() } }) {
                        Text("关闭", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(Spacings.sm))

            if (state.archivedLogs.isEmpty()) {
                Text(
                    "暂无任务记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                )
            } else {
                val groupedLogs = remember(state.archivedLogs) {
                    groupLogsByDate(state.archivedLogs)
                }

                LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                    groupedLogs.forEach { (dateHeader, logs) ->
                        item(key = "header_$dateHeader") {
                            DateSectionHeader(dateHeader)
                        }
                        itemsIndexed(logs, key = { _, (name, _) -> name }) { _, (name, content) ->
                            val globalIndex = state.archivedLogs.indexOfFirst { it.first == name }
                            val isExpanded = expandedIndex == globalIndex
                            ArchivedLogCard(
                                name = name,
                                content = content,
                                isExpanded = isExpanded,
                                onClick = { expandedIndex = if (isExpanded) -1 else globalIndex },
                                onDelete = {
                                    vm.deleteArchivedLog(name)
                                    if (expandedIndex == globalIndex) expandedIndex = -1
                                    else if (expandedIndex > globalIndex) expandedIndex--
                                }
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
    val clipboard = LocalClipboardManager.current

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
        onClick = onClick,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "折叠" else "展开详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(Spacings.sm))

                    // Log content with colored lines
                    val lines = content.split("\n")
                    lines.forEach { line ->
                        LogLine(line)
                    }

                    Spacer(Modifier.height(Spacings.sm))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(content)) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("复制", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(Spacings.sm))
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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

    Text(
        text = line,
        color = color,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
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
