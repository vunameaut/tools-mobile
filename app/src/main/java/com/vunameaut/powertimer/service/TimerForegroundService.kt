package com.vunameaut.powertimer.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.widget.RemoteViews
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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

        const val EXTRA_SECONDS = "EXTRA_SECONDS"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_FADE_AUDIO = "EXTRA_FADE_AUDIO"
        const val EXTRA_DND = "EXTRA_DND"
        const val EXTRA_WARNING = "EXTRA_WARNING"
        const val EXTRA_SHAKE = "EXTRA_SHAKE"

        private val _timerFlow = MutableStateFlow(TimerState())
        val timerFlow = _timerFlow.asStateFlow()

        fun startTimer(
            context: Context,
            seconds: Long,
            mode: ShutdownMode,
            fadeAudio: Boolean = true,
            enableDnd: Boolean = true,
            playWarning: Boolean = true,
            shakeToExtend: Boolean = true
        ) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_FADE_AUDIO, fadeAudio)
                putExtra(EXTRA_DND, enableDnd)
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
            context.startService(intent)
        }

        fun addTenMinutes(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_ADD_10_MIN
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shakeDetector: ShakeDetector? = null
    private lateinit var shutdownManager: ShutdownManager
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        shutdownManager = ShutdownManager(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PowerTimer:ServiceWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        shakeDetector?.stop()
        cancelExactAlarm()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
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
                val enableDnd = intent.getBooleanExtra(EXTRA_DND, true)
                val playWarning = intent.getBooleanExtra(EXTRA_WARNING, true)
                val shakeToExtend = intent.getBooleanExtra(EXTRA_SHAKE, true)

                startCountdown(seconds, mode, fadeAudio, enableDnd, playWarning, shakeToExtend)
            }
            ACTION_STOP -> {
                stopCountdown()
            }
            ACTION_ADD_10_MIN -> {
                handleExtendTenMinutes()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleExtendTenMinutes() {
        val current = _timerFlow.value
        if (current.isRunning) {
            val newTotal = current.totalSeconds + 600
            val newRemaining = current.remainingSeconds + 600
            _timerFlow.value = current.copy(
                totalSeconds = newTotal,
                remainingSeconds = newRemaining
            )
            scheduleExactAlarm(newRemaining, current.selectedMode)
            updateNotification(newRemaining, current.selectedMode)
            SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
            ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
            vibrateShortBuzz()
            Toast.makeText(applicationContext, "Đã cộng thêm 10 phút!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCountdown(
        seconds: Long,
        mode: ShutdownMode,
        fadeAudio: Boolean,
        enableDnd: Boolean,
        playWarning: Boolean,
        shakeToExtend: Boolean
    ) {
        timerJob?.cancel()
        shakeDetector?.stop()

        _timerFlow.value = TimerState(
            isRunning = true,
            totalSeconds = seconds,
            remainingSeconds = seconds,
            selectedMode = mode,
            fadeAudio = fadeAudio,
            turnOnDnd = enableDnd,
            playWarningSound = playWarning,
            shakeToExtend = shakeToExtend
        )

        startForeground(NOTIFICATION_ID, buildNotification(seconds, mode))
        scheduleExactAlarm(seconds, mode)
        SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
        ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)

        if (shakeToExtend) {
            shakeDetector = ShakeDetector(this) {
                handleExtendTenMinutes()
            }
            shakeDetector?.start()
        }

        timerJob = serviceScope.launch {
            var remaining = seconds
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                _timerFlow.value = _timerFlow.value.copy(remainingSeconds = remaining)
                SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)

                // Cảnh báo rung trước 30 giây
                if (remaining == 30L && playWarning) {
                    vibrateWarning()
                }

                // Cập nhật notification & widget định kỳ
                if (remaining % 5L == 0L || remaining <= 60L) {
                    updateNotification(remaining, mode)
                    ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
                }
            }

            if (remaining <= 0) {
                onCountdownFinished(mode, fadeAudio, enableDnd)
            }
        }
    }

    private fun stopCountdown() {
        timerJob?.cancel()
        shakeDetector?.stop()
        cancelExactAlarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun onCountdownFinished(mode: ShutdownMode, fadeAudio: Boolean, enableDnd: Boolean) {
        Log.i("TimerService", "Countdown finished. Locking screen and stopping media...")
        shutdownManager.executeShutdown(mode, fadeAudio, enableDnd)
        stopCountdown()
    }

    private fun scheduleExactAlarm(seconds: Long, mode: ShutdownMode) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + (seconds * 1000L)

        val intent = Intent(this, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_EXPIRED
            putExtra(TimerAlarmReceiver.EXTRA_SHUTDOWN_MODE, mode.name)
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
        val openAppIntent = Intent(this, MainActivity::class.java)
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

        val totalSeconds = _timerFlow.value.totalSeconds.coerceAtLeast(1L)
        val progress = ((remainingSeconds * 100) / totalSeconds).toInt().coerceIn(0, 100)

        // Đồng hồ đếm ngược do hệ thống tự vẽ mỗi giây (không tốn pin cập nhật notification)
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
