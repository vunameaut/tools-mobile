package com.vunameaut.powertimer.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.data.model.TimerState
import com.vunameaut.powertimer.manager.ShakeDetector
import com.vunameaut.powertimer.manager.ShutdownManager
import com.vunameaut.powertimer.receiver.TimerAlarmReceiver
import com.vunameaut.powertimer.widget.ToolsHubAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "power_timer_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_ADD_10_MIN = "ACTION_ADD_10_MIN"
        const val ACTION_LOCK_NOW = "ACTION_LOCK_NOW"

        const val EXTRA_SECONDS = "EXTRA_SECONDS"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_FADE_AUDIO = "EXTRA_FADE_AUDIO"
        const val EXTRA_WARNING = "EXTRA_WARNING"
        const val EXTRA_SHAKE = "EXTRA_SHAKE"

        private val _timerFlow = MutableStateFlow(TimerState())
        val timerFlow = _timerFlow.asStateFlow()

        fun startTimer(
            context: Context,
            seconds: Long,
            mode: ShutdownMode,
            fadeAudio: Boolean = true,
            playWarning: Boolean = true,
            shakeToExtend: Boolean = true
        ) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_FADE_AUDIO, fadeAudio)
                putExtra(EXTRA_WARNING, playWarning)
                putExtra(EXTRA_SHAKE, shakeToExtend)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(intent)
            }.onFailure {
                context.stopService(Intent(context, TimerForegroundService::class.java))
            }
        }

        fun addTenMinutes(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_ADD_10_MIN
            }
            runCatching { context.startService(intent) }
        }

        fun updatePresetSeconds(context: Context, seconds: Long, mode: ShutdownMode? = null) {
            val current = _timerFlow.value
            if (!current.isRunning) {
                _timerFlow.value = current.copy(
                    totalSeconds = seconds,
                    remainingSeconds = seconds,
                    selectedMode = mode ?: current.selectedMode
                )
                SystemDynamicIslandManager.refreshOverlay(context.applicationContext, _timerFlow.value)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shakeDetector: ShakeDetector? = null
    private lateinit var shutdownManager: ShutdownManager
    private lateinit var notificationManager: NotificationManager

    // Lưu mốc thời gian thực kết thúc hẹn giờ (Epoch ms) để không bao giờ bị đứng thời gian khi CPU vào Deep Sleep
    @Volatile
    private var targetEndEpochMs: Long = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (_timerFlow.value.isRunning && targetEndEpochMs > 0L) {
                        val now = System.currentTimeMillis()
                        val remaining = ((targetEndEpochMs - now + 999L) / 1000L).coerceAtLeast(0L)
                        if (remaining <= 0L) {
                            // Hẹn giờ đã hết từ lúc màn hình tắt trong đêm -> Dừng sạch sẽ ngay khi mở máy, không khóa lại màn hình
                            stopCountdown()
                        } else {
                            // Đồng bộ lại ngay số giây thực tế còn lại trên màn hình
                            _timerFlow.value = _timerFlow.value.copy(remainingSeconds = remaining)
                            NativeMediaIslandBridge.startOrUpdate(
                                applicationContext,
                                remaining,
                                _timerFlow.value.totalSeconds,
                                _timerFlow.value.selectedMode
                            )
                            updateNotification(remaining, _timerFlow.value.selectedMode)
                            SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
                            ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        shutdownManager = ShutdownManager(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PowerTimer:ServiceWakeLock")

        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenStateReceiver, filter)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        shakeDetector?.stop()
        cancelExactAlarm()
        NativeMediaIslandBridge.stop()
        runCatching { unregisterReceiver(screenStateReceiver) }
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        targetEndEpochMs = 0L
        _timerFlow.value = TimerState()
        SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
        ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getLongExtra(EXTRA_SECONDS, 1800L)
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: ShutdownMode.BANK_SAFE_SLEEP.name
                val mode = runCatching { ShutdownMode.valueOf(modeName) }.getOrDefault(ShutdownMode.BANK_SAFE_SLEEP)
                val fadeAudio = intent.getBooleanExtra(EXTRA_FADE_AUDIO, true)
                val playWarning = intent.getBooleanExtra(EXTRA_WARNING, true)
                val shakeToExtend = intent.getBooleanExtra(EXTRA_SHAKE, true)

                startCountdown(seconds, mode, fadeAudio, playWarning, shakeToExtend)
            }
            ACTION_STOP -> {
                stopCountdown()
            }
            ACTION_ADD_10_MIN -> {
                handleExtendTenMinutes()
            }
            ACTION_LOCK_NOW -> {
                val current = _timerFlow.value
                val mode = current.selectedMode
                stopCountdown()
                serviceScope.launch {
                    shutdownManager.executeShutdown(mode, fadeAudio = false)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun handleExtendTenMinutes() {
        val current = _timerFlow.value
        if (current.isRunning) {
            val now = System.currentTimeMillis()
            val currentRealRemaining = if (targetEndEpochMs > now) {
                ((targetEndEpochMs - now + 999L) / 1000L)
            } else {
                current.remainingSeconds
            }
            val newTotal = current.totalSeconds + 600L
            val newRemaining = currentRealRemaining + 600L
            targetEndEpochMs = now + newRemaining * 1000L

            _timerFlow.value = current.copy(
                totalSeconds = newTotal,
                remainingSeconds = newRemaining
            )
            acquireTimedWakeLock(newRemaining)
            NativeMediaIslandBridge.startOrUpdate(applicationContext, newRemaining, newTotal, current.selectedMode)
            scheduleExactAlarm(newRemaining, current.selectedMode, current.fadeAudio)
            updateNotification(newRemaining, current.selectedMode)
            SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
            ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
            vibrateShortBuzz()
            Toast.makeText(applicationContext, "Đã cộng thêm 10 phút!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun acquireTimedWakeLock(seconds: Long) {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock?.acquire((seconds + 60L) * 1000L)
        }
    }

    private fun startCountdown(
        seconds: Long,
        mode: ShutdownMode,
        fadeAudio: Boolean,
        playWarning: Boolean,
        shakeToExtend: Boolean
    ) {
        timerJob?.cancel()
        shakeDetector?.stop()

        targetEndEpochMs = System.currentTimeMillis() + seconds * 1000L
        acquireTimedWakeLock(seconds)

        _timerFlow.value = TimerState(
            isRunning = true,
            totalSeconds = seconds,
            remainingSeconds = seconds,
            selectedMode = mode,
            fadeAudio = fadeAudio,
            playWarningSound = playWarning,
            shakeToExtend = shakeToExtend
        )

        NativeMediaIslandBridge.startOrUpdate(applicationContext, seconds, seconds, mode)
        startForeground(NOTIFICATION_ID, buildNotification(seconds, mode))
        scheduleExactAlarm(seconds, mode, fadeAudio)
        SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
        ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)

        if (shakeToExtend) {
            shakeDetector = ShakeDetector(this) {
                handleExtendTenMinutes()
            }
            shakeDetector?.start()
        }

        timerJob = serviceScope.launch {
            var warnedAt30s = false
            var lastNotifSec = seconds

            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                // Luôn tính số giây còn lại dựa trên mốc thời gian thực (System.currentTimeMillis),
                // khắc phục triệt để lỗi đồng hồ bị đứng ở 50 phút khi CPU vào chế độ ngủ sâu (Deep Sleep/Doze) ban đêm!
                val remaining = ((targetEndEpochMs - now + 999L) / 1000L).coerceAtLeast(0L)
                _timerFlow.value = _timerFlow.value.copy(remainingSeconds = remaining)

                if (remaining <= 0L) {
                    break
                }

                // Chỉ cập nhật Dynamic Island khi màn hình đang bật để tiết kiệm pin và không giữ tài nguyên ngầm
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm?.isInteractive != false) {
                    NativeMediaIslandBridge.startOrUpdate(
                        applicationContext,
                        remaining,
                        _timerFlow.value.totalSeconds,
                        mode
                    )
                    SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
                }

                // Cảnh báo rung trước 30 giây (chỉ khi màn hình đang sáng)
                if (remaining <= 30L && !warnedAt30s && playWarning && pm?.isInteractive != false) {
                    warnedAt30s = true
                    vibrateWarning()
                }

                // Cập nhật notification & widget định kỳ khi màn hình đang sáng
                if (remaining != lastNotifSec && (remaining % 5L == 0L || remaining <= 60L) && pm?.isInteractive != false) {
                    lastNotifSec = remaining
                    updateNotification(remaining, mode)
                    ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
                }
            }

            if (isActive) {
                onCountdownFinished(mode, fadeAudio)
            }
        }
    }

    private fun stopCountdown() {
        targetEndEpochMs = 0L
        timerJob?.cancel()
        shakeDetector?.stop()
        cancelExactAlarm()
        NativeMediaIslandBridge.stop()
        if (wakeLock?.isHeld == true) {
            runCatching { wakeLock?.release() }
        }
        _timerFlow.value = TimerState()
        SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
        ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun onCountdownFinished(mode: ShutdownMode, fadeAudio: Boolean) {
        Log.i("TimerService", "Countdown finished. Locking screen and stopping media...")
        // Dừng ngay MediaIsland và hủy hẹn giờ trước khi khóa màn hình để không giữ bất kỳ phiên âm thanh nào
        NativeMediaIslandBridge.stop()
        cancelExactAlarm()
        shutdownManager.executeShutdown(mode, fadeAudio)
        stopCountdown()
    }

    private fun scheduleExactAlarm(
        seconds: Long,
        mode: ShutdownMode,
        fadeAudio: Boolean = true
    ) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + (seconds * 1000L)

        val intent = Intent(this, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_EXPIRED
            putExtra(TimerAlarmReceiver.EXTRA_SHUTDOWN_MODE, mode.name)
            putExtra(TimerAlarmReceiver.EXTRA_FADE_AUDIO, fadeAudio)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelExactAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_EXPIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun vibrateWarning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 300, 200, 300), -1)
            }
        }
    }

    private fun vibrateShortBuzz() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(remainingSeconds: Long, mode: ShutdownMode): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("OPEN_TOOL_ID", "power_timer")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimerForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val add10mIntent = Intent(this, TimerForegroundService::class.java).apply { action = ACTION_ADD_10_MIN }
        val add10mPendingIntent = PendingIntent.getService(
            this,
            2,
            add10mIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lockNowIntent = Intent(this, TimerForegroundService::class.java).apply { action = ACTION_LOCK_NOW }
        val lockNowPendingIntent = PendingIntent.getService(
            this,
            3,
            lockNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val totalSeconds = _timerFlow.value.totalSeconds.coerceAtLeast(1L)
        val formatted = _timerFlow.value.formattedRemaining.ifBlank { "30:00" }
        val artwork = NativeMediaIslandBridge.getCurrentArtwork(this, remainingSeconds, totalSeconds)
        val sessionToken = NativeMediaIslandBridge.getSessionToken(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && sessionToken != null) {
            val mediaStyle = Notification.MediaStyle()
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(0, 1, 2)

            val builder = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setLargeIcon(artwork)
                .setColor(0xFF22C55E.toInt())
                .setContentTitle("⏳ Còn $formatted • Hẹn giờ tắt màn")
                .setContentText("${mode.badge}  •  Bấm ⏭ để +10 phút")
                .setSubText("Tools Hub")
                .setStyle(mediaStyle)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis() + remainingSeconds * 1000L)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_stat_add,
                        "+10 phút",
                        add10mPendingIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_stat_timer,
                        "Tắt màn",
                        lockNowPendingIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_stat_stop,
                        "Dừng",
                        stopPendingIntent
                    ).build()
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            return builder.build()
        }

        val progress = ((remainingSeconds * 100) / totalSeconds).toInt().coerceIn(0, 100)
        val views = RemoteViews(packageName, R.layout.notification_timer).apply {
            setTextViewText(R.id.notif_title, "Hẹn giờ tắt màn hình")
            setTextViewText(R.id.notif_subtitle, "${mode.badge}  •  Lắc máy để +10 phút")
            setChronometerCountDown(R.id.notif_chrono, true)
            setChronometer(
                R.id.notif_chrono,
                SystemClock.elapsedRealtime() + remainingSeconds * 1000L,
                null,
                true
            )
            setProgressBar(R.id.notif_progress, 100, progress, false)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(0xFF22D3EE.toInt())
            .setContentTitle("Hẹn giờ tắt màn hình")
            .setContentText("Chế độ: ${mode.badge}")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stat_add, "+10 phút", add10mPendingIntent)
            .addAction(R.drawable.ic_stat_stop, "Dừng lại", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(remainingSeconds: Long, mode: ShutdownMode) {
        val notification = buildNotification(remainingSeconds, mode)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
