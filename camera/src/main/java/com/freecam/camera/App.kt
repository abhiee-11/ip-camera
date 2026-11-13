package com.freecam.camera

import android.app.Application
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Log.d("FreeCamCamera", "Camera app started")
        }
    }
}