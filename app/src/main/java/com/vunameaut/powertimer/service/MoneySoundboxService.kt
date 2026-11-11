package com.vunameaut.powertimer.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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

        private val _transactionsFlow = MutableStateFlow<List<SoundboxTransaction>>(emptyList())
        val transactionsFlow = _transactionsFlow.asStateFlow()

        private var activeInstance: MoneySoundboxService? = null
        private var standaloneTts: TextToSpeech? = null
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

            // Loại trừ thông báo trừ tiền rõ ràng nếu không có dấu cộng
            val plusPattern = Regex("""\+\s*(\d{1,3}(?:[.,]\d{3})+|\d{4,10})\s*(?:VND|VNĐ|đ|d|đồng)?""", RegexOption.IGNORE_CASE)
            val plusMatch = plusPattern.find(combined)
            if (plusMatch != null) {
                val digits = plusMatch.groupValues[1].replace(Regex("[^0-9]"), "")
                val value = digits.toLongOrNull() ?: 0L
                if (value in 1_000L..10_000_000_000L) return value
            }

            // Mẫu ví điện tử / ngân hàng bằng lời: "nhận được 150.000đ", "vừa nhận 50,000 VND", "cộng 200.000đ"
            val verbalPattern = Regex(
                """(?:nhận được|vừa nhận|đã nhận|cộng thêm|chuyển đến bạn)\s+(\d{1,3}(?:[.,]\d{3})+|\d{4,10})\s*(?:VND|VNĐ|đ|d|đồng)""",
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
         * Phát tiếng chuông Ting-Ting + Đọc to số tiền (dùng chung cho cả Service thực tế và nút Nghe thử).
         */
        fun announceMoneyReceived(
            context: Context,
            amount: Long,
            sourceApp: String,
            rawMessage: String,
            saveToHistory: Boolean = true
        ) {
            if (saveToHistory) {
                recordTransaction(
                    context.applicationContext,
                    SoundboxTransaction(
                        sourceApp = sourceApp,
                        amount = amount,
                        rawMessage = rawMessage
                    )
                )
            }

            val svc = activeInstance
            if (svc != null) {
                svc.speakAmountInternal(amount, sourceApp)
                return
            }

            // Fallback khi người dùng bấm "Nghe thử" lúc chưa cấp quyền NotificationListener
            playTingTingChime()
            val appCtx = context.applicationContext
            val speechText = formatVndForSpeech(amount, sourceApp)
            val rate = getSpeechRate(appCtx)

            val existing = standaloneTts
            if (existing != null) {
                existing.setSpeechRate(rate)
                existing.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "tx_${System.currentTimeMillis()}")
            } else {
                var ttsRef: TextToSpeech? = null
                ttsRef = TextToSpeech(appCtx) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        ttsRef?.language = Locale("vi", "VN")
                        ttsRef?.setSpeechRate(rate)
                        ttsRef?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "tx_${System.currentTimeMillis()}")
                    }
                }
                standaloneTts = ttsRef
            }
        }

        private fun playTingTingChime() {
            CoroutineScope(Dispatchers.Default).launch {
                runCatching {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
                    delay(160)
                    toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 240)
                    delay(300)
                    toneGen.release()
                }
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        loadTransactions(applicationContext)
        initTts()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val viLocale = Locale("vi", "VN")
                val res = tts?.setLanguage(viLocale)
                isTtsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isTtsReady) {
                    tts?.language = Locale.getDefault()
                    isTtsReady = true
                }
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (!isEnabled(applicationContext)) return

        val pkg = sbn.packageName.orEmpty()
        // Bỏ qua thông báo hệ thống hoặc của chính phần hẹn giờ/tải video trong app
        if (pkg == "android" || pkg == "com.android.systemui") return

        val knownBankName = KNOWN_BANK_PACKAGES[pkg]
        val allowAllApps = isAllAppsMode(applicationContext)
        if (knownBankName == null && !allowAllApps) return

        val extras: Bundle = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = if (bigText.length > text.length) bigText else text

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

        recordTransaction(
            applicationContext,
            SoundboxTransaction(
                sourceApp = bankLabel,
                amount = amount,
                rawMessage = "$title: $body".trim(':', ' ')
            )
        )
        speakAmountInternal(amount, bankLabel)
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

    internal fun speakAmountInternal(amount: Long, sourceApp: String) {
        playTingTingChime()
        val speech = formatVndForSpeech(amount, sourceApp)
        val rate = getSpeechRate(applicationContext)

        CoroutineScope(Dispatchers.Main).launch {
            delay(360)
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val originalVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (isBoostVolume(applicationContext) && audioManager != null) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (maxVol * 0.85f).toInt().coerceAtLeast(originalVolume ?: 0)
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
            }

            if (tts == null || !isTtsReady) {
                initTts()
                delay(400)
            }
            tts?.setSpeechRate(rate)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(speech, TextToSpeech.QUEUE_ADD, params, "soundbox_${System.currentTimeMillis()}")
        }
    }
}

