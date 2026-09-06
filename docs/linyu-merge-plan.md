# 淋浴 (LinYu) 功能接口合并方案

> 状态：一期（洗澡控制）已实现并通过编译与单元测试，待真机验证
> 参考项目：https://github.com/yehu-imei/linyu （MIT 协议，可自由参考代码）

## 一、先说清楚一件事：两个项目对接的是不同平台

这件事决定了整个方案的形态，必须先讲明白。

| | 你现在的 LightLife | linyu（淋浴） |
|---|---|---|
| 平台 | 缺渴平台 | 趣智校园 |
| 服务器 | `userapi.qiekj.com` | `v3-api.china-qzxy.cn` |
| 设备 | 饮水机（打水） | 校园热水器（洗澡） |
| 登录方式 | 手机号 + 短信验证码 → token | 手机号 + 密码（MD5 后取后 10 位大写）→ loginCode |
| 会话凭证 | token（放 Header） | loginCode（放 URL 参数 / 表单字段） |
| 积分体系 | 有（tokenCoin / integral） | 无（钱包余额 + 账单） |

所以这不是"在现有接口列表里加几行"，而是**给 LightLife 接入第二套平台**：新增一套独立的登录、独立的会话存储、独立的接口层。现有缺渴功能一行都不动，两套账号互不干扰。

**好消息**：两家都是校园水电设备平台，接口风格类似（表单 POST + JSON 响应），你项目里现成的网络栈（Retrofit + Moshi + OkHttp）可以直接复用，不用引新框架。

## 二、linyu 项目是什么、做到了什么程度

趣智校园的第三方 Android 客户端，Kotlin + Compose + Material 3，和你项目技术栈一致。12 个 commit，v1.2.0，只在**金华职业技术大学**（projectId=905）测试过。它的价值主要在两份逆向成果：

1. `API-qzxy.md` — 趣智校园 16 个接口的完整逆向文档（路径、参数、响应、坑）
2. 业务流程细节 — 开阀后 orderNo 要靠 MQTT 或轮询拿、errorCode 307 表示设备占用中、POST 请求要同时传 `telephone` 和 `telPhone` 等

这些逆向成果是可以直接搬用的，省掉自己抓包的全过程。

## 三、功能接口清单与取舍

linyu 全部能力共 17 项，逐项评估如下。

### 第一期：洗澡控制（核心，建议先做）

| 接口 | 方法/路径 | 作用 | 备注 |
|---|---|---|---|
| 密码登录 | POST `/user/login` | 拿 loginCode / userId / accountId / projectId | projectId 从登录响应读，不硬编码 |
| 钱包余额 | GET `/account/wallet` | 显示趣智钱包余额 | |
| 设备详情 | GET `/device/info/mac` | 按 MAC 拿 snCode / 设备名 / 预扣金额 | 控制设备的前提 |
| 蓝牙扫描 | 无 HTTP，系统 BLE API | 发现附近热水器，按信号排序 | 过滤名 `KLCXKJ-Water` 可配置 |
| 查进行中订单 | POST `/order/tcpDevice/query/rateOrder/using` | 拿 orderNo；恢复中断订单；检测"使用码启动"的设备 | errorCode 307 = 设备被占用 |
| 开始洗澡 | POST `/order/tcpDevice/downRate/rateOrder` | 开阀 | 开阀后 orderNo 不直接返回，轮询上一行接口拿 |
| 开阀确认 | POST `/order/tcpDevice/query/downRateResult` | 确认真的开成功了 | 失败给出提示 |
| 停止洗澡 | POST `/order/tcpDevice/closeOrder` | 关阀 | |
| 关阀确认 | POST `/order/tcpDevice/closeOrder/result/query` | 确认真的关成功了 | 失败提示重试 |
| 消费结算 | POST `/order/consumeOrder/result/query` | 关阀后显示本次花了多少钱 | |

配套 UI：登录页（手机号+密码）、设备列表区块、洗澡中卡片（计时 + 预扣金额 + 本次消费）。手输 MAC 地址作为蓝牙不可用时的兜底。

### 第二期：账单与使用码

| 接口/功能 | 作用 | 备注 |
|---|---|---|
| GET `/order/query/account/bill/list` | 当月消费记录 | 参数 `month=yyyy-MM&billRequestType=2` |
| GET `/order/query/account/bill/detail` | 单笔账单详情 | |
| 余额估算 | 手动输初始余额，按账单倒推 | 一卡通真实余额拿不到（易校园 API 有 native 层签名保护），linyu 也只能估算 |
| GET `/account/useCode/new` 等 3 个接口 | 使用码查看 / 重新生成 / 远程开关 | 在热水器键盘输使用码也能启动 |
| 扫码绑定 | 扫设备二维码直接弹设备详情 | 用 zxing-android-embedded（约 +1MB），不用 linyu 的 ML Kit（要 +3~4MB） |
| 绑定寝室 | 关键词过滤设备列表 | |
| 挤号提示 | 在别的设备登录同一账号时提示 | 检测"登录失效"关键词 + HTTP 401/403，你项目已有同类机制可参考 |

