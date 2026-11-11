package com.vunameaut.powertimer.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vunameaut.powertimer.ocr.OcrAndClipboardHelper
import com.vunameaut.powertimer.ocr.SmartExtractedEntity
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickOcrClipboardTool(
    onExit: () -> Unit,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var clipboardItems by remember { mutableStateOf(OcrAndClipboardHelper.loadClipboardHistory(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var manualNoteInput by remember { mutableStateOf("") }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var smartEntities by remember { mutableStateOf<List<SmartExtractedEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        val currentClip = OcrAndClipboardHelper.readCurrentSystemClipboard(context)
        if (!currentClip.isNullOrBlank()) {
            clipboardItems = OcrAndClipboardHelper.addClipboardHistory(context, currentClip)
        }
    }

    fun processImageForOcr(bmp: Bitmap) {
        capturedBitmap = bmp
        isRecognizing = true
        scope.launch {
            val res = OcrAndClipboardHelper.recognizeTextFromBitmap(bmp)
            isRecognizing = false
            res.onSuccess { text ->
                recognizedText = text
                smartEntities = OcrAndClipboardHelper.extractSmartEntities(text)
                if (text.isNotBlank()) {
                    clipboardItems = OcrAndClipboardHelper.addClipboardHistory(context, text, "Quét OCR")
                } else {
                    Toast.makeText(context, "Không tìm thấy chữ trong bức ảnh này!", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    "Lỗi nhận diện chữ: ${err.localizedMessage ?: "Hãy thử lại"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            val softBmp = if (bmp.config != Bitmap.Config.ARGB_8888) {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else bmp
            processImageForOcr(softBmp)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Cần quyền Camera để chụp ảnh quét chữ!", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = OcrAndClipboardHelper.loadBitmapFromUri(context, uri)
            if (bmp != null) {
                processImageForOcr(bmp)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Quick OCR & Clipboard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Quét chữ, STK, mã vận đơn từ ảnh & Quản lý bộ nhớ tạm",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = {
                        Text(
                            text = "🔍 Quét chữ từ ảnh (Quick OCR)",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentAmber,
                        selectedLabelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = {
                        Text(
                            text = "📋 Clipboard (${clipboardItems.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan,
                        selectedLabelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedTab == 0) {
                    // ==================== TAB 0: QUICK OCR ====================
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DocumentScanner,
                                contentDescription = null,
                                tint = AccentAmber,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Chụp hoặc chọn ảnh có chữ để trích xuất ngay",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            )
                            Text(
                                text = "Tự động lọc riêng Số tài khoản ngân hàng, Số điện thoại, Mã vận đơn, Số tiền và Đường link để bạn copy 1 chạm!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            cameraLauncher.launch(null)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentAmber,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Chụp ảnh", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Image, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Chọn ảnh", color = AccentAmber, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (isRecognizing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = AccentAmber, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Đang bóc tách chữ và số tài khoản từ ảnh...", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    val bmp = capturedBitmap
                    if (bmp != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Ảnh đã chọn",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                    }

                    // Chip Bóc tách thông minh (STK, SĐT, Mã vận đơn, Link, Tiền)
                    if (smartEntities.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = AccentGreen.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "⚡ Bóc tách nhanh (Bấm 1 chạm để Sao chép ngay):",
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    smartEntities.forEach { entity ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                .clickable {
                                                    clipboardItems = OcrAndClipboardHelper.copyToClipboardAndSave(
                                                        context = context,
                                                        text = entity.value,
                                                        category = entity.type.label,
                                                        toastMsg = "Đã copy ${entity.type.label}: ${entity.value}"
                                                    )
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(text = entity.type.badgeEmoji, fontSize = 13.sp)
                                                Column {
                                                    Text(
                                                        text = entity.type.label,
                                                        fontSize = 10.sp,
                                                        color = AccentCyan,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = entity.value,
                                                        fontSize = 13.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (recognizedText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
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
                                    Text(
                                        text = "📄 Toàn bộ văn bản trích xuất:",
                                        fontWeight = FontWeight.Bold,
                                        color = AccentAmber,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    Button(
                                        onClick = {
                                            clipboardItems = OcrAndClipboardHelper.copyToClipboardAndSave(
                                                context,
                                                recognizedText,
                                                "Quét OCR"
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentGreen,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Copy tất cả",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = recognizedText,
                                    onValueChange = { recognizedText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 300.dp)
                                )
                            }
                        }
                    }
                } else {
                    // ==================== TAB 1: QUẢN LÝ CLIPBOARD ====================
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualNoteInput,
                                    onValueChange = { manualNoteInput = it },
                                    placeholder = { Text("Thêm nhanh STK, địa chỉ, ghi chú vào Clipboard...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (manualNoteInput.isNotBlank()) {
                                            clipboardItems = OcrAndClipboardHelper.copyToClipboardAndSave(
                                                context,
                                                manualNoteInput,
                                                OcrAndClipboardHelper.detectCategory(manualNoteInput)
                                            )
                                            manualNoteInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCyan,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Lưu")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val sysClip = OcrAndClipboardHelper.readCurrentSystemClipboard(context)
                                        if (!sysClip.isNullOrBlank()) {
                                            clipboardItems = OcrAndClipboardHelper.addClipboardHistory(context, sysClip)
                                            Toast.makeText(context, "Đã lưu nội dung từ bộ nhớ tạm hiện tại!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Bộ nhớ tạm hiện đang trống!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Lấy từ Clipboard máy", color = AccentCyan, fontSize = 12.sp)
                                }

                                if (clipboardItems.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            clipboardItems = OcrAndClipboardHelper.clearUnpinnedClipboardHistory(context)
                                        }
                                    ) {
                                        Text("Xóa mục chưa ghim", color = AccentRed, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text("Tìm kiếm lịch sử văn bản, STK, mã vận đơn...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val filtered = remember(clipboardItems, searchQuery) {
                        if (searchQuery.isBlank()) clipboardItems
                        else clipboardItems.filter {
                            it.text.contains(searchQuery, ignoreCase = true) ||
                                it.category.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

                    filtered.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (item.isPinned) {
                                AccentPurple.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (item.isPinned) AccentPurple.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    OcrAndClipboardHelper.copyToClipboardAndSave(
                                        context,
                                        item.text,
                                        item.category
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AccentCyan.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = item.category,
                                                color = AccentCyan,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = fmt.format(Date(item.timestamp)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                clipboardItems = OcrAndClipboardHelper.togglePinClipboardItem(context, item.id)
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PushPin,
                                                contentDescription = "Ghim",
                                                tint = if (item.isPinned) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val share = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, item.text)
                                                }
                                                context.startActivity(Intent.createChooser(share, "Chia sẻ văn bản"))
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Share,
                                                contentDescription = "Chia sẻ",
                                                tint = AccentCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                clipboardItems = OcrAndClipboardHelper.deleteClipboardItem(context, item.id)
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Xóa",
                                                tint = AccentRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = item.text,
                                    fontSize = 13.5.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

