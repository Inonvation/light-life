package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.ui.DeviceTab
import com.inonvation.lightlife.ui.theme.AppColors
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings
import kotlinx.coroutines.delay

/**
 * 脉动运行指示器（绿色圆点 + 可选文字）
 */
@Composable
fun RunningIndicator(
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val pulse by rememberInfiniteTransition(label = "dot")
        .animateFloat(0.3f, 1f, infiniteRepeatable(
            tween(900), RepeatMode.Reverse
        ), label = "dotA")
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(8.dp)
                .alpha(pulse)
                .background(AppColors.runningIndicator.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(4.dp)
                    .background(AppColors.runningIndicator, CircleShape)
            )
        }
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text("执行中", style = MaterialTheme.typography.bodyMedium, color = AppColors.runningIndicator)
        }
    }
}

@Composable
fun TopBar(
    currentTab: DeviceTab,
    hasToken: Boolean,
    hapticEnabled: Boolean,
    taskRunning: Boolean,
    onSettingsClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
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
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 })
                },
                label = "topBarTitle"
            ) { tab ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = when (tab) {
                            DeviceTab.Control -> "首页"
                            DeviceTab.Points -> "积分任务"
                            DeviceTab.Water -> "喝水"
                            DeviceTab.Me -> "我的"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(Spacings.sm))
                    if (taskRunning) {
                        RunningIndicator(
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    } else {
                        Text(
                            text = when (tab) {
                                DeviceTab.Control -> "历史设备"
                                DeviceTab.Points -> "自动化刷积分"
                                DeviceTab.Water -> "定时提醒你喝水"
                                DeviceTab.Me -> if (hasToken) "已登录" else "未登录"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
            IconButton(onClick = {
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSettingsClick()
            }) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置")
            }
        }
    }
}

@Composable
fun RollingDigits(
    text: String,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val prevText = remember { mutableStateOf(text) }
    LaunchedEffect(text) { prevText.value = text }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            val prevChar = prevText.value.getOrNull(index)
            if (char.isDigit()) {
                val direction = if (
                    prevChar != null && prevChar.isDigit() &&
                    char.digitToInt() > prevChar.digitToInt()
                ) 1 else -1
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(tween(200)) { direction * it / 3 } + fadeIn(tween(150)))
                            .togetherWith(slideOutVertically(tween(200)) { -direction * it / 3 } + fadeOut(tween(150)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "Digit"
                ) { ch ->
                    Text(ch.toString(), style = style, fontWeight = fontWeight, color = color)
                }
            } else {
                Text(char.toString(), style = style, fontWeight = fontWeight, color = color)
            }
        }
    }
}

/**
 * 带滚动动画的 StatCard（数字版），使用 RollingDigits
 */
@Composable
fun RollingStatCard(
    icon: ImageVector,
    label: String,
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacings.md, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = accentColor)
            }
            Spacer(Modifier.height(Spacings.sm))
            RollingDigits(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 页面分区标题 */
@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/**
 * 通用卡片容器。
 * 默认使用全局 cardCorner、surface 背景和 1.dp 阴影，统一各页面卡片视觉。
 */
@Composable
fun StandardCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShapes.cardCorner,
    elevation: Dp = 1.dp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val cardModifier = if (onClick != null) modifier.fillMaxWidth().clickable(onClick = onClick) else modifier.fillMaxWidth()
    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            content = content
        )
    }
}

/**
 * 设置页面通用顶栏。
 * 左侧返回按钮 + 标题，支持右侧自定义操作区。
 */
@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
    haptic: HapticFeedback = LocalHapticFeedback.current,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacings.xl, vertical = Spacings.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            actions()
        }
    }
}

/**
 * 带右侧箭头的可点击行，常用于设置页面进入二级页。
 */
@Composable
fun ClickableRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
    haptic: HapticFeedback = LocalHapticFeedback.current,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "进入",
            modifier = Modifier.size(18.dp).rotate(180f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 设置页面开关行，标题 + 副标题 + Switch。
 */
@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
    haptic: HapticFeedback = LocalHapticFeedback.current,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacings.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * 下拉刷新保持最少显示时长，避免一闪而过
 * @param loading 实际的加载状态
 * @return 实际应显示的刷新状态（加载中 + 400ms 保持）
 */
@Composable
fun rememberMinRefreshDuration(loading: Boolean): Boolean {
    var forceShow by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            forceShow = true
        } else if (forceShow) {
            delay(400)
            forceShow = false
        }
    }
    return loading || forceShow
}
