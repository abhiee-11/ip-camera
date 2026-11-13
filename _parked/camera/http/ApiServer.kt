package com.freecam.camera.http

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.camera.record.StorageManager
import com.freecam.camera.service.CameraService
import com.freecam.common.api.ApiPaths
import com.freecam.common.model.CameraStatus
import com.freecam.common.model.MotionEvent
import com.freecam.common.model.RecordingInfo
import com.freecam.common.util.Bytes
import com.freecam.common.util.TimeFormat
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ApiServer(private val context: Context, private val settings: Settings, private val storageManager: StorageManager) : NanoHTTPD(8080) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val token = settings.token
    private val eventStore = com.freecam.camera.motion.EventStore.getInstance(context)

    override fun serve(session: NanoHTTPD.IHTTPSession): Response {
        val method = session.method
        val uri = session.uri
        val headers = session.headers

        // Log request
        AppLog.getInstance(context).log("ApiServer", "$method $uri")

        // Check auth for all except /api/ping
        if (uri != ApiPaths.PING) {
            val authHeader = headers["authorization"] ?: headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ") || !constantTimeEquals(authHeader.substring(7), token)) {
                return Response(Status.UNAUTHORIZED, MIME_JSON, Json.encodeToString(mapOf("error" to "Unauthorized")))
            }
        }

        return try {
            when {
                method == NanoHTTPD.Method.GET && uri == ApiPaths.PING -> handlePing()
                method == NanoHTTPD.Method.GET && uri == ApiPaths.STATUS -> handleStatus()
                method == NanoHTTPD.Method.GET && uri.startsWith(ApiPaths.RECORDINGS) -> handleRecordings(session)
                method == NanoHTTPD.Method.GET && uri.startsWith(ApiPaths.EVENTS) -> handleEvents(session)
                method == NanoHTTPD.Method.GET && uri.startsWith("/recordings/") -> handleFileDownload(uri, "recordings")
                method == NanoHTTPD.Method.GET && uri.startsWith("/thumbs/") -> handleFileDownload(uri, "thumbs")
                else -> Response(Status.NOT_FOUND, MIME_JSON, Json.encodeToString(mapOf("error" to "Not found")))
            }
        } catch (e: Exception) {
            AppLog.getInstance(context).logError("ApiServer", "Error handling $method $uri", e)
            Response(Status.INTERNAL_ERROR, MIME_JSON, Json.encodeToString(mapOf("error" to "Internal error")))
        }
    }

    private fun handlePing(): Response {
        return Response(Status.OK, MIME_JSON, Json.encodeToString(mapOf("ok" to true)))
    }

    private fun handleStatus(): Response {
        val addresses = getLocalAddresses()
        val tailscaleAddr = addresses.firstOrNull { it.startsWith("100.") }
        val primaryAddr = tailscaleAddr ?: addresses.firstOrNull() ?: "unknown"

        val status = CameraStatus(
            name = settings.cameraName,
            version = BuildConfig.VERSION_NAME,
            uptimeSeconds = CameraService.uptimeMillis / 1000,
            streaming = CameraService.isRunning,
            resolution = settings.resolution,
            fps = settings.frameRate,
            bitrateKbps = settings.bitrateKbps,
            batteryPercent = getBatteryLevel(),
            charging = isCharging(),
            freeStorageMb = storageManager.getFreeSpace() / (1024 * 1024),
            recordingsMb = storageManager.getUsedSpace() / (1024 * 1024),
            motionEnabled = settings.motionEnabled,
            recordingMode = settings.recordingMode,
            rtspPort = 8554,
            rtspPath = "live"
        )
        return Response(Status.OK, MIME_JSON, Json.encodeToString(status))
    }

    private fun handleRecordings(session: NanoHTTPD.IHTTPSession): Response {
        val dayParam = session.parameters["day"]?.firstOrNull()
        val files = storageManager.recordingsDir.listFiles { _, name -> name.endsWith(".mp4") } ?: emptyArray()
        
        val filtered = if (dayParam != null) {
            val dayPrefix = dayParam.replace("-", "").take(8)
            files.filter { it.name.startsWith(dayPrefix) }
        } else {
            files
        }.sortedByDescending { it.lastModified() }

        val recordings = filtered.map { file ->
            val thumbFile = File(file.parentFile, "${file.nameWithoutExtension}.jpg")
            RecordingInfo(
                file = file.name,
                startTs = parseTimestampFromFilename(file.name) ?: file.lastModified(),
                durationSec = 300, // TODO: actual duration
                sizeBytes = file.length(),
                thumb = if (thumbFile.exists()) thumbFile.name else null
            )
        }
        return Response(Status.OK, MIME_JSON, Json.encodeToString(recordings))
    }

    private fun handleEvents(session: NanoHTTPD.IHTTPSession): Response {
        val from = session.parameters["from"]?.firstOrNull()?.toLongOrNull() ?: 0
        val to = session.parameters["to"]?.firstOrNull()?.toLongOrNull() ?: TimeFormat.now()
        val events = eventStore.getEvents(from, to)
        return Response(Status.OK, MIME_JSON, Json.encodeToString(events))
    }

    private fun handleFileDownload(uri: String, prefix: String): Response {
        val fileName = uri.substring("/$prefix/".length)
        // Prevent path traversal
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return Response(Status.BAD_REQUEST, MIME_JSON, Json.encodeToString(mapOf("error" to "Invalid filename")))
        }

        val file = File(storageManager.recordingsDir, fileName)
        if (!file.exists() || !file.isFile) {
            return Response(Status.NOT_FOUND, MIME_JSON, Json.encodeToString(mapOf("error" to "File not found")))
        }

        val mimeType = if (prefix == "recordings") "video/mp4" else "image/jpeg"
        val fileInputStream = FileInputStream(file)
        
        // Support Range requests for video seeking
        val rangeHeader = session.headers["range"] ?: session.headers["Range"]
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.substring(6)
            val parts = range.split("-")
            val start = parts[0].toLongOrNull() ?: 0
            val end = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toLongOrNull() ?: file.length() - 1 else file.length() - 1
            val contentLength = end - start + 1
            
            fileInputStream.skip(start)
            val limitedStream = java.io.SequenceInputStream(
                java.io.ByteArrayInputStream(ByteArray(0)),
                fileInputStream
            )
            
            return Response(Status.PARTIAL_CONTENT, mimeType, limitedStream, contentLength).apply {
                addHeader("Content-Range", "bytes $start-$end/${file.length()}")
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Length", contentLength.toString())
            }
        }

        return Response(Status.OK, mimeType, fileInputStream, file.length()).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Length", file.length().toString())
        }
    }

    private fun getLocalAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            NetworkInterface.networkInterfaces.asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
                .map { android.text.format.Formatter.formatIpAddress(it.hashCode()) }
                .forEach { result.add(it) }
        } catch (e: Exception) {
            AppLog.getInstance(context).logError("ApiServer", "Failed to get network interfaces", e)
        }
        return result.sortedBy { if (it.startsWith("100.")) 0 else 1 }
    }

    private fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra("level", -1) ?: -1
        val scale = intent?.getIntExtra("scale", -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private fun isCharging(): Boolean {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra("status", -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
               status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    private fun parseTimestampFromFilename(name: String): Long? {
        // Format: YYYYMMDD_HHMMSS_mode.mp4
        val parts = name.split("_")
        if (parts.size >= 2) {
            val datePart = parts[0]
            val timePart = parts[1].take(6)
            return try {
                val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                sdf.parse("$datePart$timePart")?.time
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    companion object {
        const val MIME_JSON = "application/json"
    }
}