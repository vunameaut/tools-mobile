package com.vunameaut.powertimer

import android.app.Application
import android.util.Log
import com.vunameaut.powertimer.ui.screens.BlackoutSleepActivity

class PowerTimerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BlackoutSleepActivity.restoreOriginalScreenTimeout(this)
        Log.i("PowerTimer", "Application initialized")
    }
}
