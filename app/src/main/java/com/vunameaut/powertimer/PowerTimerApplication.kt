package com.vunameaut.powertimer

import android.app.Application
import android.util.Log
import com.vunameaut.powertimer.manager.TimerPersistenceManager
import com.vunameaut.powertimer.service.MoneySoundboxService
import com.vunameaut.powertimer.service.TimerForegroundService
import com.vunameaut.powertimer.ui.screens.BlackoutSleepActivity

class PowerTimerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val persistence = TimerPersistenceManager.from(this)
        if (!persistence.isShutdownCurrentlyEnforcing(System.currentTimeMillis())) {
            BlackoutSleepActivity.restoreOriginalScreenTimeout(this)
        }
        TimerForegroundService.reviveIfPersistedTimerActive(this)
        MoneySoundboxService.ensureServiceRebound(this)
        Log.i("PowerTimer", "Application initialized")
    }
}
