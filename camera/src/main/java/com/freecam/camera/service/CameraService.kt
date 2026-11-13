package com.freecam.camera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.freecam.camera.R
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.camera.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class CameraService : Service() {

    companion object {
        const val ACTION_START = "com.freecam.camera.ACTION_START"
        const val ACTION_STOP = "com.freecam.camera.ACTION_STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "freecam_stream"

        var isRunning = false
        var hasError = false
        var errorMessage = ""
        var uptimeMillis: Long = 0
        private var previewEnabled = false
        private var startTime: Long = 0

        fun setPreviewEnabled(enabled: Boolean) {
            previewEnabled = enabled
        }

        fun isPreviewEnabled(): Boolean = previewEnabled
    }

    private lateinit var appLog: AppLog
    private lateinit var settings: Settings
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val streamControllerJob = Job()
    private val uptimeJob = Job()
    private val watchdogJob = Job()

    override fun onCreate() {
        super.onCreate()
        appLog = (application as com.freecam.camera.App).appLog ?: com.freecam.camera.data.AppLog(this).also {
            (application as com.freecam.camera.App).appLog = it
        }
        settings = Settings.getInstance(this)
        createNotificationChannel()
        acquireLocks()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FreeCam Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Camera streaming status"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FreeCam::WakeLock").apply {
            acquire()
        }
        val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FreeCam::WifiLock").apply {
            acquire()
        }
    }

    private fun releaseLocks() {
        wakeLock?.release()
        wakeLock = null
        wifiLock?.release()
        wifiLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> startStreaming()
            ACTION_STOP -> stopStreaming()
        }
        return START_STICKY
    }

    private fun startStreaming() {
        if (isRunning) return
        appLog.log("CameraService", "Starting streaming")
        isRunning = true
        hasError = false
        errorMessage = ""
        startTime = System.currentTimeMillis()

        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        scope.launch(streamControllerJob) {
            // TODO: Initialize StreamController with RootEncoder RTSP server
            // This will be implemented in M1
            appLog.log("CameraService", "StreamController would start here (M1)")
        }
        scope.launch(uptimeJob) {
            while (isRunning) {
                uptimeMillis = System.currentTimeMillis() - startTime
                kotlinx.coroutines.delay(1000)
            }
        }
        // Watchdog - check every 60 seconds
        scope.launch(watchdogJob) {
            while (isRunning) {
                kotlinx.coroutines.delay(60_000)
                if (isRunning) {
                    // TODO: Check encoder health
                    appLog.log("CameraService", "Watchdog check")
                }
            }
        }

        updateNotification()
    }

    private fun stopStreaming() {
        if (!isRunning) return
        appLog.log("CameraService", "Stopping streaming")
        isRunning = false
        streamControllerJob.cancel()
        uptimeJob.cancel()
        watchdogJob.cancel()
        // TODO: Stop StreamController
        stopForeground(true)
        stopSelf()
        releaseLocks()
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, CameraService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val statusText = if (hasError) "Error: $errorMessage" else if (isRunning) "Streaming" else "Stopped"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FreeCam Camera")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        scope.cancel()
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Service continues running
    }
}