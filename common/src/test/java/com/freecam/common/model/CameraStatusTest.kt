package com.freecam.common.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStatusTest {

    private val json = Json { encodeDefaults = true }

    private fun sample() = CameraStatus(
        running = true,
        recording = false,
        uptimeMs = 15_877_000L,
        cameraName = "Front door",
        resolution = "640x480",
        fps = 15,
        bitrateKbps = 500,
        rtspUrl = "rtsp://freecam:abc123@100.64.0.5:8554/live",
        apiUrl = "http://100.64.0.5:8080",
        version = "1.1.0",
        host = "100.64.0.5",
        tailnet = TailnetState(true, "100.64.0.5"),
        battery = BatteryState(87, true),
        storage = StorageState(4_200_000_000L, 1_800_000_000L, 6_000_000_000L),
        motion = MotionState(true, "medium", 3, false),
        recordingMode = "motion",
        error = null
    )

    @Test
    fun encodesTheExactWireShape() {
        val encoded = json.encodeToString(sample())
        assertTrue(encoded.contains("\"uptime_ms\":15877000"))
        assertTrue(encoded.contains("\"camera_name\":\"Front door\""))
        assertTrue(encoded.contains("\"bitrate_kbps\":500"))
        assertTrue(encoded.contains("\"rtsp_url\":\"rtsp://freecam:abc123@100.64.0.5:8554/live\""))
        assertTrue(encoded.contains("\"tailnet\":{\"connected\":true,\"ip\":\"100.64.0.5\"}"))
        assertTrue(encoded.contains("\"battery\":{\"percent\":87,\"charging\":true}"))
        assertTrue(
            encoded.contains(
                "\"storage\":{\"used_bytes\":4200000000,\"free_bytes\":1800000000,\"cap_bytes\":6000000000}"
            )
        )
        assertTrue(
            encoded.contains(
                "\"motion\":{\"enabled\":true,\"sensitivity\":\"medium\",\"events_today\":3,\"available\":false}"
            )
        )
        assertTrue(encoded.contains("\"recording_mode\":\"motion\""))
        assertTrue(encoded.contains("\"error\":null"))
    }

    @Test
    fun batteryPercentNullSerializesAsNull() {
        val encoded = json.encodeToString(sample().copy(battery = BatteryState(null, false)))
        assertTrue(encoded.contains("\"battery\":{\"percent\":null,\"charging\":false}"))
    }

    @Test
    fun errorCarriesCodeAndMessage() {
        val encoded = json.encodeToString(sample().copy(error = ApiError("not_streaming", "No frame yet")))
        assertTrue(encoded.contains("\"error\":{\"code\":\"not_streaming\",\"message\":\"No frame yet\"}"))
    }

    @Test
    fun roundTrips() {
        assertEquals(sample(), json.decodeFromString<CameraStatus>(json.encodeToString(sample())))
    }
}
