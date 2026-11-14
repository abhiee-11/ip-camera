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
import com.freecam.common.model.MotionEvent
import com.freecam.common.model.Pairing
import com.freecam.common.model.RecordingInfo
import com.freecam.viewer.R
import com.freecam.viewer.databinding.ActivityEventsBinding
import com.freecam.viewer.databinding.ItemEventBinding
import com.freecam.viewer.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Suppress("UnsafeOptInUsageError")
class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private var pairing: Pairing? = null
    private val apiClient = ApiClient()
    private val adapter = EventAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_events)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val json = intent.getStringExtra("pairing") ?: return finish()
        pairing = Json.decodeFromString(Pairing.serializer(), json)

        setupRecyclerView()
        loadEvents()
    }

    private fun setupRecyclerView() {
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter
    }

    private fun loadEvents() {
        val pairing = this.pairing ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val events = apiClient.getEvents(pairing.host, pairing.httpPort, pairing.token)
            runOnUiThread {
                adapter.submitList(events)
                binding.tvEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    inner class EventAdapter : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {
        private var events = emptyList<MotionEvent>()

        fun submitList(newList: List<MotionEvent>) {
            events = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return EventViewHolder(binding)
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            val event = events[position]
            holder.bind(event)
        }

        override fun getItemCount(): Int = events.size

        inner class EventViewHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(event: MotionEvent) {
                binding.tvTimestamp.text = com.freecam.common.util.TimeFormat.formatFull(event.ts)
                binding.tvEventType.text = when (event.severity) {
                    "high" -> getString(R.string.event_motion)
                    "low" -> getString(R.string.event_recording_start)
                    else -> getString(R.string.event_recording_stop)
                }
                binding.tvDetails.text = event.recordingPath ?: ""

                binding.root.setOnClickListener {
                    val pairing = this@EventsActivity.pairing ?: return@setOnClickListener
                    val clipPath = event.recordingPath
                    if (!clipPath.isNullOrEmpty()) {
                        val recording = RecordingInfo(
                            path = clipPath,
                            filename = clipPath.substringAfterLast("/"),
                            size = 0,
                            durationSec = 0,
                            startTime = event.ts
                        )
                        val intent = Intent(binding.root.context, PlayerActivity::class.java).apply {
                            putExtra("pairing", Json.encodeToString(Pairing.serializer(), pairing))
                            putExtra("recording", Json.encodeToString(RecordingInfo.serializer(), recording))
                        }
                        binding.root.context.startActivity(intent)
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}