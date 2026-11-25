package com.vunameaut.powertimer.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.receiver.DeviceAdminSampleReceiver
import com.vunameaut.powertimer.service.PowerAccessibilityService
import com.vunameaut.powertimer.ui.screens.BlackoutSleepActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class ShutdownExecutionResult(
    val succeeded: Boolean,
    val appliedTimeoutMs: Int = -1,
    val engagedOledGuard: Boolean = false
)

class ShutdownManager(private val context: Context) {

    companion object {
        private const val TAG = "ShutdownManager"
        private val lastExecutionEpochMs = AtomicLong(0L)
    }

    private val audioMediaManager = AudioMediaManager(context)

    suspend fun executeShutdown(
        mode: ShutdownMode,
        fadeAudio: Boolean = true
    ): Boolean = executeShutdownWithResult(mode, fadeAudio, holdFocusUntilScreenOff = false).succeeded

    /**
     * Thực thi quy trình tắt màn hình thật (Lock Screen) & dừng phát media (TikTok, Facebook, YouTube...):
     * - KHÔNG hạ âm lượng máy về 0.
     * - Khi [holdFocusUntilScreenOff] = true, giữ AudioFocus xuyên suốt cửa sổ 15s–40s cho tới khi
     *   màn hình thực sự tắt (khắc phục lỗi Vivo OriginOS Video app tự play lại sau 6 giây).
     */
    suspend fun executeShutdownWithResult(
        mode: ShutdownMode,
        fadeAudio: Boolean = true,
        holdFocusUntilScreenOff: Boolean = true
    ): ShutdownExecutionResult = withContext(Dispatchers.Main) {
        val now = System.currentTimeMillis()
        val prev = lastExecutionEpochMs.get()
        if (now - prev < 2500L) {
            Log.i(TAG, "Skipping duplicate executeShutdown triggered within 2.5s")
            val curTimeout = runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 15_000)
            }.getOrDefault(15_000)
            return@withContext ShutdownExecutionResult(succeeded = true, appliedTimeoutMs = curTimeout)
        }
        lastExecutionEpochMs.set(now)

        Log.i(TAG, "Executing screen off sequence with mode: $mode")

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenCurrentlyOn = pm?.isInteractive ?: true

        // 1. Dừng & giữ chặt AudioFocus ngay lập tức để ngăn TikTok / FB / YT phát tiếp
        if (holdFocusUntilScreenOff && isScreenCurrentlyOn && mode != ShutdownMode.MEDIA_ONLY) {
            audioMediaManager.holdMediaPauseFocus(ShutdownEnforcementPolicy.FULL_LOCK_MAX_FOCUS_HOLD_MS)
        } else {
            audioMediaManager.pauseAllMediaAndReleaseFocus(ShutdownEnforcementPolicy.MEDIA_ONLY_FOCUS_HOLD_MS)
        }

        if (!isScreenCurrentlyOn) {
            Log.i(TAG, "Screen is already off (non-interactive). Media paused cleanly without waking screen.")
            return@withContext ShutdownExecutionResult(succeeded = true)
        }

        var appliedTimeoutMs = -1
        var engagedOledGuard = false

        val lockResult = when (mode) {
            ShutdownMode.BANK_SAFE_SLEEP -> {
                val res = executeBankSafeSleepWithResult()
                appliedTimeoutMs = res.appliedTimeoutMs
                engagedOledGuard = res.engagedOledGuard
                res.succeeded
            }
            ShutdownMode.DEVICE_ADMIN -> executeDeviceAdminLock(allowFallback = true)
            ShutdownMode.MEDIA_ONLY -> {
                BlackoutSleepActivity.launchHomeScreenFromBackground(context)
                Log.i(TAG, "Media paused & returned Home in MEDIA_ONLY mode.")
                true
            }
            ShutdownMode.ACCESSIBILITY -> executeAccessibilityLock()
            ShutdownMode.HOME_AND_LOCK -> executeHomeAndLock()
        }

        return@withContext ShutdownExecutionResult(
            succeeded = lockResult,
            appliedTimeoutMs = appliedTimeoutMs,
            engagedOledGuard = engagedOledGuard
        )
    }

    suspend fun reassertMediaAndTimeout(mode: ShutdownMode) = withContext(Dispatchers.Main) {
        audioMediaManager.holdMediaPauseFocus(ShutdownEnforcementPolicy.FULL_LOCK_MAX_FOCUS_HOLD_MS)
        if (mode == ShutdownMode.BANK_SAFE_SLEEP && Settings.System.canWrite(context)) {
            BlackoutSleepActivity.applyMinimumScreenTimeout(context)
        }
    }

    fun releaseHeldAudioFocus() {
        audioMediaManager.releaseHeldMediaFocus()
    }

    fun restoreTimeoutIfModified() {
        BlackoutSleepActivity.restoreOriginalScreenTimeout(context)
    }

    private suspend fun executeBankSafeSleepWithResult(): ShutdownExecutionResult {
        return try {
            val acc = PowerAccessibilityService.instance
            if (acc != null) {
                acc.performGoHome()
                delay(250L)
                if (acc.performLockScreen(autoDisableAfterLock = true)) {
                    return ShutdownExecutionResult(succeeded = true)
                }
            }
            PowerAccessibilityService.disableAndHideForBanking(context)

            if (Settings.System.canWrite(context)) {
                val applied = BlackoutSleepActivity.triggerNaturalHomeScreenLock(context)
                return ShutdownExecutionResult(succeeded = true, appliedTimeoutMs = applied)
            }

            // Fallback thông minh nếu người dùng chưa cấp WRITE_SETTINGS (ví dụ bật nhanh từ Widget)
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                BlackoutSleepActivity.launchHomeScreenFromBackground(context)
                delay(150L)
                dpm.lockNow()
                return ShutdownExecutionResult(succeeded = true)
            }

            BlackoutSleepActivity.launchOledBatteryGuard(context)
            ShutdownExecutionResult(succeeded = true, engagedOledGuard = true)
        } catch (e: Throwable) {
            Log.e(TAG, "BankSafeSleep error: ${e.message}")
            ShutdownExecutionResult(succeeded = false)
        }
    }

    private suspend fun executeBankSafeSleep(): Boolean = executeBankSafeSleepWithResult().succeeded

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
            if (Settings.System.canWrite(context)) {
                BlackoutSleepActivity.triggerNaturalHomeScreenLock(context)
            } else {
                BlackoutSleepActivity.launchOledBatteryGuard(context)
            }
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
