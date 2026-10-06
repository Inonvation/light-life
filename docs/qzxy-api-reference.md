# 趣智校园接口参考（API Reference）

> 来源：linyu `API-qzxy.md` 的接口清单 + 看雪逆向资料 + LightLife 与 JUWP-schedule（本机 `F:\JUWP-schedule`）两个客户端的实测实现。
> 2026-10-06 整理。本文是接口层总览；蓝牙帧协议细节见 `qzxy-bt-protocol.md`，账单深度分析见 `qzxy-bill-query.md`。

## 一、平台总览

### 1.1 域名与版本

| 项 | 值 |
|---|---|
| Base URL | `https://v3-api.china-qzxy.cn/` |
| 数据格式 | JSON；GET 参数在 URL，POST 为 `form-urlencoded` |
| 版本参数 | `version=6.5.28`（跟官方客户端走；服务端若按版本卡人，先动这里） |
| 客户端标识 | `phoneSystem=android` |

### 1.2 两类接口

| 族 | 接口 | 认证方式 |
|---|---|---|
| 不签名 | 登录、短信、项目信息、钱包、设备信息、账单 | 认证参数明文随 query/form 传 |
| 签名 | 蓝牙下单、消费上报 | 认证参数之外还要 `signature`，算法见 `qzxy-bt-protocol.md` |

### 1.3 认证参数怎么拼

登录响应的会话值（`loginCode` / `userId` / `accountId` / `projectId`）加手机号与客户端标识，组成每个请求都要带的参数。两个客户端命名不同、内容等价：

- LightLife：`QzxySession.queryFields()`（GET 用）/ `authFields()`（POST 用，多一个 `telPhone`）
- JUWP：`QzxySession.authFields()`（telephone / telPhone 双写在同一份里）

**历史遗留**：POST 请求必须同时传 `telephone` 和 `telPhone`（值相同），只传一个服务端会拒绝或行为异常。GET 只传 `telephone` 即可。

### 1.4 统一响应包与失败判定

```json
{ "success": true, "errorCode": 0, "errorMessage": null, "msg": null, "data": {} }
```

- `success == false` 或 `errorCode != 0` 即失败，提示取 `errorMessage ?: msg`
- LightLife 封装在 `QzxyEnvelope.throwIfFailed()`，JUWP 是 `requireSuccess()`
- 部分接口的 `data` 形状因学校而异，解析要宽容（见各接口条目）

### 1.5 会话失效与挤号

- 趣智同一账号只允许一处在线，本 App 与官方 App 同时登录会互相踢下线，这是平台规则
- 判定：响应文案含「失效 / 过期 / 未登录 / 未登陆 / 请重新登录 / token」关键词，或 HTTP 401 / 403
- LightLife 抛 `QzxySessionExpiredException`，控制器捕获后清空趣智状态、弹出重新登录

## 二、不签名接口族

### 2.1 密码登录 `POST /user/login`

| 参数 | 值 | 说明 |
|---|---|---|
| `telephone` | 手机号 | |
| `password` | 密码传输格式 | **MD5 十六进制后取后 10 位、转大写** |
| `type` | `0` | |
| `identifier` | 空串 | JUWP 传，LightLife 不传，都不影响 |
| `phoneSystem` / `version` | `android` / `6.5.28` | |

响应 `data`：`userId`、`loginCode`、`userAccount{ accountId, name, projectId, accountRealMoney }`。

坑：

- `loginCode` 是后续一切请求的会话凭证，必须存好
- `projectId` 从登录响应读，不硬编码——它是学校编号，换学校会变
- 密码推导：`MD5(明文).uppercase().takeLast(10)`；LightLife 在 `data/qzxy/QzxyPassword.kt`，JUWP 在 `domain/QzxyCredential.kt`

实现位置：LightLife `QzxyRepository.login()`；JUWP `QzxyRepository.loginByPassword()`

### 2.2 短信登录（两个接口）

LightLife 当年判定「secret 绑定个人账号，做出来别人用不了」所以没做。**这个判定是错的**：JUWP 实测并交叉验证（与 linyu `SignUtils.smsSecret`、quzhi-lite `LoginCredentials` 一致），secret 只由手机号推导，不含设备密钥，任何手机号都能本地算出，短信登录不需要抓包：

```
secret = MD5(手机号前 3 位 + 后 4 位 + "klcx")    // 小写 32 位
```

**发送验证码** `GET /user/verification/code/get`：`telephone`、`secret`、`typeId=3`、`platform=1`、`phoneSystem`、`version`

**验证码登录** `POST /user/registerAndLogin`：`telephone`、`smsCode`、`type=5`、`phoneSystem`、`version`

实现位置：JUWP `QzxyRepository.sendCode() / loginBySms()` + `domain/QzxyCredential.smsSecret()`；LightLife 未实现。

### 2.3 项目信息 `GET /project/info/triple`

