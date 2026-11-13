package com.freecam.camera.motion

import android.content.Context
import android.util.Log
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.net.HttpURLConnection
import java.net.URL

class Notifier(private val context: Context, private val settings: Settings) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastNotificationTime = 0L

    fun notifyMotion(cameraName: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationTime < settings.motionCooldownSec * 1000L) {
            return // Cooldown active
        }
        lastNotificationTime = now

        scope.launch {
            sendNtfyNotification(cameraName)
        }
    }

    private fun sendNtfyNotification(cameraName: String) {
        val topic = settings.ensureNtfyTopic()
        val server = settings.ntfyServer
        val urlString = "$server/$topic"
        val message = "Motion at $cameraName"
        val title = "FreeCam Alert"

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Title", title)
            connection.setRequestProperty("Priority", "high")
            connection.setRequestProperty("Tags", "warning,camera")
            
            connection.outputStream.use { it.write(message.toByteArray()) }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                AppLog.getInstance(context).log("Notifier", "ntfy notification sent successfully")
            } else {
                AppLog.getInstance(context).logError("Notifier", "ntfy notification failed: HTTP $responseCode")
            }
        } catch (e: Exception) {
            AppLog.getInstance(context).logError("Notifier", "ntfy notification error", e)
        }
    }
}