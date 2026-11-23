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

class ShutdownManager(private val context: Context) {

    companion object {
        private const val TAG = "ShutdownManager"
    }

    private val audioMediaManager = AudioMediaManager(context)

    /**
     * Thực thi quy trình tắt màn hình & dừng phát media cho toàn bộ 5 chế độ:
     * (Tuyệt đối không đụng vào chế độ Không làm phiền hay Chuông của máy)
     */
    suspend fun executeShutdown(
        mode: ShutdownMode,
        fadeAudio: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing screen off sequence with mode: $mode")

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenCurrentlyOn = pm?.isInteractive ?: true

        // 1. Giảm âm lượng êm ái (khi màn hình sáng) & Dừng toàn bộ media (Facebook/TikTok/YouTube...)
        audioMediaManager.fadeOutAndPauseMedia(fadeAudio = fadeAudio && isScreenCurrentlyOn)

        // Nếu màn hình ĐÃ TẮT SẴN (ví dụ hết video và máy đã tự tắt từ trước),
        // không bật BlackoutSleepActivity làm sáng lại màn hình giữa đêm!
        if (!isScreenCurrentlyOn) {
            Log.i(TAG, "Screen is already off (non-interactive). Skipping screen lock UI.")
            return@withContext true
        }

        // 2. Thực hiện tắt / khóa màn hình theo chế độ
        return@withContext when (mode) {
            ShutdownMode.BANK_SAFE_SLEEP -> executeBankSafeSleep()
            ShutdownMode.DEVICE_ADMIN -> executeDeviceAdminLock()
            ShutdownMode.MEDIA_ONLY -> {
                Log.i(TAG, "Media paused. Screen will sleep according to device screen timeout.")
                true
            }
            ShutdownMode.ACCESSIBILITY -> executeAccessibilityLock()
            ShutdownMode.HOME_AND_LOCK -> executeHomeAndLock()
        }
    }

    /**
     * Chế độ 1: Tắt màn hình Chuẩn Ngân Hàng (Không dùng Trợ năng + Giữ nguyên Vân tay / Khuôn mặt):
     * Hạ SCREEN_OFF_TIMEOUT + phủ màn hình đen OLED 0% đè lên Facebook/TikTok để app video tự pause & nhả WakeLock.
     */
    private suspend fun executeBankSafeSleep(): Boolean = withContext(Dispatchers.Main) {
        try {
            PowerAccessibilityService.disableAndHideForBanking(context)
            BlackoutSleepActivity.launch(context)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "BankSafeSleep error: ${e.message}")
            executeDeviceAdminLock(allowFallback = false)
        }
    }

    /**
     * Chế độ 2: Khóa màn hình tức thì qua Device Administrator (Không dùng Trợ năng)
     */
    private suspend fun executeDeviceAdminLock(allowFallback: Boolean = true): Boolean = withContext(Dispatchers.Main) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                return@withContext true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "DeviceAdmin lockNow error: ${e.message}")
        }
        if (allowFallback) {
            return@withContext executeBankSafeSleep()
        }
        return@withContext false
    }

    /**
     * Chế độ 4: Tắt màn hình qua Trợ năng (Giữ nguyên vân tay / khuôn mặt) rồi TỰ ĐỘNG TẮT Trợ năng ngay sau đó.
     */
    private suspend fun executeAccessibilityLock(): Boolean = withContext(Dispatchers.Main) {
        val accessibilityService = PowerAccessibilityService.instance
        if (accessibilityService != null && accessibilityService.performLockScreen(autoDisableAfterLock = true)) {
            return@withContext true
        }
        if (executeDeviceAdminLock(allowFallback = false)) {
            return@withContext true
        }
        return@withContext executeBankSafeSleep()
    }

    /**
     * Chế độ 5: Về màn hình chính (Home) rồi khóa màn hình và tự động tắt Trợ năng
     */
    private suspend fun executeHomeAndLock(): Boolean = withContext(Dispatchers.Main) {
        val accessibilityService = PowerAccessibilityService.instance
        if (accessibilityService != null) {
            accessibilityService.performGoHome()
            delay(500L)
            if (accessibilityService.performLockScreen(autoDisableAfterLock = true)) {
                return@withContext true
            }
        }
        return@withContext executeAccessibilityLock()
    }
}
