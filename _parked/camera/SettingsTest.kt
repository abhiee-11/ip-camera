package com.freecam.camera.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freecam.common.util.Bytes
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    private lateinit var context: Context
    private lateinit var settings: Settings

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        settings = Settings.getInstancePlain(context)
        // Clear all prefs
        context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun testDefaults() {
        assertThat(settings.cameraName).isEqualTo("Camera 1")
        assertThat(settings.cameraFacing).isEqualTo(0)
        assertThat(settings.resolution).isEqualTo("640x480")
        assertThat(settings.frameRate).isEqualTo(15)
        assertThat(settings.bitrateKbps).isEqualTo(500)
        assertThat(settings.rotation).isEqualTo("auto")
        assertThat(settings.recordingMode).isEqualTo("motion")
        assertThat(settings.storageCapGb).isEqualTo(6)
        assertThat(settings.retentionDays).isEqualTo(7)
        assertThat(settings.motionEnabled).isTrue()
        assertThat(settings.motionSensitivity).isEqualTo("medium")
        assertThat(settings.motionCooldownSec).isEqualTo(60)
        assertThat(settings.ntfyServer).isEqualTo("https://ntfy.sh")
        assertThat(settings.autoStart).isTrue()
        assertThat(settings.nightlyRestart).isTrue()
        assertThat(settings.restartTime).isEqualTo("04:00")
        assertThat(settings.destinationMode).isEqualTo("local")
    }

    @Test
    fun testTokenGeneration() {
        val token1 = settings.token
        assertThat(token1).hasLength(64) // 32 bytes = 64 hex chars
        
        val token2 = settings.token
        assertThat(token2).isEqualTo(token1) // Should return same token
    }

    @Test
    fun testResetToken() {
        val oldToken = settings.token
        val newToken = settings.resetToken()
        assertThat(newToken).hasLength(64)
        assertThat(newToken).isNotEqualTo(oldToken)
        assertThat(settings.token).isEqualTo(newToken)
    }

    @Test
    fun testNtfyTopicGeneration() {
        settings.ntfyTopic = ""
        val topic = settings.ensureNtfyTopic()
        assertThat(topic).hasLength(48) // 24 bytes = 48 hex chars
        assertThat(settings.ntfyTopic).isEqualTo(topic)
    }

    @Test
    fun testResolutionParsing() {
        settings.resolution = "1280x720"
        assertThat(settings.getResolutionWidth()).isEqualTo(1280)
        assertThat(settings.getResolutionHeight()).isEqualTo(720)
        
        settings.resolution = "invalid"
        assertThat(settings.getResolutionWidth()).isEqualTo(640)
        assertThat(settings.getResolutionHeight()).isEqualTo(480)
    }

    @Test
    fun testStorageCapClamping() {
        settings.storageCapGb = 0
        assertThat(settings.storageCapGb).isEqualTo(1)
        
        settings.storageCapGb = 25
        assertThat(settings.storageCapGb).isEqualTo(20)
        
        settings.storageCapGb = 10
        assertThat(settings.storageCapGb).isEqualTo(10)
    }

    @Test
    fun testRetentionClamping() {
        settings.retentionDays = 0
        assertThat(settings.retentionDays).isEqualTo(1)
        
        settings.retentionDays = 20
        assertThat(settings.retentionDays).isEqualTo(14)
    }

    @Test
    fun testMotionCooldownClamping() {
        settings.motionCooldownSec = -10
        assertThat(settings.motionCooldownSec).isEqualTo(0)
    }

    @Test
    fun testStorageCapBytes() {
        settings.storageCapGb = 6
        assertThat(settings.storageCapBytes()).isEqualTo(Bytes.parseGb(6))
    }

    @Test
    fun testRetentionMillis() {
        settings.retentionDays = 7
        assertThat(settings.retentionMillis()).isEqualTo(7L * 24 * 60 * 60 * 1000)
    }
}