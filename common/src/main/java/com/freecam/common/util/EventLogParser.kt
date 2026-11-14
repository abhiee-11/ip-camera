package com.freecam.common.util

import com.freecam.common.model.MotionEvent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Reads the camera's motion event log. */
object EventLogParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the stored event log. A missing, empty or malformed log yields an
     * empty list rather than an error. Newest event first.
     */
    fun parseEventLog(content: String?): List<MotionEvent> {
        if (content.isNullOrBlank()) return emptyList()
        val parsed = runCatching { json.decodeFromString<List<MotionEvent>>(content) }.getOrNull()
            ?: return emptyList()
        return parsed
            .map { if (it.id.isBlank()) it.copy(id = UUID.randomUUID().toString()) else it }
            .sortedByDescending { it.ts }
    }
}
