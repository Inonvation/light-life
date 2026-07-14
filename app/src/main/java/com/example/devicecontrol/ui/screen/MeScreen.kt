package com.example.devicecontrol.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.devicecontrol.R
import com.example.devicecontrol.ui.AppUiState
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.openProjectHome
import com.example.devicecontrol.ui.theme.CardShapes
import com.example.devicecontrol.ui.theme.Spacings
import com.example.devicecontrol.ui.theme.ThemeMode
import com.example.devicecontrol.ui.theme.ThemePreferences

@Composable
fun MeScreen(state: AppUiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val themePrefs = remember { ThemePreferences(ctx) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageTitle("我的", if (state.hasToken) "已登录" else "未登录")
                if (state.hasToken) { IconButton(onClick = { vm.showLogoutConfirm() }) { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "退出登录") } }
            }
            Row {
                IconButton(onClick = { vm.showCurrentToken() }) { Icon(Icons.Outlined.Code, contentDescription = "查看 Token") }
                IconButton(onClick = { openProjectHome(ctx) }) { Icon(painterResource(R.drawable.ic_github), contentDescription = "打开 GitHub", modifier = Modifier.size(24.dp)) }
            }
        }
        Spacer(Modifier.height(Spacings.xxl))

        if (!state.hasToken) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.phone, onValueChange = vm::updatePhone, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("手机号") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next), shape = RoundedCornerShape(8.dp), isError = state.phoneError != null)
                    if (state.phoneError != null) { Text(text = state.phoneError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = vm::sendCode, modifier = Modifier.fillMaxWidth(), enabled = !state.sendingCode, shape = RoundedCornerShape(8.dp)) { Text(if (state.sendingCode) "发送中" else "发送验证码") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.code, onValueChange = vm::updateCode, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("验证码") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), shape = RoundedCornerShape(8.dp))
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = vm::login, modifier = Modifier.fillMaxWidth(), enabled = !state.loggingIn, shape = RoundedCornerShape(8.dp)) { Text(if (state.loggingIn) "登录中" else "确认登录") }
                }
            }
            Spacer(Modifier.height(Spacings.lg))
        }

        if (state.hasToken) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("我的资产", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = vm::refreshBalance, enabled = state.hasToken && !state.loadingBalance) {
                            if (state.loadingBalance) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                            else { Icon(Icons.Outlined.Refresh, contentDescription = "刷新资产") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val bal = state.balance
                    if (state.loadingBalance && bal == null) {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("正在查询...", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    } else if (bal != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("小票", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(bal.ticketText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("积分", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(bal.pointsText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("积分抵扣金额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(bal.integralAmount ?: "-", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                    } else { Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text("暂无资产信息", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("积分统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = vm::refreshPointsStats) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新统计") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("累计获得积分", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${state.totalPointsEarned}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("累计抵扣金额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("¥${state.totalPointsDeducted}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = vm::showOrderHistory, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("历史订单") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("退出登录") }
            Spacer(Modifier.height(16.dp))
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = CardShapes.cardCorner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("主题设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                val currentMode = themePrefs.getThemeMode()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { themePrefs.setThemeMode(ThemeMode.SYSTEM); vm.updateThemeMode(ThemeMode.SYSTEM) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = if (currentMode == ThemeMode.SYSTEM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, contentColor = if (currentMode == ThemeMode.SYSTEM) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) { Text("跟随系统") }
                    Button(onClick = { themePrefs.setThemeMode(ThemeMode.LIGHT); vm.updateThemeMode(ThemeMode.LIGHT) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = if (currentMode == ThemeMode.LIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, contentColor = if (currentMode == ThemeMode.LIGHT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) { Text("浅色") }
                    Button(onClick = { themePrefs.setThemeMode(ThemeMode.DARK); vm.updateThemeMode(ThemeMode.DARK) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = if (currentMode == ThemeMode.DARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, contentColor = if (currentMode == ThemeMode.DARK) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) { Text("深色") }
                }
            }
        }
    }
}
