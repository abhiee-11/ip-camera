package com.freecam.camera.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.StatFs
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.common.model.RecordingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/** Keeps the recording directory inside its retention and size limits. */
class StorageManager(
    private val context: Context,
    private val settings: Settings,
    private val appLog: AppLog
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val cleanupJob = Job()
    private var running = false

    fun start() {
        if (running) return
        running = true
        appLog.log(TAG, "Starting storage manager")
        scope.launch(cleanupJob) {
            while (running) {
                try {
                    cleanup()
                } catch (e: Exception) {
                    appLog.logError(TAG, "Cleanup error", e)
                }
                delay(TimeUnit.HOURS.toMillis(1))
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        cleanupJob.cancel()
        appLog.log(TAG, "Storage manager stopped")
    }

    fun recordingsDir(): File = File(context.getExternalFilesDir(null), "recordings")

    fun cleanup() {
        val recordingsDir = recordingsDir()
        if (!recordingsDir.exists()) return

        val files = recordingsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.toList()
            ?: emptyList()

        val ageThreshold = System.currentTimeMillis() - settings.retentionMillis()
        for (file in files.sortedBy { it.lastModified() }) {
            if (file.lastModified() < ageThreshold) {
                appLog.log(TAG, "Deleted old recording: ${file.name} (${file.delete()})")
            }
        }

        val remaining = recordingsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.toList()
            ?: emptyList()
        var totalSize = remaining.sumOf { it.length() }
        val capBytes = settings.storageCapBytes()
        if (totalSize > capBytes) {
            for (file in remaining.sortedBy { it.lastModified() }) {
                if (totalSize <= capBytes) break
                val length = file.length()
                if (file.delete()) {
                    totalSize -= length
                    appLog.log(TAG, "Deleted for size cap: ${file.name}")
                }
            }
        }
    }

    fun getStorageStats(): StorageStats {
        val files = recordingsDir().listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.toList()
            ?: emptyList()
        val totalSize = files.sumOf { it.length() }
        val capBytes = settings.storageCapBytes()
        return StorageStats(
            totalFiles = files.size,
            totalSizeBytes = totalSize,
            capBytes = capBytes,
            usagePercent = if (capBytes > 0) (totalSize.toDouble() / capBytes * 100).toInt() else 0
        )
    }

    /** Free bytes on the volume the recordings live on. */
    fun freeBytes(): Long {
        return try {
            val directory = context.getExternalFilesDir(null) ?: return 0L
            val stat = StatFs(directory.absolutePath)
            stat.availableBytes
        } catch (e: Exception) {
            0L
        }
    }

    fun getRecordings(): List<RecordingInfo> {
        val recordingsDir = recordingsDir()
        if (!recordingsDir.exists()) return emptyList()
        return recordingsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".mp4") }
            ?.map { file ->
                RecordingInfo(
                    filename = file.name,
                    path = "/recordings/${file.name}",
                    size = file.length(),
                    startTime = file.lastModified(),
                    durationSec = durationSeconds(file)
                )
            }
            ?.sortedByDescending { it.startTime }
            ?: emptyList()
    }

    /** Reads the real duration of a recorded clip. */
    private fun durationSeconds(file: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val millis = raw?.toLongOrNull() ?: 0L
            (millis / 1000).toInt()
        } catch (e: Exception) {
            0
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    data class StorageStats(
        val totalFiles: Int,
        val totalSizeBytes: Long,
        val capBytes: Long,
        val usagePercent: Int
    )

    fun isRunning(): Boolean = running

    companion object {
        private const val TAG = "StorageManager"
    }
}
