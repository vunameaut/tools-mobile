package com.vunameaut.powertimer.manager

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.MediaSessionManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.vunameaut.powertimer.service.MoneySoundboxService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioMediaManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioMediaManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Dừng triệt để mọi video/nhạc đang phát trên Facebook, TikTok, YouTube, Spotify...:
     * 1. Điều khiển trực tiếp MediaSession đang hoạt động (nếu đã cấp quyền Listener).
     * 2. Yêu cầu AUDIOFOCUS_GAIN (vĩnh viễn) + setWillPauseWhenDucked(true) kèm xung tĩnh 250ms
     *    trên STREAM_MUSIC để ép ExoPlayer (Facebook Watch / Reels / TikTok) dừng hẳn thay vì phớt lờ.
     * 3. Gửi KeyEvent PAUSE/STOP 2 nhịp cách nhau 600ms để chặn video tự phát tiếp.
     * 4. Tự động nhả AudioFocus sau 4.5 giây trên coroutine nền để không ảnh hưởng chuông cuộc gọi.
     */
    suspend fun pauseAllMediaAndReleaseFocus() {
        // Bước 1: Dừng trực tiếp qua MediaSessionManager (nếu NotificationListener đang hoạt động)
        pauseActiveMediaSessionsViaController()

        // Bước 2: Gửi phím Pause & Stop toàn hệ thống (nhịp 1)
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)

        // Bước 3: Yêu cầu AUDIOFOCUS_GAIN (Permanent Loss đối với app kia) thay vì TRANSIENT
        var focusRequest: AudioFocusRequest? = null
        var legacyListener: AudioManager.OnAudioFocusChangeListener? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = req
            runCatching { audioManager.requestAudioFocus(req) }
        } else {
            val listener = AudioManager.OnAudioFocusChangeListener { }
            legacyListener = listener
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }

        // Bước 4: Phát một xung âm thanh câm 250ms trên STREAM_MUSIC để ép System Audio Policy thu hồi focus khỏi Facebook/TikTok
        playSilentFocusTriggerPulse()

        // Bước 5: Gửi bồi thêm 1 nhịp Pause sau 600ms để chặn trường hợp Facebook Reels/Watch tự play lại
        delay(600L)
        pauseActiveMediaSessionsViaController()
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)

        // Bước 6: Giữ AUDIOFOCUS_GAIN ngầm thêm 4 giây rồi mới giải phóng để không chặn chuông cuộc gọi / báo thức
        CoroutineScope(Dispatchers.Default).launch {
            delay(4000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
            } else {
                @Suppress("DEPRECATION")
                legacyListener?.let { runCatching { audioManager.abandonAudioFocus(it) } }
            }
        }
    }

    private fun pauseActiveMediaSessionsViaController() {
        runCatching {
            val cn = ComponentName(context, MoneySoundboxService::class.java)
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return
            if (!enabledListeners.contains(cn.flattenToString(), ignoreCase = true) &&
                !enabledListeners.contains(context.packageName, ignoreCase = true)
            ) {
                return
            }
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val controllers = msm.getActiveSessions(cn)
            for (ctrl in controllers) {
                if (ctrl.packageName == context.packageName) continue
                runCatching { ctrl.transportControls.pause() }
                runCatching { ctrl.transportControls.stop() }
                Log.d(TAG, "Sent pause/stop directly to media controller: ${ctrl.packageName}")
            }
        }
    }

    private fun playSilentFocusTriggerPulse() {
        runCatching {
            val sampleRate = 8000
            val count = sampleRate / 4 // 250ms
            val samples = ShortArray(count) { 0 }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(count * 2)
                .build()
            track.write(samples, 0, count)
            track.setVolume(0.0f)
            track.play()
            Thread.sleep(260L)
            track.stop()
            track.release()
        }
    }

    fun pauseAllMedia() {
        pauseActiveMediaSessionsViaController()
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
     * Giảm dần âm lượng Media trước khi dừng phát (Fade-out), sau khi dừng nhạc/video xong sẽ
     * TỰ ĐỘNG KHÔI PHỤC lại mức âm lượng ban đầu để sáng hôm sau máy không bị mất tiếng!
     */
    suspend fun fadeOutAndPauseMedia(fadeAudio: Boolean, durationMs: Long = 2800L) {
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        if (fadeAudio && originalVolume > 0) {
            val steps = originalVolume.coerceAtLeast(1)
            val stepDelay = (durationMs / steps).coerceAtLeast(100L)

            for (i in (originalVolume - 1) downTo 0) {
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, i, 0)
                }
                delay(stepDelay)
            }
        }

        // Dừng toàn bộ trình phát nhạc/video (YouTube, Facebook, TikTok...)
        pauseAllMediaAndReleaseFocus()

        // Khôi phục lại âm lượng Media ban đầu sau khi video đã dừng hẳn
        if (fadeAudio && originalVolume > 0) {
            delay(500L)
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            }
        }
    }
}
