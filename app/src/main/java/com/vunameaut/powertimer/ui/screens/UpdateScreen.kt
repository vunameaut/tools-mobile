package com.vunameaut.powertimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.BuildConfig
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentRed
import com.vunameaut.powertimer.update.UpdateManager
import com.vunameaut.powertimer.update.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    state: UpdateState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cập nhật & thông tin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Thẻ phiên bản hiện tại
            Card(
                title = "Phiên bản đang dùng",
                body = "v${BuildConfig.VERSION_NAME}  (build ${BuildConfig.VERSION_CODE})"
            )

            // Trạng thái cập nhật
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                when (state) {
                    UpdateState.Idle, UpdateState.UpToDate -> {
                        StatusRow(
                            icon = { Icon(Icons.Rounded.CheckCircle, null, tint = AccentGreen) },
                            text = if (state == UpdateState.UpToDate) "Bạn đang dùng bản mới nhất" else "Chưa kiểm tra bản cập nhật"
                        )
                        CheckButton(onCheck)
                    }

                    UpdateState.Checking -> StatusRow(
                        icon = { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp) },
                        text = "Đang kiểm tra GitHub…"
                    )

                    is UpdateState.Available -> {
                        StatusRow(
                            icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = AccentCyan) },
                            text = "Có bản mới v${state.info.versionName}" +
                                if (state.info.sizeBytes > 0) "  (${formatSize(state.info.sizeBytes)})" else ""
                        )
                        if (state.info.notes.isNotBlank()) {
                            Text(
                                text = state.info.notes,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PrimaryButton("Tải bản cập nhật", Icons.Rounded.Download, onDownload)
                    }

                    is UpdateState.Downloading -> {
                        StatusRow(
                            icon = { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp) },
                            text = "Đang tải v${state.info.versionName}… ${(state.progress * 100).toInt()}%"
                        )
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = AccentCyan,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        StatusRow(
                            icon = { Icon(Icons.Rounded.CheckCircle, null, tint = AccentGreen) },
                            text = "Đã tải xong v${state.info.versionName}"
                        )
                        PrimaryButton("Cài đặt ngay", Icons.Rounded.SystemUpdate, onInstall)
                        Text(
                            text = "Lần đầu Android sẽ yêu cầu cho phép “Cài ứng dụng không rõ nguồn gốc” cho Tools Hub.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateState.Error -> {
                        StatusRow(
                            icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = AccentRed) },
                            text = state.message
                        )
                        CheckButton(onCheck)
                    }
                }
            }

            Text(
                text = "Nguồn cập nhật: github.com/${UpdateManager.REPO}\nMỗi lần push code lên nhánh main, GitHub Actions sẽ tự build và phát hành bản mới.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Card(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun StatusRow(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon()
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun CheckButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Icon(Icons.Rounded.Refresh, null, tint = AccentCyan)
        Spacer(Modifier.size(8.dp))
        Text("Kiểm tra cập nhật", color = AccentCyan, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color(0xFF00363D)),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Icon(icon, null)
        Spacer(Modifier.size(8.dp))
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

private fun formatSize(bytes: Long): String = String.format("%.1f MB", bytes / 1024.0 / 1024.0)
