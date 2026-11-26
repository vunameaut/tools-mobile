package com.vunameaut.powertimer.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

data class CustomMemeSound(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val filePath: String,
    val accentIndex: Int = 0
)

object MemeSoundboardStorage {
    private const val PREF_NAME = "meme_soundboard_prefs"
    private const val KEY_CUSTOM_SOUNDS_JSON = "custom_sounds_json"
    private const val KEY_HIDDEN_DEFAULT_IDS = "hidden_default_ids"
    private const val MAX_AUDIO_SIZE_BYTES = 25L * 1024L * 1024L

    val ACCENT_PALETTE = listOf(
        AccentGreen,
        AccentCyan,
        AccentAmber,
        AccentPink,
        AccentPurple,
        AccentRed
    )

    val EMOJI_CHOICES = listOf(
        "🎵", "😂", "🔥", "👏", "🎺", "🥁", "🐶", "🐱",
        "🦆", "🚨", "💥", "🎉", "🤣", "🗣️", "👻", "🔔"
    )

    fun accentColor(index: Int): Color {
        return ACCENT_PALETTE.getOrElse(index.coerceAtLeast(0) % ACCENT_PALETTE.size) { AccentCyan }
    }

    fun cleanDisplayNameFromFileName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return "Âm thanh của tôi"
        val withoutExt = rawName.substringBeforeLast('.', rawName)
        val cleaned = withoutExt
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        return cleaned.take(40).ifBlank { "Âm thanh của tôi" }
    }

    fun serializeCustomSounds(list: List<CustomMemeSound>): String {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("emoji", item.emoji)
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("filePath", item.filePath)
                    put("accentIndex", item.accentIndex)
                }
            )
        }
        return arr.toString()
    }

    fun parseCustomSounds(jsonRaw: String?): List<CustomMemeSound> {
        if (jsonRaw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(jsonRaw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val filePath = obj.optString("filePath").trim()
                    if (id.isBlank() || filePath.isBlank()) continue
                    add(
                        CustomMemeSound(
                            id = id,
                            emoji = obj.optString("emoji", "🎵").ifBlank { "🎵" },
                            title = obj.optString("title", "Âm thanh của tôi").ifBlank { "Âm thanh của tôi" },
                            subtitle = obj.optString("subtitle", "Âm thanh tự tải lên").ifBlank { "Âm thanh tự tải lên" },
                            filePath = filePath,
                            accentIndex = obj.optInt("accentIndex", 0)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun loadCustomSounds(context: Context): List<CustomMemeSound> {
        val raw = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_SOUNDS_JSON, null)
        return parseCustomSounds(raw).filter { File(it.filePath).exists() }
    }

    private fun saveCustomSounds(context: Context, list: List<CustomMemeSound>) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_SOUNDS_JSON, serializeCustomSounds(list))
            .apply()
    }

    fun loadHiddenDefaultIds(context: Context): Set<String> {
        return context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_HIDDEN_DEFAULT_IDS, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    private fun saveHiddenDefaultIds(context: Context, ids: Set<String>) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_HIDDEN_DEFAULT_IDS, ids)
            .apply()
    }

    fun hideDefaultSound(context: Context, defaultId: String): Set<String> {
        val next = loadHiddenDefaultIds(context).toMutableSet().apply { add(defaultId) }
        saveHiddenDefaultIds(context, next)
        return next
    }

    fun restoreDefaultSound(context: Context, defaultId: String): Set<String> {
        val next = loadHiddenDefaultIds(context).toMutableSet().apply { remove(defaultId) }
        saveHiddenDefaultIds(context, next)
        return next
    }

    fun restoreAllDefaultSounds(context: Context): Set<String> {
        saveHiddenDefaultIds(context, emptySet())
        return emptySet()
    }

    fun queryFileDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "audio.mp3"
    }

    fun importAudioFile(
        context: Context,
        uri: Uri,
        title: String,
        emoji: String,
        accentIndex: Int
    ): Result<CustomMemeSound> {
        return runCatching {
            val appCtx = context.applicationContext
            val resolver = appCtx.contentResolver
            val displayName = queryFileDisplayName(appCtx, uri)
            val mime = resolver.getType(uri).orEmpty()
            val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime).orEmpty()
            val extFromName = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val rawExt = extFromName.ifBlank { extFromMime }.replace(Regex("[^a-z0-9]"), "")
            val safeExt = if (rawExt.length in 2..5) rawExt else "mp3"

            val dir = File(appCtx.filesDir, "custom_meme_sounds").apply {
                if (!exists()) mkdirs()
            }
            val id = "custom_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val destFile = File(dir, "$id.$safeExt")

            val copiedBytes = resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_AUDIO_SIZE_BYTES) {
                            throw IllegalStateException("File âm thanh vượt quá giới hạn 25MB")
                        }
                        output.write(buffer, 0, read)
                    }
                    total
                }
            } ?: throw IllegalStateException("Không thể đọc file âm thanh đã chọn")

            if (copiedBytes <= 0L) {
                runCatching { destFile.delete() }
                throw IllegalStateException("File âm thanh rỗng")
            }

            val finalTitle = title.trim().ifBlank { cleanDisplayNameFromFileName(displayName) }
            val newItem = CustomMemeSound(
                id = id,
                emoji = emoji.ifBlank { "🎵" },
                title = finalTitle,
                subtitle = "${safeExt.uppercase(Locale.ROOT)} • Tự tải lên",
                filePath = destFile.absolutePath,
                accentIndex = accentIndex
            )
            val updated = listOf(newItem) + loadCustomSounds(appCtx)
            saveCustomSounds(appCtx, updated)
            newItem
        }
    }

    fun deleteCustomSound(context: Context, soundId: String): List<CustomMemeSound> {
        val current = loadCustomSounds(context)
        val target = current.firstOrNull { it.id == soundId }
        if (target != null) {
            runCatching { File(target.filePath).delete() }
        }
        val remaining = current.filterNot { it.id == soundId }
        saveCustomSounds(context, remaining)
        return remaining
    }
}

