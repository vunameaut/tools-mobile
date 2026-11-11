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
import android.os.Build
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
 * trên các dòng máy có sẵn Dynamic Island trong SystemUI (ColorOS/RealmeUI/OxygenOS Fluid Cloud,
 * Vivo OriginOS Atomic Island, Xiaomi HyperOS Super Island, Honor Magic Capsule, Samsung OneUI Now Bar).
 *
 * Lưu ý quan trọng:
 * - Chỉ sử dụng Dynamic Island gốc của hệ thống (qua MediaSession + Notification.MediaStyle + Silent AudioTrack),
 *   TUYỆT ĐỐI KHÔNG vẽ thêm lớp phủ WindowManager đè lên trên để không bao giờ bị hiện 2 viên thuốc chồng nhau!
 */
object NativeMediaIslandBridge {

    private var mediaSession: MediaSession? = null
    private var silentAudioTrack: AudioTrack? = null
    private var cachedArtwork: Bitmap? = null
    private var lastArtworkKey: String = ""

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

        ensureSilentMediaStreamRunning()
        val session = ensureMediaSession(appCtx)

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
            .putString(MediaMetadata.METADATA_KEY_TITLE, "⏳ Còn $formatted • Hẹn giờ tắt màn")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "${mode.badge} • Bấm ⏭ +10p • ⏮ Tắt màn")
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
                    // Bấm nút giữa trên Dynamic Island -> Mở nhanh vào lại App hoặc cộng 10 phút
                    SystemDynamicIslandManager.openAppFromOverlay(context)
                }

                override fun onStop() {
                    TimerForegroundService.stopTimer(context)
                }

                override fun onSkipToNext() {
                    // Nút Next (⏭) trên Dynamic Island gốc -> Cộng nhanh +10 phút!
                    TimerForegroundService.addTenMinutes(context)
                }

                override fun onSkipToPrevious() {
                    // Nút Previous (⏮) trên Dynamic Island gốc -> Tắt màn hình ngay lập tức!
                    val mode = TimerForegroundService.timerFlow.value.selectedMode
                    TimerForegroundService.stopTimer(context)
                    CoroutineScope(Dispatchers.Main).launch {
                        ShutdownManager(context).executeShutdown(mode, fadeAudio = false, enableDnd = false)
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
     * Phát luồng PCM siêu tĩnh (MODE_STATIC lặp vô hạn trong phần cứng, 0% CPU, không chiếm AudioFocus)
     * để SystemUI (Fluid Cloud / Atomic Island / HyperIsland) nhận diện ứng dụng đang phát Media giống YouTube.
     */
    private fun ensureSilentMediaStreamRunning() {
        val current = silentAudioTrack
        if (current != null && current.state == AudioTrack.STATE_INITIALIZED &&
            current.playState == AudioTrack.PLAYSTATE_PLAYING
        ) {
            return
        }

        runCatching {
            current?.release()
            val sampleRate = 8000
            val sampleCount = sampleRate
            val samples = ShortArray(sampleCount) { idx -> if (idx % 2 == 0) 1 else -1 }
            val byteSize = sampleCount * 2

            val builder = AudioTrack.Builder()
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

            val track = builder.build()
            track.write(samples, 0, sampleCount)
            track.setLoopPoints(0, sampleCount, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                track.setVolume(0.001f)
            }
            track.play()
            silentAudioTrack = track
        }
    }

    private fun getOrRenderArtwork(
        formattedTime: String,
        remainingSeconds: Long,
        totalSeconds: Long,
        style: IslandDisplayStyle
    ): Bitmap {
        val shortTime = formattedTime.take(5)
        val key = "${style.id}_$shortTime"
        val existing = cachedArtwork
        if (existing != null && lastArtworkKey == key) {
            return existing
        }
        lastArtworkKey = key

        val size = 192
        val bmp = existing ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#05070E"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size * 0.44f

        // Vòng tròn nền
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 16f
        paint.color = Color.parseColor("#1E293B")
        canvas.drawCircle(cx, cy, radius, paint)

        // Vòng cung tiến trình tùy theo kiểu hiển thị người dùng chọn
        val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1L).toFloat()).coerceIn(0.05f, 1f)
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

        // Chữ thời gian (MM:SS) to, đậm, rõ nét ngay giữa vòng tròn của Dynamic Island gốc
        paint.style = Paint.Style.FILL
        paint.color = when (style) {
            IslandDisplayStyle.NEON_CYBER -> Color.parseColor("#00E5FF")
            IslandDisplayStyle.LIQUID_WAVE -> Color.WHITE
            IslandDisplayStyle.MINIMAL_IOS -> Color.parseColor("#FACC15")
            else -> Color.parseColor("#4ADE80")
        }
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(shortTime, cx, cy + 16f, paint)

        cachedArtwork = bmp
        return bmp
    }

    private fun formatTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val hrs = s / 3600
        val mins = (s % 3600) / 60
        val secs = s % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
