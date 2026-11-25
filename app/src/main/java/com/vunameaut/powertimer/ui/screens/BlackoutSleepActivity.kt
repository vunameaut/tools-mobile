package com.vunameaut.powertimer.ui.screens

import android.app.Activity
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.manager.ScreenTimeoutStateMachine
import com.vunameaut.powertimer.receiver.DeviceAdminSampleReceiver
import com.vunameaut.powertimer.service.PowerAccessibilityService
import com.vunameaut.powertimer.service.TimerForegroundService

/**
 * Điều phối thoát về Màn hình chính, thực thi AlarmClock exemption sau 30p–120p chạy nền,
 * và Màn đen OLED 0% tiết kiệm pin tuyệt đối nếu app khác cố giữ sáng màn hình:
 * - TUYỆT ĐỐI KHÔNG gây kẹt cảm ứng: chạm 1 lần vào bất kỳ đâu là hủy và thoát ngay.
 * - Tận dụng `AlarmManager.setAlarmClock` trỏ tới Activity để được miễn trừ BAL Android 14/15
 *   sau 30 phút không chạm vào app.
 */
class BlackoutSleepActivity : Activity() {

    companion object {
        private const val TAG = "BlackoutSleepActivity"
        private const val FULLSCREEN_CHANNEL_ID = "blackout_sleep_channel"
        private const val FULLSCREEN_NOTIF_ID = 1099
        private const val EXTRA_GO_HOME_AND_FINISH = "extra_go_home_and_finish"
        const val EXTRA_FROM_ALARM_CLOCK = "extra_from_alarm_clock"
        const val EXTRA_SHUTDOWN_MODE = "extra_shutdown_mode"
        const val EXTRA_FADE_AUDIO = "extra_fade_audio"
        const val EXTRA_OLED_BATTERY_GUARD = "extra_oled_battery_guard"

        fun createAlarmClockPendingIntent(
            context: Context,
            mode: ShutdownMode,
            fadeAudio: Boolean
        ): PendingIntent {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, BlackoutSleepActivity::class.java).apply {
                putExtra(EXTRA_FROM_ALARM_CLOCK, true)
                putExtra(EXTRA_SHUTDOWN_MODE, mode.name)
                putExtra(EXTRA_FADE_AUDIO, fadeAudio)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            return PendingIntent.getActivity(
                appCtx,
                1095,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Đưa thiết bị về Màn hình chính (Home) từ dưới nền mà KHÔNG cần Trợ năng (Accessibility):
         * Dùng cửa sổ neo 2x2 px alpha siêu nhỏ chờ 65ms để WindowManagerService ghi nhận
         * hasVisibleWindow() == true, rồi mở cầu nối về CATEGORY_HOME.
         */
        fun launchHomeScreenFromBackground(context: Context) {
            val appCtx = context.applicationContext
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            val optionsBundle = buildAllowBalBundle()
            val homePi = PendingIntent.getActivity(
                appCtx,
                1097,
                homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Settings.canDrawOverlays(appCtx)) {
                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post {
                    val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    val anchorView = View(appCtx).apply { setBackgroundColor(Color.argb(3, 0, 0, 0)) }
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    val params = WindowManager.LayoutParams(
                        2,
                        2,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }
                    runCatching { wm?.addView(anchorView, params) }

                    // Chờ 65ms cho WindowManagerService gắn frame đầu tiên trước khi gọi startActivity
                    mainHandler.postDelayed({
                        launchBridgeActivityDirect(appCtx, optionsBundle)
                        dispatchStartHome(appCtx, homeIntent, homePi, optionsBundle)
                    }, 65L)

                    mainHandler.postDelayed({
                        runCatching { wm?.removeView(anchorView) }
                    }, 650L)
                }
            } else {
                launchBridgeActivityDirect(appCtx, optionsBundle)
                dispatchStartHome(appCtx, homeIntent, homePi, optionsBundle)
            }
        }

        private fun buildAllowBalBundle(): Bundle? {
            return if (Build.VERSION.SDK_INT >= 34) {
                runCatching {
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                        .toBundle()
                }.getOrNull()
            } else null
        }

        private fun launchBridgeActivityDirect(appCtx: Context, optionsBundle: Bundle?) {
            val intent = Intent(appCtx, BlackoutSleepActivity::class.java).apply {
                putExtra(EXTRA_GO_HOME_AND_FINISH, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
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

        /**
         * Tắt màn hình tự nhiên Chuẩn Ngân Hàng (khi Service/Enforcer đã quản lý vòng đời khôi phục):
         * 1. Thoát app video ra Màn hình chính thật (CATEGORY_HOME + bridge).
         * 2. Hạ thời gian chờ tắt màn hình xuống mức tối thiểu ngay ngoài Home -> máy tự khóa & giữ nguyên Vân tay.
         */
        fun triggerNaturalHomeScreenLock(context: Context): Int {
            val appCtx = context.applicationContext
            launchHomeScreenFromBackground(appCtx)

            val appliedNow = applyMinimumScreenTimeout(appCtx)
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.postDelayed({
                val appliedMs = applyMinimumScreenTimeout(appCtx)
                val waitSec = ((appliedMs + 999) / 1000).coerceAtLeast(1)
                if (appliedMs > 0) {
                    android.widget.Toast.makeText(
                        appCtx,
                        "⏳ Đã thoát về Màn hình chính! Máy sẽ tự tắt sau ${waitSec}s (Giữ Vân tay)",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }, 450L)
            return appliedNow
        }

        /**
         * Lớp bảo vệ pin tuyệt đối OLED 0% (chỉ bật khi ứng dụng khác giữ sáng màn hình quá hạn
         * hoặc chưa cấp WRITE_SETTINGS): che toàn bộ bằng màu đen #000000 ở độ sáng 0%, xóa FLAG_KEEP_SCREEN_ON
         * để máy tự tắt phần cứng hoặc tiêu thụ điện gần bằng 0 thay vì sáng trắng cả đêm.
         */
        fun launchOledBatteryGuard(context: Context) {
            val appCtx = context.applicationContext
            applyMinimumScreenTimeout(appCtx)
            val intent = Intent(appCtx, BlackoutSleepActivity::class.java).apply {
                putExtra(EXTRA_OLED_BATTERY_GUARD, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            val optionsBundle = buildAllowBalBundle()
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
                if (optionsBundle != null) appCtx.startActivity(intent, optionsBundle)
                else appCtx.startActivity(intent)
            }.onFailure {
                Log.w(TAG, "Direct OLED Guard startActivity failed: ${it.message}")
            }
            postFullScreenBlackoutFallback(appCtx, pi)
        }

        fun launch(context: Context) {
            launchOledBatteryGuard(context)
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
                    .setContentTitle("Đang tắt màn hình & bảo vệ pin...")
                    .setContentText("Chạm để mở nếu muốn hủy tắt màn hình")
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
                val currentTimeout = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    60_000
                )
                val actual = ScreenTimeoutStateMachine.from(context).applyMinimumTimeout(currentTimeout) { candidate ->
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        candidate
                    )
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        currentTimeout
                    )
                }
                Log.i(TAG, "Applied SCREEN_OFF_TIMEOUT = ${actual}ms")
                actual
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to lower SCREEN_OFF_TIMEOUT: ${e.message}")
                -1
            }
        }

        fun restoreOriginalScreenTimeout(context: Context) {
            if (!Settings.System.canWrite(context)) return
            try {
                val current = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    60_000
                )
                val restored = ScreenTimeoutStateMachine.from(context).restoreIfModified(current) { target ->
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        target
                    )
                }
                if (restored != null) {
                    Log.i(TAG, "Restored original SCREEN_OFF_TIMEOUT to ${restored}ms")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to restore SCREEN_OFF_TIMEOUT: ${e.message}")
            }
        }
    }

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

        if (handleAlarmClockLaunchIfNeeded(intent)) {
            return
        }

        if (intent?.getBooleanExtra(EXTRA_GO_HOME_AND_FINISH, false) == true) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            runCatching { startActivity(homeIntent) }
            finishAndRemoveTask()
            return
        }

        renderOledBatteryGuardUi()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent == null) return
        if (handleAlarmClockLaunchIfNeeded(intent)) {
            return
        }
        if (intent.getBooleanExtra(EXTRA_GO_HOME_AND_FINISH, false)) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            runCatching { startActivity(homeIntent) }
            finishAndRemoveTask()
        }
    }

    /**
     * Xử lý luồng thức dậy trực tiếp từ `AlarmManager.setAlarmClock` sau 30p–120p:
     * Vì Activity này được `system_server` bật với đặc quyền `BAL_ALLOW_ALARM_CLOCK`,
     * nó có toàn quyền Foreground để đẩy TikTok/YouTube/Facebook về Màn hình chính Home ngay lập tức!
     */
    private fun handleAlarmClockLaunchIfNeeded(launchIntent: Intent?): Boolean {
        if (launchIntent?.getBooleanExtra(EXTRA_FROM_ALARM_CLOCK, false) != true) return false

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true
        val modeName = launchIntent.getStringExtra(EXTRA_SHUTDOWN_MODE)
        val mode = runCatching {
            if (modeName != null) ShutdownMode.valueOf(modeName) else ShutdownMode.BANK_SAFE_SLEEP
        }.getOrDefault(ShutdownMode.BANK_SAFE_SLEEP)
        val fadeAudio = launchIntent.getBooleanExtra(EXTRA_FADE_AUDIO, true)

        if (!isInteractive) {
            TimerForegroundService.notifyAlarmClockTriggered(
                applicationContext,
                mode,
                fadeAudio,
                wasInteractive = false
            )
            finishAndRemoveTask()
            return true
        }

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isAdminActive = dpm?.isAdminActive(ComponentName(this, DeviceAdminSampleReceiver::class.java)) ?: false
        val hasAcc = PowerAccessibilityService.instance != null
        val canWrite = Settings.System.canWrite(this)

        // Nếu chọn Tắt Chuẩn Ngân Hàng nhưng chưa cấp bất kỳ quyền khóa nào -> Ở lại Màn Đen OLED 0% bảo vệ pin ngay!
        if (mode == ShutdownMode.BANK_SAFE_SLEEP && !canWrite && !isAdminActive && !hasAcc) {
            TimerForegroundService.notifyAlarmClockTriggered(
                applicationContext,
                mode,
                fadeAudio,
                wasInteractive = true
            )
            renderOledBatteryGuardUi()
            return true
        }

        // Từ Activity đang ở Foreground, gọi thẳng CATEGORY_HOME (100% thành công, không bao giờ bị BAL chặn!)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        runCatching { startActivity(homeIntent) }

        if (mode == ShutdownMode.BANK_SAFE_SLEEP && canWrite) {
            applyMinimumScreenTimeout(applicationContext)
        }

        TimerForegroundService.notifyAlarmClockTriggered(
            applicationContext,
            mode,
            fadeAudio,
            wasInteractive = true
        )
        finishAndRemoveTask()
        return true
    }

    private fun renderOledBatteryGuardUi() {
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
                text = "🌙 Đã hết giờ hẹn • Màn hình sẽ tự tắt\nChạm 1 lần vào bất kỳ đâu để hủy"
                setTextColor(Color.argb(65, 255, 255, 255))
                textSize = 12.5f
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

            // Chạm 1 lần duy nhất ở bất kỳ đâu để hủy tắt màn hình & dừng Enforcer ngay lập tức!
            setOnClickListener {
                TimerForegroundService.cancelEnforcementByUser(applicationContext)
                restoreOriginalScreenTimeout(applicationContext)
                finishAndRemoveTask()
            }
        }
        setContentView(root)
        hideSystemUI(root)

        if (!receiverRegistered) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
                }
                receiverRegistered = true
            }
        }
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
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            receiverRegistered = false
        }
    }
}
