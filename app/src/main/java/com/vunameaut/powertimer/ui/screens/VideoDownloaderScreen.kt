package com.vunameaut.powertimer.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.downloader.DirectVideoExtractor
import com.vunameaut.powertimer.downloader.DownloadStatus
import com.vunameaut.powertimer.downloader.DownloadTaskState
import com.vunameaut.powertimer.downloader.VideoPlatform
import com.vunameaut.powertimer.service.VideoDownloadService
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import com.vunameaut.powertimer.ui.theme.DarkBackground
import com.vunameaut.powertimer.ui.theme.DarkSurface
import com.vunameaut.powertimer.ui.theme.DarkSurfaceVariant
import com.vunameaut.powertimer.ui.theme.OutlineSoft
import com.vunameaut.powertimer.ui.theme.TextPrimary
import com.vunameaut.powertimer.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoDownloaderTool(
    onExit: () -> Unit
) {
    BackHandler(onBack = onExit)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        VideoDownloadService.ensureHistoryLoaded(context)
    }

    val tasks by VideoDownloadService.tasks.collectAsState()
    var urlInput by rememberSaveable { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(DirectVideoExtractor.getServerUrl(context)) }
    var isUpdatingYtdlp by remember { mutableStateOf(false) }

    val detectedPlatform = remember(urlInput) {
        val extracted = VideoPlatform.extractFirstUrl(urlInput)
        if (extracted != null) VideoPlatform.detect(extracted) else null
    }

    fun triggerDownload(rawText: String) {
        val validUrl = VideoPlatform.extractFirstUrl(rawText)
        if (validUrl.isNullOrBlank()) {
            Toast.makeText(
                context,
                "Vui lòng dán đường link hợp lệ (TikTok, Facebook, Instagram, YouTube, X...)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        VideoDownloadService.startDownload(context, validUrl)
        urlInput = ""
        Toast.makeText(
            context,
            "Đang bắt đầu tải video ${VideoPlatform.detect(validUrl).displayName}...",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun pasteFromClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        return text?.takeIf { it.isNotBlank() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF101322),
                        DarkBackground,
                        DarkBackground
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(DarkSurface)
                                .border(1.dp, OutlineSoft, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Tải Video Đa Nền Tảng",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Tự động giải mã trực tiếp trên máy • Không logo",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            serverUrlInput = DirectVideoExtractor.getServerUrl(context)
                            showSettingsDialog = true
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .border(1.dp, OutlineSoft, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Cài đặt",
                            tint = AccentCyan
                        )
                    }
                }
            }

            // Engine Banner + Share Guide Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(AccentPink.copy(alpha = 0.5f), AccentCyan.copy(alpha = 0.5f))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AccentGreen.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Smartphone,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Chế độ Trích Xuất Nhanh + Cloud Server API",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = AccentGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Siêu nhẹ (~10MB) • Tải trực tiếp kết hợp Server API Online",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                VideoPlatform.TIKTOK,
                                VideoPlatform.FACEBOOK,
                                VideoPlatform.INSTAGRAM,
                                VideoPlatform.YOUTUBE,
                                VideoPlatform.TWITTER,
                                VideoPlatform.THREADS
                            ).forEach { pf ->
                                PlatformBadge(platform = pf)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Mẹo nhanh: Từ TikTok, FB, IG, YouTube bấm \"Chia sẻ\" → chọn \"Tải Video (Tools Hub)\" để tự động tải ngầm kèm thông báo!",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextPrimary.copy(alpha = 0.9f),
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // URL Input & Action Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OutlineSoft, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Đường dẫn Video (URL)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            if (detectedPlatform != null) {
                                PlatformBadge(platform = detectedPlatform)
                            }
                        }

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "Dán link TikTok, Facebook, Instagram, YouTube, X...",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 13.5.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = detectedPlatform?.badgeColor ?: AccentCyan
                                )
                            },
                            trailingIcon = {
                                if (urlInput.isNotBlank()) {
                                    IconButton(onClick = { urlInput = "" }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "Xoá",
                                            tint = TextSecondary
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = OutlineSoft,
                                focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.35f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clip = pasteFromClipboard()
                                    if (clip != null) {
                                        urlInput = clip
                                        val valid = VideoPlatform.extractFirstUrl(clip)
                                        if (valid != null) {
                                            triggerDownload(valid)
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Bộ nhớ tạm (Clipboard) đang trống",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Dán & Tải",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                            }

                            Button(
                                onClick = { triggerDownload(urlInput) },
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPink,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tải Video Ngay",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section Title for Queue & History
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tiến trình & Lịch sử tải (${tasks.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (tasks.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }) {
                        TextButton(
                            onClick = { VideoDownloadService.clearCompletedHistory(context) }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Dọn lịch sử",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurface.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OutlineSoft.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Chưa có video nào trong danh sách",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "Dán link vào ô phía trên hoặc chọn Chia sẻ → Tải Video (Tools Hub) từ ứng dụng bất kỳ.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(items = tasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onCancel = { VideoDownloadService.cancelDownload(context, task.id) },
                        onRetry = {
                            VideoDownloadService.removeTaskFromHistory(context, task.id)
                            VideoDownloadService.startDownload(context, task.sourceUrl)
                        },
                        onRemove = { VideoDownloadService.removeTaskFromHistory(context, task.id) },
                        onOpenVideo = { openSavedVideo(context, task) },
                        onShareVideo = { shareSavedVideo(context, task) }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Cấu hình Trình Tải Video",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Ứng dụng sử dụng bộ trích xuất siêu nhẹ kết hợp gọi trực tiếp tới Server API Online của bạn (không nhúng thư viện nặng vào máy):",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Địa chỉ Server API Online") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = OutlineSoft,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedButton(
                        onClick = {
                            if (!isUpdatingYtdlp) {
                                isUpdatingYtdlp = true
                                DirectVideoExtractor.setServerUrl(
                                    context,
                                    serverUrlInput.ifBlank { DirectVideoExtractor.DEFAULT_SERVER_URL }
                                )
                                scope.launch {
                                    val res = DirectVideoExtractor.checkServerHealth(context)
                                    isUpdatingYtdlp = false
                                    val msg = res.fold(
                                        onSuccess = { "Kết nối thành công: $it" },
                                        onFailure = { "Không kết nối được Server: ${it.message}" }
                                    )
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isUpdatingYtdlp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AccentCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang kiểm tra kết nối Server API...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kiểm tra kết nối Server API")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        DirectVideoExtractor.setServerUrl(
                            context,
                            serverUrlInput.ifBlank { DirectVideoExtractor.DEFAULT_SERVER_URL }
                        )
                        showSettingsDialog = false
                        Toast.makeText(context, "Đã lưu cấu hình!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                ) {
                    Text("Lưu", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        serverUrlInput = DirectVideoExtractor.DEFAULT_SERVER_URL
                    }
                ) {
                    Text("Mặc định", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun PlatformBadge(platform: VideoPlatform) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(platform.badgeColor.copy(alpha = 0.16f))
            .border(1.dp, platform.badgeColor.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = platform.displayName,
            style = MaterialTheme.typography.labelSmall.copy(
                color = platform.badgeColor,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenVideo: () -> Unit,
    onShareVideo: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = task.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "dl_progress"
    )

    val statusColor by animateColorAsState(
        targetValue = when (task.status) {
            DownloadStatus.EXTRACTING -> AccentAmber
            DownloadStatus.DOWNLOADING -> AccentCyan
            DownloadStatus.COMPLETED -> AccentGreen
            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> AccentRed
        },
        label = "status_color"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlatformBadge(platform = task.platform)
                    Text(
                        text = task.engineUsed,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when (task.status) {
                    DownloadStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = statusColor
                        )
                    }
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (task.status == DownloadStatus.EXTRACTING || task.status == DownloadStatus.DOWNLOADING) {
                if (task.status == DownloadStatus.EXTRACTING || task.totalBytes <= 0L) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50)),
                        color = statusColor,
                        trackColor = DarkSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50)),
                        color = statusColor,
                        trackColor = DarkSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val progressLabel = if (task.status == DownloadStatus.EXTRACTING) {
                        "Đang bóc tách luồng MP4 gốc..."
                    } else if (task.totalBytes > 0L) {
                        val pct = (task.progress * 100).toInt().coerceIn(0, 100)
                        "$pct% • ${VideoDownloadService.formatBytes(task.downloadedBytes)} / ${
                            VideoDownloadService.formatBytes(task.totalBytes)
                        }"
                    } else {
                        "Đã tải ${VideoDownloadService.formatBytes(task.downloadedBytes)}"
                    }

                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task.speedBytesPerSec > 0L) {
                            Text(
                                text = "${VideoDownloadService.formatBytes(task.speedBytesPerSec)}/s",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Huỷ", color = AccentRed, fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = task.status == DownloadStatus.COMPLETED,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Đã lưu vào Thư viện (Movies/ToolsHub) • ${
                            VideoDownloadService.formatBytes(task.downloadedBytes)
                        }",
                        style = MaterialTheme.typography.labelSmall.copy(color = AccentGreen)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenVideo,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen.copy(alpha = 0.2f),
                                contentColor = AccentGreen
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mở Video", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }

                        OutlinedButton(
                            onClick = onShareVideo,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chia sẻ", color = AccentCyan, fontSize = 12.5.sp)
                        }

                        IconButton(onClick = onRemove) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Xoá",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            if (task.status == DownloadStatus.FAILED || task.status == DownloadStatus.CANCELLED) {
                Text(
                    text = task.errorMessage ?: "Đã xảy ra lỗi khi tải video",
                    style = MaterialTheme.typography.bodySmall.copy(color = AccentRed)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRemove) {
                        Text("Xoá", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thử lại", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun openSavedVideo(context: Context, task: DownloadTaskState) {
    val uriStr = task.savedUri
    if (uriStr.isNullOrBlank()) {
        Toast.makeText(context, "Không tìm thấy đường dẫn file video", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = Uri.parse(uriStr)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Throwable) {
        Toast.makeText(context, "Không thể mở trình phát video: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareSavedVideo(context: Context, task: DownloadTaskState) {
    val uriStr = task.savedUri
    if (uriStr.isNullOrBlank()) {
        Toast.makeText(context, "Không tìm thấy file để chia sẻ", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = Uri.parse(uriStr)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ video"))
    } catch (e: Throwable) {
        Toast.makeText(context, "Không thể chia sẻ video: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

