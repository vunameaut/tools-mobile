package com.vunameaut.powertimer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vunameaut.powertimer.ui.viewmodel.TimerViewModel

/** Công cụ PowerTimer (có điều hướng nội bộ Home ⇄ Settings). */
@Composable
fun PowerTimerTool(onExit: () -> Unit) {
    val viewModel: TimerViewModel = viewModel()
    var screen by rememberSaveable { mutableStateOf("home") }

    BackHandler(enabled = screen == "settings") {
        viewModel.refreshPermissions()
        screen = "home"
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "power_timer_nav"
    ) { current ->
        when (current) {
            "settings" -> SettingsScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.refreshPermissions()
                    screen = "home"
                }
            )
            else -> HomeScreen(
                viewModel = viewModel,
                onBack = onExit,
                onOpenSettings = { screen = "settings" }
            )
        }
    }
}
