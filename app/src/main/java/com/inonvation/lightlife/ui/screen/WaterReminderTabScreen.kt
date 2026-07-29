package com.inonvation.lightlife.ui.screen

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.data.water.ReminderMode
import com.inonvation.lightlife.data.water.ReminderTimeSlot
import com.inonvation.lightlife.data.water.WaterReminderManager
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings

/**
 * 喝水提醒 Tab 页面（主界面：圆环统计 + 快速按钮 + 热力图 + 记录列表 + 设置入口）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterReminderTabScreen(
    manager: WaterReminderManager,
    hapticEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var stats by remember { mutableStateOf(manager.getTodayStats()) }

    var showGoalDialog by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    // 热力图数据（过去6个月）
    val dailySummary = remember(stats) {
        manager.store.getRecentMonthsDailySummary(6)
    }

    // 记录喝水并刷新统计
    fun doRecord(amountMl: Int) {
        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        manager.recordDrink(amountMl)
        stats = manager.getTodayStats()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // ── 圆环进度 ──
                Text(
                    "今日喝水",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                WaterProgressRing(
                    progress = stats.progressPercent(),
                    totalText = stats.formatTotal(),
                    percentText = "${(stats.progressPercent() * 100).toInt()}% · ${stats.drinkCount}次",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(Spacings.md))

                // 目标行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "目标 ${stats.formatGoal()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showGoalDialog = true }) {
                        Text("修改", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(Spacings.md))

                // ── 快速喝水按钮 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(100, 250, 500).forEach { ml ->
                        var animating by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (animating) 0.9f else 1f,
                            animationSpec = tween(120),
                            label = "btn_scale"
                        )
                        Button(
                            onClick = {
                                animating = true
                                doRecord(ml)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                            shape = CardShapes.cardCorner
                        ) {
                            Text("💧 +${ml}ml")
                        }
                        if (animating) {
                            LaunchedEffect(animating) {
                                delay(120)
                                animating = false
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacings.xl))

                // ── 月度热力图 ──
                Text(
                    "喝水日历",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Spacings.sm))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShapes.cardCorner,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    MonthHeatMap(
                        dailySummary = dailySummary,
                        dailyGoalMl = stats.dailyGoalMl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                Spacer(Modifier.height(Spacings.md))

                // ── 今日记录列表 ──
                Text(
                    "今日记录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Spacings.sm))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShapes.cardCorner,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    val recentLogs = remember(stats) { manager.store.getRecentLogs() }
                    val todayLogs = remember(recentLogs) {
                        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        recentLogs.filter { it.timestamp >= todayStart }
                    }
                    val dateFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

                    if (todayLogs.isEmpty()) {
                        Text(
                            "今天还没有喝水记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            todayLogs.forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        dateFormat.format(java.util.Date(log.timestamp)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "+${log.amountMl}ml",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacings.md))

                // ── 提醒设置入口 ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSettingsScreen = true
                        },
                    shape = CardShapes.cardCorner,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("提醒设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "管理模式、时间点、间隔等",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(Spacings.xxl))
            }
        }

        // 提醒设置子页面（滑入覆盖）
        AnimatedVisibility(
            visible = showSettingsScreen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            WaterReminderSettingsScreen(
                manager = manager,
                hapticEnabled = hapticEnabled,
                onBack = { showSettingsScreen = false }
            )
        }
    }

    // 对话框
    GoalDialog(show = showGoalDialog, manager = manager, onDismiss = { showGoalDialog = false }, onUpdate = { stats = manager.getTodayStats() })
}

/**
 * 提醒设置子页面（从右侧滑入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaterReminderSettingsScreen(
    manager: WaterReminderManager,
    hapticEnabled: Boolean,
    onBack: () -> Unit
) {
    // 拦截系统返回键
    BackHandler { onBack() }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var reminderMode by remember { mutableStateOf(manager.getReminderMode()) }
    var timeSlots by remember { mutableStateOf(manager.getTimeSlots()) }
    var intervalMinutes by remember { mutableStateOf(manager.getIntervalMinutes()) }
    var quietTime by remember { mutableStateOf(manager.getQuietTime()) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    var showTimePicker by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf<ReminderTimeSlot?>(null) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showQuietTimeDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            timeSlots = manager.getTimeSlots()
        } else {
            showPermissionDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "返回", modifier = Modifier.graphicsLayer { rotationZ = 180f })
            }
            Text("提醒设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // 提醒模式选择
            SectionHeader("提醒模式")
            Spacer(Modifier.height(Spacings.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = reminderMode == ReminderMode.FIXED_TIME,
                    onClick = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        manager.switchMode(ReminderMode.FIXED_TIME)
                        reminderMode = ReminderMode.FIXED_TIME
                        timeSlots = manager.getTimeSlots()
                    },
                    label = { Text("定时提醒") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = reminderMode == ReminderMode.INTERVAL,
                    onClick = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        manager.switchMode(ReminderMode.INTERVAL)
                        reminderMode = ReminderMode.INTERVAL
                    },
                    label = { Text("间隔提醒") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Spacings.md))

            when (reminderMode) {
                ReminderMode.FIXED_TIME -> {
                    SectionHeader("定时提醒")
                    Spacer(Modifier.height(Spacings.sm))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShapes.cardCorner,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("点击时间可编辑，右侧开关控制提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))

                            timeSlots.forEach { slot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            editingSlot = slot
                                            showTimePicker = true
                                        }
                                    ) {
                                        Text(
                                            slot.format(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (slot.label.isNotEmpty()) {
                                            Text(
                                                slot.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            manager.removeTimeSlot(slot.id)
                                            timeSlots = manager.getTimeSlots()
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                        }
                                        Switch(
                                            checked = slot.enabled,
                                            onCheckedChange = {
                                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!manager.hasCalendarPermission()) {
                                                    calendarPermissionLauncher.launch(
                                                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                                    )
                                                    return@Switch
                                                }
                                                manager.toggleTimeSlot(slot.id, it)
                                                timeSlots = manager.getTimeSlots()
                                            },
                                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                                if (slot != timeSlots.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    editingSlot = null
                                    showTimePicker = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("添加时间点")
                            }
                        }
                    }
                }

                ReminderMode.INTERVAL -> {
                    SectionHeader("间隔提醒")
                    Spacer(Modifier.height(Spacings.sm))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShapes.cardCorner,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingItem(
                                title = "提醒间隔",
                                value = formatInterval(intervalMinutes),
                                onClick = { showIntervalDialog = true }
                            )

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))

                            SettingItem(
                                title = "免打扰时段",
                                value = "%02d:00 - %02d:00".format(quietTime.first, quietTime.second),
                                onClick = { showQuietTimeDialog = true }
                            )

                            Spacer(Modifier.height(12.dp))
                            Text(
                                "设置修改后自动生效",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // 清除所有提醒
            Button(
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showClearAllDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("清除所有提醒")
            }

            Spacer(Modifier.height(Spacings.xxl))
        }
    }

    // 清除所有提醒确认对话框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清除所有提醒") },
            text = { Text("确定要删除所有日历提醒事件吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    manager.clearAllReminders()
                    timeSlots = manager.getTimeSlots()
                    showClearAllDialog = false
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    // 时间选择器对话框
    if (showTimePicker) {
        val initialHour = editingSlot?.hour ?: 8
        val initialMinute = editingSlot?.minute ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(if (editingSlot != null) "编辑时间" else "添加时间点") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editingSlot != null) {
                        manager.updateTimeSlot(editingSlot!!.id, timePickerState.hour, timePickerState.minute)
                    } else {
                        manager.addTimeSlot(timePickerState.hour, timePickerState.minute)
                    }
                    timeSlots = manager.getTimeSlots()
                    showTimePicker = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    // 间隔设置对话框
    if (showIntervalDialog) {
        var sliderValue by remember { mutableStateOf(intervalMinutes.toFloat()) }
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("提醒间隔") },
            text = {
                Column {
                    Text("设置提醒间隔时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        formatInterval(sliderValue.toInt()),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 15f..180f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("15分钟", style = MaterialTheme.typography.labelSmall)
                        Text("3小时", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    intervalMinutes = sliderValue.toInt()
                    manager.updateInterval(intervalMinutes)
                    if (manager.hasCalendarPermission()) {
                        manager.disableIntervalReminder()
                        manager.enableIntervalReminder()
                    }
                    showIntervalDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showIntervalDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    // 免打扰时段设置对话框
    if (showQuietTimeDialog) {
        var selectingStart by remember { mutableStateOf(true) }
        val startTimeState = rememberTimePickerState(initialHour = quietTime.first, initialMinute = 0, is24Hour = true)
        val endTimeState = rememberTimePickerState(initialHour = quietTime.second, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showQuietTimeDialog = false },
            title = { Text("免打扰时段") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("设置不提醒的时间段", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(selected = selectingStart, onClick = { selectingStart = true }, label = { Text("开始 %02d:00".format(startTimeState.hour)) })
                        Spacer(Modifier.width(8.dp))
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = !selectingStart, onClick = { selectingStart = false }, label = { Text("结束 %02d:00".format(endTimeState.hour)) })
                    }
                    Spacer(Modifier.height(12.dp))
                    if (selectingStart) TimePicker(state = startTimeState) else TimePicker(state = endTimeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    quietTime = Pair(startTimeState.hour, endTimeState.hour)
                    manager.updateQuietTime(startTimeState.hour, endTimeState.hour)
                    showQuietTimeDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showQuietTimeDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    // 权限提示对话框
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要日历权限") },
            text = {
                Column {
                    Text("喝水提醒需要日历权限才能创建提醒事件。")
                    Spacer(Modifier.height(8.dp))
                    Text("请在系统设置中找到「LightLife」→ 开启「日历」权限", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.fromParts("package", context.packageName, null)
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun GoalDialog(show: Boolean, manager: WaterReminderManager, onDismiss: () -> Unit, onUpdate: () -> Unit) {
    if (!show) return
    var sliderValue by remember { mutableStateOf(manager.getDailyGoalMl().toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每日喝水目标") },
        text = {
            Column {
                Text("设置每日喝水目标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (sliderValue >= 1000) String.format("%.1fL", sliderValue / 1000) else "${sliderValue.toInt()}ml",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(value = sliderValue, onValueChange = { sliderValue = it }, valueRange = 500f..5000f, steps = 8,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("500ml", style = MaterialTheme.typography.labelSmall)
                    Text("5L", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { manager.setDailyGoalMl(sliderValue.toInt()); onUpdate(); onDismiss() }) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(8.dp)
    )
}

// ── 工具组件 ──



@Composable
private fun SettingItem(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) { Text(value) }
    }
}

private fun formatInterval(minutes: Int): String {
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins == 0) "${hours}小时" else "${hours}小时${mins}分钟"
    } else {
        "${minutes}分钟"
    }
}
