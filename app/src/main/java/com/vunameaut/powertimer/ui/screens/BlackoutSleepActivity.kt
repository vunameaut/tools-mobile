package com.vunameaut.powertimer.ui.screens

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Màn hình tối đen OLED 0% độ sáng kết hợp hạ thời gian chờ màn hình (SCREEN_OFF_TIMEOUT) xuống mức tối thiểu.
 * Giúp máy tự tắt và khóa màn hình tự nhiên sau vài giây:
 * - KHÔNG cần bật Trợ năng (Accessibility) -> Dùng mọi App Ngân hàng & VNeID 100% bình thường!
 * - Giữ nguyên mở khóa bằng Vân tay / Khuôn mặt (Biometrics)!
 */
class BlackoutSleepActivity : Activity() {

    companion object {
        private const val TAG = "BlackoutSleepActivity"
        private const val PREFS_NAME = "bank_safe_sleep_prefs"
        private const val KEY_SAVED_TIMEOUT = "saved_screen_off_timeout"
        private const val MIN_TIMEOUT_MS = 1000 // 1 giây (Android sẽ áp mức tối thiểu của phần cứng ~5-10s)

        fun launch(context: Context) {
            applyMinimumScreenTimeout(context)
            try {
                val intent = Intent(context, BlackoutSleepActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Could not launch BlackoutSleepActivity: ${e.message}")
            }
        }

        fun applyMinimumScreenTimeout(context: Context): Boolean {
            if (!Settings.System.canWrite(context)) return false
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val currentTimeout = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    60_000
                )
                // Chỉ lưu giá trị gốc nếu nó lớn hơn 12 giây (tránh ghi đè giá trị tạm 1000ms)
                if (currentTimeout > 12_000 && !prefs.contains(KEY_SAVED_TIMEOUT)) {
                    prefs.edit().putInt(KEY_SAVED_TIMEOUT, currentTimeout).apply()
                }
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    MIN_TIMEOUT_MS
                )
                true
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to lower SCREEN_OFF_TIMEOUT: ${e.message}")
                false
            }
        }

        fun restoreOriginalScreenTimeout(context: Context) {
            if (!Settings.System.canWrite(context)) return
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val saved = prefs.getInt(KEY_SAVED_TIMEOUT, -1)
                if (saved > 0) {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        saved
                    )
                    prefs.edit().remove(KEY_SAVED_TIMEOUT).apply()
                    Log.i(TAG, "Restored original SCREEN_OFF_TIMEOUT to ${saved}ms")
                } else {
                    val current = Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        60_000
                    )
                    if (current <= 10_000) {
                        Settings.System.putInt(
                            context.contentResolver,
                            Settings.System.SCREEN_OFF_TIMEOUT,
                            60_000
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to restore SCREEN_OFF_TIMEOUT: ${e.message}")
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                restoreOriginalScreenTimeout(applicationContext)
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hạ độ sáng cửa sổ xuống mức tối thiểu (0.0f) & tắt cờ giữ sáng màn hình
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val params = window.attributes
        params.screenBrightness = 0.0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = params
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            val hint = TextView(this@BlackoutSleepActivity).apply {
                text = "Đang tắt màn hình (Chạm 2 lần để huỷ)"
                setTextColor(Color.argb(38, 255, 255, 255))
                textSize = 11f
                gravity = Gravity.CENTER
            }
            addView(
                hint,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )

            // Ẩn dòng chữ mờ sau 1.8 giây để màn hình đen tuyệt đối OLED 100%
            handler.postDelayed({ hint.visibility = View.GONE }, 1800L)

            var lastTapTime = 0L
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 600L) {
                    restoreOriginalScreenTimeout(applicationContext)
                    finishAndRemoveTask()
                }
                lastTapTime = now
            }
        }
        setContentView(root)
        hideSystemUI(root)

        try {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            receiverRegistered = true
        } catch (_: Throwable) {
        }

        // Tự động khôi phục cài đặt timeout sau 25s phòng trường hợp hệ thống đã tắt màn hình
        handler.postDelayed({
            restoreOriginalScreenTimeout(applicationContext)
        }, 25_000L)
    }

    private fun hideSystemUI(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            view.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (_: Throwable) {
            }
            receiverRegistered = false
        }
        restoreOriginalScreenTimeout(applicationContext)
    }
}
