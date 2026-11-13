package com.freecam.camera.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.freecam.camera.R
import com.freecam.camera.data.Settings
import com.freecam.camera.databinding.ActivityPairingBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.freecam.common.model.Pairing
import com.freecam.common.util.UrlBuilder
import java.net.NetworkInterface

class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_pairing)

        settings = Settings.getInstance(this)
        setupAddresses()
        generateQrCode()
        setupTokenCopy()
    }

    private fun setupAddresses() {
        val addresses = getLocalAddresses()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, addresses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAddresses.adapter = adapter
        binding.spinnerAddresses.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                generateQrCode()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun getLocalAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            NetworkInterface.networkInterfaces.asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
                .map { android.text.format.Formatter.formatIpAddress(it.hashCode()) }
                .forEach { result.add(it) }
        } catch (e: Exception) {
            // Fallback
            result.add("192.168.1.100")
        }
        // Ensure Tailscale addresses appear first
        return result.sortedBy { if (it.startsWith("100.")) 0 else 1 }
    }

    private fun generateQrCode() {
        val selectedAddress = binding.spinnerAddresses.selectedItem as? String ?: "192.168.1.100"
        val pairing = Pairing(
            name = settings.cameraName,
            host = selectedAddress,
            token = settings.token
        )
        val json = pairing.toJson()

        val qrWriter = QRCodeWriter()
        try {
            val bitMatrix = qrWriter.encode(json, BarcodeFormat.QR_CODE, 280, 280)
            val bitmap = Bitmap.createBitmap(280, 280, Bitmap.Config.RGB_565)
            for (x in 0 until 280) {
                for (y in 0 until 280) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000 else 0xFFFFFFFF)
                }
            }
            binding.ivQrCode.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            binding.ivQrCode.setImageResource(R.drawable.ic_launcher_background)
        }
    }

    private fun setupTokenCopy() {
        binding.tvToken.text = settings.token
        binding.btnCopyToken.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip = ClipData.newPlainText("FreeCam Token", settings.token)
            Toast.makeText(this, R.string.pairing_copied, Toast.LENGTH_SHORT).show()
        }
    }
}