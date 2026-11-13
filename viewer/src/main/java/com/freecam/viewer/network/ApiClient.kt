package com.freecam.viewer.network

import com.freecam.common.model.CameraStatus
import com.freecam.common.model.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(host: String, port: Int, token: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://$host:$port/api/status"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getStatus(host: String, port: Int, token: String): CameraStatus? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://$host:$port/api/status"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = org.json.JSONObject(body)
                        CameraStatus(
                            name = json.optString("camera_name", "Unknown"),
                            version = json.optString("version", "1.0.0"),
                            uptimeSeconds = json.optLong("uptime_ms", 0) / 1000,
                            streaming = json.optBoolean("running", false),
                            resolution = json.optString("resolution", "640x480"),
                            fps = json.optInt("fps", 15),
                            bitrateKbps = json.optInt("bitrate", 500),
                            batteryPercent = json.optInt("battery", -1),
                            charging = json.optBoolean("charging", false),
                            freeStorageMb = json.optLong("free_storage_mb", 0),
                            recordingsMb = json.optLong("recordings_mb", 0),
                            motionEnabled = json.optBoolean("motion_enabled", false),
                            recordingMode = json.optString("recording_mode", "off"),
                            rtspPort = json.optInt("rtsp_port", 8554),
                            rtspPath = json.optString("rtsp_path", "live")
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getRecordings(host: String, port: Int, token: String): List<RecordingInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://$host:$port/api/recordings"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val jsonArray = org.json.JSONArray(body)
                        val list = mutableListOf<RecordingInfo>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            list.add(RecordingInfo(
                                filename = obj.optString("filename", ""),
                                path = obj.optString("path", ""),
                                size = obj.optLong("size", 0),
                                durationSec = obj.optInt("duration_sec", 0),
                                startTime = obj.optLong("start_time", obj.optLong("created_at", 0))
                            ))
                        }
                        list
                    } else emptyList()
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getEvents(host: String, port: Int, token: String): List<EventInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://$host:$port/api/events"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val jsonArray = org.json.JSONArray(body)
                        val list = mutableListOf<EventInfo>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            list.add(EventInfo(
                                timestamp = obj.optLong("timestamp", 0),
                                type = obj.optString("type", "motion"),
                                severity = obj.optString("severity", "medium"),
                                details = obj.optString("details", ""),
                                recordingPath = obj.optString("recording_path", "")
                            ))
                        }
                        list
                    } else emptyList()
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    companion object {
        fun buildRtspUrl(pairing: Pairing): String {
            return "rtsp://${pairing.host}:${pairing.rtspPort}/live"
        }
    }
}

@Serializable
data class RecordingInfo(
    val filename: String,
    val path: String,
    val size: Long,
    val durationSec: Int,
    val startTime: Long = 0
)

@Serializable
data class EventInfo(
    val timestamp: Long,
    val type: String,
    val severity: String,
    val details: String,
    val recordingPath: String
)