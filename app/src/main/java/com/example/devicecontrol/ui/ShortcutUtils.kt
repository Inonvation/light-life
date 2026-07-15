package com.example.devicecontrol.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.devicecontrol.MainActivity
import com.example.devicecontrol.R
import com.example.devicecontrol.data.DeviceItem

const val ACTION_OPEN_DEVICE_SHORTCUT = "com.example.devicecontrol.OPEN_DEVICE_SHORTCUT"
const val EXTRA_GOODS_ID = "goods_id"
const val EXTRA_DEVICE_ID = "device_id"
const val EXTRA_GOODS_NAME = "goods_name"
const val PROJECT_URL = "https://github.com/Inonvation/light-life"

fun shortcutRequestFromIntent(intent: Intent?): DeviceShortcutRequest? {
    if (intent?.action != ACTION_OPEN_DEVICE_SHORTCUT) return null
    return DeviceShortcutRequest(
        goodsId = intent.getStringExtra(EXTRA_GOODS_ID),
        id = intent.getStringExtra(EXTRA_DEVICE_ID),
        goodsName = intent.getStringExtra(EXTRA_GOODS_NAME),
    )
}

fun pinDeviceShortcut(context: Context, device: DeviceItem) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(context, "当前系统不支持添加桌面快捷方式", Toast.LENGTH_LONG).show()
        return
    }
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    if (shortcutManager?.isRequestPinShortcutSupported != true) {
        Toast.makeText(context, "当前桌面不支持添加快捷方式", Toast.LENGTH_LONG).show()
        return
    }
    val label = device.goodsName.ifBlank { "历史设备" }
    val shortcutIntent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_OPEN_DEVICE_SHORTCUT
        putExtra(EXTRA_GOODS_ID, device.goodsId)
        putExtra(EXTRA_DEVICE_ID, device.id)
        putExtra(EXTRA_GOODS_NAME, device.goodsName)
    }
    val shortcut = ShortcutInfo.Builder(context, "device-${device.goodsId ?: device.id ?: device.goodsName.hashCode()}")
        .setShortLabel(label.take(10))
        .setLongLabel(label)
        .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher))
        .setIntent(shortcutIntent)
        .build()
    shortcutManager.requestPinShortcut(shortcut, null)
    Toast.makeText(context, "已尝试添加桌面快捷方式，若失败请检查是否已授权软件添加快捷方式的权限", Toast.LENGTH_LONG).show()
}

fun openProjectHome(context: Context) {
    try {
        val uri = Uri.parse(PROJECT_URL)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val appCtx = context.applicationContext
        appCtx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.util.Log.e("OpenProject", "Failed to open URL", e)
        Toast.makeText(context, "未找到浏览器，请手动打开 $PROJECT_URL", Toast.LENGTH_LONG).show()
    }
}
