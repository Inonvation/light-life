package com.inonvation.lightlife.ui.screen

import android.content.Intent
import android.net.Uri

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.inonvation.lightlife.data.QuickLink
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.pinQuickLinkShortcut
import com.inonvation.lightlife.ui.theme.CardShapes

// 首页快捷方式区（自 ControlScreen 迁出）

@Composable
private fun SortableQuickLinkCard(
    name: String,
    url: String,
    iconUri: String = "",
    isSorting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hasLink = url.isNotBlank()

    // 排序模式下的脉动动画
    val pulse by if (isSorting) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAnim",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val dashColor = if (isSorting) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent

    Card(
        modifier = modifier
            .scale(pulse)
            .then(
                if (isSorting) {
                    Modifier.drawBehind {
                            val rect = Rect(Offset.Zero, size)
                            val path = Path().apply {
                                addRoundRect(RoundRect(
                                    rect = rect,
                                    cornerRadius = CornerRadius(12.dp.toPx()),
                                ))
                            }
                            drawPath(
                                path = path,
                                color = dashColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                                        phase = 0f,
                                    ),
                                ),
                            )
                        }
                } else Modifier
            )
            .then(
                if (!isSorting && hasLink) Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ) else Modifier.clickable(enabled = !isSorting, onClick = onClick)
            ),
        shape = CardShapes.smallCardCorner,
        colors = CardDefaults.cardColors(
            containerColor = if (hasLink) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasLink) 1.dp else 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasLink) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasLink && iconUri.isNotBlank()) {
                        // 显示自定义图标
                        val iconBitmap = remember(iconUri) {
                            try {
                                val file = java.io.File(iconUri)
                                if (file.exists()) {
                                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                } else null
                            } catch (_: Exception) { null }
                        }
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = name,
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    } else if (hasLink) {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = "添加", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (hasLink) name.ifBlank { "快捷方式" } else "添加",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasLink) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 排序模式提示
            if (isSorting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = "拖动排序", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun QuickLinkCard(
    name: String,
    url: String,
    iconUri: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SortableQuickLinkCard(
        name = name,
        url = url,
        iconUri = iconUri,
        isSorting = false,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun QuickLinksSection(
    state: AppUiState,
    vm: AppViewModel,
    onPickIcon: ((Int) -> Unit)?,
    cardVisible: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: android.content.Context,
) {
    AnimatedVisibility(
        visible = cardVisible,
        enter = fadeIn(tween(400, delayMillis = 150))
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            val isSorting = remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "快捷链接",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (isSorting.value) {
                    IconButton(
                        onClick = { isSorting.value = false },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "完成", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                } else {
                    IconButton(
                        onClick = { isSorting.value = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = "排序", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val configuredLinks = state.quickLinks.filter { it.url.isNotBlank() }
            val displayLinks = if (configuredLinks.size < 3) {
                configuredLinks + List(3 - configuredLinks.size) { QuickLink() }
            } else {
                configuredLinks
            }
            val rowCount = (displayLinks.size + 2) / 3
            var draggedIndex by remember { mutableIntStateOf(-1) }
            var dragOffsetX by remember { mutableFloatStateOf(0f) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            var contextMenuIndex by remember { mutableIntStateOf(-1) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (row in 0 until rowCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (col in 0 until 3) {
                            val displayIdx = row * 3 + col
                            if (displayIdx < displayLinks.size) {
                                val link = displayLinks[displayIdx]
                                val hasLink = link.url.isNotBlank()
                                val realIndex = if (hasLink) state.quickLinks.indexOf(link) else -1
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .zIndex(if (draggedIndex == realIndex) 1f else 0f)
                                        .graphicsLayer {
                                            translationX = if (draggedIndex == realIndex) dragOffsetX else 0f
                                            translationY = if (draggedIndex == realIndex) dragOffsetY else 0f
                                            scaleX = if (draggedIndex == realIndex) 1.05f else 1f
                                            scaleY = if (draggedIndex == realIndex) 1.05f else 1f
                                        }
                                        .then(
                                            if (isSorting.value && hasLink) {
                                                Modifier.pointerInput(realIndex) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { draggedIndex = realIndex },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetX += dragAmount.x
                                                            dragOffsetY += dragAmount.y
                                                            val thresholdY = with(change) { 80.dp.toPx() }
                                                            val thresholdX = with(change) { 55.dp.toPx() }
                                                            val rowsMoved = (dragOffsetY / thresholdY).toInt()
                                                            if (rowsMoved != 0) {
                                                                val target = (realIndex + rowsMoved * 3).coerceIn(0, state.quickLinks.size - 1)
                                                                if (target != realIndex) {
                                                                    vm.swapQuickLinks(realIndex, target)
                                                                    draggedIndex = -1
                                                                    dragOffsetX = 0f
                                                                    dragOffsetY = 0f
                                                                }
                                                            }
                                                            val colsMoved = (dragOffsetX / thresholdX).toInt()
                                                            if (colsMoved != 0) {
                                                                val currentRow = realIndex / 3
                                                                val target = (realIndex + colsMoved).coerceIn(currentRow * 3, ((currentRow + 1) * 3 - 1).coerceAtMost(state.quickLinks.size - 1))
                                                                if (target != realIndex) {
                                                                    vm.swapQuickLinks(realIndex, target)
                                                                    draggedIndex = -1
                                                                    dragOffsetX = 0f
                                                                    dragOffsetY = 0f
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = { draggedIndex = -1; dragOffsetX = 0f; dragOffsetY = 0f },
                                                        onDragCancel = { draggedIndex = -1; dragOffsetX = 0f; dragOffsetY = 0f },
                                                    )
                                                }
                                            } else Modifier
                                        ),
                                ) {
                                    SortableQuickLinkCard(
                                        name = link.name,
                                        url = link.url,
                                        iconUri = link.iconUri,
                                        isSorting = isSorting.value,
                                        onLongClick = {
                                            if (!isSorting.value && hasLink) {
                                                contextMenuIndex = realIndex
                                            }
                                        },
                                        onClick = {
                                            if (!isSorting.value && hasLink) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                                    if (link.packageName.isNotBlank()) {
                                                        intent.setPackage(link.packageName)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: android.content.ActivityNotFoundException) {
                                                    try {
                                                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                                        context.startActivity(fallbackIntent)
                                                    } catch (e2: Exception) {
                                                        android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else if (!isSorting.value) {
                                                if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                vm.showQuickLinksSettings()
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    if (hasLink) {
                                        DropdownMenu(
                                            expanded = contextMenuIndex == realIndex,
                                            onDismissRequest = { contextMenuIndex = -1 },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("添加快捷方式到桌面", style = MaterialTheme.typography.bodyMedium) },
                                                onClick = {
                                                    contextMenuIndex = -1
                                                    pinQuickLinkShortcut(context, link, realIndex, vm.getQuickLinkStore())
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("设置图标", style = MaterialTheme.typography.bodyMedium) },
                                                onClick = {
                                                    contextMenuIndex = -1
                                                    onPickIcon?.invoke(realIndex)
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                            )
                                            if (link.iconUri.isNotBlank()) {
                                                DropdownMenuItem(
                                                    text = { Text("移除图标", style = MaterialTheme.typography.bodyMedium) },
                                                    onClick = {
                                                        contextMenuIndex = -1
                                                        vm.removeQuickLinkIcon(realIndex)
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                QuickLinkCard(
                                    name = "添加",
                                    url = "",
                                    onClick = {
                                        if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.showQuickLinksSettings()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (configuredLinks.isEmpty()) {
                    QuickLinkCard(
                        name = "管理",
                        url = "",
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.showQuickLinksSettings()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
