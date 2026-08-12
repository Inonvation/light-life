package com.inonvation.lightlife.data

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest

/** 签到执行结果 */
data class SignInResult(
    val success: Boolean,
    val alreadySigned: Boolean = false,
    val message: String,
    val integral: Int? = null,
)

/**
 * 轻量签到器。只保留每日签到、积分余额查询和 Android 端签名逻辑。
 * 视频、广告、首页浏览等已失效的任务接口全部移除。
 */
class SignInRunner(
    private val tokenProvider: () -> String?,
    private val context: Context? = null,
) {
    private val client = HttpClientProvider.client
    private val jsonAdapter: JsonAdapter<Map<String, Any?>> = MoshiProvider.instance
        .adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    private val prefs by lazy { context?.getSharedPreferences("ad_video_state", Context.MODE_PRIVATE) }

    private fun today(): String = DateUtils.today()

    /** 今天是否已签到 */
    fun isSignedInToday(): Boolean {
        val p = prefs ?: return false
        return p.getString(KEY_DONE_DATE, "") == today() && p.getBoolean(KEY_DONE, false)
    }

    private fun markSignedInToday() {
        prefs?.edit()
            ?.putBoolean(KEY_DONE, true)
            ?.putString(KEY_DONE_DATE, today())
            ?.apply()
    }

    /** 执行签到，返回结果 */
    suspend fun signIn(userAgent: String): SignInResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SignInResult(success = false, message = "未登录")
        val res = request(
            url = "https://userapi.qiekj.com/signin/doUserSignIn",
            token = token,
            userAgent = userAgent,
            fields = mapOf("activityId" to "600001", "token" to token),
        )
        return when (res.codeInt()) {
            0 -> {
                markSignedInToday()
                val integral = (res.dataMap()["totalIntegral"] as? Number)?.toInt()
                SignInResult(success = true, message = "签到成功", integral = integral)
            }
            33001 -> {
                markSignedInToday()
                SignInResult(success = true, alreadySigned = true, message = "今天已经签到过")
            }
            else -> SignInResult(success = false, message = res.messageText())
        }
    }

    /** 查询当前积分余额 */
    suspend fun queryBalance(userAgent: String): Int? {
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return null
        val res = request(
            url = "https://userapi.qiekj.com/user/balance",
            token = token,
            userAgent = userAgent,
            fields = mapOf("token" to token),
        )
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
                if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(200)}")
                runCatching { jsonAdapter.fromJson(body).orEmpty() }
                    .getOrElse { error("响应解析失败：${it.message ?: body.take(200)}") }
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
        val sign = sign(timestamp, url, token)
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

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun Map<String, Any?>.codeInt(): Int? = (this["code"] as? Number)?.toInt()
    private fun Map<String, Any?>.messageText(): String =
        this["msg"]?.toString() ?: this["message"]?.toString() ?: "未知结果"
    private fun Map<String, Any?>.dataMap(): Map<String, Any?> = this["data"] as? Map<String, Any?> ?: emptyMap()

    private companion object {
        const val VERSION = ApiConfig.VERSION
        const val ANDROID_SECRET = ApiConfig.ANDROID_SECRET
        const val KEY_DONE = "signin_done"
        const val KEY_DONE_DATE = "signin_done_date"
    }
}
