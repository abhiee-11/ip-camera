package com.freecam.common.model

import kotlinx.serialization.Serializable

@Serializable
data class MotionEvent(
    val id: String,
    val ts: Long,
    val type: String = "motion",
    val clipFile: String?,
    val thumb: String?
)