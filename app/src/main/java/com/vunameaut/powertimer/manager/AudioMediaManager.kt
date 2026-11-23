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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioMediaManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioMediaManager"
        private val bgScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // Giữ tham chiếu tĩnh tới AudioFocusRequest để không bị GC thu hồi trước khi nhả focus
        @Volatile
        private var heldMusicFocusRequest: AudioFocusRequest? = null

        @Volatile
        private var heldMovieFocusRequest: AudioFocusRequest? = null

        private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { }
        private var releaseFocusJob: Job? = null
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Dừng triệt để mọi nhạc & video đang phát (Facebook, TikTok, YouTube, Spotify, Zing MP3...)
     * mà TUYỆT ĐỐI KHÔNG HẠ ÂM LƯỢNG MÁY VỀ 0 (giữ nguyên mức âm lượng hiện tại của người dùng).
     */
    suspend fun pauseAllMediaAndReleaseFocus() {
        // Phục hồi nếu âm lượng bị kẹt ở mức 0 từ bản cũ
        runCatching {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol == 0 && maxVol > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol / 3).coerceAtLeast(3), 0)
            }
        }

        // 1. Dừng trực tiếp các MediaSession đang hoạt động (nếu NotificationListener đang bật)
        pauseActiveMediaSessionsViaController()

        // 2. Xin AUDIOFOCUS_GAIN (Permanent Loss) cho cả Music & Movie
        requestPermanentAudioFocus()

        // 3. Gửi phím cứng Media Pause & Stop
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)

        // 4. Phát xung âm thanh tĩnh 200ms trên STREAM_MUSIC trên luồng nền để ép System Audio Policy thu hồi focus
        bgScope.launch {
            playSilentFocusTriggerPulseAsync()
            delay(350L)
            requestPermanentAudioFocus()
            pauseActiveMediaSessionsViaController()
            sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)

            delay(450L)
            pauseActiveMediaSessionsViaController()
            sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        }

        // 5. Giữ AudioFocus 6 giây rồi mới nhả để không chặn chuông cuộc gọi / báo thức
        releaseFocusJob?.cancel()
        releaseFocusJob = bgScope.launch {
            delay(6000L)
            abandonHeldAudioFocus()
        }
    }

    private fun requestPermanentAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val musicReq = heldMusicFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
                .also { heldMusicFocusRequest = it }

            val movieReq = heldMovieFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
                .also { heldMovieFocusRequest = it }

            runCatching { audioManager.requestAudioFocus(musicReq) }
            runCatching { audioManager.requestAudioFocus(movieReq) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun abandonHeldAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            heldMusicFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
            heldMovieFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(focusChangeListener) }
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

    private suspend fun playSilentFocusTriggerPulseAsync() {
        runCatching {
            val sampleRate = 8000
            val count = sampleRate / 5 // 200ms
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
            delay(210L)
            track.stop()
            track.release()
        }
    }

    fun pauseAllMedia() {
        pauseActiveMediaSessionsViaController()
        requestPermanentAudioFocus()
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        runCatching {
            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)
        }
    }

    /**
     * Tuyệt đối KHÔNG hạ âm lượng máy về 0 nữa; dừng nhạc/video tức thì và giữ nguyên âm lượng người dùng.
     */
    suspend fun fadeOutAndPauseMedia(fadeAudio: Boolean, durationMs: Long = 0L) {
        pauseAllMediaAndReleaseFocus()
    }
}
