package com.freecam.camera.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Reads what the battery is doing. */
object BatteryStatus {

    data class Status(val percent: Int?, val charging: Boolean)

    fun read(context: Context): Status {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return Status(null, false)
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
        return Status(percent, isCharging(context))
    }

    private fun isCharging(context: Context): Boolean {
        return try {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            plugged != 0
        } catch (e: Exception) {
            false
        }
    }
}
