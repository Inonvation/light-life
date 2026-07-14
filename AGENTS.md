# LightLife - Agent 指南

## 首要原则
- **在任何发布、构建、提交、推送等操作前，必须先向用户阐述方案，等待用户明确同意后再执行**
- 不要自作主张做任何用户没有要求的事情

## 构建与版本
- `scripts\build.bat` / `build.ps1` — 自动管理版本号，构建并归档 APK
- 直接构建：`gradlew :app:archiveDebugApk -PbuildVersionName="x.y.z"`
- APK 归档至 `archive/`，命名格式 `app-debug-v{x.y.z}.apk`
- GitHub Actions Release 中命名为 `LightLife-v{x.y.z}.apk`
- `versionCode`: 每次发布 +1
- `versionName`: 语义化版本，如 "0.0.13"（不带 v 前缀）

## 发布新版本
1. 按用户指定的版本号更新 `app/build.gradle.kts` 中 `defaultConfig.versionName`
2. 构建 APK：运行 `archiveDebugApk` 任务
3. 提交推送：`git add -A && git commit -m "Bump version to x.y.z" && git push`
4. 打 tag（必须指定 `main` 分支）：`git tag vx.y.z main && git push --tags`
5. 告知用户 GitHub Actions 正在自动构建，Release 由工作流自动处理，无需手动 `gh release create`

## 签名
- `app/debug.keystore` 已提交到仓库，本地与 CI 签名一致
- **不要删除或重新生成**，否则存量安装需卸载重装

## 代码规范
- 不要在 Compose 函数外使用 `remember`
- UI 间距/颜色优先使用 `AppStyles.kt` 中的常量（含 `CardShapes.cardCorner`）
- 修改 Kotlin 文件后优先用 Node.js（`fs.writeFileSync`）而非 PowerShell 写入，避免中文乱码

## 注意事项
- Token 随手机号重新登录而变化，App 自动保存最新 Token
- 不要将个人 Token、抓包文件、签名密钥上传到公开仓库
- `PointsTaskRunner.kt` 中的 `ANDROID_SECRET` / `ALIPAY_SECRET` 是接口签名密钥，反编译 APK 也能获取，属于已知暴露项
## Commit 规范
### 前缀类型
| 前缀 | 用途 | 出现在 Release |
|------|------|:---:|
| `feat:` | 新功能 | ✅ |
| `fix:` | 修复 Bug | ✅ |
| `perf:` | 性能优化 | ✅ |
| `refactor:` | 重构（不影响功能） | ✅ |
| `chore:` | 杂项、依赖更新 | ❌ |
| `docs:` | 文档 | ❌ |
| `ci:` | CI/CD 配置 | ❌ |
| `test:` | 测试 | ❌ |
| `build:` | 构建脚本 | ❌ |
| `style:` | 代码格式（无逻辑变更） | ❌ |
| `revert:` | 回滚 | ❌ |

### 基本规则
- 前缀英文小写加冒号，描述中文，例如 `fix: 修复登录页面空指针崩溃`
- **一个 commit 只做一件事**：多个修复点必须拆成多个 commit，不要把不同功能的改动合并到一个 commit 中
- 每个 commit 必须编译通过，不允许提交编译失败的代码
- 保持线性提交历史，不合并（squash）有价值的细粒度 commit
