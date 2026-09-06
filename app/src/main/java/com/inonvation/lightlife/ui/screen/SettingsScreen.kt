package com.inonvation.lightlife.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.theme.AppColors
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.ColorTheme
import com.inonvation.lightlife.ui.theme.Spacings
import com.inonvation.lightlife.ui.theme.ThemeMode

/**
 * 设置页：按 外观 / 喝水·胖乖生活 / 洗澡·趣智校园 / 通用 分组，
 * 账号管理跟着平台走，视觉与主页统一。
 */
@Composable
fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    val haptic = LocalHapticFeedback.current
    val currentMode = state.themeMode
    var showDisclaimerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        SettingsTopBar(
            title = "设置",
            onBack = { vm.dismissSettings() },
            hapticEnabled = state.hapticEnabled,
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .padding(horizontal = Spacings.xl)
                .size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        val scrollState = rememberScrollState()

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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ═══ 外观 ═══
            SectionLabel("外观")
            Spacer(Modifier.height(Spacings.sm))
            StandardCard {
                Column {
                    Text("主题模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("切换应用的明暗主题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
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
                    Text("主题配色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("更换应用的主色调", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
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
                    SettingSwitchRow(
                        title = "触感反馈",
                        subtitle = "按钮和开关操作时触发振动",
                        checked = state.hapticEnabled,
                        onCheckedChange = { vm.toggleHaptic() },
                        hapticEnabled = state.hapticEnabled,
                    )
                }
            }

            // ═══ 喝水 · 胖乖生活 ═══
            SectionLabel("喝水 · 胖乖生活")
            Spacer(Modifier.height(Spacings.sm))
            AccountCard(
                platform = "胖乖生活账号",
                loggedIn = state.hasToken,
                accountLine = if (state.phone.length == 11) {
                    state.phone.take(3) + "****" + state.phone.takeLast(4)
                } else {
                    state.phone
                },
                onAction = {
                    if (state.hasToken) vm.showLogoutConfirm() else vm.dismissSettings()
                },
                hapticEnabled = state.hapticEnabled,
            )
            Spacer(Modifier.height(Spacings.sm))
            StandardCard {
                Column {
                    SettingSwitchRow(
                        title = "启动时自动签到",
                        subtitle = "打开 App 时自动完成今日签到",
                        checked = state.autoSignInEnabled,
                        onCheckedChange = { vm.toggleAutoSignIn() },
                        hapticEnabled = state.hapticEnabled,
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    ClickableRow(
                        title = "我的 Token",
                        subtitle = "查看当前登录凭证，可用于调试",
                        onClick = { vm.showCurrentToken() },
                        hapticEnabled = state.hapticEnabled,
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    ClickableRow(
                        title = "设备信息",
                        subtitle = "当前客户端标识",
                        onClick = { vm.showCurrentDeviceInfo() },
                        hapticEnabled = state.hapticEnabled,
                    )
                }
            }

            // ═══ 洗澡 · 趣智校园 ═══
            SectionLabel("洗澡 · 趣智校园")
            Spacer(Modifier.height(Spacings.sm))
            AccountCard(
                platform = "趣智校园账号",
                loggedIn = state.qzxy.loggedIn,
                accountLine = listOf(state.qzxy.userName, state.qzxy.accountPhone)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                onAction = {
                    if (state.qzxy.loggedIn) {
                        vm.qzxyShowLogoutConfirm()
                    } else {
                        vm.dismissSettings()
                        vm.qzxyShowLogin()
                    }
                },
                hapticEnabled = state.hapticEnabled,
            )
            Spacer(Modifier.height(Spacings.sm))
            StandardCard {
                Column {
                    ClickableRow(
                        title = "绑定设备",
                        subtitle = state.qzxy.boundDevice?.name ?: "未绑定，去扫描或手输 MAC",
                        onClick = {
                            vm.dismissSettings()
                            if (state.qzxy.loggedIn) {
                                vm.qzxySetDevicePicker(true)
                            } else {
                                vm.qzxyShowLogin()
                            }
                        },
                        hapticEnabled = state.hapticEnabled,
                    )
                }
            }

            // ═══ 通用 ═══
            SectionLabel("通用")
            Spacer(Modifier.height(Spacings.sm))
            StandardCard {
                Column {
                    SettingSwitchRow(
                        title = "显示首页快捷方式",
                        subtitle = "在主界面显示快捷链接卡片",
                        checked = state.quickLinksEnabled,
                        onCheckedChange = { vm.toggleQuickLinksEnabled() },
                        hapticEnabled = state.hapticEnabled,
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    ClickableRow(
                        title = "管理快捷链接",
                        subtitle = "编辑链接、图标与桌面快捷方式",
                        onClick = { vm.showQuickLinksSettings() },
                        hapticEnabled = state.hapticEnabled,
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            "版本",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "LightLife v${state.appVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    AboutLink(
                        title = "免责声明",
                        subtitle = "使用即代表同意以下条款",
                        isError = true,
                        onClick = { showDisclaimerDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(Spacings.xxl))
        }
    }

    state.tokenDialogText?.let { TokenDialog(token = it, title = "我的 Token", onDismiss = vm::dismissCurrentToken) }
    state.deviceInfoDialogText?.let { TokenDialog(token = it, title = "设备信息", onDismiss = vm::dismissCurrentDeviceInfo) }

    if (showDisclaimerDialog) {
        InfoDialog(
            title = "免责声明",
            titleColor = MaterialTheme.colorScheme.error,
            subtitle = "使用即代表同意以下条款，请仔细阅读",
            content = listOf(
                "本项目为个人兴趣开发，仅供学习和测试使用。",
                "自动签到与开水功能模拟正常用户操作流程，可能违反相关平台服务条款。",
                "请自行承担账号、设备、接口变更和平台规则风险。",
                "可能面临账户积分清零、永久无法使用积分甚至封号的风险。",
                "本人概不承担因此产生的任何责任。"
            ),
            onDismiss = { showDisclaimerDialog = false }
        )
    }
}

/** 平台账号卡：显示登录状态，登录后操作为退出，未登录为去登录 */
@Composable
private fun AccountCard(
    platform: String,
    loggedIn: Boolean,
    accountLine: String,
    onAction: () -> Unit,
    hapticEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShapes.cardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacings.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(platform, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (loggedIn) "已登录" else "未登录",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (loggedIn) AppColors.runningIndicator else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loggedIn && accountLine.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    accountLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (loggedIn) "退出登录" else "去登录",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (loggedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAction()
                    }
                    .padding(vertical = 2.dp),
            )
        }
    }
}
