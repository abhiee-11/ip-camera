package com.freecam.camera.record

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.freecam.camera.data.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class ThumbnailMaker(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun generateThumbnail(videoFile: java.io.File) {
        scope.launch {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, android.net.Uri.fromFile(videoFile))
                // Get frame at 1 second (1,000,000 microseconds)
                val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()

                if (bitmap != null) {
                    // Scale to ~320px wide
                    val targetWidth = 320
                    val targetHeight = (bitmap.height * targetWidth / bitmap.width)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                    
                    val thumbFile = java.io.File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.jpg")
                    java.io.FileOutputStream(thumbFile).use { out ->
                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    AppLog.getInstance(context).log("ThumbnailMaker", "Generated thumbnail: ${thumbFile.name}")
                }
            } catch (e: Exception) {
                AppLog.getInstance(context).logError("ThumbnailMaker", "Failed to generate thumbnail for ${videoFile.name}", e)
            }
        }
    }
}