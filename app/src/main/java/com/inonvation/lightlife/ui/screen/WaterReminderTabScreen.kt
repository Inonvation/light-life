package com.inonvation.lightlife.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.data.water.ReminderMode
import com.inonvation.lightlife.data.water.ReminderTimeSlot
import com.inonvation.lightlife.data.water.WaterReminderManager
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings

/**
 * 喝水提醒 Tab 页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterReminderTabScreen(
    manager: WaterReminderManager,
    hapticEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    var reminderMode by remember { mutableStateOf(manager.getReminderMode()) }
    var timeSlots by remember { mutableStateOf(manager.getTimeSlots()) }
    var intervalMinutes by remember { mutableStateOf(manager.getIntervalMinutes()) }
    var quietTime by remember { mutableStateOf(manager.getQuietTime()) }
    var stats by remember { mutableStateOf(manager.getTodayStats()) }
    
    var showTimePicker by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf<ReminderTimeSlot?>(null) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showQuietTimeDialog by remember { mutableStateOf(false) }
    var showCupSizeDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // 日历权限请求
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
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("喝水提醒", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Box(
            modifier = Modifier
                .padding(start = 20.dp, top = 4.dp)
                .size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // 今日统计卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShapes.cardCorner,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("今日喝水", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stats.formatTotal(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "共 ${stats.drinkCount} 次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(Spacings.md))
            
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

            // 根据模式显示不同内容
            when (reminderMode) {
                ReminderMode.FIXED_TIME -> {
                    // 定时提醒
                    SectionHeader("定时提醒")
                    Spacer(Modifier.height(Spacings.sm))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShapes.cardCorner,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("预设时间点，右侧开关控制提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            
                            timeSlots.forEach { slot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
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
                    // 间隔提醒
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
                            Button(
                                onClick = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!manager.hasCalendarPermission()) {
                                        calendarPermissionLauncher.launch(
                                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                        )
                                        return@Button
                                    }
                                    // 禁用旧的，重新创建
                                    manager.disableIntervalReminder()
                                    manager.enableIntervalReminder()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("应用间隔设置")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))
            
            // 手动记录
            SectionHeader("手动记录")
            Spacer(Modifier.height(Spacings.sm))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShapes.cardCorner,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("杯子容量: ${manager.getCupSizeMl()}ml", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { showCupSizeDialog = true }) {
                            Text("修改")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            manager.recordDrink()
                            stats = manager.getTodayStats()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("💧 已喝水 (${manager.getCupSizeMl()}ml)")
                    }
                }
            }

            Spacer(Modifier.height(Spacings.xxl))
        }
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
        var startHour by remember { mutableStateOf(quietTime.first) }
        var endHour by remember { mutableStateOf(quietTime.second) }
        AlertDialog(
            onDismissRequest = { showQuietTimeDialog = false },
            title = { Text("免打扰时段") },
            text = {
                Column {
                    Text("设置不提醒的时间段", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("开始时间", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..23).forEach { hour ->
                            FilterChip(
                                selected = startHour == hour,
                                onClick = { startHour = hour },
                                label = { Text("${hour}时") }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("结束时间", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..23).forEach { hour ->
                            FilterChip(
                                selected = endHour == hour,
                                onClick = { endHour = hour },
                                label = { Text("${hour}时") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    quietTime = Pair(startHour, endHour)
                    manager.updateQuietTime(startHour, endHour)
                    showQuietTimeDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showQuietTimeDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    // 杯子容量设置对话框
    if (showCupSizeDialog) {
        var sliderValue by remember { mutableStateOf(manager.getCupSizeMl().toFloat()) }
        AlertDialog(
            onDismissRequest = { showCupSizeDialog = false },
            title = { Text("杯子容量") },
            text = {
                Column {
                    Text("设置你常用的杯子容量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${sliderValue.toInt()}ml",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 50f..500f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("50ml", style = MaterialTheme.typography.labelSmall)
                        Text("500ml", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.setCupSizeMl(sliderValue.toInt())
                    showCupSizeDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showCupSizeDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    // 权限提示对话框
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要权限") },
            text = {
                Text("喝水提醒需要日历权限才能正常工作。请在系统设置中允许本应用的日历权限。")
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("我知道了") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) {
            Text(value)
        }
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
