package com.freecam.camera.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.freecam.camera.App
import com.freecam.camera.R
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.camera.databinding.ActivityMainBinding
import com.freecam.camera.databinding.LayoutDimScreenBinding
import com.freecam.camera.service.CameraService
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dimBinding: LayoutDimScreenBinding
    private lateinit var settings: Settings
    private lateinit var appLog: AppLog
    private var dimScreenVisible = false
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRunnable = Runnable { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        dimBinding = DataBindingUtil.inflate(layoutInflater, R.layout.layout_dim_screen, null, false)

        settings = Settings.getInstance(this)
        appLog = (application as App).appLog ?: AppLog(this).also { (application as App).appLog = it }

        checkPermissionsAndSetup()
        setupClickListeners()
        updateStatus()
        startStatusUpdates()
    }

    private fun checkPermissionsAndSetup() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
        // Battery optimization exemption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                appLog.log("MainActivity", "Requesting battery optimization exemption")
                // We'll show a dialog/button for this in settings
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnStartStop.setOnClickListener { toggleStreaming() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnPairing.setOnClickListener {
            startActivity(Intent(this, PairingActivity::class.java))
        }
        binding.btnLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        binding.btnDimScreen.setOnClickListener { showDimScreen() }
        dimBinding.tvDimClock.setOnClickListener { hideDimScreen() }

        binding.switchPreview.setOnCheckedChangeListener { _, isChecked ->
            CameraService.setPreviewEnabled(isChecked)
        }
    }

    private fun toggleStreaming() {
        val intent = Intent(this, CameraService::class.java)
        if (CameraService.isRunning) {
            intent.action = CameraService.ACTION_STOP
            appLog.log("MainActivity", "Stopping camera service")
        } else {
            intent.action = CameraService.ACTION_START
            appLog.log("MainActivity", "Starting camera service")
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun updateStatus() {
        val isRunning = CameraService.isRunning
        val statusText = when {
            !isRunning -> getString(R.string.status_stopped)
            CameraService.hasError -> getString(R.string.status_error) + ": " + CameraService.errorMessage
            else -> getString(R.string.status_streaming)
        }
        val statusBg = when {
            !isRunning -> R.drawable.bg_status_stopped
            CameraService.hasError -> R.drawable.bg_status_error
            else -> R.drawable.bg_status_streaming
        }

        binding.tvStatus.text = statusText
        binding.tvStatus.setBackgroundResource(statusBg)
        binding.btnStartStop.text = if (isRunning) R.string.btn_stop else R.string.btn_start
        binding.btnStartStop.setBackgroundTintList(ContextCompat.getColorStateList(this,
            if (isRunning) R.color.red_200 else R.color.green_200))

        // Update info card
        if (isRunning) {
            binding.tvStreamAddress.text = buildStreamAddress()
            binding.tvUptime.text = "${getString(R.string.info_uptime)}: ${formatUptime(CameraService.uptimeMillis)}"
            binding.tvResolutionFpsBitrate.text = "${getString(R.string.info_resolution_fps_bitrate)}: ${settings.resolution} @ ${settings.frameRate}fps ${settings.bitrateKbps}kbps"
        } else {
            binding.tvStreamAddress.text = "rtsp://<ip>:8554/live"
            binding.tvUptime.text = "${getString(R.string.info_uptime)}: 00:00:00"
            binding.tvResolutionFpsBitrate.text = "${getString(R.string.info_resolution_fps_bitrate)}: --"
        }

        // Battery
        val batteryLevel = getBatteryLevel()
        val isCharging = isCharging()
        binding.tvBattery.text = "${getString(R.string.info_battery)}: $batteryLevel%${if (isCharging) " (charging)" else ""}"

        // Storage
        val storage = getStorageStats()
        binding.tvStorageFree.text = "${getString(R.string.info_storage_free)}: ${com.freecam.common.util.Bytes.format(storage.free)}"
        binding.tvStorageUsed.text = "${getString(R.string.info_storage_used)}: ${com.freecam.common.util.Bytes.format(storage.used)}"

        // Events today
        binding.tvEventsToday.text = "${getString(R.string.info_events_today)}: ${EventStore.getInstance(this).getTodayCount()}"

        // Motion
        binding.tvMotion.text = "${getString(R.string.info_motion)}: ${if (settings.motionEnabled) "ON (${settings.motionSensitivity})" else "OFF"}"
    }

    private fun buildStreamAddress(): String {
        val addresses = getLocalAddresses()
        val tailscaleAddr = addresses.firstOrNull { it.startsWith("100.") }
        val primaryAddr = tailscaleAddr ?: addresses.firstOrNull() ?: "unknown"
        return "rtsp://$primaryAddr:8554/live"
    }

    private fun getLocalAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            NetworkInterface.networkInterfaces.asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
                .map { Formatter.formatIpAddress(it.hashCode()) }
                .forEach { result.add(it) }
        } catch (e: Exception) {
            appLog.logError("MainActivity", "Failed to get network interfaces", e)
        }
        return result
    }

    private fun formatUptime(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun getBatteryLevel(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra("level", -1) ?: -1
        val scale = intent?.getIntExtra("scale", -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private fun isCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra("status", -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
               status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    private data class StorageStats(val free: Long, val used: Long)

    private fun getStorageStats(): StorageStats {
        val recordingsDir = getExternalFilesDir("recordings") ?: filesDir
        val free = recordingsDir.freeSpace
        val used = recordingsDir.listFiles()?.sumOf { it.length() } ?: 0L
        return StorageStats(free, used)
    }

    private fun showDimScreen() {
        dimScreenVisible = true
        setContentView(dimBinding.root)
        updateDimClock()
    }

    private fun hideDimScreen() {
        dimScreenVisible = false
        setContentView(binding.root)
    }

    private fun updateDimClock() {
        if (!dimScreenVisible) return
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
        dimBinding.tvDimClock.text = time
        statusHandler.postDelayed({ updateDimClock() }, 1000)
    }

    private fun startStatusUpdates() {
        statusHandler.postDelayed(statusRunnable, 1000)
    }

    override fun onDestroy() {
        statusHandler.removeCallbacks(statusRunnable)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val denied = grantResults.indices.filter { grantResults[it] != PackageManager.PERMISSION_GRANTED }
            if (denied.isNotEmpty()) {
                appLog.logError("MainActivity", "Permissions denied: ${permissions[denied.first()]}")
                Toast.makeText(this, getString(R.string.error_permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        fun updateUIIfVisible() {
            // Called from service to notify UI to refresh
        }
    }
}