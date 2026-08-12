package com.inonvation.lightlife

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inonvation.lightlife.data.AppRepository
import com.inonvation.lightlife.data.OrderHistoryStore
import com.inonvation.lightlife.data.PointsStatsStore
import com.inonvation.lightlife.data.PointsTaskStateStore
import com.inonvation.lightlife.data.QuickLinkStore
import com.inonvation.lightlife.data.TokenStore
import com.inonvation.lightlife.ui.AppViewModel
import com.inonvation.lightlife.ui.AppViewModelFactory
import com.inonvation.lightlife.ui.UiEvent
import com.inonvation.lightlife.ui.screen.OrderHistoryBottomSheet
import com.inonvation.lightlife.ui.screen.QuickLinksSettingsScreen
import com.inonvation.lightlife.ui.screen.SettingsScreen
import com.inonvation.lightlife.ui.screen.SimpleScreen
import com.inonvation.lightlife.ui.screen.TokenDialog
import com.inonvation.lightlife.ui.shortcutRequestFromIntent
import com.inonvation.lightlife.ui.theme.DeviceControlTheme
import com.inonvation.lightlife.ui.theme.ThemeMode
import com.inonvation.lightlife.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = AppRepository(
            tokenStore = TokenStore(applicationContext),
            orderHistoryStore = OrderHistoryStore(applicationContext),
        )
        val statsStore = PointsStatsStore(applicationContext)
        val taskStateStore = PointsTaskStateStore(applicationContext)
        val themePrefs = ThemePreferences(applicationContext)
        val quickLinkStore = QuickLinkStore(applicationContext)
        setContent {
            val vm: AppViewModel = viewModel(
                factory = AppViewModelFactory(
                    application = application,
                    repository = repository,
                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown",
                    pointsStatsStore = statsStore,
                    taskStateStore = taskStateStore,
                    themePreferences = themePrefs,
                    quickLinkStore = quickLinkStore,
                ),
            )
            val uiState by vm.state.collectAsState()
            DeviceControlTheme(
                darkTheme = when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                },
                colorTheme = uiState.colorTheme,
            ) {
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingIconIndex by remember { mutableStateOf(-1) }
    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && pendingIconIndex >= 0) {
            vm.setQuickLinkIcon(pendingIconIndex, uri)
            pendingIconIndex = -1
        }
    }

    BackHandler(
        enabled = state.showOrderHistory || state.showLogoutConfirm || state.showSettings ||
            state.showQuickLinksSettings || state.tokenDialogText != null || state.deviceInfoDialogText != null
    ) {
        when {
            state.showOrderHistory -> vm.dismissOrderHistory()
            state.showLogoutConfirm -> vm.dismissLogoutConfirm()
            state.showSettings -> vm.dismissSettings()
            state.showQuickLinksSettings -> vm.dismissQuickLinksSettings()
            state.tokenDialogText != null -> vm.dismissCurrentToken()
            state.deviceInfoDialogText != null -> vm.dismissCurrentDeviceInfo()
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                is UiEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(vm) {
        shortcutRequestFromIntent((context as? ComponentActivity)?.intent)?.let(vm::openDeviceShortcut)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SimpleScreen(
            state = state,
            vm = vm,
            onPickIcon = { index ->
                pendingIconIndex = index
                iconPickerLauncher.launch("image/*")
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // 退出登录确认
    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissLogoutConfirm,
            title = { Text("确认退出") },
            text = { Text("退出后将清除本地登录状态与订单记录，确定退出吗？") },
            confirmButton = {
                TextButton(onClick = { vm.dismissLogoutConfirm(); vm.logout() }) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissLogoutConfirm) {
                    Text("取消")
                }
            },
        )
    }

    // 全屏设置页
    if (state.showSettings) {
        SettingsScreen(state = state, vm = vm)
    }
    if (state.showQuickLinksSettings) {
        QuickLinksSettingsScreen(state = state, vm = vm)
    }

    // 订单历史
    if (state.showOrderHistory) {
        OrderHistoryBottomSheet(orders = state.orderHistory, onDismiss = vm::dismissOrderHistory)
    }

    state.tokenDialogText?.let { TokenDialog(token = it, title = "我的 Token", onDismiss = vm::dismissCurrentToken) }
    state.deviceInfoDialogText?.let { TokenDialog(token = it, title = "设备信息", onDismiss = vm::dismissCurrentDeviceInfo) }
}
