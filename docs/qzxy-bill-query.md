# 趣智校园账单查询（消费记录）开发参考

> 来源：接口逆向自 linyu `API-qzxy.md` §4.6；实现参考 JUWP-schedule（本机 `F:\JUWP-schedule`，2026-09-27 真机验证）。
> 2026-10-06 调研成文。当前状态：**未做代码实现**，本文是将来实现时的路线图，先读本文再动手。

## 结论摘要

- 一个接口管全部：`GET /order/query/account/bill/list`，按 `month=yyyy-MM` 拉整月消费记录，无分页。
- 不签名。认证参数随 query 明文传，与钱包、设备信息同类；蓝牙下单/上报才要签名。
- 金额只认服务端账单，不做本地推算。预扣是预扣、实扣是实扣，是两笔账——JUWP 曾把预扣当实扣读错一次，这条规则由此而来。
- 返回的是"一条条记录"，不是汇总：合计金额、笔数都在客户端算，服务端没有合计字段。
- 会话失效沿用现有机制（关键词 + 401/403 → `QzxySessionExpiredException`），不新增判定。

## 一、接口规格

### 请求

```
GET /order/query/account/bill/list?month=2026-10&billRequestType=2&loginCode=…&userId=…&…
```

| 参数 | 值 | 说明 |
|---|---|---|
| `month` | `yyyy-MM`（如 `2026-10`） | 要查的月份 |
| `billRequestType` | `2` | 固定值，linyu 与 JUWP 都原样沿用，含义未深究，不要乱改 |
| 认证参数 | — | 现有 `QzxySession.queryFields()`（loginCode / userId / accountId / projectId / telephone / phoneSystem / version）直接平铺进来 |

### 响应

两层壳：外层 `data` 是数组，数组每项再包一层 `consumeBillDTO`。解析要剥两层。

linyu 文档版（`API-qzxy.md` §4.6）：

```json
{
  "success": true,
  "data": [
    {
      "consumeBillDTO": {
        "orderId": "1234567",
        "consumeDate": "2026-05-30 10:08:42",
        "consumeMoney": "0.08",
        "description": "热水器:学生公寓-1号楼-3层-301洗手台"
      }
    }
  ]
}
```

JUWP 真机跑过的字段（以此为准，比 linyu 多一个 `deviceDescription`）：

```kotlin
@Serializable
data class QzxyBillList(val consumeBillDTO: QzxyBill? = null)

@Serializable
data class QzxyBill(
    val orderNo: String? = null,
    val consumeDate: String? = null,      // "2026-09-27 22:11:53" 定长串
    val consumeMoney: String? = null,     // "0.08"，字符串
    val description: String? = null,
    val deviceDescription: String? = null,
)
```

### 字段与边界

| 字段 | 形态 | 说明 |
|---|---|---|
| `consumeDate` | `"2026-09-27 22:11:53"` 定长串 | 展示前可压缩成「9月27日 22:11」；格式对不上就原样显示，不猜 |
| `consumeMoney` | 字符串 `"0.08"` | 求和先 `toBigDecimalOrNull`，别拿 Double 累加 |
| `description` | 可为空 | 展示主字段，如「热水器:1号楼-301」 |
| `deviceDescription` | 可为空 | 展示兜底字段，JUWP 取用顺序：description → deviceDescription → "热水消费" |
| `orderNo` / `orderId` | 字符串 | 订单号。linyu 示例写 `orderId`、JUWP 实测模型用 `orderNo`——字段名以真机响应为准；只作展示，缺失不影响功能 |
| 本月无记录 | `data` 为空数组 | 解析对 `data=null` / 解析失败一律兜底空表，不抛错 |

更早的月份只能换 `month` 重查，接口没有分页，也没有"查全年"的入口。

## 二、JUWP-schedule 的参考实现

> 代码位置：`F:\JUWP-schedule\app\src\main\java\edu\jxslu\schedule\`

### 数据层 `data/qzxy/QzxyRepository.kt`

```kotlin
suspend fun billList(month: String): List<QzxyBill> = withContext(Dispatchers.IO) {
    val params = requireSession().authFields() + mapOf(
        "month" to month,
        "billRequestType" to "2",
    )
    val envelope = api.billList(params)
    envelope.requireSuccess()
    val element = envelope.data ?: return@withContext emptyList()
    runCatching {
        QiekjJson.json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(QzxyBillList.serializer()),
            element,
        )
    }.getOrDefault(emptyList()).mapNotNull { it.consumeBillDTO }
}
```

要点：

- JUWP 的 `authFields()` 对应本项目的 `queryFields()`。JUWP 把 telephone/telPhone 双写进一份；本项目分了 queryFields（GET 用）和 authFields（POST 用，多一个 telPhone），GET 用 `queryFields()` 即可。
- 解析全程宽容：外层 decode 失败 → 空表；`mapNotNull` 剥掉 `consumeBillDTO` 壳。
- **宽容字符串序列化器**：JUWP 给每个字段挂了 `LenientStringSerializer`（数字/字符串都能收）。本项目用 Moshi，`String?` 字段遇到服务端发数字会解析失败——真机联调时先验证字段类型；真遇到数字，给金额字段写个小 `JsonAdapter`，或先用 `Any?` 过渡。

### 状态层 `ui/qzxy/QzxyViewModel.kt`

四个状态：`billMonth` / `bills` / `loadingBills` / `billLoaded`。三个动作：

```kotlin
/** 拉某个月的消费记录，月份格式 yyyy-MM。默认当月。 */
fun loadBills(month: String = currentMonth()) { … }

