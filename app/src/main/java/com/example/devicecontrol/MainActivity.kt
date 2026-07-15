package com.example.devicecontrol

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.devicecontrol.data.AppRepository
import com.example.devicecontrol.data.OrderHistoryStore
import com.example.devicecontrol.data.PointsStatsStore
import com.example.devicecontrol.data.PointsTaskStateStore
import com.example.devicecontrol.data.TaskLogStore
import com.example.devicecontrol.data.BackupManager
import com.example.devicecontrol.data.DebugLogStore
import com.example.devicecontrol.data.TokenStore
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.AppViewModelFactory
import com.example.devicecontrol.ui.DeviceTab
import com.example.devicecontrol.ui.screen.ControlScreen
import com.example.devicecontrol.ui.screen.MeScreen
import com.example.devicecontrol.ui.screen.OrderDetailDialog
import com.example.devicecontrol.ui.screen.OrderHistoryBottomSheet
import com.example.devicecontrol.ui.screen.PointsTaskScreen
import com.example.devicecontrol.ui.screen.SettingsScreen
import com.example.devicecontrol.ui.screen.SimpleScreen
import com.example.devicecontrol.ui.screen.TokenDialog
import com.example.devicecontrol.ui.screen.TopBar
import com.example.devicecontrol.ui.shortcutRequestFromIntent
import com.example.devicecontrol.ui.theme.DeviceControlTheme
import com.example.devicecontrol.ui.theme.ThemeMode
import com.example.devicecontrol.ui.theme.ThemePreferences
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val debugLogStore = DebugLogStore(applicationContext)
        setContent {
            val vm: AppViewModel = viewModel(
                factory = AppViewModelFactory(applicationContext, repository, (packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"), statsStore, taskStateStore, taskLogStore, themePrefs, BackupManager(applicationContext), debugLogStore),
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

private val TAB_LIST = listOf(DeviceTab.Control, DeviceTab.Points, DeviceTab.Me)

@Composable
private fun DeviceControlApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler(enabled = state.showOrderHistory || state.showLogoutConfirm || state.tokenDialogText != null || state.orderDetail != null || state.showBackupTokenExpiredDialog) {
        when {
            state.showOrderHistory -> vm.dismissOrderHistory()
            state.showLogoutConfirm -> vm.dismissLogoutConfirm()
            state.tokenDialogText != null -> vm.dismissCurrentToken()
            state.orderDetail != null -> vm.dismissOrderDetail()
            state.showBackupTokenExpiredDialog -> vm.dismissBackupTokenExpiredDialog()
        }
    }
    BackHandler(enabled = state.showSettings) {
        vm.dismissSettings()
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            vm.consumeToast()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg -> snackbarHostState.showSnackbar(msg); vm.consumeError() }
    }

    state.tokenDialogText?.let { TokenDialog(token = it, onDismiss = vm::dismissCurrentToken) }

    if (state.showOrderHistory) {
        OrderHistoryBottomSheet(orders = state.orderHistory, onDismiss = vm::dismissOrderHistory)
    }

    state.orderDetail?.let { OrderDetailDialog(detail = it, onDismiss = vm::dismissOrderDetail) }


    // 退出登录确认对话框（简洁/普通模式共用）
    if (state.showLogoutConfirm) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val json = vm.prepareBackupJson()
                if (json.isBlank()) {
                    android.widget.Toast.makeText(context, "备份数据为空", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                android.widget.Toast.makeText(context, "备份导出成功", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        AlertDialog(
            onDismissRequest = vm::dismissLogoutConfirm,
            title = { Text("确认退出") },
            text = {
                Column {
                    Text(
                        text = "⚠️ 退出后将清除本地数据",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "确定要退出登录吗？退出后会清除本地的积分统计、订单记录、执行日志和任务记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "请确保已备份数据再退出，否则无法恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = {
                            val fileName = "LightLife_backup_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date()) + ".lif"
                            exportLauncher.launch(fileName)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("先去备份", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissLogoutConfirm(); vm.logout() }) {
                    Text("确定退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissLogoutConfirm() }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(8.dp),
        )
    }
    // 备份 token 过期确认对话框（简洁/普通模式共用）
    if (state.showBackupTokenExpiredDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissBackupTokenExpiredDialog,
            title = { Text("备份凭证已过期") },
            text = { Text("当前备份文件中的登录凭证已过期，是否仅导入订单和日志信息？") },
            confirmButton = {
                TextButton(onClick = { vm.confirmBackupImportOrdersOnly() }) {
                    Text("确定导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissBackupTokenExpiredDialog() }) {
                    Text("取消")
                }
            },
        )
    }

    if (state.simpleModeEnabled) {
        // 简洁模式：仅显示 SimpleScreen
        Box(modifier = Modifier.fillMaxSize()) {
            SimpleScreen(state = state, vm = vm)
            // 简洁模式下的设置页滑入
            val settingsOffset by animateFloatAsState(
                targetValue = if (state.showSettings) 0f else 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "settingsSlide"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = settingsOffset * size.width
                        alpha = 1f - settingsOffset
                    }
            ) {
                SettingsScreen(state = state, vm = vm)
            }
        }
        return
    }

    val initialPage = TAB_LIST.indexOf(state.currentTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { TAB_LIST.size }

    // 点击 tab 触发动画切页
    LaunchedEffect(state.currentTab) {
        val target = TAB_LIST.indexOf(state.currentTab)
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target, animationSpec = tween(300, easing = FastOutSlowInEasing))
        }
    }

    // 滑动 pager 时同步更新 currentTab（反向同步）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                val newTab = TAB_LIST[page]
                if (newTab != state.currentTab) {
                    vm.selectTab(newTab)
                }
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // 底部导航栏始终渲染以保持 padding 稳定，设置页打开时平滑淡出
            val bottomBarAlpha by animateFloatAsState(
                targetValue = if (state.showSettings) 0f else 1f,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = "bottomBarAlpha"
            )
            NavigationBar(
                modifier = Modifier.alpha(bottomBarAlpha),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val haptic = LocalHapticFeedback.current
                TAB_LIST.forEachIndexed { index, tab ->
                    val label = when (tab) { DeviceTab.Control -> "首页"; DeviceTab.Points -> "积分任务"; DeviceTab.Me -> "我的" }
                    val icon = when (tab) { DeviceTab.Control -> Icons.Outlined.Home; DeviceTab.Points -> Icons.Outlined.PlayArrow; DeviceTab.Me -> Icons.Outlined.Person }
                    NavigationBarItem(
                        selected = state.currentTab == tab,
                        onClick = {
                            if (state.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.selectTab(tab)
                        },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            // 主页内容
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    currentTab = state.currentTab,
                    hasToken = state.hasToken,
                    hapticEnabled = state.hapticEnabled,
                    onSettingsClick = { vm.showSettings() },
                                    )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = true,
                ) { page ->
                    when (TAB_LIST[page]) {
                        DeviceTab.Control -> ControlScreen(state, vm)
                        DeviceTab.Points -> PointsTaskScreen(state, vm)
                        DeviceTab.Me -> MeScreen(state, vm, isActive = state.currentTab == DeviceTab.Me)
                    }
                }
            }
        }
        // 设置页平滑滑入（在 Surface 外部，不受底部 padding 限制）
        val settingsOffset by animateFloatAsState(
            targetValue = if (state.showSettings) 0f else 1f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "settingsSlide"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = settingsOffset * size.width
                    // alpha = 1f - settingsOffset // 移除透明度变化，仅保留平移
                }
        ) {
            SettingsScreen(state = state, vm = vm)
        }
    }
}



