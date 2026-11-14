package com.freecam.camera.recording

import android.content.Context
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import com.freecam.camera.stream.StreamController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives recording in segments.
 *
 * The MP4 is written by the camera library's own recorder, so the file has the
 * codec's real SPS/PPS and plays everywhere. This class only decides when a
 * segment starts and stops.
 *
 * Motion triggered recording is not available yet: the library gives no access
 * to camera frames, so the camera cannot tell motion from a still scene. Until
 * that changes the camera records continuously and reports motion as
 * unavailable rather than pretending to detect it.
 */
class RecordingManager(
    private val context: Context,
    private val settings: Settings,
    private val appLog: AppLog,
    private val controller: () -> StreamController?,
    private val onRecordingStarted: (File) -> Unit,
    private val onRecordingStopped: (File, Long, Int) -> Unit,
    private val onError: (String) -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val job = Job()
    private var running = false
    private var currentFile: File? = null
    private var currentStart = 0L
    private var rotationJob: Job? = null

    fun start() {
        if (running) return
        running = true
        appLog.log(TAG, "Recording manager started in ${settings.recordingMode} mode")
        if (settings.recordingMode == "off") return

        startSegment()

        rotationJob = scope.launch(job) {
            while (running) {
                delay(SEGMENT_MS)
                if (running && settings.recordingMode != "off") rotateSegment()
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        rotationJob?.cancel()
        stopSegment()
    }

    fun isRecording(): Boolean = currentFile != null

    private fun startSegment() {
        val stream = controller() ?: return
        if (!stream.isRunning()) return
        val directory = File(context.getExternalFilesDir(null), "recordings")
        if (!directory.exists()) directory.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(directory, "rec_$timestamp.mp4")
        if (!stream.startRecording(file)) {
            onError("Could not start recording")
            return
        }
        currentFile = file
        currentStart = System.currentTimeMillis()
        onRecordingStarted(file)
    }

    private fun stopSegment() {
        val file = currentFile ?: return
        val stream = controller()
        stream?.stopRecording()
        currentFile = null
        val durationSec = ((System.currentTimeMillis() - currentStart) / 1000).toInt()
        onRecordingStopped(file, System.currentTimeMillis(), durationSec)
    }

    private fun rotateSegment() {
        stopSegment()
        // Small gap so the muxer can finalise the previous file.
        scope.launch {
            delay(300)
            if (running) startSegment()
        }
    }

    companion object {
        private const val TAG = "RecordingManager"
        private const val SEGMENT_MS = 300_000L
    }
}
