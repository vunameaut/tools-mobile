package com.vunameaut.powertimer.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.weather.WeatherAtmosphere
import com.vunameaut.powertimer.weather.WeatherCityPreset
import com.vunameaut.powertimer.weather.WeatherManager
import com.vunameaut.powertimer.widget.LunarCalendarAppWidgetProvider
import com.vunameaut.powertimer.widget.LunarWeatherAppWidgetProvider
import com.vunameaut.powertimer.widget.ToolsHubAppWidgetProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeatherForecastTool(
    onExit: () -> Unit,
    onOpenLunarCalendar: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    var snapshot by remember { mutableStateOf(WeatherManager.getCachedOrDefaultSnapshot(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showCityPicker by remember { mutableStateOf(false) }

    fun triggerRefresh(force: Boolean = true) {
        scope.launch {
            isLoading = true
            errorMsg = null
            val res = WeatherManager.refreshWeather(context, forceNetwork = force)
            res.onSuccess {
                snapshot = it
                LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
            }.onFailure {
                errorMsg = "Không lấy được dữ liệu mạng. Đang hiển thị bản lưu gần nhất."
            }
            isLoading = false
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        WeatherManager.enableAutoLocation(context)
        triggerRefresh(force = true)
    }

    LaunchedEffect(Unit) {
        triggerRefresh(force = true)
    }

    val bgGradient = when (snapshot.atmosphere) {
        WeatherAtmosphere.CLEAR_DAY -> listOf(Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF0F172A))
        WeatherAtmosphere.HOT_SUNNY -> listOf(Color(0xFFB45309), Color(0xFFC2410C), Color(0xFF271104))
        WeatherAtmosphere.CLEAR_NIGHT -> listOf(Color(0xFF0B1026), Color(0xFF1E1B4B), Color(0xFF090D16))
        WeatherAtmosphere.PARTLY_CLOUDY -> listOf(Color(0xFF1D4ED8), Color(0xFF0F3F6B), Color(0xFF0F172A))
        WeatherAtmosphere.CLOUDY -> listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF0F172A))
        WeatherAtmosphere.FOG -> listOf(Color(0xFF475569), Color(0xFF334155), Color(0xFF1E293B))
        WeatherAtmosphere.RAIN -> listOf(Color(0xFF0F2942), Color(0xFF1E3A5F), Color(0xFF091526))
        WeatherAtmosphere.STORM -> listOf(Color(0xFF1E1B4B), Color(0xFF3B0764), Color(0xFF090D16))
    }

    val todayCal = remember { Calendar.getInstance() }
    val todayLunarInfo = remember {
        VietnameseLunarCalendar.getFullDayInfo(
            todayCal.get(Calendar.DAY_OF_MONTH),
            todayCal.get(Calendar.MONTH) + 1,
            todayCal.get(Calendar.YEAR)
        )
    }

    if (showCityPicker) {
        CityPickerDialog(
            onDismiss = { showCityPicker = false },
            onSelectCity = { city ->
                showCityPicker = false
                WeatherManager.setManualCity(context, city.name, city.lat, city.lon)
                triggerRefresh(force = true)
            },
            onUseAutoLocation = {
                showCityPicker = false
                if (WeatherManager.hasLocationPermission(context)) {
                    WeatherManager.enableAutoLocation(context)
                    triggerRefresh(force = true)
                } else {
                    locationPermLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgGradient))
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Thời Tiết & Lịch Widget",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Nền thay đổi tự động theo thời tiết thực tế",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showCityPicker = true }) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = "Đổi thành phố",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { triggerRefresh(force = true) }) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Làm mới",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                errorMsg?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            fontSize = 12.sp,
                            color = AccentAmber,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Khối Thời Tiết Chính Hiện Tại
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { showCityPicker = true }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = snapshot.cityName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "• Đổi",
                                color = Color(0xFFBAE6FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = snapshot.emoji, fontSize = 56.sp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "${snapshot.tempC}°",
                                fontSize = 64.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = snapshot.conditionText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Thấp nhất ${snapshot.minTodayC}° • Cao nhất ${snapshot.maxTodayC}° • Cảm giác ${snapshot.apparentTempC}°",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WeatherStatPill(
                                icon = Icons.Rounded.WaterDrop,
                                label = "Độ ẩm",
                                value = "${snapshot.humidityPct}%",
                                modifier = Modifier.weight(1f)
                            )
                            WeatherStatPill(
                                icon = Icons.Rounded.Air,
                                label = "Tốc độ gió",
                                value = "${snapshot.windKmh} km/h",
                                modifier = Modifier.weight(1f)
                            )
                            WeatherStatPill(
                                icon = Icons.Rounded.Thermostat,
                                label = "Cập nhật",
                                value = if (snapshot.updatedAtMillis > 0L) {
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snapshot.updatedAtMillis))
                                } else "--:--",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Nút Ghim Widget Tích Hợp Thời Tiết + Lịch Ra Màn Hình Chính
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.28f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🌤️ Widget Tích Hợp Thời Tiết + Lịch Âm Dương (4x3)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Phía trên hiện Thời tiết, phía dưới là bảng Lịch Âm - Dương đầy đủ. Background Widget tự động đổi màu theo nắng, mưa, đêm hay dông bão!",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 17.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
                                    val requested = ToolsHubAppWidgetProvider.requestPinWidgetToHomeScreen(
                                        context = context,
                                        providerClass = LunarWeatherAppWidgetProvider::class.java,
                                        label = "Lịch Âm & Thời Tiết Động (4x3)",
                                        showToastOnRequest = false
                                    )
                                    Toast.makeText(
                                        context,
                                        if (requested) {
                                            "Đã gửi yêu cầu ghim Widget Lịch & Thời Tiết (hoặc nhấn giữ màn hình chính → Tiện ích → Tools Hub để kéo ra)!"
                                        } else {
                                            "Nhấn giữ màn hình chính → Chọn Tiện ích → Tools Hub → Kéo \"Lịch Âm & Thời Tiết Động\" ra ngoài!"
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Ghim Widget", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                            }

                            if (onOpenLunarCalendar != null) {
                                OutlinedButton(
                                    onClick = onOpenLunarCalendar,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Mở Lịch Âm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }

                // 3. Thông tin Lịch Âm hôm nay nhanh
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = String.format(
                                    "📅 Hôm nay: %02d/%02d/%04d DL • %02d/%02d ÂL",
                                    todayLunarInfo.solarDay,
                                    todayLunarInfo.solarMonth,
                                    todayLunarInfo.solarYear,
                                    todayLunarInfo.lunarDay,
                                    todayLunarInfo.lunarMonth
                                ),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${todayLunarInfo.zodiacAnimalEmoji} Ngày ${todayLunarInfo.dayCanChi} • ${if (todayLunarInfo.isHoangDaoDay) "Ngày Hoàng Đạo 🟢" else todayLunarInfo.starName}",
                                fontSize = 12.sp,
                                color = Color(0xFF86EFAC)
                            )
                        }
                    }
                }

                // 4. Dự báo theo giờ
                if (snapshot.hourly.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🕒 Dự báo theo giờ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(snapshot.hourly) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.10f)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Text(item.timeLabel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                            Spacer(Modifier.height(4.dp))
                                            Text(item.emoji, fontSize = 22.sp)
                                            Spacer(Modifier.height(4.dp))
                                            Text("${item.tempC}°", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Dự báo 7 ngày tới
                if (snapshot.daily.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📆 Dự báo 7 ngày tới",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            snapshot.daily.forEach { d ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = d.dayLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        modifier = Modifier.width(74.dp)
                                    )
                                    Text(d.emoji, fontSize = 20.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = d.conditionText,
                                            fontSize = 12.5.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (d.rainChancePct > 0) {
                                            Text(
                                                text = "💧 Khả năng mưa: ${d.rainChancePct}%",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF7DD3FC)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${d.minTempC}° / ${d.maxTempC}°",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFFFDE68A)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WeatherStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.22f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFBAE6FD), modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
            Text(label, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CityPickerDialog(
    onDismiss: () -> Unit,
    onSelectCity: (WeatherCityPreset) -> Unit,
    onUseAutoLocation: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<WeatherCityPreset>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Chọn Khu Vực Thời Tiết", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onUseAutoLocation,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tự động lấy vị trí hiện tại (GPS)", fontWeight = FontWeight.Bold, color = AccentCyan, fontSize = 12.5.sp)
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.trim().length >= 2) {
                            scope.launch {
                                isSearching = true
                                searchResults = WeatherManager.searchCities(it)
                                isSearching = false
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    placeholder = { Text("Tìm tỉnh / thành phố bất kỳ...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSearching) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Đang tìm thành phố...", fontSize = 12.sp)
                    }
                }

                if (searchResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        searchResults.take(5).forEach { city ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectCity(city) }
                            ) {
                                Text(
                                    text = "📍 ${city.name}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text("Thành phố phổ biến tại Việt Nam:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WeatherManager.VIETNAM_CITY_PRESETS.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { onSelectCity(preset) }
                            ) {
                                Text(
                                    text = preset.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) }
        }
    )
}

