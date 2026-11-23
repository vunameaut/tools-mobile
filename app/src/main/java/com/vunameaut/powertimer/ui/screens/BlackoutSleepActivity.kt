package com.vunameaut.powertimer.ui.screens

import android.app.Activity
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
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
import com.vunameaut.powertimer.R

/**
 * Màn hình tối đen OLED 0% độ sáng kết hợp hạ thời gian chờ màn hình (SCREEN_OFF_TIMEOUT) xuống mức tối thiểu.
 * Hỗ trợ bật đè ngay lên trên Facebook / TikTok / YouTube đang phát từ dưới nền bằng 3 lớp:
 * 1. Màn hình đen Overlay OLED (nếu có quyền Hiển thị trên ứng dụng khác)
 * 2. Activity Launch từ nền với MODE_BACKGROUND_ACTIVITY_START_ALLOWED (Android 14+)
 * 3. FullScreenIntent Notification ưu tiên cao tự hủy sau khi tắt màn hình
 * -> Ép ứng dụng video chuyển sang nền (nhả FLAG_KEEP_SCREEN_ON & tự pause), giúp máy tự tắt màn hình chuẩn Ngân hàng!
 */
class BlackoutSleepActivity : Activity() {

    companion object {
        private const val TAG = "BlackoutSleepActivity"
        private const val PREFS_NAME = "bank_safe_sleep_prefs"
        private const val KEY_SAVED_TIMEOUT = "saved_screen_off_timeout"
        private const val MIN_TIMEOUT_MS = 1000 // 1 giây (Android sẽ áp mức tối thiểu phần cứng ~5-10s)
        private const val FULLSCREEN_CHANNEL_ID = "blackout_sleep_channel"
        private const val FULLSCREEN_NOTIF_ID = 1099

        @Volatile
        private var overlayBlackView: View? = null

        fun launch(context: Context) {
            val appCtx = context.applicationContext
            applyMinimumScreenTimeout(appCtx)

            // Lớp 1: Phủ màn hình đen OLED 0% tức thì nếu đã có quyền Overlay (đè mượt lên Facebook / TikTok)
            showBlackoutOverlayIfPermitted(appCtx)

            // Lớp 2 & 3: Mở BlackoutSleepActivity lên Foreground để ép app video vào onPause() & nhả FLAG_KEEP_SCREEN_ON
            val intent = Intent(appCtx, BlackoutSleepActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }

            val optionsBundle = if (Build.VERSION.SDK_INT >= 34) {
                runCatching {
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                        .toBundle()
                }.getOrNull()
            } else null

            val pi = PendingIntent.getActivity(
                appCtx,
                1098,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Thử gửi PendingIntent kèm quyền Background Start (Android 14+)
            val sentByPi = runCatching {
                if (Build.VERSION.SDK_INT >= 34 && optionsBundle != null) {
                    pi.send(appCtx, 0, intent, null, null, null, optionsBundle)
                } else {
                    pi.send()
                }
                true
            }.getOrDefault(false)

            // Fallback trực tiếp startActivity
            runCatching {
                if (optionsBundle != null) {
                    appCtx.startActivity(intent, optionsBundle)
                } else if (!sentByPi) {
                    appCtx.startActivity(intent)
                }
            }.onFailure {
                Log.w(TAG, "Direct startActivity failed: ${it.message}")
            }

            // Lớp 3: FullScreenIntent dự phòng khi Android/OriginOS chặn startActivity từ app nền (chưa bật Overlay)
            postFullScreenBlackoutFallback(appCtx, pi)
        }

        private fun showBlackoutOverlayIfPermitted(appCtx: Context) {
            if (!Settings.canDrawOverlays(appCtx)) return
            Handler(Looper.getMainLooper()).post {
                if (overlayBlackView != null) return@post
                val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                val view = FrameLayout(appCtx).apply {
                    setBackgroundColor(Color.BLACK)
                    val label = TextView(appCtx).apply {
                        text = "Đang tắt màn hình (Chạm 2 lần để hủy)"
                        setTextColor(Color.argb(42, 255, 255, 255))
                        textSize = 11f
                        gravity = Gravity.CENTER
                    }
                    addView(
                        label,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER
                        )
                    )
                    Handler(Looper.getMainLooper()).postDelayed({ label.visibility = View.GONE }, 1500L)

                    var lastTap = 0L
                    setOnClickListener {
                        val now = System.currentTimeMillis()
                        if (now - lastTap < 600L) {
                            dismissBlackoutOverlay(appCtx)
                            restoreOriginalScreenTimeout(appCtx)
                        }
                        lastTap = now
                    }
                }

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.OPAQUE
                ).apply {
                    screenBrightness = 0.0f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                runCatching {
                    wm.addView(view, params)
                    overlayBlackView = view
                }
                // Tự động dọn overlay sau tối đa 30s nếu máy không phát ACTION_SCREEN_OFF
                Handler(Looper.getMainLooper()).postDelayed({
                    dismissBlackoutOverlay(appCtx)
                    restoreOriginalScreenTimeout(appCtx)
                }, 30_000L)
            }
        }

        fun dismissBlackoutOverlay(appCtx: Context) {
            Handler(Looper.getMainLooper()).post {
                val view = overlayBlackView ?: return@post
                val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                runCatching { wm?.removeView(view) }
                overlayBlackView = null
            }
        }

        private fun postFullScreenBlackoutFallback(appCtx: Context, fullScreenPi: PendingIntent) {
            runCatching {
                val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val chan = NotificationChannel(
                        FULLSCREEN_CHANNEL_ID,
                        "Tắt màn hình chuẩn Ngân hàng",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        setSound(null, null)
                        enableVibration(false)
                    }
                    nm.createNotificationChannel(chan)
                }
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(appCtx, FULLSCREEN_CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(appCtx)
                }
                builder.setSmallIcon(R.drawable.ic_stat_timer)
                    .setContentTitle("Đang tắt màn hình...")
                    .setContentText("Chạm để hủy hoặc chờ 5 giây máy tự khóa")
                    .setFullScreenIntent(fullScreenPi, true)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ALARM)

                nm.notify(FULLSCREEN_NOTIF_ID, builder.build())
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching { nm.cancel(FULLSCREEN_NOTIF_ID) }
                }, 4000L)
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
                dismissBlackoutOverlay(applicationContext)
                restoreOriginalScreenTimeout(applicationContext)
                runCatching {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(FULLSCREEN_NOTIF_ID)
                }
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(FULLSCREEN_NOTIF_ID)
        }

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
                text = "Đang tắt màn hình (Chạm 2 lần để hủy)"
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

            handler.postDelayed({ hint.visibility = View.GONE }, 1500L)

            var lastTapTime = 0L
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 600L) {
                    dismissBlackoutOverlay(applicationContext)
                    restoreOriginalScreenTimeout(applicationContext)
                    finishAndRemoveTask()
                }
                lastTapTime = now
            }
        }
        setContentView(root)
        hideSystemUI(root)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            }
            receiverRegistered = true
        } catch (_: Throwable) {
        }

        handler.postDelayed({
            dismissBlackoutOverlay(applicationContext)
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
        dismissBlackoutOverlay(applicationContext)
        restoreOriginalScreenTimeout(applicationContext)
    }
}
