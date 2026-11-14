package com.freecam.camera.notifications

import android.content.Context
import com.freecam.camera.data.AppLog
import com.freecam.camera.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends push notifications through an ntfy server. The topic is the camera's
 * secret: whoever holds it receives the alerts.
 */
class Notifier(
    private val context: Context,
    private val settings: Settings,
    private val appLog: AppLog
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /** Does nothing when no topic is configured. */
    fun notify(title: String, message: String, priority: String = "default") {
        val topic = settings.ntfyTopic
        if (topic.isEmpty()) return
        scope.launch { post(topic, title, message, priority) }
    }

    private fun post(topic: String, title: String, message: String, priority: String) {
        val server = settings.ntfyServer.trimEnd('/')
        val url = "$server/$topic"
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Title", title)
            connection.setRequestProperty("Priority", priority)
            connection.setRequestProperty("Tags", "camera")
            connection.outputStream.use { it.write(message.toByteArray()) }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                appLog.log(TAG, "Notification sent")
            } else {
                appLog.logError(TAG, "Notification failed: HTTP $code")
            }
            connection.disconnect()
        } catch (e: Exception) {
            appLog.logError(TAG, "Notification error", e)
        }
    }

    companion object {
        private const val TAG = "Notifier"
    }
}
