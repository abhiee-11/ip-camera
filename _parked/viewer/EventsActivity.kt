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
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.freecam.viewer.R
import com.freecam.viewer.backend.PhoneBackend
import com.freecam.viewer.databinding.ActivityEventsBinding
import com.freecam.viewer.databinding.ItemEventBinding
import com.freecam.common.model.MotionEvent
import com.freecam.common.model.Pairing
import com.freecam.common.model.RecordingInfo
import com.freecam.common.util.Bytes
import com.freecam.common.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private lateinit var pairing: Pairing
    private lateinit var backend: PhoneBackend
    private var events: List<MotionEvent> = emptyList()

    private val eventAdapter = EventAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_events)

        val json = intent.getStringExtra("camera_json") ?: return
        pairing = Pairing.fromJson(json)
        backend = PhoneBackend(pairing)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.events_title)

        binding.rvEvents.adapter = eventAdapter
        loadEvents()
    }

    private fun loadEvents() {
        val now = TimeFormat.now()
        val weekAgo = now - 7 * 24L * 60 * 60 * 1000

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { backend.events(weekAgo, now) }
                runOnUiThread {
                    events = result
                    eventAdapter.submitList(result)
                    binding.tvEmptyEvents.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvEmptyEvents.visibility = View.VISIBLE
                    binding.tvEmptyEvents.text = "Failed to load events"
                }
            }
        }
    }

    inner class EventAdapter : RecyclerView.Adapter<EventViewHolder>() {
        private var items: List<MotionEvent> = emptyList()

        fun submitList(list: List<MotionEvent>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return EventViewHolder(binding)
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    inner class EventViewHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: MotionEvent) {
            val time = TimeFormat.toDisplayString(event.ts)
            binding.tvTime.text = time
            binding.tvType.text = event.type.capitalize()

            event.thumb?.let { thumbName ->
                // Create a dummy RecordingInfo to get thumb URL
                val rec = RecordingInfo(
                    file = event.clipFile ?: "",
                    startTs = event.ts,
                    durationSec = 0,
                    sizeBytes = 0,
                    thumb = event.thumb
                )
                val thumbUrl = backend.thumbUrl(rec)
                thumbUrl?.let { Glide.with(binding.ivThumb).load(it).into(binding.ivThumb) }
            }

            binding.root.setOnClickListener {
                if (event.clipFile != null) {
                    val rec = RecordingInfo(
                        file = event.clipFile!!,
                        startTs = event.ts,
                        durationSec = 0,
                        sizeBytes = 0,
                        thumb = event.thumb
                    )
                    val intent = Intent(this@EventsActivity, PlayerActivity::class.java).apply {
                        putExtra("camera_json", pairing.toJson())
                        putExtra("recording_json", Json.encodeToString(rec))
                    }
                    startActivity(intent)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}