package com.freecam.camera.service

import android.app.AlarmManager
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
import com.freecam.camera.BuildConfig
import com.freecam.camera.R
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.CameraStateStore
import com.freecam.camera.data.CameraUiState
import com.freecam.camera.data.EventStore
import com.freecam.camera.data.Settings
import com.freecam.camera.http.ApiServer
import com.freecam.camera.notifications.Notifier
import com.freecam.camera.recording.RecordingManager
import com.freecam.camera.storage.StorageManager
import com.freecam.camera.stream.StreamController
import com.freecam.camera.ui.MainActivity
import com.freecam.camera.util.BatteryStatus
import com.freecam.camera.util.NetworkAddresses
import com.freecam.common.model.ApiError
import com.freecam.common.model.CameraStatus
import com.freecam.common.model.MotionEvent
import com.freecam.common.model.RecordingInfo
import com.freecam.common.util.StreamUrls
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Runs the camera: RTSP server, HTTP API, recording and the published state.
 */
class CameraService : Service() {

    companion object {
        const val ACTION_START = "com.freecam.camera.ACTION_START"
        const val ACTION_STOP = "com.freecam.camera.ACTION_STOP"
        const val ACTION_RESTART_CAMERA = "com.freecam.camera.ACTION_RESTART_CAMERA"
        const val ACTION_RESET_TOKEN = "com.freecam.camera.ACTION_RESET_TOKEN"
        const val ACTION_SET_PREVIEW = "com.freecam.camera.ACTION_SET_PREVIEW"
        const val EXTRA_PREVIEW_ENABLED = "com.freecam.camera.EXTRA_PREVIEW_ENABLED"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "freecam_stream"
        private const val TAG = "CameraService"

        /** The preview surface is owned by the activity and attached here. */
        @Volatile
        var previewView: OpenGlView? = null

        @Volatile
        var previewEnabled: Boolean = false

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var hasError: Boolean = false
            private set

        @Volatile
        var errorMessage: String = ""
            private set

        @Volatile
        var uptimeMillis: Long = 0L
            private set

        /** The state the dashboard and the API show. */
        fun snapshot(): CameraUiState = CameraStateStore.snapshot()

        fun attachPreviewView(view: OpenGlView?) {
            previewView = view
        }
    }

    private lateinit var appLog: AppLog
    private lateinit var settings: Settings
    private lateinit var eventStore: EventStore
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var uptimeJob: Job? = null
    private var streamController: StreamController? = null
    private var apiServer: ApiServer? = null
    private var recordingManager: RecordingManager? = null
    private var storageManager: StorageManager? = null
    private var notifier: Notifier? = null
    private var startTime: Long = 0L
    private var errorCode: String? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as com.freecam.camera.App
        appLog = app.appLog ?: AppLog(this).also { app.appLog = it }
        settings = Settings.getInstance(this)
        eventStore = EventStore(this)
        createNotificationChannel()
        acquireLocks()

