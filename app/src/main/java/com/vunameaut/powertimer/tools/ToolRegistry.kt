package com.vunameaut.powertimer.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vunameaut.powertimer.ui.screens.BillSplitterTool
import com.vunameaut.powertimer.ui.screens.PowerTimerTool
import com.vunameaut.powertimer.ui.screens.QrBarcodeStudioTool
import com.vunameaut.powertimer.ui.screens.QuickOcrClipboardTool
import com.vunameaut.powertimer.ui.screens.SoundboxTool
import com.vunameaut.powertimer.ui.screens.VideoDownloaderTool
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentPurple

/** Mô tả một công cụ nằm trong Tools Hub. */
data class ToolEntry(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val content: @Composable (onBack: () -> Unit) -> Unit
)

/**
 * ĐĂNG KÝ CÔNG CỤ MỚI Ở ĐÂY.
 *
 * Mỗi tool chỉ cần 1 composable nhận `onBack` và 1 dòng [ToolEntry] bên dưới;
 * màn hình chính, điều hướng và nút Back sẽ tự có.
 */
val ToolRegistry: List<ToolEntry> = listOf(
    ToolEntry(
        id = "video_downloader",
        title = "Tải Video Đa Nền Tảng",
        description = "Tải video không logo, chọn MP3/HD & tải trọn bộ ảnh từ TikTok, FB, IG, YouTube",
        icon = Icons.Rounded.CloudDownload,
        accent = AccentPink,
        content = { onBack -> VideoDownloaderTool(onExit = onBack) }
    ),
    ToolEntry(
        id = "power_timer",
        title = "PowerTimer",
        description = "Hẹn giờ tắt màn hình chuẩn Ngân hàng + Tương tác Giọt nước kiểu Spotify",
        icon = Icons.Rounded.Bedtime,
        accent = AccentCyan,
        content = { onBack -> PowerTimerTool(onExit = onBack) }
    ),
    ToolEntry(
        id = "qr_studio",
        title = "QR & VietQR Studio",
        description = "Quét mã QR/Barcode • Tạo VietQR chuyển khoản ngân hàng • Ghim QR cá nhân",
        icon = Icons.Rounded.QrCodeScanner,
        accent = AccentGreen,
        content = { onBack -> QrBarcodeStudioTool(onExit = onBack) }
    ),
    ToolEntry(
        id = "quick_ocr",
        title = "Quick OCR & Clipboard",
        description = "Quét chữ từ ảnh, tự lọc STK/SĐT/Mã vận đơn & Quản lý lịch sử bộ nhớ tạm",
        icon = Icons.Rounded.DocumentScanner,
        accent = AccentAmber,
        content = { onBack -> QuickOcrClipboardTool(onExit = onBack) }
    ),
    ToolEntry(
        id = "bill_splitter",
        title = "Chia Tiền Nhóm + VietQR",
        description = "Quét bill ăn uống bằng OCR, chia đều hoặc theo món & xuất ảnh tổng hợp kèm VietQR",
        icon = Icons.Rounded.ReceiptLong,
        accent = AccentPurple,
        content = { onBack -> BillSplitterTool(onExit = onBack) }
    ),
    ToolEntry(
        id = "money_soundbox",
        title = "Loa Báo Nhận Tiền",
        description = "Mini Soundbox đọc to số tiền vừa nhận từ thông báo Ngân hàng & MoMo",
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        accent = AccentGreen,
        content = { onBack -> SoundboxTool(onExit = onBack) }
    )
)
