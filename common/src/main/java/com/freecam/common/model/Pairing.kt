package com.freecam.common.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Pairing(
    val v: Int = 1,
    val name: String,
    val host: String,
    val rtspPort: Int = 8554,
    val httpPort: Int = 8080,
    val path: String = "live",
    val token: String,
    val mode: String = "local"
) {
    fun toJson(): String = Json.encodeToString(Pairing.serializer(), this)
    
    companion object {
        fun fromJson(json: String): Pairing = Json.decodeFromString(Pairing.serializer(), json)
    }
}