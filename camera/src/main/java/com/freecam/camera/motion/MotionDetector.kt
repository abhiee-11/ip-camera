package com.freecam.camera.motion

import android.content.Context
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class MotionDetector(
    private val context: Context,
    private val settings: Settings,
    private val onMotionDetected: (Long, String) -> Unit
) {

    private val TAG = "MotionDetector"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val frameChannel = Channel<ByteArray>(10)
    private val job = Job()
    private var isRunning = false
    private var lastMotionTime = 0L
    private var referenceFrame: ByteArray? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        AppLog.getInstance(context).log(TAG, "Starting motion detection")
        
        scope.launch(job) {
            processFrames()
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        job.cancel()
        frameChannel.close()
        referenceFrame = null
        AppLog.getInstance(context).log(TAG, "Motion detection stopped")
    }

    fun onFrame(yuvData: ByteArray) {
        if (isRunning) {
            frameChannel.trySend(yuvData)
        }
    }

    private suspend fun processFrames() {
        while (isRunning) {
            val result = frameChannel.receiveCatching()
            if (result.isClosed) break
            val frame = result.getOrNull() ?: continue
            if (!isRunning) break
            
            val now = System.currentTimeMillis()
            val cooldown = settings.motionCooldownSec * 1000L
            if (now - lastMotionTime < cooldown) continue

            val sensitivity = when (settings.motionSensitivity) {
                "high" -> 0.05f
                "low" -> 0.01f
                else -> 0.02f // medium
            }

            if (detectMotion(frame, sensitivity)) {
                lastMotionTime = now
                val severity = calculateSeverity(frame, sensitivity)
                onMotionDetected(now, severity)
                AppLog.getInstance(context).log(TAG, "Motion detected: $severity")
            }
        }
    }

    private fun detectMotion(currentFrame: ByteArray, threshold: Float): Boolean {
        if (referenceFrame == null) {
            referenceFrame = currentFrame.copyOf()
            return false
        }

        val ref = referenceFrame!!
        val width = settings.getResolutionWidth()
        val height = settings.getResolutionHeight()
        val ySize = width * height

        var diffPixels = 0
        var totalPixels = 0

        // Sample every 4th pixel for performance
        for (i in 0 until ySize step 4) {
            val diff = (currentFrame[i].toInt() and 0xFF) - (ref[i].toInt() and 0xFF)
            if (Math.abs(diff) > 30) diffPixels++
            totalPixels++
        }

        val diffRatio = diffPixels.toFloat() / totalPixels
        
        // Update reference frame with exponential moving average
        for (i in 0 until ySize) {
            val current = currentFrame[i].toInt() and 0xFF
            val refVal = ref[i].toInt() and 0xFF
            ref[i] = ((0.95 * refVal + 0.05 * current).toInt()).toByte()
        }

        return diffRatio > threshold
    }

    private fun calculateSeverity(frame: ByteArray, threshold: Float): String {
        val width = settings.getResolutionWidth()
        val height = settings.getResolutionHeight()
        val ySize = width * height

        var diffPixels = 0
        var totalPixels = 0

        for (i in 0 until ySize step 4) {
            val diff = (frame[i].toInt() and 0xFF) - (referenceFrame!![i].toInt() and 0xFF)
            if (Math.abs(diff) > 30) diffPixels++
            totalPixels++
        }

        val diffRatio = diffPixels.toFloat() / totalPixels
        
        return when {
            diffRatio > threshold * 5 -> "high"
            diffRatio > threshold * 2 -> "medium"
            else -> "low"
        }
    }

    fun isRunning(): Boolean = isRunning
}