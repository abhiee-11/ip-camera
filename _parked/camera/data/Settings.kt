package com.freecam.camera.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.freecam.common.util.Bytes
import com.freecam.common.util.TimeFormat
import java.security.Key
import java.security.SecureRandom

class Settings(private val prefs: SharedPreferences) {

    companion object {
        private const val PREFS_NAME = "freecam_camera_settings"
        private const val MASTER_KEY_ALIAS = "freecam_master_key"

        fun getInstance(context: Context): Settings {
            val masterKey: Key = MasterKeys.getOrCreate(
                KeyGenParameterSpec.Builder(MASTER_KEY_ALIAS)
                    .setKeyScheme(KeyGenParameterSpec.KeyScheme.AES256_GCM)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            val prefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return Settings(prefs)
        }

        fun getInstancePlain(context: Context): Settings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return Settings(prefs)
        }

        fun generateToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun generateNtfyTopic(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    // Camera settings
    var cameraName: String
        get() = prefs.getString("camera_name", "Camera 1")!!
        set(value) = prefs.edit().putString("camera_name", value).apply()

    var cameraFacing: Int
        get() = prefs.getInt("camera_facing", 0)
        set(value) = prefs.edit().putInt("camera_facing", value).apply()

    var resolution: String
        get() = prefs.getString("resolution", "640x480")!!
        set(value) = prefs.edit().putString("resolution", value).apply()

    var frameRate: Int
        get() = prefs.getInt("frame_rate", 15)
        set(value) = prefs.edit().putInt("frame_rate", value).apply()

    var bitrateKbps: Int
        get() = prefs.getInt("bitrate", 500)
        set(value) = prefs.edit().putInt("bitrate", value).apply()

    var rotation: String
        get() = prefs.getString("rotation", "auto")!!
        set(value) = prefs.edit().putString("rotation", value).apply()

    var recordingMode: String
        get() = prefs.getString("recording_mode", "motion")!!
        set(value) = prefs.edit().putString("recording_mode", value).apply()

    var storageCapGb: Int
        get() = prefs.getInt("storage_cap_gb", 6)
        set(value) = prefs.edit().putInt("storage_cap_gb", value.coerceIn(1, 20)).apply()

    var retentionDays: Int
        get() = prefs.getInt("retention_days", 7)
        set(value) = prefs.edit().putInt("retention_days", value.coerceIn(1, 14)).apply()

    // Motion settings
    var motionEnabled: Boolean
        get() = prefs.getBoolean("motion_enabled", true)
        set(value) = prefs.edit().putBoolean("motion_enabled", value).apply()

    var motionSensitivity: String
        get() = prefs.getString("motion_sensitivity", "medium")!!
        set(value) = prefs.edit().putString("motion_sensitivity", value).apply()

    var motionCooldownSec: Int
        get() = prefs.getInt("motion_cooldown", 60)
        set(value) = prefs.edit().putInt("motion_cooldown", value.coerceAtLeast(0)).apply()

    // Notifications
    var ntfyServer: String
        get() = prefs.getString("ntfy_server", "https://ntfy.sh")!!
        set(value) = prefs.edit().putString("ntfy_server", value).apply()

    var ntfyTopic: String
        get() = prefs.getString("ntfy_topic", "")!!
        set(value) = prefs.edit().putString("ntfy_topic", value).apply()

    // Advanced
    var destinationMode: String
        get() = prefs.getString("destination_mode", "local")!!
        set(value) = prefs.edit().putString("destination_mode", value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", true)
        set(value) = prefs.edit().putBoolean("auto_start", value).apply()

    var nightlyRestart: Boolean
        get() = prefs.getBoolean("nightly_restart", true)
        set(value) = prefs.edit().putBoolean("nightly_restart", value).apply()

    var restartTime: String
        get() = prefs.getString("restart_time", "04:00")!!
        set(value) = prefs.edit().putString("restart_time", value).apply()

    // Token
    var token: String
        get() = prefs.getString("token", "")!!.also {
            if (it.isEmpty()) {
                val newToken = generateToken()
                token = newToken
                return@get newToken
            }
        }
        set(value) = prefs.edit().putString("token", value).apply()

    fun resetToken(): String {
        val newToken = generateToken()
        token = newToken
        return newToken
    }

    fun ensureNtfyTopic(): String {
        if (ntfyTopic.isEmpty()) {
            ntfyTopic = generateNtfyTopic()
        }
        return ntfyTopic
    }

    // Parsed resolution
    fun getResolutionWidth(): Int = resolution.split("x").firstOrNull()?.toIntOrNull() ?: 640
    fun getResolutionHeight(): Int = resolution.split("x").lastOrNull()?.toIntOrNull() ?: 480

    fun storageCapBytes(): Long = Bytes.parseGb(storageCapGb)
    fun retentionMillis(): Long = retentionDays * 24L * 60 * 60 * 1000
}