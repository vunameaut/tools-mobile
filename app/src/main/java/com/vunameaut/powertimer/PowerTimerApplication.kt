package com.vunameaut.powertimer

import android.app.Application
import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PowerTimerApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        @Volatile
        var isYoutubeDLReady: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("PowerTimer", "Application initialized")

        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@PowerTimerApplication)
                isYoutubeDLReady = true
                Log.i("PowerTimer", "On-device YoutubeDL engine initialized successfully")

                // Tự động cập nhật nhân yt-dlp định kỳ (mỗi 24h) trong nền
                val prefs = getSharedPreferences("ytdlp_engine_prefs", Context.MODE_PRIVATE)
                val lastUpdate = prefs.getLong("last_update_ms", 0L)
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 24 * 60 * 60 * 1000L) {
                    runCatching {
                        YoutubeDL.getInstance().updateYoutubeDL(
                            this@PowerTimerApplication,
                            YoutubeDL.UpdateChannel._NIGHTLY
                        )
                        prefs.edit().putLong("last_update_ms", now).apply()
                        Log.i("PowerTimer", "On-device yt-dlp engine auto-updated")
                    }
                }
            } catch (e: Throwable) {
                Log.w("PowerTimer", "YoutubeDL init failed (direct extractors & server fallback still active): ${e.message}")
            }
        }
    }
}
