package com.freecam.common.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingInfoTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun encodesTheExactWireShape() {
        val info = RecordingInfo(
            filename = "rec_20241115_143000.mp4",
            path = "/recordings/rec_20241115_143000.mp4",
            size = 12_500_000L,
            startTime = 1_732_158_600_000L,
            durationSec = 300
        )
        val encoded = json.encodeToString(info)
        assertTrue(encoded.contains("\"filename\":\"rec_20241115_143000.mp4\""))
        assertTrue(encoded.contains("\"size\":12500000"))
        assertTrue(encoded.contains("\"start_time\":1732158600000"))
        assertTrue(encoded.contains("\"duration_sec\":300"))
        assertEquals(info, json.decodeFromString<RecordingInfo>(encoded))
    }
}
