# 简洁模式重构方案（供 AI 执行用）

> 本文档是给 AI 的执行提示词。用户已阅读并确认方向。AI 按本文档逐步执行即可。

## 一、背景与目标

LightLife 是一个饮水机积分助手 App，有两个界面模式：

- **普通模式**：4 个 Tab（首页/积分任务/喝水/我的），功能完整。
- **简洁模式**：一个单页界面（`SimpleScreen`），只保留余额、开水、刷积分三个核心功能。

当前简洁模式存在严重问题，需要重构。核心诉求：

1. **隔离**：简洁模式和普通模式彻底隔离。切换模式后**需要重启 App 才生效**（不即时切换）。
2. **精简设置**：简洁模式有自己独立的设置页，只保留必要选项，隐藏用不到的设置。和普通模式设置页**不一样**。
3. **衔接保留**：两个模式共用同一套登录态、积分任务执行核心、后台服务、持久化存储。功能上要做好衔接，不能因为隔离导致功能丢失。

---

## 二、现状代码梳理（必读，避免改错）

### 2.1 模式开关的存储与读取

- 存储类：`data/PointsTaskStateStore.kt`
  - `isSimpleModeEnabled()` / `setSimpleModeEnabled(v)` —— key 为 `"simple_mode"`
  - `isSafeModeEnabled()` / `setSafeModeEnabled(v)` —— key 为 `"safe_mode"`
- State 字段：`ui/AppUiState.kt` 第 122-123 行
  - `simpleModeEnabled: Boolean = false`
  - `safeModeEnabled: Boolean = false`
- ViewModel 读取：`ui/AppViewModel.kt` 第 174-175 行（init 块里从 store 读）
- ViewModel 切换：`ui/AppViewModel.kt` 第 716-722 行 `toggleSimpleMode()`

### 2.2 模式分发入口

`MainActivity.kt` 的 `DeviceControlApp(vm)` 函数：
- 第 278-310 行（重构后的当前状态）：`if (state.simpleModeEnabled)` 分支渲染 `SimpleScreen` + 设置页等二级页的 `AnimatedVisibility`，然后 `return`。
- 否则走普通模式的 `Scaffold` + `HorizontalPager` + 底部导航。

**关键**：当前是**即时切换**——`toggleSimpleMode()` 改 state 后立刻 `SimpleScreen` 就显示/消失。重构要改成**重启生效**。

### 2.3 简洁模式界面现状

`ui/screen/SimpleScreen.kt`：
- 顶部：标题 "LightLife" + 设置齿轮
- 未登录时（第 131-159 行）：显示"请先切换到普通模式登录后再使用简洁版"+ 按钮跳设置。**这是死循环痛点**——登录表单只在 `MeScreen` 里，简洁模式没有登录入口。
- 已登录：余额卡片 + 开水卡片（含设备选择、积分抵扣开关、开水按钮、解锁状态）+ 刷积分卡片（含日志面板、开始/暂停/停止按钮）
- 保险模式开启时（第 401-431 行）：刷积分区变成"保险模式已开启"提示

### 2.4 设置页现状

`ui/screen/SettingsScreen.kt`（普通模式用），分组顺序：
1. **外观**：主题模式、主题配色、日志风格、超级简洁版开关、触感反馈
2. **任务**：任务设置入口、保险模式
3. **健康**：喝水提醒开关
4. **快捷链接**：首页快捷方式开关 + 管理入口
5. **数据**：数据管理入口
6. **账户**：我的 Token、设备信息、退出登录
7. **关于**：版本、GitHub、账号安全、脚本提示、附加说明、免责声明

### 2.5 简洁模式用不到的功能（重构依据）

确认以下功能在简洁模式下**无入口或无意义**：
- **喝水提醒**（`waterReminderEnabled`）：普通模式才有 Water Tab，`WaterReminderManager` 只在 `MainActivity` 第 400 行 Water Tab 里实例化。简洁模式完全没有喝水入口，后台提醒也不会工作。→ 简洁模式设置应隐藏此项。
- **快捷链接**（`quickLinksEnabled`）：普通模式首页（`ControlScreen`）才有，`SimpleScreen` 没有。→ 简洁模式设置应隐藏此项。
- **日志风格**（`logStyle`）：简洁模式刷积分区也用了 `LogPanel`，所以**这个要保留**。

