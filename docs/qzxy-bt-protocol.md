# 趣智校园蓝牙款设备直控协议（四期参考）

> 来源：看雪论坛《趣智校园 app 分析》(bbs.kanxue.com/thread-289254.htm) + AshWashing 反编译源码（F:\参考\AshWashing，同厂商凯路创新 KLCXKJ 设备家族）。
> 本文由 2026-09-06 调研整理，用于 LightLife 四期"蓝牙直控"实现。

## 结论摘要

- 本校（江西水利电力大学）宿舍热水器 `communicationTypeId=0` = 蓝牙款：不连云端 4G，服务器无法远程开阀，必须手机经典蓝牙直发指令。
- 完整链路：手机蓝牙连设备 → 发查询帧拿 `deviceId/randomNumber/protocolType` → 服务器下订单拿 `downData` → 蓝牙把 `downData` 发给设备 → 开阀。结束用 `caijishuju` 采集消费数据上传结算。
- 设备端帧协议 100% 已知；服务器接口参数 100% 已知；签名算法已知模式，开阀接口的字段集待真机验证。

## 一、设备端协议（AshWashing，完全可信）

### 连接
- 经典蓝牙 RFCOMM/SPP，UUID `00001101-0000-1000-8000-00805F9B34FB`（`BluetoothDevice.createRfcommSocketToServiceRecord`）。不是 BLE。
- 读流：按字节读到 `0x0a('\n')` 为一帧。

### 帧格式
线上帧 = `0x23('#')` + **内层缓冲的 ASCII-hex** + `0x0a('\n')`。

内层缓冲（`CMDUtils.sendCommendBuffer`）：
```
[0]=0x60  [1]=0x00  [2]=dataLen+3  [3]=0x80  [4]=指令码  [5]=0x00  [6..]=payload  [n-2]=校验和  [n-1]=0x16
校验和 = (Σpayload + 0x80 + 指令码 + 0x00) & 0xFF
```
响应解析（`AnalyTools.handleResult2`）：内层 payload 从偏移 6 取 `buf[2]-3` 字节，校验 `|Σpayload|+buf[3]+buf[4]+buf[5] == buf[len-2]`；`payload[0]==0x80` 表示设备执行成功。

### 指令码表
| 指令 | 码 | code | payload 长度 | 用途 |
|---|---|---|---|---|
| qingchushebei | 0 | 0x19 | 1 | 清除设备 |
| (未知) | 1 | 0x20 | 8 | ? |
| **downFateToDev 开阀** | 2 | 0x21 | 48 | payload = 服务器返回的 downData(48字节) |
| jieshufeilv | 3 | 0x22 | 1 | 结束费率 |
| **chaxueshebei 查询设备** | 4 | 0x23 | 2 | 发 `00 00` |
| **caijishuju 采集数据** | 5 | 0x85 | 2 | 发 `00 00`，取消费数据 |
| fanhuicunchu | 6 | 0x86 | 22 | 写回存储 |
| settingDecive | 7 | 0x18 | 3 | 设置 |
| dealStart | 8 | 0x31 | 64 | ? |
| dealFinish | 9 | 0x32 | 3 | ? |

### 响应负载布局
- **查询设备(0x23)回复**：payload 长度 23/28/48 三种。以 48 字节为例：productid=[1..4]，deviceid=[5..8]，accountid=[9..12]，**mac=[13..18]**，verCode=[19]，**snCode=[24..27]**，deviceState/mayDeviceType/randomNumber 在 [28..38] 区间（见 AnalyTools 48 字节分支；实现时按 48 字节版对号入座并真机校准）。
- **采集数据(0xFB=-5)回复**：payload 42 字节：mac=[1..6]，productid=[7..10]，deviceid=[11..14]，accountid=[15..18]，状态=[19]，消费金额等=[20..35]，**snCode=[36..41]**。
- 各指令回复 `payload[0]==0x80` 即成功。

## 二、服务器接口（看雪抓包，字段全）

