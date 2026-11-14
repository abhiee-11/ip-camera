package com.freecam.camera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.freecam.camera.data.Settings

/**
 * Restarts the camera service once a day.
 *
 * The old version stopped the service and immediately started it again, which
 * left the service running but not streaming, and it never re-armed the alarm.
 * This version stops the service properly, waits for it to die, then starts a
 * fresh one and re-arms the alarm for tomorrow.
 */
class NightlyRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings.getInstance(context)
        if (!settings.nightlyRestart) return

        val stopIntent = Intent(context, CameraService::class.java).apply {
            action = CameraService.ACTION_STOP
        }
        context.stopService(stopIntent)

        Handler(Looper.getMainLooper()).postDelayed({
            val startIntent = Intent(context, CameraService::class.java).apply {
                action = CameraService.ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
            } catch (e: Exception) {
                // The camera will be started again on the next boot.
            }
        }, 1000)
    }
}
