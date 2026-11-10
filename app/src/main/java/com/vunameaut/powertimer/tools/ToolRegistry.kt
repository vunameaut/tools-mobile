package com.vunameaut.powertimer.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vunameaut.powertimer.ui.screens.PowerTimerTool
import com.vunameaut.powertimer.ui.theme.AccentCyan

/** Mô tả một công cụ nằm trong Tools Hub. */
data class ToolEntry(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val content: @Composable (onBack: () -> Unit) -> Unit
)

/**
 * ĐĂNG KÝ CÔNG CỤ MỚI Ở ĐÂY.
 *
 * Mỗi tool chỉ cần 1 composable nhận `onBack` và 1 dòng [ToolEntry] bên dưới;
 * màn hình chính, điều hướng và nút Back sẽ tự có.
 */
val ToolRegistry: List<ToolEntry> = listOf(
    ToolEntry(
        id = "power_timer",
        title = "PowerTimer",
        description = "Hẹn giờ tắt màn hình, dừng nhạc / video khi ngủ",
        icon = Icons.Rounded.Bedtime,
        accent = AccentCyan,
        content = { onBack -> PowerTimerTool(onExit = onBack) }
    )
)

