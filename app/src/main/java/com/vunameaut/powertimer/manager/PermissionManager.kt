package com.vunameaut.powertimer.manager

import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.vunameaut.powertimer.receiver.DeviceAdminSampleReceiver
import com.vunameaut.powertimer.service.PowerAccessibilityService
import rikka.shizuku.Shizuku
import java.io.File

class PermissionManager(private val context: Context) {

    fun isAccessibilityServiceEnabled(): Boolean {
        val cn = ComponentName(context, PowerAccessibilityService::class.java)
        val compState = context.packageManager.getComponentEnabledSetting(cn)
        if (compState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            compState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        ) {
            // Trong AndroidManifest.xml mặc định android:enabled="false"
            if (PowerAccessibilityService.instance == null) return false
        }

        val expectedComponentName = cn.flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            it.equals(expectedComponentName, ignoreCase = true)
        }
    }

    fun openAccessibilitySettings() {
        // Bật Component trước khi mở trang Cài đặt Trợ năng
        PowerAccessibilityService.setComponentEnabled(context, true)
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Tắt và ẩn hoàn toàn dịch vụ Trợ năng để tương thích 100% với các ứng dụng Ngân hàng & VNeID.
     */
    fun disableAccessibilityForBanking() {
        PowerAccessibilityService.disableAndHideForBanking(context)
    }

    fun canWriteSystemSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun openWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun isDeviceAdminActive(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun requestDeviceAdmin() {
        // Trên Vivo OriginOS / HyperOS, Android 14+ thường tự back về app nếu bắn thẳng ACTION_ADD_DEVICE_ADMIN
        // đối với APK cài ngoài Store. Vì vậy mở thẳng danh sách "Ứng dụng quản trị thiết bị" trong Cài đặt!
        val openedAdminList = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$DeviceAdminSettingsActivity"),
            Intent().setClassName("com.android.settings", "com.android.settings.DeviceAdminSettings")
        ).any { candidate ->
            runCatching {
                candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(candidate)
                true
            }.getOrDefault(false)
        }

        if (openedAdminList) {
            android.widget.Toast.makeText(
                context,
                "Chọn \"Tools Hub\" trong danh sách và bấm Kích hoạt!",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        val adminComponent = ComponentName(context, DeviceAdminSampleReceiver::class.java)
        val addIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Quyền Quản trị viên thiết bị giúp khóa màn hình tức thì mà KHÔNG cần bật Trợ năng (tương thích 100% với App Ngân hàng)."
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openedAdd = runCatching {
            context.startActivity(addIntent)
            true
        }.getOrDefault(false)

        if (!openedAdd) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        }
    }

    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() } || canExecuteSu()
    }

    private fun canExecuteSu(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            if (isShizukuAvailable()) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = 1001) {
        try {
            if (isShizukuAvailable() && !hasShizukuPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Exception) {
        }
    }
}
