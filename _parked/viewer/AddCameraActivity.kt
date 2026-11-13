package com.freecam.viewer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.freecam.viewer.R
import com.freecam.viewer.backend.PhoneBackend
import com.freecam.viewer.data.CameraStore
import com.freecam.viewer.databinding.ActivityAddCameraBinding
import com.freecam.common.model.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddCameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCameraBinding
    private lateinit var cameraStore: CameraStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_add_camera)

        cameraStore = CameraStore.getInstance(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnScanQr.setOnClickListener { startQrScanner() }
        binding.btnTestConnect.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { saveCamera() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun startQrScanner() {
        val intent = Intent(this, QrScannerActivity::class.java)
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val json = data?.getStringExtra("qr_json") ?: return
            try {
                val pairing = Pairing.fromJson(json)
                fillFields(pairing)
                Toast.makeText(this, "QR code parsed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fillFields(pairing: Pairing) {
        binding.etName.setText(pairing.name)
        binding.etHost.setText(pairing.host)
        binding.etRtspPort.setText(pairing.rtspPort.toString())
        binding.etHttpPort.setText(pairing.httpPort.toString())
        binding.etToken.setText(pairing.token)
    }

    private fun testConnection() {
        val name = binding.etName.text.toString().trim()
        val host = binding.etHost.text.toString().trim()
        val rtspPort = binding.etRtspPort.text.toString().toIntOrNull() ?: 8554
        val httpPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 8080
        val token = binding.etToken.text.toString().trim()

        if (name.isEmpty() || host.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val pairing = Pairing(
            name = name,
            host = host,
            rtspPort = rtspPort,
            httpPort = httpPort,
            token = token
        )

        lifecycleScope.launch {
            val backend = PhoneBackend(pairing)
            val success = withContext(Dispatchers.IO) { backend.ping() }
            if (success) {
                // Also test status endpoint
                try {
                    val status = backend.status()
                    runOnUiThread {
                        Toast.makeText(this@AddCameraActivity, R.string.connection_success, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@AddCameraActivity, "Connected but status failed", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                runOnUiThread {
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
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val pairing = Pairing(
            name = name,
            host = host,
            rtspPort = rtspPort,
            httpPort = httpPort,
            token = token
        )

        cameraStore.addCamera(pairing)
        Toast.makeText(this, "Camera saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}