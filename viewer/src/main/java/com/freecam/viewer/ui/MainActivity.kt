package com.freecam.viewer.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.freecam.common.model.CameraStatus
import com.freecam.common.model.Pairing
import com.freecam.viewer.BuildConfig
import com.freecam.viewer.R
import com.freecam.viewer.databinding.ActivityMainBinding
import com.freecam.viewer.databinding.ItemCameraBinding
import com.freecam.viewer.network.ApiClient
import com.freecam.viewer.network.ThumbnailLoader
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Suppress("UnsafeOptInUsageError")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = CameraAdapter()
    private val apiClient = ApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupActions()
        setupSwipeRefresh()
        loadCameras()
    }

    private fun setupRecyclerView() {
        binding.rvCameras.layoutManager = LinearLayoutManager(this)
        binding.rvCameras.adapter = adapter
        binding.rvCameras.setHasFixedSize(true)
    }

    private fun setupActions() {
        binding.btnScan.setOnClickListener { scanQR() }
        binding.btnScanEmpty.setOnClickListener { scanQR() }
        binding.btnAddManual.setOnClickListener {
            startActivity(Intent(this, AddCameraActivity::class.java))
        }
        binding.btnMore.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_add_camera -> {
                        showAddCameraDialog()
                        true
                    }
                    R.id.menu_settings -> {
                        startActivity(Intent(this, ViewerSettingsActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadCameras()
            binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.phosphor)
    }

    private fun showAddCameraDialog() {
        val options = arrayOf(getString(R.string.add_via_qr), getString(R.string.add_manually))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.add_camera_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> scanQR()
                    1 -> startActivity(Intent(this, AddCameraActivity::class.java))
                }
            }
            .show()
    }

    private fun scanQR() {
        val integrator = IntentIntegrator(this)
        integrator.setPrompt("Scan FreeCam pairing QR code")
        integrator.setBeepEnabled(true)
        integrator.setOrientationLocked(true)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                processQRResult(result.contents!!)
            } else {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processQRResult(qrContent: String) {
        try {
            val pairing = parseCameraPairing(qrContent)
            testAndSavePairing(pairing)
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR code: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseCameraPairing(qrContent: String): Pairing {
        // Try parsing as Pairing model first (viewer-generated QR)
        return try {
            Pairing.fromJson(qrContent)
        } catch (e: Exception) {
            // Fallback: parse camera's QR format
            val json = org.json.JSONObject(qrContent)
            val rtspUrl = json.optString("rtsp_url", "")
            val apiUrl = json.optString("api_url", "")
            val host = extractHost(rtspUrl) ?: extractHost(apiUrl) ?: "0.0.0.0"
            Pairing(
                v = json.optInt("version", 1),
                name = json.optString("name", "Camera"),
                host = host,
                rtspPort = extractPort(rtspUrl) ?: 8554,
                httpPort = extractPort(apiUrl) ?: 8080,
                path = extractPath(rtspUrl) ?: "live",
                token = json.optString("token", ""),
                mode = json.optString("mode", "local")
            )
        }
    }

    private fun extractHost(url: String): String? {
        try {
            return java.net.URL(url).host
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractPort(url: String): Int? {
        try {
            val port = java.net.URL(url).port
            return if (port > 0) port else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractPath(url: String): String? {
        try {
            val path = java.net.URL(url).path
            return if (path.isNotEmpty()) path.removePrefix("/") else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun testAndSavePairing(pairing: Pairing) {
        lifecycleScope.launch(Dispatchers.IO) {
            val success = withContext(Dispatchers.IO) {
                apiClient.testConnection(pairing.host, pairing.httpPort, pairing.token)
            }
            runOnUiThread {
                if (success) {
                    savePairing(pairing)
                    Toast.makeText(this@MainActivity, R.string.connection_success, Toast.LENGTH_SHORT).show()
                    loadCameras()
                } else {
                    Toast.makeText(this@MainActivity, R.string.connection_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun savePairing(pairing: Pairing) {
        val prefs = getSharedPreferences("freecam_viewer_cameras", Context.MODE_PRIVATE)
        val json = pairing.toJson()
        prefs.edit().putString("camera_${pairing.name}", json).apply()
    }

    private fun loadCameras() {
        val prefs = getSharedPreferences("freecam_viewer_cameras", Context.MODE_PRIVATE)
        val all = prefs.all
        val cameras = mutableListOf<Pairing>()
        
        all.values.forEach { json ->
            try {
                val pairing = Pairing.fromJson(json as String)
                cameras.add(pairing)
            } catch (e: Exception) {
                // Skip invalid
            }
        }
        
        adapter.submitList(cameras)
        binding.emptyState.visibility = if (cameras.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCameras.visibility = if (cameras.isEmpty()) View.GONE else View.VISIBLE
        updateStatusStrip(cameras)
    }

    /**
     * The strip is hidden when there is nothing to report. A strip reading
     * zeros would be a lie.
     */
    private fun updateStatusStrip(cameras: List<Pairing>) {
        if (cameras.isEmpty()) {
            binding.tvStatusStrip.visibility = View.GONE
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            var events = 0
            var recording = false
            for (camera in cameras) {
                val status = apiClient.getStatus(camera.host, camera.httpPort, camera.token)
                if (status != null) {
                    events += status.motion.eventsToday
                    recording = recording || status.recording
                }
            }
            runOnUiThread {
                if (events > 0 || recording) {
                    binding.tvStatusStrip.visibility = View.VISIBLE
                    binding.tvStatusStrip.text = buildString {
                        if (events > 0) append("$events motion events today")
                        if (events > 0 && recording) append(" · ")
                        if (recording) append("recording")
                    }
                } else {
                    binding.tvStatusStrip.visibility = View.GONE
                }
            }
        }
    }

    inner class CameraAdapter : RecyclerView.Adapter<CameraAdapter.CameraViewHolder>() {
        private var cameras = emptyList<Pairing>()

        fun submitList(newList: List<Pairing>) {
            cameras = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
            val binding = ItemCameraBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CameraViewHolder(binding)
        }

        override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
            val camera = cameras[position]
            holder.bind(camera)
        }

        override fun getItemCount(): Int = cameras.size

        inner class CameraViewHolder(private val binding: ItemCameraBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(camera: Pairing) {
                binding.tvCameraName.text = camera.name
                binding.tvCameraAddress.text = camera.host
                binding.tvStatusDot.setTextColor(getColor(R.color.mute))
                binding.tvCameraStatus.text = getString(R.string.status_offline)
                binding.ivCameraThumb.setImageDrawable(null)

                // The thumbnail comes from the camera itself. While the camera
                // cannot produce a frame the placeholder stays.
                loadThumbnail(camera)

                lifecycleScope.launch(Dispatchers.IO) {
                    val status = apiClient.getStatus(camera.host, camera.httpPort, camera.token)
                    runOnUiThread {
                        if (status != null) {
                            binding.tvStatusDot.setTextColor(
                                getColor(if (status.running) R.color.phosphor else R.color.mute)
                            )
                            binding.tvCameraStatus.text = if (status.running) {
                                buildString {
                                    append(getString(R.string.status_live))
                                    val percent = status.battery.percent
                                    if (percent != null) {
                                        append(" · ")
                                        append("$percent%")
                                        if (status.battery.charging) {
                                            append(" ${getString(R.string.value_charging)}")
                                        }
                                    }
                                }
                            } else {
                                getString(R.string.status_offline)
                            }
                        } else {
                            binding.tvStatusDot.setTextColor(getColor(R.color.mute))
                            binding.tvCameraStatus.text = getString(R.string.status_offline)
                        }
                    }
                }

                binding.root.setOnClickListener {
                    val intent = Intent(binding.root.context, PlayerActivity::class.java).apply {
                        putExtra("pairing", Json.encodeToString(Pairing.serializer(), camera))
                    }
                    binding.root.context.startActivity(intent)
                }

                binding.root.setOnLongClickListener { view ->
                    showCameraMenu(camera, view)
                    true
                }
            }

            private fun loadThumbnail(camera: Pairing) {
                val url = ApiClient.buildSnapshotUrl(camera.host, camera.httpPort)
                ThumbnailLoader.load(binding.ivCameraThumb, url, camera.token)
            }
        }
    }

    private fun showCameraMenu(camera: Pairing, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.camera_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_rename -> {
                    showRenameDialog(camera)
                    true
                }
                R.id.menu_delete -> {
                    deleteCamera(camera)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(camera: Pairing) {
        val input = android.widget.EditText(this).apply {
            setText(camera.name)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rename Camera")
            .setView(input)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != camera.name) {
                    val updated = camera.copy(name = newName)
                    deleteCamera(camera)
                    savePairing(updated)
                    loadCameras()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteCamera(camera: Pairing) {
        val prefs = getSharedPreferences("freecam_viewer_cameras", Context.MODE_PRIVATE)
        prefs.edit().remove("camera_${camera.name}").apply()
        loadCameras()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, ViewerSettingsActivity::class.java))
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        val versionName = try {
            BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            "1.0.0"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_version, versionName) + "\n\n" + getString(R.string.about_libraries))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}