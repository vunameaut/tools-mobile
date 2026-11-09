package com.vunameaut.powertimer.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PowerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PowerAccessibility"
        var instance: PowerAccessibilityService? = null
            private set

        private val POWER_OFF_TEXT_VARIANTS = listOf(
            "Tắt nguồn",
            "Tắt máy",
            "Power off",
            "Power Off",
            "Shut down",
            "Shutdown",
            "Éteindre",
            "Apagar",
            "Ausschalten",
            "Выключение",
            "关机",
            "電源を切る"
        )

        private val CONFIRMATION_TEXT_VARIANTS = listOf(
            "Chạm một lần nữa để tắt nguồn",
            "Nhấn để tắt",
            "Tap to power off",
            "Touch again to power off",
            "Confirm",
            "Xác nhận",
            "OK"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isAwaitingPowerOffClick = false
    private var clickAttempts = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "PowerAccessibilityService connected")
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

    /**
     * Kích hoạt chuỗi mở Menu Nguồn và tự động nhấn Tắt máy
     */
    fun performPowerOff() {
        Log.d(TAG, "Starting automated power off sequence...")
        isAwaitingPowerOffClick = true
        clickAttempts = 0

        // 1. Mở hộp thoại Menu Nguồn của hệ thống Android
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

        // Lên lịch quét định kỳ trong trường hợp event không bắt được ngay
        schedulePeriodicScan()
    }

    /**
     * Khóa màn hình ngay lập tức (Hỗ trợ từ Android 9+)
     */
    fun performLockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isAwaitingPowerOffClick || event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                tryFindAndClickPowerButton()
            }
        }
    }

    private fun schedulePeriodicScan() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isAwaitingPowerOffClick) return

                val success = tryFindAndClickPowerButton()
                clickAttempts++

                if (!success && clickAttempts < 15) {
                    handler.postDelayed(this, 300)
                } else if (clickAttempts >= 15) {
                    isAwaitingPowerOffClick = false
                    Log.w(TAG, "Power off timeout reached after 15 scan attempts")
                }
            }
        }, 400)
    }

    private fun tryFindAndClickPowerButton(): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        // 1. Tìm nút bấm xác nhận lần 2 (nếu có dialog xuất hiện)
        if (findAndClickByTexts(rootNode, CONFIRMATION_TEXT_VARIANTS)) {
            Log.d(TAG, "Clicked confirmation power off button!")
            // Sau khi bấm xác nhận, đợi thêm 1 chút rồi tắt cờ
            handler.postDelayed({ isAwaitingPowerOffClick = false }, 1000)
            return true
        }

        // 2. Tìm nút Tắt nguồn lần 1
        if (findAndClickByTexts(rootNode, POWER_OFF_TEXT_VARIANTS)) {
            Log.d(TAG, "Clicked main power off button! Waiting for confirmation...")
            return true
        }

        // 3. Tìm theo View ID phổ biến của AOSP / Samsung / Pixel
        val commonIds = listOf(
            "com.android.systemui:id/power",
            "com.android.systemui:id/button_power",
            "com.android.systemui:id/global_actions_power",
            "com.android.systemui:id/item_power",
            "com.android.settings:id/power_off"
        )
        for (id in commonIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (performClickRecursive(node)) {
                    Log.d(TAG, "Clicked power node by ID: $id")
                    return true
                }
            }
        }

        return false
    }

    private fun findAndClickByTexts(rootNode: AccessibilityNodeInfo, textList: List<String>): Boolean {
        for (targetText in textList) {
            val matchingNodes = rootNode.findAccessibilityNodeInfosByText(targetText)
            for (node in matchingNodes) {
                if (performClickRecursive(node)) {
                    return true
                }
            }
        }
        return false
    }

    private fun performClickRecursive(node: AccessibilityNodeInfo?): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }
            current = current.parent
        }
        return false
    }
}
