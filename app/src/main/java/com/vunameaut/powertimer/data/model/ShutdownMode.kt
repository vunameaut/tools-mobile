package com.vunameaut.powertimer.data.model

/**
 * Các phương thức tắt màn hình & hành động khi hết giờ
 */
enum class ShutdownMode(
    val title: String,
    val description: String,
    val badge: String,
    val requiresAccessibility: Boolean = false,
    val requiresDeviceAdmin: Boolean = false
) {
    /**
     * Khuyên dùng cho Android 9, 10, 11, 12, 13, 14, 15+:
     * Tắt màn hình bằng AccessibilityService (GLOBAL_ACTION_LOCK_SCREEN).
     * Điểm đặc biệt: KHÔNG làm mất vân tay/nhận diện khuôn mặt!
     */
    ACCESSIBILITY(
        title = "Tắt màn hình (Trợ năng)",
        description = "Khóa màn hình tự nhiên như bấm phím nguồn. Vẫn mở khóa bình thường bằng Vân tay / Khuôn mặt (Biometrics).",
        badge = "Khuyên dùng (Giữ vân tay)",
        requiresAccessibility = true
    ),

    /**
     * Trở về trang chính (Home) rồi khóa màn hình
     */
    HOME_AND_LOCK(
        title = "Về Home rồi tắt màn hình",
        description = "Thoát ứng dụng xem phim/nghe nhạc về màn hình chính trước khi tắt để tiết kiệm RAM và bảo mật.",
        badge = "Trợ năng + Home",
        requiresAccessibility = true
    ),

    /**
     * Khóa màn hình qua Device Administrator (Phương thức truyền thống)
     */
    DEVICE_ADMIN(
        title = "Khóa màn hình (Quản trị viên)",
        description = "Sử dụng quyền Quản trị viên thiết bị để khóa màn hình lập tức (lần mở sau có thể cần nhập mã PIN/mật khẩu).",
        badge = "Khóa bảo mật",
        requiresDeviceAdmin = true
    ),

    /**
     * Chỉ tắt nhạc / media, để màn hình tự tắt theo cài đặt máy
     */
    MEDIA_ONLY(
        title = "Chỉ dừng nhạc / Media",
        description = "Dừng toàn bộ Spotify, YouTube, podcast v.v. Màn hình sẽ tự động tắt theo thời gian chờ (Screen timeout) của máy.",
        badge = "Không cần quyền đặc biệt"
    )
}
