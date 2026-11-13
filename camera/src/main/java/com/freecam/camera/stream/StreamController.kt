package com.freecam.camera.stream

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.pedro.library.view.OpenGlView
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.common.ConnectChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StreamController(
    private val context: Context,
    private val settings: Settings,
    private val onError: (String) -> Unit,
    private val onStarted: () -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val job = Job()
    private var rtspCamera: RtspCamera2? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        AppLog.getInstance(context).log("StreamController", "Starting RTSP stream")

        scope.launch(job) {
            try {
                val width = settings.getResolutionWidth()
                val height = settings.getResolutionHeight()
                val fps = settings.frameRate
                val bitrateKbps = settings.bitrateKbps

                // Create a dummy OpenGlView for RtspCamera2 (required by 2.4.x API)
                // We don't actually display it, just use it for the encoder
                val dummyView = OpenGlView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                }

                rtspCamera = RtspCamera2(dummyView, object : ConnectChecker {
                    override fun onConnectionSuccess() {
                        AppLog.getInstance(context).log("StreamController", "RTSP connection success")
                        onStarted()
                    }

                    override fun onConnectionFailed(reason: String) {
                        AppLog.getInstance(context).logError("StreamController", "RTSP connection failed: $reason")
                        onError("Connection failed: $reason")
                    }

                    override fun onDisconnect() {
                        AppLog.getInstance(context).log("StreamController", "RTSP disconnected")
                    }

                    override fun onAuthError() {
                        AppLog.getInstance(context).logError("StreamController", "RTSP auth error")
                        onError("Authentication error")
                    }

                    override fun onAuthSuccess() {
                        AppLog.getInstance(context).log("StreamController", "RTSP auth success")
                    }

                    override fun onConnectionStarted(url: String) {
                        AppLog.getInstance(context).log("StreamController", "RTSP connection started: $url")
                    }

                    override fun onNewBitrate(bitrate: Long) {
                        AppLog.getInstance(context).log("StreamController", "RTSP new bitrate: $bitrate")
                    }
                })

                // 2.4.x API: prepareVideo(width, height, fps, bitrate, rotation)
                rtspCamera!!.prepareVideo(
                    width, height, fps, bitrateKbps * 1000, 0
                )
                // 2.4.x API: prepareAudio(sampleRate, bitrate, isStereo)
                rtspCamera!!.prepareAudio(Settings.audioSampleRate, Settings.audioBitrateKbps, Settings.audioStereo)

                rtspCamera!!.getStreamClient().setAuthorization(Settings.rtspUser, Settings.rtspPassword)
                rtspCamera!!.startStream("rtsp://0.0.0.0:${Settings.rtspPort}/${Settings.rtspPath}")

            } catch (e: Exception) {
                AppLog.getInstance(context).logError("StreamController", "Failed to start stream", e)
                onError("Failed to start: ${e.message}")
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        job.cancel()

        rtspCamera?.stopStream()
        rtspCamera?.stopPreview()

        rtspCamera = null

        AppLog.getInstance(context).log("StreamController", "RTSP stream stopped")
    }

    fun isRunning(): Boolean = isRunning

    companion object {
        private var instance: StreamController? = null
        fun getInstance(
            context: Context,
            settings: Settings,
            onError: (String) -> Unit,
            onStarted: () -> Unit
        ): StreamController {
            return instance ?: StreamController(context, settings, onError, onStarted).also { instance = it }
        }
        fun clearInstance() {
            instance?.stop()
            instance = null
        }
    }
}