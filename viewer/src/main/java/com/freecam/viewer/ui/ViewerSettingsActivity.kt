package com.freecam.viewer.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.freecam.viewer.R
import com.freecam.viewer.databinding.ActivityViewerSettingsBinding

class ViewerSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActivityViewerSettingsBinding>(this, R.layout.activity_viewer_settings)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.viewer_preferences, rootKey)
            
            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                Toast.makeText(requireContext(), "Cache cleared", Toast.LENGTH_SHORT).show()
                true
            }
            
            findPreference<Preference>("about")?.setOnPreferenceClickListener {
                // Show about dialog
                true
            }
        }
    }
}