package com.example.devicecontrol.ui.screen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.ui.PointsProgress
import com.example.devicecontrol.ui.theme.TimelineColors

private data class PhaseInfo(val key: String, val label: String, val number: Int)

private val PHASES = listOf(
    PhaseInfo("signin", "签到", 1),
    PhaseInfo("tasklist", "任务列表", 2),
    PhaseInfo("app_video", "APP 广告", 3),
    PhaseInfo("ali_video", "支付宝广告", 4),
)

private val FailRed = Color(0xFFE06C75)

@Composable
fun PhaseTimeline(
    running: Boolean,
    pointsProgress: PointsProgress?,
    phaseResults: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    // 根据 phase 名称映射到 key
    val activeKey = when {
        pointsProgress?.phase?.startsWith("APP") == true -> "app_video"
        pointsProgress?.phase?.startsWith("支付宝") == true -> "ali_video"
        else -> null
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(450)) + slideInVertically(tween(450), initialOffsetY = { it / 5 })
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                PHASES.forEachIndexed { index, phase ->
                    val result = phaseResults[phase.key]
                    val isActive = running && phase.key == activeKey
                    val isDone = result == "done"
                    val isFail = result == "fail"

                    // 节点颜色动画
                    val nodeColor by animateColorAsState(
                        targetValue = when {
                            isDone -> TimelineColors.completed
                            isFail -> FailRed
                            isActive -> TimelineColors.active
                            else -> TimelineColors.pending
                        },
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "nodeColor$index"
                    )

                    // 节点大小动画
                    val nodeSize by animateDpAsState(
                        targetValue = when {
                            isActive -> 36.dp
                            isDone || isFail -> 30.dp
                            else -> 28.dp
                        },
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "nodeSize$index"
                    )

                    // 标签透明度
                    val labelAlpha = when {
                        isActive -> 1f
                        isDone || isFail -> 0.9f
                        else -> 0.45f
                    }

                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // 节点圆圈
                            Box(
                                modifier = Modifier.size(nodeSize),
                                contentAlignment = Alignment.Center
                            ) {
                                // 运行中呼吸光晕
                                if (isActive) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "glow$index")
                                    val glowAlpha by infiniteTransition.animateFloat(
                                        0.12f, 0.3f,
                                        infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                                        label = "glowA$index"
                                    )
                                    Box(
                                        Modifier
                                            .size(nodeSize + 14.dp)
                                            .alpha(glowAlpha)
                                            .background(nodeColor.copy(alpha = 0.25f), CircleShape)
                                    )
                                }

                                // 内圈背景
                                Box(
                                    modifier = Modifier
                                        .size(nodeSize - 4.dp)
                                        .background(
                                            when {
                                                isActive -> nodeColor.copy(alpha = 0.15f)
                                                isDone || isFail -> nodeColor.copy(alpha = 0.12f)
                                                else -> Color.Transparent
                                            },
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isDone -> Icon(
                                            Icons.Outlined.Check, null,
                                            tint = nodeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        isFail -> Text(
                                            "✗", color = nodeColor,
                                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                                        )
                                        isActive -> {
                                            val pa by rememberInfiniteTransition(label = "pa$index")
                                                .animateFloat(
                                                    0.4f, 1f,
                                                    infiniteRepeatable(tween(800), RepeatMode.Reverse),
                                                    label = "pai$index"
                                                )
                                            Box(
                                                Modifier
                                                    .size(10.dp)
                                                    .alpha(pa)
                                                    .background(nodeColor, CircleShape)
                                            )
                                        }
                                        else -> Text(
                                            "${phase.number}",
                                            color = nodeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // 阶段名称
                            Text(
                                phase.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = if (isActive) 10.sp else 9.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = nodeColor.copy(alpha = labelAlpha),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(50.dp),
                            )

                            // 步骤进度
                            if (isActive && pointsProgress != null) {
                                Text(
                                    "${pointsProgress.step}/${pointsProgress.total}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    color = nodeColor.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // 连接线
                        if (index < PHASES.lastIndex) {
                            val lineColor by animateColorAsState(
                                targetValue = when {
                                    isDone -> TimelineColors.completed.copy(alpha = 0.5f)
                                    isActive -> TimelineColors.active.copy(alpha = 0.3f)
                                    else -> TimelineColors.pending.copy(alpha = 0.15f)
                                },
                                animationSpec = tween(300),
                                label = "lineColor$index"
                            )
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .height(1.5.dp)
                                    .padding(top = 12.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                lineColor,
                                                if (isDone) TimelineColors.completed.copy(alpha = 0.3f) else lineColor
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
