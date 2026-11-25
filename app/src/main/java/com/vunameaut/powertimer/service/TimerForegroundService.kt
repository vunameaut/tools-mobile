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
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.data.model.TimerState
import com.vunameaut.powertimer.manager.PersistedTimerConfig
import com.vunameaut.powertimer.manager.PollDecision
import com.vunameaut.powertimer.manager.RecoveryDecision
import com.vunameaut.powertimer.manager.ShakeDetector
import com.vunameaut.powertimer.manager.ShutdownEnforcementPolicy
import com.vunameaut.powertimer.manager.ShutdownManager
import com.vunameaut.powertimer.manager.TimerPersistenceManager
import com.vunameaut.powertimer.receiver.TimerAlarmReceiver
import com.vunameaut.powertimer.ui.screens.BlackoutSleepActivity
import com.vunameaut.powertimer.widget.ToolsHubAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class TimerForegroundService : Service() {

    companion object {
        private const val TAG = "TimerService"
        const val CHANNEL_ID = "power_timer_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_ADD_10_MIN = "ACTION_ADD_10_MIN"
        const val ACTION_LOCK_NOW = "ACTION_LOCK_NOW"
        const val ACTION_ALARM_CLOCK_TRIGGERED = "ACTION_ALARM_CLOCK_TRIGGERED"
        const val ACTION_ALARM_FALLBACK_EXPIRED = "ACTION_ALARM_FALLBACK_EXPIRED"
        const val ACTION_CANCEL_ENFORCEMENT = "ACTION_CANCEL_ENFORCEMENT"
        const val ACTION_REVIVE_PERSISTED = "ACTION_REVIVE_PERSISTED"

        const val EXTRA_SECONDS = "EXTRA_SECONDS"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_FADE_AUDIO = "EXTRA_FADE_AUDIO"
        const val EXTRA_WARNING = "EXTRA_WARNING"
        const val EXTRA_SHAKE = "EXTRA_SHAKE"
        const val EXTRA_WAS_INTERACTIVE = "EXTRA_WAS_INTERACTIVE"

        private val _timerFlow = MutableStateFlow(TimerState())
        val timerFlow = _timerFlow.asStateFlow()

        fun startTimer(
            context: Context,
            seconds: Long,
            mode: ShutdownMode,
            fadeAudio: Boolean = true,
            playWarning: Boolean = true,
            shakeToExtend: Boolean = false
        ) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_FADE_AUDIO, fadeAudio)
                putExtra(EXTRA_WARNING, playWarning)
                putExtra(EXTRA_SHAKE, shakeToExtend)
            }
            startForegroundCompat(context, intent)
        }

        fun stopTimer(context: Context) {
            TimerPersistenceManager.from(context).clearActiveTimer()
            TimerPersistenceManager.from(context).clearShutdownEnforcing()
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

        fun notifyAlarmClockTriggered(
            context: Context,
            mode: ShutdownMode,
            fadeAudio: Boolean,
            wasInteractive: Boolean
        ) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_ALARM_CLOCK_TRIGGERED
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_FADE_AUDIO, fadeAudio)
                putExtra(EXTRA_WAS_INTERACTIVE, wasInteractive)
            }
            startForegroundCompat(context, intent)
        }

        fun triggerFallbackExpiry(
            context: Context,
            mode: ShutdownMode,
            fadeAudio: Boolean
        ) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_ALARM_FALLBACK_EXPIRED
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_FADE_AUDIO, fadeAudio)
            }
            startForegroundCompat(context, intent)
        }

        fun cancelEnforcementByUser(context: Context) {
            TimerPersistenceManager.from(context).clearActiveTimer()
            TimerPersistenceManager.from(context).clearShutdownEnforcing()
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_CANCEL_ENFORCEMENT
            }
            runCatching { context.startService(intent) }
        }

        fun reviveIfPersistedTimerActive(context: Context) {
            val persistence = TimerPersistenceManager.from(context)
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isInteractive = pm?.isInteractive ?: true
            when (persistence.decideRecoveryAction(System.currentTimeMillis(), isInteractive)) {
                is RecoveryDecision.ResumeCountdown,
                is RecoveryDecision.ExecuteImmediateShutdown -> {
                    val intent = Intent(context, TimerForegroundService::class.java).apply {
                        action = ACTION_REVIVE_PERSISTED
                    }
                    startForegroundCompat(context, intent)
                }
                is RecoveryDecision.SilentCleanupStaleOffScreen -> {
                    persistence.clearActiveTimer()
                    persistence.clearShutdownEnforcing()
                    BlackoutSleepActivity.restoreOriginalScreenTimeout(context)
                }
                RecoveryDecision.None -> Unit
            }
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

        private fun startForegroundCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { context.startForegroundService(intent) }
                    .onFailure { context.startService(intent) }
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var enforcementJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shakeDetector: ShakeDetector? = null
    private lateinit var shutdownManager: ShutdownManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var persistence: TimerPersistenceManager

    private val enforcementStarted = AtomicBoolean(false)
    private var foregroundStarted = false

    @Volatile
    private var targetEndEpochMs: Long = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (enforcementStarted.get()) {
                        shutdownManager.restoreTimeoutIfModified()
                    } else {
                        // Người dùng tự tắt màn hình trước khi hết giờ -> nhả ngay WakeLock để tiết kiệm pin 0%!
                        releaseWakeLockSafely()
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (enforcementStarted.get()) return
                    if (_timerFlow.value.isRunning && targetEndEpochMs > 0L) {
                        val now = System.currentTimeMillis()
                        val remaining = TimerPersistenceManager.computeRemainingSeconds(now, targetEndEpochMs)
                        val overdueMs = now - targetEndEpochMs
                        if (remaining <= 0L) {
                            if (overdueMs > TimerPersistenceManager.STALE_OFF_SCREEN_GRACE_MS) {
                                // Hẹn giờ đã hết từ lâu khi màn hình đang tắt trong đêm -> dọn sạch khi sáng mở máy, không khóa lại!
                                stopCountdown()
                            }
                        } else {
                            renewCountdownWakeLock()
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
        persistence = TimerPersistenceManager.from(applicationContext)
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PowerTimer:ServiceWakeLock").apply {
            setReferenceCounted(false)
        }

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
        enforcementJob?.cancel()
        shakeDetector?.stop()
        NativeMediaIslandBridge.stop()
        runCatching { unregisterReceiver(screenStateReceiver) }
        releaseWakeLockSafely()
        targetEndEpochMs = 0L
        foregroundStarted = false
        _timerFlow.value = TimerState()
        SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
        ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_REVIVE_PERSISTED) {
            handleServiceRevive()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val seconds = intent.getLongExtra(EXTRA_SECONDS, 1800L)
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: ShutdownMode.BANK_SAFE_SLEEP.name
                val mode = runCatching { ShutdownMode.valueOf(modeName) }.getOrDefault(ShutdownMode.BANK_SAFE_SLEEP)
                val fadeAudio = intent.getBooleanExtra(EXTRA_FADE_AUDIO, true)
                val playWarning = intent.getBooleanExtra(EXTRA_WARNING, true)
                val shakeToExtend = intent.getBooleanExtra(EXTRA_SHAKE, false)

                startCountdown(seconds, mode, fadeAudio, playWarning, shakeToExtend)
            }
            ACTION_STOP, ACTION_CANCEL_ENFORCEMENT -> {
                shutdownManager.restoreTimeoutIfModified()
                shutdownManager.releaseHeldAudioFocus()
                stopCountdown()
            }
            ACTION_ADD_10_MIN -> {
                handleExtendTenMinutes()
            }
            ACTION_LOCK_NOW -> {
                val mode = _timerFlow.value.selectedMode
                ensureForegroundReady(0L, mode)
                launchShutdownEnforcement(mode, fadeAudio = false)
            }
            ACTION_ALARM_CLOCK_TRIGGERED, ACTION_ALARM_FALLBACK_EXPIRED -> {
                val modeName = intent.getStringExtra(EXTRA_MODE)
                val fallbackMode = persistence.loadActiveTimer()?.mode ?: _timerFlow.value.selectedMode
                val mode = runCatching {
                    if (modeName != null) ShutdownMode.valueOf(modeName) else fallbackMode
                }.getOrDefault(fallbackMode)
                val fadeAudio = intent.getBooleanExtra(EXTRA_FADE_AUDIO, true)
                ensureForegroundReady(0L, mode)
                launchShutdownEnforcement(mode, fadeAudio)
            }
        }
        return START_STICKY
    }

    private fun handleServiceRevive() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true
        when (val decision = persistence.decideRecoveryAction(System.currentTimeMillis(), isInteractive)) {
            is RecoveryDecision.ResumeCountdown -> {
                val cfg = decision.config
                startCountdown(
                    seconds = decision.remainingSeconds,
                    mode = cfg.mode,
                    fadeAudio = cfg.fadeAudio,
                    playWarning = cfg.playWarning,
                    shakeToExtend = cfg.shakeToExtend,
                    persistedTotalSeconds = cfg.totalSeconds,
                    targetEpochOverride = cfg.targetEndEpochMs
                )
            }
            is RecoveryDecision.ExecuteImmediateShutdown -> {
                ensureForegroundReady(0L, decision.config.mode)
                launchShutdownEnforcement(decision.config.mode, decision.config.fadeAudio)
            }
            is RecoveryDecision.SilentCleanupStaleOffScreen,
            RecoveryDecision.None -> {
                ensureForegroundReady(0L, ShutdownMode.BANK_SAFE_SLEEP)
                stopCountdown()
            }
        }
    }

    private fun handleExtendTenMinutes() {
        val current = _timerFlow.value
        if (current.isRunning && !enforcementStarted.get()) {
            val now = System.currentTimeMillis()
            val currentRealRemaining = if (targetEndEpochMs > now) {
                TimerPersistenceManager.computeRemainingSeconds(now, targetEndEpochMs)
            } else {
                current.remainingSeconds
            }
            val newTotal = current.totalSeconds + 600L
            val newRemaining = currentRealRemaining + 600L
            targetEndEpochMs = now + newRemaining * 1000L

            persistence.saveActiveTimer(
                PersistedTimerConfig(
                    totalSeconds = newTotal,
                    targetEndEpochMs = targetEndEpochMs,
                    mode = current.selectedMode,
                    fadeAudio = current.fadeAudio,
                    playWarning = current.playWarningSound,
                    shakeToExtend = current.shakeToExtend
                )
            )

            _timerFlow.value = current.copy(
                totalSeconds = newTotal,
                remainingSeconds = newRemaining
            )
            renewCountdownWakeLock()
            NativeMediaIslandBridge.startOrUpdate(applicationContext, newRemaining, newTotal, current.selectedMode)
            scheduleExactAlarm(targetEndEpochMs, current.selectedMode, current.fadeAudio)
            updateNotification(newRemaining, current.selectedMode)
            SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
            ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
            vibrateShortBuzz()
            Toast.makeText(applicationContext, "Đã cộng thêm 10 phút!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renewCountdownWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isInteractive != false) {
                wakeLock?.acquire(120_000L)
            }
        }
    }

    private fun releaseWakeLockSafely() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private fun ensureForegroundReady(remainingSeconds: Long, mode: ShutdownMode) {
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification(remainingSeconds, mode))
            foregroundStarted = true
        }
    }

    private fun startCountdown(
        seconds: Long,
        mode: ShutdownMode,
        fadeAudio: Boolean,
        playWarning: Boolean,
        shakeToExtend: Boolean,
        persistedTotalSeconds: Long = seconds,
        targetEpochOverride: Long? = null
    ) {
        timerJob?.cancel()
        enforcementJob?.cancel()
        enforcementStarted.set(false)
        persistence.clearShutdownEnforcing()
        shakeDetector?.stop()

        val now = System.currentTimeMillis()
        targetEndEpochMs = targetEpochOverride ?: (now + seconds * 1000L)
        persistence.saveActiveTimer(
            PersistedTimerConfig(
                totalSeconds = persistedTotalSeconds,
                targetEndEpochMs = targetEndEpochMs,
                mode = mode,
                fadeAudio = fadeAudio,
                playWarning = playWarning,
                shakeToExtend = shakeToExtend
            )
        )
        renewCountdownWakeLock()

        _timerFlow.value = TimerState(
            isRunning = true,
            totalSeconds = persistedTotalSeconds,
            remainingSeconds = seconds,
            selectedMode = mode,
            fadeAudio = fadeAudio,
            playWarningSound = playWarning,
            shakeToExtend = shakeToExtend
        )

        NativeMediaIslandBridge.startOrUpdate(applicationContext, seconds, persistedTotalSeconds, mode)
        startForeground(NOTIFICATION_ID, buildNotification(seconds, mode))
        foregroundStarted = true
        scheduleExactAlarm(targetEndEpochMs, mode, fadeAudio)
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
            var loopTicks = 0L

            while (isActive) {
                delay(1000L)
                loopTicks++
                val tickNow = System.currentTimeMillis()
                val remaining = TimerPersistenceManager.computeRemainingSeconds(tickNow, targetEndEpochMs)
                _timerFlow.value = _timerFlow.value.copy(remainingSeconds = remaining)

                if (remaining <= 0L) {
                    break
                }

                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isInteractive = pm?.isInteractive != false

                if (isInteractive && loopTicks % 60L == 0L) {
                    renewCountdownWakeLock()
                }

                if (isInteractive) {
                    NativeMediaIslandBridge.startOrUpdate(
                        applicationContext,
                        remaining,
                        _timerFlow.value.totalSeconds,
                        mode
                    )
                }

                if (remaining <= 30L && !warnedAt30s && playWarning && isInteractive) {
                    warnedAt30s = true
                    vibrateWarning()
                }

                if (TimerPersistenceManager.shouldUpdateNotification(remaining, lastNotifSec, isInteractive)) {
                    lastNotifSec = remaining
                    updateNotification(remaining, mode)
                    ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
                }
            }

            if (isActive && !enforcementStarted.get()) {
                // Chờ tối đa 1.2s để AlarmManager.setAlarmClock (quyền BAL Activity của system_server) kích hoạt trước
                var waitedMs = 0L
                while (waitedMs < 1_200L && !enforcementStarted.get() && isActive) {
                    delay(100L)
                    waitedMs += 100L
                }
                if (isActive && !enforcementStarted.get()) {
                    launchShutdownEnforcement(mode, fadeAudio)
                }
            }
        }
    }

    private fun stopCountdown() {
        targetEndEpochMs = 0L
        timerJob?.cancel()
        enforcementJob?.cancel()
        enforcementStarted.set(false)
        shakeDetector?.stop()
        cancelExactAlarm()
        NativeMediaIslandBridge.stop()
        persistence.clearActiveTimer()
        persistence.clearShutdownEnforcing()
        releaseWakeLockSafely()
        _timerFlow.value = TimerState()
        SystemDynamicIslandManager.refreshOverlay(applicationContext, _timerFlow.value)
        ToolsHubAppWidgetProvider.updateAllWidgets(applicationContext)
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun launchShutdownEnforcement(mode: ShutdownMode, fadeAudio: Boolean) {
        if (!enforcementStarted.compareAndSet(false, true)) {
            Log.i(TAG, "Shutdown enforcement already running, ignoring duplicate trigger.")
            return
        }

        timerJob?.cancel()
        shakeDetector?.stop()
        cancelExactAlarm()
        NativeMediaIslandBridge.stop()
        persistence.clearActiveTimer()
        persistence.markShutdownEnforcing(
            System.currentTimeMillis() + ShutdownEnforcementPolicy.FULL_LOCK_MAX_FOCUS_HOLD_MS
        )

        enforcementJob = serviceScope.launch {
            runCatching {
                wakeLock?.acquire(45_000L)
            }
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val initialInteractive = pm?.isInteractive ?: true

            when (ShutdownEnforcementPolicy.decideInitialAction(initialInteractive, mode)) {
                com.vunameaut.powertimer.manager.InitialEnforcementAction.PAUSE_MEDIA_SILENT_STOP_WHEN_SCREEN_ALREADY_OFF -> {
                    shutdownManager.executeShutdownWithResult(mode, fadeAudio, holdFocusUntilScreenOff = false)
                    shutdownManager.restoreTimeoutIfModified()
                    shutdownManager.releaseHeldAudioFocus()
                    stopCountdown()
                    return@launch
                }
                com.vunameaut.powertimer.manager.InitialEnforcementAction.EXECUTE_SHUTDOWN_AND_HOLD_FOCUS -> {
                    val result = shutdownManager.executeShutdownWithResult(
                        mode = mode,
                        fadeAudio = fadeAudio,
                        holdFocusUntilScreenOff = (mode != ShutdownMode.MEDIA_ONLY)
                    )
                    var appliedTimeoutMs = result.appliedTimeoutMs
                    var oledGuardLaunched = result.engagedOledGuard

                    var elapsedMs = 0L
                    while (isActive) {
                        delay(1_000L)
                        elapsedMs += 1_000L

                        if (appliedTimeoutMs <= 0 && mode == ShutdownMode.BANK_SAFE_SLEEP) {
                            appliedTimeoutMs = runCatching {
                                Settings.System.getInt(
                                    contentResolver,
                                    Settings.System.SCREEN_OFF_TIMEOUT,
                                    15_000
                                )
                            }.getOrDefault(15_000)
                        }

                        val currentlyInteractive = pm?.isInteractive ?: false
                        when (
                            ShutdownEnforcementPolicy.evaluatePollStep(
                                elapsedSinceStartMs = elapsedMs,
                                isScreenInteractive = currentlyInteractive,
                                mode = mode,
                                appliedTimeoutMs = appliedTimeoutMs,
                                oledGuardLaunched = oledGuardLaunched
                            )
                        ) {
                            PollDecision.RESTORE_AND_STOP_ON_SCREEN_OFF -> {
                                shutdownManager.restoreTimeoutIfModified()
                                delay(1_000L)
                                shutdownManager.releaseHeldAudioFocus()
                                stopCountdown()
                                return@launch
                            }
                            PollDecision.COMPLETE_MEDIA_ONLY -> {
                                stopCountdown()
                                return@launch
                            }
                            PollDecision.ENGAGE_OLED_BATTERY_GUARD -> {
                                oledGuardLaunched = true
                                BlackoutSleepActivity.launchOledBatteryGuard(applicationContext)
                                delay(1_500L)
                                releaseWakeLockSafely()
                            }
                            PollDecision.REASSERT_MEDIA_AND_TIMEOUT -> {
                                shutdownManager.reassertMediaAndTimeout(mode)
                            }
                            PollDecision.HARD_TIMEOUT_RELEASE -> {
                                if (!oledGuardLaunched && mode != ShutdownMode.MEDIA_ONLY) {
                                    BlackoutSleepActivity.launchOledBatteryGuard(applicationContext)
                                }
                                shutdownManager.releaseHeldAudioFocus()
                                stopCountdown()
                                return@launch
                            }
                            PollDecision.CONTINUE_WAITING -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun scheduleExactAlarm(
        triggerAtMillis: Long,
        mode: ShutdownMode,
        fadeAudio: Boolean = true
    ) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val showAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("OPEN_TOOL_ID", "power_timer")
        }
        val showAppPi = PendingIntent.getActivity(
            this,
            10,
            showAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Chính: setAlarmClock trỏ tới BlackoutSleepActivity -> system_server cấp miễn trừ BAL Activity sau 30p–120p!
        val alarmClockActivityPi = BlackoutSleepActivity.createAlarmClockPendingIntent(
            context = this,
            mode = mode,
            fadeAudio = fadeAudio
        )
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showAppPi),
                alarmClockActivityPi
            )
        } catch (_: Throwable) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmClockActivityPi)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmClockActivityPi)
                }
            } catch (_: Throwable) {
            }
        }

        // Dự phòng cấp 2 (+2s): BroadcastReceiver chuyển thẳng về Foreground Service nếu Activity alarm bị trễ
        val backupReceiverPi = buildBackupReceiverPendingIntent(mode, fadeAudio)
        val backupAtMillis = triggerAtMillis + 2_000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, backupAtMillis, backupReceiverPi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, backupAtMillis, backupReceiverPi)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, backupAtMillis, backupReceiverPi)
        }
    }

    private fun buildBackupReceiverPendingIntent(mode: ShutdownMode, fadeAudio: Boolean): PendingIntent {
        val intent = Intent(this, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_TIMER_EXPIRED
            putExtra(TimerAlarmReceiver.EXTRA_SHUTDOWN_MODE, mode.name)
            putExtra(TimerAlarmReceiver.EXTRA_FADE_AUDIO, fadeAudio)
        }
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelExactAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val activityPi = BlackoutSleepActivity.createAlarmClockPendingIntent(
            context = this,
            mode = _timerFlow.value.selectedMode,
            fadeAudio = _timerFlow.value.fadeAudio
        )
        val receiverPi = buildBackupReceiverPendingIntent(
            _timerFlow.value.selectedMode,
            _timerFlow.value.fadeAudio
        )
        runCatching { alarmManager.cancel(activityPi) }
        runCatching { alarmManager.cancel(receiverPi) }
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
            setTextViewText(R.id.notif_subtitle, "${mode.badge}  •  Chạm để mở")
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
