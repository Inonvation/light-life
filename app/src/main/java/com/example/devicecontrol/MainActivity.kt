package com.example.devicecontrol

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.devicecontrol.data.AppRepository
import com.example.devicecontrol.data.OrderHistoryStore
import com.example.devicecontrol.data.PointsStatsStore
import com.example.devicecontrol.data.PointsTaskStateStore
import com.example.devicecontrol.data.TaskLogStore
import com.example.devicecontrol.data.TokenStore
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.AppViewModelFactory
import com.example.devicecontrol.ui.DeviceTab
import com.example.devicecontrol.ui.screen.ControlScreen
import com.example.devicecontrol.ui.screen.MeScreen
import com.example.devicecontrol.ui.screen.OrderDetailDialog
import com.example.devicecontrol.ui.screen.OrderHistoryDialog
import com.example.devicecontrol.ui.screen.PointsTaskScreen
import com.example.devicecontrol.ui.screen.TokenDialog
import com.example.devicecontrol.ui.shortcutRequestFromIntent
import com.example.devicecontrol.ui.theme.DeviceControlTheme
import com.example.devicecontrol.ui.theme.ThemeMode
import com.example.devicecontrol.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = ThemePreferences(applicationContext)
        val repository = AppRepository(
            tokenStore = TokenStore(applicationContext),
            orderHistoryStore = OrderHistoryStore(applicationContext),
        )
        val statsStore = PointsStatsStore(applicationContext)
        val taskStateStore = PointsTaskStateStore(applicationContext)
        val taskLogStore = TaskLogStore(applicationContext)
        setContent {
            val vm: AppViewModel = viewModel(
                factory = AppViewModelFactory(repository, statsStore, taskStateStore, taskLogStore, themePrefs),
            )
            val uiState by vm.state.collectAsState()
            DeviceControlTheme(
                darkTheme = when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                },
            ) {
                LaunchedEffect(vm) {
                    shortcutRequestFromIntent(intent)?.let(vm::openDeviceShortcut)
                }
                DeviceControlApp(vm)
            }
        }
    }
}

@Composable
private fun DeviceControlApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show(); vm.consumeToast() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg -> snackbarHostState.showSnackbar(msg); vm.consumeError() }
    }

    state.tokenDialogText?.let { TokenDialog(token = it, onDismiss = vm::dismissCurrentToken) }

    if (state.showOrderHistory) {
        OrderHistoryDialog(orders = state.orderHistory, onDismiss = vm::dismissOrderHistory, onOpenOrder = vm::showHistoricalOrder)
    }

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissLogoutConfirm, title = { Text("确认退出") },
            text = { Text("确定要退出登录吗？退出后需要重新登录才能使用。") },
            confirmButton = { TextButton(onClick = { vm.dismissLogoutConfirm(); vm.logout() }) { Text("确定退出", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::dismissLogoutConfirm) { Text("取消") } },
            shape = RoundedCornerShape(8.dp),
        )
    }

    state.orderDetail?.let { OrderDetailDialog(detail = it, onDismiss = vm::dismissOrderDetail) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val haptic = LocalHapticFeedback.current
                NavigationBarItem(selected = state.currentTab == DeviceTab.Control, onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.selectTab(DeviceTab.Control) }, icon = { Icon(Icons.Outlined.Home, contentDescription = null) }, label = { Text("首页") })
                NavigationBarItem(selected = state.currentTab == DeviceTab.Points, onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.selectTab(DeviceTab.Points) }, icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) }, label = { Text("积分任务") })
                NavigationBarItem(selected = state.currentTab == DeviceTab.Me, onClick = { if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.selectTab(DeviceTab.Me) }, icon = { Icon(Icons.Outlined.Person, contentDescription = null) }, label = { Text("我的") })
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when (state.currentTab) {
                DeviceTab.Control -> ControlScreen(state, vm)
                DeviceTab.Points -> PointsTaskScreen(state, vm)
                DeviceTab.Me -> MeScreen(state, vm)
            }
        }
    }
}
