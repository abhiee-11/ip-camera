package com.freecam.viewer.ui

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.freecam.viewer.BuildConfig
import com.freecam.viewer.R
import com.freecam.viewer.databinding.ActivitySettingsBinding
import com.freecam.viewer.data.Settings

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_settings)

        settings = Settings.getInstance(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.tvVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
        binding.tvLibraries.text = "RootEncoder, NanoHTTPD, Media3 ExoPlayer, ZXing, Kotlinx Serialization, Timber"

        binding.rgTheme.check(when (settings.theme) {
            1 -> R.id.rbThemeDark
            2 -> R.id.rbThemeLight
            else -> R.id.rbThemeSystem
        })

        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.rbThemeSystem -> 0
                R.id.rbThemeDark -> 1
                R.id.rbThemeLight -> 2
                else -> 0
            }
            settings.theme = newTheme
            recreate()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}