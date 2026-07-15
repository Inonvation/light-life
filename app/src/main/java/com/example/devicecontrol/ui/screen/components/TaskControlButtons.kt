package com.example.devicecontrol.ui.screen.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicecontrol.ui.theme.AppColors

@Composable
fun TaskControlButtons(
    running: Boolean,
    paused: Boolean,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = when {
            running && paused -> "paused"
            running -> "running"
            else -> "idle"
        },
        transitionSpec = {
            fadeIn(tween(200)) + slideInVertically(tween(200), initialOffsetY = { it / 4 }) togetherWith
                fadeOut(tween(150))
        },
        label = "btnContent",
        modifier = modifier,
    ) { btnState ->
        when (btnState) {
            "running", "paused" -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // 暂停/继续按钮
                    Button(
                        onClick = onPauseResume,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (paused) AppColors.resume else AppColors.pause,
                            contentColor = AppColors.white
                        )
                    ) {
                        Text(
                            if (paused) "▶ 继续" else "⏸ 暂停",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                    // 结束按钮
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.stop),
                    ) {
                        Text("⏹ 结束", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    }
                }
            }
            else -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.start,
                        contentColor = AppColors.white
                    )
                ) {
                    Icon(
                        Icons.Filled.PlayArrow, null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("开始执行自动化任务", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            }
        }
    }
}
