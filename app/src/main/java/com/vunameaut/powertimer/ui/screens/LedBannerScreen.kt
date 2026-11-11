package com.vunameaut.powertimer.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink

private val LED_PRESET_TEXTS = listOf(
    "EM ƠI TÍNH TIỀN BÀN NÀY! 🍻",
    "ĐÓN ANH NAM - BẾN XE 🚌",
    "QUẨY LÊN ANH EM ƠI 🔥🔥🔥",
    "CHÚC MỪNG SINH NHẬT 🎉🎂",
    "CHO XIN THÊM ĐÁ & BIA 🍺",
    "I ❤️ YOU SO MUCH ✨"
)

private val LED_TEXT_COLORS = listOf(
    0xFF00FF66L to "Xanh lục Neon",
    0xFF00F0FFL to "Xanh Cyan",
    0xFFFF007FL to "Hồng Idol",
    0xFFFFEA00L to "Vàng rực",
    0xFFFF2A2AL to "Đỏ LED",
    0xFFFFFFFFL to "Trắng sáng",
    0xFFA855F7L to "Tím Neon"
)

private val LED_BG_COLORS = listOf(
    0xFF050505L to "Đen OLED",
    0xFF09152EL to "Xanh Đêm",
    0xFF280618L to "Tím Than",
    0xFF2B0707L to "Đỏ Thẫm"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedBannerTool(onExit: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    var bannerText by rememberSaveable { mutableStateOf("QUẨY LÊN ANH EM ƠI 🔥🔥🔥") }
    var textColorHex by rememberSaveable { mutableLongStateOf(0xFF00FF66L) }
    var bgColorHex by rememberSaveable { mutableLongStateOf(0xFF050505L) }
    var speedMultiplier by rememberSaveable { mutableFloatStateOf(1.4f) } // 0f = đứng yên, 0.5f..3.5f
    var fontScale by rememberSaveable { mutableFloatStateOf(0.68f) } // tỷ lệ theo chiều cao khung
    var dotMatrixOverlay by rememberSaveable { mutableStateOf(true) }
    var strobeBlink by rememberSaveable { mutableStateOf(false) }
    var isFullScreenLandscape by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (isFullScreenLandscape) {
            isFullScreenLandscape = false
        } else {
            onExit()
        }
    }

    // Tự động xoay ngang màn hình & đẩy độ sáng 100% khi bật Toàn Màn Hình
    LaunchedEffect(isFullScreenLandscape) {
        val window = activity?.window ?: return@LaunchedEffect
        if (isFullScreenLandscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val params = window.attributes
            params.screenBrightness = 1.0f
            window.attributes = params

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val params = window.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = params

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window
            if (activity != null && window != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (isFullScreenLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(bgColorHex))
                .clickable { isFullScreenLandscape = false }
        ) {
            LedMarqueeCanvas(
                text = bannerText.ifBlank { "TOOLS HUB LED" },
                textColor = Color(textColorHex),
                bgColor = Color(bgColorHex),
                speedMultiplier = speedMultiplier,
                fontHeightFraction = fontScale,
                dotMatrixOverlay = dotMatrixOverlay,
                strobeBlink = strobeBlink,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clickable { isFullScreenLandscape = false }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloseFullscreen,
                        contentDescription = "Thu nhỏ",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Chạm để thoát",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Bảng Chữ LED (LED Banner)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Chữ chạy siêu sáng toàn màn hình • Đi concert, bến xe, quán bar",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Khung xem trước trực tiếp Bảng LED
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.dp, Color(textColorHex).copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                    .clickable { isFullScreenLandscape = true }
            ) {
                LedMarqueeCanvas(
                    text = bannerText.ifBlank { "NHẬP CHỮ ĐỂ CHẠY LED..." },
                    textColor = Color(textColorHex),
                    bgColor = Color(bgColorHex),
                    speedMultiplier = speedMultiplier,
                    fontHeightFraction = fontScale,
                    dotMatrixOverlay = dotMatrixOverlay,
                    strobeBlink = strobeBlink,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Bấm để Phóng to Ngang",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Button(
                onClick = { isFullScreenLandscape = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "🚀 BẬT TOÀN MÀN HÌNH (XOAY NGANG 100% SÁNG)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Nhập nội dung & Câu mẫu nhanh
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = bannerText,
                        onValueChange = { bannerText = it },
                        label = { Text("Nhập nội dung chữ chạy LED") },
                        placeholder = { Text("VD: Đón anh Nam • Tính tiền bàn 5...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "⚡ Câu mẫu nhanh (1 chạm):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(LED_PRESET_TEXTS) { sample ->
                            FilterChip(
                                selected = bannerText == sample,
                                onClick = { bannerText = sample },
                                label = {
                                    Text(sample, fontSize = 12.sp, maxLines = 1)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            // Tùy chỉnh màu sắc, tốc độ, cỡ chữ và hiệu ứng LED
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "🎨 Màu chữ LED siêu sáng:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AccentAmber
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        LED_TEXT_COLORS.forEach { (hex, _) ->
                            val selected = textColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable { textColorHex = hex }
                            )
                        }
                    }

                    Text(
                        text = "🌑 Màu nền bảng LED:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AccentCyan
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LED_BG_COLORS.forEach { (hex, name) ->
                            val selected = bgColorHex == hex
                            FilterChip(
                                selected = selected,
                                onClick = { bgColorHex = hex },
                                label = { Text(name, fontSize = 12.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("⏩ Tốc độ chạy chữ:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (speedMultiplier <= 0.05f) "Đứng yên" else String.format("%.1fx", speedMultiplier),
                                fontSize = 12.5.sp,
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = speedMultiplier,
                            onValueChange = { speedMultiplier = it },
                            valueRange = 0f..3.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentGreen,
                                activeTrackColor = AccentGreen
                            )
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🔠 Kích thước chữ:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${(fontScale * 100).toInt()}%",
                                fontSize = 12.5.sp,
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = fontScale,
                            onValueChange = { fontScale = it },
                            valueRange = 0.35f..0.88f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🔵 Hiệu ứng mắt lưới bóng đèn LED Matrix", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = dotMatrixOverlay,
                            onCheckedChange = { dotMatrixOverlay = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentGreen
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("⚡ Hiệu ứng nhấp nháy gây chú ý (Strobe Blink)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = strobeBlink,
                            onCheckedChange = { strobeBlink = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentPink
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LedMarqueeCanvas(
    text: String,
    textColor: Color,
    bgColor: Color,
    speedMultiplier: Float,
    fontHeightFraction: Float,
    dotMatrixOverlay: Boolean,
    strobeBlink: Boolean,
    modifier: Modifier = Modifier
) {
    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(speedMultiplier, text) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L && speedMultiplier > 0.05f) {
                    val dtSec = (nanos - lastNanos) / 1_000_000_000f
                    scrollOffsetPx -= dtSec * 360f * speedMultiplier
                }
                lastNanos = nanos
            }
        }
    }

    val blinkTransition = rememberInfiniteTransition(label = "led_blink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (strobeBlink) 0.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink_alpha"
    )

    Canvas(modifier = modifier.background(bgColor)) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = textColor.copy(alpha = if (strobeBlink) blinkAlpha else 1f).toArgb()
            textSize = h * fontHeightFraction
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(24f, 0f, 0f, textColor.toArgb())
        }

        val displayStr = "$text     •     "
        val textWidth = paint.measureText(displayStr).coerceAtLeast(100f)
        val centerY = h / 2f - (paint.descent() + paint.ascent()) / 2f

        if (speedMultiplier <= 0.05f) {
            // Đứng yên căn giữa
            paint.textAlign = AndroidPaint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(text, w / 2f, centerY, paint)
        } else {
            paint.textAlign = AndroidPaint.Align.LEFT
            val normalizedOffset = ((scrollOffsetPx % textWidth) + textWidth) % textWidth - textWidth
            var x = normalizedOffset
            while (x < w + textWidth) {
                drawContext.canvas.nativeCanvas.drawText(displayStr, x, centerY, paint)
                x += textWidth
            }
        }

        // Vẽ lưới mắt cáo mô phỏng bóng đèn LED Matrix thực tế
        if (dotMatrixOverlay) {
            val step = 6.dp.toPx().coerceAtLeast(8f)
            val holeColor = bgColor.copy(alpha = 0.55f)
            var gx = 0f
            while (gx < w) {
                drawLine(
                    color = holeColor,
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 2.dp.toPx()
                )
                gx += step
            }
            var gy = 0f
            while (gy < h) {
                drawLine(
                    color = holeColor,
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 2.dp.toPx()
                )
                gy += step
            }
        }
    }
}

