package com.freecam.camera.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.freecam.camera.R
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.camera.databinding.ActivityMainBinding
import com.freecam.camera.service.CameraService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updateUI() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setupButtons()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(updateRunnable, 1000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun setupButtons() {
        binding.btnStartStop.setOnClickListener {
            val intent = Intent(this, CameraService::class.java).apply {
                action = if (CameraService.isRunning) CameraService.ACTION_STOP else CameraService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnPairing.setOnClickListener {
            startActivity(Intent(this, PairingActivity::class.java))
        }

        binding.btnLogs.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }

        binding.btnDimScreen.setOnClickListener {
            startActivity(Intent(this, DimScreenActivity::class.java))
        }

        binding.switchPreview.setOnCheckedChangeListener { _, isChecked ->
            CameraService.setPreviewEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Preview enabled" else "Preview disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        val running = CameraService.isRunning
        val hasError = CameraService.hasError
        val errorMsg = CameraService.errorMessage
        val uptime = CameraService.uptimeMillis

        binding.btnStartStop.text = if (running) getString(R.string.btn_stop) else getString(R.string.btn_start)
        binding.btnStartStop.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (running) resources.getColor(R.color.red_200, theme) else resources.getColor(R.color.green_200, theme)
        )

        val statusText = when {
            hasError -> getString(R.string.status_error) + ": $errorMsg"
            running -> getString(R.string.status_streaming)
            else -> getString(R.string.status_stopped)
        }
        binding.tvStatus.text = statusText
        binding.tvStatus.background = resources.getDrawable(
            if (hasError) R.drawable.bg_status_error else if (running) R.drawable.bg_status_streaming else R.drawable.bg_status_stopped,
            theme
        )

        // Update info card
        val settings = Settings.getInstance(this)
        val ip = getLocalIpAddress()
        val rtspUrl = "rtsp://$ip:${Settings.rtspPort}/${Settings.rtspPath}"
        val apiUrl = "http://$ip:${Settings.apiPort}"

        binding.tvStreamAddress.text = "$rtspUrl\n$apiUrl"
        binding.tvUptime.text = "${getString(R.string.info_uptime)} ${formatUptime(uptime)}"
        binding.tvResolutionFpsBitrate.text = "${getString(R.string.info_resolution_fps_bitrate)} ${settings.resolution} / ${settings.frameRate}fps / ${settings.bitrateKbps}kbps"
        
        // Battery level
        val batteryLevel = getBatteryLevel()
        binding.tvBattery.text = "${getString(R.string.info_battery)} ${if (batteryLevel >= 0) "$batteryLevel%" else "N/A"}"

        // Storage
        val storageStats = com.freecam.camera.storage.StorageManager(this, settings).getStorageStats()
        binding.tvStorageFree.text = "${getString(R.string.info_storage_free)} ${formatBytes(storageStats.capBytes - storageStats.totalSizeBytes)}"
        binding.tvStorageUsed.text = "${getString(R.string.info_storage_used)} ${formatBytes(storageStats.totalSizeBytes)} (${storageStats.usagePercent}%)"

        // Events today (placeholder)
        binding.tvEventsToday.text = "${getString(R.string.info_events_today)} 0"

        // Motion detection status
        binding.tvMotion.text = "${getString(R.string.info_motion)} ${if (settings.motionEnabled) "ON (${settings.motionSensitivity})" else "OFF"}"

        // Schedule next update
        handler.postDelayed(updateRunnable, 2000)
    }

    private fun formatUptime(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    private fun getBatteryLevel(): Int {
        try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            }
        } catch (e: Exception) {
            return -1
        }
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
            Log.e("MainActivity", "Error getting IP", e)
        }
        return "0.0.0.0"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_about -> {
                Toast.makeText(this, "FreeCam Camera v1.2.0", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}