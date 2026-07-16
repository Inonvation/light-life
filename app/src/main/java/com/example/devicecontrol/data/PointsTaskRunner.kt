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
    private var debugLog: DebugLogStore? = null
    fun setDebugLog(log: DebugLogStore?) { debugLog = log }

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

    /** 判断服务器返回是否表示任务已全部完成 */
    private fun isAlreadyCompletedResponse(taskRes: Map<String, Any?>): Boolean {
        val code = taskRes.codeInt()
        val msg = taskRes.messageText()
        if (code != 0 && (msg.contains("已完成") || msg.contains("已结束") || msg.contains("完成"))) return true
        if (code == 0 && taskRes["data"] != true && (msg.contains("已完成") || msg.contains("已结束"))) return true
        return false
    }

    suspend fun run(userAgent: String, log: suspend (String) -> Unit) {
        checkCancelled()
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: error("请先在我的页面登录")

        val user = request("https://userapi.qiekj.com/user/info", token, userAgent, mapOf("token" to token))
        val userName = user.dataMap()["userName"]?.toString()
        log(if (userName.isNullOrBlank()) "当前账号未设置昵称" else "当前账号：$userName")

        var lastBalance = balance(token, userAgent)
        log("任前前积分：${lastBalance ?: "-"}")

        // 本地状态检查：完成过的步骤直接跳过
        checkCancelled()
        if (getState("signin_done")) {
            log("签到：本地已记录，跳过")
        } else {
            signIn(token, userAgent, log)
            delay(2000)
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("签到获得 ${diff} 积分，当前 ${cur}")
            } else if (cur != null) {
                log("签到阶段完成，当前积分 ${cur}")
            }
            lastBalance = cur
        }
        checkCancelled()
        shieldingQuery(token, userAgent, log)

        log("开始执行首页浏览任务")
        queryByType(token, userAgent, log)
        delay(2000)
        run {
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("首页浏览获得 ${diff} 积分，当前 ${cur}")
            } else if (cur != null) {
                log("首页浏览阶段完成，当前积分 ${cur}")
            }
            lastBalance = cur
        }

        checkCancelled()
        delay(1000)
        lastBalance = runTaskList(token, userAgent, log, lastBalance)

        checkCancelled()
        runAppVideos(token, userAgent, log)
        delay(2000)
        run {
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("APP视频获得 ${diff} 积分，当前 ${cur}")
            } else if (cur != null) {
                log("APP视频阶段完成，当前积分 ${cur}")
            }
            lastBalance = cur
        }

        checkCancelled()
        runAlipayVideos(token, userAgent, log)
        delay(3000)
        val after = balance(token, userAgent)
        if (after != null && lastBalance != null) {
            val diff = after - lastBalance
            log("支付宝视频获得 ${diff} 积分，当前 ${after}")
        }
        val totalGained = after?.let { a -> lastBalance?.let { _ -> a - (lastBalance ?: a) } }
        log("总积分：${after ?: "-"}，今日累计获得：${totalGained ?: 0} 积分")
        log("所有任务均已完成")
    }

    private suspend fun signIn(token: String, ua: String, log: suspend (String) -> Unit) {
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
            33001 -> { log("今天已经签到过"); setState("signin_done", true) }
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
        val res = request(
            url = "https://userapi.qiekj.com/task/queryByType",
            token = token,
            userAgent = ua,
            fields = mapOf("taskCode" to "8b475b42-df8b-4039-b4c1-f9a0174a611a", "token" to token),
        )
        if (res.codeInt() == 0) {
            log("首页浏览成功，获得积分")
        } else {
            log("首页浏览失败：${res.messageText()}")
        }
    }

    /**
     * 任务列表阶段，返回最新的 lastBalance。
     * "看更多视频"和"点外卖"合并为"其他"任务标签，两者都完成才标记 other_task_done。
     * 看广告任务识别服务器"已完成"反馈，避免重复执行。
     * 每条任务完成后日志追加 +N 积分。
     */
    private suspend fun runTaskList(
        token: String,
        ua: String,
        log: suspend (String) -> Unit,
        lastBalance: Int?,
    ): Int? {
        var curBalance = lastBalance
        log("开始获取任务列表")
        val res = request("https://userapi.qiekj.com/task/list", token, ua, mapOf("token" to token))
        if (res.codeInt() != 0) {
            log("获取任务列表失败：${res.messageText()}")
            return curBalance
        }
        val items = (res.dataMap()["items"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

        // 记录"其他"任务组的完成情况
        var otherMoreVideoDone = getState("more_video_done")
        var otherTakeoutDone = getState("takeout_done")

        for (item in items) {
            checkCancelled()
            val completed = (item["completedStatus"] as? Number)?.toInt() ?: -1
            val taskCode = item["taskCode"] ?: continue
            val title = item["title"]?.toString().orEmpty().ifBlank { "未命名任务" }
            val isAdTask = title == "看广告赚积分"
            val isMoreVideoTask = title == "看更多视频赚积分"
            val isTakeoutTask = title == "点外卖攒积分"
            val isOtherTask = isMoreVideoTask || isTakeoutTask

            // 广告任务：本地已标记完成则跳过
            if (isAdTask && getState("ad_task_done")) continue
            // "其他"任务组：本地已标记完成则跳过
            if (isOtherTask && getState("other_task_done")) continue
            // 非广告/非其他任务：服务器已完成则跳过
            if (!isAdTask && !isOtherTask && (completed != 0 || taskCode.toString() in NOT_FINISH_TASKS)) continue

            val limit = (item["dailyTaskLimit"] as? Number)?.toInt() ?: 1

            if (isAdTask) {
                val adLimit = 10
                // 检查服务器返回的已完成次数，本地重置时也能跳过
                if (completed >= adLimit) {
                    setAdCount("ad_task", adLimit)
                    setState("ad_task_done", true)
                    log("$title 服务器显示已完成 $completed/$adLimit，跳过")
                    continue
                }
                val adTaskDone = getAdCount("ad_task")
                if (adTaskDone >= adLimit) {
                    setState("ad_task_done", true)
                    continue
                }
                // 先调一次接口探测服务器实际状态，防止本地记录丢失后重复执行
                val probeRes = completeTask(token, ua, taskCode)
                if (isAlreadyCompletedResponse(probeRes)) {
                    setAdCount("ad_task", adLimit)
                    setState("ad_task_done", true)
                    log("$title 已全部完成（服务器反馈），跳过")
                    continue
                }
                if (probeRes.codeInt() == 0) {
                    setAdCount("ad_task", 1)
                    delay(1500)
                    val cur = balance(token, ua)
                    val diff = if (cur != null && curBalance != null) cur - curBalance else null
                    val suffix = if (diff != null && diff > 0) " +$diff" else ""
                    log("$title 第1次完成$suffix")
                    curBalance = cur ?: curBalance
                    log("开始执行任务：$title（已完成 1/$adLimit）")
                } else {
                    log("$title 探测失败：${probeRes.messageText()}，跳过")
                    continue
                }
            } else if (isOtherTask) {
                log("开始执行任务：$title")
            } else {
                log("开始执行任务：$title")
            }

            var taskCompletedNormally = true
            var completedCount = if (isAdTask) getAdCount("ad_task") else 0

            repeat(limit) { index ->
                checkCancelled()
                if (isAdTask && index < getAdCount("ad_task")) return@repeat
                val taskRes = completeTask(token, ua, taskCode)
                // 广告任务先检查是否已全部完成，再判断是否成功
                if (isAdTask && isAlreadyCompletedResponse(taskRes)) {
                    log("$title 已全部完成（服务器反馈）")
                    setAdCount("ad_task", adTaskTotal())
                    setState("ad_task_done", true)
                    taskCompletedNormally = true
                    return@repeat
                }
                if (taskRes.codeInt() == 0 && (isAdTask || taskRes["data"] == true)) {
                    completedCount = index + 1
                    if (isAdTask) setAdCount("ad_task", index + 1)
                    // 查积分变化
                    delay(1500)
                    val cur = balance(token, ua)
                    val diff = if (cur != null && curBalance != null) cur - curBalance else null
                    val suffix = if (diff != null && diff > 0) " +$diff" else ""
                    log("$title 第${index + 1}次完成$suffix")
                    curBalance = cur ?: curBalance
                } else {
                    // 检查是否服务器返回"已完成"（非广告任务的兜底）
                    if (isAlreadyCompletedResponse(taskRes)) {
                        log("$title 已全部完成（服务器反馈）")
                        if (isAdTask) { setAdCount("ad_task", adTaskTotal()); setState("ad_task_done", true) }
                        taskCompletedNormally = true
                        return@repeat
                    }
                    log("$title 第${index + 1}次失败：${taskRes.messageText()}")
                    taskCompletedNormally = false
                    return@repeat
                }
                if (index < limit - 1) delay(10_000)
            }

            // 任务循环结束后处理本地状态
            if (isAdTask && completedCount >= adTaskTotal()) {
                setState("ad_task_done", true)
            }

            if (isMoreVideoTask && taskCompletedNormally) {
                otherMoreVideoDone = true
                setState("more_video_done", true)
            }
            if (isTakeoutTask && taskCompletedNormally) {
                otherTakeoutDone = true
                setState("takeout_done", true)
            }
            // 两者都完成时标记 other_task_done
            if (otherMoreVideoDone && otherTakeoutDone) {
                setState("other_task_done", true)
            }

            delay(5_000)
        }
        return curBalance
    }

    private fun adTaskTotal(): Int = 10

    private suspend fun runAppVideos(token: String, ua: String, log: suspend (String) -> Unit) {
        val total = 20
        val startFrom = getAdCount("app_video")
        if (startFrom >= total) {
            log("APP 视频任务：今日已完成，跳过")
            return
        }
        if (startFrom > 0) {
            log("APP 视频任务：继续执行（已完成 $startFrom/$total）")
        } else {
            log("开始执行 APP 视频任务")
        }
        var lastBalance = balance(token, ua)
        for (index in startFrom until total) {
            checkCancelled()
            val res = completeTask(token, ua, 2)
            if (res.codeInt() == 0 && res["data"] == true) {
                setAdCount("app_video", index + 1)
                delay(15_000)
                val cur = balance(token, ua)
                val diff = if (cur != null && lastBalance != null) cur - lastBalance else null
                val suffix = if (diff != null && diff > 0) " +$diff" else ""
                log("APP 视频任务完成$suffix")
                lastBalance = cur ?: lastBalance
            } else {
                val msg = res.messageText()
                if (msg.contains("任务已结束") || msg.contains("已结束")) {
                    log("APP 视频任务：今日已完成")
                    setAdCount("app_video", 20)
                } else {
                    log("APP 视频任务停止：$msg")
                }
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
        var lastBalance = balance(token, ua)
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
                setAdCount("alipay_video", index + 1)
                delay(15_000)
                val cur = balance(token, ua)
                val diff = if (cur != null && lastBalance != null) cur - lastBalance else null
                val suffix = if (diff != null && diff > 0) " +$diff" else ""
                log("第${index + 1}次支付宝视频任务完成$suffix")
                lastBalance = cur ?: lastBalance
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
                if (!response.isSuccessful) {
                    debugLog?.e("Runner", "HTTP ${response.code}: ${url.substringAfterLast("/")}, body=${body.take(300)}")
                    error("HTTP ${response.code}: ${body.take(300)}")
                }
                val result = runCatching { jsonAdapter.fromJson(body).orEmpty() }
                    .getOrElse { error("响应解析失败：${it.message ?: body.take(300)}") }
                debugLog?.d("Runner", "${url.substringAfterLast("/")} channel=$channel code=${result.codeInt()} msg=${result.messageText()}")
                result
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
