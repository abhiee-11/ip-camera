package com.freecam.viewer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.freecam.viewer.R
import com.freecam.viewer.backend.PhoneBackend
import com.freecam.viewer.databinding.ActivityHistoryBinding
import com.freecam.viewer.databinding.ItemDayChipBinding
import com.freecam.viewer.databinding.ItemRecordingBinding
import com.freecam.common.model.Pairing
import com.freecam.common.model.RecordingInfo
import com.freecam.common.util.Bytes
import com.freecam.common.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var pairing: Pairing
    private lateinit var backend: PhoneBackend
    private var selectedDay: String? = null
    private var recordings: List<RecordingInfo> = emptyList()

    private val dayAdapter = DayAdapter()
    private val recordingAdapter = RecordingAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_history)

        val json = intent.getStringExtra("camera_json") ?: return
        pairing = Pairing.fromJson(json)
        backend = PhoneBackend(pairing)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_title)

        binding.rvDays.adapter = dayAdapter
        binding.rvDays.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecordings.adapter = recordingAdapter

        // Generate last 7 days
        val days = (0..6).map { i ->
            val ts = System.currentTimeMillis() - i * 24L * 60 * 60 * 1000
            TimeFormat.toDayString(ts)
        }
        dayAdapter.submitList(days)
        if (days.isNotEmpty()) {
            selectedDay = days[0]
            loadRecordings(selectedDay!!)
        }
    }

    private fun loadRecordings(day: String) {
        binding.tvEmptyRecordings.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { backend.recordings(day) }
                runOnUiThread {
                    recordings = result
                    recordingAdapter.submitList(result)
                    binding.tvEmptyRecordings.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvEmptyRecordings.visibility = View.VISIBLE
                    binding.tvEmptyRecordings.text = "Failed to load recordings"
                }
            }
        }
    }

    inner class DayAdapter : RecyclerView.Adapter<DayViewHolder>() {
        private var items: List<String> = emptyList()

        fun submitList(list: List<String>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val binding = ItemDayChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return DayViewHolder(binding)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    inner class DayViewHolder(private val binding: ItemDayChipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(day: String) {
            val display = TimeFormat.toDisplayString(TimeFormat.parseDay(day) ?: 0L).split(",")[0]
            binding.chipDay.text = display
            binding.chipDay.isChecked = (day == selectedDay)
            binding.chipDay.setOnClickListener {
                selectedDay = day
                dayAdapter.notifyDataSetChanged()
                loadRecordings(day)
            }
        }
    }

    inner class RecordingAdapter : RecyclerView.Adapter<RecordingViewHolder>() {
        private var items: List<RecordingInfo> = emptyList()

        fun submitList(list: List<RecordingInfo>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
            val binding = ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RecordingViewHolder(binding)
        }

        override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    inner class RecordingViewHolder(private val binding: ItemRecordingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rec: RecordingInfo) {
            val startTime = TimeFormat.toDisplayString(rec.startTs)
            binding.tvTime.text = startTime
            binding.tvDuration.text = getString(R.string.recording_duration, formatDuration(rec.durationSec))
            binding.tvSize.text = getString(R.string.recording_size, Bytes.format(rec.sizeBytes))

            rec.thumb?.let { thumbName ->
                val thumbUrl = backend.thumbUrl(rec)
                thumbUrl?.let { Glide.with(binding.ivThumb).load(it).into(binding.ivThumb) }
            }

            binding.root.setOnClickListener {
                val intent = Intent(this@HistoryActivity, PlayerActivity::class.java).apply {
                    putExtra("camera_json", pairing.toJson())
                    putExtra("recording_json", Json.encodeToString(rec))
                }
                startActivity(intent)
            }
        }

        private fun formatDuration(seconds: Int): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return String.format("%d:%02d", mins, secs)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}