返回学校项目信息。JUWP 用它核对登录确实落在本校项目上（与登录响应的 `projectId` 对得上）。LightLife 未用。

### 2.4 钱包 `GET /account/wallet`

响应 `data`：`money`（字符串，单位元）、`accountRealMoney`、`accountGivenMoney`（赠送余额）。

实现位置：LightLife `QzxyRepository.wallet()`，主页洗澡卡显示「钱包 ¥x.xx」；JUWP 同。

### 2.5 设备信息 `GET /device/info/mac`

| 参数 | 说明 |
|---|---|
| `macAddress` | 服务端登记的 MAC（见坑） |
| + 认证参数 | |

响应 `data` 关键字段：`snCode`（控制设备的必需凭据）、`deviceName`、`macAddress`、`withholdMoney`（预扣）、`onlineStatusId`（1 在线 / 0 离线）、`communicationTypeId`（**0 = 蓝牙款**，其他值为联网款）。

坑：Android 蓝牙扫描拿到的地址首字节与设备注册值可能不同（实测 `C0` vs `00`，后五字节相同）。**查设备与下单必须用服务端登记值，GATT 连接必须用广播地址**——用错地址会让订单挂到「另一台设备」上。细节见 `qzxy-bt-protocol.md` 踩坑清单第 2 条。

实现位置：LightLife `QzxyRepository.deviceInfo()` + `registeredMacCandidate()`（两个地址并行查、谁先返回用谁）；JUWP 同思路。

### 2.6 账单 `GET /order/query/account/bill/list`

摘要：`month=yyyy-MM`、`billRequestType=2`；响应两层壳（`data` 数组 → 每项 `consumeBillDTO`）；金额只认服务端账单，不做本地推算。

**完整规格、JUWP 参考实现与接入建议见 `qzxy-bill-query.md`，此处不重复。**

## 三、tcpDevice 族（云端 4G 款）

适用范围：`communicationTypeId != 0` 的联网款设备。本校宿舍是蓝牙款（=0），这套接口打过去永远报「设备不在线」——**LightLife 完整实现了这一族**（对齐 linyu），但从未在本校真机验证；JUWP 直接不实现（注释写明：照抄只会重复「HTTP 通了但设备没动」）。

### 3.1 开始洗澡（开阀）`POST /order/tcpDevice/downRate/rateOrder`

参数：`xfModel=0`、`snCode`、+ 认证参数。

响应 `data`（`QzxyDownRateResult`）：`orderNo?`、`state`、`result`、`autoDisConTime`（闲置自动关停秒数，如 600 = 10 分钟）、`preDeductMoney`、`snCode`。

坑：`orderNo` 常常不在这步返回，要等 3.3 轮询。

### 3.2 开阀确认 `POST /order/tcpDevice/query/downRateResult`

参数：`snCode` + 认证参数。

成功判定：`state == 0 || result == 0 || orderNo 非空`。

轮询节奏：700ms × 8 次（linyu 验证值，LightLife 记在 `QzxyApiConfig`）。

### 3.3 查询进行中订单 `POST /order/tcpDevice/query/rateOrder/using`

参数：`xfModel=0`、`snCode`、+ 认证参数。

响应（`QzxyOrderStatus`）：`orderNo`、`state`、`snCode`、`isOwner`。`orderNo` 为 null 表示无进行中订单。

**坑（errorCode 307）**：307 表示设备正被使用，此时 `data` 可能仍带订单信息，**不能当普通错误抛出**。LightLife 在 `queryUsing()` 里对 307 特判，返回 data 而非抛错。

两个用途：冷启动恢复中断的订单；开阀后轮询拿 `orderNo`（800ms × 10 次）。

### 3.4 停止洗澡（关阀）`POST /order/tcpDevice/closeOrder`

参数：`snCode`、`orderNo`、+ 认证参数。

坑：订单可能已被设备自动关停，此时关阀接口会报错——LightLife 吞掉这个异常，继续走确认与结算，由后续结果决定成败。

### 3.5 关阀确认 `POST /order/tcpDevice/closeOrder/result/query`

参数：`snCode`、`orderNo`、+ 认证参数。

成功判定：`state == 0 || status == 0 || result == 0`。

消费金额字段**因学校而异**：`consumeMoney`（数字）或 `consumeMoneyStr`（字符串），两个都要读（`QzxyCloseOrderResult` 里都定义了）。

轮询节奏：1500ms × 5 次。

### 3.6 消费结算兜底 `POST /order/consumeOrder/result/query`

关阀确认没带回金额时用它查，参数同 3.5。响应（`QzxyConsumeResult`）：`consumeMoney` / `consumeMoneyStr` / `consumeTime`。

**流程编排**在 LightLife `QzxyRepository.startShower() / stopShower()`：开阀四步（查占用 → 开阀 → 确认 → 拿订单号），关阀三步（关阀 → 确认 → 查消费）。

## 四、蓝牙族（签名）

