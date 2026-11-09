package com.vunameaut.powertimer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentPurple

@Composable
fun CircularCountdownTimer(
    progress: Float,
    timeText: String,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    strokeWidth: Dp = 14.dp,
    onClick: () -> Unit = {}
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isRunning) progress else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "progress_animation"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val gradientColors = listOf(AccentCyan, AccentPurple)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            // Background track
            drawCircle(
                color = surfaceColor,
                radius = (size.toPx() - strokeWidth.toPx()) / 2,
                style = Stroke(width = strokeWidth.toPx())
            )

            // Animated Progress Arc
            val sweepAngle = 360f * animatedProgress
            drawArc(
                brush = Brush.sweepGradient(gradientColors),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isRunning) "ĐANG ĐẾM NGƯỢC" else "CHẠM ĐỂ CHỈNH GIỜ",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = if (isRunning) AccentCyan else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
