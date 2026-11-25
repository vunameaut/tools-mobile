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

        @Volatile
        private var heldMusicFocusRequest: AudioFocusRequest? = null

        @Volatile
        private var heldMovieFocusRequest: AudioFocusRequest? = null

        @Volatile
        private var heldVoiceFocusRequest: AudioFocusRequest? = null

        private val musicFocusListener = AudioManager.OnAudioFocusChangeListener { }
        private val movieFocusListener = AudioManager.OnAudioFocusChangeListener { }
        private val voiceFocusListener = AudioManager.OnAudioFocusChangeListener { }
        private var releaseFocusJob: Job? = null
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Dừng triệt để mọi nhạc & video đang phát (TikTok / Douyin / Facebook / YouTube / Spotify / Zing MP3...)
     * và GIỮ CHẶT AudioFocus cho đến khi màn hình tắt hẳn (hoặc tối đa [maxHoldMs] giây).
     * Tuyệt đối KHÔNG hạ âm lượng máy về 0.
     */
    suspend fun holdMediaPauseFocus(maxHoldMs: Long = ShutdownEnforcementPolicy.FULL_LOCK_MAX_FOCUS_HOLD_MS) {
        restoreStuckZeroVolumeIfNeeded()

        // 1. Dừng trực tiếp các MediaSession đang hoạt động (Facebook, YouTube, Spotify...)
        pauseActiveMediaSessionsViaController()

        // 2. Xin AUDIOFOCUS_GAIN (Music + Movie) và AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE (Voice Call simulation cho TikTok)
        requestPermanentAudioFocus()

        // 3. Gửi phím cứng Media Pause & Stop
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)

        // 4. Phát xung âm thanh micro-PCM trên luồng nền để AudioFlinger cưỡng bức thu hồi focus khỏi TikTok/FB
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

        // 5. Giữ AudioFocus xuyên suốt cửa sổ chờ tắt màn hình (mặc định 42s, giải phóng sớm ngay khi ACTION_SCREEN_OFF)
        releaseFocusJob?.cancel()
        releaseFocusJob = bgScope.launch {
            delay(maxHoldMs.coerceAtLeast(1_000L))
            abandonHeldAudioFocus()
        }
    }

    suspend fun pauseAllMediaAndReleaseFocus(holdDurationMs: Long = ShutdownEnforcementPolicy.MEDIA_ONLY_FOCUS_HOLD_MS) {
        holdMediaPauseFocus(maxHoldMs = holdDurationMs)
    }

    fun releaseHeldMediaFocus() {
        releaseFocusJob?.cancel()
        releaseFocusJob = null
        abandonHeldAudioFocus()
    }

    private fun restoreStuckZeroVolumeIfNeeded() {
        runCatching {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol == 0 && maxVol > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol / 3).coerceAtLeast(3), 0)
            }
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
                .setOnAudioFocusChangeListener(musicFocusListener)
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
                .setOnAudioFocusChangeListener(movieFocusListener)
                .build()
                .also { heldMovieFocusRequest = it }

            // Giả lập tiêu điểm cuộc gọi thoại (EXCLUSIVE) để ép trình phát TTVideoEngine của TikTok dừng phát
            val voiceReq = heldVoiceFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(voiceFocusListener)
                .build()
                .also { heldVoiceFocusRequest = it }

            runCatching { audioManager.requestAudioFocus(musicReq) }
            runCatching { audioManager.requestAudioFocus(movieReq) }
            runCatching { audioManager.requestAudioFocus(voiceReq) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.requestAudioFocus(
                    musicFocusListener,
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
            heldVoiceFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(musicFocusListener) }
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
            // Dùng giá trị PCM khác 0 siêu nhỏ (1/32767) để AudioFlinger Vivo OriginOS kích hoạt chính sách AudioFocus thực
            val samples = ShortArray(count) { 1 }
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
            track.setVolume(0.02f)
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

    suspend fun fadeOutAndPauseMedia(fadeAudio: Boolean, durationMs: Long = 0L) {
        pauseAllMediaAndReleaseFocus()
    }
}
