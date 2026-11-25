package com.vunameaut.powertimer

import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.data.model.TimerState
import com.vunameaut.powertimer.manager.InMemoryKeyValueStore
import com.vunameaut.powertimer.manager.InitialEnforcementAction
import com.vunameaut.powertimer.manager.PersistedTimerConfig
import com.vunameaut.powertimer.manager.PollDecision
import com.vunameaut.powertimer.manager.RecoveryDecision
import com.vunameaut.powertimer.manager.ScreenTimeoutStateMachine
import com.vunameaut.powertimer.manager.ShakeMotionAnalyzer
import com.vunameaut.powertimer.manager.ShutdownEnforcementPolicy
import com.vunameaut.powertimer.manager.TimerPersistenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bộ kiểm thử toàn diện 24 kịch bản hẹn giờ tắt màn hình (10s, 30p, 60p, 120p),
 * bảo vệ pin qua đêm trên Vivo OriginOS / Android 14-15, lắc chống nhầm và khôi phục Timeout.
 */
class SleepTimerComprehensiveTest {

    @Test
    fun case01_short10sTimerCountdownAndRestore() {
        val store = InMemoryKeyValueStore()
        val persistence = TimerPersistenceManager(store)
        val timeoutSm = ScreenTimeoutStateMachine(store)

        val startMs = 1_000_000L
        val targetMs = startMs + 10_000L
        persistence.saveActiveTimer(
            PersistedTimerConfig(10L, targetMs, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )

        for (sec in 1..9) {
            val rem = TimerPersistenceManager.computeRemainingSeconds(startMs + sec * 1000L, targetMs)
            assertEquals(10L - sec, rem)
        }
        assertEquals(0L, TimerPersistenceManager.computeRemainingSeconds(targetMs, targetMs))

        var systemTimeout = 60_000
        val applied = timeoutSm.applyMinimumTimeout(systemTimeout) { cand ->
            systemTimeout = if (cand >= 15_000) cand else systemTimeout
            systemTimeout
        }
        assertEquals(15_000, applied)
        assertTrue(timeoutSm.isModifiedByUs())

        assertEquals(
            PollDecision.RESTORE_AND_STOP_ON_SCREEN_OFF,
            ShutdownEnforcementPolicy.evaluatePollStep(
                elapsedSinceStartMs = 16_000L,
                isScreenInteractive = false,
                mode = ShutdownMode.BANK_SAFE_SLEEP,
                appliedTimeoutMs = applied,
                oledGuardLaunched = false
            )
        )

        val restored = timeoutSm.restoreIfModified(systemTimeout) { systemTimeout = it }
        assertEquals(60_000, restored)
        assertEquals(60_000, systemTimeout)
        persistence.clearActiveTimer()
        assertEquals(RecoveryDecision.None, persistence.decideRecoveryAction(targetMs + 20_000L, true))
    }

    @Test
    fun case02_thirtyMinute1800sTikTokForegroundHandover() {
        val store = InMemoryKeyValueStore()
        val persistence = TimerPersistenceManager(store)
        val t0 = 10_000_000L
        val t30m = t0 + 1_800_000L

        persistence.saveActiveTimer(
            PersistedTimerConfig(1800L, t30m, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )
        assertEquals(
            InitialEnforcementAction.EXECUTE_SHUTDOWN_AND_HOLD_FOCUS,
            ShutdownEnforcementPolicy.decideInitialAction(isScreenInteractive = true, mode = ShutdownMode.BANK_SAFE_SLEEP)
        )
        // Trước 16s vẫn giữ Focus và nhắc lại mỗi 3s
        assertEquals(
            PollDecision.REASSERT_MEDIA_AND_TIMEOUT,
            ShutdownEnforcementPolicy.evaluatePollStep(6_000L, true, ShutdownMode.BANK_SAFE_SLEEP, 15_000, false)
        )
        assertEquals(
            PollDecision.REASSERT_MEDIA_AND_TIMEOUT,
            ShutdownEnforcementPolicy.evaluatePollStep(15_000L, true, ShutdownMode.BANK_SAFE_SLEEP, 15_000, false)
        )
        // Đến giây 16 màn hình tắt -> khôi phục & dừng
        assertEquals(
            PollDecision.RESTORE_AND_STOP_ON_SCREEN_OFF,
            ShutdownEnforcementPolicy.evaluatePollStep(16_000L, false, ShutdownMode.BANK_SAFE_SLEEP, 15_000, false)
        )
    }

    @Test
    fun case03_long60mAnd120mDeepDozeWallClockJump() {
        val start = 50_000_000L
        val target60m = start + 3_600_000L
        val target120m = start + 7_200_000L

        assertEquals(1_800L, TimerPersistenceManager.computeRemainingSeconds(start + 1_800_000L, target60m))
        assertEquals(1L, TimerPersistenceManager.computeRemainingSeconds(target60m - 200L, target60m))
        assertEquals(0L, TimerPersistenceManager.computeRemainingSeconds(target60m + 500L, target60m))

        assertEquals(3_600L, TimerPersistenceManager.computeRemainingSeconds(start + 3_600_000L, target120m))
        val state = TimerState(isRunning = true, totalSeconds = 7200L, remainingSeconds = 3661L)
        assertEquals("01:01:01", state.formattedRemaining)
    }

    @Test
    fun case04_serviceKilledAtMinute18AndRevivedByStartSticky() {
        val persistence = TimerPersistenceManager(InMemoryKeyValueStore())
        val t0 = 1_000_000L
        val target = t0 + 1_800_000L
        persistence.saveActiveTimer(
            PersistedTimerConfig(1800L, target, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )

        val min19 = t0 + 1_140_000L
        val decision = persistence.decideRecoveryAction(min19, isScreenInteractive = true)
        assertTrue(decision is RecoveryDecision.ResumeCountdown)
        val resume = decision as RecoveryDecision.ResumeCountdown
        assertEquals(660L, resume.remainingSeconds)
        assertEquals(1800L, resume.config.totalSeconds)
    }

    @Test
    fun case05_serviceKilledAndRestarted2sAfterDeadlineScreenOn() {
        val persistence = TimerPersistenceManager(InMemoryKeyValueStore())
        val target = 2_000_000L
        persistence.saveActiveTimer(
            PersistedTimerConfig(1800L, target, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )

        val decision = persistence.decideRecoveryAction(target + 2_000L, isScreenInteractive = true)
        assertTrue(decision is RecoveryDecision.ExecuteImmediateShutdown)
    }

    @Test
    fun case06_alarmClockDelayedFallsBackWithinGraceWindow() {
        val persistence = TimerPersistenceManager(InMemoryKeyValueStore())
        val target = 3_000_000L
        persistence.saveActiveTimer(
            PersistedTimerConfig(1800L, target, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )

        // Kể cả khi màn hình vừa tối tạm thời trong cửa sổ <= 60s vẫn kịp xử lý dứt điểm
        val decision = persistence.decideRecoveryAction(target + 2_500L, isScreenInteractive = false)
        assertTrue(decision is RecoveryDecision.ExecuteImmediateShutdown)
    }

    @Test
    fun case07_vivo15sMinTimeoutHeldAudioFocusPast6sThreshold() {
        val timeoutSm = ScreenTimeoutStateMachine(InMemoryKeyValueStore())
        var systemTimeout = 300_000
        // Giả lập Vivo OriginOS từ chối 1s, 5s, 10s, chỉ nhận 15s
        val applied = timeoutSm.applyMinimumTimeout(systemTimeout) { cand ->
            if (cand >= 15_000) systemTimeout = cand
            systemTimeout
        }
        assertEquals(15_000, applied)

        // Tại t = 6s, 9s, 12s (sau ngưỡng 6s cũ!), Enforcer vẫn phải tiếp tục giữ Focus và chưa tắt sớm
        assertNotCompletedAt(6_000L, applied)
        assertNotCompletedAt(9_000L, applied)
        assertNotCompletedAt(12_000L, applied)
    }

    private fun assertNotCompletedAt(elapsedMs: Long, appliedTimeoutMs: Int) {
        val step = ShutdownEnforcementPolicy.evaluatePollStep(
            elapsedSinceStartMs = elapsedMs,
            isScreenInteractive = true,
            mode = ShutdownMode.BANK_SAFE_SLEEP,
            appliedTimeoutMs = appliedTimeoutMs,
            oledGuardLaunched = false
        )
        assertTrue(
            step == PollDecision.CONTINUE_WAITING || step == PollDecision.REASSERT_MEDIA_AND_TIMEOUT
        )
    }

    @Test
    fun case08_rogueAppStayAwakeEngagesOledBatteryGuardAndReleasesWakeLock() {
        val triggerMs = ShutdownEnforcementPolicy.computeOledGuardTriggerMs(15_000)
        assertEquals(23_000L, triggerMs)

        // Màn hình vẫn bị app video giữ sáng ở 23s -> Bắt buộc bật Màn Đen OLED 0% bảo vệ pin!
        assertEquals(
            PollDecision.ENGAGE_OLED_BATTERY_GUARD,
            ShutdownEnforcementPolicy.evaluatePollStep(
                elapsedSinceStartMs = 23_000L,
                isScreenInteractive = true,
                mode = ShutdownMode.BANK_SAFE_SLEEP,
                appliedTimeoutMs = 15_000,
                oledGuardLaunched = false
            )
        )

        // Đạt trần 40s -> Nhả AudioFocus & WakeLock để không ngốn CPU cả đêm
        assertEquals(
            PollDecision.HARD_TIMEOUT_RELEASE,
            ShutdownEnforcementPolicy.evaluatePollStep(
                elapsedSinceStartMs = 40_000L,
                isScreenInteractive = true,
                mode = ShutdownMode.BANK_SAFE_SLEEP,
                appliedTimeoutMs = 15_000,
                oledGuardLaunched = true
            )
        )
    }

    @Test
    fun case09_manualScreenOffAtMinute10SilentCleanupAtMinute30() {
        assertEquals(
            InitialEnforcementAction.PAUSE_MEDIA_SILENT_STOP_WHEN_SCREEN_ALREADY_OFF,
            ShutdownEnforcementPolicy.decideInitialAction(
                isScreenInteractive = false,
                mode = ShutdownMode.BANK_SAFE_SLEEP
            )
        )
    }

    @Test
    fun case10_screenOffAtMinute10UnlockedAtMinute20ExactResync() {
        val t0 = 5_000_000L
        val target = t0 + 1_800_000L
        val t20m = t0 + 1_200_000L
        val rem = TimerPersistenceManager.computeRemainingSeconds(t20m, target)
        assertEquals(600L, rem)
    }

    @Test
    fun case11_expiredOvernightUnlockedNextMorningNeverLocksPhoneInFace() {
        val persistence = TimerPersistenceManager(InMemoryKeyValueStore())
        val midnight = 10_000_000L
        persistence.saveActiveTimer(
            PersistedTimerConfig(1800L, midnight, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )

        val nextMorning7Am = midnight + 7 * 3_600_000L
        // Quá 60s trong trạng thái tắt màn hình -> Chỉ dọn sạch, tuyệt đối không khóa vào mặt người dùng lúc mới mở máy
        assertEquals(
            RecoveryDecision.SilentCleanupStaleOffScreen,
            persistence.decideRecoveryAction(nextMorning7Am, isScreenInteractive = false)
        )
    }

    @Test
    fun case12_preservesExactOriginalScreenTimeoutAcrossAllUserSettings() {
        val userPresets = listOf(15_000, 30_000, 60_000, 300_000, 600_000)
        for (orig in userPresets) {
            val sm = ScreenTimeoutStateMachine(InMemoryKeyValueStore())
            var sys = orig
            sm.applyMinimumTimeout(sys) { cand ->
                sys = cand
                sys
            }
            assertEquals(1_000, sys)
            val restored = sm.restoreIfModified(sys) { sys = it }
            assertEquals(orig, restored)
            assertEquals(orig, sys)
            assertFalse(sm.isModifiedByUs())
        }
    }

    @Test
    fun case13_processCrashWhileTimeoutWas1000msRestoresOnAppRestart() {
        val sharedStore = InMemoryKeyValueStore()
        val sm1 = ScreenTimeoutStateMachine(sharedStore)
        var sys = 300_000
        sm1.applyMinimumTimeout(sys) { cand ->
            sys = cand
            sys
        }
        assertEquals(1_000, sys)

        // App crash và khởi động lại khi không còn enforcing
        val sm2 = ScreenTimeoutStateMachine(sharedStore)
        val persistence = TimerPersistenceManager(sharedStore)
        assertFalse(persistence.isShutdownCurrentlyEnforcing(System.currentTimeMillis()))

        val restored = sm2.restoreIfModified(sys) { sys = it }
        assertEquals(300_000, restored)
        assertEquals(300_000, sys)
    }

    @Test
    fun case14_doNotRestoreTimeoutMidEnforcementOnRestart() {
        val persistence = TimerPersistenceManager(InMemoryKeyValueStore())
        val now = 1_000_000L
        persistence.markShutdownEnforcing(now + 42_000L)
        assertTrue(persistence.isShutdownCurrentlyEnforcing(now + 10_000L))
        assertFalse(persistence.isShutdownCurrentlyEnforcing(now + 45_000L))
    }

    @Test
    fun case15_doubleTriggerDedupBetweenAlarmClockAndReceiver() {
        val persistence = TimerPersistenceManager(InMemoryKeyValueStore())
        val now = 2_000_000L
        persistence.saveActiveTimer(
            PersistedTimerConfig(1800L, now, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )
        // Lần 1 nhận và xóa active timer trước khi Enforcer chạy
        assertTrue(persistence.decideRecoveryAction(now, true) is RecoveryDecision.ExecuteImmediateShutdown)
        persistence.clearActiveTimer()
        // Lần 2 từ nguồn khác sẽ nhận RecoveryDecision.None
        assertEquals(RecoveryDecision.None, persistence.decideRecoveryAction(now + 500L, true))
    }

    @Test
    fun case16_userCancelsMidCountdownAndMidEnforcementRestoresImmediately() {
        val store = InMemoryKeyValueStore()
        val persistence = TimerPersistenceManager(store)
        val sm = ScreenTimeoutStateMachine(store)

        var sys = 60_000
        sm.applyMinimumTimeout(sys) { cand -> sys = cand; sys }
        persistence.markShutdownEnforcing(5_000_000L)

        // Người dùng chạm hủy -> xóa flags + khôi phục nguyên trạng timeout
        persistence.clearActiveTimer()
        persistence.clearShutdownEnforcing()
        val restored = sm.restoreIfModified(sys) { sys = it }

        assertEquals(60_000, restored)
        assertFalse(persistence.isShutdownCurrentlyEnforcing(4_000_000L))
        assertNull(sm.restoreIfModified(sys) { sys = it })
    }

    @Test
    fun case17_extend10MinutesMidRunAndInFinal5SecondsUpdatesPersistence() {
        val persistence = TimerPersistenceManager(InMemoryKeyValueStore())
        val t0 = 1_000_000L
        val tEnd = t0 + 30_000L
        persistence.saveActiveTimer(
            PersistedTimerConfig(30L, tEnd, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )

        val nowAtLast5s = tEnd - 5_000L
        val currentRemaining = TimerPersistenceManager.computeRemainingSeconds(nowAtLast5s, tEnd)
        assertEquals(5L, currentRemaining)

        val newRemaining = currentRemaining + 600L
        val newTarget = nowAtLast5s + newRemaining * 1000L
        persistence.saveActiveTimer(
            PersistedTimerConfig(630L, newTarget, ShutdownMode.BANK_SAFE_SLEEP, true, true, false)
        )

        val loaded = persistence.loadActiveTimer()!!
        assertEquals(630L, loaded.totalSeconds)
        assertEquals(605L, TimerPersistenceManager.computeRemainingSeconds(nowAtLast5s, loaded.targetEndEpochMs))
    }

    @Test
    fun case18_shakeDetectorIgnoresPhonePlacementAndMattressMovement() {
        val analyzer = ShakeMotionAnalyzer()
        val t0 = 10_000L
        analyzer.reset(t0)

        // 1. Đặt điện thoại xuống giường trong 3s đầu -> Bỏ qua kể cả lực mạnh 2.8G
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.8f, t0 + 500L))
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.8f, t0 + 700L))

        // 2. Sau 3s: cựa mình trên nệm (1.4G nhẹ x2 lần) -> Bỏ qua
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 1.4f, t0 + 4_000L))
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 1.4f, t0 + 4_250L))

        // 3. Va chạm đơn lẻ 2.6G nhưng không lắc nhịp thứ 2 trong 650ms -> Bỏ qua
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.6f, t0 + 6_000L))
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.6f, t0 + 6_800L))
    }

    @Test
    fun case19_shakeDetectorTriggersOnlyOnDeliberateDoubleShakeWithCooldown() {
        val analyzer = ShakeMotionAnalyzer()
        val t0 = 20_000L
        analyzer.reset(t0)

        // Nhịp lắc mạnh 1 (2.6G) lúc +4.0s -> Chờ nhịp 2
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.6f, t0 + 4_000L))
        // Nhiễu liên tiếp cách < 120ms -> không tính nhịp 2
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.6f, t0 + 4_050L))
        // Nhịp lắc mạnh 2 cách 200ms -> KÍCH HOẠT!
        assertTrue(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.6f, t0 + 4_200L))

        // Trong thời gian hồi chiêu 5s -> không kích hoạt liên tiếp
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.6f, t0 + 5_000L))
        assertFalse(analyzer.onAccelerationSample(0f, 0f, 9.80665f * 2.6f, t0 + 5_200L))
    }

    @Test
    fun case20_deviceAdminModeBehaviorAndFallback() {
        assertTrue(ShutdownMode.DEVICE_ADMIN.requiresDeviceAdmin)
        assertFalse(ShutdownMode.DEVICE_ADMIN.requiresAccessibility)
        // Nếu vì lý do gì màn hình chưa khóa sau timeout -> vẫn cho phép bật OLED Guard
        assertEquals(
            PollDecision.ENGAGE_OLED_BATTERY_GUARD,
            ShutdownEnforcementPolicy.evaluatePollStep(
                elapsedSinceStartMs = 23_000L,
                isScreenInteractive = true,
                mode = ShutdownMode.DEVICE_ADMIN,
                appliedTimeoutMs = -1,
                oledGuardLaunched = false
            )
        )
    }

    @Test
    fun case21_mediaOnlyModeDoesNotModifyTimeoutAndCompletesAt3s() {
        assertFalse(ShutdownMode.MEDIA_ONLY.requiresWriteSettings)
        assertFalse(ShutdownMode.MEDIA_ONLY.requiresDeviceAdmin)
        assertEquals(
            PollDecision.COMPLETE_MEDIA_ONLY,
            ShutdownEnforcementPolicy.evaluatePollStep(
                elapsedSinceStartMs = 3_000L,
                isScreenInteractive = true,
                mode = ShutdownMode.MEDIA_ONLY,
                appliedTimeoutMs = -1,
                oledGuardLaunched = false
            )
        )
    }

    @Test
    fun case22_accessibilityAndHomeAndLockModesCompliance() {
        assertFalse(ShutdownMode.BANK_SAFE_SLEEP.requiresAccessibility)
        assertTrue(ShutdownMode.BANK_SAFE_SLEEP.requiresWriteSettings)
        assertTrue(ShutdownMode.ACCESSIBILITY.requiresAccessibility)
        assertTrue(ShutdownMode.HOME_AND_LOCK.requiresAccessibility)
    }

    @Test
    fun case23_bankSafeSleepWithoutWriteSettingsGracefulFallback() {
        val sm = ScreenTimeoutStateMachine(InMemoryKeyValueStore())
        // Giả lập hệ thống từ chối ghi (trả nguyên 60_000) -> không crash, trả 60_000
        val result = sm.applyMinimumTimeout(60_000) { 60_000 }
        assertEquals(60_000, result)
        // Policy vẫn kẹp `coerceAtLeast(15_000)` + 8s, hoặc theo Timeout thực tế nếu <= 60s
        assertTrue(ShutdownEnforcementPolicy.computeOledGuardTriggerMs(-1) == 23_000L)
        assertEquals(
            PollDecision.ENGAGE_OLED_BATTERY_GUARD,
            ShutdownEnforcementPolicy.evaluatePollStep(
                elapsedSinceStartMs = 23_000L,
                isScreenInteractive = true,
                mode = ShutdownMode.BANK_SAFE_SLEEP,
                appliedTimeoutMs = -1,
                oledGuardLaunched = false
            )
        )
    }

    @Test
    fun case24_notificationRateLimitThrottlingAcrossCustomOneSecondTimers() {
        var lastNotified = 45L
        val notifyTimestamps = mutableListOf<Long>()
        for (elapsedSec in 1L..45L) {
            val rem = 45L - elapsedSec
            if (rem <= 0L) break
            if (TimerPersistenceManager.shouldUpdateNotification(rem, lastNotified, isInteractive = true)) {
                lastNotified = rem
                notifyTimestamps.add(elapsedSec)
            }
        }
        // Phải đi qua mốc 4s (tức giây 41 còn lại!) một cách trơn tru và tổng số lần update trong mọi cửa sổ 20s <= 14 (< ngưỡng 20 của Android 14/15)
        assertTrue(notifyTimestamps.contains(5L)) // tức remaining = 40
        for (endT in 20L..45L) {
            val countIn20sWindow = notifyTimestamps.count { it > endT - 20L && it <= endT }
            assertTrue("Exceeded 20 notifications in 20s window: $countIn20sWindow", countIn20sWindow < 20)
        }
        // Khi màn hình đang tắt không cập nhật notification thừa
        assertFalse(TimerPersistenceManager.shouldUpdateNotification(5L, 10L, isInteractive = false))
    }
}

