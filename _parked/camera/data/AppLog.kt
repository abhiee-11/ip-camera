package com.freecam.camera.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

class AppLog(private val context: Context) {

    private val maxLines = 500
    private val logs = ConcurrentLinkedQueue<String>()
    private val logFile: File

    init {
        logFile = File(context.filesDir, "app_log.txt")
        loadFromFile()
    }

    private fun loadFromFile() {
        if (logFile.exists()) {
            logFile.readLines().forEach { logs.add(it) }
            while (logs.size > maxLines) logs.poll()
        }
    }

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$timestamp] $tag: $message${if (throwable != null) "\n${Log.getStackTraceString(throwable)}" else ""}"
        logs.add(line)
        while (logs.size > maxLines) logs.poll()
        appendToFile(line)
        if (BuildConfig.DEBUG) {
            Timber.tag(tag).d(message, throwable)
        }
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$timestamp] ERROR $tag: $message${if (throwable != null) "\n${Log.getStackTraceString(throwable)}" else ""}"
        logs.add(line)
        while (logs.size > maxLines) logs.poll()
        appendToFile(line)
        Timber.tag(tag).e(throwable, message)
    }

    private fun appendToFile(line: String) {
        try {
            FileWriter(logFile, true).use { it.write("$line\n") }
            // Trim file if too large
            if (logFile.length() > 100 * 1024) {
                val lines = logFile.readLines()
                if (lines.size > maxLines) {
                    FileWriter(logFile).use { writer ->
                        lines.takeLast(maxLines).forEach { writer.write("$it\n") }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to write log file")
        }
    }

    fun getLogs(): List<String> = logs.toList()

    fun clear() {
        logs.clear()
        logFile.delete()
        log("AppLog", "Logs cleared")
    }

    fun copyAll(): String = logs.joinToString("\n")
}