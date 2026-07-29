# LightLife 速查

基于 [wzs0512/qiekj-android](https://github.com/wzs0512/qiekj-android) 重构的饮水机积分助手。Jetpack Compose + Material3 UI，OkHttp 网络层，R8 全模式压缩。

## 首要原则

**任何发布、构建、提交、推送前，先向用户阐述方案，等用户明确同意。** 不自作主张。

## 构建命令

| 命令 | 用途 |
|------|------|
| `gradlew :app:compileDebugKotlin` | 快速编译检查（改代码后必跑） |
| `gradlew :app:assembleDebug` | Debug APK |
| `gradlew :app:assembleRelease` | Release APK（R8 优化） |
| `gradlew :app:installDebug` | 安装到手机 |
| `gradlew :app:installRelease` | 安装 Release 到手机 |
| `gradlew :app:testDebugUnitTest` | 单元测试 |
| `gradlew :app:lintDebug` | Lint 检查 |

构建脚本 `scripts\build.bat` / `build.ps1`：自动管理版本号，APK 归档至 `archive/`。

**启动：** `adb shell monkey -p com.inonvation.lightlife -c android.intent.category.LAUNCHER 1`

## 源码架构

**包名：** `com.inonvation.lightlife`
**源码路径：** `app/src/main/java/com/inonvation/lightlife/`

| 路径 | 职责 |
|------|------|
| `MainActivity.kt` | 应用入口 |
| `ui/AppUiState.kt` | 所有 UI 状态类型定义 |
| `ui/AppViewModel.kt` | 协调层，委托三个 Controller |
| `ui/auth/AuthController.kt` | 登录/Token 管理 |
| `ui/points/PointsTaskController.kt` | 积分任务启动/暂停/停止 |
| `ui/backup/BackupController.kt` | 备份导出/导入 |
| `ui/screen/` | 各页面组件，每个页面一个文件 |
| `ui/screen/Components.kt` | 跨页面共享组件 |
| `ui/screen/UnlockFlowCards.kt` | 解锁流程 4 种状态卡片 |
| `ui/theme/AppStyles.kt` | UI 间距/颜色常量 |
| `data/PointsTaskRunner.kt` | 积分任务执行核心逻辑 |
| `data/` | API 接口、Repository、Store、Model |
| `service/TaskForegroundService.kt` | 后台前台服务 |

## 代码规范

- 不要在 Compose 函数外使用 `remember`
- UI 间距/颜色优先用 `AppStyles.kt` 常量
- Kotlin 文件确保 UTF-8 编码
- 新增页面或功能按上述结构放置

## 签名

Debug 和 Release 同用 `app/debug.keystore`（alias `androiddebugkey`，password `android`）。**不要删除或重新生成**，否则存量安装需卸载重装。

## Commit 规范

格式：`<type>: <中文描述>`，如 `fix: 修复登录页面空指针崩溃`

| 前缀 | 出现在 Release Notes |
|------|:---:|
| `feat:` / `fix:` / `perf:` / `refactor:` | ✅ |
| `chore:` / `docs:` / `ci:` / `test:` / `build:` / `style:` / `revert:` | ❌ |

- 一个 commit 只做一件事，多个修复拆成多个 commit
- 每个 commit 必须编译通过
- 标题用非技术人员能看懂的语言，专业术语放正文

## 版本与发布

- `app/build.gradle.kts` 中 `defaultConfig.versionName`
- `versionCode` 从 `buildVersionCode` 属性读取
- 发布：更新 versionName → commit → `git push origin main` → `git tag vX.Y.Z` → `git push --tags`
- Release 工作流（`.github/workflows/release.yml`）：推送 `v*` tag 自动构建
- CI（`.github/workflows/ci.yml`）：PR 到 main 时运行 lint → test → assemble

## 交互约定

用户技术背景较弱，表述可能模糊或用词不准。遇到错误表述直接指出 + 替代方案；需求模糊时追问关键信息或列出选项让用户选。

## 注意事项

- `PointsTaskRunner.kt` 中 `ANDROID_SECRET` / `ALIPAY_SECRET` 是接口签名密钥
- 不要上传个人 Token、抓包文件、签名密钥到公开仓库
- Lint 禁用了 `NullSafeMutableLiveData`、`RememberInComposition`、`FrequentlyChangingValue`、`AutoboxingStateCreation`
