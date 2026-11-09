package com.vunameaut.powertimer.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.receiver.DeviceAdminSampleReceiver
import com.vunameaut.powertimer.service.PowerAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShutdownManager(private val context: Context) {

    companion object {
        private const val TAG = "ShutdownManager"
    }

    private val audioMediaManager = AudioMediaManager(context)
    private val permissionManager = PermissionManager(context)

    suspend fun executeShutdown(
        mode: ShutdownMode,
        fadeAudio: Boolean = true,
        enableDnd: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing shutdown with mode: $mode")

        // 1. Dừng media và giảm âm lượng nếu được bật
        if (fadeAudio) {
            audioMediaManager.fadeOutVolume(5000L)
        }
        audioMediaManager.pauseAllMedia()

        if (enableDnd) {
            audioMediaManager.enableDoNotDisturb()
        }

        // 2. Thực hiện hành động tắt máy theo chế độ đã chọn
        return@withContext when (mode) {
            ShutdownMode.ACCESSIBILITY -> executeAccessibilityShutdown()
            ShutdownMode.SHIZUKU -> executeShizukuShutdown()
            ShutdownMode.ROOT -> executeRootShutdown()
            ShutdownMode.SLEEP_LOCK -> executeSleepLock()
        }
    }

    /**
     * Chế độ Trợ năng: Kích hoạt menu nguồn và bấm Tắt máy
     */
    private fun executeAccessibilityShutdown(): Boolean {
        val service = PowerAccessibilityService.instance
        if (service != null) {
            service.performPowerOff()
            return true
        } else {
            showToastOnMain("Dịch vụ Trợ năng chưa bật! Chuyển sang Khóa màn hình...")
            return executeSleepLock()
        }
    }

    /**
     * Chế độ Shizuku: Chạy lệnh tắt máy qua Wireless ADB (Android 11+)
     */
    private fun executeShizukuShutdown(): Boolean {
        return try {
            if (permissionManager.hasShizukuPermission()) {
                val method = Shizuku::class.java.getMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                val process = method.invoke(null, arrayOf("svc", "power", "shutdown"), null, null) as Process
                process.waitFor()
                true
            } else {
                showToastOnMain("Chưa cấp quyền Shizuku! Thử chuyển sang Trợ năng...")
                executeAccessibilityShutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku shutdown failed: ${e.message}", e)
            executeAccessibilityShutdown()
        }
    }

    /**
     * Chế độ Root: Gọi su -c svc power shutdown
     */
    private fun executeRootShutdown(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "svc power shutdown || reboot -p"))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                true
            } else {
                Log.e(TAG, "Root command returned exit code: $exitCode")
                executeAccessibilityShutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Root shutdown failed: ${e.message}", e)
            executeAccessibilityShutdown()
        }
    }

    /**
     * Chế độ Ngủ & Khóa màn hình
     */
    private fun executeSleepLock(): Boolean {
        // Ưu tiên 1: Accessibility Service (Android 9+ không cần Device Admin)
        val accessibilityService = PowerAccessibilityService.instance
        if (accessibilityService != null && accessibilityService.performLockScreen()) {
            return true
        }

        // Ưu tiên 2: DevicePolicyManager (Khóa màn hình qua quyền Quản trị viên)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            dpm.lockNow()
            return true
        }

        showToastOnMain("Đã dừng nhạc. Vui lòng cấp quyền Trợ năng hoặc Quản trị viên để khóa màn hình tự động.")
        return false
    }

    private fun showToastOnMain(message: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
