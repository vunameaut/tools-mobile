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

    companion object {
        private const val PREF_AUDIO_STATE = "power_timer_audio_restore_prefs"
        private const val KEY_DND_ENABLED_BY_APP = "dnd_enabled_by_app"

        /**
         * Khôi phục lại chế độ Chuông cuộc gọi & Thông báo bình thường (Tắt DND nếu trước đó bị bật)
         * để đảm bảo người dùng không bao giờ bị mất chuông cuộc gọi hoặc báo thức.
         */
        fun restoreNormalRingerIfModified(context: Context) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val prefs = context.getSharedPreferences(PREF_AUDIO_STATE, Context.MODE_PRIVATE)
                val modifiedByApp = prefs.getBoolean(KEY_DND_ENABLED_BY_APP, true)
                if (modifiedByApp && nm.isNotificationPolicyAccessGranted) {
                    if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                    prefs.edit().putBoolean(KEY_DND_ENABLED_BY_APP, false).apply()
                }
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Dừng phát nhạc / media đang phát trên thiết bị (Spotify, YouTube, Apple Music, podcast, v.v.)
     * và giải phóng ngay AudioFocus sau 500ms để KHÔNG chiếm quyền thanh Âm lượng hay chặn chuông cuộc gọi.
     */
    suspend fun pauseAllMediaAndReleaseFocus() {
        // Cách 1: Gửi KeyEvent PAUSE & STOP
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)

        // Cách 2: Request Audio Focus tạm thời để các app phát video/nhạc tự pause, sau đó trả lại AudioFocus ngay!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
            delay(500L)
            audioManager.abandonAudioFocusRequest(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            delay(500L)
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun pauseAllMedia() {
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    /**
     * Giảm dần âm lượng Media trước khi dừng phát (Fade-out), sau khi dừng nhạc xong sẽ
     * TỰ ĐỘNG KHÔI PHỤC lại mức âm lượng ban đầu để sáng hôm sau máy không bị mất tiếng!
     */
    suspend fun fadeOutAndPauseMedia(fadeAudio: Boolean, durationMs: Long = 3500L) {
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        if (fadeAudio && originalVolume > 0) {
            val steps = originalVolume.coerceAtLeast(1)
            val stepDelay = (durationMs / steps).coerceAtLeast(120L)

            for (i in (originalVolume - 1) downTo 0) {
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, i, 0)
                }
                delay(stepDelay)
            }
        }

        // Dừng toàn bộ trình phát nhạc/YouTube và nhả ngay AudioFocus
        pauseAllMediaAndReleaseFocus()

        // Khôi phục lại âm lượng Media ban đầu sau khi video đã dừng hẳn
        if (fadeAudio && originalVolume > 0) {
            delay(400L)
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            }
        }
    }

    /**
     * Kích hoạt chế độ Không làm phiền (Do Not Disturb) nếu người dùng chủ động bật
     */
    fun enableDoNotDisturb() {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            context.getSharedPreferences(PREF_AUDIO_STATE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DND_ENABLED_BY_APP, true)
                .apply()
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
    }
}