/** 进页面时拉一次：已有数据或正在拉就不动，防「进→退→再进」白拉一次 */
fun loadBillsOnce() {
    if (_uiState.value.billLoaded || _uiState.value.loadingBills) return
    loadBills()
}

/** 翻月份：-1 上一月，+1 下一月 */
fun shiftBillMonth(delta: Long) {
    val base = _uiState.value.billMonth.ifBlank { currentMonth() }
    val next = YearMonth.parse(base).plusMonths(delta).toString()
    loadBills(next)
}
```

`currentMonth()` 用 `YearMonth.now().toString()`（java.time，本项目 minSdk 26 可直接用）。

失败分两条路：会话失效 → 走既有 `QzxySessionExpiredException`（提示重登）；其他错 → 提示一条消息，不打断界面。注意失败时也要置 `billLoaded = true`，否则空态文案会一直停在"读取中"。

### UI 层 `ui/qzxy/QzxyScreen.kt`（`BillSection`）

区块标题「消费记录」，副标题「按月查热水消费，金额与官方账单同源」。形态：

```
 [上月]      2026-10      [下月]        ← 月份导航
 ¥12.40   本月合计 · 23 笔              ← 合计（BigDecimal 累加）
 热水器:1号楼-301            ¥0.08       ← 每条：描述(左) + 金额(右)
 2026-09-27 22:11:53                   ← 时间
 …（默认只显示 5 条，多余的收进「展开」）
```

细节：

- 合计只把服务端这一页返回的金额相加，不参与任何计费判断：

  ```kotlin
  val total = state.bills
      .mapNotNull { bill -> bill.consumeMoney?.trim()?.toBigDecimalOrNull() }
      .fold(BigDecimal.ZERO, BigDecimal::add)
  ```

- 加载中/空/有数据三态文案分开：`读取中…` / `这个月没有消费记录` / 列表。`billLoaded` 之前不显示"没有"——不能把"还在加载"说成"没有"。
- 洗浴待机卡顺带显示最近一次：`最近一次 9月27日 22:11 · ¥0.08`（取 `bills.first()`）；当月没有就直说「本月还没有用水记录」。
- 时间压缩是个纯函数（`"2026-09-27 22:11:53"` → `"9月27日 22:11"`），格式不符原样返回。这段值得照抄成单测。

### JUWP 记录的两个决定（DESIGN.md §4.30）

- 账单只在趣智页面里拉（`loadBillsOnce`），不在冷启动时拉——首页卡片不显示账单，冷启动拉纯浪费。
- 金额口径只有一个来源：服务端账单。预扣与实扣是两笔账，不做本地推算。

## 三、接进 LightLife 的落点（规划，未实现）

按本项目现有结构，改动落点：

| 层 | 位置 | 要做的事 |
|---|---|---|
| API | `data/qzxy/QzxyApi.kt` | 加 `@GET("order/query/account/bill/list")`，`@QueryMap` 收参数 |
| 模型 | `data/qzxy/QzxyModels.kt` | `QzxyBillList` + `QzxyBill`，照现有 `String?` 风格；接入前真机验证字段类型 |
| 仓储 | `data/qzxy/QzxyRepository.kt` | `suspend fun billList(month: String): List<QzxyBill>`，params = `queryFields() + month + billRequestType`；解析失败兜底空表；走现有 `call {}` 封装拿到会话失效判定 |
| 状态 | `ui/qzxy/QzxyUiState.kt` | 加 `billMonth / bills / loadingBills / billLoaded` 四字段 |
| 控制器 | `ui/qzxy/QzxyController.kt` | `loadBills / shiftBillMonth`；进页面拉一次 |
| UI | `ui/qzxy/screen/` | 两个形态可选，见下 |

**UI 放哪（实现前先定）**——受 AGENTS.md UI 规范约束（不进新卡、长内容进弹窗、账号信息低调）：

- 选项 A：按 JUWP 形态做「消费记录」区块，挂在洗澡卡下面。代价是主页三分区骨架变长。
- 选项 B：洗澡卡里放一行低调入口（如「本月已用 ¥12.40」），点开 bottom sheet 展示月份导航 + 列表。更贴本项目"详情进弹层"的习惯。

动 UI 前先对照 `docs/ui-redesign-mockup.html`。

**测试**：时间压缩、金额求和这类纯函数值得单测（参照 `MoneyUtilsTest` 的写法）。网络层不测。

## 四、没做的部分（如实记录）

| 项 | 情况 |
|---|---|
| 账单详情 `order/query/account/bill/detail` | linyu 文档有记录（其项目已实现），JUWP 与本项目都没做。需要时回到 linyu `API-qzxy.md` |
| 余额估算 | 一卡通真实余额拿不到（易校园 API 有 native 层签名保护）。linyu 的做法是"手动输初始余额、按账单倒推"；JUWP 没采用、本项目也没做——要做先算清产品账 |
| 官方 App 账单刷新时机 | 未深究。本方案"进页面拉一次 + 手翻月份"够用 |
