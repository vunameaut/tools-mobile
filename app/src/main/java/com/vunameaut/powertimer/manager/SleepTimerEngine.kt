package com.vunameaut.powertimer.manager

import android.content.Context
import android.content.SharedPreferences
import com.vunameaut.powertimer.data.model.ShutdownMode
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Abstraction lưu trữ Key-Value giúp toàn bộ máy trạng thái Hẹn giờ (Persistence, Timeout,
 * Recovery, Enforcement) chạy đồng nhất cả trên Android thật lẫn JUnit test.
 */
interface TimerKeyValueStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)
    fun getString(key: String, defaultValue: String?): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class SharedPreferencesKeyValueStore(
    private val prefs: SharedPreferences
) : TimerKeyValueStore {
    override fun contains(key: String): Boolean = prefs.contains(key)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    override fun getString(key: String, defaultValue: String?): String? = prefs.getString(key, defaultValue)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}

class InMemoryKeyValueStore : TimerKeyValueStore {
    private val map = linkedMapOf<String, Any>()
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = (map[key] as? Boolean) ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = (map[key] as? Int) ?: defaultValue
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = (map[key] as? Long) ?: defaultValue
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getString(key: String, defaultValue: String?): String? = (map[key] as? String) ?: defaultValue
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

data class PersistedTimerConfig(
    val totalSeconds: Long,
    val targetEndEpochMs: Long,
    val mode: ShutdownMode,
    val fadeAudio: Boolean,
    val playWarning: Boolean,
    val shakeToExtend: Boolean
)

sealed class RecoveryDecision {
    object None : RecoveryDecision()
    data class ResumeCountdown(val remainingSeconds: Long, val config: PersistedTimerConfig) : RecoveryDecision()
    data class ExecuteImmediateShutdown(val config: PersistedTimerConfig) : RecoveryDecision()
    object SilentCleanupStaleOffScreen : RecoveryDecision()
}

/**
 * Lưu trữ bền vững cấu hình & mốc kết thúc hẹn giờ để khôi phục 100% nếu OS/OEM giết Service giữa chừng.
 */
class TimerPersistenceManager(
    private val store: TimerKeyValueStore
) {
    companion object {
        private const val PREF_NAME = "power_timer_persist_prefs"
        private const val KEY_IS_RUNNING = "timer_is_running"
        private const val KEY_TOTAL_SECONDS = "timer_total_seconds"
        private const val KEY_TARGET_END_EPOCH = "timer_target_end_epoch"
        private const val KEY_MODE = "timer_mode"
        private const val KEY_FADE_AUDIO = "timer_fade_audio"
        private const val KEY_PLAY_WARNING = "timer_play_warning"
        private const val KEY_SHAKE_TO_EXTEND = "timer_shake_to_extend"
        private const val KEY_ENFORCING_UNTIL_EPOCH = "timer_enforcing_until_epoch"

        const val STALE_OFF_SCREEN_GRACE_MS = 60_000L

        fun from(context: Context): TimerPersistenceManager {
            val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return TimerPersistenceManager(SharedPreferencesKeyValueStore(prefs))
        }

        fun computeRemainingSeconds(nowMs: Long, targetEndEpochMs: Long): Long {
            if (targetEndEpochMs <= nowMs) return 0L
            return ((targetEndEpochMs - nowMs + 999L) / 1000L).coerceAtLeast(0L)
        }

        /**
         * Giới hạn cập nhật thông báo hệ thống mỗi 5 giây (hoặc 10 giây cuối khi màn hình sáng)
         * để không bao giờ vượt ngưỡng rate-limit ~20 thông báo/20s của Android 14/15.
         */
        fun shouldUpdateNotification(
            remainingSeconds: Long,
            lastNotifiedSeconds: Long,
            isInteractive: Boolean
        ): Boolean {
            if (!isInteractive) return false
            if (remainingSeconds == lastNotifiedSeconds) return false
            return remainingSeconds % 5L == 0L || remainingSeconds <= 10L
        }
    }

    fun saveActiveTimer(config: PersistedTimerConfig) {
        store.putBoolean(KEY_IS_RUNNING, true)
        store.putLong(KEY_TOTAL_SECONDS, config.totalSeconds)
        store.putLong(KEY_TARGET_END_EPOCH, config.targetEndEpochMs)
        store.putString(KEY_MODE, config.mode.name)
        store.putBoolean(KEY_FADE_AUDIO, config.fadeAudio)
        store.putBoolean(KEY_PLAY_WARNING, config.playWarning)
        store.putBoolean(KEY_SHAKE_TO_EXTEND, config.shakeToExtend)
    }

    fun loadActiveTimer(): PersistedTimerConfig? {
        if (!store.getBoolean(KEY_IS_RUNNING, false)) return null
        val target = store.getLong(KEY_TARGET_END_EPOCH, 0L)
        if (target <= 0L) return null
        val total = store.getLong(KEY_TOTAL_SECONDS, 1800L).coerceAtLeast(1L)
        val modeName = store.getString(KEY_MODE, ShutdownMode.BANK_SAFE_SLEEP.name)
        val mode = runCatching {
            ShutdownMode.valueOf(modeName ?: ShutdownMode.BANK_SAFE_SLEEP.name)
        }.getOrDefault(ShutdownMode.BANK_SAFE_SLEEP)
        return PersistedTimerConfig(
            totalSeconds = total,
            targetEndEpochMs = target,
            mode = mode,
            fadeAudio = store.getBoolean(KEY_FADE_AUDIO, true),
            playWarning = store.getBoolean(KEY_PLAY_WARNING, true),
            shakeToExtend = store.getBoolean(KEY_SHAKE_TO_EXTEND, false)
        )
    }

    fun clearActiveTimer() {
        store.putBoolean(KEY_IS_RUNNING, false)
        store.remove(KEY_TOTAL_SECONDS)
        store.remove(KEY_TARGET_END_EPOCH)
        store.remove(KEY_MODE)
        store.remove(KEY_FADE_AUDIO)
        store.remove(KEY_PLAY_WARNING)
        store.remove(KEY_SHAKE_TO_EXTEND)
    }

    fun markShutdownEnforcing(untilEpochMs: Long) {
        store.putLong(KEY_ENFORCING_UNTIL_EPOCH, untilEpochMs)
    }

    fun clearShutdownEnforcing() {
        store.remove(KEY_ENFORCING_UNTIL_EPOCH)
    }

    fun isShutdownCurrentlyEnforcing(nowMs: Long): Boolean {
        val until = store.getLong(KEY_ENFORCING_UNTIL_EPOCH, 0L)
        return until > nowMs
    }

    fun decideRecoveryAction(nowMs: Long, isScreenInteractive: Boolean): RecoveryDecision {
        val config = loadActiveTimer() ?: return RecoveryDecision.None
        val remaining = computeRemainingSeconds(nowMs, config.targetEndEpochMs)
        if (remaining > 0L) {
            return RecoveryDecision.ResumeCountdown(remaining, config)
        }
        val overdueMs = nowMs - config.targetEndEpochMs
        return if (isScreenInteractive || overdueMs <= STALE_OFF_SCREEN_GRACE_MS) {
            RecoveryDecision.ExecuteImmediateShutdown(config)
        } else {
            RecoveryDecision.SilentCleanupStaleOffScreen
        }
    }
}

/**
 * Máy trạng thái quản lý an toàn SCREEN_OFF_TIMEOUT:
 * - Lưu chính xác thời gian tắt màn hình gốc của người dùng (15s, 30s, 60s, 300s, 600s...) trước khi hạ tạm thời.
 * - Đánh dấu rõ app đang chỉnh tạm (`KEY_TIMEOUT_MODIFIED_BY_US`) để chỉ khôi phục đúng lúc, không khôi phục nhầm lúc đang chờ tắt màn hình.
 */
class ScreenTimeoutStateMachine(
    private val store: TimerKeyValueStore
) {
    companion object {
        private const val PREFS_NAME = "bank_safe_sleep_prefs"
        private const val KEY_SAVED_TIMEOUT = "saved_screen_off_timeout"
        private const val KEY_TIMEOUT_MODIFIED_BY_US = "timeout_modified_by_us"
        private const val MIN_TIMEOUT_MS = 1_000
        private const val FALLBACK_DEFAULT_TIMEOUT_MS = 60_000

        val TIMEOUT_CANDIDATES = intArrayOf(MIN_TIMEOUT_MS, 5_000, 10_000, 15_000, 30_000)

        fun from(context: Context): ScreenTimeoutStateMachine {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ScreenTimeoutStateMachine(SharedPreferencesKeyValueStore(prefs))
        }
    }

    fun isModifiedByUs(): Boolean = store.getBoolean(KEY_TIMEOUT_MODIFIED_BY_US, false)

    fun applyMinimumTimeout(
        currentTimeoutMs: Int,
        writeAndReadBack: (Int) -> Int
    ): Int {
        if (!store.getBoolean(KEY_TIMEOUT_MODIFIED_BY_US, false) && !store.contains(KEY_SAVED_TIMEOUT)) {
            val baseline = if (currentTimeoutMs >= 15_000) currentTimeoutMs else FALLBACK_DEFAULT_TIMEOUT_MS
            store.putInt(KEY_SAVED_TIMEOUT, baseline)
        }
        store.putBoolean(KEY_TIMEOUT_MODIFIED_BY_US, true)

        var lastActual = currentTimeoutMs
        for (candidate in TIMEOUT_CANDIDATES) {
            lastActual = writeAndReadBack(candidate)
            if (lastActual <= candidate + 1_000) {
                return lastActual
            }
        }
        return lastActual
    }

    fun restoreIfModified(
        currentTimeoutMs: Int,
        writeTimeout: (Int) -> Unit
    ): Int? {
        val modifiedByUs = store.getBoolean(KEY_TIMEOUT_MODIFIED_BY_US, false)
        val hasSaved = store.contains(KEY_SAVED_TIMEOUT)
        if (!modifiedByUs && !hasSaved) {
            if (currentTimeoutMs <= 12_000) {
                writeTimeout(FALLBACK_DEFAULT_TIMEOUT_MS)
                return FALLBACK_DEFAULT_TIMEOUT_MS
            }
            return null
        }

        val saved = store.getInt(KEY_SAVED_TIMEOUT, FALLBACK_DEFAULT_TIMEOUT_MS)
        val target = if (saved >= 15_000) saved else FALLBACK_DEFAULT_TIMEOUT_MS
        writeTimeout(target)
        store.remove(KEY_SAVED_TIMEOUT)
        store.putBoolean(KEY_TIMEOUT_MODIFIED_BY_US, false)
        return target
    }
}

/**
 * Bộ phân tích gia tốc chống kích hoạt nhầm khi đặt máy xuống đệm/giường lúc ngủ.
 * Chỉ kích hoạt khi có >= 2 đỉnh lắc mạnh (>= 2.35G, tức lệch >= 1.35G so với 1G tĩnh)
 * trong cửa sổ 650ms, sau tối thiểu 3 giây khởi động bộ đếm.
 */
class ShakeMotionAnalyzer {
    companion object {
        const val STARTUP_GUARD_MS = 3_000L
        const val SPIKE_DELTA_G = 1.35f
        const val MIN_SAMPLE_GAP_MS = 120L
        const val DOUBLE_SPIKE_WINDOW_MS = 650L
        const val COOLDOWN_MS = 5_000L
        private const val STANDARD_GRAVITY = 9.80665f
    }

