# LightLife

将胖乖生活核心流程（饮水设备解锁 + 积分自动化）重构成原生 Android 应用，去除臃肿功能和广告，保留最实用的部分。

> Kotlin + Jetpack Compose + OkHttp / Moshi，单 APK 约 20MB。

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
- 暗黑模式：跟随系统 / 浅色 / 深色
- 触感反馈：按钮和开关操作附带振动
- Token 查看：一键复制当前登录凭据

### 数据与存储
- Token 自动保存，退出登录一键清除
- 积分统计：累计获得积分与抵扣金额持久化
- 订单历史：JSON 文件本地存储
- 任务状态：SharedPreferences 持久化，跨天自动归零

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

## 项目结构

``` 
src/main/java/com/example/devicecontrol/
├── MainActivity.kt
├── data/
│   ├── ApiConfig.kt
│   ├── AppRepository.kt
│   ├── DeviceApi.kt
│   ├── HeaderInterceptor.kt
│   ├── Models.kt
│   ├── PointsTaskRunner.kt
│   ├── PointsTaskStateStore.kt
│   ├── PointsStatsStore.kt
│   ├── TaskLogStore.kt
│   ├── TokenStore.kt
│   └── OrderHistoryStore.kt
├── ui/
│   ├── AppViewModel.kt
│   └── screen/
│       ├── ControlScreen.kt
│       ├── PointsTaskScreen.kt
│       └── MeScreen.kt
└── res/
```

---

## refactor&Todolist
- [ ] 优化断点续跑阶段判断逻辑
- [ ] 积分统计始终为零修复
- [ ] 「我的」界面重写
- [ ] 日志格式与存储逻辑优化
- [ ] 暗黑模式全量适配
- [ ] 组件复用 + 间距圆角统一
- [ ] 订单文件和日志文件支持备份和导入

## 注意事项

- Token 随手机号重新登录而变化，App 自动保存最新 Token
- **不要将个人 Token、抓包文件、签名密钥上传到公开仓库**
- `app/debug.keystore` 是本地调试签名，**请勿删除**

## 免责声明

本项目为个人兴趣开发，**仅供学习和测试使用**。

自动化积分功能模拟正常用户操作流程，可能违反相关平台服务条款。

- 请自行承担账号、设备、接口变更和平台规则风险
- 可能面临账户**积分清零****永久无法使用积分**甚至**封号**的风险
- **本人概不承担因此产生的任何责任**

## 致谢
- [3ryng1um/qiekj](https://github.com/3ryng1um/qiekj)
- [wzs0512/qiekj-android](https://github.com/wzs0512/qiekj-android)

## 许可证
MIT License
