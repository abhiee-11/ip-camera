package com.freecam.camera.stream

import android.content.Context
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
import java.io.File
import java.io.IOException

/**
 * Owns the RTSP server and the camera encoder.
 *
 * Two modes:
 * - headless: no view at all. RootEncoder renders straight into the encoder's
 *   input surface, so the stream survives the activity being closed.
 * - preview: a real attached [OpenGlView] so the camera can be aimed. The
 *   stream restarts when switching between the two.
 */
class StreamController(
    private val context: Context,
    private val settings: Settings,
    private val appLog: AppLog,
    private val onError: (code: String, message: String) -> Unit,
    private val onStarted: () -> Unit
) {

    private var rtspServerCamera: RtspServerCamera2? = null
    private var running = false
    private var recording = false

    /** Starts the stream. [previewView] enables the local preview surface. */
    fun start(previewView: OpenGlView? = null): Boolean {
        if (running) return true
        try {
            val width = settings.getResolutionWidth()
            val height = settings.getResolutionHeight()
            val fps = settings.frameRate
            val bitrate = settings.bitrateKbps * 1000
            val rotation = cameraRotation()

            val camera = if (previewView != null) {
                appLog.log(TAG, "Starting RTSP server with preview surface")
                RtspServerCamera2(previewView, connectChecker(), Settings.rtspPort)
            } else {
                appLog.log(TAG, "Starting RTSP server headless")
                RtspServerCamera2(context, false, connectChecker(), Settings.rtspPort)
            }

            if (!camera.prepareVideo(width, height, fps, bitrate, rotation)) {
                onError("encoder_rejected", "The video encoder rejected ${width}x$height at ${fps}fps")
                return false
            }

            if (settings.audioEnabled) {
                if (!camera.prepareAudio(
                        Settings.audioBitrateKbps * 1000,
                        Settings.audioSampleRate,
                        Settings.audioStereo
                    )
                ) {
                    onError("encoder_rejected", "The audio encoder rejected the requested format")
                    return false
                }
            }

            // The RTSP server authenticates clients with the per install token.
            camera.getStreamClient().setAuthorization(settings.rtspUser, settings.rtspPassword)

            rtspServerCamera = camera
            running = true
            camera.startStream()
            return true
        } catch (e: Exception) {
            appLog.logError(TAG, "Failed to start RTSP server", e)
            onError("start_failed", e.message ?: "Could not start the camera")
            stop()
            return false
        }
    }

    fun stop() {
        running = false
        recording = false
        try {
            rtspServerCamera?.stopStream()
            rtspServerCamera?.stopPreview()
        } catch (e: Exception) {
            appLog.logError(TAG, "Error stopping RTSP server", e)
        }
        rtspServerCamera = null
        appLog.log(TAG, "RTSP server stopped")
    }

    /** Starts writing the encoder output to [file] as MP4. */
    fun startRecording(file: File): Boolean {
        val camera = rtspServerCamera ?: return false
        return try {
            camera.startRecord(file.absolutePath)
            recording = true
            appLog.log(TAG, "Recording started: ${file.name}")
            true
        } catch (e: IOException) {
            appLog.logError(TAG, "Failed to start recording", e)
            onError("record_failed", e.message ?: "Could not start recording")
            false
        } catch (e: Exception) {
            appLog.logError(TAG, "Failed to start recording", e)
            onError("record_failed", e.message ?: "Could not start recording")
            false
        }
    }

    fun stopRecording() {
        val camera = rtspServerCamera ?: return
        try {
            camera.stopRecord()
        } catch (e: Exception) {
            appLog.logError(TAG, "Error stopping recording", e)
        }
        recording = false
        appLog.log(TAG, "Recording stopped")
    }

    fun isRecording(): Boolean = recording

    fun isRunning(): Boolean = running

    private fun cameraRotation(): Int {
        return try {
            CameraHelper.getCameraOrientation(context)
        } catch (e: Exception) {
            0
        }
    }

    private fun connectChecker() = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            appLog.log(TAG, "RTSP server starting on port ${Settings.rtspPort}")
        }

        override fun onConnectionSuccess() {
            appLog.log(TAG, "RTSP server ready")
            onStarted()
        }

        override fun onConnectionFailed(reason: String) {
            appLog.logError(TAG, "RTSP server failed: $reason")
            onError("server_failed", reason)
        }

        override fun onDisconnect() {
            appLog.log(TAG, "RTSP client disconnected")
        }

        override fun onAuthError() {
            appLog.logError(TAG, "RTSP auth error")
            onError("auth_failed", "A viewer failed to authenticate")
        }

        override fun onAuthSuccess() {
            appLog.log(TAG, "RTSP auth success")
        }

        override fun onNewBitrate(bitrate: Long) {
            appLog.log(TAG, "RTSP new bitrate: $bitrate")
        }
    }

    companion object {
        private const val TAG = "StreamController"
    }
}
