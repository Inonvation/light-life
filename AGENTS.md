# LightLife - Agent 指南

## 项目

基于 [wzs0512/qiekj-android](https://github.com/wzs0512/qiekj-android) 重构的校园生活助手（上游为饮水机积分助手）。Jetpack Compose + Material3 UI，OkHttp 网络层，R8 全模式压缩，Release 包约 2MB。

两个平台账号相互独立，界面上也是平权的三个功能：

- **喝水 · 胖乖生活** — 开水、积分、小票、签到
- **洗澡 · 趣智校园** — 淋浴热水器控制（登录、绑定设备、开关阀、结算）
- **快捷方式** — 用户自定义的常用链接，与平台无关

注意：本校（江西水利电力大学）宿舍热水器多为**蓝牙款**，服务器无法远程开阀，淋浴功能在本校受限，见「参考项目」一节。

## 首要原则

**任何发布、构建、提交、推送前，先向用户阐述方案，等用户明确同意。** 不自作主张。

## 构建命令

```bash
gradlew :app:compileDebugKotlin    # 快速编译检查（改代码后必跑）
gradlew :app:assembleDebug         # Debug APK
gradlew :app:assembleRelease       # Release APK（R8 优化，签名同 debug keystore）
gradlew :app:installDebug          # 安装 Debug 到手机
gradlew :app:installRelease        # 安装 Release 到手机
```

**构建脚本** `scripts\build.bat` / `build.ps1`：自动管理版本号，调用 `:app:archiveDebugApk`，APK 归档至 `archive/`。

## ADB 连接（无线调试）

手机用流量开热点 → 电脑连热点 → USB 连接手机 → 执行以下步骤：

```bash
adb devices                    # 确认 USB 已连接
adb tcpip 5555                 # 切换 TCP 模式
adb shell ip addr show wlan2   # 获取热点 IP（inet 行）
adb connect <IP>:5555          # 连接无线调试
```

连接成功后可拔掉 USB 线，通过热点局域网保持连接。

## 版本与发布

- `app/build.gradle.kts` 中 `defaultConfig.versionName`（当前 `3.0.0`），`versionCode` 从 `buildVersionCode` 属性读取
- 发布流程：更新 versionName → commit → `git push origin main` → `git tag vx.y.z` → `git push --tags`
- Release 工作流（`.github/workflows/release.yml`）：推送 `v*` tag 自动构建，从 `RELEASE.md` 或 git log 生成 Release Notes
- CI（`.github/workflows/ci.yml`）：PR 到 main 时运行 `lintDebug` → `testDebugUnitTest` → `assembleDebug`

## 源码架构

**包名**：`com.inonvation.lightlife`
**源码路径**：`app/src/main/java/com/inonvation/lightlife/`（不是 `com/example/devicecontrol/`）

| 路径 | 职责 |
|------|------|
| `MainActivity.kt` | 应用入口，组装 Repository/Store 并注入 ViewModel |
| `ui/AppUiState.kt` | 全局 UI 状态（含趣智子状态 `qzxy: QzxyUiState`） |
| `ui/AppViewModel.kt` | 协调层，委托 AuthController / QzxyController |
| `ui/auth/AuthController.kt` | 胖乖生活平台登录、验证码、Token 管理 |
| `ui/qzxy/QzxyUiState.kt` | 趣智 UI 状态与洗澡状态机（Idle/Starting/Running/Stopping/Done/Failed） |
| `ui/qzxy/QzxyController.kt` | 趣智控制器：登录、蓝牙扫描、洗澡流程、会话失效处理 |
| `ui/qzxy/screen/QzxySection.kt` | 主页洗澡卡：与开水卡统一的三行骨架 + 登录/换设备弹层 |
| `ui/qzxy/screen/QzxyLoginSheet.kt` | 趣智登录底部弹层 |
| `ui/screen/SimpleScreen.kt` | 主页：顶栏（积分）、账户状态行（小票/签到按钮）、开水统一卡、三分区 |
| `ui/screen/SettingsScreen.kt` | 设置页：外观 / 喝水·胖乖生活 / 洗澡·趣智校园 / 通用 四组 |
| `ui/screen/QuickLinksSection.kt` | 首页快捷方式卡片（支持排序、加桌面、自定义图标） |
| `ui/screen/Components.kt` | 跨页面共享组件（含分区小标题 `SectionLabel`） |
| `ui/screen/UnlockFlowCards.kt` | 旧版全屏解锁流程卡，当前未被引用，保留待清理 |
| `ui/theme/AppStyles.kt` | UI 间距/颜色常量（含 `CardShapes.cardCorner`） |
| `data/SignInRunner.kt` | 每日签到执行逻辑 |
| `data/` | API 接口、Repository、Store、Models |
| `data/qzxy/` | 趣智校园模块：ApiConfig、Models（含独立响应包）、AuthStore、QzxyApi、QzxyRepository、BLE 扫描器 |

主页与设置的效果图（含状态表）在 `docs/ui-redesign-mockup.html`，改 UI 前先对照它。

## 参考项目：linyu（淋浴）

趣智校园第三方客户端，已克隆到 `F:\参考\linyu`（GitHub: https://github.com/yehu-imei/linyu ，MIT 协议）。对接趣智校园功能前先看它：

- `API-qzxy.md` — 趣智校园 16 个接口的逆向文档（路径、参数、响应、坑），权威参考
- `PROJECT.md` / `开发者指南.md` — 架构、业务流程与踩坑记录
- 合并方案与实施状态见本仓库 `docs/linyu-merge-plan.md`
- 一期（登录/蓝牙扫描/洗澡控制/结算）已实现；二期（账单、使用码、扫码绑定、寝室绑定）与三期（MQTT、前台服务）未做
- 该项目仅在金华职业技术大学（projectId=905）验证过，适配其他学校看其 README「适配你的学校」章节

**蓝牙款设备（本校关键发现，2026-09-06）**：趣智存在两套控制协议——云端 4G 款走 `/order/tcpDevice/...`（linyu 已实现），蓝牙款（`communicationTypeId=0`）需手机经典蓝牙直发指令，从不连云端，走 tcpDevice 永远报"设备不在线"。本校宿舍设备即蓝牙款，App 目前只做提示不支持控制；逆向细节与四期（蓝牙直控）候选方案见 `docs/qzxy-bt-protocol.md` 与 `docs/linyu-merge-plan.md` 第十章。

## 代码规范

- 不要在 Compose 函数外使用 `remember`
- UI 间距/颜色优先用 `AppStyles.kt` 常量
- Kotlin 文件确保 UTF-8 编码
- 新增页面或功能按上述结构放置

## UI 规范（2026-09 定稿，改界面前必读）

- **三功能平权**：开水、洗澡、快捷方式在主页用同样式卡片 + `SectionLabel` 小标题竖排，新功能默认平级插入，谁也不特殊
- **统一卡片骨架**：开水卡与洗澡卡都是三行——设备行（点设备名弹 bottom sheet 换设备）→ 状态区 → 按钮行（主按钮原地换字）
- **状态原地切换**：进行中/结算/失败等一切状态变化用 `AnimatedContent` + `animateContentSize` 在原卡状态区完成，**不弹窗、不插新卡**，高度变化控制在约一行内；长解释文字收进"查看详情"弹窗
- **视觉基调**：白卡 + 细边框 + 灰字，无渐变、无阴影、无彩色圆形图标，仅按钮用主题色；只有"进行中"状态可用浅绿底强调
- 账号信息保持低调：积分在顶栏，小票/签到在状态行，不要再为大卡

## 签名

Debug 和 Release 同用 `app/debug.keystore`（alias `androiddebugkey`，password `android`）。**不要删除或重新生成**，否则存量安装需卸载重装。

## 测试

```bash
gradlew :app:testDebugUnitTest    # 单元测试
gradlew :app:lintDebug            # Lint 检查
```

测试文件在 `app/src/test/java/com/inonvation/lightlife/`。

## 注意事项

- `ApiConfig.kt` 中 `ANDROID_SECRET` / `ALIPAY_SECRET` 是胖乖生活接口签名密钥，反编译 APK 也能获取
- 不要上传个人 Token、抓包文件、签名密钥到公开仓库
- Lint 禁用了 `NullSafeMutableLiveData`、`RememberInComposition`、`FrequentlyChangingValue`、`AutoboxingStateCreation`

## Commit 规范

格式：`<type>: <中文描述>`，如 `fix: 修复登录页面空指针崩溃`

| 前缀 | 出现在 Release Notes |
|------|:---:|
| `feat:` / `fix:` / `perf:` / `refactor:` | ✅ |
| `chore:` / `docs:` / `ci:` / `test:` / `build:` / `style:` / `revert:` | ❌ |

- 一个 commit 只做一件事，多个修复拆成多个 commit
- 每个 commit 必须编译通过
- 标题用非技术人员能看懂的语言，专业术语放正文

## 交互约定

用户是技术小白，首次开发 Android。表述可能模糊、含错或使用非专业术语。遇到错误表述直接指出 + 替代方案；需求模糊时追问关键信息或列出选项让用户选。

## 写作规范

正文全中文，AI 专有名词不翻译（token、API、LLM 等）。禁止 AI 套话（综上所述、值得注意的是、赋能、抓手、闭环等）。句长错落，具体替代抽象，直接断言。
