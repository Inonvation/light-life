package com.inonvation.lightlife.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TaskLogStore(private val context: Context) {
    private val logDir = File(context.filesDir, "task_logs").also { it.mkdirs() }

    suspend fun save(content: String) = withContext(Dispatchers.IO) {
        val now = java.text.SimpleDateFormat("MMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date())
        val file = File(logDir, "run_${now}.txt")
        // Keep only last 10 files
        val files = logDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        if (files.size >= 10) files.take(files.size - 9).forEach { it.delete() }
        file.writeText(content, Charsets.UTF_8)
    }

    fun deleteFile(name: String) {
        File(logDir, name).delete()
    }

    fun clearAll() {
        logDir.listFiles()?.forEach { it.delete() }
    }

    fun clearToday() {
        val todayPrefix = java.text.SimpleDateFormat("MMdd", java.util.Locale.CHINA).format(java.util.Date())
        logDir.listFiles()?.filter { it.name.startsWith("run_$todayPrefix") }?.forEach { it.delete() }
    }

    fun listFiles(): List<Pair<String, String>> {
        return logDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name to it.readText(Charsets.UTF_8) }
            ?: emptyList()
    }
}