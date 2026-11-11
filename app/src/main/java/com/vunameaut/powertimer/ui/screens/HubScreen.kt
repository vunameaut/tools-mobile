package com.vunameaut.powertimer.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vunameaut.powertimer.BuildConfig
import com.vunameaut.powertimer.qr.VietQrHelper
import com.vunameaut.powertimer.service.ClipboardVideoTileService
import com.vunameaut.powertimer.service.MyVietQrTileService
import com.vunameaut.powertimer.service.QuickTileActions
import com.vunameaut.powertimer.service.ScannerOcrTileService
import com.vunameaut.powertimer.service.SleepTimerTileService
import com.vunameaut.powertimer.tools.ToolEntry
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.update.UpdateState
import kotlinx.coroutines.delay

private const val HUB_ORDER_PREFS = "tools_hub_order_prefs"
private const val KEY_TOOL_ORDER = "ordered_tool_ids"

private fun loadOrderedTools(context: Context, registry: List<ToolEntry>): List<ToolEntry> {
    val prefs = context.getSharedPreferences(HUB_ORDER_PREFS, Context.MODE_PRIVATE)
    val savedRaw = prefs.getString(KEY_TOOL_ORDER, null)
    if (savedRaw.isNullOrBlank()) return registry

    val savedIds = savedRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val byId = registry.associateBy { it.id }
    val ordered = mutableListOf<ToolEntry>()
    for (id in savedIds) {
        byId[id]?.let { ordered.add(it) }
    }
    // Thêm các công cụ mới chưa có trong thứ tự đã lưu
    for (tool in registry) {
        if (ordered.none { it.id == tool.id }) {
            ordered.add(tool)
        }
    }
    return ordered
}