### 2.6 共用的核心逻辑（重构不能破坏）

- **登录**：`AuthController`（`ui/auth/AuthController.kt`），提供 `login()`、`loginWithToken()`、`sendCode()`、`logout()` 等。当前只在 `MeScreen` 调用。
- **积分任务**：`PointsTaskController`（`ui/points/PointsTaskController.kt`），提供 `startPointsTask(ua)`、`pausePointsTask()`、`resumePointsTask()`、`stopPointsTask()`。`SimpleScreen` 和 `PointsTaskScreen` 都调用。
- **后台服务**：`TaskForegroundService`，由 `PointsTaskController` 在 `backgroundTaskEnabled` 时启动。与 UI 模式无关。
- **解锁开水**：`AppViewModel.unlock(device)`。两个模式都调用。
- **数据备份/导入**：`BackupController`。
- **数据管理**：`DataScreen`，两个模式共用。

### 2.7 开机自动任务逻辑

`AppViewModel.kt` 第 214-227 行（init 块）：开机时若已登录 + 开了自动启动 + 非保险模式 + userAgent 已设置，则自动刷积分。**这段逻辑与 UI 模式无关，重构不要动它。**

---

## 三、重构方案

### 3.1 核心改动一：切换模式改为"重启生效"

**目标**：在设置页切换"简洁版"开关后，不立即切换 UI，而是提示用户"重启后生效"，并持久化开关。下次 App 启动时根据开关决定渲染哪个模式。

**改动点：**

#### 3.1.1 `ui/AppViewModel.kt` 的 `toggleSimpleMode()`

当前（第 716-722 行）：
```kotlin
fun toggleSimpleMode() {
    val v = !state.value.simpleModeEnabled
    taskStateStore?.setSimpleModeEnabled(v)
    _state.update { it.copy(simpleModeEnabled = v) }
    if (v) showToast("已切换为简洁模式")
    else showToast("已切换为完整模式")
}
```

改为：
```kotlin
fun toggleSimpleMode() {
    val v = !state.value.simpleModeEnabled
    taskStateStore?.setSimpleModeEnabled(v)
    // 持久化但不更新当前 state 的 simpleModeEnabled（保持当前 UI 不变）
    // 用一个独立字段标记"待重启生效"
    _state.update { it.copy(simpleModePendingRestart = v) }
    if (v) showToast("已选择简洁模式，重启 App 后生效")
    else showToast("已选择完整模式，重启 App 后生效")
}
```

#### 3.1.2 `ui/AppUiState.kt` 新增字段

在设置区新增：
```kotlin
val simpleModePendingRestart: Boolean = false,  // 用户已切换但需重启生效的目标模式
```

**说明**：
- App 启动时，`AppViewModel` init 从 store 读 `simpleModeEnabled` 作为当前生效模式（保持现状）。
- 用户切换时，写 store + 设 `simpleModePendingRestart`，但**不改 `simpleModeEnabled`**（当前 UI 不变）。
- 设置页里"简洁版"开关的 checked 状态显示 `simpleModePendingRestart`（用户刚选的目标值），这样开关本身会立即响应点击，但 UI 模式不变。
- 重启后 init 读 store，`simpleModeEnabled` 变成新值，`simpleModePendingRestart` 重置为 false。

#### 3.1.3 设置页提供"立即重启"入口

在设置页"超级简洁版"开关下方，当 `simpleModePendingRestart != state.simpleModeEnabled`（即有未生效的切换）时，显示一个"立即重启生效"按钮，点击调用重启逻辑。

重启逻辑（放在 `AppViewModel`）：
```kotlin
fun restartApp() {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    // 结束当前进程，让 App 完全重启
    android.os.Process.killProcess(android.os.Process.myPid())
}
```

**注意**：`restartApp` 要在 `AppViewModel` 里新增，`context` 已有（第 58 行 `private val context: Context = application.applicationContext`）。需要确认 `context` 可见性，若不可见则改为可访问。

#### 3.1.4 设置页开关显示逻辑

`SettingsScreen.kt` 第 212-219 行的"超级简洁版"开关，`checked` 改为：
```kotlin
checked = state.simpleModePendingRestart,  // 显示用户选择的目标值
```
副标题改为：
```kotlin
subtitle = "仅显示开水与刷积分功能，切换后需重启 App 生效",
```

---

### 3.2 核心改动二：简洁模式独立设置页

