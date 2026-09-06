package com.inonvation.lightlife.data.qzxy

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 趣智校园会话与绑定设备存储。
 * 与胖乖生活平台的 TokenStore 相互独立，两套账号互不影响。
 */
class QzxyAuthStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "qzxy_secure_auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun readSession(): QzxySession? {
        val loginCode = prefs.getString(KEY_LOGIN_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
        return QzxySession(
            loginCode = loginCode,
            userId = prefs.getString(KEY_USER_ID, "").orEmpty(),
            accountId = prefs.getString(KEY_ACCOUNT_ID, "").orEmpty(),
            projectId = prefs.getString(KEY_PROJECT_ID, "").orEmpty(),
            telephone = prefs.getString(KEY_TELEPHONE, "").orEmpty(),
            userName = prefs.getString(KEY_USER_NAME, "").orEmpty(),
        )
    }

    fun saveSession(session: QzxySession) {
        prefs.edit()
            .putString(KEY_LOGIN_CODE, session.loginCode)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_ACCOUNT_ID, session.accountId)
            .putString(KEY_PROJECT_ID, session.projectId)
            .putString(KEY_TELEPHONE, session.telephone)
            .putString(KEY_USER_NAME, session.userName)
            .apply()
    }

    fun readBoundDevice(): QzxyBoundDevice? {
        val snCode = prefs.getString(KEY_BOUND_SN, null)?.takeIf { it.isNotBlank() } ?: return null
        return QzxyBoundDevice(
            mac = prefs.getString(KEY_BOUND_MAC, "").orEmpty(),
            snCode = snCode,
            name = prefs.getString(KEY_BOUND_NAME, "").orEmpty(),
        )
    }

    fun saveBoundDevice(device: QzxyBoundDevice) {
        prefs.edit()
            .putString(KEY_BOUND_MAC, device.mac)
            .putString(KEY_BOUND_SN, device.snCode)
            .putString(KEY_BOUND_NAME, device.name)
            .apply()
    }

    /** 只清登录会话，保留绑定设备（换账号登录同一宿舍无需重新绑定） */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_LOGIN_CODE)
            .remove(KEY_USER_ID)
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_PROJECT_ID)
            .remove(KEY_TELEPHONE)
            .remove(KEY_USER_NAME)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_LOGIN_CODE = "loginCode"
        const val KEY_USER_ID = "userId"
        const val KEY_ACCOUNT_ID = "accountId"
        const val KEY_PROJECT_ID = "projectId"
        const val KEY_TELEPHONE = "telephone"
        const val KEY_USER_NAME = "userName"
        const val KEY_BOUND_MAC = "boundDeviceMac"
        const val KEY_BOUND_SN = "boundDeviceSn"
        const val KEY_BOUND_NAME = "boundDeviceName"
    }
}
