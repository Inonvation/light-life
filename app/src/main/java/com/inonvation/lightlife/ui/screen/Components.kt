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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.ui.AppUiState
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

@Composable
fun InfoDialog(
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
fun LoginCard(
    state: AppUiState,
    onUpdatePhone: (String) -> Unit,
    onUpdateCode: (String) -> Unit,
    onSendCode: () -> Unit,
    onLogin: () -> Unit,
    onToggleTokenLogin: () -> Unit,
    onUpdateTokenLoginInput: (String) -> Unit,
    onToggleTokenLoginVisibility: () -> Unit,
    onLoginWithToken: () -> Unit,
    haptic: HapticFeedback,
) {
    StandardCard {
        Column {
            Text("登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Spacings.md))
            OutlinedTextField(
                value = state.phone,
                onValueChange = onUpdatePhone,
                label = { Text("手机号") },
                isError = state.phoneError != null,
                supportingText = state.phoneError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next
                ),
            )
            Spacer(Modifier.height(Spacings.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = onUpdateCode,
                    label = { Text("验证码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                    ),
                )
                Spacer(Modifier.width(Spacings.sm))
                Button(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendCode()
                    },
                    enabled = !state.sendingCode && state.phone.isNotBlank(),
                    shape = RoundedCornerShape(Spacings.sm),
                ) {
                    Text(if (state.sendingCode) "发送中" else "发送验证码")
                }
            }
            Spacer(Modifier.height(Spacings.md))
            Button(
                onClick = {
                    if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLogin()
                },
                enabled = !state.loggingIn && state.phone.isNotBlank() && state.code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Spacings.sm),
            ) {
                if (state.loggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(Spacings.sm))
                }
                Text("登录")
            }
            Spacer(Modifier.height(Spacings.xs))
            Text(
                "注意：手机号登录会刷新 Token，旧 Token 将失效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.loggingIn) {
                Spacer(Modifier.height(Spacings.lg))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "其他登录方式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacings.sm),
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }
                Spacer(Modifier.height(Spacings.md))
                Spacer(Modifier.height(Spacings.sm))
                OutlinedButton(
                    onClick = {
                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleTokenLogin()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacings.sm),
                ) {
                    Text("Token 登录")
                }
            }
            androidx.compose.animation.AnimatedVisibility(visible = state.showTokenLogin) {
                Column {
                    Spacer(Modifier.height(Spacings.sm))
                    Text(
                        "粘贴从软件获取的 Token 即可登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacings.sm))
                    OutlinedTextField(
                        value = state.tokenLoginInput,
                        onValueChange = onUpdateTokenLoginInput,
                        label = { Text("Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (state.tokenLoginVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = onToggleTokenLoginVisibility) {
                                Icon(
                                    if (state.tokenLoginVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (state.tokenLoginVisible) "隐藏" else "显示",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                        ),
                    )
                    Spacer(Modifier.height(Spacings.md))
                    Button(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLoginWithToken()
                        },
                        enabled = !state.tokenLoggingIn && state.tokenLoginInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Spacings.sm),
                    ) {
                        if (state.tokenLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(Spacings.sm))
                        }
                        Text("Token 登录")
                    }
                }
            }
        }
    }
}

@Composable
fun AboutLink(
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
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "进入", modifier = Modifier.size(18.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
