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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentPurple
import kotlinx.coroutines.launch

/** Bottom sheet chọn thời gian (giờ / phút) thay cho TimePickerDialog mặc định của hệ thống. */
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
    var minutes by remember { mutableIntStateOf(((initialSeconds % 3600) / 60).toInt()) }

    val isValid = hours > 0 || minutes > 0

    fun applyTotalMinutes(total: Int) {
        hours = (total / 60).coerceIn(0, 23)
        minutes = total % 60
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Đặt thời gian hẹn giờ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Stepper(
                    value = hours,
                    label = "GIỜ",
                    onUp = { hours = (hours + 1) % 24 },
                    onDown = { hours = (hours + 23) % 24 }
                )
                Text(
                    text = ":",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Stepper(
                    value = minutes,
                    label = "PHÚT",
                    onUp = { minutes = (minutes + 5) % 60 },
                    onDown = { minutes = (minutes + 55) % 60 }
                )
            }

            Spacer(Modifier.height(20.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(15, 30, 45, 60, 90, 120)) { m ->
                    val selected = hours * 60 + minutes == m
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) AccentCyan else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { applyTotalMinutes(m) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (m >= 60 && m % 60 == 0) "${m / 60}h" else "${m}m",
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color(0xFF00363D) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                enabled = isValid,
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onConfirm(hours * 3600L + minutes * 60L)
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
                Text("Xác nhận", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, label: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepButton(up = true, onClick = onUp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(96.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, AccentPurple.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val dir = if (targetState > initialState || (initialState == 59 && targetState == 0)) 1 else -1
                    (slideInVertically(tween(220)) { it * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(220)) { -it * dir } + fadeOut(tween(160))) using
                        SizeTransform(clip = true)
                },
                label = "stepper_value"
            ) { v ->
                Text(
                    text = String.format("%02d", v),
                    fontSize = 46.sp,
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
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = if (up) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (up) "Tăng" else "Giảm",
            tint = AccentCyan,
            modifier = Modifier.size(30.dp)
        )
    }
}
