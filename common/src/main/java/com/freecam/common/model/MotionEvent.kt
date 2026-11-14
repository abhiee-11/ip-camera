package com.freecam.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MotionEvent(
    val id: String = "",
    val ts: Long = 0L,
    val severity: String = "medium",
    @SerialName("recording_path") val recordingPath: String? = null,
    val thumbnail: String? = null
)
