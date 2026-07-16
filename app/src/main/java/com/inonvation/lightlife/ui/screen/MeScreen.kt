package com.inonvation.lightlife.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Money
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt

import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(state: AppUiState, vm: AppViewModel, isActive: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
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

    // 卡片进入动画状态
    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (isActive) {
            cardsVisible = false
            cardsVisible = true
        }
    }

    val refreshState = rememberPullToRefreshState()

    // 下拉刷新完毕后，图标至少停留 400ms，避免一闪而过
    var forceShowRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(state.loadingBalance) {
        if (state.loadingBalance) {
            forceShowRefresh = true
        } else if (forceShowRefresh) {
            delay(400)
            forceShowRefresh = false
        }
    }

    PullToRefreshBox(
        isRefreshing = state.loadingBalance || forceShowRefresh,
        onRefresh = {
            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.refreshBalance()
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = state.loadingBalance || forceShowRefresh,
                state = refreshState,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
        ) {
            item { Spacer(Modifier.height(Spacings.sm)) }
            item {
                AnimatedVisibility(
                    visible = !state.hasToken && cardsVisible,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400), initialOffsetY = { it / 3 })
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShapes.cardCorner,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(Spacings.md))
                            OutlinedTextField(
                                value = state.phone,
                                onValueChange = { vm.updatePhone(it) },
                                label = { Text("手机号") },
                                isError = state.phoneError != null,
                                supportingText = state.phoneError?.let { { Text(it) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next
                                ),
                            )
                            Spacer(Modifier.height(Spacings.sm))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = state.code,
                                    onValueChange = { vm.updateCode(it) },
                                    label = { Text("验证码") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                                    ),
                                )
                                Spacer(Modifier.width(Spacings.sm))
                                Button(
                                    onClick = {
                                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.sendCode()
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
                                    vm.login()
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
                                OutlinedButton(
                                    onClick = {
                                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        backupLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(Spacings.sm),
                                ) {
                                    Text("导入备份登录")
                                }
                                Text(
                                    "导入包含 Token 的备份文件，若已登录则仅导入订单",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacings.md, vertical = 2.dp),
                                )
                                Spacer(Modifier.height(Spacings.sm))
                                OutlinedButton(
                                    onClick = {
                                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.toggleTokenLogin()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(Spacings.sm),
                                ) {
                                    Text("Token 登录")
                                }
                            }
                            AnimatedVisibility(visible = state.showTokenLogin) {
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
                                        onValueChange = { vm.updateTokenLoginInput(it) },
                                        label = { Text("Token") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        visualTransformation = if (state.tokenLoginVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { vm.toggleTokenLoginVisibility() }) {
                                                Icon(
                                                    if (state.tokenLoginVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                    contentDescription = if (state.tokenLoginVisible) "隐藏" else "显示",
                                                )
                                            }
                                        },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                                        ),
                                    )
                                    Spacer(Modifier.height(Spacings.md))
                                    Button(
                                        onClick = {
                                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            vm.loginWithToken()
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
            }
            item {
                AnimatedVisibility(
                    visible = state.hasToken && cardsVisible,
                    enter = fadeIn(tween(400, delayMillis = 100)) + slideInVertically(tween(400, delayMillis = 100), initialOffsetY = { it / 3 })
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            icon = Icons.Outlined.Person,
                            label = "当前积分",
                            value = state.balance?.pointsText ?: "-",
                            accentColor = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Outlined.Money,
                            label = "可抵扣",
                            value = state.balance?.integralAmount?.let { "¥$it" } ?: "-",
                            accentColor = Color(0xFFE8A838),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Outlined.ConfirmationNumber,
                            label = "小票余额",
                            value = state.balance?.ticketText?.let { "¥$it" } ?: "-",
                            accentColor = Color(0xFF2E7DBA),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(Spacings.md)) }
            item {
                AnimatedVisibility(
                    visible = state.hasToken && cardsVisible,
                    enter = fadeIn(tween(400, delayMillis = 200)) + slideInVertically(tween(400, delayMillis = 200), initialOffsetY = { it / 3 })
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShapes.cardCorner,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(Spacings.lg)) {
                            Text("积分统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(Spacings.md))
                            MeStatRow(
                                icon = Icons.Outlined.Money,
                                iconColor = Color(0xFFE8A838),
                                label = "累计白嫖金额",
                                value = "${state.totalPointsDeducted}"
                            )
                            Spacer(Modifier.height(Spacings.sm))
                            MeStatRow(
                                icon = Icons.Outlined.WaterDrop,
                                iconColor = Color(0xFF2E7DBA),
                                label = "累计开水次数",
                                value = "${state.totalWaterCount} 次"
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Spacings.md)) }
            item {
                AnimatedVisibility(
                    visible = state.hasToken && cardsVisible,
                    enter = fadeIn(tween(400, delayMillis = 300)) + slideInVertically(tween(400, delayMillis = 300), initialOffsetY = { it / 3 })
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.showOrderHistory()
                            },
                        shape = CardShapes.cardCorner,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacings.lg, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Receipt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(Modifier.width(Spacings.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("订单记录", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (state.orderHistory.isNotEmpty()) {
                                    Text(
                                        "共 ${state.orderHistory.size} 笔",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        "暂无订单",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "查看订单",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Spacings.xxl)) }
            item {
                AnimatedVisibility(
                    visible = cardsVisible,
                    enter = fadeIn(tween(500, delayMillis = 400))
                ) {
                    Text(
                        text = "LightLife v${state.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
@Composable
private fun MeStatRow(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = iconColor
                )
            }
            Spacer(Modifier.width(Spacings.sm))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
