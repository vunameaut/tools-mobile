package com.vunameaut.powertimer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.ui.components.PermissionItem
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissions by viewModel.permissions.collectAsState()
    val fadeAudio by viewModel.fadeAudio.collectAsState()
    val enableDnd by viewModel.enableDnd.collectAsState()
    val playWarning by viewModel.playWarningSound.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cài đặt & Quyền hạn",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Tùy chọn hoạt động
            Text(
                text = "Tùy chọn hẹn giờ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingToggleRow(
                        title = "Giảm dần âm lượng",
                        description = "Giảm âm lượng dần trong 10 giây trước khi tắt",
                        checked = fadeAudio,
                        onCheckedChange = { viewModel.toggleFadeAudio(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Bật Không làm phiền (DND)",
                        description = "Tự động kích hoạt chế độ Không làm phiền khi hết giờ",
                        checked = enableDnd,
                        onCheckedChange = { viewModel.toggleDnd(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Rung báo trước 30 giây",
                        description = "Rung nhắc nhở để người dùng có thể gia hạn thêm giờ nếu cần",
                        checked = playWarning,
                        onCheckedChange = { viewModel.toggleWarningSound(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Danh sách quyền cho Android đời cao
            Text(
                text = "Kiểm tra quyền hệ thống (Android 11 - 15+)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Các bản Android mới yêu cầu cấp quyền để dịch vụ chạy ngầm không bị hệ điều hành tắt:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionItem(
                title = "Dịch vụ Trợ năng (Accessibility)",
                description = "Bắt buộc cho chế độ tự động mở menu nguồn và bấm Tắt máy không cần Root.",
                isGranted = permissions.isAccessibilityEnabled,
                onGrantClick = { viewModel.openAccessibilitySettings() },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            PermissionItem(
                title = "Tắt tối ưu hóa pin",
                description = "Ngăn chặn các hãng điện thoại (Samsung, Xiaomi, Pixel) ngắt đồng hồ đếm ngược khi khóa màn hình.",
                isGranted = permissions.isBatteryOptimizationIgnored,
                onGrantClick = { viewModel.requestBatteryOptimization() },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            PermissionItem(
                title = "Báo thức chính xác (Exact Alarm)",
                description = "Đảm bảo hẹn giờ chính xác đến từng giây kể cả khi thiết bị vào chế độ ngủ sâu (Doze).",
                isGranted = permissions.canScheduleExactAlarm,
                onGrantClick = { viewModel.openExactAlarmSettings() },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            PermissionItem(
                title = "Quản trị viên thiết bị",
                description = "Dùng để khóa màn hình ngay lập tức trong Chế độ Ngủ.",
                isGranted = permissions.isDeviceAdminActive,
                onGrantClick = { viewModel.requestDeviceAdmin() },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (permissions.isShizukuAvailable) {
                PermissionItem(
                    title = "Quyền Shizuku (Wireless ADB)",
                    description = "Cho phép chạy lệnh tắt máy trực tiếp qua ADB không cần Root.",
                    isGranted = permissions.hasShizukuPermission,
                    onGrantClick = { viewModel.requestShizukuPermission() },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AccentCyan
            )
        )
    }
}
