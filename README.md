# LightLife

将胖乖生活核心流程（饮水设备解锁 + 积分自动化）重构成原生 Android 应用，去除臃肿功能和广告，保留最实用的部分。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material3 |
| 网络 | OkHttp + Retrofit |
| 序列化 | Moshi |
| 存储 | SharedPreferences + JSON 文件 |
| 构建 | Gradle + AGP 8.7.3 |

---

## 功能

### 饮水设备控制
- 一键解锁：选择历史设备，调用接口解锁，订单自动保存
- 今日统计：自动计算当日喝水次数与积分抵扣金额
- 订单记录：查看历史订单详情（原价、花券、积分抵扣）

### 自动化积分任务
- 签到 + 环境检查 + 浏览任务 + 任务列表
- APP 视频广告 × 20 次，支付宝视频广告 × 50 次
- 断点续跑：中断后自动恢复进度，无需从头开始
- 暂停 / 继续 / 结束，实时进度条 + 执行日志
- 日志本地持久化，支持查看历史执行记录

### 界面与交互
- 底部导航：首页 / 积分任务 / 我的
- 主题支持：跟随系统 / 浅色 / 深色
- 触感反馈：按钮和开关操作附带振动
- Token 查看：一键复制当前登录凭据

### 数据与存储
- Token 自动保存，退出登录一键清除
- 积分统计：累计获得积分与抵扣金额持久化
- 订单历史：JSON 文件本地存储
- 任务状态：SharedPreferences 持久化，跨天自动归零

---

## 项目结构

```
app/src/main/java/com/example/devicecontrol/
├── MainActivity.kt                 # 应用入口
├── data/                           # 数据层
│   ├── ApiConfig.kt                # API 配置
│   ├── AppRepository.kt            # 数据仓库
│   ├── BackupManager.kt            # 备份管理
│   ├── DeviceApi.kt                # 设备 API 接口
│   ├── EmptyDataJsonAdapter.kt     # JSON 适配器
│   ├── HeaderInterceptor.kt        # 请求头拦截器
│   ├── LenientStringJsonAdapter.kt # JSON 适配器
│   ├── Models.kt                   # 数据模型
│   ├── OrderHistoryStore.kt        # 订单历史存储
│   ├── PointsStatsStore.kt         # 积分统计存储
│   ├── PointsTaskRunner.kt         # 积分任务执行器
│   ├── PointsTaskStateStore.kt     # 任务状态存储
│   ├── TaskLogStore.kt             # 任务日志存储
│   └── TokenStore.kt               # Token 存储
├── ui/                             # UI 层
│   ├── AppViewModel.kt             # 主 ViewModel
│   ├── ShortcutUtils.kt            # 快捷方式工具
│   └── screen/                     # 页面
│       ├── Components.kt           # 通用组件
│       ├── ControlScreen.kt        # 设备控制页
│       ├── MeScreen.kt             # 个人中心页
│       ├── OrderDetailDialog.kt    # 订单详情弹窗
│       ├── OrderHistoryDialog.kt   # 订单历史弹窗
│       ├── PointsTaskScreen.kt     # 积分任务页
│       ├── SettingsScreen.kt       # 设置页
│       └── TokenDialog.kt          # Token 弹窗
└── res/                            # 资源文件
```

---

## 使用方法

### 登录
1. 进入「我的」页面，输入手机号发送验证码
2. 输入验证码确认登录，Token 自动保存

### 解锁设备
1. 「首页」等待历史设备列表加载
2. 点击目标设备一键解锁，完成后自动记录订单

### 刷积分
1. 确保已登录，进入「积分任务」页面
2. 点击「开始执行自动化任务」
3. 可随时暂停 / 继续 / 结束任务
4. 任务中断后重新执行会自动恢复进度

---

## 构建

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 35

### 构建步骤
```bash
# 克隆仓库
git clone https://github.com/your-username/light-life.git
cd light-life

# 使用 Android Studio 打开项目，或命令行构建
./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK

# 使用构建脚本（自动管理版本号）
scripts\build.bat             # Windows
scripts\build.ps1             # PowerShell
```

### APK 归档
构建脚本会自动将 APK 归档到 `archive/` 目录，命名格式：`app-debug-v{版本号}.apk`

---

## TODO

- [x] 刷积分脚本断点续跑功能
- [x] 备份与恢复功能
- [ ] 积分统计显示为零
- [ ] 「我的」界面重写
- [ ] 日志格式与存储逻辑优化
- [ ] 主题适配全面优化
- [ ] 执行日志图形化界面


---

> [!NOTE]
> - Token 随手机号重新登录而变化，App 自动保存最新 Token
> - **不要将个人 Token、调试签名、API 密钥上传到公开仓库**
> - `app/debug.keystore` 是本地调试签名，**请勿删除**

---

> [!CAUTION]
> 本项目为个人兴趣开发，**仅供学习和测试使用**。
>
> 自动化积分功能模拟正常用户操作流程，可能违反相关平台服务条款。
>
> - 请自行承担账号、设备、接口变更和平台规则风险
> - 可能面临账户**积分清零**、**永久无法使用积分**甚至**封号**的风险
> - **本人概不承担因此产生的任何责任**

---

## 致谢

- [3ryng1um/qiekj](https://github.com/3ryng1um/qiekj) — 提供了积分自动化脚本及饮水设备解锁的接口分析与流程框架
- [wzs0512/qiekj-android](https://github.com/wzs0512/qiekj-android) — 提供了原始 Android 客户端的框架结构与项目基础

---

## 许可证

[MIT License](LICENSE)
