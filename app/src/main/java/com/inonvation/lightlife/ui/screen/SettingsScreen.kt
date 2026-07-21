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
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inonvation.lightlife.R
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.PROJECT_URL
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.ColorTheme
import com.inonvation.lightlife.ui.theme.Spacings
import com.inonvation.lightlife.ui.theme.ThemeMode

@SuppressLint("NewApi")
@Composable
fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current

    var showAccountDialog by remember { mutableStateOf(false) }
    var showScriptDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    var showExtraDialog by remember { mutableStateOf(false) }

    val currentMode = state.themeMode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        // 顶栏
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

        // 滚动触感反馈
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
            // ═══ 外观 ═══
            SectionHeader("外观")
            Spacer(Modifier.height(Spacings.sm))
            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 主题模式
                    Text("主题模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("切换应用的明暗主题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色").forEach { (mode, label) ->
                            FilterChip(
                                selected = currentMode == mode,
                                onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.updateThemeMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // 主题配色
                    Text("主题配色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("更换应用的主色调", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ColorTheme.GREEN to "绿色",
                            ColorTheme.PINK to "粉色",
                            ColorTheme.YELLOW to "黄色",
                            ColorTheme.BLUE to "蓝色",
                            ColorTheme.BROWN to "棕色",
                        ).forEach { (theme, label) ->
                            FilterChip(
                                selected = state.colorTheme == theme,
                                onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.updateColorTheme(theme) },
                                label = { Text(label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // 超级简洁版
                    SettingSwitch(
                        title = "超级简洁版",
                        subtitle = "仅显示开水与刷积分功能，立即生效",
                        checked = state.simpleModeEnabled,
                        onCheckedChange = { vm.toggleSimpleMode() },
                        hapticEnabled = state.hapticEnabled,
                        haptic = haptic
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // 触感反馈
                    SettingSwitch(
                        title = "触感反馈",
                        subtitle = "按钮和开关操作时触发振动",
                        checked = state.hapticEnabled,
                        onCheckedChange = { vm.toggleHaptic() },
                        hapticEnabled = state.hapticEnabled,
                        haptic = haptic
                    )
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ═══ 任务 ═══
            SectionHeader("任务")
            Spacer(Modifier.height(Spacings.sm))
            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 任务设置入口
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showTaskSettings() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("任务设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("自动执行、后台刷积分、定时任务等", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // 保险模式
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("保险模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("隐藏积分功能，仅保留开水接口", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
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

            // ═══ 健康 ═══
            SectionHeader("健康")
            Spacer(Modifier.height(Spacings.sm))
            Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 喝水提醒入口
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showWaterReminderSettings() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("💧 喝水提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("定时提醒你喝水，保持健康", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ═══ 快捷链接 ═══
            SectionHeader("快捷链接")
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

            // ═══ 数据 ═══
            SectionHeader("数据")
            Spacer(Modifier.height(Spacings.sm))
            Card(modifier = Modifier.fillMaxWidth().clickable { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showDataScreen() }, shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("数据管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("日志、数据备份与清除", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ═══ 账户 ═══
            SectionHeader("账户")
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
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("退出登录", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacings.md))

            // ═══ 关于 ═══
            SectionHeader("关于")
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
                    AboutLink("账号安全", "如何降低被检测的风险") { showAccountDialog = true }
                    Spacer(Modifier.height(12.dp))
                    AboutLink("脚本提示", "让脚本更稳定地运行") { showScriptDialog = true }
                    Spacer(Modifier.height(12.dp))
                    AboutLink("附加说明", "开水认证等常见问题") { showExtraDialog = true }
                    Spacer(Modifier.height(12.dp))
                    AboutLink("免责声明", "使用即代表同意以下条款", isError = true) { showDisclaimerDialog = true }
                }
            }

            Spacer(Modifier.height(Spacings.xxl))
        }
    }

    // 对话框
    if (showAccountDialog) {
        InfoDialog(
            title = "账号安全",
            subtitle = "如何保障账户安全，降低被检测的风险",
            content = listOf(
                "尽量避免多账号在同一设备、同一网络 IP 下执行刷积分任务",
                "即使在不同设备的不同账户下，也尽量避免同时执行刷积分任务",
                "即使在同一固定设备下，也尽量避免每天在同一时间段执行刷积分任务"
            ),
            onDismiss = { showAccountDialog = false }
        )
    }

    if (showScriptDialog) {
        InfoDialog(
            title = "脚本提示",
            subtitle = "让脚本更稳定地运行，减少执行失败的概率",
            content = listOf(
                "任务持续失败时，重启 APP 再试。仍失败需在官方 APP 手动观看一条广告",
                "建议在 WiFi 稳定的环境下执行任务"
            ),
            onDismiss = { showScriptDialog = false }
        )
    }

    if (showDisclaimerDialog) {
        InfoDialog(
            title = "免责声明",
            titleColor = MaterialTheme.colorScheme.error,
            subtitle = "使用即代表同意以下条款，请仔细阅读",
            content = listOf(
                "本项目为个人兴趣开发，仅供学习和测试使用。自动化积分功能模拟正常用户操作流程，可能违反相关平台服务条款。",
                "请自行承担账号、设备、接口变更和平台规则风险",
                "可能面临账户积分清零、永久无法使用积分甚至封号的风险",
                "本人概不承担因此产生的任何责任"
            ),
            onDismiss = { showDisclaimerDialog = false }
        )
    }

    if (showExtraDialog) {
        InfoDialog(
            title = "附加说明",
            subtitle = "一些你可能遇到的情况和解决方法",
            content = listOf(
                "如果遇到开水提示需要认证，要去胖乖生活里饮水中勾选使用积分选项，会弹出让你实名认证，按流程认证即可，这是平台正常的防机器人行为，不必惊慌。"
            ),
            onDismiss = { showExtraDialog = false }
        )
    }

    if (state.showScheduleInfoDialog) {
        InfoDialog(
            title = "定时任务",
            subtitle = "设定时间段后，系统会自动调度执行，建议保持APP后台运行权限以确保稳定触发",
            content = listOf(
                "系统会根据设定的时间段，每天随机选择时间自动执行积分任务",
                "可设置多个时间段，系统会在任意一个时间段内触发",
                "开启定时任务开关",
                "点击「管理时间段」添加执行时间段",
                "时间段格式为 24 小时制（如 08:00 - 12:00）",
                "建议保持 APP 后台运行权限以确保稳定触发",
                "开启保险模式后定时任务将被禁用"
            ),
            onDismiss = { vm.dismissScheduleInfoDialog() }
        )
    }

    state.deviceInfoDialogText?.let { TokenDialog(token = it, title = "设备信息", onDismiss = vm::dismissCurrentDeviceInfo) }

    // 定时任务时间段设置对话框
    if (state.showScheduleSettings) {
        ScheduleSettingsDialog(
            timeSlots = state.scheduleTimeSlots,
            onDismiss = { vm.dismissScheduleSettings() },
            onAddSlot = { slot -> vm.addScheduleTimeSlot(slot) },
            onRemoveSlot = { slot -> vm.removeScheduleTimeSlot(slot) }
        )
    }
}

// ── 辅助组件 ──

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    hapticEnabled: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = { if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onCheckedChange() },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun AboutLink(
    title: String,
    subtitle: String,
    isError: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp).clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoDialog(
    title: String,
    titleColor: Color = Color.Unspecified,
    subtitle: String,
    content: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = titleColor) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                content.forEach { item ->
                    Text("• $item", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("我知道了") } },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun ScheduleSettingsDialog(
    timeSlots: List<com.inonvation.lightlife.data.ScheduleStore.TimeSlot>,
    onDismiss: () -> Unit,
    onAddSlot: (com.inonvation.lightlife.data.ScheduleStore.TimeSlot) -> Unit,
    onRemoveSlot: (com.inonvation.lightlife.data.ScheduleStore.TimeSlot) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时任务时间段") },
        text = {
            Column {
                Text("设置多个时间段，系统每天随机选择一个时间段，在该时间段内随机选择时间执行任务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                if (timeSlots.isEmpty()) {
                    Text("暂无时间段，点击下方按钮添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    timeSlots.forEach { slot ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(slot.format(), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onRemoveSlot(slot) }) {
                                Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("添加时间段")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        shape = RoundedCornerShape(8.dp)
    )

    if (showAddDialog) {
        AddTimeSlotDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { slot ->
                onAddSlot(slot)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddTimeSlotDialog(
    onDismiss: () -> Unit,
    onConfirm: (com.inonvation.lightlife.data.ScheduleStore.TimeSlot) -> Unit
) {
    var startHour by remember { mutableStateOf(8) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(10) }
    var endMinute by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加时间段") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("开始时间", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var startText by remember { mutableStateOf("%02d:%02d".format(startHour, startMinute)) }
                        androidx.compose.material3.OutlinedTextField(
                            value = startText,
                            onValueChange = {
                                startText = it
                                val parts = it.split(":")
                                if (parts.size == 2) {
                                    startHour = parts[0].toIntOrNull() ?: startHour
                                    startMinute = parts[1].toIntOrNull() ?: startMinute
                                }
                            },
                            label = { Text("HH:mm") },
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("结束时间", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var endText by remember { mutableStateOf("%02d:%02d".format(endHour, endMinute)) }
                        androidx.compose.material3.OutlinedTextField(
                            value = endText,
                            onValueChange = {
                                endText = it
                                val parts = it.split(":")
                                if (parts.size == 2) {
                                    endHour = parts[0].toIntOrNull() ?: endHour
                                    endMinute = parts[1].toIntOrNull() ?: endMinute
                                }
                            },
                            label = { Text("HH:mm") },
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val slot = com.inonvation.lightlife.data.ScheduleStore.TimeSlot(
                        startHour = startHour.coerceIn(0, 23),
                        startMinute = startMinute.coerceIn(0, 59),
                        endHour = endHour.coerceIn(0, 23),
                        endMinute = endMinute.coerceIn(0, 59)
                    )
                    if (slot.toStartMinutes() < slot.toEndMinutes()) {
                        onConfirm(slot)
                    }
                }
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(8.dp)
    )
}