### 第三期：实时推送（可选，可以不做)

| 功能 | 说明 | 为什么放最后 |
|---|---|---|
| MQTT 实时推送 | 订阅 `app_downRate_{手机号}` 等 3 个主题 | 连接 `tcp://47.107.37.60:1883`（明文无 TLS）。但 linyu 自己承认：消费金额只在订单结束时推送，洗澡中看不到实时扣费，官方 App 也一样。所以它的实际增益只有"更快拿到 orderNo"，而一期的 HTTP 轮询（800ms×10 次）已经够快。要接的话需引入 Paho（+0.3MB）并在 NetworkSecurityConfig 放行该 IP 的明文流量 |
| 前台服务保活 | 熄屏后继续计时/轮询 | 可参考现有 TaskForegroundService 模式 |

### 明确不做

| 功能 | 原因 |
|---|---|
| 短信验证码登录（`/user/verification/code/get`、`/user/registerAndLogin`） | 接口的 secret 参数绑定个人账号，抓包者自己的账号才有效，做出来别人用不了。linyu 官方也标注"未完善，请用密码登录" |
| 深浅主题、加密存储、下拉刷新 | LightLife 全都有了，直接复用 |

## 四、代码怎么放（遵循现有架构）

按 AGENTS.md 的分层约定，趣智相关代码独立成两个子包，不污染现有代码：

```
data/qzxy/
  QzxyApiConfig.kt      # BASE_URL、version=6.5.24、可配置项默认值（BLE 过滤名等）
  QzxyApi.kt            # Retrofit 接口定义（一期 10 个）
  QzxyModels.kt         # QzxyEnvelope<T>、登录/设备/订单/账单/使用码 数据模型
  QzxyRepository.kt     # 业务封装：登录态管理、开阀轮询、关阀确认、结算
  QzxyAuthStore.kt      # loginCode/userId/accountId/projectId 加密存储（复用 EncryptedSharedPreferences）
  QzxyPassword.kt       # MD5 取后 10 位大写
  QzxyBluetoothScanner.kt  # BLE 扫描

ui/qzxy/
  QzxyController.kt     # 仿照 AuthController 的委托模式，挂进 AppViewModel
  QzxyUiState.kt        # 独立状态类：登录态、扫描结果、订单流程状态
  screen/
    QzxyLoginSheet.kt   # 登录底部弹层
    QzxyDeviceSection.kt  # 主页设备区块
    QzxyShowerCard.kt   # 洗澡中卡片（复用 UnlockFlowCards 的状态卡片模式）
```

关键设计决定：

1. **响应包独立**。缺渴是 `{code, msg, data}`，趣智是 `{success, errorCode, errorMessage, data}`。新建 `QzxyEnvelope<T>`，不硬塞进现有 `ApiEnvelope`。会话失效判定独立成 `QzxySessionExpiredException`（趣智靠关键词 + 401/403，缺渴靠 code）。
2. **独立 Retrofit 实例**。不同 baseUrl，趣智的认证是参数不是 Header，所以不需要现有 `HeaderInterceptor`。
3. **R8 混淆不用担心**。linyu 遇到的泛型擦除问题 是 Retrofit+Gson 特有的，你项目用 Moshi 且现有 suspend + 泛型接口已在 R8 全模式下正常运行，新接口照现有写法即可。
4. **AppUiState 组合而非膨胀**。现有 `AppUiState` 加一个 `val qzxy: QzxyUiState = QzxyUiState()` 字段，趣智自己的状态都收在子类里。
5. **可配置项**。projectId（登录响应自动获取）、BLE 设备名过滤、MAC 前缀、MQTT 地址，全部做成设置页里的"趣智校园"分区，默认值取 linyu 的实测值。换学校只改配置。

## 五、需要新增的权限与依赖

| 项 | 用途 | 体积影响 |
|---|---|---|
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | Android 12+ 扫描热水器 | 0 |
| `ACCESS_FINE_LOCATION` | Android 11 及以下扫描需要 | 0 |
| （二期）`CAMERA` | 扫码 | 0 |
| （二期）zxing-android-embedded | 扫码识别 | 约 +1MB |
| （三期）Eclipse Paho MQTT | 实时推送 | 约 +0.3MB |

一期不引任何新依赖，Release 包仍在 2MB 量级。整包做完预计 3MB 上下。

