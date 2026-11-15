package com.vunameaut.powertimer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.manager.ShutdownManager
import com.vunameaut.powertimer.service.TimerForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TIMER_EXPIRED = "com.vunameaut.powertimer.ACTION_TIMER_EXPIRED"
        const val EXTRA_SHUTDOWN_MODE = "extra_shutdown_mode"
        const val EXTRA_FADE_AUDIO = "extra_fade_audio"
        const val EXTRA_ENABLE_DND = "extra_enable_dnd"
        private const val TAG = "TimerAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TIMER_EXPIRED) {
            Log.d(TAG, "Exact Alarm expired! Initiating shutdown sequence and stopping timer service...")

            val appCtx = context.applicationContext
            val powerManager = appCtx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PowerTimer:AlarmWakeLock"
            )
            wakeLock.acquire(15000L)

            val modeName = intent.getStringExtra(EXTRA_SHUTDOWN_MODE)
            val mode = try {
                if (modeName != null) ShutdownMode.valueOf(modeName) else ShutdownMode.BANK_SAFE_SLEEP
            } catch (_: Exception) {
                ShutdownMode.BANK_SAFE_SLEEP
            }
            val fadeAudio = intent.getBooleanExtra(EXTRA_FADE_AUDIO, true)
            val enableDnd = intent.getBooleanExtra(EXTRA_ENABLE_DND, false)

            // Dừng ngay TimerForegroundService và NativeMediaIslandBridge để không bao giờ bị treo thời gian đếm ngược đến sáng
            TimerForegroundService.stopTimer(appCtx)

            val shutdownManager = ShutdownManager(appCtx)
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    shutdownManager.executeShutdown(
                        mode = mode,
                        fadeAudio = fadeAudio,
                        enableDnd = enableDnd
                    )
                } finally {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
            }
        }
    }
}
