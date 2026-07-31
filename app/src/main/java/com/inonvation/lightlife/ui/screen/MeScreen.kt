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
import com.inonvation.lightlife.ui.theme.StatColors
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
        vm.performImportBackup(ctx, uri, scope)
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
    val isRefreshing = rememberMinRefreshDuration(state.loadingBalance)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.refreshBalance()
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
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
                    LoginCard(
                        state = state,
                        onUpdatePhone = { vm.updatePhone(it) },
                        onUpdateCode = { vm.updateCode(it) },
                        onSendCode = { vm.sendCode() },
                        onLogin = { vm.login() },
                        onImportBackup = { backupLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                        onToggleTokenLogin = { vm.toggleTokenLogin() },
                        onUpdateTokenLoginInput = { vm.updateTokenLoginInput(it) },
                        onToggleTokenLoginVisibility = { vm.toggleTokenLoginVisibility() },
                        onLoginWithToken = { vm.loginWithToken() },
                        haptic = haptic,
                    )
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
                        RollingStatCard(
                            icon = Icons.Outlined.Person,
                            label = "当前积分",
                            text = state.balance?.pointsText ?: "-",
                            accentColor = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                        RollingStatCard(
                            icon = Icons.Outlined.Money,
                            label = "可抵扣",
                            text = state.balance?.integralAmount?.let { "¥$it" } ?: "-",
                            accentColor = Color(0xFFE8A838),
                            modifier = Modifier.weight(1f)
                        )
                        RollingStatCard(
                            icon = Icons.Outlined.ConfirmationNumber,
                            label = "小票余额",
                            text = state.balance?.ticketText?.let { "¥$it" } ?: "-",
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
                    StandardCard {
                        Column {
                            Text("积分统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(Spacings.md))
                            MeStatRow(
                                icon = Icons.Outlined.Money,
                                iconColor = StatColors.waterAmount,
                                label = "累计白嫖金额",
                                value = "${state.totalPointsDeducted}"
                            )
                            Spacer(Modifier.height(Spacings.sm))
                            MeStatRow(
                                icon = Icons.Outlined.WaterDrop,
                                iconColor = StatColors.waterCount,
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
                    StandardCard(
                        onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.showOrderHistory() },
                        contentPadding = PaddingValues(horizontal = Spacings.lg, vertical = 14.dp)
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
                                    contentDescription = "订单记录",
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
                    contentDescription = label,
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
