package com.freecam.camera.stream

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
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
    private var rtspServerCamera: RtspServerCamera2? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        AppLog.getInstance(context).log("StreamController", "Starting RTSP server")

        scope.launch(job) {
            try {
                val width = settings.getResolutionWidth()
                val height = settings.getResolutionHeight()
                val fps = settings.frameRate
                val bitrateKbps = settings.bitrateKbps

                // Create a dummy OpenGlView for RtspServerCamera2 (required by API)
                // We don't actually display it, just use it for the encoder
                val dummyView = OpenGlView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                }

                rtspServerCamera = RtspServerCamera2(dummyView, object : ConnectChecker {
                    override fun onConnectionStarted(url: String) {
                        AppLog.getInstance(context).log("StreamController", "RTSP server started: $url")
                    }

                    override fun onConnectionSuccess() {
                        AppLog.getInstance(context).log("StreamController", "RTSP server connection success")
                        onStarted()
                    }

                    override fun onConnectionFailed(reason: String) {
                        AppLog.getInstance(context).logError("StreamController", "RTSP server failed: $reason")
                        onError("Server failed: $reason")
                    }

                    override fun onDisconnect() {
                        AppLog.getInstance(context).log("StreamController", "RTSP client disconnected")
                    }

                    override fun onAuthError() {
                        AppLog.getInstance(context).logError("StreamController", "RTSP auth error")
                        onError("Authentication error")
                    }

                    override fun onAuthSuccess() {
                        AppLog.getInstance(context).log("StreamController", "RTSP auth success")
                    }

                    override fun onNewBitrate(bitrate: Long) {
                        AppLog.getInstance(context).log("StreamController", "RTSP new bitrate: $bitrate")
                    }
                }, Settings.rtspPort)

                // RtspServerCamera2 API: prepareVideo(width, height, fps, bitrate, rotation)
                rtspServerCamera!!.prepareVideo(
                    width, height, fps, bitrateKbps * 1000, 0
                )
                rtspServerCamera!!.prepareAudio(Settings.audioSampleRate, Settings.audioBitrateKbps, Settings.audioStereo)

                rtspServerCamera!!.getStreamClient().setAuthorization(Settings.rtspUser, Settings.rtspPassword)
                rtspServerCamera!!.startStream()

            } catch (e: Exception) {
                AppLog.getInstance(context).logError("StreamController", "Failed to start RTSP server", e)
                onError("Failed to start: ${e.message}")
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        job.cancel()

        rtspServerCamera?.stopStream()
        rtspServerCamera?.stopPreview()

        rtspServerCamera = null

        AppLog.getInstance(context).log("StreamController", "RTSP server stopped")
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