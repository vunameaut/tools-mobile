package com.vunameaut.powertimer.data.model

/**
 * Các phương thức thực hiện tắt máy / chế độ ngủ
 */
enum class ShutdownMode(
    val title: String,
    val description: String,
    val badge: String,
    val requiresRoot: Boolean = false,
    val requiresAccessibility: Boolean = false,
    val requiresShizuku: Boolean = false
) {
    /**
     * Dành cho máy thông thường chưa Root (Android 11, 12, 13, 14, 15+):
     * Kích hoạt Global Action mở Menu Nguồn và tự động nhấp vào nút "Tắt nguồn"
     */
    ACCESSIBILITY(
        title = "Tự động tắt nguồn (Trợ năng)",
        description = "Mở menu nguồn hệ thống và tự động bấm nút tắt máy. Hoạt động trên mọi phiên bản Android đời cao không cần Root.",
        badge = "Khuyên dùng (No Root)",
        requiresAccessibility = true
    ),

    /**
     * Dành cho Android 11+ thông qua Shizuku (Wireless Debugging):
     * Thực thi lệnh shutdown hệ thống mượt mà như lệnh adb shell.
     */
    SHIZUKU(
        title = "Tắt máy qua Shizuku (ADB)",
        description = "Chạy lệnh 'svc power shutdown' qua Shizuku. Tắt nguồn ngay lập tức, cực kỳ mượt mà cho Android 11-15+.",
        badge = "Shizuku ADB",
        requiresShizuku = true
    ),

    /**
     * Dành cho máy đã Root (Magisk / KernelSU / APatch)
     */
    ROOT(
        title = "Tắt máy trực tiếp (Root)",
        description = "Gọi lệnh 'reboot -p' bằng quyền Superuser (su). Yêu cầu máy đã Root.",
        badge = "Cần Root",
        requiresRoot = true
    ),

    /**
     * Chế độ Ngủ & Khóa máy (Sleep Timer):
     * Khóa màn hình, ngắt toàn bộ âm thanh/nhạc đang phát, kích hoạt DND.
     */
    SLEEP_LOCK(
        title = "Chế độ Ngủ & Khóa màn hình",
        description = "Dừng phát toàn bộ nhạc/video (Spotify, YouTube, v.v.), khóa màn hình và bật Không làm phiền. Thích hợp nghe nhạc trước khi ngủ.",
        badge = "An toàn 100%"
    )
}
