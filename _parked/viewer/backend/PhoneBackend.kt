package com.freecam.viewer.backend

import com.freecam.common.api.CameraBackend
import com.freecam.common.model.CameraStatus
import com.freecam.common.model.MotionEvent
import com.freecam.common.model.RecordingInfo
import com.freecam.common.model.Pairing
import com.freecam.common.util.UrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class PhoneBackend(private val pairing: Pairing) : CameraBackend {

    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "http://${pairing.host}:${pairing.httpPort}"
    private val authHeader = "Bearer ${pairing.token}"

    override suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl${com.freecam.common.api.ApiPaths.PING}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun status(): CameraStatus = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl${com.freecam.common.api.ApiPaths.STATUS}")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", authHeader)
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val input = conn.inputStream.bufferedReader().use { it.readText() }
        json.decodeFromString(input)
    }

    override suspend fun recordings(day: String?): List<RecordingInfo> = withContext(Dispatchers.IO) {
        val query = if (day != null) "?day=$day" else ""
        val url = URL("$baseUrl${com.freecam.common.api.ApiPaths.RECORDINGS}$query")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", authHeader)
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val input = conn.inputStream.bufferedReader().use { it.readText() }
        json.decodeFromString(input)
    }

    override suspend fun events(from: Long, to: Long): List<MotionEvent> = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl${com.freecam.common.api.ApiPaths.EVENTS}?from=$from&to=$to")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", authHeader)
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val input = conn.inputStream.bufferedReader().use { it.readText() }
        json.decodeFromString(input)
    }

    override fun liveUrl(): String = UrlBuilder.buildRtspUrl(pairing.host, pairing.rtspPort, pairing.path)

    override fun clipUrl(rec: RecordingInfo): String = UrlBuilder.buildClipUrl(pairing.host, pairing.httpPort, rec.file)

    override fun thumbUrl(rec: RecordingInfo): String? = rec.thumb?.let { UrlBuilder.buildThumbUrl(pairing.host, pairing.httpPort, it) }

    override fun authHeaders(): Map<String, String> = mapOf("Authorization" to authHeader)
}