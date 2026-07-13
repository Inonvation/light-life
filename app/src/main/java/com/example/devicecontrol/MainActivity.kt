package com.example.devicecontrol

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.devicecontrol.data.AppRepository
import com.example.devicecontrol.data.DeviceItem
import com.example.devicecontrol.data.OrderHistoryItem
import com.example.devicecontrol.data.OrderHistoryStore
import com.example.devicecontrol.data.PointsStatsStore
import com.example.devicecontrol.data.TokenStore
import com.example.devicecontrol.data.UnlockResult
import com.example.devicecontrol.ui.AppViewModel
import com.example.devicecontrol.ui.AppViewModelFactory
import com.example.devicecontrol.ui.DeviceShortcutRequest
import com.example.devicecontrol.ui.DeviceTab
import com.example.devicecontrol.ui.theme.DeviceControlTheme
import com.example.devicecontrol.ui.theme.ThemeMode
import com.example.devicecontrol.ui.theme.ThemePreferences
import com.example.devicecontrol.ui.theme.Spacings
import com.example.devicecontrol.ui.theme.CardShapes
import com.example.devicecontrol.ui.theme.AppColors
import com.example.devicecontrol.ui.theme.LogColors

private const val ACTION_OPEN_DEVICE_SHORTCUT = "com.example.devicecontrol.OPEN_DEVICE_SHORTCUT"
private const val EXTRA_GOODS_ID = "goods_id"
private const val EXTRA_DEVICE_ID = "device_id"
private const val EXTRA_GOODS_NAME = "goods_name"
private const val PROJECT_URL = "https://github.com/Inonvation/qiekj-android"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = ThemePreferences(applicationContext)
        val repository = AppRepository(
            tokenStore = TokenStore(applicationContext),
            orderHistoryStore = OrderHistoryStore(applicationContext),
        )
        val statsStore = PointsStatsStore(applicationContext)
        setContent {
            val vm: AppViewModel = viewModel(
                factory = AppViewModelFactory(repository, statsStore, themePrefs),
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

private fun shortcutRequestFromIntent(intent: Intent?): DeviceShortcutRequest? {
    if (intent?.action != ACTION_OPEN_DEVICE_SHORTCUT) return null
    return DeviceShortcutRequest(
        goodsId = intent.getStringExtra(EXTRA_GOODS_ID),
        id = intent.getStringExtra(EXTRA_DEVICE_ID),
        goodsName = intent.getStringExtra(EXTRA_GOODS_NAME),
    )
}

private fun pinDeviceShortcut(context: Context, device: DeviceItem) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(context, "当前系统不支持添加桌面快捷方式", Toast.LENGTH_LONG).show()
        return
    }
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    if (shortcutManager?.isRequestPinShortcutSupported != true) {
        Toast.makeText(context, "当前桌面不支持添加快捷方式", Toast.LENGTH_LONG).show()
        return
    }

    val label = device.goodsName.ifBlank { "历史设备" }
    val shortcutIntent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_OPEN_DEVICE_SHORTCUT
        putExtra(EXTRA_GOODS_ID, device.goodsId)
        putExtra(EXTRA_DEVICE_ID, device.id)
        putExtra(EXTRA_GOODS_NAME, device.goodsName)
    }
    val shortcut = ShortcutInfo.Builder(
        context,
        "device-${device.goodsId ?: device.id ?: device.goodsName.hashCode()}",
    )
        .setShortLabel(label.take(10))
        .setLongLabel(label)
        .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher))
        .setIntent(shortcutIntent)
        .build()

    shortcutManager.requestPinShortcut(shortcut, null)
}

private fun openProjectHome(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))
    context.startActivity(intent)
}

@Composable
private fun DeviceControlApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        vm.consumeToast()
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.consumeError()
    }

    state.tokenDialogText?.let { token ->
        TokenDialog(token = token, onDismiss = vm::dismissCurrentToken)
    }

    if (state.showOrderHistory) {
        OrderHistoryDialog(
            orders = state.orderHistory,
            onDismiss = vm::dismissOrderHistory,
            onOpenOrder = vm::showHistoricalOrder,
        )
    }

    state.orderDetail?.let { detail ->
        OrderDetailDialog(
            detail = detail,
            onDismiss = vm::dismissOrderDetail,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = state.currentTab == DeviceTab.Control,
                    onClick = { vm.selectTab(DeviceTab.Control) },
                    icon = { androidx.compose.material3.Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text("首页") },
                )
                NavigationBarItem(
                    selected = state.currentTab == DeviceTab.Points,
                    onClick = { vm.selectTab(DeviceTab.Points) },
                    icon = { androidx.compose.material3.Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    label = { Text("积分任务") },
                )
                NavigationBarItem(
                    selected = state.currentTab == DeviceTab.Me,
                    onClick = { vm.selectTab(DeviceTab.Me) },
                    icon = { androidx.compose.material3.Icon(Icons.Outlined.Person, contentDescription = null) },
                    label = { Text("我的") },
                )
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (state.currentTab) {
                DeviceTab.Control -> ControlScreen(state, vm)
                DeviceTab.Points -> PointsTaskScreen(state, vm)
                DeviceTab.Me -> MeScreen(state, vm)
            }
        }
    }
}