    private var armedAfterEpochMs: Long = 0L
    private var firstSpikeEpochMs: Long = 0L
    private var lastTriggerEpochMs: Long = 0L

    fun reset(startEpochMs: Long) {
        armedAfterEpochMs = startEpochMs + STARTUP_GUARD_MS
        firstSpikeEpochMs = 0L
        lastTriggerEpochMs = 0L
    }

    fun onAccelerationSample(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (nowMs < armedAfterEpochMs) return false
        if (lastTriggerEpochMs > 0L && (nowMs - lastTriggerEpochMs) < COOLDOWN_MS) return false

        val mag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val gForce = mag / STANDARD_GRAVITY
        val deltaG = abs(gForce - 1.0f)
        if (deltaG < SPIKE_DELTA_G) return false

        if (firstSpikeEpochMs == 0L || (nowMs - firstSpikeEpochMs) > DOUBLE_SPIKE_WINDOW_MS) {
            firstSpikeEpochMs = nowMs
            return false
        }

        if ((nowMs - firstSpikeEpochMs) >= MIN_SAMPLE_GAP_MS) {
            firstSpikeEpochMs = 0L
            lastTriggerEpochMs = nowMs
            return true
        }
        return false
    }
}

enum class InitialEnforcementAction {
    PAUSE_MEDIA_SILENT_STOP_WHEN_SCREEN_ALREADY_OFF,
    EXECUTE_SHUTDOWN_AND_HOLD_FOCUS
}

enum class PollDecision {
    CONTINUE_WAITING,
    REASSERT_MEDIA_AND_TIMEOUT,
    ENGAGE_OLED_BATTERY_GUARD,
    RESTORE_AND_STOP_ON_SCREEN_OFF,
    COMPLETE_MEDIA_ONLY,
    HARD_TIMEOUT_RELEASE
}

/**
 * Chính sách cưỡng bức tắt màn hình sau khi hết giờ:
 * - Nếu màn hình đã tắt sẵn -> dừng media nhẹ nhàng & kết thúc ngay.
 * - Nếu màn hình còn sáng -> giữ AudioFocus cho đến khi màn hình thực sự tắt (ACTION_SCREEN_OFF),
 *   nhắc lại mỗi 3s nếu app video cố chạy tiếp, và bật Màn đen OLED 0% tiết kiệm pin tuyệt đối
 *   nếu vượt quá `appliedTimeout + 8s` để không bao giờ cạn pin qua đêm.
 */
object ShutdownEnforcementPolicy {
    const val HARD_CEILING_MS = 40_000L
    const val MEDIA_ONLY_WAIT_MS = 3_000L
    const val MEDIA_ONLY_FOCUS_HOLD_MS = 12_000L
    const val FULL_LOCK_MAX_FOCUS_HOLD_MS = 42_000L

