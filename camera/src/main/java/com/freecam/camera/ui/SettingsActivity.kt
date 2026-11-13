package com.freecam.camera.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.freecam.camera.R
import com.freecam.camera.data.Settings

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private lateinit var settings: Settings

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            settings = Settings.getInstance(requireContext())
            
            bindPreferences()
        }

        private fun bindPreferences() {
            // Camera Name
            findPreference<EditTextPreference>("camera_name")?.apply {
                setText(settings.cameraName)
                setOnPreferenceChangeListener { _, newValue ->
                    settings.cameraName = newValue as String
                    true
                }
            }

            // Camera Facing
            findPreference<ListPreference>("camera_facing")?.apply {
                val entries = resources.getStringArray(R.array.camera_facing_entries)
                val values = resources.getStringArray(R.array.camera_facing_values)
                this.entries = entries
                this.entryValues = values
                value = settings.cameraFacing.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settings.cameraFacing = (newValue as String).toInt()
                    true
                }
            }

            // Resolution
            findPreference<ListPreference>("resolution")?.apply {
                val entries = resources.getStringArray(R.array.resolution_entries)
                val values = resources.getStringArray(R.array.resolution_values)
                this.entries = entries
                this.entryValues = values
                value = settings.resolution
                setOnPreferenceChangeListener { _, newValue ->
                    settings.resolution = newValue as String
                    true
                }
            }

            // Frame Rate
            findPreference<ListPreference>("frame_rate")?.apply {
                val entries = resources.getStringArray(R.array.frame_rate_entries)
                val values = resources.getStringArray(R.array.frame_rate_values)
                this.entries = entries
                this.entryValues = values
                value = settings.frameRate.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settings.frameRate = (newValue as String).toInt()
                    true
                }
            }

            // Bitrate
            findPreference<ListPreference>("bitrate")?.apply {
                val entries = resources.getStringArray(R.array.bitrate_entries)
                val values = resources.getStringArray(R.array.bitrate_values)
                this.entries = entries
                this.entryValues = values
                value = settings.bitrateKbps.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settings.bitrateKbps = (newValue as String).toInt()
                    true
                }
            }

            // Rotation
            findPreference<ListPreference>("rotation")?.apply {
                val entries = resources.getStringArray(R.array.rotation_entries)
                val values = resources.getStringArray(R.array.rotation_values)
                this.entries = entries
                this.entryValues = values
                value = settings.rotation
                setOnPreferenceChangeListener { _, newValue ->
                    settings.rotation = newValue as String
                    true
                }
            }

            // Recording Mode
            findPreference<ListPreference>("recording_mode")?.apply {
                val entries = resources.getStringArray(R.array.recording_mode_entries)
                val values = resources.getStringArray(R.array.recording_mode_values)
                this.entries = entries
                this.entryValues = values
                value = settings.recordingMode
                setOnPreferenceChangeListener { _, newValue ->
                    settings.recordingMode = newValue as String
                    true
                }
            }

            // Storage Cap
            findPreference<ListPreference>("storage_cap")?.apply {
                val entries = resources.getStringArray(R.array.storage_cap_entries)
                val values = resources.getStringArray(R.array.storage_cap_values)
                this.entries = entries
                this.entryValues = values
                value = settings.storageCapGb.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settings.storageCapGb = (newValue as String).toInt()
                    true
                }
            }

            // Retention Days
            findPreference<ListPreference>("retention_days")?.apply {
                val entries = resources.getStringArray(R.array.retention_days_entries)
                val values = resources.getStringArray(R.array.retention_days_values)
                this.entries = entries
                this.entryValues = values
                value = settings.retentionDays.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settings.retentionDays = (newValue as String).toInt()
                    true
                }
            }

            // Motion Enabled
            findPreference<SwitchPreferenceCompat>("motion_enabled")?.apply {
                isChecked = settings.motionEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    settings.motionEnabled = newValue as Boolean
                    true
                }
            }

            // Motion Sensitivity
            findPreference<ListPreference>("motion_sensitivity")?.apply {
                val entries = resources.getStringArray(R.array.sensitivity_entries)
                val values = resources.getStringArray(R.array.sensitivity_values)
                this.entries = entries
                this.entryValues = values
                value = settings.motionSensitivity
                setOnPreferenceChangeListener { _, newValue ->
                    settings.motionSensitivity = newValue as String
                    true
                }
            }

            // Motion Cooldown
            findPreference<ListPreference>("motion_cooldown")?.apply {
                val entries = resources.getStringArray(R.array.cooldown_entries)
                val values = resources.getStringArray(R.array.cooldown_values)
                this.entries = entries
                this.entryValues = values
                value = settings.motionCooldownSec.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    settings.motionCooldownSec = (newValue as String).toInt()
                    true
                }
            }

            // ntfy Server
            findPreference<EditTextPreference>("ntfy_server")?.apply {
                setText(settings.ntfyServer)
                setOnPreferenceChangeListener { _, newValue ->
                    settings.ntfyServer = newValue as String
                    true
                }
            }

            // ntfy Topic
            findPreference<EditTextPreference>("ntfy_topic")?.apply {
                setText(settings.ntfyTopic)
                setOnPreferenceChangeListener { _, newValue ->
                    settings.ntfyTopic = newValue as String
                    true
                }
            }

            // Destination Mode
            findPreference<ListPreference>("destination_mode")?.apply {
                val entries = resources.getStringArray(R.array.destination_mode_entries)
                val values = resources.getStringArray(R.array.destination_mode_values)
                this.entries = entries
                this.entryValues = values
                value = settings.destinationMode
                setOnPreferenceChangeListener { _, newValue ->
                    settings.destinationMode = newValue as String
                    true
                }
            }

            // Auto Start
            findPreference<SwitchPreferenceCompat>("auto_start")?.apply {
                isChecked = settings.autoStart
                setOnPreferenceChangeListener { _, newValue ->
                    settings.autoStart = newValue as Boolean
                    true
                }
            }

            // Nightly Restart
            findPreference<SwitchPreferenceCompat>("nightly_restart")?.apply {
                isChecked = settings.nightlyRestart
                setOnPreferenceChangeListener { _, newValue ->
                    settings.nightlyRestart = newValue as Boolean
                    true
                }
            }

            // Restart Time
            findPreference<EditTextPreference>("restart_time")?.apply {
                setText(settings.restartTime)
                setOnPreferenceChangeListener { _, newValue ->
                    settings.restartTime = newValue as String
                    true
                }
            }

            // Reset Token
            findPreference<Preference>("reset_token")?.setOnPreferenceClickListener {
                val newToken = settings.resetToken()
                Toast.makeText(requireContext(), "Token reset: $newToken", Toast.LENGTH_LONG).show()
                true
            }
        }
    }
}