private fun saveOrderedTools(context: Context, ordered: List<ToolEntry>) {
    val serialized = ordered.joinToString(",") { it.id }
    context.getSharedPreferences(HUB_ORDER_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_TOOL_ORDER, serialized)
        .apply()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HubScreen(
    tools: List<ToolEntry>,
    updateState: UpdateState,
    onOpenTool: (ToolEntry) -> Unit,
    onOpenUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()

    var orderedTools by remember(tools) {
        mutableStateOf(loadOrderedTools(context, tools))
    }
    var isReorderMode by remember { mutableStateOf(false) }
    val myVietQr = remember { VietQrHelper.loadMyVietQrProfile(context) }

    // Trạng thái kéo-thả trực tiếp trên lưới
    var draggingToolId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun updateOrder(newList: List<ToolEntry>) {
        orderedTools = newList
        saveOrderedTools(context, newList)
    }

    fun moveTool(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in orderedTools.indices || toIndex !in orderedTools.indices || fromIndex == toIndex) return
        val mutable = orderedTools.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        updateOrder(mutable)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

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
            state = gridState,
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
                    toolCount = orderedTools.size,
                    hasUpdate = hasUpdate,
                    onOpenUpdate = onOpenUpdate
                )
            }

            if (hasUpdate) {
                item(span = { GridItemSpan(2) }) {
                    UpdateBanner(updateState = updateState, onClick = onOpenUpdate)
                }
            }

            if (myVietQr.isValid && myVietQr.pinToHome) {
                item(span = { GridItemSpan(2) }) {
                    MyVietQrPreviewCard(
                        profile = myVietQr,
                        onEditClick = {
                            tools.firstOrNull { it.id == "qr_studio" }?.let(onOpenTool)
                        }
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CÔNG CỤ CỦA TÔI",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isReorderMode) {
                                TextButton(
                                    onClick = {
                                        updateOrder(tools)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestartAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Mặc định",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val pillBg by animateColorAsState(
                                if (isReorderMode) AccentGreen.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surface,
                                label = "reorder_pill_bg"
                            )
                            val pillBorder by animateColorAsState(
                                if (isReorderMode) AccentGreen
                                else MaterialTheme.colorScheme.outline,
                                label = "reorder_pill_border"
                            )
                            val pillContentColor by animateColorAsState(
                                if (isReorderMode) AccentGreen
                                else AccentCyan,
                                label = "reorder_pill_fg"
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(pillBg)
                                    .border(1.dp, pillBorder, RoundedCornerShape(50))
                                    .clickable {
                                        isReorderMode = !isReorderMode
                                        draggingToolId = null
                                        dragOffset = Offset.Zero
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isReorderMode) Icons.Rounded.Check else Icons.Rounded.SwapVert,
                                    contentDescription = null,
                                    tint = pillContentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (isReorderMode) "Xong" else "Sắp xếp",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = pillContentColor
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isReorderMode,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AccentCyan.copy(alpha = 0.10f))
                                .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Kéo thả thẻ đến vị trí mới, hoặc dùng các nút mũi tên / Ghim trên thẻ để đổi thứ tự.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            itemsIndexed(orderedTools, key = { _, t -> t.id }) { index, tool ->
                val isDragging = draggingToolId == tool.id

                ToolCard(
                    tool = tool,
                    index = index,
                    totalCount = orderedTools.size,
                    isReorderMode = isReorderMode,
                    isDragging = isDragging,
                    dragOffset = if (isDragging) dragOffset else Offset.Zero,
                    onClick = {
                        if (!isReorderMode) {
                            onOpenTool(tool)
                        }
                    },
                    onLongClick = {
                        if (!isReorderMode) {
                            isReorderMode = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onMoveEarlier = { moveTool(index, index - 1) },
                    onMoveLater = { moveTool(index, index + 1) },
                    onPinToTop = { moveTool(index, 0) },
                    onDragStart = {
                        draggingToolId = tool.id
                        dragOffset = Offset.Zero
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragDelta = { change ->
                        dragOffset += change
                        val currentIdx = orderedTools.indexOfFirst { it.id == tool.id }
                        if (currentIdx != -1) {
                            val itemInfo = gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == tool.id }
                            if (itemInfo != null) {
                                val centerX = itemInfo.offset.x + itemInfo.size.width / 2f + dragOffset.x
                                val centerY = itemInfo.offset.y + itemInfo.size.height / 2f + dragOffset.y
                                val targetItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
                                    candidate.key != tool.id &&
                                        candidate.key is String &&
                                        centerX >= candidate.offset.x &&
                                        centerX <= candidate.offset.x + candidate.size.width &&
                                        centerY >= candidate.offset.y &&
                                        centerY <= candidate.offset.y + candidate.size.height
                                }
                                if (targetItem != null) {
                                    val targetIdx = orderedTools.indexOfFirst { it.id == targetItem.key }
                                    if (targetIdx != -1 && targetIdx != currentIdx) {
                                        val dx = (itemInfo.offset.x - targetItem.offset.x).toFloat()
                                        val dy = (itemInfo.offset.y - targetItem.offset.y).toFloat()
                                        moveTool(currentIdx, targetIdx)
                                        dragOffset += Offset(dx, dy)
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggingToolId = null
                        dragOffset = Offset.Zero
                    },
                    modifier = Modifier
                        .zIndex(if (isDragging) 10f else 0f)
                        .animateItem(
                            fadeInSpec = tween(250),
                            fadeOutSpec = tween(200),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                )
            }

            item(span = { GridItemSpan(2) }) {
                QuickSettingsTilesBanner()
            }

            item {
                ComingSoonCard(
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun QuickSettingsTilesBanner() {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⚡ Bộ 4 Nút Bấm Nhanh Trên Thanh Trạng Thái (Quick Settings)",
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                fontSize = 13.5.sp
            )
            Text(
                text = "Chạm vào nút bên dưới để gắn trực tiếp các công cụ lên thanh kéo thông báo của Android (dùng ngay không cần mở app):",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickTileChip(
                    label = "🌙 Hẹn giờ Màn hình",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        QuickTileActions.requestAddTileToStatusBar(
                            context,
                            SleepTimerTileService::class.java,
                            "Hẹn giờ Màn hình"
                        )
                    }
                )
                QuickTileChip(
                    label = "📷 Quét QR / OCR",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        QuickTileActions.requestAddTileToStatusBar(
                            context,
                            ScannerOcrTileService::class.java,
                            "Quét QR / OCR"
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickTileChip(
                    label = "🏦 Mã VietQR Của Tôi",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        QuickTileActions.requestAddTileToStatusBar(
                            context,
                            MyVietQrTileService::class.java,
                            "Mã VietQR Của Tôi"
                        )
                    }
                )
                QuickTileChip(
                    label = "📥 Tải Video Clipboard",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        QuickTileActions.requestAddTileToStatusBar(
                            context,
                            ClipboardVideoTileService::class.java,
                            "Tải Video Clipboard"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickTileChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .border(1.dp, AccentPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCard(
    tool: ToolEntry,
    index: Int,
    totalCount: Int,
    isReorderMode: Boolean,
    isDragging: Boolean,
    dragOffset: Offset,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onPinToTop: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100L + index * 75L)
        visible = true
    }
    val enterAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(420), label = "card_alpha")
    val enterOffset by animateFloatAsState(
        if (visible) 0f else 42f,
        tween(460, easing = FastOutSlowInEasing),
        label = "card_offset"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "jiggle")
    val jiggleAngle by infiniteTransition.animateFloat(
        initialValue = -1.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(170, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jiggle_rot"
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.06f
            pressed -> 0.95f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_press"
    )

    val dragModifier = if (isReorderMode) {
        Modifier.pointerInput(tool.id) {
            detectDragGestures(
                onDragStart = { onDragStart() },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount)
                }
            )
        }
    } else {
        Modifier
    }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .graphicsLayer {
                alpha = enterAlpha
                translationX = dragOffset.x
                translationY = enterOffset * density + dragOffset.y
                scaleX = pressScale
                scaleY = pressScale
                rotationZ = if (isReorderMode && !isDragging) jiggleAngle else 0f
                shadowElevation = if (isDragging) 24f else 0f
            }
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        tool.accent.copy(alpha = if (isDragging) 0.34f else 0.20f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(
                width = if (isReorderMode || isDragging) 1.6.dp else 1.dp,
                color = if (isDragging) AccentCyan else tool.accent.copy(alpha = if (isReorderMode) 0.65f else 0.35f),
                shape = RoundedCornerShape(24.dp)
            )
            .then(dragModifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
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

            if (isReorderMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (index > 0) {
                        SmallReorderIconButton(
                            icon = Icons.Rounded.PushPin,
                            tint = AccentAmber,
                            contentDescription = "Đưa lên đầu",
                            onClick = onPinToTop
                        )
                    }
                    SmallReorderIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        tint = if (index > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        contentDescription = "Lên trước",
                        enabled = index > 0,
                        onClick = onMoveEarlier
                    )
                    SmallReorderIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        tint = if (index < totalCount - 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        contentDescription = "Xuống sau",
                        enabled = index < totalCount - 1,
                        onClick = onMoveLater
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "#${index + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                text = if (isReorderMode) "Giữ & kéo thả hoặc bấm nút để đổi vị trí" else tool.description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                color = if (isReorderMode) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmallReorderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun ComingSoonCard(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
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
