package com.vunameaut.powertimer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.data.model.TimerState
import com.vunameaut.powertimer.manager.PermissionManager
import com.vunameaut.powertimer.service.TimerForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionStatusState(
    val hasNotification: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val canScheduleExactAlarm: Boolean = false,
    val isDeviceAdminActive: Boolean = false
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val permissionManager = PermissionManager(application)

    // Configuration before timer starts
    private val _selectedSeconds = MutableStateFlow(1800L) // Mặc định 30 phút
    val selectedSeconds: StateFlow<Long> = _selectedSeconds.asStateFlow()

    private val _selectedMode = MutableStateFlow(ShutdownMode.ACCESSIBILITY)
    val selectedMode: StateFlow<ShutdownMode> = _selectedMode.asStateFlow()

    private val _fadeAudio = MutableStateFlow(true)
    val fadeAudio: StateFlow<Boolean> = _fadeAudio.asStateFlow()

    private val _enableDnd = MutableStateFlow(true)
    val enableDnd: StateFlow<Boolean> = _enableDnd.asStateFlow()

    private val _playWarningSound = MutableStateFlow(true)
    val playWarningSound: StateFlow<Boolean> = _playWarningSound.asStateFlow()

    private val _shakeToExtend = MutableStateFlow(true)
    val shakeToExtend: StateFlow<Boolean> = _shakeToExtend.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionStatusState())
    val permissions: StateFlow<PermissionStatusState> = _permissions.asStateFlow()

    // Combined active timer state
    val timerState: StateFlow<TimerState> = TimerForegroundService.timerFlow

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        _permissions.value = PermissionStatusState(
            hasNotification = permissionManager.hasNotificationPermission(),
            isAccessibilityEnabled = permissionManager.isAccessibilityServiceEnabled(),
            isBatteryOptimizationIgnored = permissionManager.isIgnoringBatteryOptimizations(),
            canScheduleExactAlarm = permissionManager.canScheduleExactAlarms(),
            isDeviceAdminActive = permissionManager.isDeviceAdminActive()
        )
    }

    fun setTimeInMinutes(minutes: Long) {
        if (!timerState.value.isRunning) {
            _selectedSeconds.value = minutes * 60
        }
    }

    fun setCustomSeconds(seconds: Long) {
        if (!timerState.value.isRunning) {
            _selectedSeconds.value = seconds
        }
    }

    fun setMode(mode: ShutdownMode) {
        _selectedMode.value = mode
    }

    fun toggleFadeAudio(enabled: Boolean) {
        _fadeAudio.value = enabled
    }

    fun toggleDnd(enabled: Boolean) {
        _enableDnd.value = enabled
    }

    fun toggleWarningSound(enabled: Boolean) {
        _playWarningSound.value = enabled
    }

    fun toggleShakeToExtend(enabled: Boolean) {
        _shakeToExtend.value = enabled
    }

    fun startTimer() {
        TimerForegroundService.startTimer(
            context = getApplication(),
            seconds = _selectedSeconds.value,
            mode = _selectedMode.value,
            fadeAudio = _fadeAudio.value,
            enableDnd = _enableDnd.value,
            playWarning = _playWarningSound.value,
            shakeToExtend = _shakeToExtend.value
        )
    }

    fun stopTimer() {
        TimerForegroundService.stopTimer(getApplication())
    }

    fun addTenMinutes() {
        TimerForegroundService.addTenMinutes(getApplication())
    }

    // Permission navigation helpers
    fun openAccessibilitySettings() = permissionManager.openAccessibilitySettings()
    fun requestBatteryOptimization() = permissionManager.requestIgnoreBatteryOptimizations()
    fun openExactAlarmSettings() = permissionManager.openExactAlarmSettings()
    fun requestDeviceAdmin() = permissionManager.requestDeviceAdmin()
}
