# LightLife 速查

**包名：** `com.inonvation.lightlife`
**源码路径：** `app/src/main/java/com/inonvation/lightlife/`
**构建：** `gradlew :app:compileDebugKotlin`（编译检查）
**安装：** `gradlew :app:installDebug`
**启动：** `adb shell monkey -p com.inonvation.lightlife -c android.intent.category.LAUNCHER 1`

| 路径 | 职责 |
|------|------|
| `ui/AppUiState.kt` | 所有 UI 状态类型 |
| `ui/AppViewModel.kt` | 协调层，委托三个 Controller |
| `ui/auth/AuthController.kt` | 登录/Token 管理 |
| `ui/points/PointsTaskController.kt` | 积分任务控制 |
| `ui/backup/BackupController.kt` | 备份导出/导入 |
| `ui/screen/Components.kt` | 共享组件 |
| `data/PointsTaskRunner.kt` | 积分任务执行（核心逻辑） |
| `data/` | API、存储、Model |

**发布：** 更新 `versionName` → commit → `git push origin main` → `git tag vX.Y.Z` → `git push --tags`
