package com.example.devicecontrol.data

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.jvm.Volatile

class TaskCancelledException : Exception()

class PointsTaskRunner(
    private val tokenProvider: () -> String?,
    private val context: Context? = null,
) {
    @Volatile
    var cancelled = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonAdapter: JsonAdapter<Map<String, Any?>> = Moshi.Builder()
        .build()
        .adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    private val statePrefs by lazy {
        context?.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE)
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    /** 读取今天某个广告任务已完成的次数，跨天自动归零 */
    private fun getAdCount(key: String): Int {
        val prefs = statePrefs ?: return 0
        val savedDate = prefs.getString("${key}_date", "") ?: ""
        if (savedDate != today()) return 0
        return prefs.getInt(key, 0)
    }

    /** 写入今天某个广告任务的完成次数 */
    private fun setAdCount(key: String, count: Int) {
        val prefs = statePrefs ?: return
        prefs.edit()
            .putInt(key, count)
            .putString("${key}_date", today())
            .apply()
    }

    /** 读取布尔状态（如签到、首页浏览是否完成），跨天自动归零 */
    private fun getState(key: String): Boolean {
        val prefs = statePrefs ?: return false
        val savedDate = prefs.getString("${key}_date", "") ?: ""
        if (savedDate != today()) return false
        return prefs.getBoolean(key, false)
    }

    /** 写入布尔状态 */
    private fun setState(key: String, value: Boolean) {
        val prefs = statePrefs ?: return
        prefs.edit()
            .putBoolean(key, value)
            .putString("${key}_date", today())
            .apply()
    }


    private fun checkCancelled() {
        if (cancelled) throw TaskCancelledException()
    }

    suspend fun run(userAgent: String, log: suspend (String) -> Unit) {
        checkCancelled()
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: error("请先在我的页面登录")

        val user = request("https://userapi.qiekj.com/user/info", token, userAgent, mapOf("token" to token))
        val userName = user.dataMap()["userName"]?.toString()
        log(if (userName.isNullOrBlank()) "当前账号未设置昵称" else "当前账号：$userName")

        val before = balance(token, userAgent)
        log("任务前积分：${before ?: "-"}")

        checkCancelled()
        signIn(token, userAgent, log)
        checkCancelled()
        shieldingQuery(token, userAgent, log)
        log("开始执行首页浏览任务")
        queryByType(token, userAgent, log)

        checkCancelled()
        delay(1000)
        runTaskList(token, userAgent, log)
        checkCancelled()
        runAppVideos(token, userAgent, log)
        checkCancelled()
        runAlipayVideos(token, userAgent, log)

        checkCancelled()
        delay(3000)
        val after = balance(token, userAgent)
        val gained = after?.let { a -> before?.let { b -> a - b } }
        log("总积分：${after ?: "-"}，今日积分：${gained ?: "-"}")
        log("所有任务均已完成")
    }

    private suspend fun signIn(token: String, ua: String, log: suspend (String) -> Unit) {
        if (getState("signin_done")) { log("签到：本地已记录，跳过"); return }
        log("开始执行签到...")
        val res = request(
            url = "https://userapi.qiekj.com/signin/doUserSignIn",
            token = token,
            userAgent = ua,
            fields = mapOf("activityId" to "600001", "token" to token),
        )
        when (res.codeInt()) {
            0 -> { log("签到成功，当前积分：${res.dataMap()["totalIntegral"] ?: "-"}")
                setState("signin_done", true)
            }
            33001 -> log("今天已经签到过")
            else -> log("签到失败：${res.messageText()}")
        }
    }

    private suspend fun shieldingQuery(token: String, ua: String, log: suspend (String) -> Unit) {
        val res = request(
            url = "https://userapi.qiekj.com/shielding/query",
            token = token,
            userAgent = ua,
            fields = mapOf("shieldingResourceType" to "1", "token" to token),
        )
        log("屏蔽资源查询完成：${res.messageText()}")
    }

    private suspend fun queryByType(token: String, ua: String, log: suspend (String) -> Unit) {
        if (getState("homepage_done")) { log("首页浏览：本地已记录，跳过"); return }
        val res = request(
            url = "https://userapi.qiekj.com/task/queryByType",
            token = token,
            userAgent = ua,
            fields = mapOf("taskCode" to "8b475b42-df8b-4039-b4c1-f9a0174a611a", "token" to token),
        )
        if (res.codeInt() == 0 && res["data"] == true) {
            log("首页浏览成功，获得积分")
            setState("homepage_done", true)
        } else {
            log("首页浏览失败：${res.messageText()}")
        }
    }

    private suspend fun runTaskList(token: String, ua: String, log: suspend (String) -> Unit) {
        if (getState("tasklist_done")) { log("任务列表：本地已记录，跳过"); return }
        log("开始获取任务列表")
        val res = request("https://userapi.qiekj.com/task/list", token, ua, mapOf("token" to token))
        if (res.codeInt() != 0) {
            log("获取任务列表失败：${res.messageText()}")
            return
        }
        val items = (res.dataMap()["items"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
        for (item in items) {
            checkCancelled()
            val completed = (item["completedStatus"] as? Number)?.toInt() ?: -1
            val taskCode = item["taskCode"] ?: continue
            if (completed != 0 || taskCode.toString() in NOT_FINISH_TASKS) continue

            val title = item["title"]?.toString().orEmpty().ifBlank { "未命名任务" }
            val limit = (item["dailyTaskLimit"] as? Number)?.toInt() ?: 1
            log("开始执行任务：$title")
            repeat(limit) { index ->
                checkCancelled()
                val taskRes = completeTask(token, ua, taskCode)
                if (taskRes.codeInt() == 0 && taskRes["data"] == true) {
                    log("$title 第${index + 1}次完成")
                } else {
                    log("$title 第${index + 1}次失败：${taskRes.messageText()}")
                    return@repeat
                }
                if (index < limit - 1) delay(10_000)
            }
            delay(5_000)
        }
        setState("tasklist_done", true)
    }

    private suspend fun runAppVideos(token: String, ua: String, log: suspend (String) -> Unit) {
        val total = 20
        val startFrom = getAdCount("app_video")
        if (startFrom >= total) {
            log("APP 视频任务：今日已完成 $startFrom/$total，跳过")
            return
        }
        if (startFrom > 0) {
            log("APP 视频任务：从第${startFrom + 1}次开始（已完成 $startFrom/$total）")
        } else {
            log("开始执行 APP 视频任务")
        }
        for (index in startFrom until total) {
            checkCancelled()
            val res = completeTask(token, ua, 2)
            if (res.codeInt() == 0 && res["data"] == true) {
                log("第${index + 1}次 APP 视频任务完成")
                setAdCount("app_video", index + 1)
                delay(15_000)
            } else {
                log("APP 视频任务停止：${res.messageText()}")
                return
            }
        }
    }

    private suspend fun runAlipayVideos(token: String, ua: String, log: suspend (String) -> Unit) {
        val total = 50
        val startFrom = getAdCount("alipay_video")
        if (startFrom >= total) {
            log("支付宝视频任务：今日已完成 $startFrom/$total，跳过")
            return
        }
        if (startFrom > 0) {
            log("支付宝视频任务：从第${startFrom + 1}次开始（已完成 $startFrom/$total）")
        } else {
            log("开始执行支付宝视频任务")
        }
        for (index in startFrom until total) {
            checkCancelled()
            val res = request(
                url = "https://userapi.qiekj.com/task/completed",
                token = token,
                userAgent = ua,
                fields = mapOf("taskCode" to "9", "token" to token),
                channel = "alipay",
            )
            if (res.codeInt() == 0 && res["data"] == true) {
                log("第${index + 1}次支付宝视频任务完成")
                setAdCount("alipay_video", index + 1)
                delay(15_000)
            } else {
                log("支付宝视频任务停止：${res.messageText()}")
                return
            }
        }
    }

    private suspend fun completeTask(token: String, ua: String, taskCode: Any): Map<String, Any?> = request(
        url = "https://userapi.qiekj.com/task/completed",
        token = token,
        userAgent = ua,
        fields = mapOf("taskCode" to taskCode.toString(), "token" to token),
    )

    private suspend fun balance(token: String, ua: String): Int? {
        val res = request("https://userapi.qiekj.com/user/balance", token, ua, mapOf("token" to token))
        return (res.dataMap()["integral"] as? Number)?.toInt()
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
        "appSecret=$ANDROID_SECRET&channel=android_app&timestamp=$timestamp&token=$token&version=$VERSION&${url.drop(25)}",
    )

    private fun signzfb(timestamp: String, url: String, token: String): String = sha256(
        "appSecret=$ALIPAY_SECRET&channel=alipay&timestamp=$timestamp&token=$token&version=$VERSION&${url.drop(25)}",
    )

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun Map<String, Any?>.codeInt(): Int? = (this["code"] as? Number)?.toInt()
    private fun Map<String, Any?>.messageText(): String = this["msg"]?.toString() ?: this["message"]?.toString() ?: "未知结果"
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
