package com.freecam.viewer.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.freecam.common.model.Pairing
import com.freecam.common.util.Bytes
import com.freecam.common.util.TimeFormat
import kotlinx.serialization.json.Json
import java.security.Key

class CameraStore(private val prefs: SharedPreferences) {

    companion object {
        private const val PREFS_NAME = "freecam_viewer_settings"
        private const val MASTER_KEY_ALIAS = "freecam_viewer_master_key"
        private const val KEY_CAMERAS = "cameras_json"

        fun getInstance(context: Context): CameraStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return CameraStore(prefs)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun getCameras(): List<Pairing> {
        val jsonString = prefs.getString(KEY_CAMERAS, "[]")!!
        return try {
            json.decodeFromString<List<Pairing>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCameras(cameras: List<Pairing>) {
        val jsonString = json.encodeToString(cameras)
        prefs.edit().putString(KEY_CAMERAS, jsonString).apply()
    }

    fun addCamera(camera: Pairing) {
        val cameras = getCameras().toMutableList()
        // Replace if same host+token exists
        val index = cameras.indexOfFirst { it.host == camera.host && it.token == camera.token }
        if (index >= 0) {
            cameras[index] = camera
        } else {
            cameras.add(camera)
        }
        saveCameras(cameras)
    }

    fun removeCamera(host: String, token: String) {
        val cameras = getCameras().filterNot { it.host == host && it.token == token }
        saveCameras(cameras)
    }

    fun updateCameraName(host: String, token: String, newName: String) {
        val cameras = getCameras().map {
            if (it.host == host && it.token == token) it.copy(name = newName) else it
        }
        saveCameras(cameras)
    }
}