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

## 代码规范
- 不要在 Compose 函数外使用 `remember`
- 所有 UI 间距/颜色优先使用 `AppStyles.kt` 中的常量
- Card 布局统一使用 `CardShapes.cardCorner` 和 `CardShapes.cardElevation`
- 修改 Kotlin 文件后优先用 Node.js（`fs.writeFileSync`）而非 PowerShell 写入，避免中文乱码

## 注意事项
- Token 随手机号重新登录而变化，App 自动保存最新 Token
- 接口返回结构变化时需同步更新数据层代码
- 不要将个人 Token、抓包文件、签名密钥上传到公开仓库
