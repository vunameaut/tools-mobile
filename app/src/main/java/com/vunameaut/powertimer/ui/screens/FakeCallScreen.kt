package com.vunameaut.powertimer.ui.screens

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentRed
import com.vunameaut.powertimer.ui.theme.PowerTimerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

data class FakeCallerPreset(
    val label: String,
    val callerName: String,
    val phoneNumber: String,
    val carrierLabel: String,
    val avatarEmoji: String,
    val voiceScript: String
)

private val FAKE_CALLER_PRESETS = listOf(
    FakeCallerPreset(
        label = "👔 Sếp Tổng",
        callerName = "Sếp Hoàng (Giám đốc)",
        phoneNumber = "0912 388 999",
        carrierLabel = "Viettel 5G • Cuộc gọi thoại HD",
        avatarEmoji = "👔",
        voiceScript = "Alo em đang ở đâu đấy? Về văn phòng ngay nhé, khách hàng đang đợi ký hợp đồng gấp lắm rồi!"
    ),
    FakeCallerPreset(
        label = "👩 Mẹ Yêu",
        callerName = "Mẹ Yêu ❤️",
        phoneNumber = "0988 456 123",
        carrierLabel = "Viettel • Di động",
        avatarEmoji = "👩",
        voiceScript = "Alo con à, về nhà ngay mẹ bảo cái này gấp lắm, cả nhà đang chờ con ở nhà đấy nhé!"
    ),
    FakeCallerPreset(
        label = "💕 Vợ / Người Yêu",
        callerName = "Vợ Yêu 💕",
        phoneNumber = "0904 666 888",
        carrierLabel = "MobiFone 5G • Cuộc gọi HD",
        avatarEmoji = "💕",
        voiceScript = "Alo anh đang ngồi ở đâu đấy? Về đón em ngay đi, muộn lắm rồi em đang đứng đợi đây này!"
    ),
    FakeCallerPreset(
        label = "📦 Shipper Giao Hàng",
        callerName = "Shipper Giao Hàng Nhanh",
        phoneNumber = "0973 555 247",
        carrierLabel = "VinaPhone • Di động",
        avatarEmoji = "📦",
        voiceScript = "Alo anh ơi em giao đơn hàng đứng ở dưới cổng nhà mình rồi nhé, anh về nhận giúp em với ạ!"
    ),
    FakeCallerPreset(
        label = "🏢 Bảo Vệ Tòa Nhà",
        callerName = "Bảo Vệ Hầm Xe",
        phoneNumber = "0936 112 911",
        carrierLabel = "Viettel • Cuộc gọi khẩn",
        avatarEmoji = "🏢",
        voiceScript = "Alo anh ơi xuống hầm dắt lại xe giúp em ngay nhé, xe đang đỗ chắn lối ra vào rồi ạ!"
    )
)

/**
 * LifecycleOwner độc lập phục vụ hiển thị giao diện Compose Cuộc gọi đến Toàn màn hình
 * trực tiếp qua WindowManager (TYPE_APPLICATION_OVERLAY) khi người dùng đã thoát khỏi ứng dụng.
 */
private class FakeCallOverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        runCatching {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}

object FakeCallScheduler {
    const val PREF_NAME = "fake_call_prefs"
    const val KEY_SCHEDULED_AT = "scheduled_trigger_ms"
    const val KEY_CALLER_NAME = "caller_name"
    const val KEY_CALLER_NUMBER = "caller_number"
    const val KEY_CARRIER = "caller_carrier"
    const val KEY_AVATAR = "caller_avatar"
    const val KEY_VOICE_SCRIPT = "caller_voice_script"
    const val KEY_AUTO_SPEAK = "caller_auto_speak"
    const val KEY_USE_CUSTOM_AUDIO = "caller_use_custom_audio"
    const val KEY_CUSTOM_AUDIO_PATH = "caller_custom_audio_path"
    const val KEY_CUSTOM_AUDIO_NAME = "caller_custom_audio_name"

    const val ALARM_REQ_CODE = 88991
    const val COUNTDOWN_NOTIF_ID = 88990
    const val CHANNEL_ID = "fake_incoming_call_channel"
    const val COUNTDOWN_CHANNEL_ID = "fake_call_countdown_channel"

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeVoicePlayer: MediaPlayer? = null
    private var ringtonePlayer: MediaPlayer? = null
    private var activeVibrator: Vibrator? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: FakeCallOverlayLifecycleOwner? = null
    var activeCallActivity: FakeCallIncomingActivity? = null

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Tải trước (Pre-cache) file âm thanh giọng đọc Tiếng Việt tự nhiên (tl=vi) vào bộ nhớ đệm.
     */
    fun preCacheVietnameseVoice(context: Context, script: String) {
        val clean = script.trim()
        if (clean.isBlank()) return
        val appCtx = context.applicationContext
        mainScope.launch(Dispatchers.IO) {
            getOrDownloadVietnameseVoiceMp3(appCtx, clean)
        }
    }

