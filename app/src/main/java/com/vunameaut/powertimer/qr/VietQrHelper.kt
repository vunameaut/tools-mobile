package com.vunameaut.powertimer.qr

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.Normalizer
import java.util.EnumMap
import java.util.Locale

data class BankInfo(
    val shortName: String,
    val fullName: String,
    val bin: String,
    val brandColorHex: Long
)

data class VietQrProfile(
    val bankBin: String = "970422",
    val bankShortName: String = "MBBank",
    val accountNumber: String = "",
    val accountName: String = "",
    val defaultAmount: String = "",
    val defaultMemo: String = "",
    val pinToHome: Boolean = true
) {
    val isValid: Boolean
        get() = bankBin.isNotBlank() && accountNumber.trim().length >= 4
}

data class QrHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val type: String, // "SCAN", "VIETQR", "TEXT_LINK", "WIFI", "BARCODE"
    val title: String,
    val subtitle: String,
    val rawContent: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PinnedQrItem(
    val id: Long = System.currentTimeMillis(),
    val type: String, // "VIETQR", "LINK", "WIFI", "BARCODE"
    val title: String,
    val subtitle: String = "",
    val rawContent: String,
    val bankBin: String = "",
    val accountNumber: String = "",
    val accountName: String = "",
    val amount: String = "",
    val memo: String = "",
    val fgColorHex: Long = 0xFF000000L,
    val timestamp: Long = System.currentTimeMillis()
)

object VietQrHelper {

    private const val PREF_NAME = "qr_studio_prefs"
    private const val KEY_MY_VIETQR = "my_vietqr_profile"
    private const val KEY_QR_HISTORY = "qr_history_items"
    private const val KEY_PINNED_QRS = "pinned_qr_items_v1"

    val SUPPORTED_BANKS: List<BankInfo> = listOf(
        BankInfo("MBBank", "MB Bank (Quân Đội)", "970422", 0xFF1E40AF),
        BankInfo("Vietcombank", "Vietcombank (VCB)", "970436", 0xFF15803D),
        BankInfo("Techcombank", "Techcombank (TCB)", "970407", 0xFFDC2626),
        BankInfo("BIDV", "BIDV", "970418", 0xFF0F766E),
        BankInfo("VietinBank", "VietinBank (CTG)", "970415", 0xFF0284C7),
        BankInfo("ACB", "ACB (Á Châu)", "970416", 0xFF1D4ED8),
        BankInfo("VPBank", "VPBank", "970432", 0xFF16A34A),
        BankInfo("TPBank", "TPBank (Tiên Phong)", "970423", 0xFF7E22CE),
        BankInfo("Agribank", "Agribank (NN&PTNT)", "970405", 0xFF991B1B),
        BankInfo("Sacombank", "Sacombank (STB)", "970403", 0xFF0369A1),
        BankInfo("VIB", "VIB (Quốc Tế)", "970441", 0xFFEA580C),
        BankInfo("HDBank", "HDBank", "970437", 0xFFB91C1C),
        BankInfo("SHB", "SHB (Sài Gòn - Hà Nội)", "970443", 0xFFD97706),
        BankInfo("MSB", "MSB (Hàng Hải)", "970426", 0xFFE11D48),
        BankInfo("OCB", "OCB (Phương Đông)", "970448", 0xFF059669),
        BankInfo("SeABank", "SeABank", "970440", 0xFFDC2626),
        BankInfo("LPBank", "LPBank (Lộc Phát)", "970449", 0xFFCA8A04),
        BankInfo("Eximbank", "Eximbank", "970431", 0xFF0284C7),
        BankInfo("BVBank", "BVBank (Bản Việt)", "970454", 0xFF2563EB),
        BankInfo("Cake", "CAKE by VPBank", "546034", 0xFFDB2777),
        BankInfo("Timo", "Timo Digital Bank", "963388", 0xFF6D28D9)
    )

    fun findBankByBin(bin: String): BankInfo {
        return SUPPORTED_BANKS.firstOrNull { it.bin == bin } ?: SUPPORTED_BANKS.first()
    }