@Composable
private fun TokenDialog(token: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("当前 Token") },
        text = {
            Text(
                text = token,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}
@Composable
private fun OrderDetailDialog(
    detail: UnlockResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("订单详情") },
        text = {
            Column {
                DetailLine("订单号", detail.orderNo)
                DetailLine("订单 ID", detail.orderId)
                DetailLine("订单原价", detail.originPrice)
                DetailLine("花费小票", detail.ticketCost)
                DetailLine("积分抵扣", detail.integralCost)
                if (detail.otherPromotions.isNotEmpty()) {
                    Spacer(Modifier.height(Spacings.sm))
                    Text("其他优惠", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    detail.otherPromotions.forEach { promotion ->
                        DetailLine(
                            label = "类型 ${promotion.promotionType ?: "-"}",
                            value = promotion.discountAmount ?: "-",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}


@Composable
private fun PointsTaskScreen(
    state: com.example.devicecontrol.ui.AppUiState,
    vm: AppViewModel,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var logExpanded by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.pointsLogs.size) {
        if (state.pointsLogs.isNotEmpty() && logExpanded) {
            listState.animateScrollToItem(state.pointsLogs.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageTitle("积分任务", "自动化刷积分")
        Spacer(Modifier.height(Spacings.xl))

                // Progress bar area
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShapes.cardCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.runningPointsTask && state.pointsProgress != null) {
                    val progress = state.pointsProgress!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = progress.phase,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${progress.step} / ${progress.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Spacings.sm))
                    LinearProgressIndicator(
                        progress = { (progress.step.toFloat() / progress.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                } else {
                    Text(
                        text = if (state.runningPointsTask) "任务启动中..." else "等待任务执行",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacings.md))
        // Log area with folding
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("执行日志", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { logExpanded = !logExpanded }) {
                    Text(if (logExpanded) "折叠" else "展开")
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = LogColors.background,
                shape = RoundedCornerShape(8.dp),
            ) {
                if (logExpanded) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    ) {
                        if (state.pointsLogs.isEmpty()) {
                            item {
                                Text(
                                    "等待执行任务...",
                                    color = Color(0xFFB8C7D1),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else {
                            items(state.pointsLogs) { line ->
                                Text(
                                    text = line,
                                    color = Color(0xFFB7F7C1),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        val lastLines = state.pointsLogs.takeLast(5)
                        Column {
                            lastLines.forEach { line ->
                                Text(
                                    text = line,
                                    color = Color(0xFFB7F7C1),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Bottom control buttons
        if (state.runningPointsTask) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.pointsTaskPaused) {
                    Button(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.resumePointsTask() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.resume, contentColor = AppColors.white),
                    ) {
                        Text("继续")
                    }
                } else {
                    Button(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.pausePointsTask() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.pause, contentColor = AppColors.white),
                    ) {
                        Text("暂停")
                    }
                }
                Button(
                    onClick = { vm.stopPointsTask() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.stop, contentColor = AppColors.white),
                ) {
                    Text("结束")
                }
                Button(
                    onClick = { vm.clearPointsLogs() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.clear, contentColor = AppColors.white),
                ) {
                    Text("清除日志")
                }
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val ua = android.webkit.WebSettings.getDefaultUserAgent(context)
                    vm.startPointsTask(ua)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.runningPointsTask,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.start, contentColor = AppColors.white),
            ) {
                Text(if (state.runningPointsTask) "任务执行中" else "开始执行自动化任务")
            }
        }
    }
}
@Composable
private fun ControlScreen(
    state: com.example.devicecontrol.ui.AppUiState,
    vm: AppViewModel,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        PageTitle("首页", state.unlockStatus ?: "历史设备")
        Spacer(Modifier.height(24.dp))
        // Today's water stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShapes.cardCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("今日喝水统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("喝水次数", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.todayWaterCount} 次", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("抵扣金额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("¥${state.todayWaterAmount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        
        Spacer(Modifier.height(18.dp))

        if (!state.hasToken) {
            EmptyText("请先到“我的”页面登录获取权限")
            return@Column
        }

        if (state.loadingDevices) {
            LoadingText("正在查询历史设备")
        } else if (state.devices.isEmpty()) {
            EmptyText("暂无历史设备")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(state.devices) { device ->
                    DeviceRow(
                        name = device.goodsName.ifBlank { "未命名设备" },
                        enabled = !state.unlocking,
                        onClick = { vm.unlock(device) },
                        onAddShortcut = { pinDeviceShortcut(context, device) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MeScreen(
    state: com.example.devicecontrol.ui.AppUiState,
    vm: AppViewModel,
) {
    val context = LocalContext.current
    val themePrefs = remember { ThemePreferences(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            PageTitle("我的", if (state.hasToken) "已登录" else "未登录")
            Row {
                IconButton(onClick = { vm.showCurrentToken() }) {
                    Icon(Icons.Outlined.Code, contentDescription = "查看 Token")
                }
                IconButton(onClick = { openProjectHome(context) }) {
                    Icon(Icons.Outlined.Code, contentDescription = "打开 GitHub")
                }
            }
        }
        Spacer(Modifier.height(Spacings.xxl))

        // Login form - only show when NOT logged in
        if (!state.hasToken) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.phone,
                        onValueChange = vm::updatePhone,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("手机号") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = vm::sendCode,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.sendingCode,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(if (state.sendingCode) "发送中" else "发送验证码")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = vm::updateCode,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("验证码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = vm::login,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loggingIn,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(if (state.loggingIn) "登录中" else "确认登录")
                    }
                }
            }
            Spacer(Modifier.height(Spacings.lg))
        }

        // Asset section - always visible when logged in
        if (state.hasToken) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("我的资产", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(
                            onClick = vm::refreshBalance,
                            enabled = state.hasToken && !state.loadingBalance,
                        ) {
                            if (state.loadingBalance) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = "刷新资产")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Stable height container to prevent card jumping
                    val balance = state.balance
                    if (state.loadingBalance && balance == null) {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在查询...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else if (balance != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("小票", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(balance.ticketText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("积分", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(balance.pointsText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("积分抵扣金额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(balance.integralAmount ?: "-", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Text("暂无资产信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Historical points stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("积分统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = vm::refreshPointsStats) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "刷新统计")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("累计获得积分", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.totalPointsEarned}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("累计抵扣金额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${state.totalPointsDeducted}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Order history button
            Button(
                onClick = vm::showOrderHistory,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("历史订单")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.logout() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            ) {
                Text("退出登录")
            }
            Spacer(Modifier.height(16.dp))
        }

        // Theme settings card - always visible
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShapes.cardCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("主题设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                val currentMode = themePrefs.getThemeMode()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { themePrefs.setThemeMode(ThemeMode.SYSTEM); vm.updateThemeMode(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMode == ThemeMode.SYSTEM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (currentMode == ThemeMode.SYSTEM) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("跟随系统") }
                    Button(
                        onClick = { themePrefs.setThemeMode(ThemeMode.LIGHT); vm.updateThemeMode(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMode == ThemeMode.LIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (currentMode == ThemeMode.LIGHT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("浅色") }
                    Button(
                        onClick = { themePrefs.setThemeMode(ThemeMode.DARK); vm.updateThemeMode(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMode == ThemeMode.DARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (currentMode == ThemeMode.DARK) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("深色") }
                }
            }
        }
    }
}
@Composable
private fun OrderHistoryDialog(
    orders: List<OrderHistoryItem>,
    onDismiss: () -> Unit,
    onOpenOrder: (OrderHistoryItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("历史订单") },
        text = {
            if (orders.isEmpty()) {
                Text("暂无历史订单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    items(orders) { item ->
                        OrderHistoryRow(item = item, onClick = { onOpenOrder(item) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}
@Composable
private fun OrderHistoryRow(item: OrderHistoryItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(item.goodsName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "订单：${item.orderNo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "原价：${item.originPrice}  小票：${item.ticketCost}  积分：${item.integralCost}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceRow(
    name: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onAddShortcut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onClick),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            TextButton(onClick = onAddShortcut, enabled = enabled) {
                androidx.compose.material3.Icon(Icons.Outlined.Add, contentDescription = "添加到桌面")
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text("桌面")
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
    }
}

@Composable
private fun LoadingText(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
