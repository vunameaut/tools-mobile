package com.vunameaut.powertimer.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apk: File, val waitingPermission: Boolean = false) : UpdateState
    data class Error(val message: String, val retryInfo: UpdateInfo? = null) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var autoChecked = false

    /** Gọi 1 lần khi mở app để âm thầm kiểm tra bản mới. */
    fun autoCheck() {
        if (autoChecked) return
        autoChecked = true
        check()
    }

    fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value = try {
                UpdateManager.checkForUpdate()
                    ?.let { UpdateState.Available(it) }
                    ?: UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Error("Không kiểm tra được: ${e.message ?: "lỗi mạng"}")
            }
        }
    }

    fun download(context: Context = getApplication()) {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Error -> s.retryInfo ?: return
            is UpdateState.ReadyToInstall -> s.info
            else -> return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val file = UpdateManager.download(getApplication(), info) { p ->
                    _state.value = UpdateState.Downloading(info, p)
                }
                val launched = UpdateManager.install(context, file)
                _state.value = UpdateState.ReadyToInstall(
                    info = info,
                    apk = file,
                    waitingPermission = !launched
                )
            } catch (e: Exception) {
                _state.value = UpdateState.Error(
                    message = "Tải thất bại: ${e.message ?: "lỗi mạng"}",
                    retryInfo = info
                )
            }
        }
    }

    fun install(context: Context = getApplication()) {
        val s = _state.value as? UpdateState.ReadyToInstall ?: return
        if (!s.apk.exists()) {
            download(context)
            return
        }
        val launched = UpdateManager.install(context, s.apk)
        _state.value = s.copy(waitingPermission = !launched)
    }

    /**
     * Khi người dùng vừa bật quyền "Cài đặt ứng dụng không rõ nguồn gốc" và quay lại app,
     * tự động mở trình cài đặt APK ngay lập tức.
     */
    fun onAppResumed(context: Context) {
        val s = _state.value as? UpdateState.ReadyToInstall ?: return
        if (s.waitingPermission && context.packageManager.canRequestPackageInstalls()) {
            val launched = UpdateManager.install(context, s.apk)
            _state.value = s.copy(waitingPermission = !launched)
        }
    }
}
