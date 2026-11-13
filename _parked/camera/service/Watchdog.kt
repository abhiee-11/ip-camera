package com.freecam.camera.service

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class Watchdog(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val job = Job()
    private val checkIntervalMs = 60_000L
    private var lastEncoderCheck = System.currentTimeMillis()
    private var restartCount = 0

    fun start() {
        scope.launch(job) {
            while (true) {
                kotlinx.coroutines.delay(checkIntervalMs)
                checkHealth()
            }
        }
    }

    fun stop() {
        job.cancel()
    }

    private fun checkHealth() {
        // TODO: Check if encoder is running and RTSP server socket is listening
        // This will be implemented when StreamController is created
        val now = System.currentTimeMillis()
        val elapsed = (now - lastEncoderCheck) / 1000
        Log.d("Watchdog", "Health check - encoder running for ${elapsed}s")
    }

    fun onEncoderRestarted() {
        lastEncoderCheck = System.currentTimeMillis()
        restartCount++
    }

    fun getRestartCount(): Int = restartCount
}