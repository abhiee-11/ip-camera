package com.freecam.common.util

import kotlinx.serialization.json.Json

object Json {
    val default = Json { ignoreUnknownKeys = true }
}