**目标**：简洁模式有自己的设置页 `SimpleSettingsScreen`，只保留必要选项。和普通模式 `SettingsScreen` 不同。

#### 3.2.1 新建 `ui/screen/SimpleSettingsScreen.kt`

保留的设置项（按顺序）：

1. **外观**分组
   - 主题模式（跟随系统/浅色/深色）—— 复用现有 `FilterChip` 写法
   - 主题配色（绿/粉/黄/蓝/棕）—— 复用现有写法
   - 触感反馈开关

2. **任务**分组
   - 任务设置入口（`ClickableRow` → `vm.showTaskSettings()`）—— 简洁模式也要能配置自动执行、后台刷积分、随机延迟、定时任务，这些 `SimpleScreen` 刷积分功能依赖它们
   - 保险模式开关

3. **数据**分组
   - 数据管理入口（`ClickableRow` → `vm.showDataScreen()`）

4. **账户**分组
   - 我的 Token、设备信息、退出登录（复用 `SettingsScreen` 账户区写法）

5. **关于**分组
   - 版本、GitHub、账号安全、脚本提示、附加说明、免责声明（复用现有写法）

6. **模式切换**分组（放最底部）
   - "超级简洁版"开关（`checked = state.simpleModePendingRestart`）
   - 副标题："仅显示开水与刷积分功能，切换后需重启 App 生效"
   - 当有未生效切换时显示"立即重启生效"按钮

**不保留**（相对普通模式设置页删除）：
- 日志风格（简洁模式刷积分区虽用 LogPanel，但为极简化可固定用气泡风格；若想保留也可，由用户决定——见 3.2.2）
- 喝水提醒开关（简洁模式无喝水功能）
- 快捷链接开关 + 管理入口（简洁模式无快捷链接）

**实现要求**：
- 复用 `Components.kt` 里的 `StandardCard`、`ClickableRow`、`SettingSwitchRow`、`SectionHeader`、`SettingsTopBar`。
- 顶栏用 `SettingsTopBar(title = "设置", onBack = { vm.dismissSettings() }, ...)`。
- 关于区的几个 `InfoDialog`（账号安全/脚本提示/附加说明/免责声明）要么复用现有（把 dialog 状态提升），要么在 `SimpleSettingsScreen` 内部用 `remember` 重新管理 `showXxxDialog` 状态。**推荐后者**：在 `SimpleSettingsScreen` 内部 `remember` 管理 4 个 dialog 布尔，`InfoDialog` 是 private 的就在本文件内复制一份调用，或把 `InfoDialog` 改为非 private 共享。先检查 `InfoDialog` 当前可见性，若 private 则改为 internal 或提到 Components。

#### 3.2.2 日志风格固定为终端风格

简洁模式刷积分区用了 `LogPanel(logStyle = state.logStyle, ...)`。`SimpleSettingsScreen` 不暴露日志风格开关，`SimpleScreen` 里 `LogPanel` 的 `logStyle` 固定写死 `LogStyle.TERMINAL`：

```kotlin
LogPanel(
    logStyle = LogStyle.TERMINAL,  // 简洁模式固定终端风格
    logs = state.pointsLogs,
    onClear = { vm.clearPointsLogs() },
    modifier = Modifier.fillMaxWidth(),
    contentHeight = 180.dp,
)
```

理由：终端风格单色等宽、信息密度高、最简约，符合简洁模式定位。

#### 3.2.3 入口切换

`MainActivity.kt` 简洁模式分支（第 278-310 行）里，把渲染 `SettingsScreen` 改为渲染 `SimpleSettingsScreen`：

当前：
```kotlin
AnimatedVisibility(
    visible = state.showSettings,
    enter = slideInHorizontally { it },
    exit = slideOutHorizontally { it },
) {
    SettingsScreen(state = state, vm = vm)
}
```
改为：
```kotlin
AnimatedVisibility(
    visible = state.showSettings,
    enter = slideInHorizontally { it },
    exit = slideOutHorizontally { it },
) {
    SimpleSettingsScreen(state = state, vm = vm)
}
```

简洁模式分支里已有的 `DataScreen`、`TaskSettingsScreen`、`QuickLinksSettingsScreen` 的 `AnimatedVisibility`：
- `DataScreen`、`TaskSettingsScreen` 保留（简洁模式要用）。
- `QuickLinksSettingsScreen` 删除（简洁模式无快捷链接，设置页也不暴露入口，不会触发）。

