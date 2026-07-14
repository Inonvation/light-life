package com.example.devicecontrol.data

import android.content.Context
import java.io.File

class TaskLogStore(private val context: Context) {
    private val logDir = File(context.filesDir, "task_logs").also { it.mkdirs() }

    fun save(content: String) {
        val now = java.text.SimpleDateFormat("MMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date())
        val file = File(logDir, "run_${now}.txt")
        // Keep only last 10 files
        val files = logDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        if (files.size >= 10) files.take(files.size - 9).forEach { it.delete() }
        file.writeText(content, Charsets.UTF_8)
    }

    fun listFiles(): List<Pair<String, String>> {
        return logDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name to it.readText(Charsets.UTF_8) }
            ?: emptyList()
    }
}