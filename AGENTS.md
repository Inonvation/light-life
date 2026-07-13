# 瘦刁生活 - Agent 指南

## 构建
- `scripts/build.bat`（Windows）— 自动管理版本号，构建并归档 APK
- `scripts/build.ps1`（Windows PowerShell）— 同上
- 直接构建：`gradlew :app:archiveDebugApk -PbuildVersionName="x.y.z"`
- APK 归档至 `archive/app-debug-v{版本号}.apk`

## 版本管理
- 版本号在 `app/build.gradle.kts` 的 `defaultConfig` 中
- `versionCode`: 每次发布 +1（整数）
- `versionName`: 语义化版本，如 "0.0.11"
- 构建脚本 `build.bat` 自动从版本文件读取并递增 patch

## 签名
- 使用项目内的 `app/debug.keystore`（自定义固定 debug 签名）
- 已配置在 `signingConfigs.fixedDebug`
- 首次安装需先卸载旧版（签名不同）

## 发布流程
- 推送 tag 即可触发 GitHub Actions 自动构建并创建 Release：
  ```bash
  git tag v0.0.x
  git push --tags
  ```
- 工作流定义在 `.github/workflows/release.yml`
- 也可手动发布：`scripts/build.bat` 构建后，用 `gh release create` 上传

## 发布新版本
当用户要求"发布新版本"时，AI 应自动执行以下步骤：
1. 读取当前版本号（`app/build.gradle.kts` 中 `defaultConfig.versionName` 的默认值）
2. 按用户指定的版本号更新 `versionName` 默认值
3. 更新 `archive/` 中对应版本的 APK（运行 `archiveDebugApk` 任务构建）
4. 提交并推送代码：`git add -A && git commit -m "Bump version to x.y.z" && git push`
5. 打 tag 并推送：`git tag vx.y.z && git push --tags`
6. 告知用户 GitHub Actions 正在自动构建中（可在 Actions 页面查看进度）
- 注意：无需手动调用 `gh release create`，GitHub Actions 工作流会自动处理 Release 创建和 APK 上传

## 代码规范
- 不要在 Compose 函数外使用 `remember`
- 所有 UI 间距/颜色优先使用 `AppStyles.kt` 中的常量
- Card 布局统一使用 `CardShapes.cardCorner` 和 `CardShapes.cardElevation`
- 修改 Kotlin 文件后优先用 Node.js（`fs.writeFileSync`）而非 PowerShell 写入，避免中文乱码

## 注意事项
- Token 随手机号重新登录而变化，App 自动保存最新 Token
- 接口返回结构变化时需同步更新数据层代码
- 不要将个人 Token、抓包文件、签名密钥上传到公开仓库