    fun computeOledGuardTriggerMs(appliedTimeoutMs: Int): Long {
        return appliedTimeoutMs.coerceAtLeast(15_000).toLong() + 8_000L
    }

    fun decideInitialAction(
        isScreenInteractive: Boolean,
        mode: ShutdownMode
    ): InitialEnforcementAction {
        return if (!isScreenInteractive) {
            InitialEnforcementAction.PAUSE_MEDIA_SILENT_STOP_WHEN_SCREEN_ALREADY_OFF
        } else {
            InitialEnforcementAction.EXECUTE_SHUTDOWN_AND_HOLD_FOCUS
        }
    }

    fun evaluatePollStep(
        elapsedSinceStartMs: Long,
        isScreenInteractive: Boolean,
        mode: ShutdownMode,
        appliedTimeoutMs: Int,
        oledGuardLaunched: Boolean
    ): PollDecision {
        if (!isScreenInteractive) {
            return PollDecision.RESTORE_AND_STOP_ON_SCREEN_OFF
        }
        if (mode == ShutdownMode.MEDIA_ONLY && elapsedSinceStartMs >= MEDIA_ONLY_WAIT_MS) {
            return PollDecision.COMPLETE_MEDIA_ONLY
        }
        if (elapsedSinceStartMs >= HARD_CEILING_MS) {
            return PollDecision.HARD_TIMEOUT_RELEASE
        }
        if (mode != ShutdownMode.MEDIA_ONLY &&
            !oledGuardLaunched &&
            elapsedSinceStartMs >= computeOledGuardTriggerMs(appliedTimeoutMs)
        ) {
            return PollDecision.ENGAGE_OLED_BATTERY_GUARD
        }
        if (elapsedSinceStartMs > 0L && elapsedSinceStartMs % 3_000L == 0L) {
            return PollDecision.REASSERT_MEDIA_AND_TIMEOUT
        }
        return PollDecision.CONTINUE_WAITING
    }
}

