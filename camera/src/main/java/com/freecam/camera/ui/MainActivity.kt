package com.freecam.camera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.freecam.camera.R
import com.freecam.camera.data.CameraUiState
import com.freecam.camera.data.Settings
import com.freecam.camera.databinding.ActivityMainBinding
import com.freecam.camera.service.CameraService

/**
 * The camera dashboard. One glance says whether the camera is working, and
 * every number on it comes from the live state the service publishes.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { render(CameraService.snapshot()) }
    private var cameraPermissionRequestCode = 1001
    private var previewAttached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        settings = Settings.getInstance(this)

        setupActions()
        render(CameraService.snapshot())
    }

    override fun onStart() {
        super.onStart()
        if (settings.previewEnabled) setPreview(true)
        handler.postDelayed(updateRunnable, 1000)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateRunnable)
        // The preview surface belongs to this activity. Without it the camera
        // keeps streaming, headless.
        if (previewAttached) setPreview(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        CameraService.attachPreviewView(null)
    }

    private fun setupActions() {
        binding.btnPrimary.setOnClickListener {
            if (!CameraService.isRunning && !hasCameraPermission()) {
                requestCameraPermission()
                return@setOnClickListener
            }
            sendServiceAction(if (CameraService.isRunning) CameraService.ACTION_STOP else CameraService.ACTION_START)
        }

        binding.tilePreview.setOnClickListener {
            val next = !settings.previewEnabled
            settings.previewEnabled = next
            setPreview(next)
            binding.tvTilePreview.text = getString(if (next) R.string.action_restarting else R.string.tile_preview)
            handler.postDelayed({
                binding.tvTilePreview.text = getString(R.string.tile_preview)
            }, 1500)
        }

        binding.tilePairing.setOnClickListener {
            startActivity(Intent(this, PairingActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnOverflow.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_restart_camera -> {
                        sendServiceAction(CameraService.ACTION_RESTART_CAMERA)
                        true
                    }
                    R.id.menu_show_logs -> {
                        startActivity(Intent(this, LogsActivity::class.java))
                        true
                    }
                    R.id.menu_pair_viewer -> {
                        startActivity(Intent(this, PairingActivity::class.java))
                        true
                    }
                    R.id.menu_dim_screen -> {
                        startActivity(Intent(this, DimScreenActivity::class.java))
                        true
                    }
                    R.id.menu_reset_token -> {
                        confirmResetToken()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun confirmResetToken() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_reset_token_title)
            .setMessage(R.string.dialog_reset_token_message)
            .setPositiveButton(R.string.dialog_reset_token_confirm) { _, _ ->
                sendServiceAction(CameraService.ACTION_RESET_TOKEN)
                Toast.makeText(this, R.string.pairing_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun setPreview(enabled: Boolean) {
        if (enabled) {
            binding.previewContainer.visibility = View.VISIBLE
            CameraService.attachPreviewView(binding.previewView)
            previewAttached = true
        } else {
            CameraService.attachPreviewView(null)
            binding.previewContainer.visibility = View.GONE
            previewAttached = false
        }
        sendServiceAction(CameraService.ACTION_SET_PREVIEW, enabled)
    }

    private fun sendServiceAction(action: String, previewEnabled: Boolean? = null) {
        val intent = Intent(this, CameraService::class.java).apply {
            this.action = action
            if (previewEnabled != null) {
                putExtra(CameraService.EXTRA_PREVIEW_ENABLED, previewEnabled)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Could not reach the camera service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render(state: CameraUiState) {
        binding.tvCameraName.text = state.cameraName.ifEmpty { getString(R.string.app_name) }

        if (state.running) {
            binding.tvStateBadge.text = getString(R.string.state_recording)
            binding.tvStateBadge.setBackgroundResource(R.drawable.bg_badge_tape)
            binding.tvStateBadge.setTextColor(getColor(R.color.tape))
        } else {
            binding.tvStateBadge.text = getString(R.string.state_stopped)
            binding.tvStateBadge.setBackgroundResource(R.drawable.bg_badge_mute)
            binding.tvStateBadge.setTextColor(getColor(R.color.mute))
        }

        binding.tvStateClock.text = formatUptime(state.uptimeMs)
        binding.tvStateSubLine.text = buildString {
            append(if (state.running) "streaming" else "not streaming")
            append(" / configured ")
            append(state.resolution)
            append(" / ")
            append(state.fps)
            append("fps / ")
            append(state.bitrateKbps)
            append(" kbps")
        }

        binding.btnPrimary.text = getString(
            if (state.running) R.string.action_stop_streaming else R.string.action_start_streaming
        )
        if (state.running) {
            // A destructive action is outlined, never filled. The filled amber
            // is reserved for the action you want.
            binding.btnPrimary.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            binding.btnPrimary.strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.tape))
            binding.btnPrimary.strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
            binding.btnPrimary.setTextColor(getColor(R.color.tape))
        } else {
            binding.btnPrimary.backgroundTintList =
                android.content.res.ColorStateList.valueOf(getColor(R.color.sodium))
            binding.btnPrimary.strokeWidth = 0
            binding.btnPrimary.setTextColor(getColor(R.color.on_accent))
        }

        binding.tvAddress.text = state.host ?: getString(R.string.value_unknown)
        binding.tvTailnet.text = getString(
            if (state.tailnetConnected) R.string.value_tailnet_connected else R.string.value_tailnet_absent
        )
        binding.tvTailnet.setTextColor(
            getColor(if (state.tailnetConnected) R.color.phosphor else R.color.sodium)
        )
        binding.tvBattery.text = when {
            state.batteryPercent == null -> getString(R.string.value_unknown)
            state.batteryCharging -> "${state.batteryPercent}% ${getString(R.string.value_charging)}"
            else -> "${state.batteryPercent}%"
        }
        binding.tvStorage.text = "${formatBytes(state.storageUsedBytes)} / ${formatBytes(state.storageCapBytes)}"
        binding.tvMotion.text = when {
            !state.motionAvailable -> getString(R.string.value_motion_unavailable)
            !state.motionEnabled -> getString(R.string.value_off)
            else -> state.motionSensitivity
        }
        binding.tvEventsToday.text =
            if (state.eventsToday > 0) state.eventsToday.toString() else getString(R.string.value_unknown)

        val fault = state.errorMessage
        if (fault.isNullOrEmpty()) {
            binding.bannerFault.visibility = View.GONE
        } else {
            binding.bannerFault.visibility = View.VISIBLE
            binding.tvFaultTitle.text = faultTitle(state.errorCode)
            binding.tvFaultMessage.text = fault
        }

        handler.postDelayed(updateRunnable, 1000)
    }

    private fun faultTitle(code: String?): String {
        return when (code) {
            "encoder_rejected" -> getString(R.string.fault_encoder_rejected)
            "start_failed" -> getString(R.string.fault_start_failed)
            "server_failed" -> getString(R.string.fault_server_failed)
            "record_failed" -> getString(R.string.fault_record_failed)
            "api_failed" -> getString(R.string.fault_api_failed)
            "auth_failed" -> getString(R.string.fault_auth_failed)
            else -> getString(R.string.fault_encoder_stopped)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            cameraPermissionRequestCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendServiceAction(CameraService.ACTION_START)
            } else {
                Toast.makeText(this, R.string.perm_camera_rationale, Toast.LENGTH_LONG).show()
            }
        }
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
            bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
