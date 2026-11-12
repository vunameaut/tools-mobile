package com.vunameaut.powertimer.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

data class ScannedNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val body: String,
    val parsedAmount: Long?,
    val isIncomingMoney: Boolean,
    val isKnownBankApp: Boolean,
    val postTime: Long
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

        @Volatile
        var activeInstance: MoneySoundboxService? = null
            private set

        private val _serviceConnectedFlow = MutableStateFlow(false)
        val serviceConnectedFlow = _serviceConnectedFlow.asStateFlow()

        @Volatile
        var lastAnnouncedTimestampMs: Long = 0L
            private set

        private var standaloneTts: TextToSpeech? = null
        private var isStandaloneTtsReady: Boolean = false
        private var activeMediaPlayer: MediaPlayer? = null
        private var lastDedupeKey: String = ""
        private var lastDedupeTimeMs: Long = 0L

        val KNOWN_BANK_PACKAGES: Map<String, String> = mapOf(
            "com.VCB" to "Vietcombank",
            "com.mbmobile" to "MB Bank",
            "vn.com.techcombank.bb.app" to "Techcombank",
            "com.techcombank.bb.app" to "Techcombank",
            "com.vnpay.bidv" to "BIDV",
            "com.vietinbank.ipay" to "VietinBank",
            "com.vnpay.Agribank3g" to "Agribank",
            "com.vnpay.agribank" to "Agribank",
            "mobile.acb.com.vn" to "ACB",
            "com.acb.acbmobile" to "ACB",
            "com.tpb.mb.gprsandroid" to "TPBank",
            "com.tpbank.mb" to "TPBank",
            "com.vnpay.vpbankonline" to "VPBank",
            "src.com.sacombank" to "Sacombank",
            "com.sacombank.ewallet" to "Sacombank",
            "com.vn.vib.mobileapp" to "VIB",
            "com.vib.myvib2" to "VIB",
            "com.vnpay.hdbank" to "HDBank",
            "vn.shb.mbanking" to "SHB",
            "com.shb.saigonhanbank" to "SHB",
            "vn.com.msb.smartbanking" to "MSB",
            "vn.com.ocb.awe" to "OCB",
            "com.ocb.omnihub" to "OCB",
            "vn.com.seabank.mb1" to "SeABank",
            "vn.com.lpb.lienviet24h" to "LPBank",
            "com.vnpay.eximbank" to "Eximbank",
            "ops.namabank.com.vn" to "Nam A Bank",
            "com.vnpay.namabank" to "Nam A Bank",
            "com.vsii.pvcombank" to "PVcomBank",
            "com.vnpay.vietabank" to "VietABank",
            "com.vnpay.vietbank" to "Vietbank",
            "com.sunshine.ksbank" to "KienlongBank",
            "vn.banvietbank.mobilebanking" to "BVBank",
            "com.timo.bvbank" to "BVBank",
            "xyz.be.cake" to "Cake",
            "io.lifestyle.timo" to "Timo",
            "vn.timo.plus" to "Timo",
            "vn.tnex.consumer" to "TNEX",
            "com.ocb.liobank" to "Liobank",
            "com.mservice.momotransfer" to "MoMo",
            "vn.com.vng.zalopay" to "ZaloPay",
            "com.bplus.vtpay" to "Viettel Money",
            "vn.com.vnpay.customer" to "VNPay",
            "com.airpay.vn" to "ShopeePay"
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
         * Đảm bảo dịch vụ NotificationListenerService được kết nối.
         * Nếu đã cấp quyền nhưng hệ điều hành (OriginOS/MIUI/ColorOS) chưa bind hoặc bị kẹt sau khi cập nhật app,
         * tự động bật/tắt Component + requestRebind để ép hệ thống kết nối lại ngay lập tức.
         */
        fun ensureServiceRebound(context: Context) {
            if (!isNotificationListenerGranted(context)) return
            if (activeInstance != null) return
            forceReconnectNotificationListener(context)
        }

        /**
         * Ép hệ thống Android kết nối lại NotificationListenerService bằng cách làm mới trạng thái Component.
         * Khắc phục triệt để lỗi "Đã cấp quyền Đọc thông báo nhưng không nhận được sự kiện" trên Vivo/Xiaomi/OPPO.
         */
        fun forceReconnectNotificationListener(context: Context) {
            if (!isNotificationListenerGranted(context)) return
            val appCtx = context.applicationContext
            val cn = ComponentName(appCtx, MoneySoundboxService::class.java)
            runCatching {
                val pm = appCtx.packageManager
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                requestRebind(cn)
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
         * Trích xuất đầy đủ tiêu đề & nội dung từ Notification (bao gồm bigText, textLines, tickerText, publicVersion).
         */
        fun extractNotificationTitleAndBody(notification: Notification?): Pair<String, String> {
            if (notification == null) return "" to ""
            val extras: Bundle = notification.extras ?: Bundle.EMPTY

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
                ?: ""

            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim().orEmpty()
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim().orEmpty()
            val linesText = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim() }
                ?.filter { it.isNotBlank() }
                ?.joinToString(" ")
                .orEmpty()
            val ticker = notification.tickerText?.toString()?.trim().orEmpty()

            val primaryText = when {
                bigText.isNotBlank() && bigText.contains(text) -> bigText
                text.isNotBlank() && bigText.isNotBlank() -> "$text | $bigText"
                bigText.isNotBlank() -> bigText
                text.isNotBlank() -> text
                linesText.isNotBlank() -> linesText
                ticker.isNotBlank() -> ticker
                else -> ""
            }

            val extraParts = mutableListOf<String>()
            if (primaryText.isNotBlank()) extraParts.add(primaryText)
            if (subText.isNotBlank() && !primaryText.contains(subText)) extraParts.add(subText)
            if (summaryText.isNotBlank() && !primaryText.contains(summaryText)) extraParts.add(summaryText)
            if (infoText.isNotBlank() && !primaryText.contains(infoText)) extraParts.add(infoText)

            var body = extraParts.joinToString(" ").trim()

            // Nếu thông báo bị ẩn ở bản chính nhưng có trong publicVersion
            if (title.isBlank() && body.isBlank() && notification.publicVersion != null) {
                return extractNotificationTitleAndBody(notification.publicVersion)
            }
            return title to body
        }

        /**
         * Phân tích nội dung thông báo xem có phải biến động cộng tiền (+VND) không.
         * Hỗ trợ đầy đủ cả Tiếng Việt có dấu & không dấu từ mọi ngân hàng Việt Nam (VCB, MB, TCB, BIDV, CTG, ACB, TPB, VPB, Agribank, MoMo, ZaloPay...).
         */
        fun parseIncomingMoneyAmount(title: String, body: String): Long? {
            val combined = "$title $body".replace('\n', ' ').trim()
            if (combined.isBlank()) return null

            // 1. Mẫu có dấu cộng (+150,000 VND, + 150.000đ, PS:+50,000, GD: +200.000VND, +VND 50,000...)
            val plusPattern = Regex(
                """\+\s*(?:VND|VNĐ|đ|d)?\s*(\d{1,3}(?:[.,]\d{3})+|\d{4,12})\s*(?:VND|VNĐ|đ|d|đồng|\b)?""",
                RegexOption.IGNORE_CASE
            )
            for (match in plusPattern.findAll(combined)) {
                val rawNum = match.groupValues[1]
                val fullMatch = match.value
                // Bỏ qua mã vùng số điện thoại +84... nếu không có dấu chấm/phẩy phân cách hàng nghìn và không có đơn vị tiền tệ
                val hasSeparator = rawNum.contains('.') || rawNum.contains(',')
                val hasCurrency = fullMatch.contains(Regex("""(?:VND|VNĐ|đ|đồng)""", RegexOption.IGNORE_CASE))
                val digits = rawNum.replace(Regex("[^0-9]"), "")
                if (!hasSeparator && !hasCurrency && digits.startsWith("84") && digits.length in 10..11) {
                    continue
                }
                val value = digits.toLongOrNull() ?: 0L
                if (value in 1_000L..50_000_000_000L) return value
            }

            // 2. Mẫu ví điện tử / ngân hàng bằng lời (có dấu & không dấu):
            // "nhận được 150.000đ", "vua nhan 50,000 VND", "cộng 200.000đ", "tăng 100,000 VND", "ghi có 500,000 VND", "tiền vào 300.000đ"
            val verbalPattern = Regex(
                """(?:nhận được|nhan duoc|vừa nhận|vua nhan|đã nhận|da nhan|nhận tiền|nhan tien|nhận thành công|nhan thanh cong|cộng thêm|cong them|được cộng|duoc cong|cộng|cong|chuyển đến|chuyen den|tiền vào|tien vao|ghi có|ghi co|tăng|tang)[^0-9+\-]{0,25}\+?\s*(\d{1,3}(?:[.,]\d{3})+|\d{4,12})\s*(?:VND|VNĐ|đ|d|đồng)?""",
                RegexOption.IGNORE_CASE
            )
            val verbalMatch = verbalPattern.find(combined)
            if (verbalMatch != null) {
                val digits = verbalMatch.groupValues[1].replace(Regex("[^0-9]"), "")
                val value = digits.toLongOrNull() ?: 0L
                if (value in 1_000L..50_000_000_000L) return value
            }

            // 3. Mẫu thông báo "Biến động số dư" / "Số tiền:" / "GD:" / "PS:" không có dấu trừ (-) hoặc từ khóa trừ tiền
            val hasBankBalanceTrigger = combined.contains(
                Regex("""(?:biến động số dư|bien dong so du|số tiền|so tien|giao dịch|giao dich|\bGD\s*:|\bPS\s*:)""", RegexOption.IGNORE_CASE)
            )
            val hasOutgoingKeyword = combined.contains(
                Regex("""(?:-\s*\d|\btrừ\b|\bgiam\b|\bgiảm\b|thanh toán|thanh toan|chuyển đi|chuyen di|rút tiền|rut tien)""", RegexOption.IGNORE_CASE)
            )
            if (hasBankBalanceTrigger && !hasOutgoingKeyword) {
                val fallbackCurrencyPattern = Regex(
                    """(\d{1,3}(?:[.,]\d{3})+|\d{4,12})\s*(?:VND|VNĐ|đ|đồng)""",
                    RegexOption.IGNORE_CASE
                )
                val fbMatch = fallbackCurrencyPattern.find(combined)
                if (fbMatch != null) {
                    val digits = fbMatch.groupValues[1].replace(Regex("[^0-9]"), "")
                    val value = digits.toLongOrNull() ?: 0L
                    if (value in 1_000L..50_000_000_000L) return value
                }
            }

            return null
        }

        /**
         * Tách bất kỳ số tiền nào có trong thông báo ngân hàng (phục vụ nút "Đọc thông báo ngân hàng hiện tại"
         * để người dùng test ngay cả khi thông báo hiện tại trên thanh trạng thái là thông báo số dư hoặc trừ tiền).
         */
        fun parseAnyMoneyAmount(title: String, body: String): Long? {
            parseIncomingMoneyAmount(title, body)?.let { return it }
            val combined = "$title $body".replace('\n', ' ').trim()
            if (combined.isBlank()) return null

            val currencyPattern = Regex(
                """[+\-]?\s*(\d{1,3}(?:[.,]\d{3})+|\d{4,12})\s*(?:VND|VNĐ|đ|d|đồng)\b""",
                RegexOption.IGNORE_CASE
            )
            val match = currencyPattern.find(combined)
            if (match != null) {
                val digits = match.groupValues[1].replace(Regex("[^0-9]"), "")
                val value = digits.toLongOrNull() ?: 0L
                if (value in 1_000L..50_000_000_000L) return value
            }
            return null
        }

        fun detectBankNameFromContent(content: String, fallbackLabel: String = "Tài khoản"): String {
            val upper = content.uppercase(Locale.getDefault())
            for (bank in KNOWN_BANK_PACKAGES.values.distinct()) {
                if (upper.contains(bank.uppercase(Locale.getDefault()))) {
                    return bank
                }
            }
            return when {
                upper.contains("VCB") || upper.contains("VIETCOMBANK") -> "Vietcombank"
                upper.contains("MBBANK") || upper.contains("MB BANK") || upper.contains("MB ") -> "MB Bank"
                upper.contains("TCB") || upper.contains("TECHCOMBANK") -> "Techcombank"
                upper.contains("BIDV") -> "BIDV"
                upper.contains("VIETINBANK") || upper.contains("CTG") || upper.contains("IPAY") -> "VietinBank"
                upper.contains("AGRIBANK") -> "Agribank"
                upper.contains("ACB") -> "ACB"
                upper.contains("TPBANK") || upper.contains("TPB") -> "TPBank"
                upper.contains("VPBANK") || upper.contains("VPB") -> "VPBank"
                upper.contains("SACOMBANK") || upper.contains("STB") -> "Sacombank"
                upper.contains("VIB") -> "VIB"
                upper.contains("HDBANK") -> "HDBank"
                upper.contains("SHB") -> "SHB"
                upper.contains("MSB") -> "MSB"
                upper.contains("OCB") -> "OCB"
                upper.contains("SEABANK") -> "SeABank"
                upper.contains("LPBANK") || upper.contains("LIENVIET") -> "LPBank"
                upper.contains("NAM A") || upper.contains("NAMABANK") -> "Nam A Bank"
                upper.contains("CAKE") -> "Cake"
                upper.contains("TIMO") -> "Timo"
                upper.contains("TNEX") -> "TNEX"
                upper.contains("MOMO") -> "MoMo"
                upper.contains("ZALOPAY") -> "ZaloPay"
                upper.contains("VIETTEL") -> "Viettel Money"
                upper.contains("VNPAY") -> "VNPay"
                else -> fallbackLabel
            }
        }

        private fun resolveAppDisplayName(context: Context, pkg: String, title: String, body: String): String {
            KNOWN_BANK_PACKAGES[pkg]?.let { return it }
            val detected = detectBankNameFromContent("$title $body", "")
            if (detected.isNotBlank()) return detected
            return runCatching {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(pkg)
        }

        private fun isBankRelatedNotification(pkg: String, title: String, body: String): Boolean {
            if (KNOWN_BANK_PACKAGES.containsKey(pkg)) return true
            val combined = "$title $body"
            if (detectBankNameFromContent(combined, "").isNotBlank()) return true
            val lowerPkg = pkg.lowercase(Locale.ROOT)
            if (lowerPkg.contains("bank") || lowerPkg.contains("pay") || lowerPkg.contains("wallet") || lowerPkg.contains("momo")) {
                return true
            }
            return combined.contains(
                Regex("""(?:biến động số dư|bien dong so du|số dư|so du|\bTK\b|tài khoản|tai khoan|chuyển khoản|chuyen khoan|VND|VNĐ)""", RegexOption.IGNORE_CASE)
            )
        }

        /**
         * Quét toàn bộ thông báo đang hiển thị trên thanh trạng thái (Status Bar) hiện tại.
         * Nếu [autoSpeakBestMatch] = true, tự động phát loa đọc ngay thông báo ngân hàng / biến động số dư mới nhất tìm thấy.
         */
        fun scanAndReadCurrentNotifications(
            context: Context,
            autoSpeakBestMatch: Boolean = true
        ): List<ScannedNotificationItem> {
            val appCtx = context.applicationContext
            val service = activeInstance ?: return emptyList()
            val rawNotifications = runCatching { service.activeNotifications }.getOrNull()?.toList() ?: return emptyList()

            val items = mutableListOf<ScannedNotificationItem>()
            for (sbn in rawNotifications) {
                val pkg = sbn.packageName.orEmpty()
                if (pkg == "android" || pkg == "com.android.systemui") continue

                val (title, body) = extractNotificationTitleAndBody(sbn.notification)
                if (title.isBlank() && body.isBlank()) continue

                // Bỏ qua thông báo hẹn giờ/tải video của chính app (chỉ giữ lại thông báo giả lập Ngân hàng nếu có)
                if (pkg == appCtx.packageName && !title.contains("Biến động số dư", ignoreCase = true)) {
                    continue
                }

                val incomingAmount = parseIncomingMoneyAmount(title, body)
                val anyAmount = incomingAmount ?: parseAnyMoneyAmount(title, body)
                val isBank = isBankRelatedNotification(pkg, title, body)
                val appName = resolveAppDisplayName(appCtx, pkg, title, body)

                items.add(
                    ScannedNotificationItem(
                        key = "${sbn.key}_${sbn.postTime}",
                        packageName = pkg,
                        appName = appName,
                        title = title,
                        body = body,
                        parsedAmount = anyAmount,
                        isIncomingMoney = incomingAmount != null,
                        isKnownBankApp = isBank,
                        postTime = sbn.postTime
                    )
                )
            }

            // Sắp xếp ưu tiên: Thông báo cộng tiền (+) -> Thông báo Ngân hàng có số tiền -> Thông báo Ngân hàng -> Các thông báo khác (mới nhất lên đầu)
            val sorted = items.sortedWith(
                compareByDescending<ScannedNotificationItem> { it.isIncomingMoney }
                    .thenByDescending { it.isKnownBankApp && it.parsedAmount != null }
                    .thenByDescending { it.parsedAmount != null }
                    .thenByDescending { it.isKnownBankApp }
                    .thenByDescending { it.postTime }
            )

            if (autoSpeakBestMatch) {
                val best = sorted.firstOrNull { it.isIncomingMoney || it.isKnownBankApp || it.parsedAmount != null }
                if (best != null) {
                    if (best.parsedAmount != null) {
                        announceMoneyReceived(
                            context = appCtx,
                            amount = best.parsedAmount,
                            sourceApp = best.appName,
                            rawMessage = "${best.title}: ${best.body}".trim(':', ' '),
                            saveToHistory = true
                        )
                    } else {
                        announceCustomNotificationText(
                            context = appCtx,
                            sourceApp = best.appName,
                            rawMessage = "${best.title}. ${best.body}".trim('.', ' ')
                        )
                    }
                }
            }

            return sorted
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
            lastAnnouncedTimestampMs = System.currentTimeMillis()
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
                Toast.makeText(
                    appCtx,
                    "🔊 Loa Soundbox: \"$speechText\"",
                    Toast.LENGTH_SHORT
                ).show()

                ensureMediaVolumeAudible(appCtx)

                withContext(Dispatchers.Default) {
                    playSynthesizedTingTingBell()
                }

                val playedOnline = withContext(Dispatchers.IO) {
                    playNaturalVietnameseVoice(appCtx, speechText, rate)
                }
                if (!playedOnline) {
                    speakViaSystemTts(appCtx, speechText, rate)
                }
            }
        }

        /**
         * Đọc nguyên văn thông báo ngân hàng (dùng khi thông báo ngân hàng không chứa con số tiền cụ thể
         * hoặc khi người dùng bấm nút nghe thử một thông báo bất kỳ trên thanh trạng thái).
         */
        fun announceCustomNotificationText(
            context: Context,
            sourceApp: String,
            rawMessage: String
        ) {
            val appCtx = context.applicationContext
            lastAnnouncedTimestampMs = System.currentTimeMillis()
            val cleanMsg = rawMessage.replace('\n', ' ').take(160).trim()
            val speechText = if (sourceApp.isNotBlank()) {
                "Thông báo từ $sourceApp: $cleanMsg"
            } else {
                cleanMsg
            }
            val rate = getSpeechRate(appCtx)

            soundboxScope.launch {
                Toast.makeText(
                    appCtx,
                    "🔊 Đang đọc: \"$speechText\"",
                    Toast.LENGTH_SHORT
                ).show()

                ensureMediaVolumeAudible(appCtx)

                withContext(Dispatchers.Default) {
                    playSynthesizedTingTingBell()
                }

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
                val minAudible = (maxVol * 0.5f).toInt().coerceAtLeast(1)
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, minAudible, 0)
                }
            }
        }

        /**
         * Tạo âm thanh chuông "Ting-Ting!" chuẩn loa thanh toán cửa hàng bằng PCM 16-bit trên kênh USAGE_MEDIA.
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
         * Phát giọng đọc Tiếng Việt chuẩn tự nhiên qua MediaPlayer (USAGE_MEDIA) có lưu cache cục bộ.
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
        activeInstance = this
        _serviceConnectedFlow.value = true
        loadTransactions(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        _serviceConnectedFlow.value = true
        loadTransactions(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (activeInstance === this) {
            activeInstance = null
            _serviceConnectedFlow.value = false
        }
        runCatching {
            requestRebind(ComponentName(applicationContext, MoneySoundboxService::class.java))
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
            _serviceConnectedFlow.value = false
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        activeInstance = this
        _serviceConnectedFlow.value = true

        if (!isEnabled(applicationContext)) return

        val pkg = sbn.packageName.orEmpty()
        if (pkg == "android" || pkg == "com.android.systemui") return

        val (title, body) = extractNotificationTitleAndBody(sbn.notification)
        if (title.isBlank() && body.isBlank()) return

        val isSimulatedSelfBankNotif = pkg == applicationContext.packageName &&
            title.contains("Biến động số dư", ignoreCase = true)

        // Chặn đọc lặp thông báo của chính dịch vụ hẹn giờ / tải video
        if (pkg == applicationContext.packageName && !isSimulatedSelfBankNotif) {
            return
        }

        val knownBankName = KNOWN_BANK_PACKAGES[pkg]
        val allowAllApps = isAllAppsMode(applicationContext)
        if (!isSimulatedSelfBankNotif && knownBankName == null && !allowAllApps) return

        val amount = parseIncomingMoneyAmount(title, body) ?: return
        val bankLabel = knownBankName ?: detectBankNameFromContent("$title $body")

        // Chống đọc lặp khi 1 thông báo cập nhật liên tiếp trong 3.5 giây (trừ thông báo bắn thử)
        val dedupeKey = "$pkg:$amount:${body.take(30)}"
        val now = System.currentTimeMillis()
        if (!isSimulatedSelfBankNotif && dedupeKey == lastDedupeKey && (now - lastDedupeTimeMs) < 3500L) {
            return
        }
        lastDedupeKey = dedupeKey
        lastDedupeTimeMs = now

        announceMoneyReceived(
            context = applicationContext,
            amount = amount,
            sourceApp = bankLabel,
            rawMessage = "$title: $body".trim(':', ' '),
            saveToHistory = true
        )
    }
}
