package com.freecam.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecordingInfo(
    val filename: String,
    val path: String,
    val size: Long,
    @SerialName("start_time") val startTime: Long,
    @SerialName("duration_sec") val durationSec: Int
)
