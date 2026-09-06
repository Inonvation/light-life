package com.inonvation.lightlife.data.qzxy

import java.security.MessageDigest

/**
 * 趣智校园的密码传输格式：MD5 后取后 10 位大写。
 * 安全性低，但这是官方协议约定，第三方客户端必须遵循。
 */
object QzxyPassword {
    fun encrypt(password: String): String {
        val md5 = MessageDigest.getInstance("MD5")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return if (md5.length >= 10) md5.substring(md5.length - 10).uppercase() else md5.uppercase()
    }
}
