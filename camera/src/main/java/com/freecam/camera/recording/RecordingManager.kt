package com.freecam.camera.recording

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RecordingManager(
    private val context: Context,
    private val settings: Settings,
    private val onRecordingStarted: (String) -> Unit,
    private val onRecordingStopped: (String, Long, Int) -> Unit,
    private val onError: (String) -> Unit
) {

    private val TAG = "RecordingManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val frameChannel = Channel<FrameData>(100)
    private val job = Job()
    private var isRunning = false
    private var isRecording = false
    private var currentMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var currentFile: File? = null
    private var recordingStartTime = 0L
    private val circularBuffer = CircularBuffer(capacitySeconds = 5)
    private var pendingPreRoll = false
    private var segmentJob: Job? = null

    data class FrameData(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val isKeyFrame: Boolean,
        val isVideo: Boolean
    )

    inner class CircularBuffer(private val capacitySeconds: Int) {
        private val buffer = mutableListOf<FrameData>()
        private val maxFrames = capacitySeconds * 30 // assume 30fps max

        fun add(frame: FrameData) {
            if (frame.isVideo && frame.isKeyFrame) {
                buffer.add(frame)
                while (buffer.size > maxFrames) {
                    buffer.removeAt(0)
                }
            }
        }

        fun getPreRoll(): List<FrameData> {
            return buffer.toList()
        }

        fun clear() {
            buffer.clear()
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        AppLog.getInstance(context).log(TAG, "Starting recording manager")
        
        scope.launch(job) {
            processFrames()
        }
        
        // Segment rotation job
        segmentJob = scope.launch {
            while (isRunning) {
                kotlinx.coroutines.delay(300_000) // 5 minutes
                if (isRecording) {
                    rotateSegment()
                }
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        job.cancel()
        segmentJob?.cancel()
        
        if (isRecording) {
            stopRecordingInternal()
        }
        
        frameChannel.close()
        circularBuffer.clear()
        AppLog.getInstance(context).log(TAG, "Recording manager stopped")
    }

    fun onVideoFrame(data: ByteArray, presentationTimeUs: Long, isKeyFrame: Boolean) {
        if (isRunning) {
            val frame = FrameData(data, presentationTimeUs, isKeyFrame, true)
            circularBuffer.add(frame)
            frameChannel.trySend(frame)
        }
    }

    fun onAudioFrame(data: ByteArray, presentationTimeUs: Long) {
        if (isRunning) {
            val frame = FrameData(data, presentationTimeUs, false, false)
            frameChannel.trySend(frame)
        }
    }

    fun triggerRecording() {
        if (!isRunning) return
        
        if (!isRecording) {
            startRecording()
        } else {
            // Extend current recording
            AppLog.getInstance(context).log(TAG, "Motion during recording - extending")
        }
    }

    fun stopRecording() {
        if (isRecording) {
            stopRecordingInternal()
        }
    }

    private fun startRecording() {
        if (isRecording) return
        
        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        recordingsDir.mkdirs()
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "rec_${timestamp}.mp4"
        currentFile = File(recordingsDir, filename)
        
        try {
            currentMuxer = MediaMuxer(currentFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            // Video format
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 
                settings.getResolutionWidth(), settings.getResolutionHeight())
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, settings.bitrateKbps * 1000)
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, settings.frameRate)
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            
            videoTrackIndex = currentMuxer!!.addTrack(videoFormat)
            
            // Audio format
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 
                Settings.audioSampleRate, if (Settings.audioStereo) 2 else 1)
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, Settings.audioBitrateKbps * 1000)
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            
            audioTrackIndex = currentMuxer!!.addTrack(audioFormat)
            
            currentMuxer!!.start()
            
            // Write pre-roll frames
            val preRollFrames = circularBuffer.getPreRoll()
            for (frame in preRollFrames) {
                currentMuxer!!.writeSampleData(videoTrackIndex, java.nio.ByteBuffer.wrap(frame.data), 
                    android.media.MediaCodec.BufferInfo().apply {
                        size = frame.data.size
                        presentationTimeUs = frame.presentationTimeUs
                        flags = if (frame.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        offset = 0
                    })
            }
            
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            pendingPreRoll = false
            circularBuffer.clear()
            
            onRecordingStarted(currentFile!!.absolutePath)
            AppLog.getInstance(context).log(TAG, "Recording started: $filename (pre-roll: ${preRollFrames.size} frames)")
            
        } catch (e: IOException) {
            onError("Failed to start recording: ${e.message}")
            AppLog.getInstance(context).logError(TAG, "Failed to start recording", e)
            cleanup()
        }
    }

    private fun stopRecordingInternal() {
        currentMuxer?.stop()
        currentMuxer?.release()
        currentMuxer = null
        videoTrackIndex = -1
        audioTrackIndex = -1
        
        val durationSec = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        val path = currentFile?.absolutePath ?: ""
        
        isRecording = false
        currentFile = null
        recordingStartTime = 0
        
        onRecordingStopped(path, System.currentTimeMillis(), durationSec)
        AppLog.getInstance(context).log(TAG, "Recording stopped: $path (${durationSec}s)")
    }

    private fun rotateSegment() {
        scope.launch {
            val oldFile = currentFile
            stopRecordingInternal()
            // Small gap, then start new segment
            kotlinx.coroutines.delay(100)
            startRecording()
            AppLog.getInstance(context).log(TAG, "Segment rotated: ${oldFile?.name} -> ${currentFile?.name}")
        }
    }

    private suspend fun processFrames() {
        while (isRunning) {
            val result = frameChannel.receiveCatching()
            if (result.isClosed) break
            val frame = result.getOrNull() ?: continue
            
            if (isRecording && currentMuxer != null) {
                try {
                    val bufferInfo = android.media.MediaCodec.BufferInfo().apply {
                        size = frame.data.size
                        presentationTimeUs = frame.presentationTimeUs
                        flags = if (frame.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        offset = 0
                    }
                    
                    val trackIndex = if (frame.isVideo) videoTrackIndex else audioTrackIndex
                    if (trackIndex >= 0) {
                        currentMuxer!!.writeSampleData(trackIndex, java.nio.ByteBuffer.wrap(frame.data), bufferInfo)
                    }
                } catch (e: Exception) {
                    AppLog.getInstance(context).logError(TAG, "Error writing frame", e)
                }
            }
        }
    }

    private fun cleanup() {
        currentMuxer?.stop()
        currentMuxer?.release()
        currentMuxer = null
        videoTrackIndex = -1
        audioTrackIndex = -1
        currentFile = null
        isRecording = false
    }

    fun isRecording(): Boolean = isRecording
    fun isRunning(): Boolean = isRunning
}