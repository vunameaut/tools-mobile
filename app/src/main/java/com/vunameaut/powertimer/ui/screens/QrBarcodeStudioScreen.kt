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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vunameaut.powertimer.ocr.OcrAndClipboardHelper
import com.vunameaut.powertimer.qr.QrHistoryItem
import com.vunameaut.powertimer.qr.VietQrHelper
import com.vunameaut.powertimer.qr.VietQrProfile
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentPurple
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

    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var historyList by remember { mutableStateOf(VietQrHelper.loadHistory(context)) }
    var myVietQr by remember { mutableStateOf(VietQrHelper.loadMyVietQrProfile(context)) }

    // Trạng thái Tab Quét QR
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
    var inputLinkOrText by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var qrFgColorHex by remember { mutableStateOf(0xFF000000L) }

    fun handleScannedImage(bmp: Bitmap) {
        scannedBitmap = bmp
        val decoded = VietQrHelper.decodeFromBitmap(bmp)
        if (decoded != null) {
            scannedText = decoded.first
            scannedFormat = decoded.second
            val itemType = if (decoded.first.startsWith("000201")) "VIETQR" else "SCAN"
            historyList = VietQrHelper.addHistoryItem(
                context,
                QrHistoryItem(
                    type = itemType,
                    title = if (itemType == "VIETQR") "Quét mã VietQR Ngân hàng" else "Quét mã (${decoded.second})",
                    subtitle = decoded.first.take(80),
                    rawContent = decoded.first
                )
            )
            Toast.makeText(context, "Đã đọc mã thành công!", Toast.LENGTH_SHORT).show()
        } else {
            scannedText = ""
            scannedFormat = ""
            Toast.makeText(
                context,
                "Không tìm thấy mã QR / Barcode trong ảnh. Hãy thử chụp rõ nét hơn!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            val softBmp = if (bmp.config != Bitmap.Config.ARGB_8888) {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else bmp
            handleScannedImage(softBmp)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPreviewLauncher.launch(null)
        } else {
            Toast.makeText(context, "Cần quyền Camera để chụp quét mã QR!", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = OcrAndClipboardHelper.loadBitmapFromUri(context, uri)
            if (bmp != null) {
                handleScannedImage(bmp)
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
                            text = "Quét mã • Tạo VietQR chuẩn NAPAS • Lưu lịch sử",
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
                val tabs = listOf(
                    0 to "📷 Quét Mã",
                    1 to "🏦 Tạo VietQR",
                    2 to "✨ Tạo QR / Barcode",
                    3 to "🕘 Lịch sử (${historyList.size})"
                )
                tabs.forEach { (idx, label) ->
                    val selected = selectedTab == idx
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTab = idx },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan,
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
                    // ========================= TAB 0: QUÉT MÃ QR / BARCODE =========================
                    0 -> {
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
                                    text = "Quét mã QR hoặc Barcode từ Camera / Ảnh",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Hỗ trợ đọc mã QR đường link, mã chuyển khoản VietQR ngân hàng, Wi-Fi, mã vạch sản phẩm...",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val hasCam = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasCam) {
                                                cameraPreviewLauncher.launch(null)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentCyan,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Chụp Camera", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    OutlinedButton(
                                        onClick = { galleryPickerLauncher.launch("image/*") },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.Image, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Chọn từ Thư viện", color = AccentCyan, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        if (scannedText.isNotBlank()) {
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
                                            Text("Sao chép", fontWeight = FontWeight.Bold)
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
                                                Text("Mở Link", color = AccentCyan, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Nếu đã lưu thẻ VietQR cá nhân -> Hiển thị ngay bên dưới để đưa cho người khác quét nhanh
                        if (myVietQr.isValid) {
                            MyVietQrPreviewCard(
                                profile = myVietQr,
                                onEditClick = { selectedTab = 1 }
                            )
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
                                            text = "Hiển thị sẵn ở màn hình chính Tools Hub, Chia tiền nhóm & Nút bấm nhanh trên thanh trạng thái",
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
                                        val newProfile = VietQrProfile(
                                            bankBin = selectedBank.bin,
                                            bankShortName = selectedBank.shortName,
                                            accountNumber = accountNumber.trim(),
                                            accountName = VietQrHelper.stripVietnameseAccents(accountName).uppercase(Locale.getDefault()),
                                            defaultAmount = transferAmount.trim(),
                                            defaultMemo = transferMemo.trim(),
                                            pinToHome = pinToHome
                                        )
                                        VietQrHelper.saveMyVietQrProfile(context, newProfile)
                                        myVietQr = newProfile
                                        historyList = VietQrHelper.addHistoryItem(
                                            context,
                                            QrHistoryItem(
                                                type = "VIETQR",
                                                title = "VietQR ${selectedBank.shortName} - ${accountNumber.trim()}",
                                                subtitle = accountName.ifBlank { "Chuyển khoản nhanh NAPAS 247" },
                                                rawContent = vietQrPayload
                                            )
                                        )
                                        Toast.makeText(context, "Đã lưu mã VietQR cá nhân!", Toast.LENGTH_SHORT).show()
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
                                        text = "Lưu làm VietQR Cá Nhân & Lịch sử",
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
                                        ).forEach { (hex, name) ->
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
                                                        title = "Tạo mã $createMode",
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
                                            Text("Lưu ảnh & Lịch sử", color = Color.White, fontWeight = FontWeight.Bold)
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
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scannedText = item.rawContent
                                            scannedFormat = item.type
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
                        Text("Chỉnh sửa", color = AccentAmber, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

