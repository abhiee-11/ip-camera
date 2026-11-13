package com.freecam.viewer.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.freecam.viewer.R
import com.freecam.viewer.backend.PhoneBackend
import com.freecam.viewer.databinding.ActivityPlayerBinding
import com.freecam.common.model.Pairing
import com.freecam.common.model.RecordingInfo
import com.freecam.common.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var pairing: Pairing
    private lateinit var recording: RecordingInfo
    private lateinit var backend: PhoneBackend
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var playbackSpeed = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_player)

        val cameraJson = intent.getStringExtra("camera_json") ?: return
        val recordingJson = intent.getStringExtra("recording_json") ?: return

        pairing = Pairing.fromJson(cameraJson)
        recording = Json.decodeFromString(recordingJson)
        backend = PhoneBackend(pairing)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = pairing.name

        setupPlayer()
        startPlayback()
    }

    private fun setupPlayer() {
        playerView = PlayerView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            controllerHideOnTouch = true
            controllerShowTimeoutMs = 3000
        }
        binding.playerContainer.addView(playerView!!)

        player = ExoPlayer.Builder(this).build()
        playerView!!.player = player

        binding.timeBar.setPlayer(player!!)

        binding.btnPlayPause.setOnClickListener {
            if (player!!.playWhenReady) {
                player!!.playWhenReady = false
                binding.btnPlayPause.text = "▶"
            } else {
                player!!.playWhenReady = true
                binding.btnPlayPause.text = "⏸"
            }
        }

        binding.btnSkipBack.setOnClickListener {
            val newPos = kotlin.math.max(0, player!!.currentPosition - 10_000)
            player!!.seekTo(newPos)
        }

        binding.btnSkipForward.setOnClickListener {
            val newPos = kotlin.math.min(player!!.duration, player!!.currentPosition + 10_000)
            player!!.seekTo(newPos)
        }

        binding.tvSpeed.setOnClickListener {
            val speeds = floatArrayOf(1f, 2f, 4f)
            val currentIndex = speeds.indexOfFirst { it == playbackSpeed }
            val nextIndex = (currentIndex + 1) % speeds.size
            playbackSpeed = speeds[nextIndex]
            player!!.setPlaybackParameters(
                PlaybackParameters(playbackSpeed)
            )
            binding.tvSpeed.text = "${playbackSpeed.toInt()}x"
        }

        player!!.addListener(object : androidx.media3.exoplayer.Player.Listener {
            override fun onPositionDiscontinuity(reason: Int) {
                updatePositionDisplay()
            }
        })
    }

    private fun startPlayback() {
        val clipUrl = backend.clipUrl(recording)
        val mediaItem = MediaItem.fromUri(clipUrl)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(backend.authHeaders())

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        player!!.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
        player!!.prepare()
        player!!.playWhenReady = true

        // Update position display periodically
        lifecycleScope.launch {
            while (!isFinishing && player != null) {
                runOnUiThread { updatePositionDisplay() }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun updatePositionDisplay() {
        val pos = player?.currentPosition ?: 0L
        val dur = player?.duration ?: 0L
        binding.tvPosition.text = "${formatTime(pos)} / ${formatTime(dur)}"
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%d:%02d", mins, secs)
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