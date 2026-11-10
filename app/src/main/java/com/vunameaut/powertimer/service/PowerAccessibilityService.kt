package com.vunameaut.powertimer.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PowerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PowerAccessibility"
        var instance: PowerAccessibilityService? = null
            private set

        /**
         * Bật lại Component dịch vụ Trợ năng khi người dùng chủ động muốn dùng chế độ Trợ năng.
         */
        fun setComponentEnabled(context: Context, enabled: Boolean) {
            try {
                val cn = ComponentName(context, PowerAccessibilityService::class.java)
                val newState = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                context.packageManager.setComponentEnabledSetting(
                    cn,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to change component state: ${e.message}")
            }
        }

        /**
         * Tắt ngay lập tức dịch vụ Trợ năng (disableSelf) VÀ ẩn Component khỏi hệ thống
         * để các ứng dụng Ngân hàng / VNeID không phát hiện và không chặn giao dịch.
         */
        fun disableAndHideForBanking(context: Context) {
            try {
                instance?.disableSelf()
            } catch (e: Throwable) {
                Log.w(TAG, "disableSelf failed: ${e.message}")
            }
            setComponentEnabled(context, false)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "PowerAccessibilityService connected and ready")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "PowerAccessibilityService interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Không lắng nghe bất kỳ sự kiện màn hình nào để đảm bảo an toàn tuyệt đối cho Ngân hàng
    }

    /**
     * Tắt và khóa màn hình tự nhiên (Hỗ trợ từ Android 9+)
     * Ngay sau khi khóa màn hình xong (800ms), tự động gọi disableSelf() để tắt Trợ năng,
     * giúp người dùng mở máy lên vào thẳng App Ngân hàng mà không bị chặn!
     */
    fun performLockScreen(autoDisableAfterLock: Boolean = true): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val result = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) returned: $result")
            if (result && autoDisableAfterLock) {
                mainHandler.postDelayed({
                    try {
                        Log.i(TAG, "Auto-disabling AccessibilityService after screen lock for Banking compatibility")
                        disableSelf()
                    } catch (e: Throwable) {
                        Log.w(TAG, "Auto disableSelf error: ${e.message}")
                    }
                }, 800L)
            }
            result
        } else {
            Log.w(TAG, "GLOBAL_ACTION_LOCK_SCREEN requires Android 9 (API 28)+")
            false
        }
    }

    /**
     * Trở về màn hình chính (Home)
     */
    fun performGoHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
