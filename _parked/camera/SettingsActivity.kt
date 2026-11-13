package com.freecam.camera.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.freecam.camera.R
import com.freecam.camera.data.Settings

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val settings = Settings.getInstancePlain(requireContext())

            // Set ntfy topic summary
            findPreference<androidx.preference.EditTextPreference>("ntfy_topic")?.let { pref ->
                pref.setOnBindViewHolderListener { view ->
                    view.summary = if (settings.ntfyTopic.isEmpty()) "Tap to generate" else "******"
                }
                pref.setOnPreferenceChangeListener { _, newValue ->
                    settings.ntfyTopic = newValue as String
                    true
                }
            }

            // Reset token
            findPreference<androidx.preference.Preference>("reset_token")?.setOnPreferenceClickListener {
                val newToken = settings.resetToken()
                Toast.makeText(requireContext(), "New token: $newToken", Toast.LENGTH_LONG).show()
                true
            }

            // Disable relay mode in Phase 1
            findPreference<androidx.preference.ListPreference>("destination_mode")?.let { pref ->
                pref.isEnabled = false
                pref.summary = getString(R.string.mode_relay_disabled)
            }
        }
    }
}