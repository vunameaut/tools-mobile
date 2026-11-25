package com.vunameaut.powertimer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.service.TimerForegroundService

class TimerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TIMER_EXPIRED = "com.vunameaut.powertimer.ACTION_TIMER_EXPIRED"
        const val EXTRA_SHUTDOWN_MODE = "extra_shutdown_mode"
        const val EXTRA_FADE_AUDIO = "extra_fade_audio"
        private const val TAG = "TimerAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TIMER_EXPIRED) {
            Log.d(TAG, "Backup Exact Alarm fired! Forwarding to TimerForegroundService Enforcer...")

            val appCtx = context.applicationContext
            val powerManager = appCtx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PowerTimer:AlarmReceiverLock"
            )
            wakeLock.acquire(5_000L)

            val modeName = intent.getStringExtra(EXTRA_SHUTDOWN_MODE)
            val mode = runCatching {
                if (modeName != null) ShutdownMode.valueOf(modeName) else ShutdownMode.BANK_SAFE_SLEEP
            }.getOrDefault(ShutdownMode.BANK_SAFE_SLEEP)
            val fadeAudio = intent.getBooleanExtra(EXTRA_FADE_AUDIO, true)

            runCatching {
                TimerForegroundService.triggerFallbackExpiry(appCtx, mode, fadeAudio)
            }.onFailure {
                Log.e(TAG, "Failed to hand off to TimerForegroundService: ${it.message}")
            }

            if (wakeLock.isHeld) {
                runCatching { wakeLock.release() }
            }
        }
    }
}
