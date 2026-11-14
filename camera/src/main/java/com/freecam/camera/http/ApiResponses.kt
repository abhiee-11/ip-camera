package com.freecam.camera.http

import com.freecam.common.model.ApiError
import kotlinx.serialization.json.Json

/** Bodies returned by the camera HTTP API. */
object ApiResponses {

    private val json = Json { encodeDefaults = true }

    /** {"error":{"code":"...","message":"..."}} */
    fun error(code: String, message: String): String {
        return """{"error":${json.encodeToString(ApiError.serializer(), ApiError(code, message))}}"""
    }
}
