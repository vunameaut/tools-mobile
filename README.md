# ⚡ PowerTimer — Hẹn Giờ Tắt Máy & Chế Độ Ngủ Thông Minh Cho Android Đời Cao

[![Android CI Build](https://github.com/vunameaut/android-power-timer/actions/workflows/android.yml/badge.svg)](https://github.com/vunameaut/android-power-timer/actions/workflows/android.yml)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0_--_15+-brightgreen.svg?logo=android)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?logo=kotlin)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**PowerTimer** là ứng dụng di động Android hiện đại được thiết kế đặc thù để giải quyết triệt để bài toán **hẹn giờ tắt máy, khóa màn hình và dừng toàn bộ âm thanh** trên các phiên bản **Android đời cao (Android 11, 12, 13, 14 và 15+)**.

---

## 🎯 Vấn Đề Kỹ Thuật Trên Android Đời Cao & Giải Pháp

Từ Android 8.0 trở lên và đặc biệt siết chặt từ Android 11-15:
1. **Bảo mật hệ điều hành:** Android không cấp quyền `android.permission.SHUTDOWN` cho ứng dụng thông thường của bên thứ ba nếu không có chứng chỉ hệ thống (System Signature) hoặc Root.
2. **Tiến trình chạy ngầm:** Hệ điều hành và các tùy biến OEM (Samsung OneUI, Xiaomi MIUI/HyperOS, Oppo ColorOS, v.v.) thường xuyên "kill" các ứng dụng đếm ngược khi màn hình tắt nếu không cấu hình Foreground Service và ngoại lệ pin đúng cách.

### 💡 4 Phương Thức Hoạt Động Của PowerTimer:

| Chế độ | Nguyên lý hoạt động | Yêu cầu | Độ tương thích |
|---|---|---|---|
| **1. Trợ năng tự động (Khuyên dùng)** | Sử dụng `AccessibilityService.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)` để kích hoạt menu nguồn và tự động click nút *Tắt nguồn* kèm xác nhận. | Cấp quyền Trợ năng | **Android 8.0 – 15+ (Không cần Root)** |
| **2. Shizuku / Wireless ADB** | Gọi `svc power shutdown` qua Shizuku Binder. Tắt máy sạch và mượt mà bằng quyền ADB. | Shizuku (Ghép nối qua Wifi) | **Android 11 – 15+ (Không cần Root)** |
| **3. Superuser (Root)** | Thực thi lệnh `su -c reboot -p` hoặc `svc power shutdown`. | Máy đã Root (Magisk/KernelSU) | **Mọi phiên bản Android đã Root** |
| **4. Chế độ Ngủ (Sleep Timer)** | Dừng phát nhạc/video (Spotify, YouTube), giảm dần âm lượng (Fade-out), bật Không làm phiền (DND) và khóa màn hình. | Quản trị viên hoặc Trợ năng | **100% thiết bị Android** |

---

## ✨ Tính Năng Nổi Bật

- ⏱ **Mặt đồng hồ đếm ngược Neon:** Hiệu ứng Circular Progress Bar mượt mà với Jetpack Compose.
- ⚡ **Mốc thời gian nhanh (Quick Presets):** 15 phút, 30 phút, 45 phút, 60 phút, 90 phút, 120 phút hoặc bộ chọn giờ phút tùy chỉnh.
- 🔔 **Dịch vụ thông báo ghim (Foreground Service):** Luôn hiển thị thời gian thực ở thanh trạng thái với nút hành động nhanh:
  - `+10 phút`: Gia hạn thêm khi chưa ngủ được.
  - `Dừng lại`: Hủy hẹn giờ lập tức.
- 📳 **Rung cảnh báo thông minh:** Rung nhắc nhở nhẹ trước 30 giây khi sắp đến giờ tắt.
- 🎵 **Làm dịu âm thanh (Fade-out):** Tự động hạ âm lượng từ từ trước khi tắt để không làm giật mình khi đang ngủ say.
- 🔋 **Chuẩn hóa Android 14/15:**
  - Hỗ trợ Foreground Service Type `specialUse` theo chính sách mới của Google Play.
  - Tích hợp `USE_EXACT_ALARM` & `SCHEDULE_EXACT_ALARM` kết hợp `WakeLock` để đánh thức CPU chính xác đến từng giây.
  - Trình kiểm tra trạng thái quyền hạn trực quan (Permission Checklist).

---

## 🛠 Kiến Trúc & Công Nghệ

```
PowerTimer/
├── app/src/main/
│   ├── AndroidManifest.xml              # Khai báo FGS specialUse, Exact Alarm, Accessibility
│   ├── java/com/vunameaut/powertimer/
│   │   ├── MainActivity.kt              # Entry point Compose, runtime permission dispatcher
│   │   ├── PowerTimerApplication.kt     # App context
│   │   ├── data/model/
│   │   │   ├── ShutdownMode.kt          # 4 chế độ tắt máy
│   │   │   └── TimerState.kt            # Trạng thái đếm ngược reactive
│   │   ├── manager/
│   │   │   ├── AudioMediaManager.kt     # Audio focus, Media pause, Fade-out, DND
│   │   │   ├── PermissionManager.kt     # Quản lý & kiểm tra quyền hệ thống
│   │   │   └── ShutdownManager.kt       # Điều phối các chiến lược tắt máy
│   │   ├── receiver/
│   │   │   ├── DeviceAdminSampleReceiver.kt # Khóa màn hình tức thì
│   │   │   └── TimerAlarmReceiver.kt    # AlarmManager wakeup backup receiver
│   │   ├── service/
│   │   │   ├── PowerAccessibilityService.kt # Tự động hóa menu tắt nguồn
│   │   │   └── TimerForegroundService.kt    # Foreground service đếm ngược bền bỉ
│   │   └── ui/
│   │       ├── components/              # CircularCountdownTimer, ModeCard, PermissionItem
│   │       ├── screens/                 # HomeScreen, SettingsScreen
│   │       ├── theme/                   # Material 3 Dynamic Dark/Light Theme
│   │       └── viewmodel/               # MVVM StateFlow
│   └── res/                             # Vector drawables, Strings đa ngôn ngữ, XML configs
└── .github/workflows/android.yml        # CI/CD tự động build debug APK
```

---

## 🚀 Hướng Dẫn Cài Đặt & Cấp Quyền

### 1. Đối với máy không Root (Dùng Trợ năng - Khuyên dùng)
1. Cài đặt ứng dụng PowerTimer.
2. Mở ứng dụng, tại mục cảnh báo hoặc vào biểu tượng **Cài đặt** (bánh răng).
3. Nhấn **Cấp quyền** tại mục **Dịch vụ Trợ năng (Accessibility)** ➔ Bật công tắc cho **PowerTimer**.
4. Nhấn **Tắt tối ưu hóa pin** để tránh bị Android tắt ngầm khi khóa màn hình.

### 2. Đối với máy dùng Shizuku (Android 11 - 15+)
1. Cài đặt và kích hoạt ứng dụng [Shizuku](https://shizuku.rikka.app/) qua tính năng *Gỡ lỗi không dây (Wireless Debugging)* trên điện thoại.
2. Mở PowerTimer ➔ Chọn chế độ **Tắt máy qua Shizuku (ADB)** ➔ Xác nhận cấp quyền khi hộp thoại Shizuku hiện lên.

### 3. Đối với máy đã Root
- Chỉ cần chọn chế độ **Tắt máy trực tiếp (Root)**, ứng dụng sẽ tự động yêu cầu quyền Superuser từ Magisk / KernelSU khi hết thời gian đếm ngược.

---

## 💻 Hướng Dẫn Biên Dịch (Build Source)

### Yêu cầu:
- **JDK:** 17 trở lên
- **Android Studio:** Ladybug (2024.2.1) hoặc mới hơn
- **Android SDK:** Compile SDK 35 (Android 15), Min SDK 26 (Android 8.0)

### Lệnh Build APK qua dòng lệnh:
```bash
# Clone repository
git clone https://github.com/vunameaut/android-power-timer.git
cd android-power-timer

# Biên dịch Debug APK
./gradlew assembleDebug

# File APK tạo ra tại:
# app/build/outputs/apk/debug/app-debug.apk
```

Hoặc tải trực tiếp file APK được build tự động tại tab **Actions** trên GitHub!

---

## 📄 License

Dự án được phân phối dưới giấy phép [MIT License](LICENSE).
Được phát triển bởi [Vũ Hoài Nam](https://github.com/vunameaut).
