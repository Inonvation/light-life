package com.inonvation.lightlife.data

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest
import kotlin.jvm.Volatile

class TaskCancelledException : Exception()

class PointsTaskRunner(
    private val tokenProvider: () -> String?,
    private val context: Context? = null,
) {
    @Volatile
    var cancelled = false
    @Volatile
    var randomDelay = false
    private var debugLog: DebugLogStore? = null
    fun setDebugLog(log: DebugLogStore?) { debugLog = log }

    /**
     * 进度回调，供前台 Service 更新通知。
     * @param stage 阶段标识：signin / app_video / alipay_video_task / alipay_video / ad_task / task_list / home_page
     * @param current 当前已完成次数
     * @param total 该阶段总次数（0 表示无进度概念）
     */
    var onProgress: ((stage: String, current: Int, total: Int) -> Unit)? = null

    private val client = HttpClientProvider.client

    private val jsonAdapter: JsonAdapter<Map<String, Any?>> = Moshi.Builder()
        .build()
        .adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    private val statePrefs by lazy {
        context?.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE)
    }

    private fun today(): String = java.time.LocalDate.now().toString()

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

    /** 随机延迟 2~6 秒，仅在 randomDelay 开启时生效。前缀 \u200B 标记居中显示 */
    private suspend fun maybeRandomDelay(log: (suspend (String) -> Unit)? = null) {
        if (randomDelay) {
            log?.invoke("\u200B随机延迟生效中")
            delay(2000L + (Math.random() * 4000).toLong())
        }
    }

    /** 判断服务器返回是否表示任务已全部完成 */
    private fun isAlreadyCompletedResponse(taskRes: Map<String, Any?>): Boolean {
        val code = taskRes.codeInt()
        val msg = taskRes.messageText()
        val keys = listOf("已完成", "已结束", "完成", "已达上限", "已达今日上限", "已达最大次数", "次数已满", "今日已满")
        if (code != 0 && keys.any { msg.contains(it) }) return true
        if (code == 0 && taskRes["data"] != true && keys.any { msg.contains(it) }) return true
        return false
    }

    suspend fun run(userAgent: String, log: suspend (String) -> Unit) {
        checkCancelled()
        homePageSubtaskIndex = 0
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: error("请先在我的页面登录")

        val user = request("https://userapi.qiekj.com/user/info", token, userAgent, mapOf("token" to token))
        val userName = user.dataMap()["userName"]?.toString()
        log(if (userName.isNullOrBlank()) "当前账号未设置昵称" else "当前账号：$userName")

        var lastBalance = balance(token, userAgent)
        log("任务前积分：${lastBalance ?: "-"}")

        // 本地状态检查：完成过的步骤直接跳过
        checkCancelled()
        if (getState("signin_done")) {
            log("签到：已跳过")
        } else {
            signIn(token, userAgent, log)
            delay(2000)
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("签到：+${diff} (${cur})")
            } else if (cur != null) {
                log("签到：${cur}")
            }
            lastBalance = cur
        }
        checkCancelled()
        shieldingQuery(token, userAgent, log)

        // 首页浏览第1次（5s）
        log("首页浏览...")
        runNextHomePageSubtask(token, userAgent, log)
        delay(1000)
        run {
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("首页浏览 (1/3)：+${diff} (${cur})")
            }
            lastBalance = cur
        }

        checkCancelled()
        delay(1000)
        lastBalance = runTaskList(token, userAgent, log, lastBalance)

        // 首页浏览第2次（10s）
        checkCancelled()
        runNextHomePageSubtask(token, userAgent, log)
        delay(1000)
        run {
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("首页浏览 (2/3)：+${diff} (${cur})")
            }
            lastBalance = cur
        }

        checkCancelled()
        runAppVideos(token, userAgent, log)
        delay(2000)
        run {
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("APP视频：+${diff} (${cur})")
            }
            lastBalance = cur
        }

        checkCancelled()
        runAlipayVideoTasks(token, userAgent, log)
        delay(3000)
        run {
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("支付宝视频：+${diff} (${cur})")
            }
            lastBalance = cur
        }

        // 首页浏览第3次（30s）
        checkCancelled()
        runNextHomePageSubtask(token, userAgent, log)
        delay(1000)
        run {
            val cur = balance(token, userAgent)
            if (cur != null && lastBalance != null) {
                val diff = cur - lastBalance
                log("首页浏览 (3/3)：+${diff} (${cur})")
            }
            lastBalance = cur
        }
        // 首页浏览全部完成，记录状态
        setState("home_page_done", true)

        checkCancelled()
        runAlipayAds(token, userAgent, log)
        delay(3000)
        val after = balance(token, userAgent)
        if (after != null && lastBalance != null) {
            val diff = after - lastBalance
            log("支付宝广告：+${diff} (${after})")
        }
        val totalGained = after?.let { a -> lastBalance?.let { _ -> a - (lastBalance ?: a) } }
        log("任务完成，当前积分：${after ?: "-"}（今日 +${totalGained ?: 0}）")
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
                onProgress?.invoke("signin", 1, 1)
            }
            33001 -> { log("今天已经签到过"); setState("signin_done", true); onProgress?.invoke("signin", 1, 1) }
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
        log("屏蔽：${res.messageText()}")
    }

    private var homePageSubtaskIndex = 0

    private val homePageSubtasks = listOf(
        "4a86e8b5-e46c-4dac-9e73-c6e3cf39c7d6" to "5s",
        "73310f73-b076-40d5-a53f-c79c48f14d64" to "10s",
        "f3814d95-38f0-4778-8da3-6b8e3fc113d0" to "30s",
    )

    /** 执行首页浏览的下一个子任务 */
    private suspend fun runNextHomePageSubtask(token: String, ua: String, log: suspend (String) -> Unit) {
        if (homePageSubtaskIndex >= homePageSubtasks.size) {
            log("首页浏览：已完成，跳过")
            return
        }
        val current = homePageSubtaskIndex + 1
        val (subtaskCode, label) = homePageSubtasks[homePageSubtaskIndex]
        val res = try {
            request(
                url = "https://userapi.qiekj.com/task/completed",
                token = token,
                userAgent = ua,
                fields = mapOf(
                    "taskCode" to "8b475b42-df8b-4039-b4c1-f9a0174a611a",
                    "subtaskCode" to subtaskCode,
                    "token" to token,
                ),
            )
        } catch (e: Exception) {
            log("首页浏览 $current/3（${label}）：请求失败 ${e.message}")
            homePageSubtaskIndex++
            return
        }
        if (res.codeInt() == 0 && res["data"] == true) {
            log("首页浏览 $current/3（${label}）：成功")
        } else {
            log("首页浏览 $current/3（${label}）：${res.messageText()}")
        }
        onProgress?.invoke("home_page", current, 3)
        setAdCount("home_page_count", current)
        homePageSubtaskIndex++
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
        log("任务列表...")
        onProgress?.invoke("task_list", 0, 0)
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
            val isTakeoutTask = title.contains("点外卖")
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
                if (completed >= adLimit) {
                    setAdCount("ad_task", adLimit)
                    setState("ad_task_done", true)
                    log("$title 已完成，跳过")
                    continue
                }
                val adTaskDone = getAdCount("ad_task")
                if (adTaskDone >= adLimit) {
                    setState("ad_task_done", true)
                    continue
                }
                if (adTaskDone > 0) {
                    log("$title：继续（$adTaskDone/$adLimit）")
                } else {
                    log("开始执行：$title")
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
                val code = taskRes.codeInt()
                val dataVal = taskRes["data"]
                val msg = taskRes.messageText()
                if (code == 0 && (isAdTask || dataVal == true)) {
                    // 广告任务：data 为 false 说明服务器认为已完成
                    if (isAdTask && dataVal != true) {
                        log("$title：已完成")
                        setAdCount("ad_task", 10)
                        setState("ad_task_done", true)
                        taskCompletedNormally = true
                        return@repeat
                    }
                    completedCount = index + 1
                    if (isAdTask) setAdCount("ad_task", index + 1)
                    delay(1500)
                    val cur = balance(token, ua)
                    val diff = if (cur != null && curBalance != null) cur - curBalance else null
                    val suffix = if (diff != null && diff > 0) " +$diff" else ""
                    log("$title 第${index + 1}/${limit}次完成$suffix")
                    curBalance = cur ?: curBalance
                    onProgress?.invoke("ad_task", index + 1, 10)
                } else {
                    if (isAlreadyCompletedResponse(taskRes) || msg.contains("任务已结束") || msg.contains("已结束")) {
                        log("$title：已完成")
                        if (isAdTask) { setAdCount("ad_task", 10); setState("ad_task_done", true) }
                        taskCompletedNormally = true
                    } else {
                        log("$title 第${index + 1}/${limit}次失败：$msg")
                        taskCompletedNormally = false
                    }
                    return@repeat
                }
                if (index < limit - 1) { delay(10_000); maybeRandomDelay(log) }
            }

            // 任务循环结束后处理本地状态
            if (isAdTask && completedCount >= 10) {
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
            maybeRandomDelay(log)
        }
        return curBalance
    }

    private suspend fun runAppVideos(token: String, ua: String, log: suspend (String) -> Unit) {
        val total = 20
        val startFrom = getAdCount("app_video")
        if (startFrom >= total) {
            log("APP视频：已完成，跳过")
            setState("app_video_done", true)
            return
        }
        if (startFrom > 0) {
            log("APP视频：继续（$startFrom/$total）")
        } else {
            log("APP视频...")
        }
        var lastBalance = balance(token, ua)
        for (index in startFrom until total) {
            checkCancelled()
            val res = completeTask(token, ua, 2)
            if (res.codeInt() == 0 && res["data"] == true) {
                setAdCount("app_video", index + 1)
                onProgress?.invoke("app_video", index + 1, 20)
                delay(15_000)
                maybeRandomDelay(log)
                val cur = balance(token, ua)
                val diff = if (cur != null && lastBalance != null) cur - lastBalance else null
                val suffix = if (diff != null && diff > 0) " +$diff" else ""
                log("APP视频（${index + 1}/20）$suffix")
                lastBalance = cur ?: lastBalance
            } else {
                val msg = res.messageText()
                val code = res.codeInt()
                // 服务器返回成功但数据为false，可能表示任务已完成
                if (code == 0 && res["data"] == false) {
                    log("APP视频：已完成")
                    setAdCount("app_video", 20)
                    setState("app_video_done", true)
                } else if (msg.contains("任务已结束") || msg.contains("已结束")) {
                    log("APP视频：已完成")
                    setAdCount("app_video", 20)
                    setState("app_video_done", true)
                } else {
                    log("APP视频停止：$msg")
                }
                return
            }
        }
        // 循环全部完成，记录状态
        setState("app_video_done", true)
    }

    /** 支付宝视频任务（taskCode=dc18b525，最多10次） */
    private suspend fun runAlipayVideoTasks(token: String, ua: String, log: suspend (String) -> Unit) {
        val taskCode = "dc18b525-f679-47d8-805a-e331f8f3341d"
        val total = 10
        val startFrom = getAdCount("alipay_video_task")
        if (startFrom >= total) {
            log("支付宝视频：已完成，跳过")
            setState("alipay_video_task_done", true)
            return
        }
        if (startFrom > 0) {
            log("支付宝视频：继续（$startFrom/$total）")
        } else {
            log("支付宝视频...")
        }
        var lastBalance = balance(token, ua)
        for (index in startFrom until total) {
            checkCancelled()
            val res = request(
                url = "https://userapi.qiekj.com/task/completed",
                token = token,
                userAgent = ua,
                fields = mapOf("taskCode" to taskCode, "token" to token),
                channel = "alipay",
            )
            if (res.codeInt() == 0 && res["data"] == true) {
                setAdCount("alipay_video_task", index + 1)
                onProgress?.invoke("alipay_video_task", index + 1, total)
                delay(15_000)
                maybeRandomDelay(log)
                val cur = balance(token, ua)
                val diff = if (cur != null && lastBalance != null) cur - lastBalance else null
                val suffix = if (diff != null && diff > 0) " +$diff" else ""
                log("支付宝视频（${index + 1}/10）$suffix")
                lastBalance = cur ?: lastBalance
            } else {
                val msg = res.messageText()
                val code = res.codeInt()
                if (code == 0 && res["data"] == false) {
                    log("支付宝视频：已完成")
                    setAdCount("alipay_video_task", total)
                    setState("alipay_video_task_done", true)
                } else if (isAlreadyCompletedResponse(res) || msg.contains("任务已结束") || msg.contains("已结束")) {
                    log("支付宝视频：已完成")
                    setAdCount("alipay_video_task", total)
                    setState("alipay_video_task_done", true)
                } else {
                    log("支付宝视频停止：$msg")
                }
                return
            }
        }
        // 循环全部完成，记录状态
        setState("alipay_video_task_done", true)
    }

    /** 支付宝广告任务（taskCode=9，最多50次） */
    private suspend fun runAlipayAds(token: String, ua: String, log: suspend (String) -> Unit) {
        val total = 50
        val startFrom = getAdCount("alipay_video")
        if (startFrom >= total) {
            log("支付宝广告：已完成，跳过")
            setState("alipay_video_done", true)
            return
        }
        if (startFrom > 0) {
            log("支付宝广告：继续（$startFrom/$total）")
        } else {
            log("支付宝广告...")
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
                onProgress?.invoke("alipay_video", index + 1, 50)
                delay(15_000)
                maybeRandomDelay(log)
                val cur = balance(token, ua)
                val diff = if (cur != null && lastBalance != null) cur - lastBalance else null
                val suffix = if (diff != null && diff > 0) " +$diff" else ""
                log("支付宝广告（${index + 1}/50）$suffix")
                lastBalance = cur ?: lastBalance
            } else {
                val msg = res.messageText()
                val code = res.codeInt()
                val dataVal = res["data"]
                // 服务器返回成功但数据为false，可能表示任务已完成
                if (code == 0 && dataVal == false) {
                    log("支付宝广告：已完成")
                    setAdCount("alipay_video", 50)
                    setState("alipay_video_done", true)
                } else if (isAlreadyCompletedResponse(res) || msg.contains("任务已结束") || msg.contains("已结束")) {
                    log("支付宝广告：已完成")
                    setAdCount("alipay_video", 50)
                    setState("alipay_video_done", true)
                } else {
                    log("支付宝广告停止：$msg")
                }
                return
            }
        }
        // 循环全部完成，记录状态
        setState("alipay_video_done", true)
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
