package com.example.devicecontrol

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.devicecontrol.data.AppRepository
import com.example.devicecontrol.data.OrderHistoryStore
import com.example.devicecontrol.data.PointsStatsStore
import com.example.devicecontrol.data.PointsTaskStateStore
import com.example.devicecontrol.data.TaskLogStore
import com.example.devicecontrol.data.BackupManager
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
import com.example.devicecontrol.ui.screen.TokenDialog
import com.example.devicecontrol.ui.screen.TopBar
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
                factory = AppViewModelFactory(repository, (packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"), statsStore, taskStateStore, taskLogStore, themePrefs, BackupManager(applicationContext)),
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

    BackHandler(enabled = state.showOrderHistory || state.showLogoutConfirm || state.tokenDialogText != null || state.orderDetail != null) {
        when {
            state.showOrderHistory -> vm.dismissOrderHistory()
            state.showLogoutConfirm -> vm.dismissLogoutConfirm()
            state.tokenDialogText != null -> vm.dismissCurrentToken()
            state.orderDetail != null -> vm.dismissOrderDetail()
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

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissLogoutConfirm, title = { Text("确认退出") },
            text = { Text("确定要退出登录吗？退出后需要重新登录才能使用。") },
            confirmButton = { TextButton(onClick = { vm.dismissLogoutConfirm(); vm.logout() }) { Text("确定退出", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { vm.dismissLogoutConfirm() }) { Text("取消") } },
            shape = RoundedCornerShape(8.dp),
        )
    }

    state.orderDetail?.let { OrderDetailDialog(detail = it, onDismiss = vm::dismissOrderDetail) }

    val initialPage = TAB_LIST.indexOf(state.currentTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { TAB_LIST.size }

    // 点击 tab 触发动画切页
    LaunchedEffect(state.currentTab) {
        val target = TAB_LIST.indexOf(state.currentTab)
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target, animationSpec = tween(300, easing = FastOutSlowInEasing))
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
                    onLogoutClick = { vm.showLogoutConfirm() },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(pagerState) {
                            // 左右滑动检测：检测到滑动方向后通过 animateScrollToPage 切页
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    //change consumed
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    val threshold = 60f
                                    val cp = pagerState.currentPage
                                    if (totalDrag < -threshold && cp < TAB_LIST.size - 1) {
                                        vm.selectTab(TAB_LIST[cp + 1])
                                    } else if (totalDrag > threshold && cp > 0) {
                                        vm.selectTab(TAB_LIST[cp - 1])
                                    }
                                },
                                onDragCancel = { }
                            )
                        },
                    beyondViewportPageCount = 1,
                    userScrollEnabled = false,
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
                    // 透明度使用平滑过渡，而不是在 0.5 处跳变
                    alpha = 1f - settingsOffset
                }
        ) {
            SettingsScreen(state = state, vm = vm)
        }
    }
}