### 1. 下单开阀 `POST /order/downRate/bluetooth/rateOrder`
form 参数（抓包原文顺序）：
```
macType=0001
signature=<双重md5>
loginCode, telephone, userId, accountId, projectId, telPhone, phoneSystem=android, version  ← 常规登录态
protocolType=<设备返回，BathingBtActivity 初始为 ""，查询设备后填充>
deviceId=<deviceInfo 的 deviceId>
bigTypeId=4      ← deviceInfo 返回
smallTypeId=1    ← deviceInfo 返回
xfModel=0
macAddress=<设备 MAC>
randomNumber=<设备查询帧返回的随机数>
```
响应 `data`：`accountId, realMoney, givenMoney, preDeductMoney, useCount, downData(开阀指令48字节), rate, minTime, minMoney, chargeMethod, minChargeUnit, autoDisConTime, consumeDate, liquidOrderNo, orderNo(此处 null), preDeductMoneySend`。

### 2. 上传消费数据 `POST /order/upload/bluetooth/data`
form 参数：`accountId, telPhone, signature, phoneSystem=android, randomNumber, loginCode, telephone, protocolType, xfData, projectId, userId, version`。
响应 `data`：`consumeTime, preDeductMoney, preDeductMoneyAfter, consumeMoney, orderAccountId, clData, createTime, orderNo, deviceSnCode, ...`。**orderNo 从这里来**。

### 3. 签名算法（双重 MD5，均小写 32 位）
- 规则：参与签名的字段按 key 字典序排序 → `key1value1key2value2...` 直接拼接（不加分隔符）→ 末尾拼 `&key=<loginCode>` → md5 → 再 md5。
- 上传接口的模板（看雪原文）：`loginCode{值}telephone{值}xfData{值}&key={loginCode}`。
- 开阀接口的字段集未直接给出，最可能为 `loginCode{值}randomNumber{值}telephone{值}&key={loginCode}`（同构：两个身份字段 + 一个本请求的净荷字段）。**待真机验证**，候选第三字段：randomNumber / macAddress / deviceId。

## 三、完整流程

```
开阀：
1. 蓝牙 SPP 连接设备 MAC
2. 发 chaxueshebei(4,00 00) → 解析 deviceState/mayDeviceType/randomNumber/protocolType
   （deviceState=0 且 mayDeviceType=0 → 空闲可开）
3. POST /order/downRate/bluetooth/rateOrder（signature 按上节）
4. 响应 downData → 发 downFateToDev(2, downData) → 设备回 payload[0]=0x80 即开阀成功
5. 进入使用中（预扣金额取 preDeductMoney；autoDisConTime 闲置关停倒计时）

结束：
6. 发 caijishuju(5,00 00) → 42 字节负载 → 取出 xfData（原始 42 字节 hex 或 [19..35] 段，真机校准）
7. POST /order/upload/bluetooth/data（signature 模板已知）→ 得 consumeMoney/orderNo 结算
```

## 四、待验证清单（实现期真机校准）
1. 查询设备回复 48 字节负载中 randomNumber/protocolType/deviceState/mayDeviceType 的确切偏移
2. 开阀 signature 的字段集（候选见二.3）
3. downData 是否直接是 48 字节 payload hex（或需再包一层）
4. xfData 的取值范围（42 字节全量 hex 或部分段）
5. 心跳/中途查询：洗澡中每 30s 发 chaxueshebei 看 deviceState 是否变化（对应 tcp 版 queryUsing）

## 五、App 实现方案（四期）
- `data/qzxy/QzxyBtProtocol.kt`：帧构造/解析/校验（纯函数，可单元测试）
- `data/qzxy/QzxyBtClient.kt`：SPP 连接 + 读写协程（权限：BLUETOOTH_CONNECT 已在 manifest）
- `QzxyRepository` 增加 `btQueryDevice / btOrderRate / btUploadConsume`，`startShower` 按 `communicationTypeId==0` 分流到 BT 流程
- UI：Starting 步骤文案加"蓝牙连接中"；Failed 卡对蓝牙款给出设备不在身边/连接失败提示
- 回退：官方 App / 键盘使用码
