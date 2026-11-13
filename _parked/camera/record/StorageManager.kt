package com.freecam.camera.record

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.common.util.Bytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class StorageManager(private val context: Context, private val settings: Settings) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val job = Job()
    private val recordingsDir: java.io.File
    private val minFreeSpace = Bytes.parseGb(1) // 1.5 GB minimum

    init {
        recordingsDir = context.getExternalFilesDir("recordings") ?: context.filesDir
        recordingsDir.mkdirs()
    }

    fun startPeriodicCleanup() {
        scope.launch(job) {
            while (true) {
                kotlinx.coroutines.delay(10 * 60 * 1000) // 10 minutes
                cleanup()
            }
        }
    }

    fun stop() {
        job.cancel()
    }

    fun cleanup() {
        AppLog.getInstance(context).log("StorageManager", "Running cleanup")
        val now = System.currentTimeMillis()
        val retentionMs = settings.retentionMillis()
        val capBytes = settings.storageCapBytes()

        // Get all recording files
        val files = recordingsDir.listFiles { _, name -> name.endsWith(".mp4") }
            ?: return

        // Sort by modification time (oldest first)
        val sortedFiles = files.sortedBy { it.lastModified() }

        // 1. Delete files older than retention
        var deletedCount = 0
        var freedBytes = 0L
        for (file in sortedFiles) {
            val age = now - file.lastModified()
            if (age > retentionMs) {
                freedBytes += file.length()
                if (file.delete()) {
                    deletedCount++
                    // Also delete thumbnail
                    deleteThumbnail(file)
                }
            }
        }

        // 2. Delete oldest files until under cap
        var currentSize = sortedFiles.sumOf { it.length() }
        val remainingFiles = recordingsDir.listFiles { _, name -> name.endsWith(".mp4") }?.sortedBy { it.lastModified() } ?: emptyArray()
        for (file in remainingFiles) {
            if (currentSize <= capBytes) break
            freedBytes += file.length()
            currentSize -= file.length()
            if (file.delete()) {
                deletedCount++
                deleteThumbnail(file)
            }
        }

        if (deletedCount > 0) {
            AppLog.getInstance(context).log("StorageManager", "Deleted $deletedCount files, freed ${Bytes.format(freedBytes)}")
        }

        // Check free space
        val freeSpace = recordingsDir.freeSpace
        if (freeSpace < minFreeSpace) {
            AppLog.getInstance(context).logError("StorageManager", "Low storage: ${Bytes.format(freeSpace)} free")
        }
    }

    fun cleanupAfterFileClosed(file: java.io.File) {
        // Run cleanup after each segment closes
        scope.launch { cleanup() }
    }

    fun isStorageLow(): Boolean = recordingsDir.freeSpace < minFreeSpace

    fun getUsedSpace(): Long = recordingsDir.listFiles()?.sumOf { it.length() } ?: 0L
    fun getFreeSpace(): Long = recordingsDir.freeSpace

    private fun deleteThumbnail(videoFile: java.io.File) {
        val thumbFile = java.io.File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.jpg")
        if (thumbFile.exists()) thumbFile.delete()
    }
}