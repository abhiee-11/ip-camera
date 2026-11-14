package com.freecam.camera.data

/**
 * One immutable snapshot of everything the camera UI shows. Published by
 * [CameraService] and read by the dashboard, the API and the notification.
 */
data class CameraUiState(
    val running: Boolean = false,
    val recording: Boolean = false,
    val uptimeMs: Long = 0L,
    val cameraName: String = "",
    val resolution: String = "",
    val fps: Int = 0,
    val bitrateKbps: Int = 0,
    val rtspUrl: String = "",
    val apiUrl: String = "",
    val host: String? = null,
    val tailnetConnected: Boolean = false,
    val tailnetIp: String? = null,
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val storageFreeBytes: Long = 0L,
    val storageCapBytes: Long = 0L,
    val motionEnabled: Boolean = false,
    val motionSensitivity: String = "medium",
    val motionAvailable: Boolean = false,
    val eventsToday: Int = 0,
    val recordingMode: String = "continuous",
    val errorCode: String? = null,
    val errorMessage: String? = null
)

/** Holds the current camera state for the whole process. */
object CameraStateStore {

    @Volatile
    var state: CameraUiState = CameraUiState()
        private set

    fun publish(next: CameraUiState) {
        state = next
    }

    fun snapshot(): CameraUiState = state
}
