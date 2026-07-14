package com.example.devicecontrol.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.devicecontrol.R
import com.example.devicecontrol.ui.AppUiState
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.openProjectHome
import com.example.devicecontrol.ui.theme.CardShapes
import com.example.devicecontrol.ui.theme.Spacings
import com.example.devicecontrol.ui.theme.ThemeMode
import com.example.devicecontrol.ui.theme.ThemePreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var showAccountDialog by remember { mutableStateOf(false) }
    var showScriptDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    var showExtraDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = vm.prepareBackupJson()
            if (json.isBlank()) {
                android.widget.Toast.makeText(ctx, "备份数据为空", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            android.widget.Toast.makeText(ctx, "备份导出成功", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8)).readText()
                        }
                    }.getOrNull()
                }
                if (json.isNullOrBlank()) {
                    android.widget.Toast.makeText(ctx, "文件内容为空", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                vm.restoreFromBackupJson(json)
            } catch (e: Exception) {
                android.widget.Toast.makeText(ctx, "导入失败：" + (e.message ?: "无法读取文件"), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val themePrefs = remember { ThemePreferences(ctx) }
    val currentMode = state.themeMode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        // 自定义顶栏，与主页 TopBar 高度对齐
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.dismissSettings() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                    Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Display settings
            Text("显示", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("深色模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("切换应用的明暗主题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色").forEach { (mode, label) ->
                            val selected = currentMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.updateThemeMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // Interaction settings
            Text("交互", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("触感反馈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("按钮和开关操作时触发振动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.hapticEnabled, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleHaptic() }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // Task settings
            Text("任务", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("提示说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("点击查看各类积分任务相关提示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp).clickable { showAccountDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("账号安全", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("如何保障账户安全，降低被检测的风险", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp).clickable { showScriptDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("脚本提示", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("让脚本更稳定地运行，减少执行失败的概率", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp).clickable { showExtraDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("附加说明", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("一些你可能遇到的情况和解决方法", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp).clickable { showDisclaimerDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("免责声明", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            Text("使用即代表同意以下条款，请仔细阅读", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f))
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // Data settings
            Text("数据", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("执行日志精简", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("任务完成后自动折叠重复的成功日志", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.logCompactEnabled, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleLogCompact() }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("历史执行日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("查看过去保存的任务执行记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showArchivedLogs() }, shape = RoundedCornerShape(8.dp)) { Text("查看") }
                    }
                }
            }


            Spacer(Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("数据备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("备份文件为 .lif 格式（JSON），包含 Token、订单记录、积分统计、执行日志和设置项。恢复时会覆盖当前数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                exportLauncher.launch("LightLife_backup_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date()) + ".lif")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("导出备份") }
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                importLauncher.launch(arrayOf("application/json"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("导入恢复") }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // Account
            Text("账户", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("我的 Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("查看当前登录凭证，可用于调试", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showCurrentToken() }) {
                            Icon(Icons.Outlined.Code, contentDescription = "查看 Token", modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // About
            Text("关于", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LightLife", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("版本 ${state.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { openProjectHome(ctx) }) {
                            Icon(painterResource(R.drawable.ic_github), contentDescription = "GitHub", modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.xxl))
        }
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("账号安全") },
            text = {
                Column {
                    Text("如何保障账户安全，降低被检测的风险", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("• 尽量避免多账号在同一设备、同一网络 IP 下执行刷积分任务")
                    Spacer(Modifier.height(4.dp))
                    Text("• 即使在不同设备的不同账户下，也尽量避免同时执行刷积分任务")
                    Spacer(Modifier.height(4.dp))
                    Text("• 即使在同一固定设备下，也尽量避免每天在同一时间段执行刷积分任务")
                }
            },
            confirmButton = { TextButton(onClick = { showAccountDialog = false }) { Text("我知道了") } },
            shape = RoundedCornerShape(8.dp),
        )
    }

    if (showScriptDialog) {
        AlertDialog(
            onDismissRequest = { showScriptDialog = false },
            title = { Text("脚本提示") },
            text = {
                Column {
                    Text("让脚本更稳定地运行，减少执行失败的概率", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("• 任务持续失败时，重启 APP 再试。仍失败需在官方 APP 手动观看一条广告")
                    Spacer(Modifier.height(4.dp))
                    Text("• 建议在 WiFi 稳定的环境下执行任务")
                }
            },
            confirmButton = { TextButton(onClick = { showScriptDialog = false }) { Text("我知道了") } },
            shape = RoundedCornerShape(8.dp),
        )
    }

    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showDisclaimerDialog = false },
            title = { Text("免责声明", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text("使用即代表同意以下条款，请仔细阅读", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("本项目为个人兴趣开发，仅供学习和测试使用。自动化积分功能模拟正常用户操作流程，可能违反相关平台服务条款。")
                    Spacer(Modifier.height(4.dp))
                    Text("• 请自行承担账号、设备、接口变更和平台规则风险")
                    Text("• 可能面临账户积分清零、永久无法使用积分甚至封号的风险")
                    Text("• 本人概不承担因此产生的任何责任")
                }
            },
            confirmButton = { TextButton(onClick = { showDisclaimerDialog = false }) { Text("我知道了") } },
            shape = RoundedCornerShape(8.dp),
        )
    }

    if (showExtraDialog) {
        AlertDialog(
            onDismissRequest = { showExtraDialog = false },
            title = { Text("附加说明") },
            text = {
                Column {
                    Text("一些你可能遇到的情况和解决方法", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("如果遇到开水提示需要认证，要去胖乖生活里饮水中勾选使用积分选项，会弹出让你实名认证，按流程认证即可，这是平台正常的防机器人行为，不必惊慌。")
                }
            },
            confirmButton = { TextButton(onClick = { showExtraDialog = false }) { Text("我知道了") } },
            shape = RoundedCornerShape(8.dp),
        )
    }

    if (state.showArchivedLogs) {
        ArchivedLogsDialog(state, vm)
    }

    state.tokenDialogText?.let { TokenDialog(token = it, onDismiss = vm::dismissCurrentToken) }
}

@Composable
private fun ArchivedLogsDialog(state: AppUiState, vm: AppViewModel) {
    // 记录展开的日志索引
    var expandedIndex by remember { mutableStateOf(-1) }
    
    AlertDialog(
        onDismissRequest = { vm.dismissArchivedLogs() },
        title = { Text("历史执行日志") },
        text = {
            if (state.archivedLogs.isEmpty()) {
                Text("暂无历史日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(state.archivedLogs.size) { index ->
                        val (name, content) = state.archivedLogs[index]
                        val isExpanded = expandedIndex == index
                        
                        // 日期标题（可点击展开/折叠）
                        val logTime = remember(name) {
                            try {
                                val timePart = name.removePrefix("run_").removeSuffix(".txt") // MMdd_HHmmss
                                val parts = timePart.split("_")
                                if (parts.size == 2) "${parts[0].substring(0,2)}-${parts[0].substring(2)} ${parts[1].substring(0,2)}:${parts[1].substring(2,4)}:${parts[1].substring(4)}"
                                else name
                            } catch (_: Exception) { name }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedIndex = if (isExpanded) -1 else index }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                logTime, 
                                style = MaterialTheme.typography.labelMedium, 
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "折叠" else "展开",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // 展开时显示详细日志
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.dismissArchivedLogs() }) { Text("关闭") } },
        dismissButton = {
            TextButton(onClick = {
                vm.clearArchivedLogs()
                vm.dismissArchivedLogs()
            }) { Text("清除所有", color = MaterialTheme.colorScheme.error) }
        },
        shape = RoundedCornerShape(8.dp)
    )
}
