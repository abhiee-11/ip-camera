package com.freecam.common.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionEventTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun encodesTheExactWireShape() {
        val event = MotionEvent(
            id = "e1",
            ts = 1_732_158_600_000L,
            severity = "high",
            recordingPath = "/recordings/rec_20241115_143000.mp4",
            thumbnail = null
        )
        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains("\"id\":\"e1\""))
        assertTrue(encoded.contains("\"ts\":1732158600000"))
        assertTrue(encoded.contains("\"severity\":\"high\""))
        assertTrue(encoded.contains("\"recording_path\":\"/recordings/rec_20241115_143000.mp4\""))
        assertTrue(encoded.contains("\"thumbnail\":null"))
        assertEquals(event, json.decodeFromString<MotionEvent>(encoded))
    }

    @Test
    fun missingRecordingPathStaysNull() {
        val decoded = json.decodeFromString<MotionEvent>("""{"id":"e2","ts":1,"severity":"low"}""")
        assertNull(decoded.recordingPath)
        assertNull(decoded.thumbnail)
    }
}
