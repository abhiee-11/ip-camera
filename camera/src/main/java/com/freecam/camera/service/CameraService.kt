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
import com.freecam.camera.http.ApiServer
import com.freecam.camera.http.MotionEvent
import com.freecam.camera.http.RecordingInfo
import com.freecam.camera.stream.StreamController
import com.freecam.camera.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
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
    private var streamController: StreamController? = null
    private var apiServer: ApiServer? = null

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        // Start HTTP API server
        apiServer = ApiServer(
            context = this,
            settings = settings,
            appLog = appLog,
            onStatus = { buildStatusJson() },
            onRecordings = { getRecordings() },
            onEvents = { from, to -> getEvents(from, to) }
        )
        try {
            apiServer?.start()
            appLog.log("CameraService", "API server started on port ${Settings.apiPort}")
        } catch (e: Exception) {
            appLog.logError("CameraService", "Failed to start API server", e)
        }

        streamController = StreamController.getInstance(this, settings,
            onError = { msg ->
                hasError = true
                errorMessage = msg
                appLog.logError("CameraService", "Stream error: $msg")
                updateNotification()
            },
            onStarted = {
                appLog.log("CameraService", "Stream started successfully")
                hasError = false
                errorMessage = ""
                updateNotification()
            }
        )
        streamController?.start()

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
        streamController?.stop()
        StreamController.clearInstance()
        streamController = null

        apiServer?.stop()
        apiServer = null

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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
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
        streamControllerJob.cancel()
        uptimeJob.cancel()
        watchdogJob.cancel()
        apiServer?.stop()
        releaseLocks()
        super.onDestroy()
    }

    private fun buildStatusJson(): String {
        val ip = getLocalIpAddress()
        val rtspUrl = "rtsp://$ip:${Settings.rtspPort}/${Settings.rtspPath}"
        val apiUrl = "http://$ip:${Settings.apiPort}"
        return """{"running":$isRunning,"error":${if (hasError) "\"$errorMessage\"" else "null"},"uptime_ms":$uptimeMillis,"rtsp_url":"$rtspUrl","api_url":"$apiUrl","camera_name":"${settings.cameraName}","resolution":"${settings.resolution}","fps":${settings.frameRate}}"""
    }

    private fun getRecordings(): List<RecordingInfo> {
        val recordingsDir = File(getExternalFilesDir(null), "recordings")
        if (!recordingsDir.exists()) return emptyList()
        return recordingsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp4") }?.map { file ->
            RecordingInfo(
                filename = file.name,
                path = "/recordings/${file.name}",
                size = file.length(),
                createdAt = file.lastModified(),
                durationSec = 0 // TODO: extract duration from MP4
            )
        }?.sortedByDescending { it.createdAt } ?: emptyList()
    }

    private fun getEvents(from: Long, to: Long): List<MotionEvent> {
        // TODO: Read from events JSON file
        return emptyList()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            appLog.logError("CameraService", "Error getting IP", e)
        }
        return "0.0.0.0"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Service continues running
    }
}