package com.vunameaut.powertimer.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.ui.components.CircularCountdownTimer
import com.vunameaut.powertimer.ui.components.ModeSelectorCard
import com.vunameaut.powertimer.ui.components.PermissionItem
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import com.vunameaut.powertimer.ui.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TimerViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timerState by viewModel.timerState.collectAsState()
    val selectedSeconds by viewModel.selectedSeconds.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val context = LocalContext.current

    val scrollState = rememberScrollState()

    val presets = listOf(15L, 30L, 45L, 60L, 90L, 120L)

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
                                text = "v1.0 • Android 15 ready",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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
            // Permission Alert Banner if Accessibility mode is selected but not enabled
            if (selectedMode == ShutdownMode.ACCESSIBILITY && !permissions.isAccessibilityEnabled) {
                PermissionItem(
                    title = "Cần bật Dịch vụ Trợ năng",
                    description = "Để tự động tắt máy trên Android đời cao, vui lòng bật dịch vụ PowerTimer trong cài đặt Trợ năng.",
                    isGranted = false,
                    onGrantClick = { viewModel.openAccessibilitySettings() },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Circular Countdown Display
            CircularCountdownTimer(
                progress = timerState.progress,
                timeText = currentDisplayTime,
                isRunning = timerState.isRunning,
                onClick = {
                    if (!timerState.isRunning) {
                        val hours = (selectedSeconds / 3600).toInt()
                        val minutes = ((selectedSeconds % 3600) / 60).toInt()
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                viewModel.setCustomSeconds((h * 3600L) + (m * 60L))
                            },
                            hours,
                            minutes,
                            true
                        ).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Presets row (only active when timer is not running)
            if (!timerState.isRunning) {
                Text(
                    text = "Mốc thời gian nhanh",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { minutes ->
                        val isSelected = selectedSeconds == minutes * 60
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setTimeInMinutes(minutes) },
                            label = {
                                Text(
                                    text = "${minutes}m",
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

                // Mode Selection Section
                Text(
                    text = "Phương thức hẹn giờ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(10.dp))

                ShutdownMode.values().forEach { mode ->
                    val isAvailable = when (mode) {
                        ShutdownMode.ACCESSIBILITY -> true
                        ShutdownMode.SHIZUKU -> permissions.isShizukuAvailable
                        ShutdownMode.ROOT -> permissions.isRootAvailable
                        ShutdownMode.SLEEP_LOCK -> true
                    }
                    ModeSelectorCard(
                        mode = mode,
                        isSelected = selectedMode == mode,
                        isAvailable = isAvailable,
                        onSelect = { viewModel.setMode(mode) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                // If timer is running, show active info
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Đang áp dụng: ${timerState.selectedMode.title}",
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hệ thống sẽ tự động thực thi khi đồng hồ về 0. Thông báo ghim đang hoạt động ở thanh trạng thái.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Action Buttons
            if (!timerState.isRunning) {
                Button(
                    onClick = { viewModel.startTimer() },
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
                        onClick = { viewModel.stopTimer() },
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
