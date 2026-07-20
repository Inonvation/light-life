package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.R
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.PROJECT_URL
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.ColorTheme
import com.inonvation.lightlife.ui.theme.Spacings
import com.inonvation.lightlife.ui.theme.ThemeMode
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("NewApi")
@Composable
fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
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
                    IconButton(onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.dismissSettings() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (state.runningPointsTask) {
                        val pulse by rememberInfiniteTransition(label = "dot")
                            .animateFloat(0.3f, 1f, infiniteRepeatable(
                                tween(900), RepeatMode.Reverse
                            ), label = "dotA")
                        Spacer(Modifier.width(Spacings.sm))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                            Box(
                                Modifier.size(8.dp).alpha(pulse).background(Color(0xFF4CAF50).copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(4.dp).background(Color(0xFF4CAF50), CircleShape))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("执行中", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
                        }
                    }
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
        val scrollState = rememberScrollState()

        // 滚动到顶部/底部时触发触感反馈
        LaunchedEffect(scrollState) {
            var lastEdgeTrigger = 0L
            var started = false
            snapshotFlow { scrollState.canScrollBackward to scrollState.canScrollForward }
                .collect { pair ->
                    if (!started) { started = true; return@collect }
                    val atEdge = pair.first == false || pair.second == false
                    if (atEdge) {
                        val now = System.currentTimeMillis()
                        if (now - lastEdgeTrigger > 500) {
                            lastEdgeTrigger = now
                            if (vm.state.value.hapticEnabled)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // ── 外观 ──
            Text("外观", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主题模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("切换应用的明暗主题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色").forEach { (mode, label) ->
                            val selected = currentMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.updateThemeMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("主题配色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("更换应用的主色调", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val currentTheme = state.colorTheme
                        listOf(
                            ColorTheme.GREEN to "绿色",
                            ColorTheme.PINK to "粉色",
                            ColorTheme.YELLOW to "黄色",
                            ColorTheme.BLUE to "蓝色",
                            ColorTheme.BROWN to "棕色",
                        ).forEach { (theme, label) ->
                            val selected = currentTheme == theme
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.updateColorTheme(theme)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("超级简洁版", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("仅显示开水与刷积分功能，立即生效", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.simpleModeEnabled, onCheckedChange = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleSimpleMode() }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ── 任务 ──
            Text("任务", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(300))) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp).alpha(if (state.safeModeEnabled) 0.5f else 1f)) {
                            Text("启动自动执行任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("打开 App 时自动检测并执行未完成的积分任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Switch(
                            checked = state.autoStartTaskEnabled && !state.safeModeEnabled,
                            onCheckedChange = {
                                if (state.safeModeEnabled) {
                                    android.widget.Toast.makeText(ctx, "请先关闭保险模式", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.toggleAutoStartTask()
                                }
                            },
                            modifier = Modifier.alpha(if (state.safeModeEnabled) 0.5f else 1f),
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp).alpha(if (state.safeModeEnabled) 0.5f else 1f)) {
                            Text("后台刷积分", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("退出应用后任务仍在通知栏持续执行，需开启通知权限", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Switch(
                            checked = state.backgroundTaskEnabled && !state.safeModeEnabled,
                            onCheckedChange = {
                                if (state.safeModeEnabled) {
                                    android.widget.Toast.makeText(ctx, "请先关闭保险模式", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.toggleBackgroundTask()
                                }
                            },
                            modifier = Modifier.alpha(if (state.safeModeEnabled) 0.5f else 1f),
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp).alpha(if (state.safeModeEnabled) 0.5f else 1f)) {
                            Text("随机延迟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("执行步骤间随机等待几秒，降低风控风险，会略微增加总耗时", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Switch(
                            checked = state.randomDelayEnabled && !state.safeModeEnabled,
                            onCheckedChange = {
                                if (state.safeModeEnabled) {
                                    android.widget.Toast.makeText(ctx, "请先关闭保险模式", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.toggleRandomDelay()
                                }
                            },
                            modifier = Modifier.alpha(if (state.safeModeEnabled) 0.5f else 1f),
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    AnimatedVisibility(
                        visible = state.backgroundTaskEnabled && !state.safeModeEnabled,
                        enter = fadeIn() + slideInVertically { -it / 4 },
                        exit = fadeOut() + slideOutVertically { -it / 4 },
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.openBatteryOptimizationSettings()
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("电池优化", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("建议改为无限制，避免被系统省电策略杀掉。执行期间可能耗电加快、轻微发烫，完成后杀掉进程即可", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ── 保险模式 ──
            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("保险模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("完全禁用积分脚本，适合担心账号风控的用户", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Switch(checked = state.safeModeEnabled, onCheckedChange = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleSafeMode() }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
                    }
                    if (state.safeModeEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "保险模式开启后，积分任务页面和所有刷积分功能将隐藏，\n不会执行任何自动任务。如需使用积分功能，请在此关闭。",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ── 快捷链接 ──
            Text("快捷链接", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShapes.cardCorner,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(300))) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("首页快捷方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.quickLinksEnabled) "显示快捷链接区域，${state.quickLinks.count { it.url.isNotBlank() }}/9 已设置"
                                else "首页不显示快捷链接区域",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.quickLinksEnabled,
                            onCheckedChange = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleQuickLinksEnabled() },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                    AnimatedVisibility(
                        visible = state.quickLinksEnabled,
                        enter = fadeIn() + slideInVertically { -it / 4 },
                        exit = fadeOut() + slideOutVertically { -it / 4 },
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("管理快捷方式", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                IconButton(
                                    onClick = {
                                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.showQuickLinksSettings()
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "管理", modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ── 交互 ──
            Text("交互", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("触感反馈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("按钮和开关操作时触发振动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.hapticEnabled, onCheckedChange = { if (!state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleHaptic() }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ── 数据 ──
            Text("数据", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth().clickable { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showLogCenter() }, shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("任务记录与调试日志", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("数据备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("备份文件为 .lif 格式（JSON），恢复时会覆盖当前数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("去隐私化备份", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (state.backupPrivacySafe) "备份文件不包含登录凭证和设备信息，仅保留订单、日志和设置"
                                else "开启后备份文件无法用于免验证码登录，适合分享或存放到不安全的位置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.backupPrivacySafe,
                            onCheckedChange = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleBackupPrivacySafe() },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                exportLauncher.launch("LightLife_backup_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date()) + ".lif")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("导出备份") }
                        OutlinedButton(
                            onClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("导入恢复") }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ── 账户 ──
            Text("账户", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("我的 Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("查看当前登录凭证，可用于调试", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showCurrentToken() }) {
                            Icon(Icons.Outlined.Code, contentDescription = "查看 Token", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("设备信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("当前设备的 User-Agent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showCurrentDeviceInfo() }) {
                            Icon(Icons.Outlined.Code, contentDescription = "查看设备信息", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.hasToken) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Spacer(Modifier.height(Spacings.sm))
                        Button(
                            onClick = {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.showLogoutConfirm()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("退出登录", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ── 关于 ──
            Text("关于", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacings.sm))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShapes.cardCorner,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().clickable {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        uriHandler.openUri(PROJECT_URL)
                    }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("LightLife", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("版本 ${state.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(PROJECT_URL, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Icon(painterResource(R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp).clickable { showAccountDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("账号安全", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("如何降低被检测的风险", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp).clickable { showScriptDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("脚本提示", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("让脚本更稳定地运行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp).clickable { showExtraDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("附加说明", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("开水认证等常见问题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp).clickable { showDisclaimerDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("免责声明", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            Text("使用即代表同意以下条款", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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


    state.deviceInfoDialogText?.let { TokenDialog(token = it, title = "设备信息", onDismiss = vm::dismissCurrentDeviceInfo) }
}
