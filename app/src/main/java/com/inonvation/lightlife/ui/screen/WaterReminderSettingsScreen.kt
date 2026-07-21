package com.inonvation.lightlife.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.data.water.WaterReminderManager
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings

/**
 * 喝水提醒设置页面。
 */
@Composable
fun WaterReminderSettingsScreen(
    manager: WaterReminderManager,
    onBack: () -> Unit,
    hapticEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    var config by remember { mutableStateOf(manager.getConfig()) }
    var stats by remember { mutableStateOf(manager.getTodayStats()) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showCupSizeDialog by remember { mutableStateOf(false) }
    var showQuietTimeDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

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
            IconButton(onClick = { if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onBack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
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

            // 提醒开关
            SectionHeader("提醒设置")
            Spacer(Modifier.height(Spacings.sm))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShapes.cardCorner,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 启用开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("定时提醒你喝水", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (it) {
                                    if (manager.canScheduleExactAlarms()) {
                                        manager.enable()
                                    } else {
                                        showPermissionDialog = true
                                        return@Switch
                                    }
                                } else {
                                    manager.disable()
                                }
                                config = manager.getConfig()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    if (config.enabled) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        // 提醒间隔
                        SettingItem(
                            title = "提醒间隔",
                            value = config.formatInterval(),
                            onClick = { showIntervalDialog = true }
                        )

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        // 杯子容量
                        SettingItem(
                            title = "杯子容量",
                            value = "${config.cupSizeMl}ml",
                            onClick = { showCupSizeDialog = true }
                        )

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        // 免打扰时段
                        SettingItem(
                            title = "免打扰时段",
                            value = config.formatQuietTime(),
                            onClick = { showQuietTimeDialog = true }
                        )
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
                    Text("点击记录一次喝水", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            manager.recordDrink()
                            stats = manager.getTodayStats()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("💧 已喝水 (${config.cupSizeMl}ml)")
                    }
                }
            }

            Spacer(Modifier.height(Spacings.xxl))
        }
    }

    // 间隔设置对话框
    if (showIntervalDialog) {
        var sliderValue by remember { mutableStateOf(config.intervalMinutes.toFloat()) }
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
                        steps = 10,
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
                    manager.updateInterval(sliderValue.toInt())
                    config = manager.getConfig()
                    showIntervalDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showIntervalDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    // 杯子容量设置对话框
    if (showCupSizeDialog) {
        var sliderValue by remember { mutableStateOf(config.cupSizeMl.toFloat()) }
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
                    manager.updateCupSize(sliderValue.toInt())
                    config = manager.getConfig()
                    showCupSizeDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showCupSizeDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }

    // 免打扰时段设置对话框
    if (showQuietTimeDialog) {
        var startHour by remember { mutableStateOf(config.quietStartHour) }
        var endHour by remember { mutableStateOf(config.quietEndHour) }
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
                    manager.updateQuietTime(startHour, endHour)
                    config = manager.getConfig()
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
            title = { Text("需要权限") },
            text = {
                Text("喝水提醒需要精确闹钟权限才能正常工作。请在系统设置中允许本应用的精确闹钟权限。")
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
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
