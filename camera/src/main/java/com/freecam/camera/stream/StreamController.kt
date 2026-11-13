package com.freecam.camera.stream

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtplibrary.rtsp.RtspCamera2
import com.pedro.rtplibrary.rtsp.RtspServer
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
    private var rtspCamera2: RtspCamera2? = null
    private var rtspServer: RtspServer? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        AppLog.getInstance(context).log("StreamController", "Starting RTSP server")
        
        scope.launch(job) {
            try {
                val width = settings.resolutionWidth
                val height = settings.resolutionHeight
                val fps = settings.frameRate
                val bitrateKbps = settings.bitrateKbps
                
                rtspCamera2 = RtspCamera2(context, object : RtspCamera2.Callback {
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
                    
                    override fun onBitrateUpdate(bitrate: Long) {
                        // Bitrate changed
                    }
                })
                
                rtspCamera2!!.setCameraFacing(CameraHelper.Facing.BACK)
                rtspCamera2!!.prepareVideo(
                    width, height, fps, bitrateKbps * 1000,
                    false, // rotation
                    Settings.h264Profile, Settings.h264Level
                )
                rtspCamera2!!.prepareAudio(Settings.audioSampleRate, Settings.audioStereo, Settings.audioBitrateKbps)
                
                rtspServer = RtspServer(context, Settings.rtspPort)
                rtspServer!!.start()
                
                rtspCamera2!!.setAuthorization(settings.rtspUser, settings.rtspPassword)
                rtspCamera2!!.startStream(rtspServer!!.getUrl(settings.rtspPath))
                
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
        
        rtspCamera2?.stopStream()
        rtspCamera2?.stopPreview()
        rtspServer?.stop()
        
        rtspCamera2 = null
        rtspServer = null
        
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