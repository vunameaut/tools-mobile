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
     * Thực thi quy trình tắt màn hình & dừng phát media
     */
    suspend fun executeShutdown(
        mode: ShutdownMode,
        fadeAudio: Boolean = true,
        enableDnd: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing screen off sequence with mode: $mode")

        // Kiểm tra xem màn hình hiện tại đang sáng hay đã tắt sẵn từ trước
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenCurrentlyOn = pm?.isInteractive ?: true

        // 1 & 2. Giảm âm lượng êm ái (chỉ khi màn hình đang sáng), dừng phát nhạc/video, nhả AudioFocus và khôi phục lại mức âm lượng ban đầu
        audioMediaManager.fadeOutAndPauseMedia(fadeAudio = fadeAudio && isScreenCurrentlyOn)

        // 3. Xử lý chế độ Không làm phiền (Mặc định tắt để không bao giờ làm lỡ cuộc gọi / báo thức sáng hôm sau)
        if (enableDnd) {
            audioMediaManager.enableDoNotDisturb()
        } else {
            AudioMediaManager.restoreNormalRingerIfModified(context)
        }

        // Nếu màn hình ĐÃ TẮT SẴN (ví dụ hết video YouTube và máy đã tự tắt màn hình từ trước),
        // không cần mở BlackoutSleepActivity làm sáng màn hình lại giữa đêm!
        if (!isScreenCurrentlyOn) {
            Log.i(TAG, "Screen is already off (non-interactive). Skipping screen blackout launch.")
            return@withContext true
        }

        // 4. Thực hiện tắt / khóa màn hình theo chế độ khi màn hình đang sáng
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
     * Tắt màn hình chuẩn Ngân hàng (Không dùng Trợ năng + Giữ nguyên Vân tay / Khuôn mặt):
     * Hạ thời gian sáng màn hình xuống mức tối thiểu & mở màn hình tối đen OLED 0% độ sáng.
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
     * Tắt màn hình qua Trợ năng (Giữ nguyên vân tay / khuôn mặt) rồi TỰ ĐỘNG TẮT Trợ năng ngay sau đó.
     */
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

    /**
     * Về màn hình chính (Home) rồi tắt màn hình và tự động tắt Trợ năng
     */
    private suspend fun executeHomeAndLock(): Boolean {
        val accessibilityService = PowerAccessibilityService.instance
        if (accessibilityService != null) {
            accessibilityService.performGoHome()
            delay(400L)
            return accessibilityService.performLockScreen(autoDisableAfterLock = true)
        }
        return executeAccessibilityLock()
    }

    /**
     * Khóa màn hình qua quyền Quản trị viên thiết bị (DevicePolicyManager) — Không dùng Trợ năng
     */
    private suspend fun executeDeviceAdminLock(allowFallback: Boolean = true): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            dpm.lockNow()
            return true
        }
        if (allowFallback) {
            return executeBankSafeSleep()
        }
        return false
    }
}
