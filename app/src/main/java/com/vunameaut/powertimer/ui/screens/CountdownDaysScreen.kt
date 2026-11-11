package com.vunameaut.powertimer.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayEventItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val emoji: String,
    val year: Int,
    val month: Int, // 1..12
    val day: Int,   // 1..31
    val isCountUp: Boolean = false, // false = Đếm ngược tới tương lai, true = Đếm số ngày đã qua (Yêu nhau / Kỷ niệm)
    val repeatMode: String = "NONE", // "NONE", "MONTHLY", "YEARLY"
    val colorHex: Long = 0xFF00F0FFL
)

private val EVENT_EMOJIS = listOf("🧧", "💰", "🎂", "❤️", "💍", "✈️", "🎄", "🎓", "🏖️", "🔥")

private val EVENT_COLORS = listOf(
    0xFFEF4444L, // Đỏ Tết
    0xFFF59E0BL, // Vàng Lương
    0xFFEC4899L, // Hồng Tình Yêu
    0xFF06B6D4L, // Xanh Cyan
    0xFF10B981L, // Xanh Ngọc
    0xFF8B5CF6L  // Tím
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownDaysTool(onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)

    var events by remember { mutableStateOf(loadOrInitDayEvents(context)) }
    var selectedEventId by remember(events) {
        mutableLongStateOf(events.firstOrNull()?.id ?: 0L)
    }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Cập nhật đồng hồ đếm ngược mỗi giây
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // Form tạo sự kiện mới
    var showAddForm by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf("🎂") }
    var newColorHex by remember { mutableLongStateOf(0xFF06B6D4L) }
    var newIsCountUp by remember { mutableStateOf(false) }
    var newRepeatMode by remember { mutableStateOf("NONE") }

    val initCal = remember { Calendar.getInstance() }
    var pickedYear by remember { mutableStateOf(initCal.get(Calendar.YEAR)) }
    var pickedMonth by remember { mutableStateOf(initCal.get(Calendar.MONTH) + 1) }
    var pickedDay by remember { mutableStateOf(initCal.get(Calendar.DAY_OF_MONTH)) }

    fun openDatePicker() {
        DatePickerDialog(
            context,
            { _, y, m, d ->
                pickedYear = y
                pickedMonth = m + 1
                pickedDay = d
            },
            pickedYear,
            pickedMonth - 1,
            pickedDay
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Đếm Ngày & Kỷ Niệm (Countdown)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Đếm ngược tới Tết, Lương về, Sinh nhật • Đếm ngày yêu nhau",
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
            val activeEvent = events.firstOrNull { it.id == selectedEventId } ?: events.firstOrNull()

            // Thẻ đồng hồ đếm ngược trực tiếp (Hero Card) cho sự kiện đang chọn
            if (activeEvent != null) {
                HeroEventCountdownCard(
                    event = activeEvent,
                    nowMillis = nowMillis
                )
            }

            // Nút mở khung Thêm cột mốc / sự kiện mới
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 Danh sách sự kiện của bạn (${events.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AccentCyan
                )
                Button(
                    onClick = { showAddForm = !showAddForm },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showAddForm) AccentAmber else AccentGreen,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (showAddForm) "Đóng khung thêm" else "Thêm sự kiện",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (showAddForm) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "✨ Tạo Sự Kiện Đếm Ngày Mới",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = AccentGreen
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !newIsCountUp,
                                onClick = { newIsCountUp = false },
                                label = { Text("⏳ Đếm ngược ngày tới", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                            FilterChip(
                                selected = newIsCountUp,
                                onClick = {
                                    newIsCountUp = true
                                    newRepeatMode = "NONE"
                                    newEmoji = "❤️"
                                    newColorHex = 0xFFEC4899L
                                },
                                label = { Text("❤️ Đếm ngày yêu / đã qua", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentPink,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = {
                                Text(
                                    if (newIsCountUp) "Tên kỷ niệm (VD: Ngày bắt đầu yêu nhau ❤️)"
                                    else "Tên sự kiện (VD: Sinh nhật người yêu, Du lịch Đà Lạt...)"
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Chọn ngày tháng năm
                        OutlinedButton(
                            onClick = { openDatePicker() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = String.format(
                                    Locale.getDefault(),
                                    "Ngày đã chọn: %02d/%02d/%04d (Chạm để đổi)",
                                    pickedDay,
                                    pickedMonth,
                                    pickedYear
                                ),
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!newIsCountUp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Lặp lại:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                listOf(
                                    "NONE" to "Một lần",
                                    "MONTHLY" to "Hàng tháng (Lương)",
                                    "YEARLY" to "Hàng năm (Sinh nhật)"
                                ).forEach { (mode, label) ->
                                    FilterChip(
                                        selected = newRepeatMode == mode,
                                        onClick = { newRepeatMode = mode },
                                        label = { Text(label, fontSize = 11.5.sp, maxLines = 1) }
                                    )
                                }
                            }
                        }

                        // Chọn biểu tượng Emoji & Màu sắc
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(EVENT_EMOJIS) { emo ->
                                val selected = newEmoji == emo
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) AccentCyan.copy(alpha = 0.25f)
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) AccentCyan else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { newEmoji = emo }
                                ) {
                                    Text(emo, fontSize = 18.sp)
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Màu thẻ:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            EVENT_COLORS.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (newColorHex == hex) 2.5.dp else 1.dp,
                                            color = if (newColorHex == hex) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { newColorHex = hex }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (newTitle.isBlank()) {
                                    Toast.makeText(context, "Vui lòng nhập tên sự kiện!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val item = DayEventItem(
                                    title = newTitle.trim(),
                                    emoji = newEmoji,
                                    year = pickedYear,
                                    month = pickedMonth,
                                    day = pickedDay,
                                    isCountUp = newIsCountUp,
                                    repeatMode = if (newIsCountUp) "NONE" else newRepeatMode,
                                    colorHex = newColorHex
                                )
                                val updated = listOf(item) + events
                                events = updated
                                saveDayEvents(context, updated)
                                selectedEventId = item.id
                                newTitle = ""
                                showAddForm = false
                                Toast.makeText(context, "Đã lưu sự kiện đếm ngày!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text("Lưu Sự Kiện Đếm Ngày", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // Danh sách tất cả các thẻ sự kiện
            events.forEach { ev ->
                val isSelected = ev.id == activeEvent?.id
                val targetMs = computeTargetTimestamp(ev, nowMillis)
                val diffMs = if (ev.isCountUp) {
                    (nowMillis - targetMs).coerceAtLeast(0L)
                } else {
                    (targetMs - nowMillis).coerceAtLeast(0L)
                }
                val daysCount = (diffMs / (1000L * 60 * 60 * 24)).toInt()
                val accent = Color(ev.colorHex)

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) {
                        accent.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accent else accent.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedEventId = ev.id }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.22f))
                        ) {
                            Text(text = ev.emoji, fontSize = 24.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ev.title,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val targetFmt = remember(targetMs) {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(targetMs))
                            }
                            Text(
                                text = if (ev.isCountUp) {
                                    "Từ ngày $targetFmt • Kỷ niệm"
                                } else {
                                    "Mốc: $targetFmt" + when (ev.repeatMode) {
                                        "MONTHLY" -> " (Hàng tháng)"
                                        "YEARLY" -> " (Hàng năm)"
                                        else -> ""
                                    }
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (ev.isCountUp) "${daysCount + 1}" else "$daysCount",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Monospace,
                                color = accent
                            )
                            Text(
                                text = if (ev.isCountUp) "ngày bên nhau" else "ngày nữa",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (events.size > 1) {
                            IconButton(
                                onClick = {
                                    val updated = events.filterNot { it.id == ev.id }
                                    events = updated
                                    saveDayEvents(context, updated)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Xóa",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroEventCountdownCard(
    event: DayEventItem,
    nowMillis: Long
) {
    val accent = Color(event.colorHex)
    val targetMs = computeTargetTimestamp(event, nowMillis)
    val diffMs = if (event.isCountUp) {
        (nowMillis - targetMs).coerceAtLeast(0L)
    } else {
        (targetMs - nowMillis).coerceAtLeast(0L)
    }

    val totalSec = diffMs / 1000L
    val days = (totalSec / 86400L).toInt()
    val hours = ((totalSec % 86400L) / 3600L).toInt()
    val minutes = ((totalSec % 3600L) / 60L).toInt()
    val seconds = (totalSec % 60L).toInt()

    val dateStr = remember(targetMs) {
        SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")).format(Date(targetMs))
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.28f),
                        Color(0xFF0F172A),
                        accent.copy(alpha = 0.14f)
                    )
                )
            )
            .border(2.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = accent.copy(alpha = 0.2f)
            ) {
                Text(
                    text = if (event.isCountUp) "❤️ ĐẾM SỐ NGÀY ĐÃ QUA / YÊU NHAU" else "⏳ ĐẾM NGƯỢC TỚI SỰ KIỆN",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "${event.emoji} ${event.title}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            Text(
                text = "Ngày mốc: $dateStr",
                fontSize = 12.5.sp,
                color = Color(0xFFCBD5E1)
            )

            Spacer(Modifier.height(4.dp))

            // 4 ô Ngày : Giờ : Phút : Giây
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeUnitBox(
                    value = if (event.isCountUp) days + 1 else days,
                    label = "NGÀY",
                    accent = accent,
                    modifier = Modifier.weight(1.2f)
                )
                TimeUnitBox(
                    value = hours,
                    label = "GIỜ",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                TimeUnitBox(
                    value = minutes,
                    label = "PHÚT",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                TimeUnitBox(
                    value = seconds,
                    label = "GIÂY",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }

            if (event.isCountUp && days >= 30) {
                val years = days / 365
                val remDaysAfterYears = days % 365
                val months = remDaysAfterYears / 30
                val remDays = remDaysAfterYears % 30
                Text(
                    text = buildString {
                        append("Tương đương: ")
                        if (years > 0) append("$years năm, ")
                        if (months > 0) append("$months tháng, ")
                        append("${remDays + 1} ngày bên nhau 💕")
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentAmber
                )
            }
        }
    }
}

@Composable
private fun TimeUnitBox(
    value: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.45f),
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%02d", value),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

private fun computeTargetTimestamp(event: DayEventItem, nowMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (event.isCountUp) {
        cal.set(event.year, (event.month - 1).coerceIn(0, 11), event.day.coerceIn(1, 31))
        return cal.timeInMillis
    }

    when (event.repeatMode) {
        "MONTHLY" -> {
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            cal.set(
                nowCal.get(Calendar.YEAR),
                nowCal.get(Calendar.MONTH),
                event.day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            )
            if (cal.timeInMillis <= nowMillis) {
                cal.add(Calendar.MONTH, 1)
                cal.set(
                    Calendar.DAY_OF_MONTH,
                    event.day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                )
            }
            return cal.timeInMillis
        }

        "YEARLY" -> {
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            cal.set(
                nowCal.get(Calendar.YEAR),
                (event.month - 1).coerceIn(0, 11),
                event.day.coerceIn(1, 31)
            )
            if (cal.timeInMillis <= nowMillis) {
                cal.add(Calendar.YEAR, 1)
            }
            return cal.timeInMillis
        }

        else -> {
            cal.set(event.year, (event.month - 1).coerceIn(0, 11), event.day.coerceIn(1, 31))
            return cal.timeInMillis
        }
    }
}

/**
 * Tự động tìm ngày Mùng 1 Tết Nguyên Đán tiếp theo (từ bảng Lịch Âm chuẩn 2026-2032).
 */
private fun nextLunarNewYearTriple(nowMillis: Long): Triple<Int, Int, Int> {
    val tetDates = listOf(
        Triple(2026, 2, 17), // Tết Bính Ngọ
        Triple(2027, 2, 6),  // Tết Đinh Mùi
        Triple(2028, 1, 26), // Tết Mậu Thân
        Triple(2029, 2, 13), // Tết Kỷ Dậu
        Triple(2030, 2, 3),  // Tết Canh Tuất
        Triple(2031, 1, 23), // Tết Tân Hợi
        Triple(2032, 2, 11)  // Tết Nhâm Tý
    )
    val cal = Calendar.getInstance()
    for ((y, m, d) in tetDates) {
        cal.set(y, m - 1, d, 0, 0, 0)
        if (cal.timeInMillis > nowMillis) {
            return Triple(y, m, d)
        }
    }
    return tetDates.last()
}

private const val COUNTDOWN_PREFS = "countdown_days_prefs"
private const val KEY_SAVED_EVENTS = "saved_day_events_v1"

private fun loadOrInitDayEvents(context: Context): List<DayEventItem> {
    val prefs = context.getSharedPreferences(COUNTDOWN_PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_SAVED_EVENTS, null)
    if (!raw.isNullOrBlank()) {
        val loaded = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        DayEventItem(
                            id = o.optLong("id", i.toLong()),
                            title = o.optString("title", "Sự kiện"),
                            emoji = o.optString("emoji", "📅"),
                            year = o.optInt("year", 2027),
                            month = o.optInt("month", 1),
                            day = o.optInt("day", 1),
                            isCountUp = o.optBoolean("isCountUp", false),
                            repeatMode = o.optString("repeatMode", "NONE"),
                            colorHex = o.optLong("colorHex", 0xFF06B6D4L)
                        )
                    )
                }
            }
        }.getOrNull()
        if (!loaded.isNullOrEmpty()) return loaded
    }

    val now = System.currentTimeMillis()
    val (tetY, tetM, tetD) = nextLunarNewYearTriple(now)
    val nextSolarYear = Calendar.getInstance().get(Calendar.YEAR) + 1

    val defaults = listOf(
        DayEventItem(
            id = 1L,
            title = "Tết Nguyên Đán $tetY",
            emoji = "🧧",
            year = tetY,
            month = tetM,
            day = tetD,
            isCountUp = false,
            repeatMode = "NONE",
            colorHex = 0xFFEF4444L
        ),
        DayEventItem(
            id = 2L,
            title = "Ngày Lương Về (Mùng 10)",
            emoji = "💰",
            year = 2026,
            month = 1,
            day = 10,
            isCountUp = false,
            repeatMode = "MONTHLY",
            colorHex = 0xFFF59E0BL
        ),
        DayEventItem(
            id = 3L,
            title = "Đếm Ngày Yêu Nhau ❤️",
            emoji = "❤️",
            year = 2025,
            month = 2,
            day = 14,
            isCountUp = true,
            repeatMode = "NONE",
            colorHex = 0xFFEC4899L
        ),
        DayEventItem(
            id = 4L,
            title = "Tết Dương Lịch $nextSolarYear",
            emoji = "🎆",
            year = nextSolarYear,
            month = 1,
            day = 1,
            isCountUp = false,
            repeatMode = "YEARLY",
            colorHex = 0xFF06B6D4L
        )
    )
    saveDayEvents(context, defaults)
    return defaults
}

private fun saveDayEvents(context: Context, list: List<DayEventItem>) {
    val arr = JSONArray()
    list.forEach { ev ->
        arr.put(
            JSONObject().apply {
                put("id", ev.id)
                put("title", ev.title)
                put("emoji", ev.emoji)
                put("year", ev.year)
                put("month", ev.month)
                put("day", ev.day)
                put("isCountUp", ev.isCountUp)
                put("repeatMode", ev.repeatMode)
                put("colorHex", ev.colorHex)
            }
        )
    }
    context.getSharedPreferences(COUNTDOWN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_SAVED_EVENTS, arr.toString())
        .apply()
}

