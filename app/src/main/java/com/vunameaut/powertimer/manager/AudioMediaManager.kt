package com.vunameaut.powertimer.manager

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import kotlinx.coroutines.delay

class AudioMediaManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Dừng phát nhạc / media đang phát trên thiết bị (Spotify, YouTube, Apple Music, podcast, v.v.)
     */
    fun pauseAllMedia() {
        // Cách 1: Gửi KeyEvent PAUSE
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)

        // Cách 2: Request Audio Focus để các app nghe nhạc tự động pause
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    /**
     * Giảm dần âm lượng trong 30 giây trước khi tắt (Fade-out effect)
     */
    suspend fun fadeOutVolume(durationMs: Long = 10000L) {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVolume <= 0) return

        val steps = currentVolume
        val stepDelay = durationMs / steps

        for (i in (currentVolume - 1) downTo 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, i, 0)
            delay(stepDelay)
        }
    }

    /**
     * Kích hoạt chế độ Không làm phiền (Do Not Disturb)
     */
    fun enableDoNotDisturb() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
    }
}
