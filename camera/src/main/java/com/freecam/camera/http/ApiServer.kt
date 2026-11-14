package com.freecam.camera.http

import android.content.Context
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.common.api.ApiPaths
import com.freecam.common.model.CameraStatus
import com.freecam.common.model.MotionEvent
import com.freecam.common.model.RecordingInfo
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * The camera's HTTP API. Every endpoint needs the bearer token; errors use a
 * consistent envelope so the viewer can say what went wrong.
 */
class ApiServer(
    private val context: Context,
    private val settings: Settings,
    private val appLog: AppLog,
    private val onStatus: () -> CameraStatus,
    private val onRecordings: () -> List<RecordingInfo>,
    private val onEvents: (Long, Long) -> List<MotionEvent>,
    private val onSnapshot: () -> ByteArray?,
    port: Int = Settings.apiPort
) : NanoHTTPD(port) {

    private val json = Json { encodeDefaults = true }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (!authenticate(session)) {
            return error(Status.UNAUTHORIZED, "unauthorized", "Missing or invalid bearer token")
        }

        return when {
            uri == ApiPaths.STATUS && method == NanoHTTPD.Method.GET -> handleStatus()
            uri == ApiPaths.SNAPSHOT && method == NanoHTTPD.Method.GET -> handleSnapshot()
            uri == ApiPaths.RECORDINGS && method == NanoHTTPD.Method.GET -> handleRecordings()
            uri == ApiPaths.EVENTS && method == NanoHTTPD.Method.GET -> handleEvents(session)
            uri.startsWith(ApiPaths.RECORDING_FILE) && method == NanoHTTPD.Method.GET ->
                handleRecordingFile(uri, session)
            else -> error(Status.NOT_FOUND, "not_found", "Unknown endpoint")
        }
    }

    private fun authenticate(session: IHTTPSession): Boolean {
        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"] ?: return false
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) return false
        return authHeader.substring(7) == settings.token
    }

    private fun error(status: Status, code: String, message: String): Response {
        return newFixedLengthResponse(status, MIME_JSON, ApiResponses.error(code, message))
    }

    private fun handleStatus(): Response {
        return newFixedLengthResponse(Status.OK, MIME_JSON, json.encodeToString(onStatus()))
    }

    private fun handleSnapshot(): Response {
        val bytes = onSnapshot()
            ?: return error(Status.NOT_FOUND, "not_streaming", "No frame is available yet")
        return newFixedLengthResponse(
            Status.OK,
            MIME_JPEG,
            java.io.ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
    }

    private fun handleRecordings(): Response {
        return newFixedLengthResponse(
            Status.OK,
            MIME_JSON,
            json.encodeToString(onRecordings())
        )
    }

    private fun handleEvents(session: IHTTPSession): Response {
        val params = session.parms
        val from = params["from"]?.toLongOrNull() ?: 0L
        val to = params["to"]?.toLongOrNull() ?: System.currentTimeMillis()
        val events = onEvents(from, to)
        return newFixedLengthResponse(Status.OK, MIME_JSON, json.encodeToString(events))
    }

    private fun handleRecordingFile(uri: String, session: IHTTPSession): Response {
        val filename = uri.substring(ApiPaths.RECORDING_FILE.length)
        val decodedName = try {
            URLDecoder.decode(filename, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            return error(Status.FORBIDDEN, "path_traversal", "Invalid file name")
        }

        if (decodedName.contains("..") || decodedName.contains("/") || decodedName.contains("\\")) {
            return error(Status.FORBIDDEN, "path_traversal", "Invalid file name")
        }

        val file = File(recordingsDir(), decodedName)
        if (!file.exists() || !file.isFile) {
            return error(Status.NOT_FOUND, "not_found", "No such recording")
        }

        return try {
            val fileLength = file.length()
            var start = 0L
            var end = fileLength - 1
            val rangeHeader = session.headers["range"] ?: session.headers["Range"]

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = rangeHeader.substring(6)
                val parts = range.split("-")
                if (parts.size == 2) {
                    start = parts[0].toLongOrNull() ?: 0L
                    end = parts[1].toLongOrNull() ?: (fileLength - 1)
                }
            }

            if (fileLength == 0L || start >= fileLength || start > end) {
                return error(Status.RANGE_NOT_SATISFIABLE, "invalid_range", "Invalid range")
            }

            val contentLength = end - start + 1
            val status = if (rangeHeader != null) Status.PARTIAL_CONTENT else Status.OK

            val stream = FileInputStream(file)
            if (start > 0) stream.skip(start)

            // Streamed, never loaded into memory: clips are tens of megabytes
            // and this phone has 1 GB of RAM.
            val response = newFixedLengthResponse(status, MIME_MP4, stream, contentLength)
            response.addHeader("Accept-Ranges", "bytes")
            if (rangeHeader != null) {
                response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            }
            response
        } catch (e: IOException) {
            appLog.logError(TAG, "Error serving file $decodedName", e)
            error(Status.INTERNAL_ERROR, "server_error", "Unexpected server error")
        }
    }

    private fun recordingsDir(): File = File(context.getExternalFilesDir(null), "recordings")

    companion object {
        private const val TAG = "ApiServer"
        const val MIME_JSON = "application/json"
        const val MIME_MP4 = "video/mp4"
        const val MIME_JPEG = "image/jpeg"
    }
}
