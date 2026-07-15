package com.example.devicecontrol.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试日志存储。开启后实时写入文件，关闭后不记录。
 * 日志格式：[HH:mm:ss.SSS] [级别] [模块] 内容
 */
class DebugLogStore(private val context: Context) {
    private val logDir = File(context.filesDir, "debug_logs").also { it.mkdirs() }
    private var writer: OutputStreamWriter? = null
    private var currentFile: File? = null
    private var sessionStartLine: Int = 0

    companion object {
        private const val PREF_NAME = "debug_log_prefs"
        private const val KEY_ENABLED = "debug_log_enabled"
        private const val MAX_FILES = 5
        private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
        private val dateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
    }

    /**
     * 标记当前调试日志的会话起始点，用于后续获取本次会话的日志内容
     */
    fun markSessionStart() {
        if (!isEnabled()) return
        closeWriter()
        val content = currentFile?.readText(Charsets.UTF_8) ?: ""
        sessionStartLine = if (content.isBlank()) 0 else content.lines().size
    }

    /**
     * 获取从会话起始点到现在的调试日志内容
     */
    fun getSessionContent(): String {
        if (!isEnabled()) return ""
        closeWriter()
        val content = currentFile?.readText(Charsets.UTF_8) ?: ""
        if (content.isBlank()) return ""
        val lines = content.lines()
        return if (sessionStartLine < lines.size) {
            lines.subList(sessionStartLine, lines.size).joinToString("\n")
        } else ""
    }

    fun isEnabled(): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) closeWriter()
    }

    @Synchronized
    fun d(tag: String, msg: String) {
        if (!isEnabled()) return
        write("D", tag, msg)
    }

    @Synchronized
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (!isEnabled()) return
        val fullMsg = if (throwable != null) {
            "$msg\n${throwable.javaClass.simpleName}: ${throwable.message}\n${throwable.stackTraceToString()}"
        } else msg
        write("E", tag, fullMsg)
    }

    @Synchronized
    fun i(tag: String, msg: String) {
        if (!isEnabled()) return
        write("I", tag, msg)
    }

    @Synchronized
    fun w(tag: String, msg: String) {
        if (!isEnabled()) return
        write("W", tag, msg)
    }

    private fun write(level: String, tag: String, msg: String) {
        try {
            val w = getWriter()
            val time = timeFmt.format(Date())
            w.write("[$time] [$level] [$tag] $msg\n")
            w.flush()
        } catch (_: Exception) {}
    }

    private fun getWriter(): OutputStreamWriter {
        writer?.let { return it }
        pruneOldFiles()
        val file = File(logDir, "debug_${dateFmt.format(Date())}.txt")
        currentFile = file
        val w = OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8)
        writer = w
        return w
    }

    private fun closeWriter() {
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        currentFile = null
    }

    private fun pruneOldFiles() {
        val files = logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (files.size >= MAX_FILES) {
            files.drop(MAX_FILES - 1).forEach { it.delete() }
        }
    }

    fun listFiles(): List<Pair<String, String>> {
        closeWriter() // 确保当前文件写入完毕再读
        return logDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name to it.readText(Charsets.UTF_8) }
            ?: emptyList()
    }

    fun getLatestContent(): String {
        closeWriter()
        return logDir.listFiles()
            ?.maxByOrNull { it.lastModified() }
            ?.readText(Charsets.UTF_8)
            ?: ""
    }

    fun deleteFile(name: String) {
        File(logDir, name).delete()
    }

    fun clearAll() {
        closeWriter()
        logDir.listFiles()?.forEach { it.delete() }
    }
}
