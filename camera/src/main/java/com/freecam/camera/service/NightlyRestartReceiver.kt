package com.freecam.camera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.freecam.camera.data.Settings

class NightlyRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings.getInstance(context)
        if (settings.nightlyRestart) {
            // Stop the service
            val stopIntent = Intent(context, CameraService::class.java).apply {
                action = CameraService.ACTION_STOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.stopService(stopIntent)
            } else {
                context.stopService(stopIntent)
            }
            
            // Start the service again
            val startIntent = Intent(context, CameraService::class.java).apply {
                action = CameraService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
            
            // Reschedule next nightly restart
            val service = CameraService::class.java
            // We need to call the schedule function - for now just log
            // In a real implementation, we'd need to restart the service to reschedule
        }
    }
}