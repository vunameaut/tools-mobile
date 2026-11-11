package com.vunameaut.powertimer.ui.screens

import android.content.Context
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class WheelPreset(
    val id: String,
    val title: String,
    val items: List<String>
)

private data class DecisionHistoryRecord(
    val type: String, // "WHEEL", "COIN", "DICE"
    val title: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

private val WHEEL_SLICE_COLORS = listOf(
    Color(0xFFEF4444), // Red
    Color(0xFFF59E0B), // Amber
    Color(0xFF10B981), // Emerald
    Color(0xFF06B6D4), // Cyan
    Color(0xFF3B82F6), // Blue
    Color(0xFF8B5CF6), // Violet
    Color(0xFFEC4899), // Pink
    Color(0xFFF97316), // Orange
    Color(0xFF14B8A6), // Teal
    Color(0xFF6366F1)  // Indigo
)

private val DEFAULT_WHEEL_PRESETS = listOf(
    WheelPreset(
        id = "eat_today",
        title = "🍜 Hôm nay ăn gì?",
        items = listOf(
            "Phở bò",
            "Bún chả",
            "Cơm tấm sườn",
            "Bún bò Huế",
            "Bún đậu mắm tôm",
            "Mì cay",
            "Lẩu nướng",
            "Bánh mì chảo",
            "Gà rán",
            "Cơm rang dưa bò"
        )
    ),
    WheelPreset(
        id = "drink_today",
        title = "🧋 Uống gì chiều nay?",
        items = listOf(
            "Trà sữa trân châu",
            "Cà phê muối",
            "Trà đào cam sả",
            "Matcha Latte",
            "Sinh tố bơ",
            "Nước ép cam",
            "Trà mãng cầu",
            "Bạc xỉu đá"
        )
    ),
    WheelPreset(
        id = "who_pays",
        title = "💸 Ai trả kèo này?",
        items = listOf(
            "Người 1",
            "Người 2",
            "Người 3",
            "Người 4",
            "Chia đều cả nhóm",
            "Quay lại lần nữa!"
        )
    ),
    WheelPreset(
        id = "yes_or_no",
        title = "🎯 Quyết định nhanh",
        items = listOf(
            "Chốt làm luôn! 🔥",
            "Không nên đâu ❌",
            "Từ từ suy nghĩ lại 🤔",
            "Triển ngay 100% ✅",
            "Hỏi đứa bên cạnh 💬",
            "Thử vận may lại 🔄"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RandomDecisionTool(onExit: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    val prefs = remember { context.getSharedPreferences("random_decision_prefs", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val historyRecords = remember { mutableStateListOf<DecisionHistoryRecord>() }

    // ======================== STATE: VÒNG QUAY NGẪU NHIÊN ========================
    var selectedPresetIdx by remember { mutableIntStateOf(0) }
    var wheelItems by remember {
        val savedCustom = prefs.getString("saved_wheel_items_0", null)
        val initial = if (!savedCustom.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(savedCustom)
                List(arr.length()) { arr.getString(it) }.filter { it.isNotBlank() }
            }.getOrNull()?.takeIf { it.size >= 2 } ?: DEFAULT_WHEEL_PRESETS[0].items
        } else {
            DEFAULT_WHEEL_PRESETS[0].items
        }
        mutableStateOf(initial)
    }
    var newOptionInput by remember { mutableStateOf("") }
    var autoRemoveWinner by remember { mutableStateOf(false) }
    var isWheelSpinning by remember { mutableStateOf(false) }
    var wheelWinner by remember { mutableStateOf<String?>(null) }
    val wheelRotation = remember { Animatable(0f) }

    fun saveCurrentWheelItems(presetIndex: Int, items: List<String>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString("saved_wheel_items_$presetIndex", arr.toString()).apply()
    }

    fun selectPreset(idx: Int) {
        selectedPresetIdx = idx
        val saved = prefs.getString("saved_wheel_items_$idx", null)
        val loaded = if (!saved.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(saved)
                List(arr.length()) { arr.getString(it) }.filter { it.isNotBlank() }
            }.getOrNull()?.takeIf { it.size >= 2 }
        } else null
        wheelItems = loaded ?: DEFAULT_WHEEL_PRESETS[idx].items
        wheelWinner = null
    }

    // Phát xung rung và tiếng cạch nhẹ mỗi khi vạch chia ô đi qua kim chỉ 12 giờ
    var lastTickSliceIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(wheelRotation.value, wheelItems.size) {
        if (isWheelSpinning && wheelItems.size >= 2) {
            val sliceAngle = 360f / wheelItems.size
            val normalized = ((270f - (wheelRotation.value % 360f)) + 360f) % 360f
            val currentIdx = (normalized / sliceAngle).toInt() % wheelItems.size
            if (currentIdx != lastTickSliceIndex) {
                lastTickSliceIndex = currentIdx
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    fun spinWheel() {
        if (isWheelSpinning || wheelItems.size < 2) {
            if (wheelItems.size < 2) {
                Toast.makeText(context, "Cần ít nhất 2 lựa chọn trên vòng quay!", Toast.LENGTH_SHORT).show()
            }
            return
        }
        scope.launch {
            isWheelSpinning = true
            wheelWinner = null
            val extraSpins = Random.nextInt(6, 10) * 360f
            val randomAngle = Random.nextFloat() * 360f
            val targetAngle = wheelRotation.value + extraSpins + randomAngle

            wheelRotation.animateTo(
                targetValue = targetAngle,
                animationSpec = tween(
                    durationMillis = 3600,
                    easing = CubicBezierEasing(0.12f, 0.84f, 0.18f, 1.0f)
                )
            )

            // Kim chỉ ở góc 270 độ (hướng 12 giờ trên Canvas)
            val sliceAngle = 360f / wheelItems.size
            val finalMod = ((wheelRotation.value % 360f) + 360f) % 360f
            val pointerInWheelSpace = ((270f - finalMod) + 360f) % 360f
            val winnerIndex = (pointerInWheelSpace / sliceAngle).toInt().coerceIn(0, wheelItems.lastIndex)
            val chosen = wheelItems[winnerIndex]

            wheelWinner = chosen
            isWheelSpinning = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                    .startTone(ToneGenerator.TONE_PROP_ACK, 160)
            }

            historyRecords.add(
                0,
                DecisionHistoryRecord(
                    type = "WHEEL",
                    title = DEFAULT_WHEEL_PRESETS[selectedPresetIdx].title,
                    result = chosen
                )
            )
        }
    }

    // ======================== STATE: TUNG ĐỒNG XU ========================
    var headsLabel by remember { mutableStateOf("Mặt Ngửa (CHỐT / CÓ)") }
    var tailsLabel by remember { mutableStateOf("Mặt Sấp (KHÔNG / ĐỔI Ý)") }
    var isCoinFlipping by remember { mutableStateOf(false) }
    var coinIsHeads by remember { mutableStateOf(true) }
    var coinResultText by remember { mutableStateOf<String?>(null) }
    var headsCount by remember { mutableIntStateOf(0) }
    var tailsCount by remember { mutableIntStateOf(0) }
    val coinFlipAnim = remember { Animatable(0f) }

    fun flipCoin() {
        if (isCoinFlipping) return
        scope.launch {
            isCoinFlipping = true
            coinResultText = null
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

            val outcomeIsHeads = Random.nextBoolean()
            val currentHalfTurns = (coinFlipAnim.value / 180f).toInt()
            val extraHalfTurns = Random.nextInt(10, 15)
            var targetHalfTurns = currentHalfTurns + extraHalfTurns
            // Chẵn = Ngửa (0 deg mod 360), Lẻ = Sấp (180 deg mod 360)
            if (outcomeIsHeads && targetHalfTurns % 2 != 0) targetHalfTurns += 1
            if (!outcomeIsHeads && targetHalfTurns % 2 == 0) targetHalfTurns += 1

            coinFlipAnim.animateTo(
                targetValue = targetHalfTurns * 180f,
                animationSpec = tween(durationMillis = 1550, easing = FastOutSlowInEasing)
            )

            coinIsHeads = outcomeIsHeads
            val winnerLabel = if (outcomeIsHeads) headsLabel else tailsLabel
            coinResultText = winnerLabel
            if (outcomeIsHeads) headsCount++ else tailsCount++
            isCoinFlipping = false

            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                    .startTone(ToneGenerator.TONE_PROP_ACK, 140)
            }
            historyRecords.add(
                0,
                DecisionHistoryRecord(
                    type = "COIN",
                    title = "🪙 Tung Đồng Xu",
                    result = winnerLabel
                )
            )
        }
    }

    // ======================== STATE: ĐỔ XÚC XẮC ========================
    var diceCount by remember { mutableIntStateOf(2) }
    var diceValues by remember { mutableStateOf(listOf(6, 6)) }
    var isDiceRolling by remember { mutableStateOf(false) }

    fun rollDice() {
        if (isDiceRolling) return
        scope.launch {
            isDiceRolling = true
            repeat(12) {
                diceValues = List(diceCount) { Random.nextInt(1, 7) }
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(65L)
            }
            val finalRoll = List(diceCount) { Random.nextInt(1, 7) }
            diceValues = finalRoll
            isDiceRolling = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                    .startTone(ToneGenerator.TONE_PROP_ACK, 140)
            }

            val sum = finalRoll.sum()
            historyRecords.add(
                0,
                DecisionHistoryRecord(
                    type = "DICE",
                    title = "🎲 Đổ $diceCount viên xúc xắc",
                    result = "Tổng: $sum điểm (${finalRoll.joinToString(" + ")})"
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Vòng Quay & Ra Quyết Định",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Hôm nay ăn gì? • Vòng quay may mắn • Đồng xu • Xúc xắc",
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
        ) {
            // Thanh chuyển Tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    0 to "🎡 Vòng Quay",
                    1 to "🪙 Tung Đồng Xu",
                    2 to "🎲 Đổ Xúc Xắc",
                    3 to "🕘 Lịch sử (${historyRecords.size})"
                )
                tabs.forEach { (idx, label) ->
                    val selected = selectedTab == idx
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTab = idx },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentAmber,
                            selectedLabelColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    // ========================= TAB 0: VÒNG QUAY NGẪU NHIÊN =========================
                    0 -> {
                        // Chọn chủ đề gợi ý
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(DEFAULT_WHEEL_PRESETS) { idx, preset ->
                                val isSelected = selectedPresetIdx == idx
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { if (!isWheelSpinning) selectPreset(idx) },
                                    label = {
                                        Text(
                                            text = preset.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentCyan,
                                        selectedLabelColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        // Vòng quay Canvas chính giữa
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(300.dp)
                                        .clickable(enabled = !isWheelSpinning) { spinWheel() }
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val wheelDiameter = size.minDimension - 24.dp.toPx()
                                        val radius = wheelDiameter / 2f
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val topLeft = Offset(center.x - radius, center.y - radius)

                                        // Vòng viền ngoài phát sáng
                                        drawCircle(
                                            color = Color(0xFF1E293B),
                                            radius = radius + 8.dp.toPx(),
                                            center = center
                                        )
                                        drawCircle(
                                            color = AccentAmber,
                                            radius = radius + 8.dp.toPx(),
                                            center = center,
                                            style = Stroke(width = 3.dp.toPx())
                                        )

                                        val count = wheelItems.size.coerceAtLeast(1)
                                        val sweep = 360f / count

                                        rotate(degrees = wheelRotation.value, pivot = center) {
                                            wheelItems.forEachIndexed { index, itemText ->
                                                val startAngle = index * sweep
                                                val sliceColor = WHEEL_SLICE_COLORS[index % WHEEL_SLICE_COLORS.size]

                                                drawArc(
                                                    color = sliceColor,
                                                    startAngle = startAngle,
                                                    sweepAngle = sweep,
                                                    useCenter = true,
                                                    topLeft = topLeft,
                                                    size = Size(wheelDiameter, wheelDiameter)
                                                )
                                                drawArc(
                                                    color = Color.White.copy(alpha = 0.35f),
                                                    startAngle = startAngle,
                                                    sweepAngle = sweep,
                                                    useCenter = true,
                                                    topLeft = topLeft,
                                                    size = Size(wheelDiameter, wheelDiameter),
                                                    style = Stroke(width = 1.5.dp.toPx())
                                                )

                                                // Vẽ tên lựa chọn hướng vào tâm
                                                val midAngleDeg = startAngle + sweep / 2f
                                                drawContext.canvas.nativeCanvas.apply {
                                                    save()
                                                    translate(center.x, center.y)
                                                    rotate(midAngleDeg)
                                                    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                                        color = android.graphics.Color.WHITE
                                                        textSize = if (count > 8) 11.5.sp.toPx() else 13.sp.toPx()
                                                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                                        textAlign = AndroidPaint.Align.RIGHT
                                                        setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(140, 0, 0, 0))
                                                    }
                                                    val label = if (itemText.length > 15) itemText.take(14) + "…" else itemText
                                                    val yOffset = -(textPaint.descent() + textPaint.ascent()) / 2f
                                                    drawText(label, radius * 0.88f, yOffset, textPaint)
                                                    restore()
                                                }
                                            }
                                        }

                                        // Nút tròn QUAY ở tâm vòng quay
                                        drawCircle(
                                            color = Color(0xFF0F172A),
                                            radius = 34.dp.toPx(),
                                            center = center
                                        )
                                        drawCircle(
                                            color = AccentAmber,
                                            radius = 34.dp.toPx(),
                                            center = center,
                                            style = Stroke(width = 3.dp.toPx())
                                        )
                                        drawContext.canvas.nativeCanvas.apply {
                                            val centerTextPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                                color = AccentAmber.toArgb()
                                                textSize = 13.sp.toPx()
                                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                                textAlign = AndroidPaint.Align.CENTER
                                            }
                                            val yOff = -(centerTextPaint.descent() + centerTextPaint.ascent()) / 2f
                                            drawText(if (isWheelSpinning) "..." else "QUAY", center.x, center.y + yOff, centerTextPaint)
                                        }

                                        // Kim chỉ (Pointer) ở đỉnh 12 giờ
                                        val pointerPath = Path().apply {
                                            val tipY = topLeft.y + 14.dp.toPx()
                                            val topY = topLeft.y - 12.dp.toPx()
                                            val halfW = 13.dp.toPx()
                                            moveTo(center.x, tipY)
                                            lineTo(center.x - halfW, topY)
                                            lineTo(center.x + halfW, topY)
                                            close()
                                        }
                                        drawPath(path = pointerPath, color = Color(0xFFFACC15))
                                        drawPath(
                                            path = pointerPath,
                                            color = Color(0xFF0F172A),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                }

                                // Kết quả vừa trúng
                                if (wheelWinner != null) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = AccentGreen.copy(alpha = 0.16f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.5.dp, AccentGreen, RoundedCornerShape(18.dp))
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "🎉 KẾT QUẢ ĐƯỢC CHỌN:",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentGreen
                                            )
                                            Text(
                                                text = wheelWinner ?: "",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                            if (wheelItems.size > 2) {
                                                TextButton(
                                                    onClick = {
                                                        val target = wheelWinner
                                                        if (target != null && wheelItems.size > 2) {
                                                            wheelItems = wheelItems.filterNot { it == target }
                                                            wheelWinner = null
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = "Loại bỏ lựa chọn này để quay tiếp",
                                                        color = AccentAmber,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (autoRemoveWinner && wheelWinner != null && wheelItems.size > 2) {
                                            wheelItems = wheelItems.filterNot { it == wheelWinner }
                                        }
                                        spinWheel()
                                    },
                                    enabled = !isWheelSpinning && wheelItems.size >= 2,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentAmber,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text(
                                        text = if (isWheelSpinning) "🎡 Đang quay..." else "🎡 QUAY NGẪU NHIÊN NGAY!",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Quản lý danh sách lựa chọn trên Vòng Quay
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📝 Các lựa chọn trên vòng quay (${wheelItems.size}):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = AccentCyan
                                    )
                                    TextButton(
                                        onClick = {
                                            val defaultItems = DEFAULT_WHEEL_PRESETS[selectedPresetIdx].items
                                            wheelItems = defaultItems
                                            saveCurrentWheelItems(selectedPresetIdx, defaultItems)
                                            wheelWinner = null
                                            Toast.makeText(context, "Đã khôi phục danh sách gốc!", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Rounded.Restore, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Khôi phục gốc", color = AccentAmber, fontSize = 11.5.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newOptionInput,
                                        onValueChange = { newOptionInput = it },
                                        label = { Text("Thêm món / lựa chọn mới...") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val added = newOptionInput.split(',', '\n')
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }
                                            if (added.isNotEmpty()) {
                                                val updated = (wheelItems + added).distinct()
                                                wheelItems = updated
                                                saveCurrentWheelItems(selectedPresetIdx, updated)
                                                newOptionInput = ""
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentGreen,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Thêm", fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    wheelItems.forEachIndexed { idx, item ->
                                        val dotColor = WHEEL_SLICE_COLORS[idx % WHEEL_SLICE_COLORS.size]
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.border(
                                                1.dp,
                                                dotColor.copy(alpha = 0.6f),
                                                RoundedCornerShape(50)
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(dotColor)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = item,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Xóa",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            if (wheelItems.size > 2) {
                                                                val updated = wheelItems.filterIndexed { i, _ -> i != idx }
                                                                wheelItems = updated
                                                                saveCurrentWheelItems(selectedPresetIdx, updated)
                                                            } else {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Vòng quay cần giữ lại tối thiểu 2 lựa chọn!",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tự động loại bỏ lựa chọn đã trúng ở lần quay sau",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = autoRemoveWinner,
                                        onCheckedChange = { autoRemoveWinner = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = AccentGreen
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // ========================= TAB 1: TUNG ĐỒNG XU 3D =========================
                    1 -> {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "🪙 Tung Đồng Xu Quyết Định 50 / 50",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = AccentAmber
                                )

                                // Đồng xu 3D
                                val angleMod = ((coinFlipAnim.value % 360f) + 360f) % 360f
                                val showingHeadsNow = angleMod < 90f || angleMod >= 270f
                                val bounceScale = if (isCoinFlipping) {
                                    val progress = (coinFlipAnim.value % 180f) / 180f
                                    1f + 0.18f * sin(progress * Math.PI.toFloat())
                                } else 1f

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(190.dp)
                                        .graphicsLayer {
                                            rotationX = coinFlipAnim.value
                                            scaleX = bounceScale
                                            scaleY = bounceScale
                                            cameraDistance = 14f * density
                                        }
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                if (showingHeadsNow) {
                                                    listOf(Color(0xFFFDE047), Color(0xFFD97706), Color(0xFF92400E))
                                                } else {
                                                    listOf(Color(0xFFE2E8F0), Color(0xFF64748B), Color(0xFF1E293B))
                                                }
                                            )
                                        )
                                        .border(
                                            6.dp,
                                            if (showingHeadsNow) Color(0xFFFACC15) else Color(0xFF94A3B8),
                                            CircleShape
                                        )
                                        .clickable(enabled = !isCoinFlipping) { flipCoin() }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.graphicsLayer {
                                            // Đảo chiều chữ khi mặt sau lật lên để chữ không bị ngược
                                            if (!showingHeadsNow) {
                                                rotationX = 180f
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (showingHeadsNow) "👑" else "🛡️",
                                            fontSize = 44.sp
                                        )
                                        Text(
                                            text = if (showingHeadsNow) "NGỬA" else "SẤP",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 24.sp,
                                            color = if (showingHeadsNow) Color(0xFF451A03) else Color.White
                                        )
                                        Text(
                                            text = if (showingHeadsNow) "HEADS" else "TAILS",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (showingHeadsNow) Color(0xFF78350F) else Color(0xFFCBD5E1)
                                        )
                                    }
                                }

                                if (coinResultText != null) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = AccentGreen.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, AccentGreen, RoundedCornerShape(16.dp))
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            Text(
                                                text = "KẾT QUẢ ĐỒNG XU:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentGreen
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = coinResultText ?: "",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Text(
                                        text = "👑 Ngửa: $headsCount lần",
                                        fontWeight = FontWeight.Bold,
                                        color = AccentAmber,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "🛡️ Sấp: $tailsCount lần",
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan,
                                        fontSize = 13.sp
                                    )
                                }

                                Button(
                                    onClick = { flipCoin() },
                                    enabled = !isCoinFlipping,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentAmber,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Icon(Icons.Rounded.MonetizationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isCoinFlipping) "Đang tung đồng xu..." else "🪙 TUNG ĐỒNG XU NGAY!",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }

                                // Tùy chỉnh ý nghĩa 2 mặt đồng xu
                                OutlinedTextField(
                                    value = headsLabel,
                                    onValueChange = { headsLabel = it },
                                    label = { Text("Ý nghĩa Mặt Ngửa (👑)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = tailsLabel,
                                    onValueChange = { tailsLabel = it },
                                    label = { Text("Ý nghĩa Mặt Sấp (🛡️)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // ========================= TAB 2: ĐỔ XÚC XẮC =========================
                    2 -> {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "🎲 Chọn số lượng Xúc Xắc (1 – 6 viên):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = AccentCyan
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    (1..6).forEach { count ->
                                        val selected = diceCount == count
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                if (!isDiceRolling) {
                                                    diceCount = count
                                                    diceValues = List(count) { Random.nextInt(1, 7) }
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = "$count viên",
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AccentCyan,
                                                selectedLabelColor = Color.Black
                                            )
                                        )
                                    }
                                }

                                // Bàn đổ xúc xắc
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    diceValues.forEach { faceValue ->
                                        DiceFaceView(
                                            value = faceValue,
                                            modifier = Modifier
                                                .size(88.dp)
                                                .clickable(enabled = !isDiceRolling) { rollDice() }
                                        )
                                    }
                                }

                                // Tổng điểm & Nhận diện
                                val sum = diceValues.sum()
                                val allSame = diceValues.size >= 2 && diceValues.distinct().size == 1
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, AccentCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "TỔNG ĐIỂM: $sum",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp,
                                            color = AccentAmber
                                        )
                                        Text(
                                            text = buildString {
                                                append("Chi tiết: ${diceValues.joinToString(" + ")}")
                                                append(" • ")
                                                append(if (sum % 2 == 0) "CHẴN" else "LẺ")
                                                if (diceCount == 3) {
                                                    append(" • ")
                                                    append(
                                                        when {
                                                            allSame -> "🔥 BÃO (TAM HOA ${diceValues.first()})"
                                                            sum in 11..17 -> "TÀI (11-17)"
                                                            else -> "XỈU (4-10)"
                                                        }
                                                    )
                                                } else if (allSame) {
                                                    append(" • 🔥 ĐỒNG CHẤT!")
                                                }
                                            },
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Button(
                                    onClick = { rollDice() },
                                    enabled = !isDiceRolling,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCyan,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isDiceRolling) "🎲 Đang lắc xúc xắc..." else "🎲 LẮC XÚC XẮC NGAY!",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // ========================= TAB 3: LỊCH SỬ KẾT QUẢ =========================
                    3 -> {
                        if (historyRecords.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text("Chưa có lượt quay hoặc tung đồng xu / xúc xắc nào.")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lịch sử quyết định gần đây (${historyRecords.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentAmber
                                )
                                TextButton(onClick = { historyRecords.clear() }) {
                                    Text("Xóa lịch sử", color = AccentRed, fontSize = 12.sp)
                                }
                            }

                            val fmt = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }
                            historyRecords.forEach { rec ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rec.title,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = rec.result,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = AccentGreen
                                            )
                                        }
                                        Text(
                                            text = fmt.format(Date(rec.timestamp)),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = AccentCyan
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

@Composable
private fun DiceFaceView(
    value: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = modifier.border(2.dp, Color(0xFFCBD5E1), RoundedCornerShape(20.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val dotRadius = if (value == 1) w * 0.13f else w * 0.095f
            val dotColor = if (value == 1 || value == 4) Color(0xFFDC2626) else Color(0xFF0F172A)

            val cX = w * 0.5f
            val cY = h * 0.5f
            val lX = w * 0.26f
            val rX = w * 0.74f
            val tY = h * 0.26f
            val bY = h * 0.74f

            val positions = when (value) {
                1 -> listOf(Offset(cX, cY))
                2 -> listOf(Offset(lX, tY), Offset(rX, bY))
                3 -> listOf(Offset(lX, tY), Offset(cX, cY), Offset(rX, bY))
                4 -> listOf(Offset(lX, tY), Offset(rX, tY), Offset(lX, bY), Offset(rX, bY))
                5 -> listOf(Offset(lX, tY), Offset(rX, tY), Offset(cX, cY), Offset(lX, bY), Offset(rX, bY))
                else -> listOf(
                    Offset(lX, tY), Offset(rX, tY),
                    Offset(lX, cY), Offset(rX, cY),
                    Offset(lX, bY), Offset(rX, bY)
                )
            }

            positions.forEach { pos ->
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = pos
                )
            }
        }
    }
}