    /**
     * Xây dựng chuỗi EMVCo VietQR chuẩn NAPAS 247 (Hỗ trợ quét offline bằng mọi App Ngân hàng & Ví điện tử).
     */
    fun buildVietQrPayload(
        bankBin: String,
        accountNumber: String,
        amount: Long? = null,
        memo: String? = null
    ): String {
        val cleanBin = bankBin.trim()
        val cleanAcc = accountNumber.trim().replace(" ", "")
        val cleanAmount = amount?.takeIf { it > 0L }?.toString().orEmpty()
        val cleanMemo = stripVietnameseAccents(memo.orEmpty().trim())
            .replace(Regex("[^A-Za-z0-9 .-]+"), " ")
            .trim()
            .take(50)

        fun tlv(id: String, value: String): String {
            val len = value.toByteArray(Charsets.UTF_8).size.toString().padStart(2, '0')
            return "$id$len$value"
        }

        // Tag 00: Payload Format Indicator = "01"
        val tag00 = tlv("00", "01")
        // Tag 01: Point of Initiation Method ("11" = Static QR, "12" = Dynamic QR with amount/memo)
        val isDynamic = cleanAmount.isNotEmpty() || cleanMemo.isNotEmpty()
        val tag01 = tlv("01", if (isDynamic) "12" else "11")

        // Tag 38: Consumer Account Information (NAPAS VietQR GUID = A000000727)
        val beneficiarySub = tlv("00", cleanBin) + tlv("01", cleanAcc)
        val tag38Value = tlv("00", "A000000727") +
            tlv("01", beneficiarySub) +
            tlv("02", "QRIBFTTA")
        val tag38 = tlv("38", tag38Value)

        // Tag 53: Transaction Currency (704 = VND)
        val tag53 = tlv("53", "704")
        // Tag 54: Transaction Amount (optional)
        val tag54 = if (cleanAmount.isNotEmpty()) tlv("54", cleanAmount) else ""
        // Tag 58: Country Code = "VN"
        val tag58 = tlv("58", "VN")
        // Tag 62: Additional Data Field Template (Subtag 08 = Purpose of Transaction)
        val tag62 = if (cleanMemo.isNotEmpty()) tlv("62", tlv("08", cleanMemo)) else ""

        // Tag 63: CRC (ID "63", Length "04")
        val payloadWithoutCrc = tag00 + tag01 + tag38 + tag53 + tag54 + tag58 + tag62 + "6304"
        val crc = computeCrc16Ccitt(payloadWithoutCrc)
        return payloadWithoutCrc + crc
    }

