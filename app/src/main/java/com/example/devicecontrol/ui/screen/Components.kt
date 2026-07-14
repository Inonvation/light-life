package com.example.devicecontrol.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.devicecontrol.ui.DeviceTab
import com.example.devicecontrol.ui.theme.Spacings

@Composable fun PageTitle(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(Spacings.sm))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable fun DeviceRow(name: String, enabled: Boolean, onClick: () -> Unit, onAddShortcut: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = onClick), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            TextButton(onClick = { if (enabled) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAddShortcut() }}, enabled = enabled) {
                Icon(Icons.Outlined.Add, contentDescription = "添加到桌面")
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text("桌面")
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
    }
}

@Composable fun LoadingText(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TopBar(
    currentTab: DeviceTab,
    hasToken: Boolean,
    unlockStatus: String?,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val tabColor = when (currentTab) {
        DeviceTab.Control -> com.example.devicecontrol.ui.theme.AppColors.tabControl
        DeviceTab.Points -> com.example.devicecontrol.ui.theme.AppColors.tabPoints
        DeviceTab.Me -> com.example.devicecontrol.ui.theme.AppColors.tabMe
    }
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
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "topBarTitle"
            ) { tab ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = when (tab) {
                            DeviceTab.Control -> "首页"
                            DeviceTab.Points -> "积分任务"
                            DeviceTab.Me -> "我的"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(Spacings.sm))
                    Text(
                        text = when (tab) {
                            DeviceTab.Control -> unlockStatus ?: "历史设备"
                            DeviceTab.Points -> "自动化刷积分"
                            DeviceTab.Me -> if (hasToken) "已登录" else "未登录"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentTab == DeviceTab.Me && hasToken) {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLogoutClick()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "退出登录")
                    }
                }
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSettingsClick()
                }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置")
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tabColor)
        )
    }
}