---

### 3.3 核心改动三：简洁模式自带登录入口

**目标**：解决"简洁模式未登录死循环"。`SimpleScreen` 未登录时直接显示登录表单，登录成功后留在简洁模式。

#### 3.3.1 抽取登录组件

当前登录表单写在 `MeScreen.kt` 第 135-294 行的 `StandardCard` 里，包含：
- 手机号输入、验证码输入 + 发送验证码按钮、登录按钮
- 导入备份登录按钮（`backupLauncher`）
- Token 登录展开区

**抽取为独立 Composable**：在 `ui/screen/Components.kt` 新增（或新建 `ui/screen/LoginCard.kt`）：

```kotlin
@Composable
fun LoginCard(
    state: AppUiState,
    vm: AppViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    backupLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri>,
) {
    StandardCard {
        Column {
            // 把 MeScreen 第 137-294 行的登录表单原样搬过来
            // 包括：登录标题、手机号、验证码+发送、登录按钮、提示、导入备份、Token 登录展开
        }
    }
}
```

**注意点**：
- `backupLauncher` 是 `MeScreen` 里用 `rememberLauncherForActivityResult` 创建的，需要由调用方传入。`SimpleScreen` 和 `MeScreen` 各自创建自己的 launcher（用同样的 contract `ActivityResultContracts.OpenDocument()`，content type `arrayOf("application/json", "application/octet-stream")`）。
- 抽取后 `MeScreen` 原位置改为调用 `LoginCard(state, vm, haptic, backupLauncher)`，行为不变。

#### 3.3.2 `SimpleScreen` 未登录分支改造

当前第 131-159 行的死循环提示，改为调用 `LoginCard`：

```kotlin
if (!state.hasToken) {
    item {
        val backupLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) vm.performBackupImport(context, uri, rememberCoroutineScope())
        }
        Spacer(Modifier.height(Spacings.sm))
        LoginCard(state = state, vm = vm, haptic = haptic, backupLauncher = backupLauncher)
    }
    return@LazyColumn  // 未登录时不显示下面的余额/开水/刷积分卡片
}
```

**需要确认的导入备份方法名**：`MeScreen` 里调用的是什么？去 `MeScreen.kt` 查 backupLauncher 的 onResult 回调，找到 `vm.performXxx` 方法名，`SimpleScreen` 用同一个方法。如果方法签名需要 `Context` 和 `CoroutineScope`，在 `SimpleScreen` 里用 `LocalContext.current` 和 `rememberCoroutineScope()` 传入。

**登录成功后行为**：`AuthController.onAuthSuccess` 已经会 `refreshBalance()` + `refreshDevices()` + `refreshTodayWater()`（第 115-119 行），state.hasToken 变 true 后 `SimpleScreen` 自动从登录卡片切到功能卡片，无需额外处理。

---

### 3.4 核心改动四：禁用未登录时的无意义下拉刷新

`SimpleScreen` 第 108-124 行的 `PullToRefreshBox`，未登录时下拉刷新会触发 `refreshDevices()` + `refreshBalance()`，没 token 时无意义。

改为：
```kotlin
PullToRefreshBox(
    isRefreshing = state.loadingBalance || state.loadingDevices,
    onRefresh = {
        if (state.hasToken) {  // 仅登录后允许刷新
            vm.refreshDevices()
            vm.refreshBalance()
        }
    },
    ...
)
```
或者更彻底：未登录时不包 `PullToRefreshBox`，直接用 `LazyColumn`。**推荐前者**（改动小，且登录后立刻可用）。

---

### 3.5 核心改动五：补齐简洁模式的对话框渲染

`MainActivity.kt` 简洁模式分支（第 278-310 行）需补齐以下渲染，这些目前在 `return` 之后（第 444-449 行）不渲染：

- `state.tokenDialogText?.let { TokenDialog(...) }`
- `if (state.showOrderHistory) { OrderHistoryBottomSheet(...) }`
- `state.deviceInfoDialogText?.let { TokenDialog(..., title = "设备信息") }`（设置页设备信息弹窗）
- `if (state.showLogoutConfirm) { ... }`（退出登录确认，含备份导出 launcher）
- `if (state.showBackupTokenExpiredDialog) { ... }`（备份导入 token 过期确认）

