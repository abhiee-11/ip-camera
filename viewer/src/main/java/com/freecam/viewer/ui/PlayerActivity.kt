package com.freecam.viewer.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.freecam.common.model.Pairing
import com.freecam.viewer.R
import com.freecam.viewer.databinding.ActivityPlayerBinding
import com.freecam.viewer.network.ApiClient
import com.freecam.viewer.network.RecordingInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var pairing: Pairing? = null
    private val apiClient = ApiClient()
    private var isLive = true
    private var playbackRecording: RecordingInfo? = null
    
    // Controller views
    private lateinit var btnSkipBack: ImageButton
    private lateinit var btnSkipForward: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnSpeed: Button
    private lateinit var btnHistory: Button
    private lateinit var btnEvents: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_player)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val json = intent.getStringExtra("pairing") ?: return finish()
        pairing = Json.decodeFromString(Pairing.serializer(), json)

        val recordingJson = intent.getStringExtra("recording")
        if (recordingJson != null) {
            isLive = false
            playbackRecording = Json.decodeFromString(RecordingInfo.serializer(), recordingJson)
        }

        setupPlayer()
        setupControls()
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer
        
        // Get controller views after layout
        binding.playerView.post {
            initControllerViews()
        }

        if (isLive) {
            playLive()
        } else {
            playRecording()
        }

        exoPlayer?.playWhenReady = true
    }

    private fun initControllerViews() {
        btnSkipBack = binding.playerView.findViewById(R.id.btnSkipBack)
        btnSkipForward = binding.playerView.findViewById(R.id.btnSkipForward)
        btnPlayPause = binding.playerView.findViewById(R.id.exo_play_pause)
        btnSpeed = binding.playerView.findViewById(R.id.btnSpeed)
        btnHistory = binding.playerView.findViewById(R.id.btnHistory)
        btnEvents = binding.playerView.findViewById(R.id.btnEvents)
    }

    private fun playLive() {
        val pairing = this.pairing ?: return
        val rtspUrl = ApiClient.buildRtspUrl(pairing)
        
        @OptIn(UnstableApi::class)
        val mediaSource = RtspMediaSource.Factory()
            .setUserAgent("FreeCam Viewer")
            .createMediaSource(MediaItem.fromUri(rtspUrl))
        
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
        
        supportActionBar?.title = pairing.name
        binding.tvMode.text = getString(R.string.live)
        binding.tvMode.setTextColor(0xFF03DAC5.toInt())
    }

    private fun playRecording() {
        val pairing = this.pairing ?: return
        val recording = playbackRecording ?: return
        
        val url = "http://${pairing.host}:${pairing.httpPort}${recording.path}"
        
        val mediaItem = MediaItem.fromUri(url)
            .buildUpon()
            .setMediaId(recording.filename)
            .build()
        
        val mediaSource = DefaultMediaSourceFactory(this).createMediaSource(mediaItem)
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
        
        supportActionBar?.title = pairing.name
        binding.tvMode.text = getString(R.string.history_title)
        binding.tvMode.setTextColor(0xFFFFFFFF.toInt())
    }

    private fun setupControls() {
        btnSkipBack.setOnClickListener { seekRelative(-10000) }
        btnSkipForward.setOnClickListener { seekRelative(10000) }
        btnPlayPause.setOnClickListener { 
            exoPlayer?.playWhenReady = !(exoPlayer?.playWhenReady ?: false)
        }
        btnSpeed.setOnClickListener { showSpeedMenu() }
        
        if (isLive) {
            btnHistory.setOnClickListener {
                val intent = Intent(this, HistoryActivity::class.java).apply {
                    putExtra("pairing", Json.encodeToString(Pairing.serializer(), pairing!!))
                }
                startActivity(intent)
            }
            btnEvents.setOnClickListener {
                val intent = Intent(this, EventsActivity::class.java).apply {
                    putExtra("pairing", Json.encodeToString(Pairing.serializer(), pairing!!))
                }
                startActivity(intent)
            }
        } else {
            btnHistory.visibility = View.GONE
            btnEvents.visibility = View.GONE
        }
    }

    private fun seekRelative(ms: Long) {
        exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) + ms)
    }

    private fun showSpeedMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(this, btnSpeed)
        popup.menuInflater.inflate(R.menu.speed_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            val speed = when (item.itemId) {
                R.id.speed_1x -> 1.0f
                R.id.speed_2x -> 2.0f
                R.id.speed_4x -> 4.0f
                else -> 1.0f
            }
            val currentParams = exoPlayer?.playbackParameters
            exoPlayer?.playbackParameters = currentParams?.withSpeed(speed) ?: androidx.media3.common.PlaybackParameters(speed)
            btnSpeed.text = when (speed) {
                1.0f -> getString(R.string.speed_1x)
                2.0f -> getString(R.string.speed_2x)
                4.0f -> getString(R.string.speed_4x)
                else -> getString(R.string.speed_1x)
            }
            true
        }
        popup.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.player_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_fullscreen -> {
                // Toggle fullscreen
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}