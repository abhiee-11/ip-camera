package com.freecam.viewer.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.freecam.common.model.Pairing
import com.freecam.viewer.R
import com.freecam.viewer.databinding.ActivityAddCameraBinding
import com.freecam.viewer.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddCameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCameraBinding
    private val apiClient = ApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_camera)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnTestConnect.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { saveCamera() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun testConnection() {
        val host = binding.etHost.text.toString().trim()
        val rtspPort = binding.etRtspPort.text.toString().toIntOrNull() ?: 8554
        val httpPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 8080
        val token = binding.etToken.text.toString().trim()

        if (host.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Host and token required", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnTestConnect.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val success = apiClient.testConnection(host, httpPort, token)
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.btnTestConnect.isEnabled = true
                if (success) {
                    Toast.makeText(this@AddCameraActivity, R.string.connection_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AddCameraActivity, R.string.connection_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveCamera() {
        val name = binding.etName.text.toString().trim()
        val host = binding.etHost.text.toString().trim()
        val rtspPort = binding.etRtspPort.text.toString().toIntOrNull() ?: 8554
        val httpPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 8080
        val token = binding.etToken.text.toString().trim()

        if (name.isEmpty() || host.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show()
            return
        }

        val pairing = Pairing(
            name = name,
            host = host,
            rtspPort = rtspPort,
            httpPort = httpPort,
            token = token,
            mode = "local"
        )

        val prefs = getSharedPreferences("freecam_viewer_cameras", Context.MODE_PRIVATE)
        val json = pairing.toJson()
        prefs.edit().putString("camera_${pairing.name}", json).apply()

        Toast.makeText(this, "Camera saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}