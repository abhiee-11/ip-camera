package com.freecam.camera.data

import android.content.Context
import com.freecam.common.model.MotionEvent
import com.freecam.common.util.EventLogParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Stores the camera's event log. Events are the truth the viewer shows, so they
 * are written as the shared [MotionEvent] shape and never silently dropped.
 */
class EventStore(private val context: Context) {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val file: File get() = File(context.filesDir, "events.json")
    private val maxEvents = 1000

    @Synchronized
    fun add(type: String, severity: String = "medium", recordingPath: String? = null): MotionEvent {
        val event = MotionEvent(
            id = UUID.randomUUID().toString(),
            ts = System.currentTimeMillis(),
            severity = severity,
            recordingPath = recordingPath,
            thumbnail = null
        )
        val events = readAll().toMutableList()
        events.add(event)
        while (events.size > maxEvents) events.removeAt(0)
        write(events)
        return event
    }

    @Synchronized
    fun readAll(): List<MotionEvent> {
        return try {
            EventLogParser.parseEventLog(if (file.exists()) file.readText() else null)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun todayCount(): Int {
        val start = startOfToday()
        return readAll().count { it.ts >= start }
    }

    private fun startOfToday(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun write(events: List<MotionEvent>) {
        try {
            val temp = File(context.filesDir, "events.json.tmp")
            temp.writeText(json.encodeToString(events))
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (e: Exception) {
            // Losing the log is better than crashing the camera.
        }
    }
}
