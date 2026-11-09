# 🌙 PowerTimer — App Hẹn Giờ Tắt Màn Hình & Chế Độ Ngủ Cho Android Đời Cao

[![Android CI Build](https://github.com/vunameaut/android-power-timer/actions/workflows/android.yml/badge.svg)](https://github.com/vunameaut/android-power-timer/actions/workflows/android.yml)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0_--_15+-brightgreen.svg?logo=android)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?logo=kotlin)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**PowerTimer** là ứng dụng di động Android cao cấp giúp **hẹn giờ tắt màn hình, khóa máy và dừng toàn bộ âm thanh/nhạc/video** tự động, chuyên dụng cho người dùng thường xuyên nghe nhạc (Spotify, SoundCloud), xem video (YouTube, Netflix, TikTok) hoặc podcast trước khi ngủ.

Đặc biệt, ứng dụng được tối ưu sâu cho **các phiên bản Android đời cao hiện nay (Android 11, 12, 13, 14 và 15+)** để khắc phục hoàn toàn tình trạng bị hệ điều hành tắt ngầm hoặc làm mất tính năng mở khóa vân tay!

---

## 💡 Điểm Vượt Trội Trên Android Đời Cao (Android 11 – 15+)

### 1. 🔒 Tắt màn hình KHÔNG làm mất Vân tay / Khuôn mặt (Biometrics)
- Các app truyền thống dùng quyền *Device Administrator* (`DevicePolicyManager.lockNow()`), dẫn đến việc khi mở máy lại **bắt buộc phải gõ mật khẩu/PIN**, không dùng được vân tay.
- **PowerTimer** sử dụng API Trợ năng hiện đại `AccessibilityService.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` (từ Android 9+). Màn hình tắt tự nhiên như bạn vừa bấm phím nguồn vật lý, **vẫn mở khóa bình thường bằng Vân tay / Khuôn mặt!**

### 2. 📳 Lắc máy để gia hạn (+10 phút)
- Bạn đang nghe nhạc nhưng vẫn chưa ngủ được? Không cần mở sáng màn hình làm chói mắt, chỉ cần **lắc nhẹ điện thoại**, cảm biến gia tốc kế (Accelerometer) sẽ tự động cộng thêm 10 phút và rung nhẹ xác nhận.

### 3. 🎵 Giảm âm lượng từ từ (Fade-out) & Ngắt Media
- 10 giây trước khi tắt màn hình, âm lượng sẽ giảm dần êm ái, sau đó giải phóng Audio Focus để dừng mọi ứng dụng phát nhạc (Spotify, YouTube, Podbean, Chrome...).

### 4. 🌙 Bật chế độ Không làm phiền (Do Not Disturb - DND)
- Tự động chuyển sang Không làm phiền khi tắt màn hình để các thông báo tin nhắn đêm khuya không làm phiền giấc ngủ của bạn.

### 5. 🔋 Chạy ngầm bền bỉ — Không bị hệ thống ngắt (Anti-Kill Service)
- Tích hợp chuẩn **Android 14+ Foreground Service** với type `specialUse`.
- Sử dụng `USE_EXACT_ALARM` và `WakeLock` đảm bảo đồng hồ đánh thức máy chính xác đến từng giây kể cả khi thiết bị vào trạng thái ngủ sâu (Doze Mode).

---

## 📱 4 Chế Độ Tắt Màn Hình Linh Hoạt

| Chế độ | Mô tả chi tiết | Quyền yêu cầu | Ưu điểm |
|---|---|---|---|
| **1. Tắt màn hình (Trợ năng)** | Tắt và khóa màn hình tự nhiên qua Accessibility. | Dịch vụ Trợ năng | **Giữ nguyên mở khóa Vân tay / Face Unlock** |
| **2. Về Home rồi tắt màn hình** | Thoát ứng dụng phim/nhạc về màn hình chính (Home) trước khi khóa máy. | Dịch vụ Trợ năng | Tránh lộ màn hình app khi mở lại máy |
| **3. Khóa bảo mật (Quản trị viên)** | Khóa màn hình lập tức qua Device Administrator. | Quản trị viên thiết bị | Phù hợp khóa máy an toàn |
| **4. Chỉ dừng nhạc / Media** | Chỉ dừng âm thanh phát, màn hình sẽ tự tắt theo thời gian chờ (timeout) của máy. | Không cần quyền | Nhẹ nhàng, đơn giản |

---

## ⚙️ Hướng Dẫn Cài Đặt Cho Các Hãng Máy Đời Mới

Để ứng dụng đếm ngược không bị hệ thống tự động tắt:

1. **Samsung (OneUI 5/6/7 - Android 13/14/15):**
   - Vào Cài đặt ➔ Ứng dụng ➔ PowerTimer ➔ Pin ➔ Chọn **Không hạn chế (Unrestricted)**.
   - Bật Dịch vụ Trợ năng cho **PowerTimer**.
2. **Xiaomi / Redmi / POCO (MIUI / HyperOS):**
   - Nhấn giữ icon PowerTimer ➔ Thông tin ứng dụng ➔ Bật **Tự khởi chạy (Autostart)**.
   - Tiết kiệm pin ➔ Chọn **Không hạn chế**.
3. **Google Pixel / Moto / Sony (Stock Android 12 - 15):**
   - Vào mục **Cài đặt & Quyền** trong ứng dụng và nhấn **Tắt tối ưu hóa pin**.

---

## 🛠 Kiến Trúc Kỹ Thuật

```
PowerTimer/
├── app/src/main/
│   ├── AndroidManifest.xml              # Khai báo FGS specialUse, Exact Alarm, Accessibility
│   ├── java/com/vunameaut/powertimer/
│   │   ├── MainActivity.kt              # Jetpack Compose UI & Runtime Permissions
│   │   ├── PowerTimerApplication.kt     # App Context
│   │   ├── data/model/
│   │   │   ├── ShutdownMode.kt          # 4 chế độ tắt màn hình
│   │   │   └── TimerState.kt            # Model reactive Flow
│   │   ├── manager/
│   │   │   ├── AudioMediaManager.kt     # Audio Focus, Media pause, Fade-out, DND
│   │   │   ├── PermissionManager.kt     # Kiểm tra quyền Android đời cao
│   │   │   ├── ShakeDetector.kt         # Cảm biến lắc máy gia hạn giờ
│   │   │   └── ShutdownManager.kt       # Điều phối tắt màn hình / về Home
│   │   ├── receiver/
│   │   │   ├── DeviceAdminSampleReceiver.kt # Khóa màn hình dự phòng
│   │   │   └── TimerAlarmReceiver.kt    # Exact Alarm Wakeup Receiver
│   │   ├── service/
│   │   │   ├── PowerAccessibilityService.kt # Tắt màn hình giữ vân tay (GLOBAL_ACTION_LOCK_SCREEN)
│   │   │   └── TimerForegroundService.kt    # Foreground service đếm ngược bền bỉ
│   │   └── ui/
│   │       ├── components/              # CircularCountdownTimer Neon, ModeCard, PermissionItem
│   │       ├── screens/                 # HomeScreen, SettingsScreen
│   │       ├── theme/                   # Material 3 Dynamic Dark/Light Theme
│   │       └── viewmodel/               # MVVM ViewModel
│   └── res/                             # Adaptive icons, Strings tiếng Việt, XML configs
└── .github/workflows/android.yml        # CI/CD tự động build debug APK
```

---

## 💻 Biên Dịch Dự Án (Build APK)

```bash
# Clone repository
git clone https://github.com/vunameaut/android-power-timer.git
cd android-power-timer

# Biên dịch Debug APK
./gradlew assembleDebug

# File APK được xuất tại:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Bản Quyền (License)

Dự án được phân phối dưới giấy phép [MIT License](LICENSE).  
Phát triển bởi [Vũ Hoài Nam](https://github.com/vunameaut).
