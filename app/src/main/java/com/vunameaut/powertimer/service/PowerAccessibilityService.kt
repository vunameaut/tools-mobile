package com.vunameaut.powertimer.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PowerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PowerAccessibility"
        var instance: PowerAccessibilityService? = null
            private set
    }

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
        // Not strictly needed for global actions, but available for window events
    }

    /**
     * Tắt và khóa màn hình tự nhiên (Hỗ trợ từ Android 9+)
     * KHÔNG làm mất tính năng mở khóa bằng Vân tay / Khuôn mặt (Biometrics)!
     */
    fun performLockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val result = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) returned: $result")
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
