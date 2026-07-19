package com.inonvation.lightlife.ui.screen

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.data.DEFAULT_QUICK_LINKS
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.theme.CardShapes
import com.inonvation.lightlife.ui.theme.Spacings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun QuickLinksSettingsScreen(state: AppUiState, vm: AppViewModel) {
    val haptic = LocalHapticFeedback.current
    // 动态显示数量：初始为最后有内容的槽位+1，最少3个，最多9个
    val lastUsedIndex = state.quickLinks.indexOfLast { it.url.isNotBlank() || it.name.isNotBlank() }
    val initialDisplay = maxOf(3, (lastUsedIndex + 2).coerceAtLeast(3))
    var displayCount by remember { mutableIntStateOf(initialDisplay.coerceAtMost(9)) }

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
                    IconButton(onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.dismissQuickLinksSettings() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("快捷链接", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // 内容区域
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            item {
                val configuredCount = state.quickLinks.count { it.url.isNotBlank() }
                Text("已设置 $configuredCount/9 个", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
            }

            itemsIndexed(state.quickLinks.take(displayCount), key = { i, _ -> "quicklink_$i" }) { index, link ->
                val hasContent = link.name.isNotBlank() || link.url.isNotBlank()
                // 显示标题：有内容时用名称，无内容时用编号
                val title = when {
                    hasContent -> link.name.ifBlank { "未命名链接" }
                    else -> "快捷方式 ${index + 1}"
                }
                val expanded = remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded.value = !expanded.value },
                    shape = CardShapes.cardCorner,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // ── 标题行（始终显示） ──
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hasContent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (index < 3) {
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "预设",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Icon(
                                if (expanded.value) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (expanded.value) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            // 自定义槽折叠态：删除按钮（仅非预设且有内容时显示）
                            if (index >= 3 && hasContent && !expanded.value) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            vm.deleteQuickLink(index)
                                        },
                                )
                            }
                        }

                        // ── 展开详情 ──
                        AnimatedVisibility(visible = expanded.value) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = link.name,
                                    onValueChange = { vm.updateQuickLink(index, it, link.url, link.packageName, link.presetIndex) },
                                    label = { Text("名称") },
                                    placeholder = { Text("如：项目文档") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = link.url,
                                    onValueChange = { vm.updateQuickLink(index, link.name, it, link.packageName, link.presetIndex) },
                                    label = { Text("链接") },
                                    placeholder = { Text("如：https:// 或 weixin://") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = link.packageName,
                                    onValueChange = { vm.updateQuickLink(index, link.name, link.url, it, link.presetIndex) },
                                    label = { Text("包名（可选）") },
                                    placeholder = { Text("如：com.tencent.mm") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(12.dp))
                                // ── 底部按钮 ──
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    // 左：重置（预设槽）/ 删除此快捷方式（自定义槽）
                                    if (index < 3) {
                                        TextButton(
                                            onClick = {
                                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val preset = DEFAULT_QUICK_LINKS.getOrNull(index)
                                                if (preset != null) {
                                                    vm.updateQuickLink(index, preset.name, preset.url, preset.packageName, index)
                                                }
                                            },
                                        ) {
                                            Text("重置为默认", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                vm.deleteQuickLink(index)
                                            },
                                        ) {
                                            Text("删除", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    // 右：清除（预设槽有内容时）
                                    if (index < 3 && hasContent) {
                                        TextButton(
                                            onClick = {
                                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                vm.updateQuickLink(index, "", "", "", index)
                                            },
                                        ) {
                                            Text("清除", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (displayCount < 9) {
                item {
                    OutlinedButton(
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            displayCount = (displayCount + 1).coerceAtMost(9)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加快捷方式")
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacings.xxl))
            }
        }
    }
}
