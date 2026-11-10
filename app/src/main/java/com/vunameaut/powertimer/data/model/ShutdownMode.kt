package com.vunameaut.powertimer.data.model

/**
 * Các phương thức tắt màn hình & hành động khi hết giờ.
 * Ưu tiên các phương thức KHÔNG dùng Trợ năng (Accessibility) để tương thích 100% với App Ngân hàng & VNeID.
 */
enum class ShutdownMode(
    val title: String,
    val description: String,
    val badge: String,
    val requiresAccessibility: Boolean = false,
    val requiresDeviceAdmin: Boolean = false,
    val requiresWriteSettings: Boolean = false
) {
    /**
     * Khuyên dùng số 1:
     * KHÔNG dùng Trợ năng (100% không bị App Ngân hàng / VNeID chặn).
     * Hạ thời gian chờ màn hình xuống mức tối thiểu + bật màn hình đen OLED 0% độ sáng để máy tự khóa tự nhiên,
     * giữ nguyên mở khóa bằng Vân tay / Khuôn mặt (Biometrics), sau đó tự trả lại thời gian sáng màn hình cũ.
     */
    BANK_SAFE_SLEEP(
        title = "Tắt màn hình Chuẩn Ngân Hàng",
        description = "Không dùng Trợ năng (dùng mọi App Ngân hàng & VNeID bình thường). Tự làm tối & tắt màn hình tự nhiên, giữ nguyên Vân tay / Khuôn mặt.",
        badge = "Khuyên dùng • Giữ Vân Tay",
        requiresWriteSettings = true
    ),

    /**
     * Khóa màn hình tức thì qua Device Administrator (Không dùng Trợ năng -> Không bị Ngân hàng chặn)
     */
    DEVICE_ADMIN(
        title = "Khóa màn hình Tức Thì",
        description = "Khóa màn hình ngay lập tức (0.1s) qua Quản trị viên thiết bị, KHÔNG dùng Trợ năng nên dùng Ngân hàng bình thường (mở lại có thể cần PIN).",
        badge = "Tắt ngay • Chuẩn Ngân Hàng",
        requiresDeviceAdmin = true
    ),

    /**
     * Chỉ tắt nhạc / media, để màn hình tự tắt theo cài đặt máy
     */
    MEDIA_ONLY(
        title = "Chỉ dừng Nhạc / Media",
        description = "Dừng toàn bộ YouTube, TikTok, Spotify, Podcast... Màn hình sẽ tự động tắt theo thời gian chờ của máy. Không cần cấp quyền đặc biệt.",
        badge = "Không cần quyền"
    ),

    /**
     * Tắt màn hình bằng AccessibilityService + TỰ ĐỘNG TẮT Trợ năng (disableSelf) ngay sau khi khóa màn hình
     * để khi mở máy lên vẫn dùng được App Ngân hàng.
     */
    ACCESSIBILITY(
        title = "Trợ năng (Tự tắt sau khi khóa)",
        description = "Khóa màn hình bằng Trợ năng rồi TỰ ĐỘNG TẮT luôn Trợ năng ngay sau 1 giây để không cản trở App Ngân hàng khi bạn mở máy lại.",
        badge = "Tự ngắt Trợ năng",
        requiresAccessibility = true
    ),

    /**
     * Trở về trang chính (Home) rồi khóa màn hình và tự tắt Trợ năng
     */
    HOME_AND_LOCK(
        title = "Về Home rồi tắt màn hình",
        description = "Thoát ứng dụng đang xem về màn hình chính rồi khóa máy và tự động tắt luôn dịch vụ Trợ năng.",
        badge = "Home + Tự ngắt",
        requiresAccessibility = true
    )
}
