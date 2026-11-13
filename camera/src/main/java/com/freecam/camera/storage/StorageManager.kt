package com.freecam.camera.storage

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.camera.http.RecordingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class StorageManager(
    private val context: Context,
    private val settings: Settings
) {

    private val TAG = "StorageManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val cleanupJob = Job()
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        AppLog.getInstance(context).log(TAG, "Starting storage manager")
        
        scope.launch(cleanupJob) {
            while (isRunning) {
                try {
                    cleanup()
                } catch (e: Exception) {
                    AppLog.getInstance(context).logError(TAG, "Cleanup error", e)
                }
                // Run cleanup every hour
                kotlinx.coroutines.delay(60 * 60 * 1000)
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        cleanupJob.cancel()
        AppLog.getInstance(context).log(TAG, "Storage manager stopped")
    }

    fun cleanup() {
        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        if (!recordingsDir.exists()) return

        val files = recordingsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.toList() ?: emptyList()
        
        // Sort by last modified (oldest first)
        val sortedFiles = files.sortedBy { it.lastModified() }
        
        // Cleanup by age
        val retentionMillis = settings.retentionMillis()
        val now = System.currentTimeMillis()
        val ageThreshold = now - retentionMillis
        
        for (file in sortedFiles) {
            if (file.lastModified() < ageThreshold) {
                val deleted = file.delete()
                AppLog.getInstance(context).log(TAG, "Deleted old recording: ${file.name} (${if (deleted) "success" else "failed"})")
            }
        }
        
        // Cleanup by size (after age cleanup)
        val remainingFiles = recordingsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.toList() ?: emptyList()
        val totalSize = remainingFiles.sumOf { it.length() }
        val capBytes = settings.storageCapBytes()
        
        if (totalSize > capBytes) {
            var currentSize = totalSize
            for (file in remainingFiles.sortedBy { it.lastModified() }) {
                if (currentSize <= capBytes) break
                val deleted = file.delete()
                if (deleted) {
                    currentSize -= file.length()
                    AppLog.getInstance(context).log(TAG, "Deleted for size cap: ${file.name} (freed ${file.length()} bytes)")
                }
            }
        }
        
        // Also clean events file if too old
        val eventsFile = File(context.getFilesDir(), "events.json")
        if (eventsFile.exists() && eventsFile.lastModified() < ageThreshold) {
            eventsFile.delete()
            AppLog.getInstance(context).log(TAG, "Deleted old events file")
        }
    }

    fun getStorageStats(): StorageStats {
        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        val files = recordingsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.toList() ?: emptyList()
        val totalSize = files.sumOf { it.length() }
        val capBytes = settings.storageCapBytes()
        
        return StorageStats(
            totalFiles = files.size,
            totalSizeBytes = totalSize,
            capBytes = capBytes,
            usagePercent = if (capBytes > 0) (totalSize.toDouble() / capBytes * 100).toInt() else 0
        )
    }

    fun getRecordings(): List<RecordingInfo> {
        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        if (!recordingsDir.exists()) return emptyList()
        return recordingsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.map { file ->
            RecordingInfo(
                filename = file.name,
                path = "/recordings/${file.name}",
                size = file.length(),
                createdAt = file.lastModified(),
                durationSec = 0 // TODO: extract from MP4
            )
        }?.sortedByDescending { it.createdAt } ?: emptyList()
    }

    data class StorageStats(
        val totalFiles: Int,
        val totalSizeBytes: Long,
        val capBytes: Long,
        val usagePercent: Int
    )

    fun isRunning(): Boolean = isRunning
}