        storageManager = StorageManager(this, settings, appLog)
        notifier = Notifier(this, settings, appLog)
        recordingManager = RecordingManager(
            context = this,
            settings = settings,
            appLog = appLog,
            controller = { streamController },
            onRecordingStarted = { file -> onRecordingStarted(file) },
            onRecordingStopped = { file, timestamp, durationSec -> onRecordingStopped(file, timestamp, durationSec) },
            onError = { message -> onError("record_failed", message) }
        )
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
            setReferenceCounted(false)
            acquire()
        }
        val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FreeCam::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        wakeLock = null
        try {
            wifiLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        wifiLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startStreaming()
            ACTION_STOP -> stopStreaming()
            ACTION_RESTART_CAMERA -> {
                stopStreaming()
                startStreaming()
            }
            ACTION_RESET_TOKEN -> resetToken()
            ACTION_SET_PREVIEW -> {
                previewEnabled = intent?.getBooleanExtra(EXTRA_PREVIEW_ENABLED, false) ?: false
                settings.previewEnabled = previewEnabled
                if (isRunning) {
                    restartStream()
                }
            }
        }
        return START_STICKY
    }

    private fun startStreaming() {
        if (isRunning) return
        appLog.log(TAG, "Starting streaming")
        startTime = System.currentTimeMillis()
        errorCode = null
        hasError = false
        errorMessage = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        apiServer = ApiServer(
            context = this,
            settings = settings,
            appLog = appLog,
            onStatus = { buildCameraStatus() },
            onRecordings = { storageManager?.getRecordings() ?: emptyList() },
            onEvents = { from, to -> eventsBetween(from, to) },
            onSnapshot = { null }
        )
        try {
            apiServer?.start()
            appLog.log(TAG, "API server started on port ${Settings.apiPort}")
        } catch (e: Exception) {
            appLog.logError(TAG, "Failed to start API server", e)
            onError("api_failed", "The API server could not start")
        }

        storageManager?.start()

        streamController = StreamController(
            context = this,
            settings = settings,
            appLog = appLog,
            onError = { code, message -> onError(code, message) },
            onStarted = { onStreamStarted() }
        )
        val started = streamController?.start(if (previewEnabled) previewView else null) == true
        if (!started) {
            appLog.logError(TAG, "Stream did not start")
            return
        }

        isRunning = true
        recordingManager?.start()
        uptimeJob?.cancel()
        uptimeJob = scope.launch {
            var tick = 0
            while (isRunning) {
                uptimeMillis = System.currentTimeMillis() - startTime
                tick++
                if (tick % 5 == 0) publishState()
                delay(1000)
            }
        }

        if (settings.nightlyRestart) scheduleNightlyRestart()
        publishState()
    }

    private fun stopStreaming() {
        if (!isRunning && streamController == null) return
        appLog.log(TAG, "Stopping streaming")
        isRunning = false
        uptimeJob?.cancel()

        recordingManager?.stop()
        streamController?.stop()
        streamController = null
        storageManager?.stop()
        apiServer?.stop()
        apiServer = null

        publishState()
        stopForeground(true)
        stopSelf()
        releaseLocks()
    }

    /** Restarts only the camera and the stream, keeping the API alive. */
    private fun restartStream() {
        appLog.log(TAG, "Restarting the camera stream")
        recordingManager?.stop()
        streamController?.stop()
        streamController = StreamController(
            context = this,
            settings = settings,
            appLog = appLog,
            onError = { code, message -> onError(code, message) },
            onStarted = { onStreamStarted() }
        )
        val started = streamController?.start(if (previewEnabled) previewView else null) == true
        if (!started) {
            onError("start_failed", "The camera could not be restarted")
        } else {
            hasError = false
            errorCode = null
            errorMessage = ""
            recordingManager?.start()
        }
        publishState()
    }

    private fun onStreamStarted() {
        appLog.log(TAG, "Stream started")
        hasError = false
        errorCode = null
        errorMessage = ""
        eventStore.add("stream_start", "low")
        notifier?.notify("FreeCam", "${settings.cameraName} is streaming")
        recordingManager?.start()
        publishState()
        updateNotification()
    }

    private fun onRecordingStarted(file: java.io.File) {
        appLog.log(TAG, "Recording started: ${file.name}")
        eventStore.add("recording_start", "low", "/recordings/${file.name}")
        publishState()
        updateNotification()
    }

    private fun onRecordingStopped(file: java.io.File, timestamp: Long, durationSec: Int) {
        appLog.log(TAG, "Recording stopped: ${file.name} (${durationSec}s)")
        eventStore.add("recording_stop", "low", "/recordings/${file.name}")
        publishState()
        updateNotification()
    }

    private fun onError(code: String, message: String) {
        errorCode = code
        errorMessage = message
        hasError = true
        appLog.logError(TAG, "$code: $message")
        eventStore.add("error", "high")
        notifier?.notify("FreeCam", message, "high")
        publishState()
        updateNotification()
    }

    private fun resetToken() {
        settings.resetToken()
        appLog.log(TAG, "Pairing token reset")
        eventStore.add("token_reset", "medium")
        publishState()
    }

    private fun eventsBetween(from: Long, to: Long): List<MotionEvent> {
        return eventStore.readAll().filter { it.ts in from..to }
    }

    private fun buildCameraStatus(): CameraStatus {
        val state = CameraStateStore.snapshot()
        return CameraStatus(
            running = state.running,
            recording = state.recording,
            uptimeMs = state.uptimeMs,
            cameraName = state.cameraName,
            resolution = state.resolution,
            fps = state.fps,
            bitrateKbps = state.bitrateKbps,
            rtspUrl = state.rtspUrl,
            apiUrl = state.apiUrl,
            version = BuildConfig.VERSION_NAME,
            host = state.host,
            tailnet = com.freecam.common.model.TailnetState(state.tailnetConnected, state.tailnetIp),
            battery = com.freecam.common.model.BatteryState(state.batteryPercent, state.batteryCharging),
            storage = com.freecam.common.model.StorageState(
                state.storageUsedBytes,
                state.storageFreeBytes,
                state.storageCapBytes
            ),
            motion = com.freecam.common.model.MotionState(
                state.motionEnabled,
                state.motionSensitivity,
                state.eventsToday,
                state.motionAvailable
            ),
            recordingMode = state.recordingMode,
            error = errorCode?.let { ApiError(it, errorMessage) }
        )
    }

    /** Collects everything the UI shows into one snapshot. */
    private fun publishState() {
        val choice = NetworkAddresses.hostChoice()
        val battery = BatteryStatus.read(this)
        val stats = storageManager?.getStorageStats()
        val capBytes = settings.storageCapBytes()
        val usedBytes = stats?.totalSizeBytes ?: 0L
        val freeBytes = storageManager?.freeBytes() ?: 0L

        val host = choice.host
        // The status never carries the RTSP password: the viewer already has the
        // token from pairing and builds the credentialed URL itself.
        val rtspUrl = if (host != null) "rtsp://$host:${Settings.rtspPort}/${Settings.rtspPath}" else ""
        val apiUrl = if (host != null) "http://$host:${Settings.apiPort}" else ""

        CameraStateStore.publish(
            CameraUiState(
                running = isRunning,
                recording = recordingManager?.isRecording() == true,
                uptimeMs = uptimeMillis,
                cameraName = settings.cameraName,
                resolution = settings.resolution,
                fps = settings.frameRate,
                bitrateKbps = settings.bitrateKbps,
                rtspUrl = rtspUrl,
                apiUrl = apiUrl,
                host = host,
                tailnetConnected = choice.tailnetIp != null,
                tailnetIp = choice.tailnetIp,
                batteryPercent = battery.percent,
                batteryCharging = battery.charging,
                storageUsedBytes = usedBytes,
                storageFreeBytes = freeBytes,
                storageCapBytes = capBytes,
                motionEnabled = settings.motionEnabled,
                motionSensitivity = settings.motionSensitivity,
                // The camera library gives no access to camera frames, so motion
                // cannot be detected yet. The UI says so instead of guessing.
                motionAvailable = false,
                eventsToday = eventStore.todayCount(),
                recordingMode = settings.recordingMode,
                errorCode = errorCode,
                errorMessage = if (errorCode == null) null else errorMessage
            )
        )
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, CameraService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val statusText = when {
            hasError -> "Error: $errorMessage"
            isRunning -> "Streaming"
            else -> "Stopped"
        }

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
        isRunning = false
        uptimeJob?.cancel()
        apiServer?.stop()
        recordingManager?.stop()
        streamController?.stop()
        storageManager?.stop()
        releaseLocks()
        super.onDestroy()
    }

    private fun scheduleNightlyRestart() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val parts = settings.restartTime.split(":")
            val hour = parts[0].toIntOrNull() ?: 4
            val minute = parts[1].toIntOrNull() ?: 0

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, NightlyRestartReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
            appLog.log(TAG, "Nightly restart scheduled for ${calendar.time}")
        } catch (e: Exception) {
            appLog.logError(TAG, "Failed to schedule nightly restart", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // The camera keeps streaming when the activity is closed.
    }
}
