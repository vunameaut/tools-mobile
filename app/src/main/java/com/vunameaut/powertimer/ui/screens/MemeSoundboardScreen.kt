package com.vunameaut.powertimer.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeSoundboardTool(onExit: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    var activeSoundId by remember { mutableStateOf<String?>(null) }
    var includeVoiceMeme by remember { mutableStateOf(true) }
    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var ttsRef by remember { mutableStateOf<TextToSpeech?>(null) }

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
                ttsInstance?.stop()
                ttsInstance?.shutdown()
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
        FakeCallScheduler.stopActiveVoice()
        runCatching { ttsRef?.stop() }
        activeSoundId = null
    }

    fun playMemeSound(sound: MemeSoundType) {
        stopAllSounds()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        activeSoundId = sound.id

        // Đảm bảo âm lượng Media đủ nghe nếu đang ở mức 0
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (cur == 0 && max > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.6f).toInt().coerceAtLeast(1), 0)
            }
        }

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
                            text = "Túi Âm Thanh Vui Nhộn (Soundboard)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Vỗ tay • Dế kêu • Trống Tùng Xèng • Còi hơi • Cười khả ố",
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item(span = { GridItemSpan(2) }) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                text = "Chạm vào bất kỳ ô nào bên dưới để phát âm thanh tức thì (100% Offline, không cần mạng)",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

            items(MemeSoundType.entries, key = { it.id }) { sound ->
                val isPlaying = activeSoundId == sound.id
                val scale by animateFloatAsState(if (isPlaying) 0.96f else 1f, label = "pad_scale")

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isPlaying) {
                        sound.accent.copy(alpha = 0.28f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .border(
                            width = if (isPlaying) 2.5.dp else 1.dp,
                            color = if (isPlaying) sound.accent else sound.accent.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable { playMemeSound(sound) }
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
                                            sound.accent.copy(alpha = 0.35f),
                                            sound.accent.copy(alpha = 0.08f)
                                        )
                                    )
                                )
                        ) {
                            Text(text = sound.emoji, fontSize = 28.sp)
                        }

                        Text(
                            text = sound.title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = if (isPlaying) sound.accent else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (isPlaying) "🔊 Đang phát..." else sound.subtitle,
                            fontSize = 11.5.sp,
                            color = if (isPlaying) sound.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
                // 3 cụm kêu, mỗi cụm 0.42s, nghỉ 0.3s
                val cycle = t % 0.72
                val amp = if (cycle < 0.42) {
                    // Xung nhịp 34Hz trong mỗi lần dế cọ cánh
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
                // Tùng 1 (High Tom)
                if (t in 0.0..0.20) {
                    val dt = t
                    val freq = 165.0 - 75.0 * (dt / 0.20)
                    val env = exp(-dt * 18.0)
                    sample += sin(2.0 * PI * freq * dt) * env * 0.95
                }
                // Tùng 2 (Low Tom + Snare snap)
                if (t in 0.21..0.44) {
                    val dt = t - 0.21
                    val freq = 125.0 - 55.0 * (dt / 0.23)
                    val env = exp(-dt * 16.0)
                    val snareNoise = (rng.nextDouble() * 2.0 - 1.0) * exp(-dt * 32.0) * 0.35
                    sample += sin(2.0 * PI * freq * dt) * env * 0.95 + snareNoise
                }
                // Xènggg! (Crash Cymbal metallic shimmer)
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
            // Còi hơi sân vận động: 2 nhịp ngắn + 1 nhịp kéo dài (BÈO - BÈO - BÈOOOOOO!)
            val totalSamples = (sampleRate * 1.85).toInt()
            val out = ShortArray(totalSamples)
            val freqs = doubleArrayOf(392.0, 493.88, 587.33, 784.0)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val active = (t in 0.0..0.16) || (t in 0.22..0.38) || (t in 0.44..1.75)
                if (active) {
                    var sum = 0.0
                    for (f in freqs) {
                        // Sóng răng cưa giàu họa âm đồng thau
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
            // Tiếng vỗ tay rần rần từ hàng chục người chồng lớp ngẫu nhiên
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
            // Chuỗi âm tiết cười "Ha - Ha - Ha - Ha - Hà!" nhấp nhô cao độ
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
            // 4 nốt kèn đồng thất bại: D4 -> C#4 -> C4 -> B3 (trượt xuống + rung vibrato)
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
            // Tiếng Sub-Bass Drop nổ trầm chấn động
            val totalSamples = (sampleRate * 1.35).toInt()
            val out = ShortArray(totalSamples)
            var phase = 0.0
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val freq = 36.0 + 125.0 * exp(-t * 14.0)
                phase += (2.0 * PI * freq) / sampleRate
                val env = exp(-t * 2.6)
                // Distortion nhẹ ở đầu để tạo lực đấm (punch)
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
            // Còi báo sai "ÈN ÈN!"
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
            // Chuông ngân vang "Tinh Tinh!"
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
            // 2 tiếng vịt kêu "Quạc! Quạc!"
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
            // Nhạc 8-Bit Level Up: C5 - E5 - G5 - C6
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

