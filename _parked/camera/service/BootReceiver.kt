package com.freecam.camera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.freecam.camera.data.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_QUICKBOOT_POWERON ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val settings = Settings.getInstancePlain(context)
            if (settings.autoStart) {
                val serviceIntent = Intent(context, CameraService::class.java)
                serviceIntent.action = CameraService.ACTION_START
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d("BootReceiver", "Auto-started CameraService")
            }
        }
    }
}