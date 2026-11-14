package com.freecam.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The single status contract served by the camera over /api/status and consumed
 * by the viewer. Field names here are the wire format.
 */
@Serializable
data class CameraStatus(
    val running: Boolean = false,
    val recording: Boolean = false,
    @SerialName("uptime_ms") val uptimeMs: Long = 0L,
    @SerialName("camera_name") val cameraName: String = "",
    val resolution: String = "",
    val fps: Int = 0,
    @SerialName("bitrate_kbps") val bitrateKbps: Int = 0,
    @SerialName("rtsp_url") val rtspUrl: String = "",
    @SerialName("api_url") val apiUrl: String = "",
    val version: String = "",
    val host: String? = null,
    val tailnet: TailnetState = TailnetState(),
    val battery: BatteryState = BatteryState(),
    val storage: StorageState = StorageState(),
    val motion: MotionState = MotionState(),
    @SerialName("recording_mode") val recordingMode: String = "",
    val error: ApiError? = null
)

@Serializable
data class TailnetState(
    val connected: Boolean = false,
    val ip: String? = null
)

@Serializable
data class BatteryState(
    val percent: Int? = null,
    val charging: Boolean = false
)

@Serializable
data class StorageState(
    @SerialName("used_bytes") val usedBytes: Long = 0L,
    @SerialName("free_bytes") val freeBytes: Long = 0L,
    @SerialName("cap_bytes") val capBytes: Long = 0L
)

@Serializable
data class MotionState(
    val enabled: Boolean = false,
    val sensitivity: String = "medium",
    @SerialName("events_today") val eventsToday: Int = 0,
    val available: Boolean = false
)
