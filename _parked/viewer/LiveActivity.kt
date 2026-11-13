package com.freecam.viewer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import com.freecam.viewer.R
import com.freecam.viewer.backend.PhoneBackend
import com.freecam.viewer.data.CameraStore
import com.freecam.viewer.databinding.ActivityLiveBinding
import com.freecam.common.model.CameraStatus
import com.freecam.common.model.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class LiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveBinding
    private lateinit var pairing: Pairing
    private lateinit var backend: PhoneBackend
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_live)

        val json = intent.getStringExtra("camera_json") ?: return
        pairing = Pairing.fromJson(json)
        backend = PhoneBackend(pairing)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = pairing.name

        binding.tvCameraName.text = pairing.name
        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java).apply {
                putExtra("camera_json", pairing.toJson())
            }
            startActivity(intent)
        }
        binding.btnEvents.setOnClickListener {
            val intent = Intent(this, EventsActivity::class.java).apply {
                putExtra("camera_json", pairing.toJson())
            }
            startActivity(intent)
        }

        setupPlayer()
        startPlayback()
        startStatusUpdates()
    }

    private fun setupPlayer() {
        playerView = PlayerView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        binding.playerContainer.addView(playerView!!)

        player = ExoPlayer.Builder(this).build()
        playerView!!.player = player
    }

    private fun startPlayback() {
        val mediaItem = MediaItem.fromUri(backend.liveUrl())
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(backend.authHeaders())

        val rtspMediaSourceFactory = RtspMediaSource.Factory(dataSourceFactory)
        
        val mediaSource = rtspMediaSourceFactory.createMediaSource(mediaItem)

        player!!.setMediaSource(mediaSource)
        player!!.prepare()
        player!!.playWhenReady = true

        player!!.addListener(object : androidx.media3.exoplayer.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                runOnUiThread {
                    when (playbackState) {
                        androidx.media3.common.Player.STATE_BUFFERING -> binding.tvConnectionState.text = getString(R.string.reconnecting)
                        androidx.media3.common.Player.STATE_READY -> binding.tvConnectionState.text = getString(R.string.live)
                        androidx.media3.common.Player.STATE_ENDED -> binding.tvConnectionState.text = "Ended"
                        else -> binding.tvConnectionState.text = getString(R.string.connecting)
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.exoplayer.PlaybackException) {
                runOnUiThread {
                    binding.tvConnectionState.text = getString(R.string.error_stream)
                    // Auto-reconnect
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(2000)
                        if (!isFinishing) startPlayback()
                    }
                }
            }
        })
    }

    private fun startStatusUpdates() {
        lifecycleScope.launch {
            while (!isFinishing) {
                try {
                    val status = withContext(Dispatchers.IO) { backend.status() }
                    runOnUiThread {
                        binding.tvBattery.text = "Battery: ${status.batteryPercent}%${if (status.charging) " ⚡" else ""}"
                        binding.tvStorage.text = "Free: ${com.freecam.common.util.Bytes.format(status.freeStorageMb * 1024L * 1024L)}"
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                kotlinx.coroutines.delay(10_000)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}