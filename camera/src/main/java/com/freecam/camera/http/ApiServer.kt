package com.freecam.camera.http

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ApiServer(
    private val context: Context,
    private val settings: Settings,
    private val appLog: AppLog,
    private val onStatus: () -> String,
    private val onRecordings: () -> List<RecordingInfo>,
    private val onEvents: (Long, Long) -> List<MotionEvent>,
    port: Int = Settings.apiPort
) : NanoHTTPD(port) {

    private val TAG = "ApiServer"

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (!authenticate(session)) {
            return newFixedLengthResponse(Status.UNAUTHORIZED, MIME_JSON, """{"error":"unauthorized"}""")
        }

        return when {
            uri == "/api/status" && method == NanoHTTPD.Method.GET -> handleStatus()
            uri == "/api/recordings" && method == NanoHTTPD.Method.GET -> handleRecordings()
            uri == "/api/events" && method == NanoHTTPD.Method.GET -> handleEvents(session)
            uri.startsWith("/recordings/") && method == NanoHTTPD.Method.GET -> handleRecordingFile(uri, session)
            else -> newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, """{"error":"not found"}""")
        }
    }

    private fun authenticate(session: IHTTPSession): Boolean {
        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"] ?: return false
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) return false
        val token = authHeader.substring(7)
        return token == settings.token
    }

    private fun handleStatus(): Response {
        val statusJson = onStatus()
        return newFixedLengthResponse(Status.OK, MIME_JSON, statusJson)
    }

    private fun handleRecordings(): Response {
        val recordings = onRecordings()
        val json = recordings.map { it.toJson() }.joinToString(",", "[", "]")
        return newFixedLengthResponse(Status.OK, MIME_JSON, json)
    }

    private fun handleEvents(session: IHTTPSession): Response {
        val params = session.parms
        val from = params["from"]?.toLongOrNull() ?: 0
        val to = params["to"]?.toLongOrNull() ?: System.currentTimeMillis()
        val events = onEvents(from, to)
        val json = events.map { it.toJson() }.joinToString(",", "[", "]")
        return newFixedLengthResponse(Status.OK, MIME_JSON, json)
    }

    private fun handleRecordingFile(uri: String, session: IHTTPSession): Response {
        val filename = uri.substring("/recordings/".length)
        val decodedName = URLDecoder.decode(filename, StandardCharsets.UTF_8.name())

        if (decodedName.contains("..") || decodedName.contains("/") || decodedName.contains("\\")) {
            return newFixedLengthResponse(Status.FORBIDDEN, MIME_JSON, """{"error":"path traversal"}""")
        }

        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        val file = File(recordingsDir, decodedName)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, """{"error":"not found"}""")
        }

        try {
            val rangeHeader = session.headers["range"] ?: session.headers["Range"]
            val fileLength = file.length()
            var start = 0L
            var end = fileLength - 1

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = rangeHeader.substring(6)
                val parts = range.split("-")
                if (parts.size == 2) {
                    start = parts[0].toLongOrNull() ?: 0
                    end = parts[1].toLongOrNull() ?: fileLength - 1
                }
            }

            if (start > end || start >= fileLength) {
                return newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, MIME_JSON, """{"error":"invalid range"}""")
            }

            val contentLength = end - start + 1
            val status = if (rangeHeader != null) Status.PARTIAL_CONTENT else Status.OK

            // Read file portion into memory (using ISO-8859-1 to preserve byte values)
            val fis = FileInputStream(file)
            fis.skip(start)
            val buffer = ByteArray(contentLength.toInt())
            var totalRead = 0
            while (totalRead < buffer.size) {
                val read = fis.read(buffer, totalRead, buffer.size - totalRead)
                if (read == -1) break
                totalRead += read
            }
            fis.close()

            val content = String(buffer, StandardCharsets.ISO_8859_1)
            val response = newFixedLengthResponse(status, MIME_MP4, content)
            response.addHeader("Accept-Ranges", "bytes")
            if (rangeHeader != null) {
                response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            }
            response.addHeader("Content-Length", contentLength.toString())
            return response
        } catch (e: IOException) {
            appLog.logError(TAG, "Error serving file $decodedName", e)
            return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, """{"error":"server error"}""")
        }
    }

    companion object {
        const val MIME_JSON = "application/json"
        const val MIME_MP4 = "video/mp4"
    }
}

data class RecordingInfo(
    val filename: String,
    val path: String,
    val size: Long,
    val createdAt: Long,
    val durationSec: Int
) {
    fun toJson(): String {
        return """{"filename":"$filename","path":"$path","size":$size,"created_at":$createdAt,"duration_sec":$durationSec}"""
    }
}

data class MotionEvent(
    val timestamp: Long,
    val severity: String
) {
    fun toJson(): String {
        return """{"timestamp":$timestamp,"severity":"$severity"}"""
    }
}