    fun getOrDownloadVietnameseVoiceMp3(context: Context, script: String): File? {
        val clean = script.trim().take(195)
        if (clean.isBlank()) return null
        return runCatching {
            val cacheDir = File(context.cacheDir, "soundbox_tts_cache").apply { mkdirs() }
            val hash = MessageDigest.getInstance("MD5")
                .digest(clean.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val audioFile = File(cacheDir, "vi_call_$hash.mp3")

            if (!audioFile.exists() || audioFile.length() < 256L) {
                val encoded = URLEncoder.encode(clean, "UTF-8")
                val urlStr = "https://translate.google.com/translate_tts?ie=UTF-8&tl=vi&client=tw-ob&q=$encoded"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 5000
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return null
                }
                conn.inputStream.use { input ->
                    audioFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                conn.disconnect()
            }
            if (audioFile.exists() && audioFile.length() >= 256L) audioFile else null
        }.getOrNull()
    }

    fun playVietnameseVoiceScript(
        context: Context,
        script: String,
        useSpeaker: Boolean = true,
        fallbackTts: TextToSpeech? = null
    ) {
        val clean = script.trim()
        if (clean.isBlank()) return
        val appCtx = context.applicationContext

        mainScope.launch {
            runCatching {
                val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (cur == 0 && max > 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.65f).toInt().coerceAtLeast(1), 0)
                }
            }

            val mp3File = withContext(Dispatchers.IO) {
                getOrDownloadVietnameseVoiceMp3(appCtx, clean)
            }

            if (mp3File != null) {
                runCatching {
                    stopActiveVoice()
                    val mp = MediaPlayer()
                    activeVoicePlayer = mp
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    mp.setDataSource(mp3File.absolutePath)
                    mp.prepare()
                    val vol = if (useSpeaker) 1.0f else 0.55f
                    mp.setVolume(vol, vol)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching {
                            mp.playbackParams = PlaybackParams().setSpeed(1.08f)
                        }
                    }
                    mp.setOnCompletionListener { player ->
                        player.release()
                        if (activeVoicePlayer === player) activeVoicePlayer = null
                    }
                    mp.start()
                    return@launch
                }
            }

