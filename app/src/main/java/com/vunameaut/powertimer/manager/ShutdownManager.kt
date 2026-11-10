package com.vunameaut.powertimer.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.receiver.DeviceAdminSampleReceiver
import com.vunameaut.powertimer.service.PowerAccessibilityService
import com.vunameaut.powertimer.ui.screens.BlackoutSleepActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        enableDnd: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing screen off sequence with mode: $mode")

        // 1. Giảm âm lượng từ từ (Fade-out)
        if (fadeAudio) {
            audioMediaManager.fadeOutVolume(5000L)
        }

        // 2. Dừng phát toàn bộ nhạc / video / podcast
        audioMediaManager.pauseAllMedia()

        // 3. Kích hoạt chế độ Không làm phiền (Do Not Disturb) nếu được chọn
        if (enableDnd) {
            audioMediaManager.enableDoNotDisturb()
        }

        // 4. Thực hiện tắt / khóa màn hình theo chế độ
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
            // Đảm bảo tắt/ẩn Trợ năng nếu trước đó từng bật để không làm ảnh hưởng App Ngân hàng
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

        // Nếu Trợ năng chưa bật, tự động chuyển sang phương thức Chuẩn Ngân Hàng (BankSafeSleep / DeviceAdmin)
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

    private fun showToastOnMain(message: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
