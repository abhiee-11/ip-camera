package com.freecam.viewer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Settings(private val prefs: SharedPreferences) {

    companion object {
        private const val PREFS_NAME = "freecam_viewer_prefs"
        private const val MASTER_KEY_ALIAS = "freecam_viewer_prefs_master_key"

        fun getInstance(context: Context): Settings {
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
            return Settings(prefs)
        }
    }

    var theme: Int
        get() = prefs.getInt("theme", 0) // 0=system, 1=dark, 2=light
        set(value) = prefs.edit().putInt("theme", value).apply()

    fun getNtfyTopic(cameraId: String): String = prefs.getString("ntfy_topic_$cameraId", "")!!
    fun setNtfyTopic(cameraId: String, topic: String) = prefs.edit().putString("ntfy_topic_$cameraId", topic).apply()

    fun getNtfyServer(cameraId: String): String = prefs.getString("ntfy_server_$cameraId", "https://ntfy.sh")!!
    fun setNtfyServer(cameraId: String, server: String) = prefs.edit().putString("ntfy_server_$cameraId", server).apply()
}