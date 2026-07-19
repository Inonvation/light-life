package com.inonvation.lightlife.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

data class QuickLink(val name: String = "", val url: String = "", val packageName: String = "", val presetIndex: Int = -1, val iconUri: String = "")

val DEFAULT_QUICK_LINKS = listOf(
    QuickLink("淘宝取件码", "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code", "com.taobao.taobao", 0),
    QuickLink("拼多多取件码", "pinduoduo://com.xunmeng.pinduoduo/mdkd/package", "com.xunmeng.pinduoduo", 1),
    QuickLink("学校教务系统", "https://eapp2.juwp.edu.cn:9443/cas/login?service=http%3A%2F%2Fportal.juwp.edu.cn%2Fcas%2Flogin_portal", "", 2),
)

class QuickLinkStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("quick_links", Context.MODE_PRIVATE)
    private val count = 9
    private val iconDir = File(context.filesDir, "quicklink_icons").apply { mkdirs() }

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)
    fun setEnabled(v: Boolean) { prefs.edit().putBoolean("enabled", v).apply() }

    fun getLinks(): List<QuickLink> {
        return (0 until count).map { i ->
            QuickLink(
                name = prefs.getString("name_$i", "") ?: "",
                url = prefs.getString("url_$i", "") ?: "",
                packageName = prefs.getString("pkg_$i", "") ?: "",
                presetIndex = prefs.getInt("preset_$i", -1),
                iconUri = prefs.getString("icon_$i", "") ?: "",
            )
        }
    }

    fun updateLink(index: Int, name: String, url: String, packageName: String, presetIndex: Int = -1) {
        prefs.edit()
            .putString("name_$index", name)
            .putString("url_$index", url)
            .putString("pkg_$index", packageName)
            .putInt("preset_$index", presetIndex)
            .apply()
    }

    fun saveIcon(index: Int, sourceUri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return false
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // 压缩到合理大小（128x128）
            val scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true)

            val iconFile = File(iconDir, "icon_$index.png")
            FileOutputStream(iconFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()

            prefs.edit().putString("icon_$index", iconFile.absolutePath).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeIcon(index: Int) {
        val path = prefs.getString("icon_$index", "") ?: ""
        if (path.isNotBlank()) {
            File(path).delete()
        }
        prefs.edit().remove("icon_$index").apply()
    }

    fun getIconFile(index: Int): File? {
        val path = prefs.getString("icon_$index", "") ?: ""
        return if (path.isNotBlank()) File(path).takeIf { it.exists() } else null
    }

    fun swapLinks(index1: Int, index2: Int) {
        val links = getLinks().toMutableList()
        val temp = links[index1]
        links[index1] = links[index2]
        links[index2] = temp
        links.forEachIndexed { i, link ->
            prefs.edit()
                .putString("name_$i", link.name)
                .putString("url_$i", link.url)
                .putString("pkg_$i", link.packageName)
                .putInt("preset_$i", link.presetIndex)
                .apply()
        }
    }

    /** 导出所有快捷链接数据（用于备份） */
    fun exportData(): Map<String, *> {
        return prefs.all
    }

    /** 导入快捷链接数据（用于恢复） */
    fun importData(data: Map<String, *>) {
        val editor = prefs.edit()
        data.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.apply()
    }

    /** 导出图标文件为 Base64 编码的 Map（用于备份） */
    fun exportIcons(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (i in 0 until count) {
            val file = getIconFile(i)
            if (file != null) {
                try {
                    val bytes = file.readBytes()
                    result["icon_$i"] = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } catch (_: Exception) {}
            }
        }
        return result
    }

    /** 导入图标文件（用于恢复） */
    fun importIcons(icons: Map<String, String>) {
        icons.forEach { (key, base64) ->
            val index = key.removePrefix("icon_").toIntOrNull() ?: return@forEach
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                val iconFile = File(iconDir, "icon_$index.png")
                FileOutputStream(iconFile).use { it.write(bytes) }
                prefs.edit().putString("icon_$index", iconFile.absolutePath).apply()
            } catch (_: Exception) {}
        }
    }
}
