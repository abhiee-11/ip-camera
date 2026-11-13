package com.freecam.camera.motion

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.common.model.MotionEvent
import com.freecam.common.util.TimeFormat
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class EventStore(private val context: Context) {

    private val maxDays = 7
    private val eventsFile: File
    private val events = ConcurrentLinkedQueue<MotionEvent>()

    init {
        eventsFile = File(context.filesDir, "events.json")
        load()
    }

    private fun load() {
        if (eventsFile.exists()) {
            try {
                val json = eventsFile.readText()
                val list = Json.decodeFromString<List<MotionEvent>>(json)
                val cutoff = TimeFormat.now() - maxDays * 24L * 60 * 60 * 1000
                list.filter { it.ts >= cutoff }.forEach { events.add(it) }
            } catch (e: Exception) {
                AppLog.getInstance(context).logError("EventStore", "Failed to load events", e)
            }
        }
    }

    private fun save() {
        try {
            val json = Json.encodeToString(events.toList())
            eventsFile.writeText(json)
        } catch (e: Exception) {
            AppLog.getInstance(context).logError("EventStore", "Failed to save events", e)
        }
    }

    fun addEvent(clipFile: String? = null, thumb: String? = null): MotionEvent {
        val event = MotionEvent(
            id = java.util.UUID.randomUUID().toString(),
            ts = TimeFormat.now(),
            clipFile = clipFile,
            thumb = thumb
        )
        events.add(event)
        // Keep only last 7 days
        val cutoff = TimeFormat.now() - maxDays * 24L * 60 * 60 * 1000
        while (events.peek()?.ts ?: Long.MAX_VALUE < cutoff) {
            events.poll()
        }
        save()
        return event
    }

    fun getEvents(from: Long, to: Long): List<MotionEvent> {
        return events.toList().filter { it.ts >= from && it.ts <= to }.reversed()
    }

    fun getTodayCount(): Int {
        val todayStart = TimeFormat.parseDay(TimeFormat.toDayString(TimeFormat.now())) ?: 0L
        val tomorrowStart = todayStart + 24 * 60 * 60 * 1000
        return getEvents(todayStart, tomorrowStart).size
    }

    fun getAllEvents(): List<MotionEvent> = events.toList().reversed()

    companion object {
        private var instance: EventStore? = null
        fun getInstance(context: Context): EventStore {
            return instance ?: EventStore(context.applicationContext).also { instance = it }
        }
    }
}