**注意**：退出登录确认对话框里有 `exportLauncher`（备份导出），需要 `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"))`。这个 launcher 当前在 `DeviceControlApp` 第 203-208 行创建，简洁模式分支也能访问到（因为在同一个 Composable 作用域）。所以把这些对话框渲染挪到简洁模式分支的 `Box` 内即可，launcher 共用。

**具体做法**：把 `return` 之后的对话框/BottomSheet 渲染区（第 444-449 行及相关的 `showLogoutConfirm`、`showBackupTokenExpiredDialog` 两个 AlertDialog，第 199-276 行）提取为一个独立 Composable，例如 `SharedDialogs(state, vm, context, exportLauncher)`，放在 `MainActivity` 顶层或新文件。然后：
- 简洁模式分支 `Box` 内调用 `SharedDialogs(...)`。
- 普通模式 `Scaffold` 外调用 `SharedDialogs(...)`。

这样两个模式共用同一套对话框，避免重复。

---

## 四、文件改动清单

| 文件 | 改动 |
|------|------|
| `ui/AppUiState.kt` | 新增 `simpleModePendingRestart` 字段 |
| `ui/AppViewModel.kt` | 改 `toggleSimpleMode()`；新增 `restartApp()`；init 里读 store 后同步 `simpleModePendingRestart = simpleModeEnabled` |
| `ui/screen/Components.kt`（或新建 `LoginCard.kt`） | 新增 `LoginCard` Composable；`InfoDialog` 若 private 改为可共享 |
| `ui/screen/SimpleSettingsScreen.kt` | **新建**，简洁模式独立设置页 |
| `ui/screen/SimpleScreen.kt` | 未登录分支改用 `LoginCard`；下拉刷新加 hasToken 判断；`LogPanel` 的 logStyle 写死 `LogStyle.BUBBLE` |
| `ui/screen/MeScreen.kt` | 登录卡片区改为调用 `LoginCard`（行为不变） |
| `ui/screen/SettingsScreen.kt` | "超级简洁版"开关 checked 改为 `simpleModePendingRestart`；副标题改"重启生效"；加"立即重启"按钮 |
| `MainActivity.kt` | 简洁模式分支：SettingsScreen→SimpleSettingsScreen，删 QuickLinksSettings 渲染，补 SharedDialogs；提取 SharedDialogs Composable；普通模式也用 SharedDialogs |

---

## 五、执行顺序（建议分步提交）

1. **第一步：抽取 `LoginCard`** —— 从 `MeScreen` 抽出登录组件到 `Components.kt`/`LoginCard.kt`，`MeScreen` 改为调用。编译验证。这一步独立，不涉及模式逻辑。
2. **第二步：新建 `SimpleSettingsScreen`** —— 参照 `SettingsScreen` 写精简版。编译验证。
3. **第三步：改 `SimpleScreen`** —— 未登录用 `LoginCard`，下拉刷新加判断，日志风格写死。编译验证。
4. **第四步：改模式切换为重启生效** —— `AppUiState` 加字段，`AppViewModel` 改 `toggleSimpleMode` 加 `restartApp`，`SettingsScreen` 改开关显示。
5. **第五步：改 `MainActivity`** —— 简洁分支用 `SimpleSettingsScreen`，提取 `SharedDialogs`，补齐对话框渲染。
6. **每步都跑 `gradlew :app:compileDebugKotlin` 确认编译通过。**

---

## 六、硬约束（不可违反）

1. **5 连点积分任务 Tab 的隐藏入口保留不动**（调试用，在 `MainActivity` 第 352-361 行）。
2. **165 秒倒计时时长不改**（饮水机自动结算真实周期，在解锁流程相关代码里）。
3. **签名 `app/debug.keystore` 不删除不重新生成**。
4. **不上传个人 Token、抓包文件、签名密钥到公开仓库**。
5. **Kotlin 文件确保 UTF-8 编码**。
6. **不要在 Compose 函数外使用 `remember`**。
7. **UI 间距/颜色优先用 `AppStyles.kt` / `AppThemes.kt` 里的 `Spacings`、`AppColors`、`CardShapes` 常量**，不要硬编码 dp/颜色。
8. **每个 commit 必须编译通过，commit 格式 `<type>: <中文描述>`**（fix/feat/perf/refactor 进 Release Notes，chore/style 等不进）。
9. **git commit 用 Bash 工具执行，不用 PowerShell here-string**。

