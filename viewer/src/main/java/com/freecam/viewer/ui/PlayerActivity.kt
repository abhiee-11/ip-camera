package com.freecam.viewer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import com.freecam.common.model.Pairing
import com.freecam.common.model.RecordingInfo
import com.freecam.viewer.R
import com.freecam.viewer.databinding.ActivityPlayerBinding
import com.freecam.viewer.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * The live feed and recorded clips. The feed owns the screen: the chrome is
 * minimal and a fault names its cause instead of leaving a black rectangle.
 */
@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var pairing: Pairing? = null
    private val apiClient = ApiClient()
    private var isLive = true
    private var playbackRecording: RecordingInfo? = null
    private var isFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_player)

        val json = intent.getStringExtra("pairing") ?: return finish()
        pairing = Json.decodeFromString(Pairing.serializer(), json)

        val recordingJson = intent.getStringExtra("recording")
        if (recordingJson != null) {
            isLive = false
            playbackRecording = Json.decodeFromString(RecordingInfo.serializer(), recordingJson)
        }

        setupPlayer()
        setupRail()
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showFault(error)
            }
        })

        if (isLive) playLive() else playRecording()

        exoPlayer?.playWhenReady = true
        binding.tvLiveChip.visibility = if (isLive) View.VISIBLE else View.GONE
        binding.liveBar.visibility = if (isLive) View.VISIBLE else View.GONE
        binding.rail.visibility = if (isLive) View.VISIBLE else View.GONE
        binding.tvPlayerName.text = pairing?.name ?: ""
    }

    private fun playLive() {
        val pairing = this.pairing ?: return
        // The credentials are part of the URL: the camera's RTSP server asks
        // for them.
        val rtspUrl = ApiClient.buildRtspUrl(pairing)
        val mediaSource = RtspMediaSource.Factory()
            .setUserAgent("FreeCam Viewer")
            .createMediaSource(MediaItem.fromUri(rtspUrl))
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
        startElapsedTicker()
    }

    private fun playRecording() {
        val pairing = this.pairing ?: return
        val recording = playbackRecording ?: return

        val url = ApiClient.buildHttpUrl(pairing.host, pairing.httpPort, recording.path)

        val mediaItem = MediaItem.fromUri(url)
            .buildUpon()
            .setMediaId(recording.filename)
            .build()

        // The camera API needs the bearer token on every request.
        val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${pairing.token}"))
        val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
    }

    /** Shows the camera's own uptime, so the viewer never invents a number. */
    private fun startElapsedTicker() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                val pairing = this@PlayerActivity.pairing ?: break
                val status = apiClient.getStatus(pairing.host, pairing.httpPort, pairing.token)
                runOnUiThread {
                    if (status != null) {
                        val seconds = status.uptimeMs / 1000
                        binding.tvElapsed.text = String.format(
                            "%02d:%02d:%02d",
                            seconds / 3600,
                            (seconds % 3600) / 60,
                            seconds % 60
                        )
                    }
                }
                delay(5000)
            }
        }
    }

    private fun setupRail() {
        binding.btnRailHistory.setOnClickListener { openCameraList(R.string.btn_history) }
        binding.btnRailEvents.setOnClickListener { openCameraList(R.string.btn_events) }
        binding.btnRailFullscreen.setOnClickListener {
            val decor = window.decorView
            if (isFullscreen) {
                decor.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                isFullscreen = false
            } else {
                decor.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                isFullscreen = true
            }
        }
        binding.btnRetry.setOnClickListener {
            binding.faultBlock.visibility = View.GONE
            if (isLive) playLive() else playRecording()
        }
    }

    private fun openCameraList(titleRes: Int) {
        val pairing = this.pairing ?: return
        val target = if (titleRes == R.string.btn_history) HistoryActivity::class.java else EventsActivity::class.java
        val intent = Intent(this, target).apply {
            putExtra("pairing", Json.encodeToString(Pairing.serializer(), pairing))
        }
        startActivity(intent)
    }

    private fun showFault(error: PlaybackException) {
        val message = when {
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                getString(R.string.fault_camera_offline)
            error.errorCode == PlaybackException.ERROR_CODE_INVALID_STATE ->
                getString(R.string.fault_cannot_play)
            else -> getString(R.string.fault_cannot_play)
        }
        binding.faultBlock.visibility = View.VISIBLE
        binding.tvFault.text = message
        binding.tvLiveChip.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
        if (exoPlayer?.isPlaying == true) exoPlayer?.pause()
    }
}
