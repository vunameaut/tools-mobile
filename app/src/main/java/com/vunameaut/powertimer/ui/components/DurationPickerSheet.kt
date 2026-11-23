package com.vunameaut.powertimer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentPurple
import kotlinx.coroutines.launch

private data class QuickTestPreset(val label: String, val totalSeconds: Long)

private enum class EditingUnit(val title: String, val maxVal: Int) {
    HOURS("Nhập số Giờ (0 - 23)", 23),
    MINUTES("Nhập số Phút (0 - 59)", 59),
    SECONDS("Nhập số Giây (0 - 59)", 59)
}

/** Bottom sheet chọn thời gian tự do (Giờ : Phút : Giây, bước 1 đơn vị & chạm để nhập số). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerSheet(
    initialSeconds: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var hours by remember { mutableIntStateOf((initialSeconds / 3600).toInt().coerceIn(0, 23)) }
    var minutes by remember { mutableIntStateOf(((initialSeconds % 3600) / 60).toInt().coerceIn(0, 59)) }
    var seconds by remember { mutableIntStateOf((initialSeconds % 60).toInt().coerceIn(0, 59)) }

    var editingUnit by remember { mutableStateOf<EditingUnit?>(null) }
    var inputBuffer by remember { mutableStateOf("") }

    val totalSelectedSeconds = hours * 3600L + minutes * 60L + seconds
    val isValid = totalSelectedSeconds > 0L

    fun applyTotalSeconds(totalSecs: Long) {
        val clamped = totalSecs.coerceIn(1L, 86399L)
        hours = (clamped / 3600L).toInt().coerceIn(0, 23)
        minutes = ((clamped % 3600L) / 60L).toInt().coerceIn(0, 59)
        seconds = (clamped % 60L).toInt().coerceIn(0, 59)
    }

    val quickTestPresets = remember {
        listOf(
            QuickTestPreset("5s", 5L),
            QuickTestPreset("10s", 10L),
            QuickTestPreset("15s", 15L),
            QuickTestPreset("30s", 30L),
            QuickTestPreset("1p", 60L),
            QuickTestPreset("2p", 120L),
            QuickTestPreset("5p", 300L),
            QuickTestPreset("15p", 900L),
            QuickTestPreset("30p", 1800L),
            QuickTestPreset("45p", 2700L),
            QuickTestPreset("60p", 3600L),
            QuickTestPreset("90p", 5400L),
            QuickTestPreset("120p", 7200L)
        )
    }

    editingUnit?.let { unit ->
        AlertDialog(
            onDismissRequest = { editingUnit = null },
            shape = RoundedCornerShape(22.dp),
            title = { Text(unit.title, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                OutlinedTextField(
                    value = inputBuffer,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(2)
                        inputBuffer = digits
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("0 - ${unit.maxVal}") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = (inputBuffer.toIntOrNull() ?: 0).coerceIn(0, unit.maxVal)
                        when (unit) {
                            EditingUnit.HOURS -> hours = parsed
                            EditingUnit.MINUTES -> minutes = parsed
                            EditingUnit.SECONDS -> seconds = parsed
                        }
                        editingUnit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                ) {
                    Text("Áp dụng", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingUnit = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Đặt thời gian hẹn giờ tự do",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Chạm mũi tên chỉnh từng đơn vị hoặc chạm vào ô số để nhập tay",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Stepper(
                    value = hours,
                    label = "GIỜ",
                    maxValue = 23,
                    onUp = { hours = (hours + 1) % 24 },
                    onDown = { hours = (hours + 23) % 24 },
                    onNumberClick = {
                        inputBuffer = hours.toString()
                        editingUnit = EditingUnit.HOURS
                    }
                )
                Text(
                    text = ":",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                Stepper(
                    value = minutes,
                    label = "PHÚT",
                    maxValue = 59,
                    onUp = { minutes = (minutes + 1) % 60 },
                    onDown = { minutes = (minutes + 59) % 60 },
                    onNumberClick = {
                        inputBuffer = minutes.toString()
                        editingUnit = EditingUnit.MINUTES
                    }
                )
                Text(
                    text = ":",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                Stepper(
                    value = seconds,
                    label = "GIÂY",
                    maxValue = 59,
                    onUp = { seconds = (seconds + 1) % 60 },
                    onDown = { seconds = (seconds + 59) % 60 },
                    onNumberClick = {
                        inputBuffer = seconds.toString()
                        editingUnit = EditingUnit.SECONDS
                    }
                )
            }

            Spacer(Modifier.height(18.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickTestPresets) { preset ->
                    val selected = totalSelectedSeconds == preset.totalSeconds
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) AccentCyan else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { applyTotalSeconds(preset.totalSeconds) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selected) Color(0xFF00363D) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Button(
                enabled = isValid,
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onConfirm(totalSelectedSeconds)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color(0xFF00363D)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isValid) {
                        "Xác nhận (${String.format("%02d:%02d:%02d", hours, minutes, seconds)})"
                    } else {
                        "Chọn ít nhất 1 giây"
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.5.sp
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    value: Int,
    label: String,
    maxValue: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onNumberClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepButton(up = true, onClick = onUp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(78.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, AccentPurple.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .clickable(onClick = onNumberClick)
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val dir = if (targetState > initialState || (initialState == maxValue && targetState == 0)) 1 else -1
                    (slideInVertically(tween(180)) { it * dir } + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(tween(180)) { -it * dir } + fadeOut(tween(130))) using
                        SizeTransform(clip = true)
                },
                label = "stepper_value"
            ) { v ->
                Text(
                    text = String.format("%02d", v),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        StepButton(up = false, onClick = onDown)
        Text(
            text = label,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepButton(up: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = if (up) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (up) "Tăng" else "Giảm",
            tint = AccentCyan,
            modifier = Modifier.size(28.dp)
        )
    }
}