            fallbackTts?.let { engine ->
                runCatching {
                    val viLocale = Locale.forLanguageTag("vi-VN")
                    engine.language = viLocale
                    val viVoice = engine.voices?.firstOrNull {
                        it.locale.language.equals("vi", ignoreCase = true)
                    }
                    if (viVoice != null) {
                        engine.voice = viVoice
                    }
                    val params = Bundle().apply {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                    }
                    engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, "fake_call_vi_fallback")
                }
            }
        }
    }

    fun stopActiveVoice() {
        runCatching {
            activeVoicePlayer?.stop()
            activeVoicePlayer?.release()
        }
        activeVoicePlayer = null
    }

    fun importCustomAudioFromUri(context: Context, uri: Uri): Pair<String, String>? {
        val appCtx = context.applicationContext
        return runCatching {
            var displayName = "cuoc_goi_gia.mp3"
            appCtx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                }
            }

            val ext = displayName.substringAfterLast('.', "mp3")
                .lowercase(Locale.US)
                .takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }
                ?: "mp3"

            val audioDir = File(appCtx.filesDir, "fake_call_audio").apply { mkdirs() }
            audioDir.listFiles()?.forEach { oldFile ->
                runCatching { oldFile.delete() }
            }

            val destFile = File(audioDir, "custom_call_audio_${System.currentTimeMillis()}.$ext")
            appCtx.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!destFile.exists() || destFile.length() <= 0L) {
                runCatching { destFile.delete() }
                return null
            }

            appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CUSTOM_AUDIO_PATH, destFile.absolutePath)
                .putString(KEY_CUSTOM_AUDIO_NAME, displayName)
                .putBoolean(KEY_USE_CUSTOM_AUDIO, true)
                .apply()

            Pair(destFile.absolutePath, displayName)
        }.getOrNull()
    }

    fun clearCustomAudio(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val oldPath = prefs.getString(KEY_CUSTOM_AUDIO_PATH, "") ?: ""
        if (oldPath.isNotBlank()) {
            runCatching { File(oldPath).delete() }
        }
        prefs.edit()
            .remove(KEY_CUSTOM_AUDIO_PATH)
            .remove(KEY_CUSTOM_AUDIO_NAME)
            .putBoolean(KEY_USE_CUSTOM_AUDIO, false)
            .apply()
    }

    fun updateActiveVoiceVolume(useSpeaker: Boolean): Boolean {
        val player = activeVoicePlayer ?: return false
        return runCatching {
            if (player.isPlaying) {
                val vol = if (useSpeaker) 1.0f else 0.55f
                player.setVolume(vol, vol)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun playCustomAudioFile(
        context: Context,
        audioPath: String,
        useSpeaker: Boolean = true,
        onCompletion: (() -> Unit)? = null
    ): Boolean {
        val file = File(audioPath)
        if (!file.exists() || file.length() <= 0L) return false
        val appCtx = context.applicationContext

        return runCatching {
            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (cur == 0 && max > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.65f).toInt().coerceAtLeast(1), 0)
            }

            stopActiveVoice()
            val mp = MediaPlayer()
            activeVoicePlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            val vol = if (useSpeaker) 1.0f else 0.55f
            mp.setVolume(vol, vol)
            mp.setOnCompletionListener { player ->
                player.release()
                if (activeVoicePlayer === player) activeVoicePlayer = null
                onCompletion?.invoke()
            }
            mp.start()
            true
        }.getOrDefault(false)
    }

    fun playConfiguredCallAudio(
        context: Context,
        script: String,
        useCustomAudio: Boolean,
        customAudioPath: String,
        useSpeaker: Boolean = true,
        fallbackTts: TextToSpeech? = null
    ) {
        if (useCustomAudio && customAudioPath.isNotBlank()) {
            val played = playCustomAudioFile(
                context = context,
                audioPath = customAudioPath,
                useSpeaker = useSpeaker
            )
            if (played) return
        }
        if (script.isNotBlank()) {
            playVietnameseVoiceScript(
                context = context,
                script = script,
                useSpeaker = useSpeaker,
                fallbackTts = fallbackTts
            )
        }
    }

    /**
     * Phát nhạc chuông & rung trực tiếp từ cấp độ Service/Application (kể cả khi người dùng đã thoát khỏi app).
     * Nếu điện thoại đang để chế độ Rung / Im lặng chuông (STREAM_RING = 0), tự động phát nhạc chuông qua kênh MEDIA
     * để cuộc gọi giả luôn đổ chuông lớn và chân thực.
     */
    fun startRingingAndVibration(context: Context) {
        val appCtx = context.applicationContext
        if (ringtonePlayer?.isPlaying == true) return

        stopRingingAndVibration()

        runCatching {
            val pm = appCtx.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "PowerTimer:FakeCallScreenWake"
            )
            wl.acquire(45_000L)
            screenWakeLock = wl
        }

        runCatching {
            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val ringVol = am.getStreamVolume(AudioManager.STREAM_RING)
            val useMediaFallback = am.ringerMode != AudioManager.RINGER_MODE_NORMAL || ringVol == 0

            if (useMediaFallback) {
                val maxMedia = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val curMedia = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (curMedia < (maxMedia * 0.5f).toInt()) {
                    runCatching {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxMedia * 0.7f).toInt().coerceAtLeast(1), 0)
                    }
                }
            }

            val ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            ringtonePlayer = MediaPlayer().apply {
                setDataSource(appCtx, ringUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (useMediaFallback) AudioAttributes.USAGE_MEDIA
                            else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }

        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appCtx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            activeVibrator = vib
            val pattern = longArrayOf(0, 1000, 800, 1000, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        }
    }

    fun stopRingingAndVibration() {
        runCatching {
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
        }
        ringtonePlayer = null
        runCatching { activeVibrator?.cancel() }
        activeVibrator = null
        runCatching {
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
        }
        screenWakeLock = null
    }

    /**
     * Hiển thị giao diện Cuộc gọi đến Toàn màn hình trực tiếp qua WindowManager (TYPE_APPLICATION_OVERLAY).
     * Giúp cuộc gọi giả bật lên 100% ngay cả khi người dùng đã thoát app ra Màn hình chính, đang lướt Facebook/Zalo/TikTok,
     * vượt qua cơ chế chặn Background Activity Launch của Android 12-15 & OriginOS/HyperOS.
     */
    fun showFullScreenIncomingCallOverlay(context: Context): Boolean {
        val appCtx = context.applicationContext
        if (!canDrawOverlays(appCtx)) return false

        mainScope.launch(Dispatchers.Main) {
            dismissOverlayWindowOnly()

            val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val callerName = prefs.getString(KEY_CALLER_NAME, "Sếp Hoàng (Giám đốc)") ?: "Sếp Hoàng"
            val callerNumber = prefs.getString(KEY_CALLER_NUMBER, "0912 388 999") ?: "0912 388 999"
            val carrier = prefs.getString(KEY_CARRIER, "Viettel 5G • Cuộc gọi thoại HD") ?: "Viettel 5G"
            val avatarEmoji = prefs.getString(KEY_AVATAR, "👔") ?: "👔"
            val voiceScript = prefs.getString(KEY_VOICE_SCRIPT, "") ?: ""
            val autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true)
            val useCustomAudio = prefs.getBoolean(KEY_USE_CUSTOM_AUDIO, false)
            val customAudioPath = prefs.getString(KEY_CUSTOM_AUDIO_PATH, "") ?: ""

            val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@launch
            val owner = FakeCallOverlayLifecycleOwner().apply { onCreate() }
            overlayLifecycleOwner = owner

            val composeView = ComposeView(appCtx).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    PowerTimerTheme {
                        FakeIncomingCallScreenContent(
                            callerName = callerName,
                            callerNumber = callerNumber,
                            carrierLabel = carrier,
                            avatarEmoji = avatarEmoji,
                            onAnswer = {
                                stopRingingAndVibration()
                                cancelCallNotifications(appCtx)
                                if (autoSpeak) {
                                    playConfiguredCallAudio(
                                        context = appCtx,
                                        script = voiceScript,
                                        useCustomAudio = useCustomAudio,
                                        customAudioPath = customAudioPath,
                                        useSpeaker = false
                                    )
                                }
                            },
                            onToggleSpeaker = { speakerOn ->
                                if (!updateActiveVoiceVolume(speakerOn) && !useCustomAudio && voiceScript.isNotBlank()) {
                                    playVietnameseVoiceScript(
                                        context = appCtx,
                                        script = voiceScript,
                                        useSpeaker = speakerOn
                                    )
                                }
                            },
                            onReplayVoice = { speakerOn ->
                                playConfiguredCallAudio(
                                    context = appCtx,
                                    script = voiceScript,
                                    useCustomAudio = useCustomAudio,
                                    customAudioPath = customAudioPath,
                                    useSpeaker = speakerOn
                                )
                            },
                            onEndCall = {
                                endActiveFakeCall(appCtx)
                            }
                        )
                    }
                }
            }

            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            runCatching {
                wm.addView(composeView, lp)
                overlayView = composeView
            }
        }
        return true
    }

    fun dismissOverlayWindowOnly() {
        val view = overlayView
        val owner = overlayLifecycleOwner
        overlayView = null
        overlayLifecycleOwner = null

        if (view != null) {
            runCatching {
                val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeViewImmediate(view)
            }
        }
        owner?.onDestroy()
    }

    fun cancelCallNotifications(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ALARM_REQ_CODE)
            nm.cancel(COUNTDOWN_NOTIF_ID)
        }
    }

    fun endActiveFakeCall(context: Context) {
        val appCtx = context.applicationContext
        stopRingingAndVibration()
        stopActiveVoice()
        dismissOverlayWindowOnly()
        cancelCallNotifications(appCtx)
        runCatching {
            activeCallActivity?.finish()
            activeCallActivity = null
        }
        runCatching {
            appCtx.stopService(Intent(appCtx, FakeCallService::class.java))
        }
    }

    fun scheduleFakeCall(
        context: Context,
        delaySeconds: Int,
        callerName: String,
        phoneNumber: String,
        carrier: String,
        avatarEmoji: String,
        voiceScript: String,
        autoSpeakOnAnswer: Boolean,
        useCustomAudio: Boolean = false
    ) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        prefs.edit()
            .putLong(KEY_SCHEDULED_AT, if (delaySeconds > 0) triggerAt else 0L)
            .putString(KEY_CALLER_NAME, callerName)
            .putString(KEY_CALLER_NUMBER, phoneNumber)
            .putString(KEY_CARRIER, carrier)
            .putString(KEY_AVATAR, avatarEmoji)
            .putString(KEY_VOICE_SCRIPT, voiceScript)
            .putBoolean(KEY_AUTO_SPEAK, autoSpeakOnAnswer)
            .putBoolean(KEY_USE_CUSTOM_AUDIO, useCustomAudio)
            .apply()

        if (!useCustomAudio && voiceScript.isNotBlank()) {
            preCacheVietnameseVoice(appCtx, voiceScript)
        }

        if (delaySeconds <= 0) {
            launchIncomingCallNow(appCtx)
            return
        }

        // 1. Khởi chạy Foreground Service đếm ngược ngầm (giữ WakeLock để không bị hệ thống đóng băng khi thoát app)
        val serviceIntent = Intent(appCtx, FakeCallService::class.java).apply {
            action = FakeCallService.ACTION_START_COUNTDOWN
            putExtra(FakeCallService.EXTRA_TRIGGER_AT_MS, triggerAt)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(serviceIntent)
            } else {
                appCtx.startService(serviceIntent)
            }
        }

        // 2. Đăng ký thêm AlarmClock cấp hệ thống (được Android miễn trừ quyền mở Activity nền)
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val broadcastIntent = Intent(appCtx, FakeCallAlarmReceiver::class.java)
        val broadcastPi = PendingIntent.getBroadcast(
            appCtx,
            ALARM_REQ_CODE,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showActivityIntent = Intent(appCtx, FakeCallIncomingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val showActivityPi = PendingIntent.getActivity(
            appCtx,
            ALARM_REQ_CODE + 1,
            showActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            val clockInfo = AlarmManager.AlarmClockInfo(triggerAt, showActivityPi)
            alarmManager.setAlarmClock(clockInfo, broadcastPi)
        }.onFailure {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, broadcastPi)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, broadcastPi)
                }
            }
        }
    }

    fun cancelScheduledCall(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_SCHEDULED_AT, 0L).apply()

        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appCtx, FakeCallAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appCtx,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.cancel(pendingIntent) }

        endActiveFakeCall(appCtx)
    }

    fun launchIncomingCallNow(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_SCHEDULED_AT, 0L).apply()

        val callerName = prefs.getString(KEY_CALLER_NAME, "Sếp Hoàng (Giám đốc)") ?: "Sếp Hoàng"
        val callerNumber = prefs.getString(KEY_CALLER_NUMBER, "0912 388 999") ?: "0912 388 999"

        // Bước 1: Đánh thức màn hình + Phát nhạc chuông & Rung ngay lập tức ở cấp Service/Application
        startRingingAndVibration(appCtx)

        // Bước 2: Nếu đã có quyền Vẽ đè (Hiển thị trên ứng dụng khác), bung ngay màn hình Cuộc gọi đến Toàn màn hình
        // đè lên Màn hình chính / Zalo / TikTok (100% không bị chặn bởi Background Activity Launch)
        val overlayShown = showFullScreenIncomingCallOverlay(appCtx)

        val callIntent = Intent(appCtx, FakeCallIncomingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }

        // Bước 3: Gửi kèm Full-Screen Call Notification (có nút Nghe máy / Từ chối) để đánh thức màn hình khóa
        runCatching {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel(
                    CHANNEL_ID,
                    "Cuộc gọi đến giả lập",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Hiển thị giao diện cuộc gọi đến toàn màn hình"
                    setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(chan)
            }

            val fullScreenPi = PendingIntent.getActivity(
                appCtx,
                ALARM_REQ_CODE,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val declineIntent = Intent(appCtx, FakeCallService::class.java).apply {
                action = FakeCallService.ACTION_DECLINE_CALL
            }
            val declinePi = PendingIntent.getService(
                appCtx,
                ALARM_REQ_CODE + 2,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("📞 $callerName")
                .setContentText("Cuộc gọi đến: $callerNumber (Chạm để mở)")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPi, true)
                .setContentIntent(fullScreenPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Từ chối", declinePi)
                .addAction(android.R.drawable.ic_menu_call, "Trả lời", fullScreenPi)
                .setAutoCancel(false)
                .setOngoing(true)
                .build()
            nm.notify(ALARM_REQ_CODE, notif)

            // Trên Android 14+, thử kích hoạt PendingIntent với quyền Background Activity Start
            if (!overlayShown) {
                if (Build.VERSION.SDK_INT >= 34) {
                    val options = ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                    fullScreenPi.send(appCtx, 0, null, null, null, null, options.toBundle())
                }
            }
        }

        // Mở Activity khi màn hình đang khóa hoặc khi chưa bật quyền Overlay
        val km = appCtx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceLocked = km?.isKeyguardLocked == true
        if (!overlayShown || isDeviceLocked) {
            runCatching {
                appCtx.startActivity(callIntent)
            }
        }
    }
}

