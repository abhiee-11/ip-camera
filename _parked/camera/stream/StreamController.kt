package com.freecam.camera.stream

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class StreamController(private val context: Context, private val settings: Settings) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val job = Job()
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        AppLog.getInstance(context).log("StreamController", "Starting RTSP server (placeholder)")
        scope.launch(job) {
            // TODO M1: Initialize RootEncoder RTSP server
            // 1. Create RootEncoder Camera2 instance with settings
            // 2. Start RTSP server on port 8554 with path "live"
            // 3. Handle hardware encoder failures
            // 4. Exponential backoff retry
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        job.cancel()
        AppLog.getInstance(context).log("StreamController", "Stopping RTSP server")
        // TODO: Stop RootEncoder and RTSP server
    }

    fun isRunning(): Boolean = isRunning

    companion object {
        private var instance: StreamController? = null
        fun getInstance(context: Context, settings: Settings): StreamController {
            return instance ?: StreamController(context, settings).also { instance = it }
        }
        fun clearInstance() {
            instance = null
        }
    }
}