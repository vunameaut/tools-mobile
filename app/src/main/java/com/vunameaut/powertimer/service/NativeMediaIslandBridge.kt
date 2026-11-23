package com.vunameaut.powertimer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.manager.ShutdownManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Cầu nối kích hoạt DUY NHẤT Dynamic Island GỐC CỦA HỆ THỐNG (giống hệt YouTube / YT Music / Spotify)
 * trên các dòng máy có sẵn Dynamic Island trong SystemUI (Vivo OriginOS Atomic Island, ColorOS Fluid Cloud,
 * Xiaomi HyperOS Super Island, Honor Magic Capsule, Samsung OneUI Now Bar).
 */
object NativeMediaIslandBridge {

    private var mediaSession: MediaSession? = null
    private var silentAudioTrack: AudioTrack? = null

    // Double-buffering 2 Bitmap luân phiên mỗi giây để SystemUI luôn nhận diện có Bitmap mới và vẽ lại số giây!
    private var cachedArtworkA: Bitmap? = null
    private var cachedArtworkB: Bitmap? = null
    private var useBitmapA: Boolean = true
    private var lastArtworkKey: String = ""
    private var lastRenderedArtwork: Bitmap? = null

    fun startOrUpdate(
        context: Context,
        remainingSeconds: Long,
        totalSeconds: Long,
        mode: ShutdownMode
    ) {
        val appCtx = context.applicationContext
        if (!SystemDynamicIslandManager.isEnabled(appCtx)) {
            stop()
            return
        }

        val isNewSession = (mediaSession == null)
        val session = ensureMediaSession(appCtx)
        if (isNewSession) {
            pulseSilentMediaInitOnce()
        }

        val style = SystemDynamicIslandManager.getStyle(appCtx)
        val formatted = formatTime(remainingSeconds)
        val totalMs = (totalSeconds.coerceAtLeast(1L)) * 1000L
        val elapsedMs = ((totalSeconds - remainingSeconds).coerceAtLeast(0L)) * 1000L

        val artwork = getOrRenderArtwork(formatted, remainingSeconds, totalSeconds, style)

        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO
            )
            .setState(
                PlaybackState.STATE_PLAYING,
                elapsedMs,
                1.0f,
                SystemClock.elapsedRealtime()
            )
            .build()
        session.setPlaybackState(playbackState)

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "⏳ $formatted • Tắt màn hình")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "${mode.badge} • ⏭ +10p • ⏮ Khóa")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Tools Hub PowerTimer")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, totalMs)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork)
            .build()
        session.setMetadata(metadata)
        if (!session.isActive) {
            session.isActive = true
        }
    }

    /**
     * Đánh thức viên thuốc con nhộng của hệ thống khi người dùng rời màn hình ứng dụng
     * (tránh trường hợp OriginOS / HyperOS không tự thu nhỏ MediaSession vừa mới tạo khi app vừa mất focus).
     */
    fun nudgeCapsulePlayback(context: Context) {
        val appCtx = context.applicationContext
        val state = TimerForegroundService.timerFlow.value
        if (!state.isRunning || !SystemDynamicIslandManager.isEnabled(appCtx)) return
        pulseSilentMediaInitOnce()
        startOrUpdate(appCtx, state.remainingSeconds, state.totalSeconds, state.selectedMode)
    }

    fun getSessionToken(context: Context): MediaSession.Token? {
        if (!SystemDynamicIslandManager.isEnabled(context)) return null
        return ensureMediaSession(context.applicationContext).sessionToken
    }

    fun getCurrentArtwork(context: Context, remainingSeconds: Long, totalSeconds: Long): Bitmap {
        val style = SystemDynamicIslandManager.getStyle(context)
        return getOrRenderArtwork(formatTime(remainingSeconds), remainingSeconds, totalSeconds, style)
    }

    fun stop() {
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null

        runCatching {
            silentAudioTrack?.stop()
            silentAudioTrack?.release()
        }
        silentAudioTrack = null
        lastArtworkKey = ""
        lastRenderedArtwork = null
    }

    private fun ensureMediaSession(context: Context): MediaSession {
        mediaSession?.let { return it }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("OPEN_TOOL_ID", "power_timer")
        }
        val sessionActivityPi = PendingIntent.getActivity(
            context,
            9901,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return MediaSession(context, "ToolsHubDynamicIsland").apply {
            setSessionActivity(sessionActivityPi)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    val state = TimerForegroundService.timerFlow.value
                    if (!state.isRunning) {
                        TimerForegroundService.startTimer(context, 30 * 60L, ShutdownMode.BANK_SAFE_SLEEP)
                    }
                }

                override fun onPause() {
                    SystemDynamicIslandManager.openAppFromOverlay(context)
                }

                override fun onStop() {
                    TimerForegroundService.stopTimer(context)
                }

                override fun onSkipToNext() {
                    TimerForegroundService.addTenMinutes(context)
                }

                override fun onSkipToPrevious() {
                    val mode = TimerForegroundService.timerFlow.value.selectedMode
                    TimerForegroundService.stopTimer(context)
                    CoroutineScope(Dispatchers.Main).launch {
                        ShutdownManager(context).executeShutdown(mode, fadeAudio = false)
                    }
                }

                override fun onSeekTo(pos: Long) {
                    // Giữ nguyên bộ đếm
                }
            })
            isActive = true
            mediaSession = this
        }
    }

    /**
     * Phát 1 xung âm thanh tĩnh cực ngắn (140ms) rồi GIẢI PHÓNG NGAY,
     * tuyệt đối không chiếm thanh Âm lượng hay chặn chuông cuộc gọi / báo thức.
     */
    private fun pulseSilentMediaInitOnce() {
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                silentAudioTrack?.release()
                silentAudioTrack = null

                val sampleRate = 8000
                val sampleCount = (sampleRate * 14) / 100 // 140ms
                val samples = ShortArray(sampleCount) { 0 }
                val byteSize = sampleCount * 2

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
                    .setBufferSizeInBytes(byteSize)
                    .build()

                track.write(samples, 0, sampleCount)
                track.setVolume(0.0f)
                track.play()
                kotlinx.coroutines.delay(150L)
                track.stop()
                track.release()
            }
        }
    }

    private fun getOrRenderArtwork(
        formattedTime: String,
        remainingSeconds: Long,
        totalSeconds: Long,
        style: IslandDisplayStyle
    ): Bitmap {
        val key = "${style.id}_$formattedTime"
        val existing = lastRenderedArtwork
        if (existing != null && lastArtworkKey == key && !existing.isRecycled) {
            return existing
        }
        lastArtworkKey = key

        val size = 192
        useBitmapA = !useBitmapA
        val bmp = if (useBitmapA) {
            cachedArtworkA?.takeIf { !it.isRecycled }
                ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { cachedArtworkA = it }
        } else {
            cachedArtworkB?.takeIf { !it.isRecycled }
                ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { cachedArtworkB = it }
        }

        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#05070E"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size * 0.44f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 16f
        paint.color = Color.parseColor("#1E293B")
        canvas.drawCircle(cx, cy, radius, paint)

        val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1L).toFloat()).coerceIn(0.04f, 1f)
        val ringColors = when (style) {
            IslandDisplayStyle.NEON_CYBER -> intArrayOf(
                Color.parseColor("#00E5FF"),
                Color.parseColor("#A855F7"),
                Color.parseColor("#EC4899")
            )
            IslandDisplayStyle.LIQUID_WAVE -> intArrayOf(
                Color.parseColor("#EC4899"),
                Color.parseColor("#6366F1"),
                Color.parseColor("#06B6D4")
            )
            IslandDisplayStyle.MINIMAL_IOS -> intArrayOf(
                Color.parseColor("#F59E0B"),
                Color.parseColor("#22C55E"),
                Color.parseColor("#10B981")
            )
            else -> intArrayOf(
                Color.parseColor("#22C55E"),
                Color.parseColor("#00E5FF"),
                Color.parseColor("#3B82F6")
            )
        }

        paint.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            ringColors,
            null,
            Shader.TileMode.CLAMP
        )
        paint.strokeCap = Paint.Cap.ROUND
        val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arcRect, -90f, 360f * progress, false, paint)
        paint.shader = null

        if (style == IslandDisplayStyle.LIQUID_WAVE) {
            val wavePath = Path()
            wavePath.moveTo(24f, cy + 36f)
            var x = 24f
            while (x <= size - 24f) {
                val y = cy + 34f + sin((x / size) * 4.0 * Math.PI).toFloat() * 6f
                wavePath.lineTo(x, y)
                x += 8f
            }
            paint.strokeWidth = 4f
            paint.color = Color.parseColor("#EC4899")
            canvas.drawPath(wavePath, paint)
        }

        // Hiển thị đầy đủ cả số giây (MM:SS hoặc H:MM:SS) rõ nét ngay giữa vòng tròn Artwork của viên thuốc
        paint.style = Paint.Style.FILL
        paint.color = when (style) {
            IslandDisplayStyle.NEON_CYBER -> Color.parseColor("#00E5FF")
            IslandDisplayStyle.LIQUID_WAVE -> Color.WHITE
            IslandDisplayStyle.MINIMAL_IOS -> Color.parseColor("#FACC15")
            else -> Color.parseColor("#4ADE80")
        }
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = if (formattedTime.length > 5) 33f else 48f
        paint.textAlign = Paint.Align.CENTER
        val textY = cy + (if (formattedTime.length > 5) 11f else 16f)
        canvas.drawText(formattedTime, cx, textY, paint)

        lastRenderedArtwork = bmp
        return bmp
    }

    private fun formatTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val hrs = s / 3600
        val mins = (s % 3600) / 60
        val secs = s % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
