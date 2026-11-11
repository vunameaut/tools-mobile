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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vunameaut.powertimer.ocr.OcrAndClipboardHelper
import com.vunameaut.powertimer.qr.LiveCameraQrScanner
import com.vunameaut.powertimer.qr.PinnedQrItem
import com.vunameaut.powertimer.qr.QrHistoryItem
import com.vunameaut.powertimer.qr.VietQrHelper
import com.vunameaut.powertimer.qr.VietQrProfile
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentRed
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrBarcodeStudioTool(
    onExit: () -> Unit,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)

    var historyList by remember { mutableStateOf(VietQrHelper.loadHistory(context)) }
    var myVietQr by remember { mutableStateOf(VietQrHelper.loadMyVietQrProfile(context)) }
    var pinnedQrs by remember { mutableStateOf(VietQrHelper.loadPinnedQrs(context)) }
    var selectedPinnedId by remember(pinnedQrs) {
        mutableLongStateOf(pinnedQrs.firstOrNull()?.id ?: 0L)
    }

    // Nếu người dùng đã ghim mã QR, khi mở vào chức năng sẽ hiển thị luôn Tab "📌 Đã Ghim" (-1)
    // để thấy ngay mã QR mà không cần thao tác thêm bước nào!
    var selectedTab by remember {
        mutableIntStateOf(
            if (initialTab == 0 && pinnedQrs.isNotEmpty()) -1 else initialTab
        )
    }

    // Trạng thái Tab Quét QR Trực Tiếp (Live Camera)
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isLiveScannerActive by remember { mutableStateOf(true) }
    var scannedText by remember { mutableStateOf("") }
    var scannedFormat by remember { mutableStateOf("") }
    var scannedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Trạng thái Tab VietQR
    var selectedBankBin by remember { mutableStateOf(myVietQr.bankBin.ifBlank { "970422" }) }
    var accountNumber by remember { mutableStateOf(myVietQr.accountNumber) }
    var accountName by remember { mutableStateOf(myVietQr.accountName) }
    var transferAmount by remember { mutableStateOf(myVietQr.defaultAmount) }
    var transferMemo by remember { mutableStateOf(myVietQr.defaultMemo) }
    var pinToHome by remember { mutableStateOf(myVietQr.pinToHome) }

    // Trạng thái Tab Tạo QR Đa năng
    var createMode by remember { mutableStateOf("LINK") } // LINK, WIFI, BARCODE
    var customQrTitle by remember { mutableStateOf("") }
    var inputLinkOrText by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var qrFgColorHex by remember { mutableLongStateOf(0xFF000000L) }

    fun onQrDecoded(rawText: String, formatName: String, bmp: Bitmap?) {
        scannedBitmap = bmp
        scannedText = rawText
        scannedFormat = formatName
        isLiveScannerActive = false
        val itemType = if (rawText.startsWith("000201")) "VIETQR" else "SCAN"
        historyList = VietQrHelper.addHistoryItem(
            context,
            QrHistoryItem(
                type = itemType,
                title = if (itemType == "VIETQR") "Quét mã VietQR Ngân hàng" else "Quét mã ($formatName)",
                subtitle = rawText.take(80),
                rawContent = rawText
            )
        )
        Toast.makeText(context, "✅ Đã quét mã thành công!", Toast.LENGTH_SHORT).show()
    }

    fun handleGalleryBitmap(bmp: Bitmap) {
        val decoded = VietQrHelper.decodeFromBitmap(bmp)
        if (decoded != null) {
            onQrDecoded(decoded.first, decoded.second, bmp)
        } else {
            Toast.makeText(
                context,
                "Không tìm thấy mã QR / Barcode trong ảnh. Hãy thử ảnh rõ nét hơn!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            isLiveScannerActive = true
        } else {
            Toast.makeText(context, "Cần quyền Camera để quét mã QR trực tiếp!", Toast.LENGTH_SHORT).show()
        }
    }

    // Tự động xin quyền Camera ngay khi chuyển sang Tab Quét Mã (0) nếu chưa cấp
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = OcrAndClipboardHelper.loadBitmapFromUri(context, uri)
            if (bmp != null) {
                handleGalleryBitmap(bmp)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "QR & VietQR Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Ghim QR mở lên thấy ngay • Quét Camera trực tiếp • VietQR",
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
            // Thanh chuyển Tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = buildList {
                    if (pinnedQrs.isNotEmpty()) {
                        add(-1 to "📌 Đã Ghim (${pinnedQrs.size})")
                    }
                    add(0 to "📷 Quét Mã")
                    add(1 to "🏦 Tạo VietQR")
                    add(2 to "✨ Tạo QR / Barcode")
                    add(3 to "🕘 Lịch sử (${historyList.size})")
                }
                tabs.forEach { (idx, label) ->
                    val selected = selectedTab == idx
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedTab = idx
                            if (idx == 0 && scannedText.isBlank()) {
                                isLiveScannerActive = true
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (idx == -1) AccentGreen else AccentCyan,
                            selectedLabelColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    // ========================= TAB -1: MÃ QR ĐÃ GHIM (MỞ VÀO THẤY LUÔN) =========================
                    -1 -> {
                        if (pinnedQrs.isEmpty()) {
                            selectedTab = 0
                        } else {
                            val activeItem = pinnedQrs.firstOrNull { it.id == selectedPinnedId }
                                ?: pinnedQrs.first()

                            if (pinnedQrs.size > 1) {
                                Text(
                                    text = "📌 Chọn mã QR đã ghim của bạn (${pinnedQrs.size} mã):",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(pinnedQrs, key = { it.id }) { item ->
                                        val isCurrent = item.id == activeItem.id
                                        FilterChip(
                                            selected = isCurrent,
                                            onClick = { selectedPinnedId = item.id },
                                            label = {
                                                Text(
                                                    text = item.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AccentGreen,
                                                selectedLabelColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }

                            PinnedQrShowcaseCard(
                                item = activeItem,
                                isPrimary = pinnedQrs.firstOrNull()?.id == activeItem.id,
                                showMakePrimaryButton = pinnedQrs.size > 1,
                                onSetPrimary = {
                                    pinnedQrs = VietQrHelper.setPrimaryPinnedQr(context, activeItem.id)
                                    selectedPinnedId = activeItem.id
                                    Toast.makeText(
                                        context,
                                        "⭐ Đã đặt làm mã QR hiển thị đầu tiên khi mở chức năng!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onUnpin = {
                                    pinnedQrs = VietQrHelper.unpinQrById(context, activeItem.id)
                                    Toast.makeText(context, "Đã bỏ ghim mã QR", Toast.LENGTH_SHORT).show()
                                    if (pinnedQrs.isEmpty()) {
                                        selectedTab = 0
                                    } else {
                                        selectedPinnedId = pinnedQrs.first().id
                                    }
                                },
                                onEditVietQr = {
                                    if (activeItem.type == "VIETQR") {
                                        selectedBankBin = activeItem.bankBin.ifBlank { selectedBankBin }
                                        accountNumber = activeItem.accountNumber
                                        accountName = activeItem.accountName
                                        transferAmount = activeItem.amount
                                        transferMemo = activeItem.memo
                                        selectedTab = 1
                                    } else {
                                        createMode = activeItem.type
                                        customQrTitle = activeItem.title
                                        inputLinkOrText = activeItem.rawContent
                                        qrFgColorHex = activeItem.fgColorHex
                                        selectedTab = 2
                                    }
                                }
                            )

                            // Nút chuyển nhanh sang Camera Quét QR Trực Tiếp hoặc Tạo QR mới
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isLiveScannerActive = true
                                        selectedTab = 0
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCyan,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Quét Camera Ngay",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = { selectedTab = 2 },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Tạo & Ghim QR Mới",
                                        color = AccentGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // ========================= TAB 0: QUÉT MÃ QR / BARCODE TRỰC TIẾP =========================
                    0 -> {
                        if (hasCameraPermission && isLiveScannerActive) {
                            LiveCameraQrScanner(
                                onCodeScanned = { text, format, snapshot ->
                                    onQrDecoded(text, format, snapshot)
                                },
                                onPickFromGallery = {
                                    galleryPickerLauncher.launch("image/*")
                                }
                            )
                        } else if (!hasCameraPermission) {
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
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "Cấp quyền Camera để quét QR tự động",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Chỉ cần bật Camera và hướng về phía mã QR / Barcode là tự động nhận diện ngay lập tức, không cần bấm chụp hay xác nhận.",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentCyan,
                                                contentColor = Color.Black
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Mở Camera Quét", fontWeight = FontWeight.Bold, maxLines = 1)
                                        }

                                        OutlinedButton(
                                            onClick = { galleryPickerLauncher.launch("image/*") },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.Image, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Chọn từ Thư viện", color = AccentCyan, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        if (scannedText.isNotBlank()) {
                            val isScannedPinned = pinnedQrs.any { it.rawContent == scannedText }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = AccentGreen.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AccentGreen.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "✅ Kết quả giải mã ($scannedFormat)",
                                            color = AccentGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        )
                                        if (scannedText.startsWith("000201")) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = AccentCyan.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "Chuẩn EMVCo VietQR",
                                                    color = AccentCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = scannedText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.5.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                OcrAndClipboardHelper.copyToClipboardAndSave(
                                                    context,
                                                    scannedText,
                                                    "Mã QR"
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentGreen,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Sao chép", fontWeight = FontWeight.Bold, maxLines = 1)
                                        }

                                        if (scannedText.startsWith("http://", true) || scannedText.startsWith("https://", true)) {
                                            OutlinedButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(scannedText)).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                        )
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Mở Link", color = AccentCyan, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                if (isScannedPinned) {
                                                    pinnedQrs = VietQrHelper.unpinQrByContent(context, scannedText)
                                                    Toast.makeText(context, "Đã bỏ ghim mã QR", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val newPinned = PinnedQrItem(
                                                        type = if (scannedFormat.contains("128") || scannedFormat.contains("EAN")) "BARCODE" else "LINK",
                                                        title = "QR Đã Quét ($scannedFormat)",
                                                        subtitle = scannedText.take(60),
                                                        rawContent = scannedText
                                                    )
                                                    pinnedQrs = VietQrHelper.pinQrItem(context, newPinned)
                                                    selectedPinnedId = newPinned.id
                                                    Toast.makeText(
                                                        context,
                                                        "📌 Đã ghim mã QR! Lần sau mở vào chức năng sẽ thấy ngay",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PushPin,
                                                contentDescription = null,
                                                tint = if (isScannedPinned) AccentAmber else AccentCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isScannedPinned) "Bỏ ghim" else "Ghim QR",
                                                color = if (isScannedPinned) AccentAmber else AccentCyan,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Nút Quét tiếp mã khác (mở lại Live Camera ngay lập tức)
                                    Button(
                                        onClick = {
                                            scannedText = ""
                                            scannedFormat = ""
                                            isLiveScannerActive = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentCyan,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Quét tiếp mã QR khác (Camera trực tiếp)", fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    // ========================= TAB 1: TẠO MÃ VIETQR NGÂN HÀNG =========================
                    1 -> {
                        val selectedBank = remember(selectedBankBin) {
                            VietQrHelper.findBankByBin(selectedBankBin)
                        }
                        val cleanAmt = transferAmount.replace(Regex("[^0-9]"), "").toLongOrNull()
                        val vietQrPayload = remember(selectedBankBin, accountNumber, cleanAmt, transferMemo) {
                            if (accountNumber.trim().length >= 4) {
                                VietQrHelper.buildVietQrPayload(
                                    bankBin = selectedBankBin,
                                    accountNumber = accountNumber,
                                    amount = cleanAmt,
                                    memo = transferMemo
                                )
                            } else ""
                        }
                        val qrPreviewBitmap = remember(vietQrPayload) {
                            if (vietQrPayload.isNotBlank()) {
                                VietQrHelper.generateQrBitmap(vietQrPayload, sizePx = 560)
                            } else null
                        }
                        val isVietQrPinned = remember(pinnedQrs, vietQrPayload) {
                            vietQrPayload.isNotBlank() && pinnedQrs.any { it.rawContent == vietQrPayload }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "1. Chọn Ngân hàng thụ hưởng (NAPAS 247):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = AccentCyan
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(VietQrHelper.SUPPORTED_BANKS) { bank ->
                                        val isSelected = bank.bin == selectedBankBin
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedBankBin = bank.bin },
                                            label = {
                                                Text(
                                                    text = bank.shortName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(bank.brandColorHex),
                                                selectedLabelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = accountNumber,
                                    onValueChange = { accountNumber = it.filter { c -> c.isLetterOrDigit() } },
                                    label = { Text("Số tài khoản (${selectedBank.shortName}) *") },
                                    placeholder = { Text("Nhập số tài khoản nhận tiền...") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = accountName,
                                    onValueChange = { accountName = it },
                                    label = { Text("Tên chủ tài khoản (Không dấu)") },
                                    placeholder = { Text("VD: NGUYEN VAN A") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = transferAmount,
                                        onValueChange = { transferAmount = it.filter { c -> c.isDigit() } },
                                        label = { Text("Số tiền (VNĐ)") },
                                        placeholder = { Text("Để trống = tùy nhập") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = transferMemo,
                                        onValueChange = { transferMemo = it },
                                        label = { Text("Nội dung CK") },
                                        placeholder = { Text("VD: Tien an trua") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📌 Ghim làm Thẻ VietQR Cá Nhân",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Mở vào chức năng QR là thấy luôn mã này, đồng thời hiện ở màn hình chính & Chia tiền nhóm",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = pinToHome,
                                        onCheckedChange = { pinToHome = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = AccentGreen
                                        )
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (accountNumber.trim().length < 4) {
                                            Toast.makeText(context, "Vui lòng nhập số tài khoản hợp lệ!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val cleanName = VietQrHelper.stripVietnameseAccents(accountName).uppercase(Locale.getDefault())
                                        val newProfile = VietQrProfile(
                                            bankBin = selectedBank.bin,
                                            bankShortName = selectedBank.shortName,
                                            accountNumber = accountNumber.trim(),
                                            accountName = cleanName,
                                            defaultAmount = transferAmount.trim(),
                                            defaultMemo = transferMemo.trim(),
                                            pinToHome = pinToHome
                                        )
                                        VietQrHelper.saveMyVietQrProfile(context, newProfile)
                                        myVietQr = newProfile

                                        val newPinned = PinnedQrItem(
                                            type = "VIETQR",
                                            title = "${selectedBank.shortName} - ${accountNumber.trim()}",
                                            subtitle = cleanName.ifBlank { "Chuyển khoản nhanh NAPAS 247" },
                                            rawContent = vietQrPayload,
                                            bankBin = selectedBank.bin,
                                            accountNumber = accountNumber.trim(),
                                            accountName = cleanName,
                                            amount = transferAmount.trim(),
                                            memo = transferMemo.trim()
                                        )
                                        pinnedQrs = VietQrHelper.pinQrItem(context, newPinned)
                                        selectedPinnedId = newPinned.id

                                        historyList = VietQrHelper.addHistoryItem(
                                            context,
                                            QrHistoryItem(
                                                type = "VIETQR",
                                                title = "VietQR ${selectedBank.shortName} - ${accountNumber.trim()}",
                                                subtitle = cleanName.ifBlank { "Chuyển khoản nhanh NAPAS 247" },
                                                rawContent = vietQrPayload
                                            )
                                        )
                                        Toast.makeText(
                                            context,
                                            "📌 Đã lưu & ghim mã VietQR! Lần sau mở vào chức năng sẽ thấy ngay mã này",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreen,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "📌 Ghim QR Này (Mở vào là thấy ngay)",
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Khung xem trước mã VietQR trực tiếp
                        if (qrPreviewBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(2.dp, Color(selectedBank.brandColorHex), RoundedCornerShape(24.dp))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(selectedBank.brandColorHex),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = selectedBank.fullName.uppercase(Locale.getDefault()),
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "VIETQR • NAPAS 247",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    Image(
                                        bitmap = qrPreviewBitmap.asImageBitmap(),
                                        contentDescription = "VietQR",
                                        modifier = Modifier.size(230.dp)
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    if (accountName.isNotBlank()) {
                                        Text(
                                            text = VietQrHelper.stripVietnameseAccents(accountName).uppercase(Locale.getDefault()),
                                            color = Color(0xFF0F172A),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Text(
                                        text = "STK: $accountNumber",
                                        color = Color(selectedBank.brandColorHex),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 16.sp
                                    )
                                    if (cleanAmt != null && cleanAmt > 0L) {
                                        val formatted = NumberFormat.getNumberInstance(Locale("vi", "VN")).format(cleanAmt) + " đ"
                                        Text(
                                            text = "Số tiền: $formatted",
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    if (transferMemo.isNotBlank()) {
                                        Text(
                                            text = "Nội dung: $transferMemo",
                                            color = Color(0xFF475569),
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    // Nút Ghim nổi bật ngay dưới mã VietQR vừa tạo
                                    Button(
                                        onClick = {
                                            if (isVietQrPinned) {
                                                selectedTab = -1
                                            } else {
                                                val cleanName = VietQrHelper.stripVietnameseAccents(accountName).uppercase(Locale.getDefault())
                                                val newPinned = PinnedQrItem(
                                                    type = "VIETQR",
                                                    title = "${selectedBank.shortName} - ${accountNumber.trim()}",
                                                    subtitle = cleanName.ifBlank { "Chuyển khoản nhanh NAPAS 247" },
                                                    rawContent = vietQrPayload,
                                                    bankBin = selectedBank.bin,
                                                    accountNumber = accountNumber.trim(),
                                                    accountName = cleanName,
                                                    amount = transferAmount.trim(),
                                                    memo = transferMemo.trim()
                                                )
                                                pinnedQrs = VietQrHelper.pinQrItem(context, newPinned)
                                                selectedPinnedId = newPinned.id
                                                Toast.makeText(
                                                    context,
                                                    "📌 Đã ghim mã VietQR! Lần sau mở vào chức năng sẽ thấy luôn mã này",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                selectedTab = -1
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF16A34A),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isVietQrPinned) Icons.Rounded.CheckCircle else Icons.Rounded.PushPin,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isVietQrPinned) "✅ Đã ghim (Bấm để xem màn hình Ghim)" else "📌 Ghim QR này (Mở vào là thấy luôn)",
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val cardBmp = VietQrHelper.renderVietQrCardBitmap(
                                                    bank = selectedBank,
                                                    accountNumber = accountNumber.trim(),
                                                    accountName = accountName.trim(),
                                                    amount = cleanAmt,
                                                    memo = transferMemo.trim()
                                                )
                                                if (cardBmp != null) {
                                                    VietQrHelper.saveBitmapToGallery(context, cardBmp, "VietQR_${selectedBank.shortName}")
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(selectedBank.brandColorHex),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Lưu ảnh", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val cardBmp = VietQrHelper.renderVietQrCardBitmap(
                                                    bank = selectedBank,
                                                    accountNumber = accountNumber.trim(),
                                                    accountName = accountName.trim(),
                                                    amount = cleanAmt,
                                                    memo = transferMemo.trim()
                                                )
                                                if (cardBmp != null) {
                                                    VietQrHelper.shareBitmap(
                                                        context,
                                                        cardBmp,
                                                        "Mã VietQR ${selectedBank.shortName} - $accountNumber"
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Share, contentDescription = null, tint = Color(selectedBank.brandColorHex), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Chia sẻ", color = Color(selectedBank.brandColorHex), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ========================= TAB 2: TẠO MÃ QR LINK / WI-FI / BARCODE =========================
                    2 -> {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "LINK" to "🔗 Link / Văn bản",
                                        "WIFI" to "📶 Chia sẻ Wi-Fi",
                                        "BARCODE" to "🏷️ Mã vạch 128"
                                    ).forEach { (modeKey, modeLabel) ->
                                        FilterChip(
                                            selected = createMode == modeKey,
                                            onClick = { createMode = modeKey },
                                            label = { Text(modeLabel, fontSize = 12.sp, maxLines = 1) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = customQrTitle,
                                    onValueChange = { customQrTitle = it },
                                    label = { Text("Tên nhãn khi Ghim (Tùy chọn, VD: QR Zalo, Thẻ thành viên...)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (createMode == "WIFI") {
                                    OutlinedTextField(
                                        value = wifiSsid,
                                        onValueChange = { wifiSsid = it },
                                        label = { Text("Tên mạng Wi-Fi (SSID)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = wifiPassword,
                                        onValueChange = { wifiPassword = it },
                                        label = { Text("Mật khẩu Wi-Fi") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = inputLinkOrText,
                                        onValueChange = { inputLinkOrText = it },
                                        label = {
                                            Text(
                                                if (createMode == "BARCODE") "Nhập mã số / chữ không dấu (Code-128)"
                                                else "Nhập đường link URL hoặc văn bản bất kỳ"
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (createMode != "BARCODE") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("Màu QR:", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        listOf(
                                            0xFF000000L to "Đen",
                                            0xFF1E3A8AL to "Xanh",
                                            0xFF581C87L to "Tím",
                                            0xFF991B1BL to "Đỏ"
                                        ).forEach { (hex, _) ->
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(hex))
                                                    .border(
                                                        width = if (qrFgColorHex == hex) 2.5.dp else 1.dp,
                                                        color = if (qrFgColorHex == hex) AccentCyan else Color.White.copy(alpha = 0.4f),
                                                        shape = CircleShape
                                                    )
                                                    .clickable { qrFgColorHex = hex }
                                            ) {}
                                        }
                                    }
                                }
                            }
                        }

                        val rawPayload = remember(createMode, inputLinkOrText, wifiSsid, wifiPassword) {
                            when (createMode) {
                                "WIFI" -> if (wifiSsid.isNotBlank()) "WIFI:T:WPA;S:${wifiSsid.trim()};P:${wifiPassword.trim()};;" else ""
                                "BARCODE" -> VietQrHelper.stripVietnameseAccents(inputLinkOrText.trim())
                                else -> inputLinkOrText.trim()
                            }
                        }

                        val generatedBmp = remember(rawPayload, createMode, qrFgColorHex) {
                            if (rawPayload.isBlank()) null
                            else if (createMode == "BARCODE") {
                                VietQrHelper.generateBarcodeBitmap(rawPayload)
                            } else {
                                VietQrHelper.generateQrBitmap(
                                    content = rawPayload,
                                    sizePx = 640,
                                    foregroundColor = qrFgColorHex.toInt()
                                )
                            }
                        }

                        val isCustomPinned = remember(pinnedQrs, rawPayload) {
                            rawPayload.isNotBlank() && pinnedQrs.any { it.rawContent == rawPayload }
                        }

                        if (generatedBmp != null) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (customQrTitle.isNotBlank()) {
                                        Text(
                                            text = customQrTitle,
                                            color = Color(0xFF0F172A),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Image(
                                        bitmap = generatedBmp.asImageBitmap(),
                                        contentDescription = "Generated Code",
                                        modifier = if (createMode == "BARCODE") {
                                            Modifier
                                                .fillMaxWidth()
                                                .height(130.dp)
                                        } else {
                                            Modifier.size(230.dp)
                                        }
                                    )
                                    Text(
                                        text = rawPayload.take(80),
                                        color = Color.Black,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Nút Ghim QR vừa tạo để lần sau mở vào thấy luôn
                                    Button(
                                        onClick = {
                                            if (isCustomPinned) {
                                                selectedTab = -1
                                            } else {
                                                val resolvedTitle = customQrTitle.trim().ifBlank {
                                                    when (createMode) {
                                                        "WIFI" -> "Wi-Fi: ${wifiSsid.trim()}"
                                                        "BARCODE" -> "Barcode: ${rawPayload.take(24)}"
                                                        else -> "QR: ${rawPayload.take(28)}"
                                                    }
                                                }
                                                val newPinned = PinnedQrItem(
                                                    type = createMode,
                                                    title = resolvedTitle,
                                                    subtitle = rawPayload.take(80),
                                                    rawContent = rawPayload,
                                                    fgColorHex = qrFgColorHex
                                                )
                                                pinnedQrs = VietQrHelper.pinQrItem(context, newPinned)
                                                selectedPinnedId = newPinned.id
                                                historyList = VietQrHelper.addHistoryItem(
                                                    context,
                                                    QrHistoryItem(
                                                        type = createMode,
                                                        title = resolvedTitle,
                                                        subtitle = rawPayload.take(60),
                                                        rawContent = rawPayload
                                                    )
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "📌 Đã ghim mã QR! Lần sau mở vào chức năng sẽ thấy luôn mã này",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                selectedTab = -1
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF16A34A),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCustomPinned) Icons.Rounded.CheckCircle else Icons.Rounded.PushPin,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isCustomPinned) "✅ Đã ghim (Bấm để xem màn hình Ghim)" else "📌 Ghim QR này (Mở vào là thấy luôn)",
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                VietQrHelper.saveBitmapToGallery(context, generatedBmp, "ToolsHub_Code")
                                                historyList = VietQrHelper.addHistoryItem(
                                                    context,
                                                    QrHistoryItem(
                                                        type = createMode,
                                                        title = customQrTitle.trim().ifBlank { "Tạo mã $createMode" },
                                                        subtitle = rawPayload.take(60),
                                                        rawContent = rawPayload
                                                    )
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Lưu ảnh", color = Color.White, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                VietQrHelper.shareBitmap(context, generatedBmp, rawPayload)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Share, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Chia sẻ", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ========================= TAB 3: LỊCH SỬ MÃ QR =========================
                    3 -> {
                        if (historyList.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text("Chưa có lịch sử quét hoặc tạo mã QR nào.")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Danh sách mã đã lưu (${historyList.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                                TextButton(
                                    onClick = {
                                        VietQrHelper.clearHistory(context)
                                        historyList = emptyList()
                                    }
                                ) {
                                    Text("Xóa tất cả", color = AccentRed, fontSize = 12.sp)
                                }
                            }

                            val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                            historyList.forEach { item ->
                                val isItemPinned = pinnedQrs.any { it.rawContent == item.rawContent }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scannedText = item.rawContent
                                            scannedFormat = item.type
                                            isLiveScannerActive = false
                                            selectedTab = 0
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = item.subtitle,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = fmt.format(Date(item.timestamp)),
                                                fontSize = 10.5.sp,
                                                color = AccentCyan
                                            )
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    if (isItemPinned) {
                                                        pinnedQrs = VietQrHelper.unpinQrByContent(context, item.rawContent)
                                                        Toast.makeText(context, "Đã bỏ ghim mã QR", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val newPinned = PinnedQrItem(
                                                            type = if (item.type == "BARCODE") "BARCODE" else if (item.rawContent.startsWith("000201")) "VIETQR" else "LINK",
                                                            title = item.title,
                                                            subtitle = item.subtitle,
                                                            rawContent = item.rawContent
                                                        )
                                                        pinnedQrs = VietQrHelper.pinQrItem(context, newPinned)
                                                        selectedPinnedId = newPinned.id
                                                        Toast.makeText(
                                                            context,
                                                            "📌 Đã ghim! Mở vào chức năng sẽ thấy luôn mã này",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        selectedTab = -1
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PushPin,
                                                    contentDescription = "Ghim",
                                                    tint = if (isItemPinned) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    OcrAndClipboardHelper.copyToClipboardAndSave(
                                                        context,
                                                        item.rawContent,
                                                        "Lịch sử QR"
                                                    )
                                                }
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = AccentCyan)
                                            }
                                            IconButton(
                                                onClick = {
                                                    historyList = VietQrHelper.deleteHistoryItem(context, item.id)
                                                }
                                            ) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Xóa", tint = AccentRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thẻ hiển thị Mã QR Đã Ghim kích thước lớn (240dp) ngay khi vừa mở chức năng QR & VietQR Studio.
 */
@Composable
private fun PinnedQrShowcaseCard(
    item: PinnedQrItem,
    isPrimary: Boolean,
    showMakePrimaryButton: Boolean,
    onSetPrimary: () -> Unit,
    onUnpin: () -> Unit,
    onEditVietQr: () -> Unit
) {
    val context = LocalContext.current
    val isVietQr = item.type == "VIETQR" && item.bankBin.isNotBlank()
    val bank = remember(item.bankBin) {
        if (isVietQr) VietQrHelper.findBankByBin(item.bankBin) else null
    }
    val brandColor = if (bank != null) Color(bank.brandColorHex) else Color(0xFF0F172A)

    val qrBitmap = remember(item.rawContent, item.type, item.fgColorHex) {
        if (item.type == "BARCODE") {
            VietQrHelper.generateBarcodeBitmap(item.rawContent)
        } else {
            VietQrHelper.generateQrBitmap(
                content = item.rawContent,
                sizePx = 680,
                foregroundColor = item.fgColorHex.toInt()
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(2.5.dp, if (bank != null) brandColor else AccentGreen, RoundedCornerShape(24.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(18.dp)
        ) {
            // Header
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = brandColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = if (bank != null) bank.fullName.uppercase(Locale.getDefault()) else item.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (bank != null) "📌 MÃ VIETQR ĐÃ GHIM • ĐƯA ĐỂ QUÉT NGAY"
                        else "📌 MÃ QR ĐÃ GHIM • ĐƯA ĐỂ QUÉT NGAY",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Pinned QR",
                    modifier = if (item.type == "BARCODE") {
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    } else {
                        Modifier.size(245.dp)
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            if (isVietQr) {
                if (item.accountName.isNotBlank()) {
                    Text(
                        text = item.accountName,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
                if (item.accountNumber.isNotBlank()) {
                    Text(
                        text = "STK: ${item.accountNumber}",
                        color = brandColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                }
                val cleanAmt = item.amount.replace(Regex("[^0-9]"), "").toLongOrNull()
                if (cleanAmt != null && cleanAmt > 0L) {
                    val formatted = NumberFormat.getNumberInstance(Locale("vi", "VN")).format(cleanAmt) + " đ"
                    Text(
                        text = "Số tiền: $formatted",
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                if (item.memo.isNotBlank()) {
                    Text(
                        text = "Nội dung: ${item.memo}",
                        color = Color(0xFF475569),
                        fontSize = 13.sp
                    )
                }
            } else {
                Text(
                    text = item.subtitle.ifBlank { item.rawContent }.take(100),
                    color = Color(0xFF334155),
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val exportBmp = if (isVietQr && bank != null) {
                            VietQrHelper.renderVietQrCardBitmap(
                                bank = bank,
                                accountNumber = item.accountNumber,
                                accountName = item.accountName,
                                amount = item.amount.replace(Regex("[^0-9]"), "").toLongOrNull(),
                                memo = item.memo
                            )
                        } else qrBitmap
                        if (exportBmp != null) {
                            VietQrHelper.shareBitmap(context, exportBmp, item.title)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Chia sẻ", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        val exportBmp = if (isVietQr && bank != null) {
                            VietQrHelper.renderVietQrCardBitmap(
                                bank = bank,
                                accountNumber = item.accountNumber,
                                accountName = item.accountName,
                                amount = item.amount.replace(Regex("[^0-9]"), "").toLongOrNull(),
                                memo = item.memo
                            )
                        } else qrBitmap
                        if (exportBmp != null) {
                            VietQrHelper.saveBitmapToGallery(context, exportBmp, "PinnedQR")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = brandColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Lưu ảnh", color = brandColor, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showMakePrimaryButton && !isPrimary) {
                    TextButton(onClick = onSetPrimary) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hiện đầu tiên", color = Color(0xFFB45309), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = onEditVietQr) {
                        Text("Chỉnh sửa mã này", color = Color(0xFF0284C7), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                TextButton(onClick = onUnpin) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bỏ ghim", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MyVietQrPreviewCard(
    profile: VietQrProfile,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bank = remember(profile.bankBin) { VietQrHelper.findBankByBin(profile.bankBin) }
    val amt = profile.defaultAmount.replace(Regex("[^0-9]"), "").toLongOrNull()
    val payload = remember(profile) {
        VietQrHelper.buildVietQrPayload(profile.bankBin, profile.accountNumber, amt, profile.defaultMemo)
    }
    val qrBmp = remember(payload) { VietQrHelper.generateQrBitmap(payload, sizePx = 420) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AccentGreen.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (qrBmp != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.size(92.dp)
                ) {
                    Image(
                        bitmap = qrBmp.asImageBitmap(),
                        contentDescription = "My VietQR",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalance,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "VietQR Cá Nhân (${bank.shortName})",
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
                Text(
                    text = "STK: ${profile.accountNumber}",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                if (profile.accountName.isNotBlank()) {
                    Text(
                        text = profile.accountName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val cardBmp = VietQrHelper.renderVietQrCardBitmap(
                                bank = bank,
                                accountNumber = profile.accountNumber,
                                accountName = profile.accountName,
                                amount = amt,
                                memo = profile.defaultMemo
                            )
                            if (cardBmp != null) {
                                VietQrHelper.shareBitmap(context, cardBmp, "VietQR ${bank.shortName} - ${profile.accountNumber}")
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Chia sẻ QR", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onEditClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Mở lớn / Sửa", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
