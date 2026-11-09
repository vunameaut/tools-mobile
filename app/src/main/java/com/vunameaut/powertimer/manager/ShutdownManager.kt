package com.vunameaut.powertimer.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.receiver.DeviceAdminSampleReceiver
import com.vunameaut.powertimer.service.PowerAccessibilityService
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
            ShutdownMode.ACCESSIBILITY -> executeAccessibilityLock()
            ShutdownMode.HOME_AND_LOCK -> executeHomeAndLock()
            ShutdownMode.DEVICE_ADMIN -> executeDeviceAdminLock()
            ShutdownMode.MEDIA_ONLY -> {
                Log.i(TAG, "Media paused. Screen will sleep according to device screen timeout.")
                true
            }
        }
    }

    /**
     * Tắt màn hình qua Trợ năng (Giữ nguyên vân tay / khuôn mặt)
     */
    private fun executeAccessibilityLock(): Boolean {
        val accessibilityService = PowerAccessibilityService.instance
        if (accessibilityService != null && accessibilityService.performLockScreen()) {
            return true
        }

        // Nếu Trợ năng chưa bật, thử dùng quyền Quản trị viên
        val adminSuccess = executeDeviceAdminLock()
        if (!adminSuccess) {
            showToastOnMain("Đã dừng nhạc. Hãy bật Dịch vụ Trợ năng để app có thể tắt màn hình tự động.")
        }
        return adminSuccess
    }

    /**
     * Về màn hình chính (Home) rồi tắt màn hình
     */
    private suspend fun executeHomeAndLock(): Boolean {
        val accessibilityService = PowerAccessibilityService.instance
        if (accessibilityService != null) {
            accessibilityService.performGoHome()
            delay(400L)
            return accessibilityService.performLockScreen()
        }
        return executeAccessibilityLock()
    }

    /**
     * Khóa màn hình qua quyền Quản trị viên thiết bị (DevicePolicyManager)
     */
    private fun executeDeviceAdminLock(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            dpm.lockNow()
            return true
        }
        return false
    }

    private fun showToastOnMain(message: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
