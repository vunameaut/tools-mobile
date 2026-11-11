package com.vunameaut.powertimer.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sin

data class SoundboxTransaction(
    val id: Long = System.currentTimeMillis(),
    val sourceApp: String,
    val amount: Long,
    val rawMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MoneySoundboxService : NotificationListenerService() {

    companion object {
        private const val PREF_NAME = "money_soundbox_prefs"
        private const val KEY_ENABLED = "soundbox_enabled"
        private const val KEY_BOOST_VOLUME = "soundbox_boost_volume"
        private const val KEY_SPEECH_RATE = "soundbox_speech_rate"
        private const val KEY_ALL_APPS_MODE = "soundbox_all_apps_mode"
        private const val KEY_TRANSACTIONS = "soundbox_tx_history"

        private val soundboxScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        private val _transactionsFlow = MutableStateFlow<List<SoundboxTransaction>>(emptyList())
        val transactionsFlow = _transactionsFlow.asStateFlow()

        private var standaloneTts: TextToSpeech? = null
        private var isStandaloneTtsReady: Boolean = false
        private var activeMediaPlayer: MediaPlayer? = null
        private var lastDedupeKey: String = ""
        private var lastDedupeTimeMs: Long = 0L

        val KNOWN_BANK_PACKAGES: Map<String, String> = mapOf(
            "com.VCB" to "Vietcombank",
            "com.mbmobile" to "MB Bank",
            "vn.com.techcombank.bb.app" to "Techcombank",
            "com.vnpay.bidv" to "BIDV",
            "com.vietinbank.ipay" to "VietinBank",
            "com.vnpay.Agribank3g" to "Agribank",
            "mobile.acb.com.vn" to "ACB",
            "com.tpb.mb.gprsandroid" to "TPBank",
            "com.vnpay.vpbankonline" to "VPBank",
            "src.com.sacombank" to "Sacombank",
            "com.vn.vib.mobileapp" to "VIB",
            "com.vnpay.hdbank" to "HDBank",
            "vn.shb.mbanking" to "SHB",
            "vn.com.msb.smartbanking" to "MSB",
            "vn.com.ocb.awe" to "OCB",
            "xyz.be.cake" to "Cake",
            "io.lifestyle.timo" to "Timo",
            "com.mservice.momotransfer" to "MoMo",
            "vn.com.vng.zalopay" to "ZaloPay",
            "com.bplus.vtpay" to "Viettel Money",
            "vn.com.vnpay.customer" to "VNPay"
        )

        fun isNotificationListenerGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            val cn = ComponentName(context, MoneySoundboxService::class.java).flattenToString()
            return flat.contains(cn) || flat.contains(context.packageName)
        }

        /**
         * Yêu cầu hệ thống Android kết nối lại ngay lập tức với NotificationListenerService
         * sau khi người dùng vừa cấp quyền (không cần khởi động lại máy).
         */
        fun ensureServiceRebound(context: Context) {
            if (!isNotificationListenerGranted(context)) return
            runCatching {
                requestRebind(ComponentName(context.applicationContext, MoneySoundboxService::class.java))
            }
        }

        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
            if (enabled) ensureServiceRebound(context)
        }

        fun isBoostVolume(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_BOOST_VOLUME, true)
        }

        fun setBoostVolume(context: Context, boost: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_BOOST_VOLUME, boost)
                .apply()
        }

        fun getSpeechRate(context: Context): Float {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_SPEECH_RATE, 1.0f)
        }

        fun setSpeechRate(context: Context, rate: Float) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_SPEECH_RATE, rate.coerceIn(0.75f, 1.4f))
                .apply()
        }

        fun isAllAppsMode(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALL_APPS_MODE, true)
        }

        fun setAllAppsMode(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALL_APPS_MODE, enabled)
                .apply()
        }

        fun loadTransactions(context: Context): List<SoundboxTransaction> {
            val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TRANSACTIONS, null) ?: return emptyList()
            val list = runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            SoundboxTransaction(
                                id = o.optLong("id", i.toLong()),
                                sourceApp = o.optString("sourceApp", "Ngân hàng"),
                                amount = o.optLong("amount", 0L),
                                rawMessage = o.optString("rawMessage", ""),
                                timestamp = o.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
            _transactionsFlow.value = list
            return list
        }

        fun recordTransaction(context: Context, tx: SoundboxTransaction): List<SoundboxTransaction> {
            val current = loadTransactions(context).toMutableList()
            current.add(0, tx)
            val trimmed = current.take(100)
            val arr = JSONArray()
            for (item in trimmed) {
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("sourceApp", item.sourceApp)
                        put("amount", item.amount)
                        put("rawMessage", item.rawMessage)
                        put("timestamp", item.timestamp)
                    }
                )
            }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TRANSACTIONS, arr.toString())
                .apply()
            _transactionsFlow.value = trimmed
            return trimmed
        }

        fun clearTransactions(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TRANSACTIONS)
                .apply()
            _transactionsFlow.value = emptyList()
        }

        /**
         * Phân tích nội dung thông báo xem có phải biến động cộng tiền (+VND) không.
         * Trả về số tiền (Long) nếu hợp lệ, hoặc null nếu là thông báo trừ tiền / không liên quan.
         */
        fun parseIncomingMoneyAmount(title: String, body: String): Long? {
            val combined = "$title $body".replace('\n', ' ').trim()
            if (combined.isBlank()) return null

            // 1. Mẫu có dấu cộng (+150,000 VND, + 150.000đ, PS:+50,000, GD: +200.000...)
            val plusPattern = Regex(
                """\+\s*(\d{1,3}(?:[.,]\d{3})+|\d{4,11})\s*(?:VND|VNĐ|đ|d|đồng|\b)?""",
                RegexOption.IGNORE_CASE
            )
            val plusMatch = plusPattern.find(combined)
            if (plusMatch != null) {
                val digits = plusMatch.groupValues[1].replace(Regex("[^0-9]"), "")
                val value = digits.toLongOrNull() ?: 0L
                if (value in 1_000L..10_000_000_000L) return value
            }

            // 2. Mẫu ví điện tử / ngân hàng bằng lời: "nhận được 150.000đ", "vừa nhận 50,000 VND", "cộng 200.000đ", "tăng 100,000 VND"
            val verbalPattern = Regex(
                """(?:nhận được|vừa nhận|đã nhận|nhận tiền|cộng thêm|chuyển đến bạn|tăng)\s*:?\s*\+?\s*(\d{1,3}(?:[.,]\d{3})+|\d{4,11})\s*(?:VND|VNĐ|đ|d|đồng)""",
                RegexOption.IGNORE_CASE
            )
            val verbalMatch = verbalPattern.find(combined)
            if (verbalMatch != null) {
                val digits = verbalMatch.groupValues[1].replace(Regex("[^0-9]"), "")
                val value = digits.toLongOrNull() ?: 0L
                if (value in 1_000L..10_000_000_000L) return value
            }

            return null
        }

        /**
         * Chuyển số tiền sang câu đọc tiếng Việt tự nhiên như loa Soundbox cửa hàng.
         * Ví dụ: 150000 -> "150 nghìn đồng", 1250000 -> "1 triệu 250 nghìn đồng"
         */
        fun formatVndForSpeech(amount: Long, sourceApp: String): String {
            val ty = amount / 1_000_000_000L
            val trieu = (amount % 1_000_000_000L) / 1_000_000L
            val nghin = (amount % 1_000_000L) / 1_000L
            val le = amount % 1_000L

            val parts = mutableListOf<String>()
            if (ty > 0) parts.add("$ty tỷ")
            if (trieu > 0) parts.add("$trieu triệu")
            if (nghin > 0) parts.add("$nghin nghìn")
            if (le > 0 || parts.isEmpty()) parts.add("$le")

            val amountWords = parts.joinToString(" ") + " đồng"
            return if (sourceApp.isNotBlank() && sourceApp != "Thử nghiệm") {
                "Đã nhận $amountWords vào $sourceApp"
            } else {
                "Đã nhận $amountWords"
            }
        }

        /**
         * Phát tiếng chuông Ting-Ting + Đọc to số tiền qua kênh MEDIA (STREAM_MUSIC)
         * để đảm bảo 100% phát ra tiếng kể cả khi điện thoại đang để chế độ Rung / Im lặng thông báo.
         */
        fun announceMoneyReceived(
            context: Context,
            amount: Long,
            sourceApp: String,
            rawMessage: String,
            saveToHistory: Boolean = true
        ) {
            val appCtx = context.applicationContext
            ensureServiceRebound(appCtx)

            if (saveToHistory) {
                recordTransaction(
                    appCtx,
                    SoundboxTransaction(
                        sourceApp = sourceApp,
                        amount = amount,
                        rawMessage = rawMessage
                    )
                )
            }

            val speechText = formatVndForSpeech(amount, sourceApp)
            val rate = getSpeechRate(appCtx)

            soundboxScope.launch {
                // Thông báo trực quan trên màn hình ngay lập tức
                Toast.makeText(
                    appCtx,
                    "🔊 Loa Soundbox: \"$speechText\"",
                    Toast.LENGTH_SHORT
                ).show()

                // Đảm bảo âm lượng kênh Media (STREAM_MUSIC) đủ lớn để nghe rõ
                ensureMediaVolumeAudible(appCtx)

                // Bước 1: Phát tiếng chuông "Ting - Ting!" bằng sóng âm tổng hợp trên kênh STREAM_MUSIC
                withContext(Dispatchers.Default) {
                    playSynthesizedTingTingBell()
                }

                // Bước 2: Đọc giọng nói Tiếng Việt (ưu tiên giọng đọc Tiếng Việt chuẩn tự nhiên + fallback TTS nội bộ)
                val playedOnline = withContext(Dispatchers.IO) {
                    playNaturalVietnameseVoice(appCtx, speechText, rate)
                }
                if (!playedOnline) {
                    speakViaSystemTts(appCtx, speechText, rate)
                }
            }
        }

        private fun ensureMediaVolumeAudible(context: Context) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (isBoostVolume(context)) {
                val targetVol = (maxVol * 0.85f).toInt().coerceAtLeast(currentVol)
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
            } else if (currentVol == 0) {
                // Nếu âm lượng Media đang bị tắt hẳn về 0, tự nâng lên 50% để người dùng nghe thấy khi bấm thử
                val minAudible = (maxVol * 0.5f).toInt().coerceAtLeast(1)
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, minAudible, 0)
                }
            }
        }

        /**
         * Tạo âm thanh chuông "Ting-Ting!" chuẩn loa thanh toán cửa hàng bằng PCM 16-bit trên kênh USAGE_MEDIA.
         * Không dùng ToneGenerator(STREAM_NOTIFICATION) vì sẽ bị tắt tiếng khi máy bật chế độ Rung / Không làm phiền.
         */
        private fun playSynthesizedTingTingBell() {
            runCatching {
                val sampleRate = 44100
                val durationMs = 460
                val totalSamples = (sampleRate * durationMs) / 1000
                val pcm = ShortArray(totalSamples)

                val note1Samples = (sampleRate * 145) / 1000
                val freq1 = 1318.5 // Note E6
                val freq2 = 1760.0 // Note A6

                for (i in 0 until totalSamples) {
                    val isFirstNote = i < note1Samples
                    val localIdx = if (isFirstNote) i else (i - note1Samples)
                    val t = localIdx.toDouble() / sampleRate
                    val freq = if (isFirstNote) freq1 else freq2
                    val envelope = exp(-t * (if (isFirstNote) 11.0 else 7.5))
                    val fundamental = sin(2.0 * Math.PI * freq * t)
                    val harmonic = 0.35 * sin(2.0 * Math.PI * freq * 2.0 * t)
                    val sample = ((fundamental + harmonic) * envelope * 0.72 * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    pcm[i] = sample.toShort()
                }

                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttrs)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 40L)
                track.stop()
                track.release()
            }
        }

        /**
         * Phát giọng đọc Tiếng Việt chuẩn tự nhiên qua MediaPlayer (USAGE_MEDIA) có lưu cache cục bộ,
         * giúp mọi dòng máy (kể cả máy xách tay chưa cài gói giọng đọc Tiếng Việt) đều phát loa rõ ràng.
         */
        private fun playNaturalVietnameseVoice(
            context: Context,
            speechText: String,
            rate: Float
        ): Boolean {
            return runCatching {
                val cacheDir = File(context.cacheDir, "soundbox_tts_cache").apply { mkdirs() }
                val hash = MessageDigest.getInstance("MD5")
                    .digest(speechText.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                val audioFile = File(cacheDir, "vi_$hash.mp3")

                if (!audioFile.exists() || audioFile.length() < 256L) {
                    val encoded = URLEncoder.encode(speechText, "UTF-8")
                    val urlStr = "https://translate.google.com/translate_tts?ie=UTF-8&tl=vi&client=tw-ob&q=$encoded"
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3500
                        readTimeout = 4500
                        requestMethod = "GET"
                        setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        )
                    }
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        conn.disconnect()
                        return false
                    }
                    conn.inputStream.use { input ->
                        audioFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    conn.disconnect()
                }

                if (!audioFile.exists() || audioFile.length() < 256L) return false

                soundboxScope.launch(Dispatchers.Main) {
                    runCatching {
                        activeMediaPlayer?.release()
                        val mp = MediaPlayer()
                        activeMediaPlayer = mp
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        mp.setDataSource(audioFile.absolutePath)
                        mp.prepare()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            runCatching {
                                mp.playbackParams = PlaybackParams().setSpeed(rate.coerceIn(0.8f, 1.35f))
                            }
                        }
                        mp.setOnCompletionListener { player ->
                            player.release()
                            if (activeMediaPlayer === player) activeMediaPlayer = null
                        }
                        mp.start()
                    }
                }
                true
            }.getOrDefault(false)
        }

        /**
         * Dự phòng Offline: Đọc bằng TextToSpeech hệ thống trên kênh USAGE_MEDIA / STREAM_MUSIC.
         */
        private fun speakViaSystemTts(
            context: Context,
            speechText: String,
            rate: Float
        ) {
            val appCtx = context.applicationContext
            val mediaAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }

            val existing = standaloneTts
            if (existing != null && isStandaloneTtsReady) {
                existing.setAudioAttributes(mediaAttrs)
                existing.setSpeechRate(rate)
                existing.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, "tx_${System.currentTimeMillis()}")
                return
            }

            var ttsRef: TextToSpeech? = null
            ttsRef = TextToSpeech(appCtx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = ttsRef ?: return@TextToSpeech
                    engine.setAudioAttributes(mediaAttrs)
                    val viRes = engine.setLanguage(Locale("vi", "VN"))
                    if (viRes == TextToSpeech.LANG_MISSING_DATA || viRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.language = Locale.getDefault()
                    }
                    isStandaloneTtsReady = true
                    engine.setSpeechRate(rate)
                    engine.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, "tx_${System.currentTimeMillis()}")
                }
            }
            standaloneTts = ttsRef
        }
    }

    override fun onCreate() {
        super.onCreate()
        loadTransactions(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        loadTransactions(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching {
            requestRebind(ComponentName(applicationContext, MoneySoundboxService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (!isEnabled(applicationContext)) return

        val pkg = sbn.packageName.orEmpty()
        // Bỏ qua thông báo hệ thống
        if (pkg == "android" || pkg == "com.android.systemui") return

        val knownBankName = KNOWN_BANK_PACKAGES[pkg]
        val allowAllApps = isAllAppsMode(applicationContext)
        if (knownBankName == null && !allowAllApps) return

        val extras: Bundle = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val body = listOf(bigText, text, subText).filter { it.isNotBlank() }.joinToString(" ")

        // Chặn đọc lặp thông báo của chính dịch vụ hẹn giờ / tải video
        if (pkg == applicationContext.packageName && !title.contains("Biến động số dư", ignoreCase = true)) {
            return
        }

        val amount = parseIncomingMoneyAmount(title, body) ?: return
        val bankLabel = knownBankName ?: detectBankNameFromContent("$title $body")

        // Chống đọc lặp khi 1 thông báo cập nhật liên tiếp trong 4 giây
        val dedupeKey = "$pkg:$amount:${body.take(30)}"
        val now = System.currentTimeMillis()
        if (dedupeKey == lastDedupeKey && (now - lastDedupeTimeMs) < 4000L) {
            return
        }
        lastDedupeKey = dedupeKey
        lastDedupeTimeMs = now

        announceMoneyReceived(
            context = applicationContext,
            amount = amount,
            sourceApp = bankLabel,
            rawMessage = "$title: $text".trim(':', ' '),
            saveToHistory = true
        )
    }

    private fun detectBankNameFromContent(content: String): String {
        val upper = content.uppercase(Locale.getDefault())
        for (bank in KNOWN_BANK_PACKAGES.values) {
            if (upper.contains(bank.uppercase(Locale.getDefault()))) {
                return bank
            }
        }
        return when {
            upper.contains("VCB") || upper.contains("VIETCOMBANK") -> "Vietcombank"
            upper.contains("MBBANK") || upper.contains("MB ") -> "MB Bank"
            upper.contains("TCB") || upper.contains("TECHCOMBANK") -> "Techcombank"
            upper.contains("BIDV") -> "BIDV"
            upper.contains("VIETINBANK") || upper.contains("CTG") -> "VietinBank"
            upper.contains("AGRIBANK") -> "Agribank"
            upper.contains("ACB") -> "ACB"
            upper.contains("TPBANK") -> "TPBank"
            upper.contains("VPBANK") -> "VPBank"
            upper.contains("MOMO") -> "MoMo"
            upper.contains("ZALOPAY") -> "ZaloPay"
            else -> "Tài khoản"
        }
    }
}
