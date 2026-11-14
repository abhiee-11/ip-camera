package com.freecam.common.model

import kotlinx.serialization.Serializable

/** Error envelope used by every non-2xx API response. */
@Serializable
data class ApiError(
    val code: String,
    val message: String
)