适用范围：`communicationTypeId == 0` 的蓝牙款。两个客户端都实现。帧协议与签名算法细节见 `qzxy-bt-protocol.md`，这里只列 HTTP 接口。

### 4.1 蓝牙下单 `POST /order/downRate/bluetooth/rateOrder`

| 参数 | 来源 | 说明 |
|---|---|---|
| `xfModel` | `"0"` | |
| `deviceId` | 设备查询帧 | |
| `macType` | 主/子类型拼成 `"%02x%02x"` | |
| `protocolType` | 设备查询帧 | |
| `randomNumber` | 设备查询帧 | **蓝牙实时读取，纯 HTTP 方案拿不到，这是必然失败的原因** |
| `smallTypeId` / `bigTypeId` | 子/主类型 | JUWP 新版字段名；旧版是 `smallTypeId` + `macAddress`/`bigTypeId`（见下） |
| `macAddress` 或 `deviceMac` | 服务端登记 MAC | JUWP 保留新旧两套字段名（`useLegacyFieldNames` 开关切换） |
| `signature` | 见下 | |

**签名只签四项**：`telephone`（或 `telPhone`）、`deviceId`、`xfModel`、`randomNumber`。官方 App 内部按开关决定用 telephone 还是 telPhone，JUWP 两组候选都生成、试通哪组就固定哪组；LightLife 固定用 telephone。

响应 `data`（`QzxyBtRateOrderData`）：**`downData`**（要经手机蓝牙写进设备的开阀指令，十六进制）、`preDeductMoney`（**单位厘**，0.04 元返回 "40"）、`autoDisConTime`、`orderNo`（通常为 null，订单号要等结算上传才有）。

### 4.2 消费上报 `POST /order/upload/bluetooth/data`

参数：`xfData`（从设备读回的消费记录十六进制）、`randomNumber`、`protocolType`、`signature`（签 `loginCode`、`telephone`、`xfData` 三项）+ 认证参数。

响应 `data`（`QzxyBtUploadData`）：`consumeMoney`（**单位厘**）、`consumeTime`、`orderNo`、`clData`（AES 密文，解出清除设备记录用的参数）。

### 4.3 完整链路

```
GATT 连接 → 查设备 0x23（须空闲）→ 下单拿 downData
→ 写 0x21 开阀 → 用完发 0x22 停阀 → 轮询到状态 3（待采集）
→ 0x85 采集 → HTTP 上报结算 → 0x86 清除设备记录
```

帧格式、校验、设备状态机、清除候选表、四个真机坑全在 `qzxy-bt-protocol.md`。

实现位置：LightLife `QzxyRepository.btStartShower() / btStopShower()` + `QzxyBtProtocol` / `QzxyBtClient`；JUWP `QzxyWaterFlow` + `QzxyRepository.rateOrder() / uploadConsume()` + `domain/` 下的 `QzxyFrame` / `QzxyProtocol` / `QzxySign` / `QzxyClData`。

## 五、实现状态矩阵

| 接口 | LightLife | JUWP-schedule | linyu |
|---|---|---|---|
| `POST /user/login` | ✅ | ✅ | ✅ |
| `GET /user/verification/code/get` | — | ✅ | 标注未完善 |
| `POST /user/registerAndLogin` | — | ✅ | 标注未完善 |
| `GET /project/info/triple` | — | ✅ | — |
| `GET /account/wallet` | ✅ | ✅ | ✅ |
| `GET /device/info/mac` | ✅ | ✅ | ✅ |
| `GET /order/query/account/bill/list` | 仅文档 | ✅ | ✅ |
| `GET /order/query/account/bill/detail` | — | — | ✅ |
| tcpDevice 族（3.1–3.6） | ✅ 未真机验证 | 不实现 | ✅ |
| `POST /order/downRate/bluetooth/rateOrder` | ✅ | ✅ | — |
| `POST /order/upload/bluetooth/data` | ✅ | ✅ | — |
| `GET /account/useCode/new` 等 3 个 | 曾实现后移除 | — | ✅ |
| `GET /account/info` | — | 不调用 | — |

「—」= 未实现或不适用。linyu 是金华职业技术大学（projectId=905）验证的参考实现，蓝牙族它没做。

## 六、未实现与未验证

| 项 | 说明 |
|---|---|
| 账单详情 `bill/detail` | linyu 有实现，LightLife 与 JUWP 都没做；需要时看 linyu `API-qzxy.md` |
| `account/info`（姓名学号） | JUWP 注释：学校没同步学籍数据时整条返回 null，页面也用不上，不调用 |
| 扫码绑定 / 寝室绑定 | `linyu-merge-plan.md` 二期候选，未做 |
| tcpDevice 族真机验证 | LightLife 代码完整，但本校是蓝牙款无从验证；有联网款设备的学校可实测 |
| 短信登录（LightLife 侧） | secret 推导已明确（见 2.2），未实现代码 |
