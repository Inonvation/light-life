package com.inonvation.lightlife.data

import android.content.Context

data class QuickLink(val name: String = "", val url: String = "", val packageName: String = "", val presetIndex: Int = -1)

val DEFAULT_QUICK_LINKS = listOf(
    QuickLink("淘宝取件码", "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code", "com.taobao.taobao", 0),
    QuickLink("拼多多取件码", "pinduoduo://com.xunmeng.pinduoduo/mdkd/package", "com.xunmeng.pinduoduo", 1),
    QuickLink("学校教务系统", "https://eapp2.juwp.edu.cn:9443/cas/login?service=http%3A%2F%2Fportal.juwp.edu.cn%2Fcas%2Flogin_portal", "", 2),
)

class QuickLinkStore(context: Context) {
    private val prefs = context.getSharedPreferences("quick_links", Context.MODE_PRIVATE)
    private val count = 9

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)
    fun setEnabled(v: Boolean) { prefs.edit().putBoolean("enabled", v).apply() }

    fun getLinks(): List<QuickLink> {
        return (0 until count).map { i ->
            QuickLink(
                name = prefs.getString("name_$i", "") ?: "",
                url = prefs.getString("url_$i", "") ?: "",
                packageName = prefs.getString("pkg_$i", "") ?: "",
                presetIndex = prefs.getInt("preset_$i", -1),
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
                .apply()
        }
    }
}
