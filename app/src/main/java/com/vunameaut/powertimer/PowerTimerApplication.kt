package com.vunameaut.powertimer

import android.app.Application
import android.util.Log

class PowerTimerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("PowerTimer", "Application initialized")
    }
}
