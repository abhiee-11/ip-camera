package com.freecam.common.model

import kotlinx.serialization.Serializable

@Serializable
data class CameraStatus(
    val name: String,
    val version: String,
    val uptimeSeconds: Long,
    val streaming: Boolean,
    val resolution: String,
    val fps: Int,
    val bitrateKbps: Int,
    val batteryPercent: Int,
    val charging: Boolean,
    val freeStorageMb: Long,
    val recordingsMb: Long,
    val motionEnabled: Boolean,
    val recordingMode: String,
    val rtspPort: Int,
    val rtspPath: String
)