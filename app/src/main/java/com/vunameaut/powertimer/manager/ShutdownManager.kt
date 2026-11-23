package com.vunameaut.powertimer.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.receiver.DeviceAdminSampleReceiver
import com.vunameaut.powertimer.service.PowerAccessibilityService
import com.vunameaut.powertimer.ui.screens.BlackoutSleepActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class ShutdownManager(private val context: Context) {

    companion object {
        private const val TAG = "ShutdownManager"
        private val lastExecutionEpochMs = AtomicLong(0L)
    }

    private val audioMediaManager = AudioMediaManager(context)

    /**
     * Thực thi quy trình tắt màn hình thật (Lock Screen) & dừng phát media (TikTok, Facebook, YouTube...):
     * - KHÔNG giữ màn hình đen phủ đè gây kẹt cảm ứng / video chạy ngầm.
     * - KHÔNG hạ âm lượng máy về 0.
     * - Thoát ứng dụng video về Màn hình chính (Home) rồi khóa màn hình phần cứng thật ngay lập tức!
     */
    suspend fun executeShutdown(
        mode: ShutdownMode,
        fadeAudio: Boolean = true
    ): Boolean = withContext(Dispatchers.Main) {
        val now = System.currentTimeMillis()
        val prev = lastExecutionEpochMs.get()
        if (now - prev < 2500L) {
            Log.i(TAG, "Skipping duplicate executeShutdown triggered within 2.5s")
            return@withContext true
        }
        lastExecutionEpochMs.set(now)

        Log.i(TAG, "Executing screen off sequence with mode: $mode")

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenCurrentlyOn = pm?.isInteractive ?: true

        // 1. Dừng ngay lập tức nhạc/video qua AudioFocus + KeyEvent + MediaSession
        audioMediaManager.pauseAllMedia()

        // 2. Xử lý khi màn hình đang sáng
        val lockResult = if (isScreenCurrentlyOn) {
            when (mode) {
                ShutdownMode.BANK_SAFE_SLEEP -> executeBankSafeSleep()
                ShutdownMode.DEVICE_ADMIN -> executeDeviceAdminLock()
                ShutdownMode.MEDIA_ONLY -> {
                    // Nếu đang mở TikTok/Douyin (không nghe lệnh AudioFocus khi ở Foreground),
                    // tự động thoát về Màn hình chính (Home) để dừng video 100% và cho máy tự tắt theo timeout!
                    BlackoutSleepActivity.launchHomeScreenFromBackground(context)
                    Log.i(TAG, "Media paused & returned Home in MEDIA_ONLY mode.")
                    true
                }
                ShutdownMode.ACCESSIBILITY -> executeAccessibilityLock()
                ShutdownMode.HOME_AND_LOCK -> executeHomeAndLock()
            }
        } else {
            Log.i(TAG, "Screen is already off (non-interactive). Skipping screen lock UI.")
            true
        }

        // 3. Bồi thêm nhịp Pause & Voice Focus pulse để chắc chắn các trình phát khác dừng hẳn
        withContext(Dispatchers.IO) {
            audioMediaManager.pauseAllMediaAndReleaseFocus()
        }

        return@withContext lockResult
    }

    /**
     * Chế độ Tắt màn hình Chuẩn Ngân Hàng (Không dùng Trợ năng -> 100% App Ngân hàng & VNeID hoạt động bình thường):
     * 1) Nếu đã bật Quản trị viên thiết bị: Thoát về Màn hình chính + khóa Lock Screen phần cứng tức thì (`dpm.lockNow()`).
     * 2) Mặc định (chỉ cần quyền Hạ thời gian sáng + Hiển thị trên app khác):
     *    Thoát ứng dụng video (TikTok/FB/YT) ra Màn hình chính thật + hạ thời gian chờ xuống mức tối thiểu
     *    -> Máy tự tắt & khóa Lock Screen tự nhiên, giữ nguyên Vân tay / Khuôn mặt, KHÔNG dùng màn hình đen giả!
     */
    private suspend fun executeBankSafeSleep(): Boolean {
        return try {
            PowerAccessibilityService.disableAndHideForBanking(context)
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                BlackoutSleepActivity.launchHomeScreenFromBackground(context)
                delay(150L)
                dpm.lockNow()
            } else {
                BlackoutSleepActivity.triggerNaturalHomeScreenLock(context)
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "BankSafeSleep error: ${e.message}")
            executeDeviceAdminLock(allowFallback = false)
        }
    }

    private suspend fun executeDeviceAdminLock(allowFallback: Boolean = true): Boolean {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                BlackoutSleepActivity.launchHomeScreenFromBackground(context)
                delay(120L)
                dpm.lockNow()
                return true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "DeviceAdmin lockNow error: ${e.message}")
        }
        if (allowFallback) {
            BlackoutSleepActivity.triggerNaturalHomeScreenLock(context)
            return true
        }
        return false
    }

    private suspend fun executeAccessibilityLock(): Boolean {
        val accessibilityService = PowerAccessibilityService.instance
        if (accessibilityService != null && accessibilityService.performLockScreen(autoDisableAfterLock = true)) {
            return true
        }
        if (executeDeviceAdminLock(allowFallback = false)) {
            return true
        }
        return executeBankSafeSleep()
    }

    private suspend fun executeHomeAndLock(): Boolean {
        val accessibilityService = PowerAccessibilityService.instance
        if (accessibilityService != null) {
            accessibilityService.performGoHome()
            delay(450L)
            if (accessibilityService.performLockScreen(autoDisableAfterLock = true)) {
                return true
            }
        }
        return executeAccessibilityLock()
    }
}