## 六、实施顺序（每步都可编译、可提交）

按 commit 规范拆分，每步做完跑 `gradlew :app:compileDebugKotlin`：

1. `feat: 新增趣智校园登录与会话存储` — QzxyApiConfig / QzxyApi / QzxyModels / QzxyPassword / QzxyAuthStore / 登录弹层
2. `feat: 新增热水器蓝牙扫描与设备列表` — BluetoothScanner / 设备区块 / 手输 MAC 兜底
3. `feat: 新增洗澡开始与停止控制` — 开阀/轮询 orderNo/关阀/确认，洗澡中卡片
4. `feat: 新增洗澡消费结算` — 结算查询，成功卡片显示金额
5. （二期）`feat: 新增趣智账单查询` / `feat: 新增使用码管理` / `feat: 新增扫码绑定设备` / `feat: 新增寝室绑定筛选`
6. （三期，可选）`feat: 接入趣智 MQTT 推送` / `feat: 洗澡前台服务保活`

## 七、风险与限制（如实告知）

1. **适配范围未验证**。linyu 只在金华职业技术大学测过。你学校能不能用，取决于学校热水器是不是趣智校园系统、接口有没有定制。第一台设备连通之前，一切未定。
2. **只能密码登录**。趣智官方 App 里要先设置好密码。如果你只有验证码登录习惯，先去官方 App 设置密码。
3. **一卡通余额拿不到**，只能手动输入初始余额估算（linyu 同样如此，易校园的签名在 native 层，绕不过）。
4. **安全性和官方一样差**：密码是 MD5 截断，MQTT 明文。这是趣智协议本身的问题，第三方客户端只能遵循。
5. **挤号**：同一趣智账号只能一处在线，本 App 和官方 App 同时登录会互相踢下线，这是平台规则。

## 八、UI 状态与排版细化

### 8.1 状态结构：两层，各管各的

沿用项目现有两套机制，趣智模块不做新发明：

1. **区块状态** `QzxyUiState`（数据类）— 管"主页淋浴区块显示什么"：登录态、扫描结果、钱包余额、弹层开关。
2. **流程状态机** `QzxyShowerState`（密封类）— 管"一次洗澡进行到哪一步"，仿照现有 `UnlockFlowState` 的 Idle/PreChecking/Working/Success/Failed 写法。

```kotlin
// ui/qzxy/QzxyUiState.kt
data class QzxyUiState(
    // ── 登录（趣智独立账号，与缺渴互不影响）──
    val loggedIn: Boolean = false,
    val userName: String = "",            // 官方账号姓名，显示用
    val showLoginSheet: Boolean = false,  // 登录底部弹层
    val phone: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loggingIn: Boolean = false,
    val loginError: String? = null,

    // ── 设备发现 ──
    val scanning: Boolean = false,
    val nearbyDevices: List<QzxyNearbyDevice> = emptyList(), // mac/名称/信号档位
    val lastDevice: QzxyLastDevice? = null,  // 上次使用的设备，一键开始
    val showManualMacDialog: Boolean = false,// 手输 MAC 兜底弹窗

    // ── 洗澡流程（状态机，见下）──
    val showerFlow: QzxyShowerState = QzxyShowerState.Idle,
    val elapsedSeconds: Int = 0,          // 洗澡计时，独立于现有 unlockElapsedSeconds

    // ── 钱包 ──
    val wallet: QzxyWallet? = null,
    val loadingWallet: Boolean = false,
)
```

```kotlin
sealed class QzxyShowerState {
    data object Idle : QzxyShowerState()          // 无订单
    data class Starting(val step: String) : QzxyShowerState()
    // step 覆盖：发送开阀指令 → 轮询订单号(800ms×10) → 开阀结果确认
    data class Running(
        val deviceName: String,
        val withholdMoney: String,          // 预扣金额
        val autoCloseSecondsLeft: Int? = null, // autoDisConTime 闲置关停倒计时，接口可能不给
    ) : QzxyShowerState()
    data class Stopping(val step: String) : QzxyShowerState()
    // step 覆盖：发送关阀指令 → 关阀结果确认 → 查询本次消费
    data class Done(val result: QzxySettleResult) : QzxyShowerState()
    // result：设备名 / 时长 / 本次消费金额
    data class Failed(
        val message: String,   // 给用户看的
        val step: String,      // 哪一步
        val rawError: String,  // 原始报错，排查用
    ) : QzxyShowerState()
}
```

状态与卡片的对应关系：

