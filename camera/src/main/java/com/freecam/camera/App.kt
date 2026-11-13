package com.freecam.camera

import android.app.Application
import android.util.Log
import com.freecam.camera.data.AppLog

class App : Application() {

    var appLog: AppLog? = null

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Log.d("FreeCamCamera", "Camera app started")
        }
    }
}