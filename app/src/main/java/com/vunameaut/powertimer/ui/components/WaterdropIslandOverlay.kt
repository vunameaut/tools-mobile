package com.vunameaut.powertimer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.manager.ShutdownManager
import com.vunameaut.powertimer.service.SystemDynamicIslandManager
import com.vunameaut.powertimer.service.TimerForegroundService
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import kotlin.math.sin

/**
 * Hiệu ứng tương tác màn hình giọt nước (Waterdrop / Dynamic Island) kiểu YouTube & Spotify
 * dành cho Hẹn giờ tắt màn hình (PowerTimer).
 *
 * - Ôm sát camera trước (giọt nước / đục lỗ) ở chính giữa đỉnh màn hình.
 * - Có sóng nhạc động (Equalizer Visualizer) & gợn sóng chất lỏng chuyển động theo thời gian thực.
 * - Chạm 1 lần hoặc vuốt xuống để "nở rộng" giọt nước thành bảng điều khiển tương tác nhanh.
 */
@Composable
fun WaterdropIslandOverlay(
    forceVisibleForPreview: Boolean = false,
    onOpenPowerTimerScreen: (() -> Unit)? = null,
    onDismissPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val timerState by TimerForegroundService.timerFlow.collectAsState()

    // Đã sử dụng trực tiếp Dynamic Island gốc của hệ thống (qua NativeMediaIslandBridge giống YouTube),
    // nên tắt hoàn toàn lớp phủ phụ để không bao giờ bị hiện đè 2 viên thuốc lên nhau.
    val isVisible = false
    var isExpanded by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(SystemDynamicIslandManager.isMinimized(context)) }

    val infiniteTransition = rememberInfiniteTransition(label = "waterdrop_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(280)) + scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)
        ),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.6f),
        modifier = modifier
            .fillMaxWidth()
            .zIndex(99f)
    ) {
        val pillWidth by animateDpAsState(
            targetValue = when {
                isExpanded -> 348.dp
                isMinimized -> 90.dp
                else -> 218.dp
            },
            animationSpec = spring(
                dampingRatio = 0.70f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "island_width"
        )
        val cornerRadius by animateDpAsState(
            targetValue = if (isExpanded) 30.dp else 50.dp,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            label = "island_corner"
        )

        Box(
            contentAlignment = if (isMinimized && !isExpanded) Alignment.TopEnd else Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 12.dp, end = 12.dp)
        ) {
            val borderBrush = Brush.linearGradient(
                colors = listOf(
                    AccentCyan.copy(alpha = glowPulse),
                    AccentPurple.copy(alpha = 0.85f),
                    AccentGreen.copy(alpha = glowPulse),
                    AccentPink.copy(alpha = 0.75f)
                )
            )

            Surface(
                shape = RoundedCornerShape(cornerRadius),
                color = Color(0xFF05070E),
                modifier = Modifier
                    .width(pillWidth)
                    .shadow(
                        elevation = if (isExpanded) 24.dp else 12.dp,
                        shape = RoundedCornerShape(cornerRadius),
                        ambientColor = AccentCyan,
                        spotColor = AccentPurple
                    )
                    .border(
                        width = if (isExpanded) 1.6.dp else 1.3.dp,
                        brush = borderBrush,
                        shape = RoundedCornerShape(cornerRadius)
                    )
                    .pointerInput(isExpanded) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 10f && !isExpanded) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isExpanded = true
                            } else if (dragAmount < -10f && isExpanded) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = false
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isMinimized && !isExpanded) {
                            isMinimized = false
                            SystemDynamicIslandManager.setMinimized(context, false)
                        } else {
                            isExpanded = !isExpanded
                        }
                    }
            ) {
                AnimatedContent(
                    targetState = Pair(isExpanded, isMinimized),
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f)) togetherWith
                            (fadeOut(tween(150)) + scaleOut(targetScale = 0.92f))
                    },
                    label = "island_body"
                ) { (expanded, minimized) ->
                    if (!expanded && minimized) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (timerState.isRunning) AccentGreen else AccentCyan)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = timerState.formattedRemaining.ifBlank { "30:00" }.take(5),
                                color = if (timerState.isRunning) AccentGreen else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else if (!expanded) {
                        // ================= TRẠNG THÁI THU GỌN ÔM SÁT GIỌT NƯỚC =================
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .padding(horizontal = 12.dp)
                        ) {
                            // Cụm bên trái giọt nước: Icon Mặt trăng + Chấm trạng thái
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(AccentCyan.copy(alpha = 0.18f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = timerState.formattedRemaining.ifBlank { "30:00" },
                                    color = if (timerState.isRunning) AccentGreen else Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Vùng lõm Giọt nước Camera chính giữa (với hiệu ứng gợn sóng chất lỏng)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 28.dp, height = 20.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cX = size.width / 2f
                                    val cY = size.height / 2f
                                    val r = 6.dp.toPx() + sin(wavePhase) * 1.5.dp.toPx()
                                    drawCircle(
                                        color = AccentPurple.copy(alpha = 0.32f),
                                        radius = r,
                                        center = Offset(cX, cY)
                                    )
                                    drawCircle(
                                        color = Color(0xFF0A0F1D),
                                        radius = 4.5.dp.toPx(),
                                        center = Offset(cX, cY)
                                    )
                                }
                            }

                            // Cụm bên phải giọt nước: Thanh sóng nhạc Spotify / YouTube Equalizer
                            SpotifyStyleEqualizerBars(
                                phase = wavePhase,
                                isActive = timerState.isRunning || forceVisibleForPreview,
                                modifier = Modifier.size(width = 28.dp, height = 16.dp)
                            )
                        }
                    } else {
                        // ================= TRẠNG THÁI NỞ RỘNG (EXPANDED DYNAMIC ISLAND) =================
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Hàng đầu: Biểu tượng + Tiêu đề + Sóng âm + Thu gọn
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(AccentCyan, AccentPurple)
                                                )
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bedtime,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (timerState.isRunning) {
                                                "Đang hẹn giờ tắt màn hình"
                                            } else {
                                                "Giọt nước Hẹn giờ (Chạm nhanh)"
                                            },
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = timerState.selectedMode.title,
                                            color = AccentGreen,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier.clickable {
                                            isExpanded = false
                                            isMinimized = true
                                            SystemDynamicIslandManager.setMinimized(context, true)
                                        }
                                    ) {
                                        Text(
                                            text = "🗕 Thu nhỏ",
                                            color = AccentAmber,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { isExpanded = false },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ExpandLess,
                                            contentDescription = "Thu gọn",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Sóng nước Liquid Waveform + Đồng hồ lớn
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0D1326))
                                    .clickable(enabled = onOpenPowerTimerScreen != null) {
                                        isExpanded = false
                                        onOpenPowerTimerScreen?.invoke()
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val path = Path()
                                    val w = size.width
                                    val h = size.height
                                    val midY = h * 0.65f
                                    path.moveTo(0f, midY)
                                    val step = 12f
                                    var x = 0f
                                    while (x <= w) {
                                        val y = midY + sin((x / w) * 4f * Math.PI + wavePhase).toFloat() * 8.dp.toPx()
                                        path.lineTo(x, y)
                                        x += step
                                    }
                                    drawPath(
                                        path = path,
                                        brush = Brush.horizontalGradient(
                                            listOf(
                                                AccentCyan.copy(alpha = 0.35f),
                                                AccentPurple.copy(alpha = 0.55f),
                                                AccentGreen.copy(alpha = 0.35f)
                                            )
                                        ),
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = timerState.formattedRemaining.ifBlank { "30:00" },
                                        color = AccentCyan,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    val pct = if (timerState.isRunning) {
                                        (timerState.progress * 100).toInt().coerceIn(0, 100)
                                    } else 100
                                    Text(
                                        text = if (timerState.isRunning) "Còn $pct%" else "Sẵn sàng",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (timerState.isRunning) {
                                val animatedProgress by animateFloatAsState(
                                    targetValue = timerState.progress.coerceIn(0f, 1f),
                                    label = "island_progress"
                                )
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = AccentCyan,
                                    trackColor = Color.White.copy(alpha = 0.12f)
                                )

                                // Hàng nút điều khiển nhanh khi đang đếm ngược
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            TimerForegroundService.addTenMinutes(context)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text("+10 Phút", fontSize = 11.5.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val mode = timerState.selectedMode
                                            isExpanded = false
                                            TimerForegroundService.stopTimer(context)
                                            scope.launch {
                                                ShutdownManager(context).executeShutdown(mode, fadeAudio = false, enableDnd = false)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = AccentAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text("Tắt ngay", fontSize = 11.5.sp, color = AccentAmber, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            TimerForegroundService.stopTimer(context)
                                            isExpanded = false
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .height(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Stop,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text("Dừng", fontSize = 11.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                // Chưa chạy -> Cho phép bấm hẹn giờ 1 chạm ngay từ giọt nước!
                                Text(
                                    text = "Chạm để bật nhanh hẹn giờ từ Giọt nước:",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(15L, 30L, 45L, 60L).forEach { mins ->
                                        Button(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                TimerForegroundService.startTimer(
                                                    context = context,
                                                    seconds = mins * 60L,
                                                    mode = ShutdownMode.BANK_SAFE_SLEEP
                                                )
                                                isExpanded = false
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentCyan.copy(alpha = 0.2f),
                                                contentColor = AccentCyan
                                            ),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text("${mins}p", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Hàng phụ: Mở màn hình Hẹn giờ hoặc Ẩn xem trước
                            if (onOpenPowerTimerScreen != null || onDismissPreview != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (onOpenPowerTimerScreen != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    isExpanded = false
                                                    onOpenPowerTimerScreen()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.OpenInNew,
                                                contentDescription = null,
                                                tint = AccentCyan,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Mở chi tiết PowerTimer",
                                                color = AccentCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (!timerState.isRunning && onDismissPreview != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    isExpanded = false
                                                    onDismissPreview()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = "Ẩn giọt nước",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyStyleEqualizerBars(
    phase: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val barColors = listOf(AccentGreen, AccentCyan, AccentPurple, AccentPink)
    Canvas(modifier = modifier) {
        val barCount = 4
        val spacing = 3.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())
        for (i in 0 until barCount) {
            val ratio = if (isActive) {
                0.28f + 0.72f * ((sin(phase + i * 1.15f) + 1f) / 2f)
            } else {
                0.25f
            }
            val barH = (size.height * ratio).coerceAtLeast(3.dp.toPx())
            val left = i * (barWidth + spacing)
            val top = (size.height - barH) / 2f
            drawRoundRect(
                color = barColors[i % barColors.size],
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

