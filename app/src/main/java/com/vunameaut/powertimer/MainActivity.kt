package com.vunameaut.powertimer

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.vunameaut.powertimer.downloader.VideoPlatform
import com.vunameaut.powertimer.service.VideoDownloadService
import com.vunameaut.powertimer.tools.ToolRegistry
import com.vunameaut.powertimer.ui.screens.HubScreen
import com.vunameaut.powertimer.ui.screens.UpdateScreen
import com.vunameaut.powertimer.ui.theme.PowerTimerTheme
import com.vunameaut.powertimer.ui.viewmodel.TimerViewModel
import com.vunameaut.powertimer.update.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val timerViewModel: TimerViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private var currentRoute by mutableStateOf("hub")

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

        checkAndRequestNotificationPermission()
        handleIncomingIntent(intent)

        setContent {
            PowerTimerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val updateState by updateViewModel.state.collectAsState()

                    LaunchedEffect(Unit) { updateViewModel.autoCheck() }

                    BackHandler(enabled = currentRoute != "hub") { currentRoute = "hub" }

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
                                onDownload = updateViewModel::download,
                                onInstall = updateViewModel::install
                            )
                            else -> HubScreen(
                                tools = ToolRegistry,
                                updateState = updateState,
                                onOpenTool = { currentRoute = "tool:${it.id}" },
                                onOpenUpdate = { currentRoute = "update" }
                            )
                        }
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

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

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
        timerViewModel.refreshPermissions()
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