| QzxyShowerState | 界面表现 |
|---|---|
| Idle | 不显示洗澡卡，区块只显示设备列表 |
| Starting | 洗澡卡：步骤文字 + 转圈，不可点"开始" |
| Running | 洗澡卡：计时 + 设备名 + 预扣金额 + 闲置倒计时（有才显示）+【结束使用】 |
| Stopping | 洗澡卡：步骤文字 + 转圈（"正在关阀…"/"正在查询本次消费…"） |
| Done | 结算卡：本次消费金额 + 设备名 + 时长，点【完成】回到 Idle |
| Failed | 失败卡：原因 + 发生步骤 +【重试】【关闭】 |

会话失效（挤号/过期）不进状态机：仿照 `AuthController.handleTokenExpired()`，弹 Toast + 自动弹出登录弹层。

### 8.2 状态怎么进 AppUiState

现有 `AppUiState` 只加一个组合字段，趣智的全部字段收在子状态里，缺渴字段一个不动：

```kotlin
data class AppUiState(
    …现有字段不动…
    val qzxy: QzxyUiState = QzxyUiState(),
)
```

AppViewModel 里加一个私有辅助方法，避免每处都写 `s.copy(qzxy = s.qzxy.copy(...))`：

```kotlin
private fun updateQzxy(reduce: (QzxyUiState) -> QzxyUiState) {
    _state.update { it.copy(qzxy = reduce(it.qzxy)) }
}
```

`QzxyController` 持有 `updateQzxy` 引用，仿照 `AuthController` 的构造参数传法（state / updateState / scope / repository / 回调）。Toast 和报错复用现有 `UiEvent` 通道，不另开。

### 8.3 主页排版：淋浴区块插在"开水"和"签到"之间

现有主页自上而下是：统计 / 快捷方式 / 开水 / 签到。淋浴和开水同属"设备操作"，紧挨着放：

```
┌────────────────────────────────┐
│ LightLife                 ⚙    │
│ 已登录 · 趣智 已连接            │
├────────────────────────────────┤
│ 统计区块（现状，不动）          │
│ 快捷方式区块（现状，不动）      │
│ 开水区块（现状，不动）          │
├────────────────────────────────┤
│ 🚿 淋浴 · 趣智校园    钱包 12.40 │
│                                │
│ ┌────────────────────────────┐ │
│ │ 洗澡中 · 03:25             │ │ ← 仅 Starting/Running/Stopping
│ │ 热水器-1号楼-301            │ │    时显示；Done 换成结算卡
│ │ 预扣 2.00 · 闲置关停 04:59  │ │
│ │ 【结束使用】                │ │
│ └────────────────────────────┘ │
│                                │
│ 【扫描附近设备】 【手输 MAC】    │
│ ● 热水器-301    信号强  -58dBm  │ ← 信号三档：强≥-70 / 中≥-85 / 弱
│ ○ 热水器-302    信号中  -76dBm  │
│                                │
│ 上次使用：热水器-301  【开始】   │ ← lastDevice 有值才显示
├────────────────────────────────┤
│ 签到区块（现状，不动）          │
└────────────────────────────────┘
```

区块内容按登录态递进：

| 状态 | 区块显示 |
|---|---|
| 未登录趣智 | 一行说明 +【连接趣智校园】按钮，点了弹登录弹层 |
| 已登录、还没扫描过 | 扫描按钮 + 手输 MAC 入口 |
| 扫描中 | 转圈提示，设备列表实时追加 |
| 有结果 | 设备列表（点选后列表项高亮，显示预扣金额），【开始】按钮启动 |
| 洗澡中 | 洗澡卡置顶，主页仍可正常滚动、操作缺渴功能 |

登录是底部弹层（BottomSheet），不整页跳转——和现有 Token 弹层、设置弹层的交互习惯一致。弹层内容：手机号 + 密码（带可见切换）+ 错误提示一行。

### 8.4 洗澡卡为什么不做全屏

现有开水解锁用全屏流程卡（UnlockFlowCards），但那次流程只持续一两分钟，全屏锁死可以接受。洗澡动辄十几分钟，全屏会挡住签到、开水等其他功能，所以淋浴用**常驻区块卡片**：熄屏、切走、回来，状态都还在（状态在 ViewModel 里，跟现有解锁计时同一套保活方式）。

## 九、开始前需要你确认的 4 件事

1. **你学校洗澡的热水器，是用"趣智校园"官方 App 控制的吗？**（官方 App 图标搜"趣智"）如果不是，这套接口全部用不上，方案作废。
2. **你在官方 App 里设置过登录密码吗？** 没有的话先去设置，本方案不支持短信登录。
3. **功能范围**：三个包（一期洗澡控制 → 二期账单+使用码+扫码 → 三期 MQTT 推送）都要，还是只要一期？
4. **界面位置**：我的建议是主页新增一个"淋浴"区块（和现有"开水"区块并列，样式一致）。如果你想要独立二级页面或其他摆法，说一声。
