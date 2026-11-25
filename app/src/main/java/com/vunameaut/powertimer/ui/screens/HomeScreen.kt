package com.vunameaut.powertimer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vunameaut.powertimer.ui.components.DurationPickerSheet
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.service.IslandDisplayStyle
import com.vunameaut.powertimer.service.NativeMediaIslandBridge
import com.vunameaut.powertimer.service.SystemDynamicIslandManager
import com.vunameaut.powertimer.ui.components.CircularCountdownTimer
import com.vunameaut.powertimer.ui.components.ModeSelectorCard
import com.vunameaut.powertimer.ui.components.PermissionItem
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import com.vunameaut.powertimer.ui.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timerState by viewModel.timerState.collectAsState()
    val selectedSeconds by viewModel.selectedSeconds.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val shakeToExtend by viewModel.shakeToExtend.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    BackHandler(onBack = onBack)

    var showPicker by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    var showMissingWriteSettingsDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var alwaysShowWaterdrop by remember {
        mutableStateOf(SystemDynamicIslandManager.isEnabled(context))
    }
    var canDrawOverlays by remember {
        mutableStateOf(SystemDynamicIslandManager.canDrawOverlays(context))
    }
    var selectedIslandStyle by remember {
        mutableStateOf(SystemDynamicIslandManager.getStyle(context))
    }
    var islandOffsetY by remember {
        mutableStateOf(SystemDynamicIslandManager.getOffsetYDp(context))
    }
    var isIslandMinimized by remember {
        mutableStateOf(SystemDynamicIslandManager.isMinimized(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.refreshPermissions()
                    canDrawOverlays = SystemDynamicIslandManager.canDrawOverlays(context)
                    isIslandMinimized = SystemDynamicIslandManager.isMinimized(context)
                    SystemDynamicIslandManager.refreshOverlay(context)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (viewModel.timerState.value.isRunning) {
                        NativeMediaIslandBridge.nudgeCapsulePlayback(context)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPicker) {
        DurationPickerSheet(
            initialSeconds = selectedSeconds,
            onDismiss = { showPicker = false },
            onConfirm = { seconds ->
                viewModel.setCustomSeconds(seconds)
                showPicker = false
            }
        )
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(Icons.Default.Stop, contentDescription = null, tint = AccentRed) },
            title = { Text("Hủy hẹn giờ?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Màn hình sẽ không tự tắt nữa. Bạn có chắc muốn dừng bộ đếm đang chạy?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stopTimer()
                        showStopConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Hủy hẹn giờ", fontWeight = FontWeight.Bold, color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text("Giữ lại", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showMissingWriteSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showMissingWriteSettingsDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Chưa cấp quyền Hạ thời gian sáng", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Để chế độ Chuẩn Ngân Hàng tự thoát TikTok/YouTube về Màn hình chính và tắt khóa màn hình phần cứng (giữ Vân tay), bạn cần bật \"Cho phép sửa cài đặt hệ thống\".\n\nNếu chưa cấp ngay, app vẫn chạy với lớp Màn đen OLED 0% để bảo vệ pin không bị sáng cả đêm.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMissingWriteSettingsDialog = false
                        viewModel.openWriteSettingsPermission()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Cấp quyền ngay", fontWeight = FontWeight.Bold, color = Color.Black) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMissingWriteSettingsDialog = false
                        viewModel.startTimer()
                    }
                ) {
                    Text("Vẫn chạy (Màn đen 0%)", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val scrollState = rememberScrollState()
    val quickSecondPresets = remember {
        listOf(
            "10s" to 10L,
            "30s" to 30L,
            "1m" to 60L,
            "5m" to 300L,
            "15m" to 900L,
            "30m" to 1800L,
            "45m" to 2700L,
            "60m" to 3600L,
            "90m" to 5400L,
            "120m" to 7200L
        )
    }

    val currentDisplayTime = if (timerState.isRunning) {
        timerState.formattedRemaining
    } else {
        val hours = selectedSeconds / 3600
        val minutes = (selectedSeconds % 3600) / 60
        val seconds = selectedSeconds % 60
        if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PowerTimer",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentPurple.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Hẹn giờ tắt màn hình",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Về Tools Hub",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cài đặt & Quyền",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Nếu máy đang bật Trợ năng từ trước -> Hiển thị nút 1 chạm Tắt & Ẩn ngay lập tức để mở khóa App Ngân hàng!
            if (permissions.isAccessibilityEnabled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentRed.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🛡️ Đang bật Trợ năng (Cản trở Ngân hàng)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Bấm nút bên cạnh để tắt & ẩn hoàn toàn Trợ năng ngay lập tức, giúp dùng mọi App Ngân hàng & VNeID bình thường!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { viewModel.disableAccessibilityForBanking() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                        ) {
                            Text(
                                text = "Tắt Trợ năng",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Gợi ý cấp quyền tương ứng với chế độ đang chọn
            if (selectedMode.requiresWriteSettings && !permissions.canWriteSettings) {
                PermissionItem(
                    title = "Cấp quyền Hạ thời gian sáng (Chuẩn Ngân Hàng)",
                    description = "Cho phép tự hạ thời gian chờ màn hình xuống mức tối thiểu ngay ngoài Màn hình chính để máy tự tắt & khóa Lock Screen tự nhiên (100% không dùng Trợ năng).",
                    isGranted = false,
                    onGrantClick = { viewModel.openWriteSettingsPermission() },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            if ((selectedMode == ShutdownMode.BANK_SAFE_SLEEP || selectedMode == ShutdownMode.MEDIA_ONLY) && !canDrawOverlays) {
                PermissionItem(
                    title = "Bật Hiển thị trên ứng dụng khác (Để dừng hẳn TikTok / Facebook)",
                    description = "Cho phép thoát ứng dụng video (TikTok, Douyin, Facebook) về Màn hình chính khi hết giờ giúp video dừng phát 100% và nhả sáng màn hình.",
                    isGranted = false,
                    onGrantClick = { SystemDynamicIslandManager.openOverlayPermissionSettings(context) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (selectedMode.requiresDeviceAdmin && !permissions.isDeviceAdminActive) {
                PermissionItem(
                    title = "Cần bật Quản trị viên thiết bị",
                    description = "Khóa màn hình tức thì mà KHÔNG dùng Trợ năng (hoàn toàn không bị App Ngân hàng chặn).",
                    isGranted = false,
                    onGrantClick = { viewModel.requestDeviceAdmin() },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else if (selectedMode.requiresAccessibility && !permissions.isAccessibilityEnabled) {
                PermissionItem(
                    title = "Cần bật Dịch vụ Trợ năng",
                    description = "Lưu ý: Dịch vụ sẽ TỰ ĐỘNG TẮT ngay sau khi khóa màn hình 1 giây để không làm gián đoạn App Ngân hàng của bạn.",
                    isGranted = false,
                    onGrantClick = { viewModel.openAccessibilitySettings() },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Đồng hồ đếm ngược tròn Neon
            CircularCountdownTimer(
                progress = timerState.progress,
                timeText = currentDisplayTime,
                isRunning = timerState.isRunning,
                onClick = {
                    if (!timerState.isRunning) showPicker = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Thẻ bật/tắt & tương tác Màn hình Giọt nước (Dynamic Island Nổi Ngoài Màn Hình Chính kiểu YouTube / Spotify)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AccentPurple.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "💧 Dynamic Island Ngoài Màn Hình Chính",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Hiển thị viên thuốc sóng nhạc trên thanh trạng thái ngay cả khi thoát ra ngoài màn hình chính hoặc lướt TikTok/Zalo (giống YouTube/Spotify).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = alwaysShowWaterdrop,
                            onCheckedChange = { enabled ->
                                alwaysShowWaterdrop = enabled
                                SystemDynamicIslandManager.setEnabled(context, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentGreen
                            )
                        )
                    }

                    if (alwaysShowWaterdrop) {
                        Text(
                            text = "🎨 Chọn màu đồng hồ trong Dynamic Island gốc:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(IslandDisplayStyle.entries.filter { it != IslandDisplayStyle.NATIVE_SYSTEM }) { styleItem ->
                                val selected = styleItem == selectedIslandStyle ||
                                    (selectedIslandStyle == IslandDisplayStyle.NATIVE_SYSTEM && styleItem == IslandDisplayStyle.SPOTIFY_YOUTUBE)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedIslandStyle = styleItem
                                        SystemDynamicIslandManager.setStyle(context, styleItem)
                                    },
                                    label = {
                                        Text(
                                            text = styleItem.title,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentCyan,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        Text(
                            text = "💡 Tích hợp 100% với Dynamic Island gốc của máy (giống hệt YouTube/Spotify, không vẽ đè lớp phủ giả): Chạm trực tiếp vào viên thuốc quanh camera để bung bảng điều khiển, nhấn vào tên/đồng hồ để mở lại App, nút ⏭ (+10 phút) hoặc ⏮ (Khóa ngay)!",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Danh sách preset thời gian nhanh & Tự nhập (khi chưa chạy)
            if (!timerState.isRunning) {
                Text(
                    text = "Mốc thời gian nhanh (Chạm đồng hồ hoặc 'Tự nhập' để chỉnh từng giây)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = { showPicker = true },
                            label = { Text("⚙️ Tự nhập", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = AccentPurple.copy(alpha = 0.2f),
                                labelColor = AccentCyan
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                    items(quickSecondPresets) { (label, secs) ->
                        val isSelected = selectedSeconds == secs
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCustomSeconds(secs) },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCyan,
                                selectedLabelColor = Color.Black
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Lựa chọn cách tắt màn hình
                Text(
                    text = "Tùy chọn khi hết giờ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(10.dp))

                ShutdownMode.entries.forEach { mode ->
                    val isAvailable = when (mode) {
                        ShutdownMode.BANK_SAFE_SLEEP -> permissions.canWriteSettings
                        ShutdownMode.DEVICE_ADMIN -> permissions.isDeviceAdminActive
                        ShutdownMode.MEDIA_ONLY -> true
                        ShutdownMode.ACCESSIBILITY, ShutdownMode.HOME_AND_LOCK -> permissions.isAccessibilityEnabled
                    }
                    ModeSelectorCard(
                        mode = mode,
                        isSelected = selectedMode == mode,
                        isAvailable = isAvailable,
                        onSelect = { viewModel.setMode(mode) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📳 Lắc mạnh để +10 phút",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mặc định tắt để tránh cựa mình khi ngủ làm cộng thêm giờ. Bật nếu muốn lắc mạnh 2 lần để +10 phút.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = shakeToExtend,
                            onCheckedChange = { viewModel.toggleShakeToExtend(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentCyan
                            )
                        )
                    }
                }
            } else {
                // Đang đếm ngược
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Đang kích hoạt: ${timerState.selectedMode.title}",
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (timerState.shakeToExtend) {
                                "✨ Mẹo: Bạn có thể lắc mạnh điện thoại 2 lần bất kỳ lúc nào để cộng thêm 10 phút!"
                            } else {
                                "🌙 Đã khóa tính năng lắc +10 phút để tránh chạm/cựa mình khi ngủ làm kéo dài thời gian tắt màn hình."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Nút bấm chính (chuyển đổi mượt giữa Bắt đầu ⇄ Đang chạy)
            AnimatedContent(
                targetState = timerState.isRunning,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300))) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)))
                },
                label = "main_button"
            ) { running ->
            if (!running) {
                Button(
                    onClick = {
                        if (selectedMode == ShutdownMode.BANK_SAFE_SLEEP && !permissions.canWriteSettings) {
                            showMissingWriteSettingsDialog = true
                        } else {
                            viewModel.startTimer()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BẮT ĐẦU HẸN GIỜ",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { viewModel.addTenMinutes() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = AccentCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "+10 Phút", color = AccentCyan, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showStopConfirm = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "HỦY HẸN GIỜ", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            }
        }
    }
}
