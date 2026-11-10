package com.vunameaut.powertimer.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoFile
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vunameaut.powertimer.downloader.DirectVideoExtractor
import com.vunameaut.powertimer.downloader.DownloadPreference
import com.vunameaut.powertimer.downloader.DownloadStatus
import com.vunameaut.powertimer.downloader.DownloadTaskState
import com.vunameaut.powertimer.downloader.ExtractedMediaItem
import com.vunameaut.powertimer.downloader.ExtractedVideo
import com.vunameaut.powertimer.downloader.MediaType
import com.vunameaut.powertimer.downloader.StoryLoginRequiredException
import com.vunameaut.powertimer.downloader.VideoFormatOption
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
    var selectedPreference by remember {
        mutableStateOf(DirectVideoExtractor.getDownloadPreference(context))
    }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(DirectVideoExtractor.getServerUrl(context)) }
    var isUpdatingYtdlp by remember { mutableStateOf(false) }

    // Format / Quality Picker state
    var isInspectingFormats by remember { mutableStateOf(false) }
    var extractedForPicker by remember { mutableStateOf<ExtractedVideo?>(null) }

    // Story Login WebView dialog state
    var loginDialogPlatform by remember { mutableStateOf<VideoPlatform?>(null) }
    var pendingRetryUrlAfterLogin by remember { mutableStateOf<String?>(null) }
    var loginStatusTick by remember { mutableIntStateOf(0) }

    val fbLoggedIn = remember(loginStatusTick) {
        DirectVideoExtractor.hasPlatformLogin(context, VideoPlatform.FACEBOOK)
    }
    val igLoggedIn = remember(loginStatusTick) {
        DirectVideoExtractor.hasPlatformLogin(context, VideoPlatform.INSTAGRAM)
    }

    val detectedPlatform = remember(urlInput) {
        val extracted = VideoPlatform.extractFirstUrl(urlInput)
        if (extracted != null) VideoPlatform.detect(extracted) else null
    }

    fun inspectAndPickFormat(rawText: String) {
        val validUrl = VideoPlatform.extractFirstUrl(rawText)
        if (validUrl.isNullOrBlank()) {
            Toast.makeText(
                context,
                "Vui lòng dán đường link hợp lệ (TikTok, Facebook, Instagram, YouTube, X...)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (isInspectingFormats) return
        isInspectingFormats = true
        scope.launch {
            val res = DirectVideoExtractor.extract(context, validUrl)
            isInspectingFormats = false
            res.fold(
                onSuccess = { extracted ->
                    extractedForPicker = extracted
                },
                onFailure = { err ->
                    if (err is StoryLoginRequiredException) {
                        pendingRetryUrlAfterLogin = validUrl
                        loginDialogPlatform = err.platform
                        Toast.makeText(
                            context,
                            "Story yêu cầu đăng nhập ${err.platform.displayName} 1 lần để tải!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            err.message ?: "Không thể phân tích link này",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }

    fun triggerDownload(rawText: String, forceAskFormat: Boolean = false) {
        val validUrl = VideoPlatform.extractFirstUrl(rawText)
        if (validUrl.isNullOrBlank()) {
            Toast.makeText(
                context,
                "Vui lòng dán đường link hợp lệ (TikTok, Facebook, Instagram, YouTube, X...)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (forceAskFormat || selectedPreference == DownloadPreference.ASK_FORMAT) {
            inspectAndPickFormat(validUrl)
            return
        }
        VideoDownloadService.startDownload(context, validUrl, selectedPreference)
        urlInput = ""
        val modeLabel = when (selectedPreference) {
            DownloadPreference.AUDIO_MP3 -> "nhạc MP3"
            DownloadPreference.VIDEO_SD -> "video SD"
            else -> "video/ảnh HD"
        }
        Toast.makeText(
            context,
            "Đang tải $modeLabel từ ${VideoPlatform.detect(validUrl).displayName}...",
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
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
                                text = "Tải Video & Ảnh Đa Năng",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Video HD/SD • Nhạc MP3 • Full Album Ảnh • Story",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary
                                )
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

            // Engine Banner + Story Account Status
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
                                    text = "Hỗ trợ Video nhiều chất lượng, MP3 & Full Album Ảnh",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = AccentGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Tự động tải trọn bộ ảnh Carousel (IG/TikTok/FB/X) • Tách nhạc MP3 siêu tốc",
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

                        // Story Account Quick Bar (FB & IG Stories)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Tải Story:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                StoryLoginChip(
                                    label = if (fbLoggedIn) "FB Story ✓" else "Đăng nhập FB",
                                    isLoggedIn = fbLoggedIn,
                                    accentColor = VideoPlatform.FACEBOOK.badgeColor,
                                    onClick = { loginDialogPlatform = VideoPlatform.FACEBOOK }
                                )
                                StoryLoginChip(
                                    label = if (igLoggedIn) "IG Story ✓" else "Đăng nhập IG",
                                    isLoggedIn = igLoggedIn,
                                    accentColor = VideoPlatform.INSTAGRAM.badgeColor,
                                    onClick = { loginDialogPlatform = VideoPlatform.INSTAGRAM }
                                )
                            }
                        }
                    }
                }
            }

            // URL Input, Mode Selector & Action Card
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
                                text = "Đường dẫn Video / Story / Bài Ảnh",
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
                                    text = "Dán link Video, Story, Reels hoặc bài đăng nhiều ảnh...",
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

                        // Format / Quality Preference Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Chế độ tải mặc định (áp dụng cả khi bấm Chia sẻ từ app khác):",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DownloadPreference.entries.forEach { pref ->
                                    val isSelected = selectedPreference == pref
                                    val chipColor = when (pref) {
                                        DownloadPreference.AUTO_HD -> AccentGreen
                                        DownloadPreference.VIDEO_SD -> AccentCyan
                                        DownloadPreference.AUDIO_MP3 -> AccentAmber
                                        DownloadPreference.ASK_FORMAT -> AccentPurple
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) chipColor.copy(alpha = 0.22f)
                                                else DarkSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) chipColor else OutlineSoft,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                selectedPreference = pref
                                                DirectVideoExtractor.setDownloadPreference(context, pref)
                                            }
                                            .padding(horizontal = 11.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = pref.shortLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) chipColor else TextSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

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
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
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
                                enabled = !isInspectingFormats,
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPink,
                                    contentColor = Color.White
                                )
                            ) {
                                if (isInspectingFormats) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Đang phân tích...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedPreference == DownloadPreference.ASK_FORMAT) {
                                            "Chọn Chất Lượng"
                                        } else {
                                            "Tải Ngay"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // Secondary Button: Always allow inspecting formats/qualities/photos on demand
                        OutlinedButton(
                            onClick = {
                                val target = urlInput.ifBlank { pasteFromClipboard().orEmpty() }
                                if (target.isNotBlank() && urlInput.isBlank()) {
                                    urlInput = target
                                }
                                inspectAndPickFormat(target)
                            },
                            enabled = !isInspectingFormats,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Phân tích Link & Chọn Chất lượng (1080p / 720p / SD / MP3 / Ảnh)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                                text = "Chưa có mục tải nào trong danh sách",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "Dán link Video, Story hoặc bài đăng nhiều ảnh vào ô phía trên — hoặc chọn Chia sẻ → Tải Video (Tools Hub).",
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
                            VideoDownloadService.startDownload(context, task.sourceUrl, selectedPreference)
                        },
                        onLoginAndRetry = {
                            pendingRetryUrlAfterLogin = task.sourceUrl
                            VideoDownloadService.removeTaskFromHistory(context, task.id)
                            loginDialogPlatform = if (task.platform == VideoPlatform.INSTAGRAM) {
                                VideoPlatform.INSTAGRAM
                            } else {
                                VideoPlatform.FACEBOOK
                            }
                        },
                        onPickFormat = {
                            urlInput = task.sourceUrl
                            inspectAndPickFormat(task.sourceUrl)
                        },
                        onRemove = { VideoDownloadService.removeTaskFromHistory(context, task.id) },
                        onOpenMedia = { openSavedMedia(context, task) },
                        onShareMedia = { shareSavedMedia(context, task) }
                    )
                }
            }
        }
    }

    // Format / Quality & Multi-Photo Picker Dialog
    val currentExtracted = extractedForPicker
    if (currentExtracted != null) {
        FormatSelectionDialog(
            extracted = currentExtracted,
            onDismiss = { extractedForPicker = null },
            onSelectFormat = { fmt ->
                extractedForPicker = null
                urlInput = ""
                VideoDownloadService.startDownloadWithFormat(
                    context = context,
                    extracted = currentExtracted,
                    selectedFormat = fmt,
                    downloadAllPhotos = false
                )
                Toast.makeText(
                    context,
                    "Đang tải: ${fmt.label}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDownloadAllPhotos = {
                extractedForPicker = null
                urlInput = ""
                VideoDownloadService.startDownloadWithFormat(
                    context = context,
                    extracted = currentExtracted,
                    selectedFormat = null,
                    downloadAllPhotos = true
                )
                Toast.makeText(
                    context,
                    "Đang tải trọn bộ ${currentExtracted.mediaItems.size} ảnh...",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDownloadSingleMediaItem = { idx, item ->
                extractedForPicker = null
                urlInput = ""
                val itemLabel = "Ảnh ${idx + 1}/${currentExtracted.mediaItems.size}"
                val singleFormat = VideoFormatOption(
                    id = "item_${idx + 1}",
                    label = itemLabel,
                    qualityBadge = "Ảnh #${idx + 1}",
                    downloadUrl = item.url,
                    mediaType = item.mediaType,
                    ext = item.ext,
                    headers = item.headers.ifEmpty { currentExtracted.headers }
                )
                VideoDownloadService.startDownloadWithFormat(
                    context = context,
                    extracted = currentExtracted,
                    selectedFormat = singleFormat,
                    downloadAllPhotos = false
                )
                Toast.makeText(
                    context,
                    "Đang tải $itemLabel...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Story Login WebView Dialog
    val currentLoginPlatform = loginDialogPlatform
    if (currentLoginPlatform != null) {
        StoryWebViewLoginDialog(
            platform = currentLoginPlatform,
            onDismiss = {
                loginDialogPlatform = null
                pendingRetryUrlAfterLogin = null
            },
            onLoginSuccess = { cookie ->
                DirectVideoExtractor.setPlatformCookie(context, currentLoginPlatform, cookie)
                loginStatusTick++
                loginDialogPlatform = null
                Toast.makeText(
                    context,
                    "Đã kết nối tài khoản ${currentLoginPlatform.displayName} thành công!",
                    Toast.LENGTH_SHORT
                ).show()
                val retryUrl = pendingRetryUrlAfterLogin
                pendingRetryUrlAfterLogin = null
                if (!retryUrl.isNullOrBlank()) {
                    VideoDownloadService.startDownload(context, retryUrl, selectedPreference)
                }
            },
            onLogout = {
                DirectVideoExtractor.clearPlatformCookie(context, currentLoginPlatform)
                loginStatusTick++
                loginDialogPlatform = null
                Toast.makeText(
                    context,
                    "Đã xoá phiên đăng nhập ${currentLoginPlatform.displayName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Cấu hình Trình Tải Video & Story",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Ứng dụng sử dụng bộ trích xuất trực tiếp siêu nhẹ kết hợp Server API Online:",
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

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tài khoản tải Story (Facebook / Instagram yêu cầu đăng nhập để xem Story):",
                        fontSize = 12.5.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showSettingsDialog = false
                                loginDialogPlatform = VideoPlatform.FACEBOOK
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (fbLoggedIn) "FB: Đã kết nối" else "Đăng nhập FB",
                                fontSize = 12.sp,
                                color = if (fbLoggedIn) AccentGreen else AccentCyan
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                showSettingsDialog = false
                                loginDialogPlatform = VideoPlatform.INSTAGRAM
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (igLoggedIn) "IG: Đã kết nối" else "Đăng nhập IG",
                                fontSize = 12.sp,
                                color = if (igLoggedIn) AccentGreen else AccentPink
                            )
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
private fun StoryLoginChip(
    label: String,
    isLoggedIn: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val color = if (isLoggedIn) AccentGreen else accentColor
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun FormatSelectionDialog(
    extracted: ExtractedVideo,
    onDismiss: () -> Unit,
    onSelectFormat: (VideoFormatOption) -> Unit,
    onDownloadAllPhotos: () -> Unit,
    onDownloadSingleMediaItem: (Int, ExtractedMediaItem) -> Unit
) {
    val scrollState = rememberScrollState()
    val videoFormats = remember(extracted) {
        extracted.formats.filter { it.mediaType == MediaType.VIDEO }
    }
    val audioFormats = remember(extracted) {
        extracted.formats.filter { it.mediaType == MediaType.AUDIO }
    }
    val imageFormats = remember(extracted) {
        extracted.formats.filter { it.mediaType == MediaType.IMAGE }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .border(1.dp, OutlineSoft, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlatformBadge(platform = extracted.platform)
                        Text(
                            text = "Tuỳ chọn Tải về",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Đóng",
                            tint = TextSecondary
                        )
                    }
                }

                Text(
                    text = extracted.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Multi-photo / Carousel section
                if (extracted.mediaItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AccentGreen.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AccentGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Collections,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Phát hiện bài đăng chứa ${extracted.mediaItems.size} ảnh/slide",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = AccentGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Button(
                                onClick = onDownloadAllPhotos,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tải toàn bộ ${extracted.mediaItems.size} ảnh gốc (Full HD)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }

                            if (extracted.mediaItems.size > 1) {
                                Text(
                                    text = "Hoặc bấm để tải lẻ từng ảnh:",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    extracted.mediaItems.forEachIndexed { idx, item ->
                                        OutlinedButton(
                                            onClick = { onDownloadSingleMediaItem(idx, item) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "Ảnh ${idx + 1}",
                                                fontSize = 11.5.sp,
                                                color = AccentCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Video Qualities section
                if (videoFormats.isNotEmpty()) {
                    Text(
                        text = "🎬 Chọn chất lượng Video (MP4):",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    videoFormats.forEach { fmt ->
                        FormatOptionRow(
                            fmt = fmt,
                            accentColor = AccentCyan,
                            onClick = { onSelectFormat(fmt) }
                        )
                    }
                }

                // Single Image Formats (when not a multi-photo carousel)
                if (extracted.mediaItems.isEmpty() && imageFormats.isNotEmpty()) {
                    Text(
                        text = "🖼️ Tải Ảnh gốc:",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    imageFormats.forEach { fmt ->
                        FormatOptionRow(
                            fmt = fmt,
                            accentColor = AccentGreen,
                            onClick = { onSelectFormat(fmt) }
                        )
                    }
                }

                // Audio / MP3 section
                if (audioFormats.isNotEmpty()) {
                    Text(
                        text = "🎵 Tải Âm thanh / Nhạc nền (MP3 / M4A):",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    audioFormats.forEach { fmt ->
                        FormatOptionRow(
                            fmt = fmt,
                            accentColor = AccentAmber,
                            onClick = { onSelectFormat(fmt) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatOptionRow(
    fmt: VideoFormatOption,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (fmt.mediaType) {
                        MediaType.AUDIO -> Icons.Rounded.MusicNote
                        MediaType.IMAGE -> Icons.Rounded.Image
                        MediaType.VIDEO -> Icons.Rounded.VideoFile
                    },
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = fmt.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val subInfo = buildList {
                        add(fmt.ext.uppercase())
                        if (fmt.qualityBadge.isNotBlank()) add(fmt.qualityBadge)
                        if (fmt.sizeBytes > 0L) add(VideoDownloadService.formatBytes(fmt.sizeBytes))
                        if (fmt.extractAudioFromVideo) add("Tách âm thanh trực tiếp")
                    }.joinToString(" • ")
                    Text(
                        text = subInfo,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "Tải",
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StoryWebViewLoginDialog(
    platform: VideoPlatform,
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val loginUrl = remember(platform) {
        if (platform == VideoPlatform.INSTAGRAM) {
            "https://www.instagram.com/accounts/login/"
        } else {
            "https://m.facebook.com/login.php"
        }
    }
    val hasExistingSession = remember(platform) {
        DirectVideoExtractor.hasPlatformLogin(context, platform)
    }
    var loginCompleted by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = DarkSurface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f)
                .border(1.dp, OutlineSoft, RoundedCornerShape(22.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Đăng nhập ${platform.displayName} để tải Story",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Cookie chỉ lưu cục bộ trên máy bạn để mở khoá tải Story",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (hasExistingSession) {
                            TextButton(onClick = onLogout) {
                                Text("Đăng xuất", color = AccentRed, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Đóng",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (loginCompleted) return
                                        val checkDomains = if (platform == VideoPlatform.INSTAGRAM) {
                                            listOf("https://www.instagram.com", "https://instagram.com")
                                        } else {
                                            listOf("https://www.facebook.com", "https://m.facebook.com")
                                        }
                                        val combinedCookie = checkDomains.mapNotNull { d ->
                                            cookieManager.getCookie(d)
                                        }.maxByOrNull { it.length }.orEmpty()

                                        val isAuthed = if (platform == VideoPlatform.INSTAGRAM) {
                                            combinedCookie.contains("sessionid=") &&
                                                !combinedCookie.contains("sessionid=\"\"") &&
                                                !combinedCookie.contains("sessionid=;")
                                        } else {
                                            combinedCookie.contains("c_user=") && combinedCookie.contains("xs=")
                                        }
                                        if (isAuthed) {
                                            loginCompleted = true
                                            cookieManager.flush()
                                            onLoginSuccess(combinedCookie)
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val reqUrl = request?.url?.toString().orEmpty()
                                        if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                            return false
                                        }
                                        return true
                                    }
                                }
                                loadUrl(loginUrl)
                            }
                        }
                    )
                }
            }
        }
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
    onLoginAndRetry: () -> Unit,
    onPickFormat: () -> Unit,
    onRemove: () -> Unit,
    onOpenMedia: () -> Unit,
    onShareMedia: () -> Unit
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    PlatformBadge(platform = task.platform)
                    if (task.qualityLabel.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(AccentPurple.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = task.qualityLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentPurple,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                if (task.status == DownloadStatus.EXTRACTING || (task.totalBytes <= 0L && task.totalItems <= 1)) {
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
                        "Đang bóc tách luồng gốc..."
                    } else if (task.totalItems > 1) {
                        "Đang tải ảnh ${task.completedItems + 1}/${task.totalItems} • ${
                            VideoDownloadService.formatBytes(task.downloadedBytes)
                        }"
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
                val folderLabel = when (task.mediaType) {
                    MediaType.IMAGE -> if (task.totalItems > 1) {
                        "Đã lưu ${task.totalItems} ảnh vào Thư viện (Pictures/ToolsHub)"
                    } else {
                        "Đã lưu ảnh vào Thư viện (Pictures/ToolsHub)"
                    }
                    MediaType.AUDIO -> "Đã lưu nhạc vào Music/ToolsHub"
                    MediaType.VIDEO -> "Đã lưu vào Thư viện (Movies/ToolsHub)"
                }
                val openBtnLabel = when (task.mediaType) {
                    MediaType.IMAGE -> if (task.totalItems > 1) "Mở Ảnh (${task.totalItems})" else "Mở Ảnh"
                    MediaType.AUDIO -> "Phát Nhạc"
                    MediaType.VIDEO -> "Mở Video"
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$folderLabel • ${VideoDownloadService.formatBytes(task.downloadedBytes)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = AccentGreen)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenMedia,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen.copy(alpha = 0.2f),
                                contentColor = AccentGreen
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = when (task.mediaType) {
                                    MediaType.IMAGE -> Icons.Rounded.Image
                                    MediaType.AUDIO -> Icons.Rounded.MusicNote
                                    MediaType.VIDEO -> Icons.Rounded.PlayArrow
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(openBtnLabel, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }

                        OutlinedButton(
                            onClick = onShareMedia,
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

                        IconButton(onClick = onPickFormat) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Đổi chất lượng / MP3",
                                tint = AccentPurple
                            )
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
                    text = task.errorMessage ?: "Đã xảy ra lỗi khi tải",
                    style = MaterialTheme.typography.bodySmall.copy(color = AccentRed)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRemove) {
                        Text("Xoá", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    if (task.requiresLogin ||
                        task.errorMessage?.contains("login.php", ignoreCase = true) == true ||
                        task.errorMessage?.contains("Story", ignoreCase = true) == true
                    ) {
                        Button(
                            onClick = onLoginAndRetry,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Đăng nhập ${task.platform.displayName} & Tải lại",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
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

private fun openSavedMedia(context: Context, task: DownloadTaskState) {
    val uriStr = task.savedUri ?: task.savedUris.firstOrNull()
    if (uriStr.isNullOrBlank()) {
        Toast.makeText(context, "Không tìm thấy đường dẫn file đã tải", Toast.LENGTH_SHORT).show()
        return
    }
    val mimeType = when (task.mediaType) {
        MediaType.IMAGE -> "image/*"
        MediaType.AUDIO -> "audio/*"
        MediaType.VIDEO -> "video/mp4"
    }
    try {
        val uri = Uri.parse(uriStr)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Throwable) {
        Toast.makeText(context, "Không thể mở file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareSavedMedia(context: Context, task: DownloadTaskState) {
    val uris = if (task.savedUris.isNotEmpty()) {
        task.savedUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
    } else {
        listOfNotNull(task.savedUri?.let { runCatching { Uri.parse(it) }.getOrNull() })
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, "Không tìm thấy file để chia sẻ", Toast.LENGTH_SHORT).show()
        return
    }
    val mimeType = when (task.mediaType) {
        MediaType.IMAGE -> "image/*"
        MediaType.AUDIO -> "audio/*"
        MediaType.VIDEO -> "video/mp4"
    }
    try {
        val shareIntent = if (uris.size > 1) {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ"))
    } catch (e: Throwable) {
        Toast.makeText(context, "Không thể chia sẻ: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
