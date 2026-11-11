package com.vunameaut.powertimer.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

enum class SmartEntityType(val label: String, val badgeEmoji: String) {
    BANK_ACCOUNT("Số tài khoản", "🏦"),
    PHONE("Số điện thoại", "📞"),
    TRACKING_CODE("Mã vận đơn", "📦"),
    URL("Đường dẫn Link", "🔗"),
    AMOUNT("Số tiền", "💰")
}

data class SmartExtractedEntity(
    val type: SmartEntityType,
    val value: String,
    val rawContext: String = ""
)

data class ParsedReceiptItem(
    val name: String,
    val price: Long
)

data class ClipboardHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val category: String = "Văn bản",
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object OcrAndClipboardHelper {

    private const val PREF_NAME = "quick_ocr_clipboard_prefs"
    private const val KEY_CLIPBOARD_HISTORY = "clipboard_history_v1"

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            if (raw.config != Bitmap.Config.ARGB_8888) {
                raw.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                raw
            }
        }.getOrNull()
    }

    /**
     * Nhận diện văn bản từ ảnh bằng Google ML Kit Text Recognition.
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        if (cont.isActive) {
                            cont.resume(Result.success(visionText.text.trim()))
                        }
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) {
                            cont.resume(Result.failure(e))
                        }
                    }
            } catch (t: Throwable) {
                if (cont.isActive) {
                    cont.resume(Result.failure(t))
                }
            }
        }

    /**
     * Bóc tách thông minh Số tài khoản, Số điện thoại, Mã vận đơn, Link URL, Số tiền từ văn bản OCR.
     */
    fun extractSmartEntities(text: String): List<SmartExtractedEntity> {
        if (text.isBlank()) return emptyList()
        val results = mutableListOf<SmartExtractedEntity>()
        val seenValues = mutableSetOf<String>()

        fun addUnique(type: SmartEntityType, value: String) {
            val clean = value.trim()
            if (clean.isNotEmpty() && seenValues.add("${type.name}:$clean")) {
                results.add(SmartExtractedEntity(type = type, value = clean))
            }
        }

        // 1. URLs
        val urlRegex = Regex("""https?://[^\s"'<>]+|www\.[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        urlRegex.findAll(text).forEach { match ->
            addUnique(SmartEntityType.URL, match.value.trimEnd('.', ',', ')', ']'))
        }

        // 2. Mã vận đơn (SPX, GHN, GHTK, ViettelPost, J&T...)
        val trackingRegex = Regex("""\b(?:SPXVN[A-Z0-9]{7,15}|GHN[A-Z0-9]{6,14}|GHTK[A-Z0-9]{6,14}|VN[0-9]{9,14}[A-Z]{0,2}|JT[0-9]{10,14}|84[0-9]{10,13})\b""", RegexOption.IGNORE_CASE)
        trackingRegex.findAll(text).forEach { match ->
            addUnique(SmartEntityType.TRACKING_CODE, match.value.uppercase())
        }

        // 3. Số điện thoại Việt Nam
        val phoneRegex = Regex("""(?:\+84|0)(?:3[2-9]|5[2689]|7[06-9]|8[1-9]|9[0-9])(?:[\s.-]?\d){7}\b""")
        val matchedPhones = mutableSetOf<String>()
        phoneRegex.findAll(text).forEach { match ->
            val normalized = match.value.replace(Regex("[\\s.-]"), "")
            matchedPhones.add(normalized)
            addUnique(SmartEntityType.PHONE, normalized)
        }

        // 4. Số tiền (VD: 150.000, 2,500,000đ, 500.000 VND)
        val amountRegex = Regex("""\b\d{1,3}(?:[.,]\d{3})+(?:\s?(?:đ|d|vnđ|vnd|đồng))?\b""", RegexOption.IGNORE_CASE)
        amountRegex.findAll(text).forEach { match ->
            val digits = match.value.replace(Regex("[^0-9]"), "")
            val num = digits.toLongOrNull() ?: 0L
            if (num in 1_000L..5_000_000_000L) {
                addUnique(SmartEntityType.AMOUNT, digits)
            }
        }

        // 5. Số tài khoản ngân hàng (8 - 16 chữ số liền nhau, không trùng SĐT)
        val bankAccRegex = Regex("""\b\d{8,16}\b""")
        bankAccRegex.findAll(text).forEach { match ->
            val candidate = match.value
            if (candidate !in matchedPhones) {
                addUnique(SmartEntityType.BANK_ACCOUNT, candidate)
            }
        }

        return results
    }

    /**
     * Phân tích ảnh hóa đơn ăn uống (OCR) thành danh sách các món + giá tiền để đưa vào Chia tiền nhóm.
     */
    fun parseReceiptItemsFromOcr(ocrText: String): Pair<List<ParsedReceiptItem>, Long?> {
        if (ocrText.isBlank()) return emptyList<ParsedReceiptItem>() to null
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val items = mutableListOf<ParsedReceiptItem>()
        var detectedTotal: Long? = null

        val priceEndRegex = Regex("""^(.*?)\s+(\d{1,3}(?:[.,]\d{3})+|\d{2,4}[kK])(?:\s?(?:đ|d|vnd|vnđ))?$""")
        val totalKeywordRegex = Regex("""(tổng|tong|total|thành tiền|thanh toan|thanh toán|cộng|phải trả)""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val m = priceEndRegex.find(line) ?: continue
            val rawName = m.groupValues[1].trim('-', '.', ':', ' ', '*', '#')
            val rawPrice = m.groupValues[2].lowercase()
            val price = if (rawPrice.endsWith("k")) {
                (rawPrice.removeSuffix("k").toLongOrNull() ?: 0L) * 1000L
            } else {
                rawPrice.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
            }
            if (price !in 1_000L..500_000_000L) continue

            if (totalKeywordRegex.containsMatchIn(rawName)) {
                if (detectedTotal == null || price > detectedTotal) {
                    detectedTotal = price
                }
            } else if (rawName.length >= 2 && !rawName.all { it.isDigit() }) {
                items.add(ParsedReceiptItem(name = rawName.take(40), price = price))
            }
        }

        if (detectedTotal == null && items.isNotEmpty()) {
            detectedTotal = items.sumOf { it.price }
        }
        return items to detectedTotal
    }

    fun copyToClipboardAndSave(
        context: Context,
        text: String,
        category: String = "Văn bản",
        toastMsg: String = "Đã sao chép vào bộ nhớ tạm!"
    ): List<ClipboardHistoryItem> {
        val clean = text.trim()
        if (clean.isBlank()) return loadClipboardHistory(context)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("ToolsHub", clean))
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        return addClipboardHistory(context, clean, category)
    }

    fun readCurrentSystemClipboard(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        return item?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun detectCategory(text: String): String {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> "Link URL"
            trimmed.matches(Regex("""^(?:\+84|0)\d{9}$""")) -> "Số điện thoại"
            trimmed.matches(Regex("""^\d{8,16}$""")) -> "Số tài khoản / Mã số"
            trimmed.matches(Regex("""^(?:SPXVN|GHN|GHTK|VN|JT)[A-Z0-9]{6,16}$""", RegexOption.IGNORE_CASE)) -> "Mã vận đơn"
            else -> "Văn bản"
        }
    }

    fun loadClipboardHistory(context: Context): List<ClipboardHistoryItem> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CLIPBOARD_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ClipboardHistoryItem(
                            id = o.optLong("id", i.toLong()),
                            text = o.optString("text", ""),
                            category = o.optString("category", "Văn bản"),
                            isPinned = o.optBoolean("isPinned", false),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }.sortedWith(compareByDescending<ClipboardHistoryItem> { it.isPinned }.thenByDescending { it.timestamp })
        }.getOrDefault(emptyList())
    }

    fun addClipboardHistory(
        context: Context,
        text: String,
        category: String = detectCategory(text)
    ): List<ClipboardHistoryItem> {
        val clean = text.trim()
        if (clean.isBlank()) return loadClipboardHistory(context)
        val current = loadClipboardHistory(context).toMutableList()
        val existingPinned = current.firstOrNull { it.text == clean }?.isPinned ?: false
        current.removeAll { it.text == clean }
        current.add(
            0,
            ClipboardHistoryItem(
                id = System.currentTimeMillis(),
                text = clean,
                category = category,
                isPinned = existingPinned,
                timestamp = System.currentTimeMillis()
            )
        )
        val sorted = current
            .sortedWith(compareByDescending<ClipboardHistoryItem> { it.isPinned }.thenByDescending { it.timestamp })
            .take(60)
        saveClipboardHistory(context, sorted)
        return sorted
    }

    fun togglePinClipboardItem(context: Context, id: Long): List<ClipboardHistoryItem> {
        val updated = loadClipboardHistory(context).map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }.sortedWith(compareByDescending<ClipboardHistoryItem> { it.isPinned }.thenByDescending { it.timestamp })
        saveClipboardHistory(context, updated)
        return updated
    }

    fun deleteClipboardItem(context: Context, id: Long): List<ClipboardHistoryItem> {
        val updated = loadClipboardHistory(context).filterNot { it.id == id }
        saveClipboardHistory(context, updated)
        return updated
    }

    fun clearUnpinnedClipboardHistory(context: Context): List<ClipboardHistoryItem> {
        val pinnedOnly = loadClipboardHistory(context).filter { it.isPinned }
        saveClipboardHistory(context, pinnedOnly)
        return pinnedOnly
    }

    private fun saveClipboardHistory(context: Context, list: List<ClipboardHistoryItem>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("category", item.category)
                    put("isPinned", item.isPinned)
                    put("timestamp", item.timestamp)
                }
            )
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIPBOARD_HISTORY, arr.toString())
            .apply()
    }
}

