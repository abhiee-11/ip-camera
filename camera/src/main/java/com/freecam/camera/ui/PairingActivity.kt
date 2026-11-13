package com.freecam.camera.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.freecam.camera.R
import com.freecam.camera.data.Settings
import com.freecam.camera.databinding.ActivityPairingBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_pairing)
        settings = Settings.getInstance(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        generatePairingQR()
        
        binding.btnCopyToken.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("FreeCam Token", settings.token)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.pairing_copied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun generatePairingQR() {
        val ip = getLocalIpAddress()
        val pairingData = JSONObject().apply {
            put("type", "freecam_pairing")
            put("version", 1)
            put("name", settings.cameraName)
            put("rtsp_url", "rtsp://$ip:${Settings.rtspPort}/${Settings.rtspPath}")
            put("api_url", "http://$ip:${Settings.apiPort}")
            put("token", settings.token)
            put("mode", settings.destinationMode)
        }.toString()

        binding.tvAddress.text = getString(R.string.pairing_address_label) + "\nrtsp://$ip:${Settings.rtspPort}/${Settings.rtspPath}"
        binding.tvToken.text = getString(R.string.pairing_token_label) + "\n${settings.token}"

        lifecycleScope.launch(Dispatchers.IO) {
            val qrBitmap = generateQRCode(pairingData, 400)
            withContext(Dispatchers.Main) {
                binding.ivQrCode.setImageBitmap(qrBitmap)
            }
        }
    }

    private fun generateQRCode(data: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        try {
            val bitMatrix: BitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            return bitmap
        } catch (e: WriterException) {
            Log.e("PairingActivity", "QR generation failed", e)
            return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        }
    }

    private fun getLocalIpAddress(): String {
        // Priority 1: Tailscale IP (100.64.0.0/10 CGNAT range)
        // Priority 2: Local Wi-Fi IP
        var tailscaleIp: String? = null
        var wifiIp: String? = null
        
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress
                        // Tailscale uses 100.64.0.0/10 (CGNAT)
                        if (ip.startsWith("100.")) {
                            tailscaleIp = ip
                        } else if (wifiIp == null) {
                            wifiIp = ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PairingActivity", "Error getting IP", e)
        }
        
        // Prefer Tailscale IP for pairing
        return tailscaleIp ?: wifiIp ?: "0.0.0.0"
    }
}