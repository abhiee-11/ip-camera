package com.freecam.camera.motion

import android.util.Log

class MotionDetector {

    companion object {
        // Sensitivity thresholds
        private const val LOW_THRESHOLD = 30
        private const val LOW_FRACTION = 0.02
        private const val MEDIUM_THRESHOLD = 25
        private const val MEDIUM_FRACTION = 0.015
        private const val HIGH_THRESHOLD = 20
        private const val HIGH_FRACTION = 0.01

        private const val SAMPLE_RATE = 3 // frames per second
        private const val REQUIRED_CONSECUTIVE = 2
        private const val WARMUP_FRAMES = 9 // 3 seconds at 3 fps
    }

    private var background: FloatArray? = null
    private var frameCount = 0
    private var consecutiveDetections = 0
    private var lastDetectionTime = 0L
    private var sensitivity = "medium"

    fun setSensitivity(level: String) {
        sensitivity = level
    }

    fun processFrame(lumaData: ByteArray, width: Int, height: Int): Boolean {
        frameCount++
        
        // Convert to float array and normalize
        val current = FloatArray(lumaData.size)
        var sum = 0f
        for (i in lumaData.indices) {
            val v = (lumaData[i].toInt() and 0xFF).toFloat()
            current[i] = v
            sum += v
        }
        val mean = sum / lumaData.size
        
        // Normalize to mean 128
        val normalized = FloatArray(lumaData.size)
        for (i in current.indices) {
            normalized[i] = current[i] * 128f / mean.max(1f)
        }

        // Warmup: build initial background
        if (frameCount <= WARMUP_FRAMES) {
            if (background == null) {
                background = FloatArray(normalized.size)
            }
            val alpha = 1f / frameCount
            for (i in normalized.indices) {
                background!![i] = background!![i] * (1 - alpha) + normalized[i] * alpha
            }
            return false
        }

        // Initialize background if not done
        if (background == null) {
            background = normalized.copyOf()
            return false
        }

        // Update running background (slow adaptation)
        val bgAlpha = 0.005f
        for (i in normalized.indices) {
            background!![i] = background!![i] * (1 - bgAlpha) + normalized[i] * bgAlpha
        }

        // Compute difference
        val (threshold, fraction) = getThresholds()
        var changedPixels = 0
        for (i in normalized.indices) {
            val diff = kotlin.math.abs(normalized[i] - background!![i])
            if (diff > threshold) changedPixels++
        }
        val changedFraction = changedPixels.toFloat() / normalized.size

        val detected = changedFraction > fraction
        if (detected) {
            consecutiveDetections++
        } else {
            consecutiveDetections = 0
        }

        return consecutiveDetections >= REQUIRED_CONSECUTIVE
    }

    private fun getThresholds(): Pair<Float, Float> = when (sensitivity) {
        "low" -> Pair(LOW_THRESHOLD.toFloat(), LOW_FRACTION)
        "high" -> Pair(HIGH_THRESHOLD.toFloat(), HIGH_FRACTION)
        else -> Pair(MEDIUM_THRESHOLD.toFloat(), MEDIUM_FRACTION)
    }

    fun reset() {
        background = null
        frameCount = 0
        consecutiveDetections = 0
    }
}