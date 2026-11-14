package com.freecam.viewer.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.freecam.common.model.Pairing
import com.freecam.common.model.RecordingInfo
import com.freecam.viewer.R
import com.freecam.viewer.databinding.ActivityHistoryBinding
import com.freecam.viewer.databinding.ItemRecordingBinding
import com.freecam.viewer.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private var pairing: Pairing? = null
    private val apiClient = ApiClient()
    private val adapter = RecordingAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_history)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val json = intent.getStringExtra("pairing") ?: return finish()
        pairing = Json.decodeFromString(Pairing.serializer(), json)

        setupRecyclerView()
        loadRecordings()
    }

    private fun setupRecyclerView() {
        binding.rvRecordings.layoutManager = LinearLayoutManager(this)
        binding.rvRecordings.adapter = adapter
    }

    private fun loadRecordings() {
        val pairing = this.pairing ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val recordings = apiClient.getRecordings(pairing.host, pairing.httpPort, pairing.token)
            runOnUiThread {
                adapter.submitList(recordings)
                binding.tvEmpty.visibility = if (recordings.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    inner class RecordingAdapter : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {
        private var recordings = emptyList<RecordingInfo>()

        fun submitList(newList: List<RecordingInfo>) {
            recordings = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
            val binding = ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RecordingViewHolder(binding)
        }

        override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
            val recording = recordings[position]
            holder.bind(recording)
        }

        override fun getItemCount(): Int = recordings.size

        inner class RecordingViewHolder(private val binding: ItemRecordingBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(recording: RecordingInfo) {
                binding.tvFilename.text = recording.filename
                binding.tvDuration.text = getString(R.string.recording_duration, com.freecam.common.util.TimeFormat.formatDuration(recording.durationSec * 1000L))
                binding.tvSize.text = getString(R.string.recording_size, com.freecam.common.util.Bytes.format(recording.size))
                
                binding.root.setOnClickListener {
                    val pairing = this@HistoryActivity.pairing ?: return@setOnClickListener
                    val intent = Intent(binding.root.context, PlayerActivity::class.java).apply {
                        putExtra("pairing", Json.encodeToString(Pairing.serializer(), pairing))
                        putExtra("recording", Json.encodeToString(RecordingInfo.serializer(), recording))
                    }
                    binding.root.context.startActivity(intent)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}