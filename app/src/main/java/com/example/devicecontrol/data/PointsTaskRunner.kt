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
        // code 非 0，或 code=0 但 data 不为 true，都视为额度耗尽
        return (code != null && code != 0) || data != true
    }

        suspend fun run(userAgent: String, log: suspend (String) -> Unit) {
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: error("请先在我的页面登录")
        var phase = stateStore?.getPhase() ?: "none"
        if (phase == "complete") { stateStore?.reset(); phase = "none" }
        if (phase == "none") stateStore?.setPhase("start")
        log("已读取登录凭证")
        log("已获取设备信息")
        val user = request("https://userapi.qiekj.com/user/info", token, userAgent, mapOf("token" to token))
        val userName = user.dataMap()["userName"]?.toString()
        log(if (userName.isNullOrBlank()) "当前账号未设置昵称" else "当前账号：$userName")
        val before = balance(token, userAgent)
        log("当前积分：${before ?: "-"}")
        if (phase in listOf("none", "start")) {
            checkPause(); signIn(token, userAgent, log); stateStore?.setPhase("signin_done"); checkPause()
        }
        if (phase in listOf("none", "start", "signin_done")) {
            checkPause(); shieldingQuery(token, userAgent, log); log("▶ 执行浏览任务"); queryByType(token, userAgent, log); stateStore?.setPhase("browse_done"); checkPause()
        }
        if (phase in listOf("none", "start", "signin_done", "browse_done")) {
            delay(1000); checkPause(); runTaskList(token, userAgent, log); stateStore?.setPhase("tasks_done"); checkPause()
        }
        if ((stateStore?.getAppVideoCount() ?: 0) < 20) { runAppVideos(token, userAgent, log); stateStore?.setPhase("app_videos_done"); checkPause() }
        if ((stateStore?.getAlipayVideoCount() ?: 0) < 50) { runAlipayVideos(token, userAgent, log); stateStore?.setPhase("ali_videos_done"); checkPause() }
        delay(3000)
        val after = balance(token, userAgent); val gained = after?.let { a -> before?.let { b -> a - b } }
        log("总积分：${after ?: "-"}")
        val conclusion = when { gained != null && gained > 0 -> "本次获得 $gained 积分"; gained == 0 -> "积分未变化，广告次数可能已达上限"; else -> "无法获取积分数据" }
        log(conclusion); stateStore?.setPhase("complete")
    }private suspend fun signIn(token: String, ua: String, log: suspend (String) -> Unit) {
        log("▶ 签到...")
        val res = request(
            url = "https://userapi.qiekj.com/signin/doUserSignIn",
            token = token,
            userAgent = ua,
            fields = mapOf("activityId" to "600001", "token" to token),
        )
        when (res.codeInt()) {
            0 -> log("签到成功，当前积分：${res.dataMap()["totalIntegral"] ?: "-"}")
            33001 -> log("签到：今日已完成")
            else -> log("签到失败（code: ${res.codeInt()}）")
        }
    }

    private suspend fun shieldingQuery(token: String, ua: String, log: suspend (String) -> Unit) {
        val res = request(
            url = "https://userapi.qiekj.com/shielding/query",
            token = token,
            userAgent = ua,
            fields = mapOf("shieldingResourceType" to "1", "token" to token),
        )
        log("环境检查完成")
    }

    private suspend fun queryByType(token: String, ua: String, log: suspend (String) -> Unit) {
        val res = request(
            url = "https://userapi.qiekj.com/task/queryByType",
            token = token,
            userAgent = ua,
            fields = mapOf("taskCode" to "8b475b42-df8b-4039-b4c1-f9a0174a611a", "token" to token),
        )
        if (res.codeInt() == 0 && res["data"] == true) {
            log("浏览任务已触发")
        } else {
            log("浏览任务触发失败（code: ${res.codeInt()}）")
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
                val ok = taskRes.codeInt() == 0 && taskRes["data"] == true
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
    }
    private suspend fun runAppVideos(token: String, ua: String, log: suspend (String) -> Unit) {
        val startFrom = stateStore?.getAppVideoCount() ?: 0
        if (startFrom > 0) log("→ 已观看过 $startFrom 次，从第 ${startFrom + 1} 次继续")
        log("→ 开始观看 APP 广告（共 20 次）")
        for (i in startFrom until 20) {
            checkPause()
            reportProgress("APP 广告", i + 1, 20)
            val res = runCatching { completeTask(token, ua, 2) }.getOrElse {
                log("  广告 #${i + 1}：网络异常，结束广告阶段")
                return
            }
            if (res.codeInt() == 0 && res["data"] == true) {
                log("  ✓ 广告 #${i + 1}/20 完成")
                stateStore?.setAppVideoCount(i + 1)
                cancellableDelay(15000)
            } else if (isQuotaExhausted(res)) {
                log("  广告次数已用完，今日已完成 $i/20 次")
                stateStore?.setAppVideoCount(20)
                return
            } else {
                log("  广告 #${i + 1}/20 失败（code: ${res.codeInt()}），结束广告阶段")
                return
            }
        }
        log("APP 广告 20 次全部完成")
    }
    private suspend fun runAlipayVideos(token: String, ua: String, log: suspend (String) -> Unit) {
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
                    fields = mapOf("taskCode" to "9", "token" to token),
                    channel = "alipay",
                )
            }.getOrElse {
                log("  支付宝广告 #${i + 1}：网络异常，结束广告阶段")
                return
            }
            if (res.codeInt() == 0 && res["data"] == true) {
                log("  ✓ 支付宝广告 #${i + 1}/50 完成")
                stateStore?.setAlipayVideoCount(i + 1)
                cancellableDelay(15000)
            } else if (isQuotaExhausted(res)) {
                log("  支付宝广告次数已用完，今日已完成 $i/50 次")
                stateStore?.setAlipayVideoCount(50)
                return
            } else {
                log("  支付宝广告 #${i + 1}/50 失败（code: ${res.codeInt()}），结束广告阶段")
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
            .add("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .add("Host", "userapi.qiekj.com")
            .add("Connection", "Keep-Alive")
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