/**
 * Foreground Service giữ tiến trình đếm ngược Cuộc Gọi Giả sống 100% khi người dùng thoát ra ngoài ứng dụng
 * hoặc khóa màn hình điện thoại.
 */
class FakeCallService : Service() {

    companion object {
        const val ACTION_START_COUNTDOWN = "com.vunameaut.powertimer.FAKE_CALL_START_COUNTDOWN"
        const val ACTION_DECLINE_CALL = "com.vunameaut.powertimer.FAKE_CALL_DECLINE"
        const val ACTION_TRIGGER_NOW = "com.vunameaut.powertimer.FAKE_CALL_TRIGGER_NOW"
        const val EXTRA_TRIGGER_AT_MS = "extra_trigger_at_ms"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createCountdownChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DECLINE_CALL -> {
                FakeCallScheduler.cancelScheduledCall(applicationContext)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_NOW -> {
                FakeCallScheduler.launchIncomingCallNow(applicationContext)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_COUNTDOWN -> {
                val prefs = getSharedPreferences(FakeCallScheduler.PREF_NAME, Context.MODE_PRIVATE)
                val triggerAt = intent.getLongExtra(
                    EXTRA_TRIGGER_AT_MS,
                    prefs.getLong(FakeCallScheduler.KEY_SCHEDULED_AT, 0L)
                )
                val callerName = prefs.getString(FakeCallScheduler.KEY_CALLER_NAME, "Sếp Tổng") ?: "Sếp Tổng"
                val remainSec = ((triggerAt - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(1)

                startForeground(
                    FakeCallScheduler.COUNTDOWN_NOTIF_ID,
                    buildCountdownNotification(callerName, remainSec)
                )
                acquireCpuWakeLock((remainSec + 60) * 1000L)
                startCountdownLoop(triggerAt, callerName)
            }
        }
        return START_STICKY
    }

    private fun startCountdownLoop(triggerAt: Long, callerName: String) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            var lastNotifSec = -1
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now >= triggerAt) {
                    break
                }
                val remSec = ((triggerAt - now + 999L) / 1000L).toInt()
                if (remSec != lastNotifSec) {
                    lastNotifSec = remSec
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(
                        FakeCallScheduler.COUNTDOWN_NOTIF_ID,
                        buildCountdownNotification(callerName, remSec)
                    )
                }
                delay(250L)
            }

            // Đúng giờ hẹn -> Kích hoạt đổ chuông & bung màn hình Cuộc gọi giả ngay!
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(FakeCallScheduler.COUNTDOWN_NOTIF_ID)
            FakeCallScheduler.launchIncomingCallNow(applicationContext)
            stopSelf()
        }
    }

    private fun acquireCpuWakeLock(timeoutMs: Long) {
        runCatching {
            if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release()
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PowerTimer:FakeCallCountdownLock").apply {
                acquire(timeoutMs)
            }
        }
    }

    private fun createCountdownChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(
                FakeCallScheduler.COUNTDOWN_CHANNEL_ID,
                "Hẹn giờ Cuộc gọi giả",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Đếm ngược ngầm chờ kích hoạt cuộc gọi giả"
                setShowBadge(false)
            }
            nm.createNotificationChannel(chan)
        }
    }

    private fun buildCountdownNotification(callerName: String, remainSec: Int): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openAppPi = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, FakeCallService::class.java).apply {
            action = ACTION_DECLINE_CALL
        }
        val cancelPi = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callNowIntent = Intent(this, FakeCallService::class.java).apply {
            action = ACTION_TRIGGER_NOW
        }
        val callNowPi = PendingIntent.getService(
            this,
            2,
            callNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FakeCallScheduler.COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏳ Cuộc gọi giả từ $callerName sau ${remainSec}s")
            .setContentText("Bạn có thể thoát app hoặc khóa màn hình, máy sẽ tự đổ chuông")
            .setContentIntent(openAppPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Đổ chuông ngay", callNowPi)
            .addAction(0, "Hủy", cancelPi)
            .build()
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        runCatching {
            if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release()
        }
        super.onDestroy()
    }
}

class FakeCallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(FakeCallScheduler.PREF_NAME, Context.MODE_PRIVATE)
        val scheduledAt = prefs.getLong(FakeCallScheduler.KEY_SCHEDULED_AT, 0L)
        // Nếu FakeCallService chưa kích hoạt thì AlarmReceiver kích hoạt dự phòng
        if (scheduledAt != 0L) {
            FakeCallScheduler.launchIncomingCallNow(context)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeCallTool(onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler(onBack = onExit)

    val prefs = remember { context.getSharedPreferences(FakeCallScheduler.PREF_NAME, Context.MODE_PRIVATE) }
    var hasOverlayPermission by remember { mutableStateOf(FakeCallScheduler.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = FakeCallScheduler.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedPresetIdx by remember { mutableIntStateOf(0) }
    var callerName by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_CALLER_NAME, FAKE_CALLER_PRESETS[0].callerName) ?: FAKE_CALLER_PRESETS[0].callerName)
    }
    var phoneNumber by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_CALLER_NUMBER, FAKE_CALLER_PRESETS[0].phoneNumber) ?: FAKE_CALLER_PRESETS[0].phoneNumber)
    }
    var carrierLabel by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_CARRIER, FAKE_CALLER_PRESETS[0].carrierLabel) ?: FAKE_CALLER_PRESETS[0].carrierLabel)
    }
    var avatarEmoji by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_AVATAR, FAKE_CALLER_PRESETS[0].avatarEmoji) ?: FAKE_CALLER_PRESETS[0].avatarEmoji)
    }
    var voiceScript by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_VOICE_SCRIPT, FAKE_CALLER_PRESETS[0].voiceScript) ?: FAKE_CALLER_PRESETS[0].voiceScript)
    }
    var autoSpeakOnAnswer by remember {
        mutableStateOf(prefs.getBoolean(FakeCallScheduler.KEY_AUTO_SPEAK, true))
    }
    var useCustomAudio by remember {
        mutableStateOf(prefs.getBoolean(FakeCallScheduler.KEY_USE_CUSTOM_AUDIO, false))
    }
    var customAudioPath by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_CUSTOM_AUDIO_PATH, "") ?: "")
    }
    var customAudioName by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_CUSTOM_AUDIO_NAME, "") ?: "")
    }
    var isPreviewingCustomAudio by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            if (isPreviewingCustomAudio) {
                FakeCallScheduler.stopActiveVoice()
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val imported = withContext(Dispatchers.IO) {
                    FakeCallScheduler.importCustomAudioFromUri(context, uri)
                }
                if (imported != null) {
                    customAudioPath = imported.first
                    customAudioName = imported.second
                    useCustomAudio = true
                    isPreviewingCustomAudio = false
                    FakeCallScheduler.stopActiveVoice()
                    Toast.makeText(
                        context,
                        "✅ Đã tải lên file âm thanh: ${imported.second}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "❌ Không thể đọc file âm thanh này. Hãy thử chọn file .mp3 khác!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    var selectedDelaySec by remember { mutableIntStateOf(60) } // Mặc định 1 phút (60s) theo yêu cầu
    var scheduledTargetMs by remember { mutableLongStateOf(prefs.getLong(FakeCallScheduler.KEY_SCHEDULED_AT, 0L)) }
    var remainingCountdownSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(scheduledTargetMs) {
        while (scheduledTargetMs > System.currentTimeMillis()) {
            val diff = ((scheduledTargetMs - System.currentTimeMillis() + 999L) / 1000L).toInt()
            remainingCountdownSec = diff
            delay(500L)
        }
        remainingCountdownSec = 0
        if (scheduledTargetMs != 0L && System.currentTimeMillis() >= scheduledTargetMs) {
            scheduledTargetMs = 0L
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Cuộc Gọi Giả (Fake Call)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Hẹn giờ Sếp / Mẹ gọi đến • Chạy ngầm cả khi thoát App",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. Thẻ kiểm tra quyền Hiển thị trên ứng dụng khác (Để bung cuộc gọi khi đã thoát khỏi App)
            if (!hasOverlayPermission) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AccentAmber.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentAmber.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Layers, contentDescription = null, tint = AccentAmber)
                            Text(
                                text = "Bật quyền Hiển thị khi thoát ra ngoài App",
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber,
                                fontSize = 13.5.sp
                            )
                        }
                        Text(
                            text = "Để màn hình Cuộc gọi đến tự động bật đè lên Màn hình chính, Zalo, Facebook hay TikTok khi bạn đã thoát khỏi ứng dụng, hãy cấp quyền \"Hiển thị trên ứng dụng khác\".",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                        Button(
                            onClick = { FakeCallScheduler.openOverlayPermissionSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentAmber,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bật quyền Hiển thị trên ứng dụng khác", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AccentGreen.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Sẵn sàng chạy ngầm: Khi hẹn giờ xong, bạn có thể thoát hẳn ra ngoài màn hình chính hoặc mở app khác, đúng giờ máy sẽ tự đổ chuông & hiện cuộc gọi!",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Trạng thái đang đếm ngược chờ đổ chuông
            if (remainingCountdownSec > 0) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = AccentGreen.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, AccentGreen, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⏳ ĐANG HẸN GIỜ CUỘC GỌI ĐẾN (CHẠY NGẦM)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AccentGreen
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%02d:%02d",
                                remainingCountdownSec / 60,
                                remainingCountdownSec % 60
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Người gọi: $callerName ($phoneNumber)\nBạn có thể bấm nút Home thoát ra ngoài hoặc úp điện thoại xuống bàn, đúng giờ máy sẽ tự đổ chuông!",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                FakeCallScheduler.cancelScheduledCall(context)
                                scheduledTargetMs = 0L
                                remainingCountdownSec = 0
                                Toast.makeText(context, "Đã hủy hẹn giờ cuộc gọi giả!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hủy cuộc gọi hẹn giờ", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 1. Chọn nhanh kịch bản người gọi
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Chọn nhanh Người Gọi Đến (Hoặc tự nhập):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = AccentCyan
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(FAKE_CALLER_PRESETS) { idx, preset ->
                            val selected = selectedPresetIdx == idx
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedPresetIdx = idx
                                    callerName = preset.callerName
                                    phoneNumber = preset.phoneNumber
                                    carrierLabel = preset.carrierLabel
                                    avatarEmoji = preset.avatarEmoji
                                    voiceScript = preset.voiceScript
                                },
                                label = {
                                    Text(
                                        text = preset.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = callerName,
                        onValueChange = { callerName = it },
                        label = { Text("Tên người gọi hiển thị trên màn hình") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Số điện thoại") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = carrierLabel,
                            onValueChange = { carrierLabel = it },
                            label = { Text("Nhà mạng / Phụ đề") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = voiceScript,
                        onValueChange = { voiceScript = it },
                        label = { Text("Lời thoại giả lập phát ra loa khi bấm Nghe máy") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = {
                            if (voiceScript.isNotBlank()) {
                                isPreviewingCustomAudio = false
                                Toast.makeText(context, "🔊 Đang phát thử giọng đọc Tiếng Việt...", Toast.LENGTH_SHORT).show()
                                FakeCallScheduler.playVietnameseVoiceScript(
                                    context = context,
                                    script = voiceScript,
                                    useSpeaker = true
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "🔊 Nghe thử lời thoại Tiếng Việt chuẩn",
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // Chọn nguồn âm thanh: Giọng đọc TTS hoặc File MP3 tải lên
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🎵 Nguồn âm thanh phát khi Nghe máy:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = AccentGreen
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !useCustomAudio,
                                onClick = {
                                    useCustomAudio = false
                                    prefs.edit().putBoolean(FakeCallScheduler.KEY_USE_CUSTOM_AUDIO, false).apply()
                                },
                                label = {
                                    Text(
                                        text = "🗣️ Giọng đọc TTS",
                                        fontWeight = if (!useCustomAudio) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = useCustomAudio,
                                onClick = {
                                    if (customAudioPath.isNotBlank() && File(customAudioPath).exists()) {
                                        useCustomAudio = true
                                        prefs.edit().putBoolean(FakeCallScheduler.KEY_USE_CUSTOM_AUDIO, true).apply()
                                    } else {
                                        audioPickerLauncher.launch(arrayOf("audio/*"))
                                    }
                                },
                                label = {
                                    Text(
                                        text = "🎧 File MP3 / Ghi âm",
                                        fontWeight = if (useCustomAudio) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGreen,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedButton(
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (customAudioPath.isNotBlank() && File(customAudioPath).exists()) {
                                    "📂 Đổi file MP3 / Ghi âm khác"
                                } else {
                                    "📂 Tải lên file MP3 / Ghi âm cuộc gọi"
                                },
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (customAudioPath.isNotBlank() && File(customAudioPath).exists()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (useCustomAudio) {
                                    AccentGreen.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (useCustomAudio) AccentGreen.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = customAudioName.ifBlank { "custom_call_audio.mp3" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (useCustomAudio) {
                                                "Đang chọn phát file này khi nghe máy"
                                            } else {
                                                "Đã lưu • Chạm 'File MP3 / Ghi âm' để bật"
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isPreviewingCustomAudio) {
                                                FakeCallScheduler.stopActiveVoice()
                                                isPreviewingCustomAudio = false
                                            } else {
                                                isPreviewingCustomAudio = FakeCallScheduler.playCustomAudioFile(
                                                    context = context,
                                                    audioPath = customAudioPath,
                                                    useSpeaker = true,
                                                    onCompletion = { isPreviewingCustomAudio = false }
                                                )
                                                if (!isPreviewingCustomAudio) {
                                                    Toast.makeText(context, "Không thể phát file âm thanh này!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewingCustomAudio) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                            contentDescription = if (isPreviewingCustomAudio) "Dừng" else "Nghe thử",
                                            tint = AccentCyan
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isPreviewingCustomAudio) {
                                                FakeCallScheduler.stopActiveVoice()
                                                isPreviewingCustomAudio = false
                                            }
                                            FakeCallScheduler.clearCustomAudio(context)
                                            customAudioPath = ""
                                            customAudioName = ""
                                            useCustomAudio = false
                                            Toast.makeText(context, "Đã xóa file MP3 đã tải lên", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Xóa file",
                                            tint = AccentRed
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🗣️ Phát âm thanh từ đầu dây bên kia khi Nghe máy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "Khi bạn ấn Trả lời, điện thoại sẽ phát file MP3 đã tải lên (hoặc đọc câu thoại TTS) để người ngồi cạnh nghe thấy như có người gọi thật",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSpeakOnAnswer,
                            onCheckedChange = { autoSpeakOnAnswer = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentGreen
                            )
                        )
                    }
                }
            }

            // 2. Chọn thời gian hẹn giờ đổ chuông
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "2. Hẹn thời gian điện thoại bắt đầu đổ chuông:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = AccentAmber
                    )

                    val delayOptions = listOf(
                        5 to "Sau 5 giây",
                        15 to "Sau 15 giây",
                        30 to "Sau 30 giây",
                        60 to "⭐ Sau 1 phút",
                        180 to "Sau 3 phút",
                        300 to "Sau 5 phút"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        delayOptions.forEach { (sec, title) ->
                            val isSelected = selectedDelaySec == sec
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedDelaySec = sec },
                                label = {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentAmber,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                FakeCallScheduler.scheduleFakeCall(
                                    context = context,
                                    delaySeconds = selectedDelaySec,
                                    callerName = callerName.ifBlank { "Sếp Tổng" },
                                    phoneNumber = phoneNumber.ifBlank { "0912 388 999" },
                                    carrier = carrierLabel.ifBlank { "Viettel 5G" },
                                    avatarEmoji = avatarEmoji,
                                    voiceScript = voiceScript,
                                    autoSpeakOnAnswer = autoSpeakOnAnswer,
                                    useCustomAudio = useCustomAudio
                                )
                                scheduledTargetMs = System.currentTimeMillis() + selectedDelaySec * 1000L
                                Toast.makeText(
                                    context,
                                    "📞 Đã hẹn giờ! Bạn có thể thoát khỏi app ngay bây giờ, máy sẽ tự đổ chuông sau $selectedDelaySec giây.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Hẹn giờ gọi (${selectedDelaySec}s)",
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                FakeCallScheduler.scheduleFakeCall(
                                    context = context,
                                    delaySeconds = 0,
                                    callerName = callerName.ifBlank { "Sếp Tổng" },
                                    phoneNumber = phoneNumber.ifBlank { "0912 388 999" },
                                    carrier = carrierLabel.ifBlank { "Viettel 5G" },
                                    avatarEmoji = avatarEmoji,
                                    voiceScript = voiceScript,
                                    autoSpeakOnAnswer = autoSpeakOnAnswer,
                                    useCustomAudio = useCustomAudio
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Rounded.PhoneCallback, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Đổ chuông ngay",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Activity toàn màn hình mô phỏng giao diện Cuộc Gọi Đến & Đang Nghe Máy y hệt cuộc gọi thật.
 */
class FakeCallIncomingActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        FakeCallScheduler.activeCallActivity = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences(FakeCallScheduler.PREF_NAME, Context.MODE_PRIVATE)
        val callerName = prefs.getString(FakeCallScheduler.KEY_CALLER_NAME, "Sếp Hoàng (Giám đốc)") ?: "Sếp Hoàng"
        val callerNumber = prefs.getString(FakeCallScheduler.KEY_CALLER_NUMBER, "0912 388 999") ?: "0912 388 999"
        val carrier = prefs.getString(FakeCallScheduler.KEY_CARRIER, "Viettel 5G • Cuộc gọi thoại HD") ?: "Viettel 5G"
        val avatarEmoji = prefs.getString(FakeCallScheduler.KEY_AVATAR, "👔") ?: "👔"
        val voiceScript = prefs.getString(FakeCallScheduler.KEY_VOICE_SCRIPT, "") ?: ""
        val autoSpeak = prefs.getBoolean(FakeCallScheduler.KEY_AUTO_SPEAK, true)
        val useCustomAudio = prefs.getBoolean(FakeCallScheduler.KEY_USE_CUSTOM_AUDIO, false)
        val customAudioPath = prefs.getString(FakeCallScheduler.KEY_CUSTOM_AUDIO_PATH, "") ?: ""

        if (!useCustomAudio && voiceScript.isNotBlank()) {
            FakeCallScheduler.preCacheVietnameseVoice(this, voiceScript)
        }

        tts = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag("vi-VN")
                tts?.setSpeechRate(1.05f)
                ttsReady = true
            }
        }, "com.google.android.tts")

        FakeCallScheduler.startRingingAndVibration(this)

        setContent {
            PowerTimerTheme {
                FakeIncomingCallScreenContent(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    carrierLabel = carrier,
                    avatarEmoji = avatarEmoji,
                    onAnswer = {
                        FakeCallScheduler.stopRingingAndVibration()
                        FakeCallScheduler.cancelCallNotifications(this)
                        if (autoSpeak) {
                            playConfiguredAudio(
                                script = voiceScript,
                                useCustomAudio = useCustomAudio,
                                customAudioPath = customAudioPath,
                                useSpeaker = false
                            )
                        }
                    },
                    onToggleSpeaker = { speakerOn ->
                        if (!FakeCallScheduler.updateActiveVoiceVolume(speakerOn) && !useCustomAudio && voiceScript.isNotBlank()) {
                            speakCallerScript(voiceScript, useSpeaker = speakerOn)
                        }
                    },
                    onReplayVoice = { speakerOn ->
                        playConfiguredAudio(
                            script = voiceScript,
                            useCustomAudio = useCustomAudio,
                            customAudioPath = customAudioPath,
                            useSpeaker = speakerOn
                        )
                    },
                    onEndCall = {
                        FakeCallScheduler.endActiveFakeCall(this)
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Nếu Activity đã hiển thị lên tiền cảnh thành công, ẩn lớp phủ Overlay để không hiển thị 2 lớp trùng nhau
        FakeCallScheduler.dismissOverlayWindowOnly()
    }

    private fun playConfiguredAudio(
        script: String,
        useCustomAudio: Boolean,
        customAudioPath: String,
        useSpeaker: Boolean
    ) {
        FakeCallScheduler.playConfiguredCallAudio(
            context = this,
            script = script,
            useCustomAudio = useCustomAudio,
            customAudioPath = customAudioPath,
            useSpeaker = useSpeaker,
            fallbackTts = if (ttsReady) tts else null
        )
    }

    private fun speakCallerScript(script: String, useSpeaker: Boolean) {
        FakeCallScheduler.playVietnameseVoiceScript(
            context = this,
            script = script,
            useSpeaker = useSpeaker,
            fallbackTts = if (ttsReady) tts else null
        )
    }

    override fun onDestroy() {
        if (FakeCallScheduler.activeCallActivity === this) {
            FakeCallScheduler.activeCallActivity = null
        }
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}

@Composable
private fun FakeIncomingCallScreenContent(
    callerName: String,
    callerNumber: String,
    carrierLabel: String,
    avatarEmoji: String,
    onAnswer: () -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onReplayVoice: (Boolean) -> Unit,
    onEndCall: () -> Unit
) {
    var isConnected by remember { mutableStateOf(false) }
    var callDurationSec by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isOnHold by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                delay(1000L)
                callDurationSec++
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_ring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF090D16),
                        Color(0xFF111827),
                        Color(0xFF1E293B),
                        Color(0xFF090D16)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        // Phần trên: Thông tin người gọi
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    text = carrierLabel,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(26.dp))

            // Avatar với vòng sóng âm
            Box(contentAlignment = Alignment.Center) {
                if (!isConnected) {
                    Box(
                        modifier = Modifier
                            .size(134.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(alpha = 0.14f))
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(106.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF334155), Color(0xFF1E293B))
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Text(text = avatarEmoji, fontSize = 46.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = callerName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = callerNumber,
                color = Color(0xFFCBD5E1),
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (!isConnected) {
                    "Đang gọi đến..."
                } else if (isOnHold) {
                    "Đang giữ cuộc gọi"
                } else {
                    String.format(
                        Locale.US,
                        "%02d:%02d • HD Voice",
                        callDurationSec / 60,
                        callDurationSec % 60
                    )
                },
                color = if (isConnected) AccentGreen else Color(0xFF94A3B8),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Phần dưới: Nút điều khiển cuộc gọi
        if (!isConnected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onEndCall() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Nhắc tôi", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onEndCall() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Message,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Nhắn tin", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Từ chối
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .clickable { onEndCall() }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "Từ chối",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Từ chối",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Nút Trả lời
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(74.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                                .clickable {
                                    isConnected = true
                                    onAnswer()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Trả lời",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Trả lời",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // Giao diện Đang Nghe Máy (6 nút chức năng + Nút Cúp Máy)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InCallActionButton(
                        icon = Icons.Rounded.MicOff,
                        label = "Tắt tiếng",
                        active = isMuted,
                        onClick = { isMuted = !isMuted }
                    )
                    InCallActionButton(
                        icon = Icons.Rounded.Dialpad,
                        label = "Bàn phím",
                        active = false,
                        onClick = {}
                    )
                    InCallActionButton(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "Loa ngoài",
                        active = isSpeakerOn,
                        onClick = {
                            isSpeakerOn = !isSpeakerOn
                            onToggleSpeaker(isSpeakerOn)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InCallActionButton(
                        icon = Icons.Rounded.PersonAdd,
                        label = "Thêm gọi",
                        active = false,
                        onClick = {}
                    )
                    InCallActionButton(
                        icon = Icons.Rounded.Pause,
                        label = "Giữ máy",
                        active = isOnHold,
                        onClick = { isOnHold = !isOnHold }
                    )
                    InCallActionButton(
                        icon = Icons.Rounded.RecordVoiceOver,
                        label = "Phát thoại",
                        active = true,
                        onClick = { onReplayVoice(isSpeakerOn) }
                    )
                }

                Spacer(Modifier.height(6.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .clickable { onEndCall() }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "Kết thúc",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InCallActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.12f))
                .clickable { onClick() }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF0F172A) else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
