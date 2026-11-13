package com.freecam.common.model

import kotlinx.serialization.Serializable

@Serializable
data class RecordingInfo(
    val file: String,
    val startTs: Long,
    val durationSec: Int,
    val sizeBytes: Long,
    val thumb: String?
)