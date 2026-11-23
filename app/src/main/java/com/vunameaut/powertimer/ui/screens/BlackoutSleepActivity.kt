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
 * Điều phối thoát về Màn hình chính & Tắt màn hình an toàn Chuẩn Ngân Hàng:
 * - TUYỆT ĐỐI KHÔNG giữ lớp phủ đen toàn màn hình đè lên app đang chạy (không gây kẹt cảm ứng hay video chạy ngầm).
 * - Đưa máy về Màn hình chính (CATEGORY_HOME) để TikTok / Douyin / Facebook tự dừng video và nhả FLAG_KEEP_SCREEN_ON.
 * - Khi mở Activity tắt màn hình (trường hợp fallback chưa bật Device Admin), chỉ cần chạm 1 lần bất kỳ đâu là thoát ngay.
 */
class BlackoutSleepActivity : Activity() {

    companion object {
        private const val TAG = "BlackoutSleepActivity"
        private const val PREFS_NAME = "bank_safe_sleep_prefs"
        private const val KEY_SAVED_TIMEOUT = "saved_screen_off_timeout"
        private const val MIN_TIMEOUT_MS = 1000
        private const val FULLSCREEN_CHANNEL_ID = "blackout_sleep_channel"
        private const val FULLSCREEN_NOTIF_ID = 1099
        private const val EXTRA_GO_HOME_AND_FINISH = "extra_go_home_and_finish"

        /**
         * Đưa thiết bị về Màn hình chính (Home) từ dưới nền mà KHÔNG cần Trợ năng (Accessibility):
         * Dùng một cửa sổ neo 1x1 pixel trong suốt trong 350ms (nếu đã có quyền Overlay) để Android
         * BackgroundActivityStartController xác thực cửa sổ hiển thị hợp lệ, rồi mở CATEGORY_HOME.
         * -> Ép TikTok / Douyin / Facebook rơi ngay vào onPause()/onStop(), dừng phát video 100%!
         */
        fun launchHomeScreenFromBackground(context: Context) {
            val appCtx = context.applicationContext
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
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

            val homePi = PendingIntent.getActivity(
                appCtx,
                1097,
                homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Settings.canDrawOverlays(appCtx)) {
                Handler(Looper.getMainLooper()).post {
                    val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    val anchorView = View(appCtx).apply { setBackgroundColor(Color.TRANSPARENT) }
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    val params = WindowManager.LayoutParams(
                        1,
                        1,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }
                    runCatching { wm?.addView(anchorView, params) }

                    dispatchStartHome(appCtx, homeIntent, homePi, optionsBundle)

                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching { wm?.removeView(anchorView) }
                    }, 350L)
                }
            } else {
                dispatchStartHome(appCtx, homeIntent, homePi, optionsBundle)
            }
        }

        private fun dispatchStartHome(
            appCtx: Context,
            homeIntent: Intent,
            homePi: PendingIntent,
            optionsBundle: Bundle?
        ) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 34 && optionsBundle != null) {
                    homePi.send(appCtx, 0, homeIntent, null, null, null, optionsBundle)
                } else {
                    homePi.send()
                }
            }
            runCatching {
                if (optionsBundle != null) {
                    appCtx.startActivity(homeIntent, optionsBundle)
                } else {
                    appCtx.startActivity(homeIntent)
                }
            }
        }

        @Volatile
        private var appLevelScreenOffReceiver: BroadcastReceiver? = null

        /**
         * Tắt màn hình tự nhiên Chuẩn Ngân Hàng KHÔNG mở bất kỳ màn hình đen giả nào:
         * 1. Thoát TikTok/Facebook/YouTube ra Màn hình chính thật (dừng video & nhả cờ giữ sáng).
         * 2. Hạ thời gian chờ tắt màn hình (SCREEN_OFF_TIMEOUT) xuống mức tối thiểu ngay ngoài Màn hình chính
         *    -> Máy tự tắt & khóa Lock Screen phần cứng sau vài giây, giữ nguyên Vân tay / Khuôn mặt!
         * 3. Lắng nghe ACTION_SCREEN_OFF cấp Application để tự trả lại timeout ban đầu ngay khi màn hình tắt.
         */
        fun triggerNaturalHomeScreenLock(context: Context) {
            val appCtx = context.applicationContext
            launchHomeScreenFromBackground(appCtx)
            // Mở kèm BlackoutSleepActivity ở chế độ cầu nối 100ms để chắc chắn vượt qua màn hình TikTok đang ở Foreground,
            // đẩy TikTok xuống nền rồi tự động chuyển về Màn hình chính Home và đóng ngay lập tức!
            launchBridgeToHome(appCtx)

            val mainHandler = Handler(Looper.getMainLooper())
            appLevelScreenOffReceiver?.let { existing ->
                runCatching { appCtx.unregisterReceiver(existing) }
                appLevelScreenOffReceiver = null
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                        restoreOriginalScreenTimeout(appCtx)
                        runCatching { appCtx.unregisterReceiver(this) }
                        if (appLevelScreenOffReceiver === this) {
                            appLevelScreenOffReceiver = null
                        }
                    }
                }
            }
            appLevelScreenOffReceiver = receiver
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appCtx.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    appCtx.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
                }
            }

            // Áp dụng timeout thấp nhất sau khi đã về Home (500ms) để hệ thống không reset lại idle timer
            mainHandler.postDelayed({
                val appliedMs = applyMinimumScreenTimeout(appCtx)
                val waitSec = ((appliedMs + 999) / 1000).coerceAtLeast(1)
                if (appliedMs > 0) {
                    android.widget.Toast.makeText(
                        appCtx,
                        "⏳ Đã thoát về Màn hình chính! Máy sẽ tự tắt sau ${waitSec}s (Giữ Vân tay)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                val safetyRestoreMs = (appliedMs.toLong() + 25_000L).coerceAtLeast(45_000L)
                mainHandler.postDelayed({
                    restoreOriginalScreenTimeout(appCtx)
                    appLevelScreenOffReceiver?.let { r ->
                        runCatching { appCtx.unregisterReceiver(r) }
                        if (appLevelScreenOffReceiver === r) {
                            appLevelScreenOffReceiver = null
                        }
                    }
                }, safetyRestoreMs)
            }, 500L)
        }

        private fun launchBridgeToHome(appCtx: Context) {
            val intent = Intent(appCtx, BlackoutSleepActivity::class.java).apply {
                putExtra(EXTRA_GO_HOME_AND_FINISH, true)
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
                1096,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching {
                if (Build.VERSION.SDK_INT >= 34 && optionsBundle != null) {
                    pi.send(appCtx, 0, intent, null, null, null, optionsBundle)
                } else {
                    pi.send()
                }
            }
            runCatching {
                if (optionsBundle != null) appCtx.startActivity(intent, optionsBundle)
                else appCtx.startActivity(intent)
            }
            postFullScreenBlackoutFallback(appCtx, pi)
        }

        fun launch(context: Context) {
            val appCtx = context.applicationContext
            applyMinimumScreenTimeout(appCtx)

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

            runCatching {
                if (Build.VERSION.SDK_INT >= 34 && optionsBundle != null) {
                    pi.send(appCtx, 0, intent, null, null, null, optionsBundle)
                } else {
                    pi.send()
                }
            }
            runCatching {
                if (optionsBundle != null) {
                    appCtx.startActivity(intent, optionsBundle)
                } else {
                    appCtx.startActivity(intent)
                }
            }.onFailure {
                Log.w(TAG, "Direct startActivity failed: ${it.message}")
            }

            postFullScreenBlackoutFallback(appCtx, pi)
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
                    .setContentText("Chạm 1 lần để hủy hoặc chờ vài giây máy tự khóa")
                    .setFullScreenIntent(fullScreenPi, true)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ALARM)

                nm.notify(FULLSCREEN_NOTIF_ID, builder.build())
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching { nm.cancel(FULLSCREEN_NOTIF_ID) }
                }, 4000L)
            }
        }

        fun applyMinimumScreenTimeout(context: Context): Int {
            if (!Settings.System.canWrite(context)) return -1
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
                // Thử lần lượt từ 1s -> 5s -> 10s -> 15s -> 30s vì Vivo OriginOS từ chối số quá nhỏ (< 15s)
                val candidates = intArrayOf(MIN_TIMEOUT_MS, 5_000, 10_000, 15_000, 30_000)
                for (candidate in candidates) {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        candidate
                    )
                    val actual = Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        currentTimeout
                    )
                    if (actual <= candidate + 1000) {
                        Log.i(TAG, "Applied SCREEN_OFF_TIMEOUT = ${actual}ms (candidate $candidate)")
                        return actual
                    }
                }
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to lower SCREEN_OFF_TIMEOUT: ${e.message}")
                -1
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

        if (intent?.getBooleanExtra(EXTRA_GO_HOME_AND_FINISH, false) == true) {
            // Đã chiếm quyền Foreground khỏi TikTok -> Đẩy về Màn hình chính Home thật rồi đóng ngay!
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            runCatching { startActivity(homeIntent) }
            finishAndRemoveTask()
            return
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val params = window.attributes
        params.screenBrightness = 0.01f
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
                text = "Đang tắt màn hình...\nChạm 1 lần vào màn hình để hủy & quay lại"
                setTextColor(Color.argb(115, 255, 255, 255))
                textSize = 13f
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

            // Chạm 1 lần duy nhất ở bất kỳ đâu để hủy tắt màn hình và thoát ngay, tuyệt đối không gây kẹt màn hình!
            setOnClickListener {
                restoreOriginalScreenTimeout(applicationContext)
                finishAndRemoveTask()
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
            restoreOriginalScreenTimeout(applicationContext)
            if (!isFinishing && !isDestroyed) {
                finishAndRemoveTask()
            }
        }, 20_000L)
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
