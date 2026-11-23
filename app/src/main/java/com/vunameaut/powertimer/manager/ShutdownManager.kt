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
     * Thực thi quy trình tắt màn hình & dừng phát media tức thì khi hết giờ:
     * - Chống chạy trùng lặp giữa TimerForegroundService và TimerAlarmReceiver trong vòng 2.5 giây.
     * - KHÔNG hạ âm lượng máy về 0.
     * - Thực hiện tắt/khóa màn hình NGAY LẬP TỨC đồng thời với lệnh dừng nhạc/video!
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

        // 1. Dừng ngay lập tức nhạc/video (MediaSession + Permanent AudioFocus + KeyEvent)
        audioMediaManager.pauseAllMedia()

        // 2. Khóa / tắt màn hình NGAY LẬP TỨC (0ms delay) khi màn hình đang sáng
        val lockResult = if (isScreenCurrentlyOn) {
            when (mode) {
                ShutdownMode.BANK_SAFE_SLEEP -> executeBankSafeSleep()
                ShutdownMode.DEVICE_ADMIN -> executeDeviceAdminLock()
                ShutdownMode.MEDIA_ONLY -> {
                    Log.i(TAG, "Media paused in MEDIA_ONLY mode.")
                    true
                }
                ShutdownMode.ACCESSIBILITY -> executeAccessibilityLock()
                ShutdownMode.HOME_AND_LOCK -> executeHomeAndLock()
            }
        } else {
            Log.i(TAG, "Screen is already off (non-interactive). Skipping screen lock UI.")
            true
        }

        // 3. Bồi thêm xung tĩnh & nhịp Pause lần 2/3 để chặn Facebook/YouTube phát tiếp
        withContext(Dispatchers.IO) {
            audioMediaManager.pauseAllMediaAndReleaseFocus()
        }

        return@withContext lockResult
    }

    private fun executeBankSafeSleep(): Boolean {
        return try {
            PowerAccessibilityService.disableAndHideForBanking(context)
            BlackoutSleepActivity.launch(context)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "BankSafeSleep error: ${e.message}")
            executeDeviceAdminLock(allowFallback = false)
        }
    }

    private fun executeDeviceAdminLock(allowFallback: Boolean = true): Boolean {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                return true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "DeviceAdmin lockNow error: ${e.message}")
        }
        if (allowFallback) {
            return executeBankSafeSleep()
        }
        return false
    }

    private fun executeAccessibilityLock(): Boolean {
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