    /**
     * Tính CRC16-CCITT (Polynomial 0x1021, Initial Value 0xFFFF) theo chuẩn EMVCo.
     */
    fun computeCrc16Ccitt(input: String): String {
        var crc = 0xFFFF
        val bytes = input.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            for (i in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return String.format(Locale.US, "%04X", crc)
    }

    fun stripVietnameseAccents(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return normalized
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }

    /**
     * Tạo Bitmap mã QR sắc nét từ bất kỳ nội dung nào.
     */
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 720,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return runCatching {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 2)
            }
            val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) foregroundColor else backgroundColor
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    /**
     * Tạo Bitmap mã vạch (Barcode Code 128).
     */
    fun generateBarcodeBitmap(
        content: String,
        widthPx: Int = 900,
        heightPx: Int = 320
    ): Bitmap? {
        if (content.isBlank()) return null
        return runCatching {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 10)
            }
            val matrix = MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, widthPx, heightPx, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    /**
     * Giải mã QR / Barcode từ Bitmap (thử nhiều chế độ Binarizer & kích thước để đạt độ chính xác cao nhất).
     */
    fun decodeFromBitmap(source: Bitmap): Pair<String, String>? {
        val candidates = listOf(
            source,
            if (source.width > 1280 || source.height > 1280) {
                val ratio = 1024f / maxOf(source.width, source.height)
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * ratio).toInt().coerceAtLeast(100),
                    (source.height * ratio).toInt().coerceAtLeast(100),
                    true
                )
            } else null
        ).filterNotNull()

        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.TRY_HARDER, true)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
            put(
                DecodeHintType.POSSIBLE_FORMATS,
                listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.PDF_417,
                    BarcodeFormat.AZTEC
                )
            )
        }
        val reader = MultiFormatReader().apply { setHints(hints) }

        for (bmp in candidates) {
            val width = bmp.width
            val height = bmp.height
            val pixels = IntArray(width * height)
            bmp.getPixels(pixels, 0, width, 0, 0, width, height)
            val luminance = RGBLuminanceSource(width, height, pixels)

            // 1. HybridBinarizer
            runCatching {
                val res = reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance)))
                return res.text to res.barcodeFormat.name
            }
            reader.reset()

            // 2. GlobalHistogramBinarizer
            runCatching {
                val res = reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(luminance)))
                return res.text to res.barcodeFormat.name
            }
            reader.reset()

            // 3. Inverted (QR trắng trên nền tối)
            runCatching {
                val inverted = luminance.invert()
                val res = reader.decodeWithState(BinaryBitmap(HybridBinarizer(inverted)))
                return res.text to res.barcodeFormat.name
            }
            reader.reset()
        }
        return null
    }

    /**
     * Vẽ thẻ VietQR sang trọng thành Bitmap để lưu hoặc chia sẻ.
     */
    fun renderVietQrCardBitmap(
        bank: BankInfo,
        accountNumber: String,
        accountName: String,
        amount: Long?,
        memo: String
    ): Bitmap? {
        val payload = buildVietQrPayload(bank.bin, accountNumber, amount, memo)
        val qrBitmap = generateQrBitmap(payload, sizePx = 620) ?: return null

        val width = 920
        val height = 1260
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF0B1120.toInt(), 0xFF172554.toInt(), 0xFF0F172A.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Khung thẻ trắng giữa
        val cardRect = RectF(60f, 70f, width - 60f, height - 70f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        // Header màu thương hiệu ngân hàng
        val headerRect = RectF(60f, 70f, width - 60f, 235f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bank.brandColorHex.toInt()
        }
        canvas.drawRoundRect(headerRect, 48f, 48f, headerPaint)
        canvas.drawRect(60f, 160f, width - 60f, 235f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(bank.fullName.uppercase(Locale.getDefault()), width / 2f, 150f, titlePaint)

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("VIETQR • NAPAS 247 CHUYỂN KHOẢN NHANH", width / 2f, 198f, subHeaderPaint)

        // Vẽ mã QR chính giữa
        val qrLeft = (width - qrBitmap.width) / 2f
        val qrTop = 265f
        val borderRect = RectF(qrLeft - 16f, qrTop - 16f, qrLeft + qrBitmap.width + 16f, qrTop + qrBitmap.height + 16f)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(borderRect, 28f, 28f, borderPaint)
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

        var y = qrTop + qrBitmap.height + 70f

        if (accountName.isNotBlank()) {
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0F172A.toInt()
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                stripVietnameseAccents(accountName).uppercase(Locale.getDefault()),
                width / 2f,
                y,
                namePaint
            )
            y += 52f
        }

        val accPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bank.brandColorHex.toInt()
            textSize = 44f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("STK: $accountNumber", width / 2f, y, accPaint)
        y += 52f

        if (amount != null && amount > 0L) {
            val formattedVnd = NumberFormat.getNumberInstance(Locale("vi", "VN")).format(amount) + " đ"
            val amtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFDC2626.toInt()
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Số tiền: $formattedVnd", width / 2f, y, amtPaint)
            y += 48f
        }

        if (memo.isNotBlank()) {
            val memoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF475569.toInt()
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Nội dung: ${memo.take(40)}", width / 2f, y, memoPaint)
        }

        return bmp
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileNamePrefix: String = "ToolsHub_QR"): Uri? {
        return runCatching {
            val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ToolsHub")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Toast.makeText(context, "Đã lưu ảnh vào Thư viện (Pictures/ToolsHub)!", Toast.LENGTH_SHORT).show()
            uri
        }.onFailure {
            Toast.makeText(context, "Lỗi lưu ảnh: ${it.message}", Toast.LENGTH_SHORT).show()
        }.getOrNull()
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, title: String = "Chia sẻ mã QR") {
        runCatching {
            val cacheDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(cacheDir, "toolshub_share_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            Toast.makeText(context, "Không thể chia sẻ ảnh: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadMyVietQrProfile(context: Context): VietQrProfile {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MY_VIETQR, null) ?: return VietQrProfile()
        return runCatching {
            val obj = JSONObject(raw)
            val bin = obj.optString("bankBin", "970422")
            VietQrProfile(
                bankBin = bin,
                bankShortName = obj.optString("bankShortName", findBankByBin(bin).shortName),
                accountNumber = obj.optString("accountNumber", ""),
                accountName = obj.optString("accountName", ""),
                defaultAmount = obj.optString("defaultAmount", ""),
                defaultMemo = obj.optString("defaultMemo", ""),
                pinToHome = obj.optBoolean("pinToHome", true)
            )
        }.getOrDefault(VietQrProfile())
    }

    fun saveMyVietQrProfile(context: Context, profile: VietQrProfile) {
        val obj = JSONObject().apply {
            put("bankBin", profile.bankBin)
            put("bankShortName", profile.bankShortName)
            put("accountNumber", profile.accountNumber.trim())
            put("accountName", profile.accountName.trim())
            put("defaultAmount", profile.defaultAmount.trim())
            put("defaultMemo", profile.defaultMemo.trim())
            put("pinToHome", profile.pinToHome)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MY_VIETQR, obj.toString())
            .apply()
        runCatching {
            com.vunameaut.powertimer.widget.ToolsHubAppWidgetProvider.updateAllWidgets(context)
        }
    }

    fun loadHistory(context: Context): List<QrHistoryItem> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QR_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        QrHistoryItem(
                            id = o.optLong("id", i.toLong()),
                            type = o.optString("type", "SCAN"),
                            title = o.optString("title", ""),
                            subtitle = o.optString("subtitle", ""),
                            rawContent = o.optString("rawContent", ""),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addHistoryItem(context: Context, item: QrHistoryItem): List<QrHistoryItem> {
        val current = loadHistory(context).toMutableList()
        current.removeAll { it.rawContent == item.rawContent && it.type == item.type }
        current.add(0, item)
        val trimmed = current.take(40)
        saveHistory(context, trimmed)
        return trimmed
    }

    fun deleteHistoryItem(context: Context, id: Long): List<QrHistoryItem> {
        val updated = loadHistory(context).filterNot { it.id == id }
        saveHistory(context, updated)
        return updated
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_QR_HISTORY)
            .apply()
    }

    private fun saveHistory(context: Context, list: List<QrHistoryItem>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type)
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("rawContent", item.rawContent)
                    put("timestamp", item.timestamp)
                }
            )
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QR_HISTORY, arr.toString())
            .apply()
    }

    /**
     * Tải danh sách các mã QR đã ghim. Nếu chưa có mã nào nhưng người dùng đã lưu VietQR cá nhân
     * với `pinToHome = true`, tự động đồng bộ mã VietQR đó vào danh sách ghim để mở lên thấy ngay.
     */
    fun loadPinnedQrs(context: Context): List<PinnedQrItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PINNED_QRS, null)
        if (raw == null) {
            val myViet = loadMyVietQrProfile(context)
            if (myViet.isValid && myViet.pinToHome) {
                val amt = myViet.defaultAmount.replace(Regex("[^0-9]"), "").toLongOrNull()
                val payload = buildVietQrPayload(myViet.bankBin, myViet.accountNumber, amt, myViet.defaultMemo)
                val autoItem = PinnedQrItem(
                    id = 1L,
                    type = "VIETQR",
                    title = "VietQR ${myViet.bankShortName} - ${myViet.accountNumber}",
                    subtitle = myViet.accountName.ifBlank { "Chuyển khoản nhanh NAPAS 247" },
                    rawContent = payload,
                    bankBin = myViet.bankBin,
                    accountNumber = myViet.accountNumber,
                    accountName = myViet.accountName,
                    amount = myViet.defaultAmount,
                    memo = myViet.defaultMemo
                )
                savePinnedQrs(context, listOf(autoItem))
                return listOf(autoItem)
            }
            return emptyList()
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PinnedQrItem(
                            id = o.optLong("id", i.toLong()),
                            type = o.optString("type", "LINK"),
                            title = o.optString("title", "Mã QR Đã Ghim"),
                            subtitle = o.optString("subtitle", ""),
                            rawContent = o.optString("rawContent", ""),
                            bankBin = o.optString("bankBin", ""),
                            accountNumber = o.optString("accountNumber", ""),
                            accountName = o.optString("accountName", ""),
                            amount = o.optString("amount", ""),
                            memo = o.optString("memo", ""),
                            fgColorHex = o.optLong("fgColorHex", 0xFF000000L),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun pinQrItem(context: Context, item: PinnedQrItem): List<PinnedQrItem> {
        val current = loadPinnedQrs(context).toMutableList()
        current.removeAll { it.rawContent == item.rawContent }
        current.add(0, item)
        val trimmed = current.take(15)
        savePinnedQrs(context, trimmed)
        return trimmed
    }

    fun unpinQrById(context: Context, id: Long): List<PinnedQrItem> {
        val updated = loadPinnedQrs(context).filterNot { it.id == id }
        savePinnedQrs(context, updated)
        return updated
    }

    fun unpinQrByContent(context: Context, rawContent: String): List<PinnedQrItem> {
        val updated = loadPinnedQrs(context).filterNot { it.rawContent == rawContent }
        savePinnedQrs(context, updated)
        return updated
    }

    fun setPrimaryPinnedQr(context: Context, id: Long): List<PinnedQrItem> {
        val current = loadPinnedQrs(context).toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx > 0) {
            val picked = current.removeAt(idx)
            current.add(0, picked)
            savePinnedQrs(context, current)
        }
        return current
    }

    private fun savePinnedQrs(context: Context, list: List<PinnedQrItem>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type)
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("rawContent", item.rawContent)
                    put("bankBin", item.bankBin)
                    put("accountNumber", item.accountNumber)
                    put("accountName", item.accountName)
                    put("amount", item.amount)
                    put("memo", item.memo)
                    put("fgColorHex", item.fgColorHex)
                    put("timestamp", item.timestamp)
                }
            )
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PINNED_QRS, arr.toString())
            .apply()
    }
}

