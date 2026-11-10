package com.vunameaut.powertimer.update

import android.app.Application
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
    data class ReadyToInstall(val info: UpdateInfo, val apk: File) : UpdateState
    data class Error(val message: String) : UpdateState
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

    fun download() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Error -> return
            else -> return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            _state.value = try {
                val file = UpdateManager.download(getApplication(), info) { p ->
                    _state.value = UpdateState.Downloading(info, p)
                }
                UpdateState.ReadyToInstall(info, file)
            } catch (e: Exception) {
                UpdateState.Error("Tải thất bại: ${e.message ?: "lỗi mạng"}")
            }
        }
    }

    fun install() {
        val s = _state.value as? UpdateState.ReadyToInstall ?: return
        UpdateManager.install(getApplication(), s.apk)
    }
}
