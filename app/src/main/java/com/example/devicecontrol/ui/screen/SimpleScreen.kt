package com.example.devicecontrol.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.devicecontrol.data.DeviceItem
import com.example.devicecontrol.ui.AppUiState
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.LogLevel
import com.example.devicecontrol.ui.UnlockFlowState
import com.example.devicecontrol.ui.theme.CardShapes
import com.example.devicecontrol.ui.theme.LogColors
import com.example.devicecontrol.ui.theme.Spacings

@Composable
fun SimpleScreen(state: AppUiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    var selectedDevice: DeviceItem? by remember { mutableStateOf(state.devices.firstOrNull()) }

    // 设备列表变化时更新默认选中
    LaunchedEffect(state.devices) {
        if (selectedDevice == null || state.devices.none { it.id == selectedDevice!!.id }) {
            selectedDevice = state.devices.firstOrNull()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LightLife", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { vm.showSettings() }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Spacings.md)
        ) {
            if (!state.hasToken) {
                item {
                    Spacer(Modifier.height(Spacings.xxl))
                    Text(
                        "请先切换到普通模式登录后再使用简洁版",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacings.sm))
                    Text(
                        "在设置中关闭简洁版即可回到普通模式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                return@LazyColumn
            }

            // === 余额卡片 ===
            item {
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
                            Text("积分余额", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { vm.refreshBalance() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "刷新余额", modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("当前积分", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${state.balance?.pointsText ?: "-"}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("可抵扣金额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${state.balance?.integralAmount?.let { "¥$it" } ?: "-"}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("剩余小票", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${state.balance?.ticketText?.let { "¥$it" } ?: "-"}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // === 开水区域 ===
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShapes.cardCorner,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("开水", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))

                        // 设备选择
                        if (state.devices.isEmpty()) {
                            Text(
                                "暂无设备，请先刷新",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { vm.refreshDevices() }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("刷新设备")
                            }
                        } else {
                            // 设备列表
                            state.devices.forEach { device ->
                                val isSelected = device.id == selectedDevice?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedDevice = device }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Devices, contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (device.goodsName.isNotBlank()) device.goodsName else "未知设备",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isSelected) {
                                        Text(
                                            "✓", color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val device = selectedDevice
                                if (device != null) vm.unlock(device)
                                else android.widget.Toast.makeText(ctx, "请先选择设备", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            enabled = state.hasToken && !state.unlocking && selectedDevice != null
                        ) {
                            Text(
                                if (state.unlocking) "正在开水…" else "开水",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 开水流状态
                        if (state.unlockFlowState !is UnlockFlowState.Idle) {
                            Spacer(Modifier.height(8.dp))
                            when (val flow = state.unlockFlowState) {
                                is UnlockFlowState.PreChecking -> {
                                    Text(
                                        "正在准备…", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                is UnlockFlowState.Working -> {
                                    Text(
                                        flow.step, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    flow.step.let {
                                        if (it.contains("设备工作") || it.contains("等待完成")) {
                                            Text(
                                                "已用时 ${state.unlockElapsedSeconds}秒",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                is UnlockFlowState.Success -> {
                                    Text(
                                        "开水成功！", style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    flow.result.integralCost.let { cost ->
                                        if (cost != "-") {
                                            Text(
                                                "消耗积分：$cost",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { vm.dismissUnlockFlow() },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("关闭") }
                                }
                                is UnlockFlowState.Failed -> {
                                    Text(
                                        "开水失败", style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        flow.message, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (flow.step != "未知") {
                                        Text(
                                            "失败步骤：${flow.step}", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (flow.suggestions.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        flow.suggestions.forEach { suggestion ->
                                            Text(
                                                "• $suggestion", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { vm.dismissUnlockFlow() },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("关闭") }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // === 刷积分区域 ===
            item {
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
                            Text("积分任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (state.pointsLogs.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { vm.clearPointsLogs() },
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) { Text("清空", style = MaterialTheme.typography.labelSmall) }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 执行日志区域
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            color = LogColors.background.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val logListState = rememberLazyListState()
                            LaunchedEffect(state.pointsLogs.size) {
                                if (state.pointsLogs.isNotEmpty()) {
                                    logListState.animateScrollToItem(state.pointsLogs.lastIndex)
                                }
                            }
                            LazyColumn(state = logListState, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                                if (state.pointsLogs.isEmpty()) {
                                    item {
                                        Text(
                                            "等待执行任务…",
                                            color = LogColors.info.copy(alpha = 0.5f),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else {
                                    items(state.pointsLogs, key = { "${it.timestamp}_${it.id}" }) { entry ->
                                        val color = when (entry.level) {
                                            LogLevel.SUCCESS -> LogColors.success
                                            LogLevel.WARN -> LogColors.warn
                                            LogLevel.ERROR -> LogColors.error
                                            else -> LogColors.info
                                        }
                                        Row {
                                            Text(
                                                "[${entry.timestamp}]", color = LogColors.info.copy(alpha = 0.6f),
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                entry.message, color = color,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }



                        Spacer(Modifier.height(10.dp))

                        // 控制按钮
                        if (state.runningPointsTask) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { if (state.pointsTaskPaused) vm.resumePointsTask() else vm.pausePointsTask() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text(if (state.pointsTaskPaused) "继续" else "暂停") }
                                OutlinedButton(
                                    onClick = { vm.stopPointsTask() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("停止") }
                            }
                        } else {
                            Button(
                                onClick = {
                                    val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                                    vm.startPointsTask(ua)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Text("开始执行自动化任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Spacings.xxl)) }
        }
    }
}
