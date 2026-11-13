package com.freecam.camera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.freecam.camera.data.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val settings = Settings.getInstance(context)
            if (settings.autoStart) {
                val serviceIntent = Intent(context, CameraService::class.java).apply {
                    setAction(CameraService.ACTION_START)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}