---

## 七、验证清单（改完逐项确认）

- [ ] 普通模式设置页切换"简洁版"开关 → 开关响应但 UI 不变 → 提示重启 → 点重启 → App 重启后进入简洁模式
- [ ] 简洁模式设置页切换"简洁版"开关 → 同上，重启后回到普通模式
- [ ] 简洁模式未登录 → 显示登录表单（手机号+验证码+Token 登录+导入备份）→ 登录成功 → 自动显示余额/开水/刷积分
- [ ] 简洁模式设置页只包含：外观（主题模式/配色/触感）、任务（任务设置入口/保险模式）、数据（数据管理入口）、账户（Token/设备信息/退出登录）、关于、模式切换。**不含**喝水提醒、快捷链接、日志风格。
- [ ] 简洁模式点"任务设置"能进 TaskSettingsScreen 并正常配置
- [ ] 简洁模式点"数据管理"能进 DataScreen
- [ ] 简洁模式退出登录确认对话框、备份导入对话框能正常弹出（不是空白）
- [ ] 简洁模式未登录时下拉刷新不触发无意义请求
- [ ] 简洁模式刷积分功能正常（开始/暂停/继续/停止）
- [ ] 简洁模式开水功能正常（设备选择、解锁、状态显示）
- [ ] 普通模式所有原有功能不受影响（4 Tab、设置页、5 连点彩蛋入口）
- [ ] `gradlew :app:compileDebugKotlin` 通过
- [ ] `gradlew :app:testDebugUnitTest` 通过

---

## 八、风险点与注意事项

1. **重启 App 的方式**：`Process.killProcess` + 重新 startActivity 是常见做法，但部分设备/版本可能有兼容问题。如果重启不彻底，备选方案是用 `finishAffinity()` + startActivity，但那不会重建 Application。优先用 killProcess 方案，测试不行再调整。

2. **`simpleModePendingRestart` 的初始化**：App 首次启动时（用户从没切过），`simpleModePendingRestart` 应等于 `simpleModeEnabled`（当前生效值），这样开关显示当前真实状态。在 `AppViewModel` init 里：
```kotlin
_state.update { s -> s.copy(
    ...
    simpleModeEnabled = it.isSimpleModeEnabled(),
    simpleModePendingRestart = it.isSimpleModeEnabled(),  // 初始一致
    ...
) }
```

3. **`SharedDialogs` 里退出登录对话框的 exportLauncher**：这个 launcher 需要 `rememberLauncherForActivityResult`，必须在 Composable 作用域创建。提取 `SharedDialogs` 时，launcher 作为参数从外层（`DeviceControlApp`）传入，不要在 `SharedDialogs` 内部 remember（因为 `SharedDialogs` 可能被多个地方调用，launcher 应共享）。实际上 `DeviceControlApp` 第 203-208 行已有这个 launcher，直接传入 `SharedDialogs` 即可。

4. **`InfoDialog` 可见性**：先 Grep 确认 `InfoDialog` 是 `private` 还是 public。如果 private 在 `SettingsScreen.kt`，`SimpleSettingsScreen` 无法调用。解决办法：把 `InfoDialog` 及其依赖移到 `Components.kt` 改为非 private，或复制一份。推荐移到 `Components.kt` 共享。

5. **`SimpleScreen` 的 `backupLauncher` 回调方法名**：执行前先读 `MeScreen.kt` 里 `backupLauncher` 的 onResult，确认调用的 `vm` 方法名和参数，`SimpleScreen` 保持一致。

6. **不要动 `PointsTaskController`、`AuthController`、`TaskForegroundService`、`PointsTaskRunner` 的核心逻辑**——这些是共用层，只动 UI 分发和设置页。

7. **备份导入相关**：`BackupController` 在导入备份时会设置 `simpleModeEnabled`（`ui/backup/BackupController.kt` 第 159-161 行）。重构后导入备份若触发了模式切换，同样需要重启生效。检查 `BackupController` 第 159-161 行，把 `updateState { s -> s.copy(simpleModeEnabled = enabled) }` 改为同时设 `simpleModePendingRestart = enabled`，或保持原样但确保不破坏"重启生效"语义。**执行时先读这段代码再决定**，原则是：备份导入不应绕过重启机制直接切模式。
