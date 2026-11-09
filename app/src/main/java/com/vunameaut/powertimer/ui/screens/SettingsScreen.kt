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
    val shakeToExtend by viewModel.shakeToExtend.collectAsState()

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
            // Tùy chọn hoạt động thông minh
            Text(
                text = "Tính năng thông minh",
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
                        title = "Lắc máy để gia hạn (+10 phút)",
                        description = "Lắc nhẹ điện thoại khi còn thức để tự động cộng thêm thời gian mà không cần bật sáng màn hình",
                        checked = shakeToExtend,
                        onCheckedChange = { viewModel.toggleShakeToExtend(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Giảm dần âm lượng (Fade-out)",
                        description = "Hạ âm lượng nhạc từ từ trong 10 giây trước khi tắt màn hình giúp dễ ngủ",
                        checked = fadeAudio,
                        onCheckedChange = { viewModel.toggleFadeAudio(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Bật Không làm phiền (DND)",
                        description = "Tự động kích hoạt Không làm phiền khi tắt màn hình để giấc ngủ không bị gián đoạn",
                        checked = enableDnd,
                        onCheckedChange = { viewModel.toggleDnd(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Rung báo trước 30 giây",
                        description = "Rung nhắc nhở nhẹ trước 30 giây để bạn biết nếu muốn gia hạn",
                        checked = playWarning,
                        onCheckedChange = { viewModel.toggleWarningSound(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Danh sách quyền cho Android đời cao
            Text(
                text = "Quyền hạn hệ thống (Android 11 - 15+)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Để tắt màn hình êm ái mà vẫn giữ nguyên mở khóa Vân tay / Khuôn mặt:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionItem(
                title = "Dịch vụ Trợ năng (Accessibility)",
                description = "Bắt buộc để tắt màn hình tự nhiên mà không làm mất tính năng mở khóa bằng Vân tay / Khuôn mặt.",
                isGranted = permissions.isAccessibilityEnabled,
                onGrantClick = { viewModel.openAccessibilitySettings() },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            PermissionItem(
                title = "Tắt tối ưu hóa pin",
                description = "Ngăn chặn hệ điều hành (Samsung, Xiaomi, Pixel...) ngắt đồng hồ đếm ngược khi máy ngủ sâu.",
                isGranted = permissions.isBatteryOptimizationIgnored,
                onGrantClick = { viewModel.requestBatteryOptimization() },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            PermissionItem(
                title = "Báo thức chính xác (Exact Alarm)",
                description = "Đảm bảo hẹn giờ chính xác đến từng giây trên Android 12, 13, 14, 15+.",
                isGranted = permissions.canScheduleExactAlarm,
                onGrantClick = { viewModel.openExactAlarmSettings() },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            PermissionItem(
                title = "Quản trị viên thiết bị",
                description = "Phương thức thay thế nếu bạn không muốn bật Trợ năng (Lưu ý: sẽ yêu cầu nhập mã PIN khi mở lại).",
                isGranted = permissions.isDeviceAdminActive,
                onGrantClick = { viewModel.requestDeviceAdmin() },
                modifier = Modifier.padding(vertical = 4.dp)
            )
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
