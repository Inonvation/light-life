package com.example.devicecontrol.data

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest

class HeaderInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val timestamp = System.currentTimeMillis().toString()
        val isLoginApi = chain.request().url.encodedPath.startsWith("/common/")
            || chain.request().url.encodedPath.startsWith("/user/reg")
        val channel = if (isLoginApi) ApiConfig.LOGIN_CHANNEL else ApiConfig.API_CHANNEL
        val builder = chain.request().newBuilder()
            .header("Version", ApiConfig.VERSION)
            .header("channel", channel)
            .header("phoneBrand", ApiConfig.PHONE_BRAND)
            .header("User-Agent", ApiConfig.USER_AGENT)
            .header("Content-Type", ApiConfig.CONTENT_TYPE)
            .header("timestamp", timestamp)
            .header("Host", "userapi.qiekj.com")
            .header("Connection", "Keep-Alive")
            .header("Accept-Encoding", "gzip")

        tokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
            builder.header("token", token)
            builder.header("Authorization", token)
            if (!isLoginApi) {
                var sign = sign(timestamp, chain.request().url.encodedPath, token, channel)
                builder.header("sign", sign)
            }
        }

        return chain.proceed(builder.build())
    }

    private fun sign(timestamp: String, path: String, token: String, channel: String): String {
        var raw = "appSecret=${ApiConfig.ANDROID_SECRET}&channel=$channel&timestamp=$timestamp&token=$token&version=${ApiConfig.VERSION}$path"
        var digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
