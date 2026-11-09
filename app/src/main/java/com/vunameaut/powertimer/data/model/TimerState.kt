package com.vunameaut.powertimer.data.model

data class TimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val totalSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    val selectedMode: ShutdownMode = ShutdownMode.ACCESSIBILITY,
    val fadeAudio: Boolean = true,
    val playWarningSound: Boolean = true,
    val turnOnDnd: Boolean = true
) {
    val progress: Float
        get() = if (totalSeconds > 0) {
            remainingSeconds.toFloat() / totalSeconds.toFloat()
        } else 0f

    val formattedRemaining: String
        get() {
            val hours = remainingSeconds / 3600
            val minutes = (remainingSeconds % 3600) / 60
            val seconds = remainingSeconds % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
}
