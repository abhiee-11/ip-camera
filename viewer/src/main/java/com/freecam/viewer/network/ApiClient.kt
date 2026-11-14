package com.freecam.viewer.network

import com.freecam.common.api.ApiPaths
import com.freecam.common.model.CameraStatus
import com.freecam.common.model.MotionEvent
import com.freecam.common.model.Pairing
import com.freecam.common.model.RecordingInfo
import com.freecam.common.util.StreamUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Talks to the camera API. Every call carries the bearer token, and every
 * response is decoded into the shared models so the viewer and the camera can
 * never drift apart.
 */
class ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun testConnection(host: String, port: Int, token: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${baseUrl(host, port)}${ApiPaths.STATUS}")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getStatus(host: String, port: Int, token: String): CameraStatus? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${baseUrl(host, port)}${ApiPaths.STATUS}")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        json.decodeFromString<CameraStatus>(body)
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getRecordings(host: String, port: Int, token: String): List<RecordingInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${baseUrl(host, port)}${ApiPaths.RECORDINGS}")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        json.decodeFromString<List<RecordingInfo>>(body)
                    } else emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getEvents(host: String, port: Int, token: String): List<MotionEvent> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${baseUrl(host, port)}${ApiPaths.EVENTS}")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        json.decodeFromString<List<MotionEvent>>(body)
                    } else emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    companion object {
        private fun baseUrl(host: String, port: Int): String = "http://$host:$port"

        /** Live URL with the RTSP credentials the camera expects. */
        fun buildRtspUrl(pairing: Pairing): String = StreamUrls.buildRtspUrl(
            pairing.host,
            pairing.rtspPort,
            pairing.path,
            pairing.rtspUser,
            pairing.token
        )

        fun buildHttpUrl(host: String, port: Int, path: String): String =
            StreamUrls.buildHttpUrl(host, port, path)

        fun buildSnapshotUrl(host: String, port: Int): String =
            StreamUrls.buildHttpUrl(host, port, ApiPaths.SNAPSHOT)
    }
}
