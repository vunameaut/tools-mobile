package com.vunameaut.powertimer.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunameaut.powertimer.BuildConfig
import com.vunameaut.powertimer.tools.ToolEntry
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.update.UpdateState

@Composable
fun HubScreen(
    tools: List<ToolEntry>,
    updateState: UpdateState,
    onOpenTool: (ToolEntry) -> Unit,
    onOpenUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUpdate = updateState is UpdateState.Available ||
        updateState is UpdateState.Downloading ||
        updateState is UpdateState.ReadyToInstall

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Vệt sáng trang trí phía trên
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(AccentPurple.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            item(span = { GridItemSpan(2) }) {
                HubHeader(
                    toolCount = tools.size,
                    hasUpdate = hasUpdate,
                    onOpenUpdate = onOpenUpdate
                )
            }

            if (hasUpdate) {
                item(span = { GridItemSpan(2) }) {
                    UpdateBanner(updateState = updateState, onClick = onOpenUpdate)
                }
            }

            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "CÔNG CỤ CỦA TÔI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            itemsIndexed(tools, key = { _, t -> t.id }) { index, tool ->
                ToolCard(tool = tool, index = index, onClick = { onOpenTool(tool) })
            }

            item { ComingSoonCard() }
        }
    }
}

@Composable
private fun HubHeader(toolCount: Int, hasUpdate: Boolean, onOpenUpdate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tools Hub",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$toolCount công cụ  •  v${BuildConfig.VERSION_NAME}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(46.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                IconButton(onClick = onOpenUpdate) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Cập nhật & thông tin",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            if (hasUpdate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(AccentAmber)
                )
            }
        }
    }
}

@Composable
private fun UpdateBanner(updateState: UpdateState, onClick: () -> Unit) {
    val text = when (updateState) {
        is UpdateState.Available -> "Có bản mới v${updateState.info.versionName}"
        is UpdateState.Downloading -> "Đang tải bản cập nhật… ${(updateState.progress * 100).toInt()}%"
        is UpdateState.ReadyToInstall -> "Đã tải xong v${updateState.info.versionName} — chạm để cài"
        else -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(AccentPurple, AccentCyan.copy(alpha = 0.85f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = Color.White)
        Spacer(Modifier.size(12.dp))
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun ToolCard(tool: ToolEntry, index: Int, onClick: () -> Unit) {
    // Hiệu ứng xuất hiện lần lượt (stagger) + nhún khi nhấn
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120L + index * 90L)
        visible = true
    }
    val enterAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "card_alpha")
    val enterOffset by animateFloatAsState(
        if (visible) 0f else 48f,
        tween(500, easing = FastOutSlowInEasing),
        label = "card_offset"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "card_press"
    )

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .graphicsLayer {
                alpha = enterAlpha
                translationY = enterOffset * density
                scaleX = pressScale
                scaleY = pressScale
            }
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(tool.accent.copy(alpha = 0.20f), MaterialTheme.colorScheme.surface)
                )
            )
            .border(1.dp, tool.accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tool.accent.copy(alpha = 0.22f))
        ) {
            Icon(tool.icon, contentDescription = null, tint = tool.accent, modifier = Modifier.size(28.dp))
        }
        Column {
            Text(
                text = tool.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tool.description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComingSoonCard() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Công cụ tiếp theo",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

