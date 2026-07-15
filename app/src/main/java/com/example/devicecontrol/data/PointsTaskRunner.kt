package com.example.devicecontrol.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.jvm.Volatile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class PointsTaskRunner(
    private val tokenProvider: () -> String?,
    private val stateStore: PointsTaskStateStore? = null,
) {
    @Volatile
    var paused = false
    @Volatile
    var cancelled = false
    private var onProgress: (suspend (String, Int, Int) -> Unit)? = null
    fun setOnProgress(callback: (suspend (String, Int, Int) -> Unit)?) { onProgress = callback }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonAdapter: JsonAdapter<Map<String, Any?>> = Moshi.Builder()
        .build()
        .adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

        private suspend fun checkPause() {
        while (paused) {
            if (cancelled) error("用户已取消任务")
            delay(500)
        }
        if (cancelled) error("用户已取消任务")
    }

    private suspend fun cancellableDelay(ms: Long) {
        var remaining = ms
        while (remaining > 0) {
            checkPause()
            delay(minOf(500L, remaining))
            remaining -= 500L
        }
    }

    private suspend fun reportProgress(phase: String, step: Int, total: Int) {
        onProgress?.invoke(phase, step, total)
    }

    private fun isQuotaExhausted(res: Map<String, Any?>): Boolean {
        val code = res.codeInt()
        val data = res["data"]
        // 仅 code=0 但 data 非 true 时视为额度耗尽
        // 其他 code（如服务器错误）不视为额度耗尽，避免误标导致当天广告全部跳过
        return code == 0 && data != true
    }

    suspend fun run(userAgent: String, log: suspend (String) -> Unit) {
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: error("请先在我的页面登录")
        log("已读取登录凭证")
        log("已获取设备信息")
        val signInDone = stateStore?.isSignInDone() ?: false
        val taskListDone = stateStore?.isTaskListDone() ?: false
        val appVideoCount = stateStore?.getAppVideoCount() ?: 0
        val alipayVideoCount = stateStore?.getAlipayVideoCount() ?: 0
        log("已读取本地断点")
        val lb = StringBuilder("断点: ")
        if (signInDone) lb.append("签到 ") else lb.append("签到✗ ")
        if (taskListDone) lb.append("任务 ") else lb.append("任务✗ ")
        lb.append("APP广告${appVideoCount}/20 支付宝广告${alipayVideoCount}/50")
        log(lb.toString())
        val user = request("https://userapi.qiekj.com/user/info", token, userAgent, mapOf("token" to token))
        val userName = user.dataMap()["userName"]?.toString()
        log(if (userName.isNullOrBlank()) "当前账号未设置昵称" else "当前账号：$userName")
        val before = balance(token, userAgent)
        log("当前积分：${before ?: "-"}")
        if (!signInDone) {
            checkPause(); signIn(token, userAgent, log); checkPause()
        }
        // 屏蔽资源查询 + 首页浏览：服务端前置校验，必须在广告任务前调用
        shieldingQuery(token, userAgent, log)
        queryByType(token, userAgent, log)
        if (!taskListDone) {
            delay(1000); checkPause(); runTaskList(token, userAgent, log); checkPause()
        }
        if (appVideoCount < 20) { runAppVideos(token, userAgent, log); checkPause() }
        if (alipayVideoCount < 50) { runAlipayVideos(token, userAgent, log); checkPause() }
        delay(3000)
        val after = balance(token, userAgent); val gained = after?.let { a -> before?.let { b -> a - b } }
        log("总积分：${after ?: "-"}")
        val conclusion = when { gained != null && gained > 0 -> "本次获得 $gained 积分"; gained == 0 -> "积分未变化，广告次数可能已达上限"; else -> "无法获取积分数据" }
        log(conclusion)
    }
    private suspend fun signIn(token: String, ua: String, log: suspend (String) -> Unit) {
        log("▶ 签到...")
        val res = request(
            url = "https://userapi.qiekj.com/signin/doUserSignIn",
            token = token,
            userAgent = ua,
            fields = mapOf("activityId" to "600001", "token" to token),
        )
        when (res.codeInt()) {
            0 -> { log("签到成功，当前积分：${res.dataMap()["totalIntegral"] ?: "-"}"); stateStore?.setSignInDone(true) }
            33001 -> { log("签到：今日已完成"); stateStore?.setSignInDone(true) }
            else -> log("签到失败（code: ${res.codeInt()}）")
        }
    }

    private suspend fun shieldingQuery(token: String, ua: String, log: suspend (String) -> Unit) {
        log("▶ 屏蔽资源查询...")
        val res = request(
            url = "https://userapi.qiekj.com/shielding/query",
            token = token,
            userAgent = ua,
            fields = mapOf("shieldingResourceType" to "1", "token" to token),
        )
        log("屏蔽资源查询完成（code: ${res.codeInt()}）")
    }

    private suspend fun queryByType(token: String, ua: String, log: suspend (String) -> Unit) {
        log("▶ 首页浏览任务...")
        val res = request(
            url = "https://userapi.qiekj.com/task/queryByType",
            token = token,
            userAgent = ua,
            fields = mapOf("taskCode" to "8b475b42-df8b-4039-b4c1-f9a0174a611a", "token" to token),
        )
        if (res.codeInt() == 0 && res["data"] == true) {
            log("首页浏览成功，获得积分")
        } else {
            log("首页浏览完成（code: ${res.codeInt()}, data: ${res["data"]}）")
        }
    }

    private suspend fun runTaskList(token: String, ua: String, log: suspend (String) -> Unit) {
        log("开始查询可执行任务")
        val res = request("https://userapi.qiekj.com/task/list", token, ua, mapOf("token" to token))
        if (res.codeInt() != 0) {
            log("查询任务列表失败（code: ${res.codeInt()}）")
            return
        }
        val items = (res.dataMap()["items"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
        if (items.isEmpty()) {
            log("无可执行任务，跳过")
            stateStore?.setTaskListDone(true)
            return
        }
        var executedCount = 0
        for (item in items) {
            checkPause()
            val completed = (item["completedStatus"] as? Number)?.toInt() ?: -1
            val taskCode = item["taskCode"] ?: continue
            if (completed != 0 || taskCode.toString() in NOT_FINISH_TASKS) continue

            val title = item["title"]?.toString().orEmpty().ifBlank { "未命名任务" }
            val limit = ((item["dailyTaskLimit"] as? Number)?.toInt() ?: 1).coerceAtLeast(1)
            log("▶ $title")
            var taskFailed = false
            repeat(limit) { index ->
                if (taskFailed) return@repeat
                reportProgress(title, index + 1, limit)
                val taskRes = completeTask(token, ua, taskCode)
                val ok = taskRes.codeInt() == 0 && taskRes.isDataTrue()
                val seq = if (limit > 1) "（${index + 1}/$limit）" else ""
                if (ok) {
                    log("$title$seq 完成")
                } else {
                    log("$title$seq 未完成，跳过后续次数")
                    taskFailed = true
                }
                cancellableDelay(10000)
            }
            executedCount++
            cancellableDelay(5000)
        }
        if (executedCount == 0) {
            log("所有任务今日已完成")
        } else {
            log("任务列表执行完毕")
        }
        stateStore?.setTaskListDone(true)
    }


    private suspend fun findVideoAdTaskCode(token: String, ua: String, keywords: List<String>, log: suspend (String) -> Unit): String? {
        val res = request("https://userapi.qiekj.com/task/list", token, ua, mapOf("token" to token))
        if (res.codeInt() != 0) return null
        val items = (res.dataMap()["items"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
        for ((idx, item) in items.withIndex()) {
            val title = item["title"]?.toString().orEmpty()
            val taskCode = item["taskCode"]?.toString() ?: "?"
            val limit = ((item["dailyTaskLimit"] as? Number)?.toInt() ?: 1)
            val completed = (item["completedStatus"] as? Number)?.toInt() ?: -1
            log("  [task$idx] $title | code=$taskCode | limit=$limit | done=$completed")
        }
        for (item in items) {
            val title = item["title"]?.toString().orEmpty()
            val taskCode = item["taskCode"]?.toString() ?: continue
            if (taskCode in NOT_FINISH_TASKS) continue
            val limit = ((item["dailyTaskLimit"] as? Number)?.toInt() ?: 1)
            if (keywords.any { title.contains(it) }) {
                return taskCode
            }
        }
        return null
    }
    private suspend fun runAppVideos(token: String, ua: String, log: suspend (String) -> Unit) {
        val taskCode = findVideoAdTaskCode(token, ua, listOf("广告"), log) ?: "2"
        log("APP 广告使用 taskCode: $taskCode")
        val startFrom = stateStore?.getAppVideoCount() ?: 0
        if (startFrom > 0) log("→ 已观看过 $startFrom 次，从第 ${startFrom + 1} 次继续")
        log("→ 开始观看 APP 广告（共 20 次）")
        for (i in startFrom until 20) {
            checkPause()
            reportProgress("APP 广告", i + 1, 20)
            val res = runCatching { completeTask(token, ua, taskCode) }.getOrElse {
                log("  广告 #${i + 1}：网络异常，结束广告阶段")
                return
            }
            if (res.codeInt() == 0 && res.isDataTrue()) {
                stateStore?.setAppVideoCount(i + 1)
                cancellableDelay(15000)
            } else if (isQuotaExhausted(res)) {
                log("  广告次数已用完，今日已完成 $i/20 次")
                stateStore?.setAppVideoCount(20)
                return
            } else {
                log("  广告 #${i + 1}/20 失败（code: ${res.codeInt()}, data: ${res["data"]}），结束广告阶段")
                return
            }
        }
        log("APP 广告 20 次全部完成")
    }
    private suspend fun runAlipayVideos(token: String, ua: String, log: suspend (String) -> Unit) {
        val aliTaskCode = findVideoAdTaskCode(token, ua, listOf("支付宝"), log) ?: "9"
        log("支付宝广告使用 taskCode: $aliTaskCode")
        val startFrom = stateStore?.getAlipayVideoCount() ?: 0
        if (startFrom > 0) log("→ 已观看过 $startFrom 次，从第 ${startFrom + 1} 次继续")
        log("→ 开始观看支付宝广告（共 50 次）")
        for (i in startFrom until 50) {
            checkPause()
            reportProgress("支付宝广告", i + 1, 50)
            val res = runCatching {
                request(
                    url = "https://userapi.qiekj.com/task/completed",
                    token = token,
                    userAgent = ua,
                    fields = mapOf("taskCode" to aliTaskCode, "token" to token),
                    channel = "alipay",
                )
            }.getOrElse {
                log("  支付宝广告 #${i + 1}：网络异常，结束广告阶段")
                return
            }
            if (res.codeInt() == 0 && res.isDataTrue()) {
                log("  ✓ 支付宝广告 #${i + 1}/50 完成")
                stateStore?.setAlipayVideoCount(i + 1)
                cancellableDelay(15000)
            } else if (isQuotaExhausted(res)) {
                log("  支付宝广告次数已用完，今日已完成 $i/50 次")
                stateStore?.setAlipayVideoCount(50)
                return
            } else {
                log("  支付宝广告 #${i + 1}/50 失败（code: ${res.codeInt()}, data: ${res["data"]}），结束广告阶段")
                return
            }
        }
        log("支付宝广告 50 次全部完成")
    }
    private suspend fun completeTask(token: String, ua: String, taskCode: Any): Map<String, Any?> = request(
        url = "https://userapi.qiekj.com/task/completed",
        token = token,
        userAgent = ua,
        fields = mapOf("taskCode" to taskCode.toString(), "token" to token),
    )

    private suspend fun balance(token: String, ua: String): Int? {
        val res = request("https://userapi.qiekj.com/user/balance", token, ua, mapOf("token" to token))
        val integral = res.dataMap()["integral"]
        return when (integral) {
            is Number -> integral.toInt()
            is String -> integral.toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    private suspend fun request(
        url: String,
        token: String,
        userAgent: String,
        fields: Map<String, String>,
        channel: String = "android_app",
    ): Map<String, Any?> {
        val timestamp = System.currentTimeMillis().toString()
        val form = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val req = Request.Builder()
            .url(url)
            .post(form)
            .headers(headers(url, token, userAgent, timestamp, channel))
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(300)}")
                runCatching { jsonAdapter.fromJson(body).orEmpty() }
                    .getOrElse { error("响应解析失败：${it.message ?: body.take(300)}") }
            }
        }
    }

    private fun headers(
        url: String,
        token: String,
        userAgent: String,
        timestamp: String,
        channel: String,
    ): okhttp3.Headers {
        val sign = if (channel == "alipay") signzfb(timestamp, url, token) else sign(timestamp, url, token)
        return okhttp3.Headers.Builder()
            .add("Authorization", token)
            .add("Version", VERSION)
            .add("channel", channel)
            .add("phoneBrand", "Redmi")
            .add("timestamp", timestamp)
            .add("sign", sign)
            .add("token", token)
            .add("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .add("Host", "userapi.qiekj.com")
            .add("Connection", "Keep-Alive")
            .add("Accept-Encoding", "gzip")
            .add("User-Agent", userAgent)
            .build()
    }

    private fun sign(timestamp: String, url: String, token: String): String = sha256(
        "appSecret=$ANDROID_SECRET&channel=android_app&timestamp=$timestamp&token=$token&version=${ApiConfig.VERSION}&${url.drop(25)}",
    )

    private fun signzfb(timestamp: String, url: String, token: String): String = sha256(
        "appSecret=$ALIPAY_SECRET&channel=alipay&timestamp=$timestamp&token=$token&version=$VERSION&${url.drop(25)}",
    )

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun Map<String, Any?>.codeInt(): Int? = (this["code"] as? Number)?.toInt()
    private fun Map<String, Any?>.dataMap(): Map<String, Any?> = this["data"] as? Map<String, Any?> ?: emptyMap()
    // 安全比较 data 是否为 true，处理服务端返回 1、"true"、true 等不同格式
    private fun Map<String, Any?>.isDataTrue(): Boolean {
        val d = this["data"] ?: return false
        return when (d) {
            is Boolean -> d
            is Number -> d.toInt() == 1
            else -> d.toString().toBooleanStrictOrNull() ?: false
        }
    }

    private companion object {
        const val VERSION = ApiConfig.VERSION
        const val ANDROID_SECRET = ApiConfig.ANDROID_SECRET
        const val ALIPAY_SECRET = ApiConfig.ALIPAY_SECRET
        val NOT_FINISH_TASKS = setOf(
            "7328b1db-d001-4e6a-a9e6-6ae8d281ddbf",
            "e8f837b8-4317-4bf5-89ca-99f809bf9041",
            "65a4e35d-c8ae-4732-adb7-30f8788f2ea7",
            "73f9f146-4b9a-4d14-9d81-3a83f1204b74",
            "12e8c1e4-65d9-45f2-8cc1-16763e710036",
        )
    }
}