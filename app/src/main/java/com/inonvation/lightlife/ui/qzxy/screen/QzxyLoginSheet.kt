package com.inonvation.lightlife.ui.qzxy.screen

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.qzxy.QzxyUiState
import com.inonvation.lightlife.ui.theme.Spacings

/** 趣智校园登录弹层：手机号 + 密码（不支持短信登录，secret 绑定账号） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QzxyLoginSheet(qzxy: QzxyUiState, vm: AppViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.qzxyDismissLogin() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacings.xl)
                .padding(bottom = Spacings.xxl),
        ) {
            Text("连接趣智校园", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Spacings.sm))
            Text(
                "使用趣智校园官方账号的手机号和密码登录，与开水功能互不影响。若没设置过密码，请先在官方 App 内设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacings.lg))

            OutlinedTextField(
                value = qzxy.phone,
                onValueChange = { vm.qzxyUpdatePhone(it) },
                label = { Text("手机号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )
            Spacer(Modifier.height(Spacings.md))
            OutlinedTextField(
                value = qzxy.password,
                onValueChange = { vm.qzxyUpdatePassword(it) },
                label = { Text("密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                visualTransformation = if (qzxy.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { vm.qzxyTogglePasswordVisible() }) {
                        Icon(
                            imageVector = if (qzxy.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (qzxy.passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
            qzxy.loginError?.let { error ->
                Spacer(Modifier.height(Spacings.sm))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(Spacings.lg))

            Button(
                onClick = { vm.qzxyLogin() },
                enabled = !qzxy.loggingIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (qzxy.loggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Spacings.sm))
                }
                Text(if (qzxy.loggingIn) "登录中…" else "登录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(Spacings.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "登录即表示在另一设备上使用本账号会互踢下线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
