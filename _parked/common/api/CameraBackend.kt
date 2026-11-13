package com.freecam.common.api

import com.freecam.common.model.CameraStatus
import com.freecam.common.model.MotionEvent
import com.freecam.common.model.RecordingInfo

interface CameraBackend {
    suspend fun ping(): Boolean
    suspend fun status(): CameraStatus
    suspend fun recordings(day: String?): List<RecordingInfo>
    suspend fun events(from: Long, to: Long): List<MotionEvent>
    fun liveUrl(): String
    fun clipUrl(rec: RecordingInfo): String
    fun thumbUrl(rec: RecordingInfo): String?
    fun authHeaders(): Map<String, String>
}