private enum class MemeSoundType(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val voiceLine: String? = null
) {
    APPLAUSE(
        id = "applause",
        emoji = "👏",
        title = "Vỗ Tay Rần Rần",
        subtitle = "Hoan hô nhiệt liệt",
        accent = AccentGreen,
        voiceLine = "Quá đỉnh luôn, vỗ tay anh em ơi!"
    ),
    CRICKETS(
        id = "crickets",
        emoji = "🦗",
        title = "Tiếng Dế Kêu",
        subtitle = "Khi ai đó kể chuyện nhạt",
        accent = AccentCyan
    ),
    BA_DUM_TSS(
        id = "ba_dum_tss",
        emoji = "🥁",
        title = "Trống Tùng Xèng",
        subtitle = "Ba Dum Tss! Chốt câu đùa",
        accent = AccentAmber
    ),
    AIR_HORN(
        id = "air_horn",
        emoji = "📢",
        title = "Còi Hơi Cực Mạnh",
        subtitle = "Khuấy động cuộc vui",
        accent = AccentRed
    ),
    FUNNY_LAUGH(
        id = "funny_laugh",
        emoji = "😂",
        title = "Cười Khả Ố",
        subtitle = "Cười ha hả trêu chọc",
        accent = AccentPink,
        voiceLine = "Há há há há, ối giồi ôi buồn cười quá đi mất!"
    ),
    SAD_TROMBONE(
        id = "sad_trombone",
        emoji = "🎺",
        title = "Kèn Thất Bại",
        subtitle = "Wah wah wah waaah...",
        accent = AccentPurple
    ),
    VINE_BOOM(
        id = "vine_boom",
        emoji = "💥",
        title = "Vine Boom Bass",
        subtitle = "Tiếng nổ trầm chấn động",
        accent = AccentAmber
    ),
    POLICE_SIREN(
        id = "police_siren",
        emoji = "🚨",
        title = "Còi Báo Động",
        subtitle = "Bắt quả tang tại trận",
        accent = AccentRed
    ),
    WRONG_BUZZER(
        id = "wrong_buzzer",
        emoji = "❌",
        title = "Buzzer Sai Rồi!",
        subtitle = "Èn èn! Trả lời sai bét",
        accent = AccentPink
    ),
    DING_DING(
        id = "ding_ding",
        emoji = "🔔",
        title = "Chuông Chính Xác",
        subtitle = "Tinh tinh! +100 điểm",
        accent = AccentGreen
    ),
    DUCK_QUACK(
        id = "duck_quack",
        emoji = "🦆",
        title = "Vịt Kêu Quạc Quạc",
        subtitle = "Quạc quạc hài hước",
        accent = AccentAmber
    ),
    LEVEL_UP(
        id = "level_up",
        emoji = "🎮",
        title = "Chiến Thắng 8-Bit",
        subtitle = "Nhạc lên cấp Game",
        accent = AccentCyan
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MemeSoundboardTool(onExit: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    var activeSoundId by remember { mutableStateOf<String?>(null) }
    var includeVoiceMeme by remember { mutableStateOf(true) }
    var isEditMode by remember { mutableStateOf(false) }

    var customSounds by remember { mutableStateOf(MemeSoundboardStorage.loadCustomSounds(context)) }
    var hiddenDefaultIds by remember { mutableStateOf(MemeSoundboardStorage.loadHiddenDefaultIds(context)) }
    val visibleDefaults = remember(hiddenDefaultIds) {
        MemeSoundType.entries.filterNot { it.id in hiddenDefaultIds }
    }
    val hiddenDefaults = remember(hiddenDefaultIds) {
        MemeSoundType.entries.filter { it.id in hiddenDefaultIds }
    }

    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var currentMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var ttsRef by remember { mutableStateOf<TextToSpeech?>(null) }

    // Upload dialog state
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportTitle by remember { mutableStateOf("") }
    var pendingImportEmoji by remember { mutableStateOf("🎵") }
    var pendingImportAccent by remember { mutableIntStateOf(0) }
    var isImporting by remember { mutableStateOf(false) }

    // Delete / Restore dialogs
    var pendingDeleteCustom by remember { mutableStateOf<CustomMemeSound?>(null) }
    var pendingDeleteDefault by remember { mutableStateOf<MemeSoundType?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val rawName = MemeSoundboardStorage.queryFileDisplayName(context, uri)
            pendingImportTitle = MemeSoundboardStorage.cleanDisplayNameFromFileName(rawName)
            pendingImportEmoji = MemeSoundboardStorage.EMOJI_CHOICES.first()
            pendingImportAccent = customSounds.size % MemeSoundboardStorage.ACCENT_PALETTE.size
            pendingImportUri = uri
        }
    }

    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale("vi", "VN")
                ttsInstance?.setPitch(1.25f)
                ttsInstance?.setSpeechRate(1.18f)
                ttsRef = ttsInstance
            }
        }
        onDispose {
            currentJob?.cancel()
            runCatching {
                currentTrack?.stop()
                currentTrack?.release()
            }
            runCatching {
                currentMediaPlayer?.stop()
                currentMediaPlayer?.release()
            }
            runCatching {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            }
        }
    }

    fun ensureMediaVolumeAudible() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (cur == 0 && max > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.6f).toInt().coerceAtLeast(1), 0)
            }
        }
    }

    fun stopAllSounds() {
        currentJob?.cancel()
        runCatching {
            currentTrack?.pause()
            currentTrack?.flush()
            currentTrack?.release()
        }
        currentTrack = null
        runCatching {
            currentMediaPlayer?.stop()
            currentMediaPlayer?.release()
        }
        currentMediaPlayer = null
        FakeCallScheduler.stopActiveVoice()
        runCatching { ttsRef?.stop() }
        activeSoundId = null
    }

    fun playCustomSound(sound: CustomMemeSound) {
        stopAllSounds()
        val file = File(sound.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "Không tìm thấy file âm thanh!", Toast.LENGTH_SHORT).show()
            customSounds = MemeSoundboardStorage.deleteCustomSound(context, sound.id)
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        activeSoundId = sound.id
        ensureMediaVolumeAudible()

        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { player ->
                    runCatching { player.start() }
                }
                setOnCompletionListener { player ->
                    if (activeSoundId == sound.id) activeSoundId = null
                    runCatching { player.release() }
                    if (currentMediaPlayer === player) currentMediaPlayer = null
                }
                setOnErrorListener { player, _, _ ->
                    if (activeSoundId == sound.id) activeSoundId = null
                    runCatching { player.release() }
                    if (currentMediaPlayer === player) currentMediaPlayer = null
                    Toast.makeText(context, "Không phát được định dạng âm thanh này", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
            currentMediaPlayer = mp
        }.onFailure {
            activeSoundId = null
            Toast.makeText(context, "Lỗi phát âm thanh: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun playMemeSound(sound: MemeSoundType) {
        stopAllSounds()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        activeSoundId = sound.id
        ensureMediaVolumeAudible()

        if (includeVoiceMeme && sound.voiceLine != null) {
            FakeCallScheduler.playVietnameseVoiceScript(
                context = context,
                script = sound.voiceLine,
                useSpeaker = true,
                fallbackTts = ttsRef
            )
        }

        currentJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val pcm = synthesizeMemePcm(sound, sampleRate)
            val track = runCatching {
                AudioTrack.Builder()
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
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
            }.getOrNull() ?: return@launch

            currentTrack = track
            runCatching {
                track.write(pcm, 0, pcm.size)
                track.play()
                val durationMs = (pcm.size * 1000L) / sampleRate
                kotlinx.coroutines.delay(durationMs + 120L)
            }
            runCatching {
                track.stop()
                track.release()
            }
            if (activeSoundId == sound.id) {
                activeSoundId = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Túi Âm Thanh Vui Nhộn",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Tải âm thanh của bạn lên • Xóa bớt & tùy chỉnh bảng nút",
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
                actions = {
                    if (activeSoundId != null) {
                        IconButton(onClick = { stopAllSounds() }) {
                            Icon(
                                imageVector = Icons.Rounded.StopCircle,
                                contentDescription = "Dừng âm thanh",
                                tint = AccentRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Upload + Edit action bar
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tải âm thanh lên",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = { isEditMode = !isEditMode },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (isEditMode) AccentGreen else AccentRed.copy(alpha = 0.6f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isEditMode) AccentGreen else AccentRed
                        ),
                        modifier = Modifier
                            .weight(0.95f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Rounded.Check else Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isEditMode) "Xong" else "Xóa bớt",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Voice meme switch + tip
            item(span = { GridItemSpan(2) }) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🗣️ Kèm giọng đọc hoạt náo (Vỗ tay / Cười)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AccentCyan
                            )
                            Text(
                                text = if (isEditMode) {
                                    "Chế độ chỉnh sửa: chạm nút ❌ trên bất kỳ ô nào để xóa"
                                } else {
                                    "Chạm để phát • Nhấn giữ hoặc bấm \"Xóa bớt\" để ẩn/xóa ô"
                                },
                                fontSize = 11.5.sp,
                                color = if (isEditMode) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeVoiceMeme,
                            onCheckedChange = { includeVoiceMeme = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentGreen
                            )
                        )
                    }
                }
            }

            // Section 1: Custom uploaded sounds
            if (customSounds.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "🎧 Âm thanh của tôi (${customSounds.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = AccentGreen,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(customSounds, key = { it.id }) { custom ->
                    val isPlaying = activeSoundId == custom.id
                    val accent = MemeSoundboardStorage.accentColor(custom.accentIndex)
                    SoundPadCard(
                        emoji = custom.emoji,
                        title = custom.title,
                        subtitle = if (isPlaying) "🔊 Đang phát..." else custom.subtitle,
                        accent = accent,
                        isPlaying = isPlaying,
                        isCustom = true,
                        showDeleteBadge = isEditMode,
                        onClick = {
                            if (isEditMode) {
                                pendingDeleteCustom = custom
                            } else {
                                playCustomSound(custom)
                            }
                        },
                        onLongClick = { pendingDeleteCustom = custom },
                        onDeleteClick = { pendingDeleteCustom = custom }
                    )
                }
            }

            // Section 2: Default built-in sounds
            if (visibleDefaults.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "🎭 Âm thanh mặc định (${visibleDefaults.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = AccentCyan,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(visibleDefaults, key = { it.id }) { sound ->
                    val isPlaying = activeSoundId == sound.id
                    SoundPadCard(
                        emoji = sound.emoji,
                        title = sound.title,
                        subtitle = if (isPlaying) "🔊 Đang phát..." else sound.subtitle,
                        accent = sound.accent,
                        isPlaying = isPlaying,
                        isCustom = false,
                        showDeleteBadge = isEditMode,
                        onClick = {
                            if (isEditMode) {
                                pendingDeleteDefault = sound
                            } else {
                                playMemeSound(sound)
                            }
                        },
                        onLongClick = { pendingDeleteDefault = sound },
                        onDeleteClick = { pendingDeleteDefault = sound }
                    )
                }
            } else {
                item(span = { GridItemSpan(2) }) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Đã ẩn toàn bộ âm thanh mặc định",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Bạn có thể bấm \"Tải âm thanh lên\" hoặc khôi phục lại bên dưới.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 3: Restore hidden default sounds if any
            if (hiddenDefaults.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Khôi phục ${hiddenDefaults.size} âm thanh mặc định đã xóa",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Dialog 1: Nhập thông tin cho file âm thanh vừa chọn từ máy
    val importUri = pendingImportUri
    if (importUri != null) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) pendingImportUri = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = AccentCyan)
                    Text("Thêm âm thanh của tôi", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = pendingImportTitle,
                        onValueChange = { pendingImportTitle = it },
                        label = { Text("Tên hiển thị trên nút") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Chọn biểu tượng:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MemeSoundboardStorage.EMOJI_CHOICES.forEach { emoji ->
                            val selected = pendingImportEmoji == emoji
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) AccentCyan.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) AccentCyan else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { pendingImportEmoji = emoji }
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }

                    Text(
                        text = "Chọn màu viền:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MemeSoundboardStorage.ACCENT_PALETTE.forEachIndexed { idx, color ->
                            val selected = pendingImportAccent == idx
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { pendingImportAccent = idx }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isImporting,
                    onClick = {
                        isImporting = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                MemeSoundboardStorage.importAudioFile(
                                    context = context,
                                    uri = importUri,
                                    title = pendingImportTitle,
                                    emoji = pendingImportEmoji,
                                    accentIndex = pendingImportAccent
                                )
                            }
                            isImporting = false
                            result.fold(
                                onSuccess = { added ->
                                    customSounds = MemeSoundboardStorage.loadCustomSounds(context)
                                    pendingImportUri = null
                                    Toast.makeText(
                                        context,
                                        "Đã thêm \"${added.title}\" vào Túi Âm Thanh!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onFailure = { err ->
                                    Toast.makeText(
                                        context,
                                        err.message ?: "Không thể lưu âm thanh",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                ) {
                    Text(if (isImporting) "Đang lưu..." else "Lưu vào bảng", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isImporting, onClick = { pendingImportUri = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Dialog 2: Xác nhận xóa âm thanh tự tải lên
    val deleteCustom = pendingDeleteCustom
    if (deleteCustom != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteCustom = null },
            title = { Text("Xóa âm thanh \"${deleteCustom.title}\"?", fontWeight = FontWeight.Bold) },
            text = { Text("File âm thanh này sẽ bị xóa khỏi Túi Âm Thanh.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeSoundId == deleteCustom.id) stopAllSounds()
                        customSounds = MemeSoundboardStorage.deleteCustomSound(context, deleteCustom.id)
                        pendingDeleteCustom = null
                        Toast.makeText(context, "Đã xóa ${deleteCustom.title}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White)
                ) {
                    Text("Xóa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCustom = null }) { Text("Giữ lại") }
            }
        )
    }

    // Dialog 3: Xác nhận xóa / ẩn âm thanh mặc định
    val deleteDefault = pendingDeleteDefault
    if (deleteDefault != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteDefault = null },
            title = { Text("Xóa \"${deleteDefault.title}\" khỏi bảng?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Âm thanh mặc định này sẽ được ẩn khỏi danh sách. Bạn luôn có thể khôi phục lại bất kỳ lúc nào.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeSoundId == deleteDefault.id) stopAllSounds()
                        hiddenDefaultIds = MemeSoundboardStorage.hideDefaultSound(context, deleteDefault.id)
                        pendingDeleteDefault = null
                        Toast.makeText(context, "Đã xóa ${deleteDefault.title} khỏi bảng", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White)
                ) {
                    Text("Xóa khỏi bảng", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteDefault = null }) { Text("Giữ lại") }
            }
        )
    }

    // Dialog 4: Khôi phục âm thanh mặc định đã xóa
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Khôi phục âm thanh mặc định", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Chạm vào từng mục để đưa lại vào bảng hoặc bấm Khôi phục tất cả:",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    hiddenDefaults.forEach { sound ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    hiddenDefaultIds = MemeSoundboardStorage.restoreDefaultSound(context, sound.id)
                                    if (hiddenDefaultIds.isEmpty()) showRestoreDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${sound.emoji} ${sound.title}", fontWeight = FontWeight.SemiBold)
                                Text("Khôi phục", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        hiddenDefaultIds = MemeSoundboardStorage.restoreAllDefaultSounds(context)
                        showRestoreDialog = false
                        Toast.makeText(context, "Đã khôi phục toàn bộ âm thanh mặc định!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                ) {
                    Text("Khôi phục tất cả", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Đóng") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundPadCard(
    emoji: String,
    title: String,
    subtitle: String,
    accent: Color,
    isPlaying: Boolean,
    isCustom: Boolean,
    showDeleteBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isPlaying) 0.96f else 1f, label = "pad_scale")

    Box(modifier = Modifier.fillMaxWidth().scale(scale)) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = if (isPlaying) {
                accent.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isPlaying) 2.5.dp else 1.dp,
                    color = if (isPlaying) accent else accent.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(22.dp)
                )
                .clip(RoundedCornerShape(22.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    accent.copy(alpha = 0.35f),
                                    accent.copy(alpha = 0.08f)
                                )
                            )
                        )
                ) {
                    Text(text = emoji, fontSize = 28.sp)
                }

                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (isPlaying) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = if (isPlaying) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isCustom && !showDeleteBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.22f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Của tôi",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }

        if (showDeleteBadge) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AccentRed)
                    .clickable(onClick = onDeleteClick)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Xóa",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Bộ tổng hợp âm thanh PCM 16-bit 44.1kHz chất lượng cao cho từng nút Meme Soundboard.
 */
private fun synthesizeMemePcm(type: MemeSoundType, sampleRate: Int): ShortArray {
    return when (type) {
        MemeSoundType.CRICKETS -> {
            // Tiếng dế kêu "rích... rích... rích" chuẩn tần số 4250Hz
            val totalSamples = (sampleRate * 2.2).toInt()
            val out = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val cycle = t % 0.72
                val amp = if (cycle < 0.42) {
                    val pulse = sin(2.0 * PI * 34.0 * cycle)
                    if (pulse > 0) pulse else 0.0
                } else 0.0
                val wave = sin(2.0 * PI * 4250.0 * t) * 0.75 + sin(2.0 * PI * 8500.0 * t) * 0.25
                out[i] = (wave * amp * 24000).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }

        MemeSoundType.BA_DUM_TSS -> {
            // "Tùng (0..0.18s) - Tùng (0.22..0.42s) - Xènggg! (0.46..1.65s)"
            val totalSamples = (sampleRate * 1.7).toInt()
            val out = ShortArray(totalSamples)
            val rng = Random(42)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0
                if (t in 0.0..0.20) {
                    val dt = t
                    val freq = 165.0 - 75.0 * (dt / 0.20)
                    val env = exp(-dt * 18.0)
                    sample += sin(2.0 * PI * freq * dt) * env * 0.95
                }
                if (t in 0.21..0.44) {
                    val dt = t - 0.21
                    val freq = 125.0 - 55.0 * (dt / 0.23)
                    val env = exp(-dt * 16.0)
                    val snareNoise = (rng.nextDouble() * 2.0 - 1.0) * exp(-dt * 32.0) * 0.35
                    sample += sin(2.0 * PI * freq * dt) * env * 0.95 + snareNoise
                }
                if (t >= 0.45) {
                    val dt = t - 0.45
                    val env = exp(-dt * 3.2)
                    val metallic = (
                        sin(2.0 * PI * 3140.0 * dt) +
                            sin(2.0 * PI * 4580.0 * dt) +
                            sin(2.0 * PI * 6120.0 * dt) +
                            sin(2.0 * PI * 7890.0 * dt)
                        ) * 0.15
                    val highNoise = (rng.nextDouble() * 2.0 - 1.0) * 0.65
                    sample += (metallic + highNoise) * env
                }
                out[i] = (sample * 27000).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }

        MemeSoundType.AIR_HORN -> {
            val totalSamples = (sampleRate * 1.85).toInt()
            val out = ShortArray(totalSamples)
            val freqs = doubleArrayOf(392.0, 493.88, 587.33, 784.0)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val active = (t in 0.0..0.16) || (t in 0.22..0.38) || (t in 0.44..1.75)
                if (active) {
                    var sum = 0.0
                    for (f in freqs) {
                        val phase = (t * f) % 1.0
                        val saw = 2.0 * phase - 1.0
                        sum += saw
                    }
                    val tailFade = if (t > 1.45) ((1.75 - t) / 0.30).coerceIn(0.0, 1.0) else 1.0
                    out[i] = ((sum / freqs.size) * tailFade * 28000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            out
        }

        MemeSoundType.APPLAUSE -> {
            val totalSamples = (sampleRate * 2.4).toInt()
            val mix = FloatArray(totalSamples)
            val rng = Random(101)
            val clapCount = 68
            for (c in 0 until clapCount) {
                val startSample = rng.nextInt(0, (totalSamples * 0.85).toInt())
                val decayRate = 45.0 + rng.nextDouble() * 35.0
                val resonanceFreq = 950.0 + rng.nextDouble() * 850.0
                val clapLen = (sampleRate * 0.09).toInt()
                for (k in 0 until clapLen) {
                    val idx = startSample + k
                    if (idx >= totalSamples) break
                    val dt = k.toDouble() / sampleRate
                    val env = exp(-dt * decayRate)
                    val burst = (rng.nextFloat() * 2f - 1f) * 0.7f +
                        sin(2.0 * PI * resonanceFreq * dt).toFloat() * 0.3f
                    mix[idx] += burst * env.toFloat() * 0.36f
                }
            }
            ShortArray(totalSamples) { i ->
                (mix[i] * 26000f).toInt().coerceIn(-32767, 32767).toShort()
            }
        }

        MemeSoundType.FUNNY_LAUGH -> {
            val totalSamples = (sampleRate * 1.75).toInt()
            val out = ShortArray(totalSamples)
            val syllables = listOf(
                Triple(0.00, 0.22, 420.0),
                Triple(0.26, 0.48, 390.0),
                Triple(0.52, 0.74, 360.0),
                Triple(0.78, 1.00, 330.0),
                Triple(1.05, 1.65, 285.0)
            )
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0
                for ((start, end, baseF) in syllables) {
                    if (t in start..end) {
                        val dt = t - start
                        val dur = end - start
                        val f = baseF * (1.0 + 0.15 * sin(PI * dt / dur))
                        val env = sin(PI * dt / dur)
                        val wave = sin(2.0 * PI * f * dt) * 0.55 +
                            sin(2.0 * PI * f * 2.0 * dt) * 0.30 +
                            sin(2.0 * PI * f * 3.0 * dt) * 0.15
                        sample = wave * env
                        break
                    }
                }
                out[i] = (sample * 26000).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }

        MemeSoundType.SAD_TROMBONE -> {
            val totalSamples = (sampleRate * 2.2).toInt()
            val out = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val freq = when {
                    t < 0.36 -> 293.66
                    t in 0.40..0.76 -> 277.18
                    t in 0.80..1.16 -> 261.63
                    t >= 1.20 -> {
                        val dt = t - 1.20
                        246.94 - 22.0 * (dt / 1.0) + 4.5 * sin(2.0 * PI * 6.5 * dt)
                    }
                    else -> 0.0
                }
                if (freq > 0.0) {
                    val wave = sin(2.0 * PI * freq * t) * 0.5 +
                        sin(2.0 * PI * freq * 2.0 * t) * 0.3 +
                        sin(2.0 * PI * freq * 3.0 * t) * 0.2
                    val fade = if (t > 1.85) ((2.2 - t) / 0.35).coerceIn(0.0, 1.0) else 1.0
                    out[i] = (wave * fade * 26000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            out
        }

        MemeSoundType.VINE_BOOM -> {
            val totalSamples = (sampleRate * 1.35).toInt()
            val out = ShortArray(totalSamples)
            var phase = 0.0
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val freq = 36.0 + 125.0 * exp(-t * 14.0)
                phase += (2.0 * PI * freq) / sampleRate
                val env = exp(-t * 2.6)
                val raw = sin(phase) * 1.45
                val clipped = raw.coerceIn(-1.0, 1.0)
                out[i] = (clipped * env * 30000).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }

        MemeSoundType.POLICE_SIREN -> {
            val totalSamples = (sampleRate * 2.0).toInt()
            val out = ShortArray(totalSamples)
            var phase = 0.0
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val freq = 920.0 + 360.0 * sin(2.0 * PI * 2.2 * t)
                phase += (2.0 * PI * freq) / sampleRate
                val wave = sin(phase) * 0.75 + sin(phase * 2.0) * 0.25
                out[i] = (wave * 26000).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }

        MemeSoundType.WRONG_BUZZER -> {
            val totalSamples = (sampleRate * 0.95).toInt()
            val out = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val active = (t in 0.0..0.36) || (t in 0.44..0.92)
                if (active) {
                    val saw1 = 2.0 * ((t * 110.0) % 1.0) - 1.0
                    val saw2 = 2.0 * ((t * 116.5) % 1.0) - 1.0
                    out[i] = (((saw1 + saw2) * 0.5) * 26000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            out
        }

        MemeSoundType.DING_DING -> {
            val totalSamples = (sampleRate * 1.25).toInt()
            val out = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var s = 0.0
                if (t >= 0.0) {
                    val env1 = exp(-t * 6.5)
                    s += (sin(2.0 * PI * 1567.98 * t) * 0.7 + sin(2.0 * PI * 3135.96 * t) * 0.3) * env1
                }
                if (t >= 0.22) {
                    val dt = t - 0.22
                    val env2 = exp(-dt * 4.5)
                    s += (sin(2.0 * PI * 2093.0 * dt) * 0.75 + sin(2.0 * PI * 4186.0 * dt) * 0.25) * env2
                }
                out[i] = (s * 24000).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }

        MemeSoundType.DUCK_QUACK -> {
            val totalSamples = (sampleRate * 0.95).toInt()
            val out = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val dt = when {
                    t in 0.0..0.32 -> t
                    t in 0.45..0.82 -> t - 0.45
                    else -> -1.0
                }
                if (dt >= 0.0) {
                    val baseF = 310.0 + 180.0 * sin(PI * (dt / 0.32))
                    val env = sin(PI * (dt / 0.35).coerceIn(0.0, 1.0))
                    val nasal = sin(2.0 * PI * baseF * dt) * 0.4 +
                        sin(2.0 * PI * baseF * 3.0 * dt) * 0.35 +
                        sin(2.0 * PI * baseF * 5.0 * dt) * 0.25
                    out[i] = (nasal * env * 27000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            out
        }

        MemeSoundType.LEVEL_UP -> {
            val totalSamples = (sampleRate * 1.1).toInt()
            val out = ShortArray(totalSamples)
            val notes = listOf(
                Triple(0.00, 0.14, 523.25),
                Triple(0.15, 0.29, 659.25),
                Triple(0.30, 0.44, 783.99),
                Triple(0.45, 1.05, 1046.50)
            )
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var s = 0.0
                for ((start, end, f) in notes) {
                    if (t in start..end) {
                        val square = if (sin(2.0 * PI * f * t) >= 0.0) 0.8 else -0.8
                        val fade = if (t > 0.85) ((1.05 - t) / 0.20).coerceIn(0.0, 1.0) else 1.0
                        s = square * fade
                        break
                    }
                }
                out[i] = (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
            }
            out
        }
    }
}
