package com.vunameaut.powertimer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.vunameaut.powertimer.downloader.VideoPlatform
import com.vunameaut.powertimer.ocr.OcrAndClipboardHelper
import com.vunameaut.powertimer.service.QuickTileActions
import com.vunameaut.powertimer.service.VideoDownloadService
import com.vunameaut.powertimer.tools.ToolRegistry
import com.vunameaut.powertimer.ui.components.WaterdropIslandOverlay
import com.vunameaut.powertimer.ui.screens.HubScreen
import com.vunameaut.powertimer.ui.screens.UpdateScreen
import com.vunameaut.powertimer.ui.theme.PowerTimerTheme
import com.vunameaut.powertimer.ui.viewmodel.TimerViewModel
import com.vunameaut.powertimer.update.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val timerViewModel: TimerViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private var currentRoute by mutableStateOf("hub")
    private var pendingClipboardVideoDownload by mutableStateOf(false)
    private var showWaterdropPreview by mutableStateOf(true)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        timerViewModel.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        val waterdropPrefs = getSharedPreferences("waterdrop_island_prefs", Context.MODE_PRIVATE)
        showWaterdropPreview = waterdropPrefs.getBoolean("always_show_waterdrop", true)

        checkAndRequestNotificationPermission()
        handleIncomingIntent(intent)

        setContent {
            PowerTimerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val updateState by updateViewModel.state.collectAsState()

                    LaunchedEffect(Unit) { updateViewModel.autoCheck() }

                    BackHandler(enabled = currentRoute != "hub") { currentRoute = "hub" }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentRoute,
                            transitionSpec = {
                                val backToHub = targetState == "hub"
                                val dir = if (backToHub) -1 else 1
                                (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { dir * it / 4 } +
                                    fadeIn(tween(380))) togetherWith
                                    (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -dir * it / 4 } +
                                        fadeOut(tween(220)))
                            },
                            label = "root_nav"
                        ) { current ->
                            val tool = ToolRegistry.firstOrNull { "tool:${it.id}" == current }
                            when {
                                tool != null -> {
                                    tool.content { currentRoute = "hub" }
                                }
                                current == "update" -> UpdateScreen(
                                    state = updateState,
                                    onBack = { currentRoute = "hub" },
                                    onCheck = updateViewModel::check,
                                    onDownload = { updateViewModel.download(this@MainActivity) },
                                    onInstall = { updateViewModel.install(this@MainActivity) }
                                )
                                else -> HubScreen(
                                    tools = ToolRegistry,
                                    updateState = updateState,
                                    onOpenTool = { currentRoute = "tool:${it.id}" },
                                    onOpenUpdate = { currentRoute = "update" }
                                )
                            }
                        }

                        // Hiệu ứng tương tác Màn hình Giọt nước (Dynamic Island kiểu YouTube / Spotify)
                        WaterdropIslandOverlay(
                            forceVisibleForPreview = showWaterdropPreview && currentRoute == "tool:power_timer",
                            onOpenPowerTimerScreen = if (currentRoute != "tool:power_timer") {
                                { currentRoute = "tool:power_timer" }
                            } else null,
                            onDismissPreview = {
                                showWaterdropPreview = false
                                getSharedPreferences("waterdrop_island_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("always_show_waterdrop", false)
                                    .apply()
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingClipboardVideoDownload) {
            pendingClipboardVideoDownload = false
            triggerDownloadFromClipboard()
        }
    }

    private fun triggerDownloadFromClipboard() {
        val clipText = OcrAndClipboardHelper.readCurrentSystemClipboard(this)
        val extractedUrl = VideoPlatform.extractFirstUrl(clipText)
        if (!extractedUrl.isNullOrBlank()) {
            val platform = VideoPlatform.detect(extractedUrl)
            VideoDownloadService.startDownload(this, extractedUrl)
            Toast.makeText(
                this,
                "Đang tải ngay video ${platform.displayName} từ Clipboard...",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "Không tìm thấy đường link video hợp lệ trong Clipboard!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // Xử lý từ Bộ nút bấm nhanh Quick Settings Tiles trên thanh trạng thái
        val quickAction = intent.getStringExtra(QuickTileActions.EXTRA_QUICK_ACTION)
        if (!quickAction.isNullOrBlank()) {
            intent.removeExtra(QuickTileActions.EXTRA_QUICK_ACTION)
            when (quickAction) {
                QuickTileActions.ACTION_OPEN_QR_SCANNER -> {
                    currentRoute = "tool:qr_studio"
                }
                QuickTileActions.ACTION_OPEN_QUICK_OCR -> {
                    currentRoute = "tool:quick_ocr"
                }
                QuickTileActions.ACTION_SHOW_MY_VIETQR -> {
                    currentRoute = "tool:qr_studio"
                }
                QuickTileActions.ACTION_DOWNLOAD_CLIPBOARD_VIDEO -> {
                    currentRoute = "tool:video_downloader"
                    pendingClipboardVideoDownload = true
                    if (hasWindowFocus()) {
                        pendingClipboardVideoDownload = false
                        triggerDownloadFromClipboard()
                    }
                }
            }
            return
        }

        if (intent.getBooleanExtra(VideoDownloadService.EXTRA_OPEN_DOWNLOADER, false)) {
            currentRoute = "tool:video_downloader"
            intent.removeExtra(VideoDownloadService.EXTRA_OPEN_DOWNLOADER)
            return
        }

        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val extractedUrl = VideoPlatform.extractFirstUrl(sharedText)
            currentRoute = "tool:video_downloader"

            if (!extractedUrl.isNullOrBlank()) {
                val platform = VideoPlatform.detect(extractedUrl)
                VideoDownloadService.startDownload(this, extractedUrl)
                Toast.makeText(
                    this,
                    "Đang tự động tải video ${platform.displayName}...",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Không tìm thấy đường link video hợp lệ trong nội dung chia sẻ",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Xoá action để tránh kích hoạt lại khi xoay màn hình
            intent.action = Intent.ACTION_MAIN
        }
    }

    override fun onResume() {
        super.onResume()
        val waterdropPrefs = getSharedPreferences("waterdrop_island_prefs", Context.MODE_PRIVATE)
        showWaterdropPreview = waterdropPrefs.getBoolean("always_show_waterdrop", true)
        timerViewModel.refreshPermissions()
        updateViewModel.onAppResumed(this)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!isGranted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
