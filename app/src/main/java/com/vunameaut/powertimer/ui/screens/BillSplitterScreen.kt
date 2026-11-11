package com.vunameaut.powertimer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PersonAdd
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vunameaut.powertimer.qr.BankInfo
import com.vunameaut.powertimer.qr.VietQrHelper
import com.vunameaut.powertimer.qr.VietQrProfile
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Một khoản chi trong chuyến đi / buổi tụ tập nhiều tăng:
 * - [title]: Tên khoản chi (VD: "Tăng 1: Lẩu nướng", "Tăng 2: Trà sữa", "Tiền Taxi")
 * - [amount]: Số tiền của khoản đó
 * - [paidBy]: Tên người đã ứng tiền trả trước khoản này
 * - [participants]: Danh sách những người tham gia chia khoản này
 */
data class ExpenseEntry(
    val id: Long = System.nanoTime(),
    val title: String,
    val amount: Long,
    val paidBy: String,
    val participants: Set<String>
)

/**
 * Tổng hợp tài chính của từng thành viên:
 * - [paidAmount]: Tổng số tiền đã bỏ ra trả trước
 * - [shareAmount]: Tổng phần tiền phải đóng theo các khoản đã tham gia
 * - [netBalance]: `paidAmount - shareAmount` (>0 là được nhận lại, <0 là còn nợ cần trả thêm)
 */
data class MemberBalanceSummary(
    val name: String,
    val paidAmount: Long,
    val shareAmount: Long,
    val netBalance: Long
)

/**
 * Lệnh chuyển khoản tối ưu sau khi khấu trừ công nợ chéo:
 * [fromMember] cần chuyển [amount] cho [toMember].
 */
data class DebtSettlementTransfer(
    val fromMember: String,
    val toMember: String,
    val amount: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillSplitterTool(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    val savedVietQr = remember { VietQrHelper.loadMyVietQrProfile(context) }

    var groupTitle by remember { mutableStateOf("Tổng kết Đi chơi / Ăn uống") }

    val members = remember {
        mutableStateListOf("Nam", "Minh", "Lan", "Huy")
    }
    var newMemberInput by remember { mutableStateOf("") }

    // Danh sách các khoản chi (mặc định có sẵn 2 khoản mẫu để người dùng dễ hình dung, có thể xóa/thêm tùy ý)
    val expenses = remember {
        mutableStateListOf(
            ExpenseEntry(
                title = "Tăng 1: Ăn lẩu nướng",
                amount = 800_000L,
                paidBy = "Nam",
                participants = setOf("Nam", "Minh", "Lan", "Huy")
            ),
            ExpenseEntry(
                title = "Tăng 2: Cafe / Trà sữa",
                amount = 200_000L,
                paidBy = "Minh",
                participants = setOf("Nam", "Minh", "Lan", "Huy")
            )
        )
    }

    // Form thêm khoản chi mới
    var newExpenseTitle by remember { mutableStateOf("") }
    var newExpenseAmount by remember { mutableStateOf("") }
    var selectedPayer by remember { mutableStateOf("Nam") }
    var selectedParticipants by remember { mutableStateOf(members.toSet()) }

    // Tài khoản VietQR nhận tiền đính kèm góc ảnh
    var selectedBankBin by remember { mutableStateOf(savedVietQr.bankBin.ifBlank { "970422" }) }
    var accountNumber by remember { mutableStateOf(savedVietQr.accountNumber) }
    var accountName by remember { mutableStateOf(savedVietQr.accountName) }

    var isScanningOcr by remember { mutableStateOf(false) }

    fun handleOcrReceiptBitmap(bmp: Bitmap) {
        isScanningOcr = true
        scope.launch {
            val res = OcrAndClipboardHelper.recognizeTextFromBitmap(bmp)
            isScanningOcr = false
            res.onSuccess { text ->
                val (parsedItems, parsedTotal) = OcrAndClipboardHelper.parseReceiptItemsFromOcr(text)
                val payer = selectedPayer.takeIf { it in members } ?: members.firstOrNull().orEmpty()
                val allSet = members.toSet()
                if (parsedTotal != null && parsedTotal > 0L) {
                    newExpenseAmount = parsedTotal.toString()
                    if (newExpenseTitle.isBlank()) {
                        newExpenseTitle = "Hóa đơn quét OCR (${parsedItems.size} món)"
                    }
                    expenses.add(
                        ExpenseEntry(
                            title = newExpenseTitle.ifBlank { "Hóa đơn OCR" },
                            amount = parsedTotal,
                            paidBy = payer,
                            participants = allSet
                        )
                    )
                    newExpenseTitle = ""
                    newExpenseAmount = ""
                    Toast.makeText(
                        context,
                        "Đã thêm khoản chi ${formatCurrencyVnd(parsedTotal)} (Người trả: $payer)!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Không tìm thấy tổng tiền tự động trên ảnh, hãy nhập số tiền vào ô bên dưới!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure {
                Toast.makeText(context, "Lỗi đọc ảnh: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
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
            handleOcrReceiptBitmap(softBmp)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = OcrAndClipboardHelper.loadBitmapFromUri(context, uri)
            if (bmp != null) handleOcrReceiptBitmap(bmp)
        }
    }

    // Tính toán bảng công nợ & khấu trừ nợ chéo
    val (memberBalances, transfers, grandTotal) = remember(members.toList(), expenses.toList()) {
        computeGroupSettlement(members.toList(), expenses.toList())
    }

    val selectedBank = remember(selectedBankBin) { VietQrHelper.findBankByBin(selectedBankBin) }

    val summaryPreviewBitmap = remember(
        groupTitle,
        expenses.toList(),
        memberBalances,
        transfers,
        grandTotal,
        selectedBank,
        accountNumber,
        accountName
    ) {
        renderMultiExpenseSummaryBitmap(
            title = groupTitle,
            expenses = expenses.toList(),
            balances = memberBalances,
            transfers = transfers,
            grandTotal = grandTotal,
            bank = selectedBank,
            accountNumber = accountNumber,
            accountName = accountName
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chia Tiền Nhóm Đa Chặng",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Ai trả bao nhiêu • Khấu trừ nợ chéo • Xuất ảnh kèm VietQR",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Tên buổi đi chơi & Danh sách thành viên
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it },
                        label = { Text("Tên chuyến đi / buổi tụ tập") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "👥 Thành viên trong nhóm (${members.size} người):",
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontSize = 13.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newMemberInput,
                            onValueChange = { newMemberInput = it },
                            placeholder = { Text("Thêm tên người (VD: Tuấn, Mai...)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val name = newMemberInput.trim()
                                if (name.isNotEmpty() && members.none { it.equals(name, ignoreCase = true) }) {
                                    members.add(name)
                                    selectedParticipants = selectedParticipants + name
                                    newMemberInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Thêm", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        members.forEach { mName ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(50))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(mName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                    if (members.size > 2) {
                                        IconButton(
                                            onClick = {
                                                members.remove(mName)
                                                selectedParticipants = selectedParticipants - mName
                                                if (selectedPayer == mName) {
                                                    selectedPayer = members.firstOrNull().orEmpty()
                                                }
                                            },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Xóa",
                                                tint = AccentRed,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Thêm Khoản Chi Mới (Tăng 1, Tăng 2, Cafe, Taxi... + Quét OCR)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AccentPurple.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentPurple.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "➕ Thêm khoản chi mới (Ai trả cái gì, bao nhiêu tiền):",
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber,
                        fontSize = 14.sp
                    )

                    OutlinedTextField(
                        value = newExpenseTitle,
                        onValueChange = { newExpenseTitle = it },
                        label = { Text("Tên khoản chi (VD: Tăng 1: Ăn lẩu, Cafe...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newExpenseAmount,
                        onValueChange = { newExpenseAmount = it.filter { c -> c.isDigit() } },
                        label = { Text("Số tiền khoản này (VNĐ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Chọn người đã ứng tiền trả trước khoản này
                    Text(
                        text = "💳 Ai đã trả trước khoản này?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(members) { mName ->
                            val isPayer = selectedPayer == mName
                            FilterChip(
                                selected = isPayer,
                                onClick = { selectedPayer = mName },
                                label = {
                                    Text(
                                        text = "$mName trả",
                                        fontWeight = if (isPayer) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGreen,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Chọn những ai tham gia chia khoản này
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🍽️ Chia cho những ai? (${selectedParticipants.intersect(members.toSet()).size}/${members.size} người)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentCyan.copy(alpha = 0.16f),
                            onClick = { selectedParticipants = members.toSet() }
                        ) {
                            Text(
                                text = "Chọn tất cả",
                                fontSize = 11.5.sp,
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        members.forEach { mName ->
                            val checked = mName in selectedParticipants
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    selectedParticipants = if (checked && selectedParticipants.size > 1) {
                                        selectedParticipants - mName
                                    } else {
                                        selectedParticipants + mName
                                    }
                                },
                                label = { Text(mName, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val amt = newExpenseAmount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                            if (amt <= 0L) {
                                Toast.makeText(context, "Vui lòng nhập số tiền hợp lệ!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val payer = selectedPayer.takeIf { it in members } ?: members.firstOrNull().orEmpty()
                            val validParts = selectedParticipants.intersect(members.toSet()).ifEmpty { members.toSet() }
                            expenses.add(
                                ExpenseEntry(
                                    title = newExpenseTitle.trim().ifBlank { "Khoản chi #${expenses.size + 1}" },
                                    amount = amt,
                                    paidBy = payer,
                                    participants = validParts
                                )
                            )
                            newExpenseTitle = ""
                            newExpenseAmount = ""
                            selectedParticipants = members.toSet()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Thêm khoản chi này vào danh sách",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) cameraLauncher.launch(null)
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Chụp Hóa Đơn",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Chọn Ảnh Bill",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isScanningOcr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Đang đọc tổng tiền từ ảnh hóa đơn...", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 3. Danh sách các khoản chi đã nhập
            if (expenses.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🧾 Các khoản đã chi (${expenses.size} khoản):",
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                fontSize = 13.5.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = "Tổng: ${formatCurrencyVnd(grandTotal)}",
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentGreen,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        expenses.forEachIndexed { idx, exp ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${exp.title}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                        Text(
                                            text = "💳 ${exp.paidBy} đã trả • Chia cho: ${exp.participants.joinToString(", ")}",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatCurrencyVnd(exp.amount),
                                            color = AccentAmber,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        IconButton(
                                            onClick = { expenses.removeAt(idx) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Xóa khoản chi",
                                                tint = AccentRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Bảng Tổng Hợp Công Nợ: Ai đã trả bao nhiêu • Còn nợ hay Được nhận lại
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📊 Bảng Công Nợ Từng Người (Đã trả vs Phải đóng):",
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCyan,
                        fontSize = 14.sp
                    )

                    memberBalances.forEach { b ->
                        val isPositive = b.netBalance > 0L
                        val isNegative = b.netBalance < 0L
                        val statusColor = when {
                            isPositive -> AccentGreen
                            isNegative -> AccentRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val statusText = when {
                            isPositive -> "Được nhận +${formatCurrencyVnd(b.netBalance)}"
                            isNegative -> "Còn nợ ${formatCurrencyVnd(b.netBalance)}"
                            else -> "Đã cân bằng (0 đ)"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = b.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.5.sp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = statusColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Đã chi: ${formatCurrencyVnd(b.paidAmount)}  •  Phần đóng: ${formatCurrencyVnd(b.shareAmount)}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (transfers.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "💸 Chốt sổ: Ai cần chuyển tiền cho Ai?",
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentGreen,
                            fontSize = 14.sp
                        )
                        transfers.forEach { t ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentGreen.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AccentGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = t.fromMember,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = AccentAmber,
                                            fontSize = 14.sp
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "chuyển cho ${t.toMember}",
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGreen,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                    Text(
                                        text = formatCurrencyVnd(t.amount),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Tài khoản VietQR đính kèm góc ảnh tổng kết
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🏦 Mã VietQR đính kèm ảnh tổng kết:",
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        fontSize = 13.5.sp
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VietQrHelper.SUPPORTED_BANKS) { bank ->
                            val selected = bank.bin == selectedBankBin
                            FilterChip(
                                selected = selected,
                                onClick = { selectedBankBin = bank.bin },
                                label = { Text(bank.shortName, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(bank.brandColorHex),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = accountNumber,
                            onValueChange = { accountNumber = it.filter { c -> c.isLetterOrDigit() } },
                            label = { Text("STK (${selectedBank.shortName})") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = { accountName = it },
                            label = { Text("Chủ TK") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. Ảnh Tổng Hợp Xuất Ra Nhóm Chat
            if (summaryPreviewBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, AccentGreen.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📸 Ảnh Tổng Kết Công Nợ + Mã VietQR:",
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentGreen,
                            fontSize = 15.sp
                        )

                        Image(
                            bitmap = summaryPreviewBitmap.asImageBitmap(),
                            contentDescription = "Ảnh tổng kết chia tiền",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (accountNumber.trim().length >= 4) {
                                        VietQrHelper.saveMyVietQrProfile(
                                            context,
                                            VietQrProfile(
                                                bankBin = selectedBank.bin,
                                                bankShortName = selectedBank.shortName,
                                                accountNumber = accountNumber.trim(),
                                                accountName = VietQrHelper.stripVietnameseAccents(accountName).uppercase(Locale.getDefault()),
                                                pinToHome = true
                                            )
                                        )
                                    }
                                    VietQrHelper.shareBitmap(
                                        context,
                                        summaryPreviewBitmap,
                                        "Bảng chia tiền: $groupTitle"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Gửi vào Nhóm Chat",
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    VietQrHelper.saveBitmapToGallery(
                                        context,
                                        summaryPreviewBitmap,
                                        "BillSplit"
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Lưu ảnh",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatCurrencyVnd(amount: Long): String {
    return NumberFormat.getNumberInstance(Locale("vi", "VN")).format(amount) + " đ"
}

/**
 * Tính toán công nợ ròng của từng thành viên và tìm các lệnh chuyển tiền tối ưu nhất
 * để tất toán toàn bộ các khoản chi trong nhóm.
 */
private fun computeGroupSettlement(
    members: List<String>,
    expenses: List<ExpenseEntry>
): Triple<List<MemberBalanceSummary>, List<DebtSettlementTransfer>, Long> {
    val paidMap = mutableMapOf<String, Long>()
    val shareMap = mutableMapOf<String, Long>()
    members.forEach {
        paidMap[it] = 0L
        shareMap[it] = 0L
    }

    var totalAll = 0L
    for (exp in expenses) {
        if (exp.amount <= 0L) continue
        totalAll += exp.amount
        val payer = exp.paidBy.takeIf { it in members } ?: members.firstOrNull() ?: continue
        paidMap[payer] = (paidMap[payer] ?: 0L) + exp.amount

        val validParticipants = exp.participants.intersect(members.toSet()).ifEmpty { members.toSet() }
        val count = validParticipants.size.coerceAtLeast(1)
        val perPerson = exp.amount / count
        val remainder = exp.amount % count
        validParticipants.forEachIndexed { idx, pName ->
            val extra = if (idx < remainder) 1L else 0L
            shareMap[pName] = (shareMap[pName] ?: 0L) + perPerson + extra
        }
    }

    val summaries = members.map { mName ->
        val paid = paidMap[mName] ?: 0L
        val share = shareMap[mName] ?: 0L
        // Làm tròn đến hàng trăm đồng
        val roundedPaid = ((paid + 50L) / 100L) * 100L
        val roundedShare = ((share + 50L) / 100L) * 100L
        MemberBalanceSummary(
            name = mName,
            paidAmount = roundedPaid,
            shareAmount = roundedShare,
            netBalance = roundedPaid - roundedShare
        )
    }

    // Khấu trừ nợ chéo giữa những người đang âm (con nợ) và những người đang dương (chủ nợ)
    val debtors = summaries
        .filter { it.netBalance < -100L }
        .map { it.name to abs(it.netBalance) }
        .toMutableList()
    val creditors = summaries
        .filter { it.netBalance > 100L }
        .map { it.name to it.netBalance }
        .toMutableList()

    val transfers = mutableListOf<DebtSettlementTransfer>()
    var dIdx = 0
    var cIdx = 0
    while (dIdx < debtors.size && cIdx < creditors.size) {
        val (dName, dOwed) = debtors[dIdx]
        val (cName, cRecv) = creditors[cIdx]
        val payAmt = min(dOwed, cRecv)
        if (payAmt >= 100L) {
            transfers.add(
                DebtSettlementTransfer(
                    fromMember = dName,
                    toMember = cName,
                    amount = ((payAmt + 50L) / 100L) * 100L
                )
            )
        }
        val remD = dOwed - payAmt
        val remC = cRecv - payAmt
        debtors[dIdx] = dName to remD
        creditors[cIdx] = cName to remC
        if (remD < 100L) dIdx++
        if (remC < 100L) cIdx++
    }

    return Triple(summaries, transfers, totalAll)
}

/**
 * Vẽ bức ảnh Tổng kết Chia Tiền Nhóm Đa Chặng gồm:
 * 1. Danh sách các khoản chi (Khoản gì • Bao nhiêu • Ai đã trả trước)
 * 2. Bảng công nợ từng thành viên (Đã chi | Phần đóng | Còn nợ / Được nhận)
 * 3. Chốt sổ thanh toán (Ai cần chuyển bao nhiêu tiền cho Ai)
 * 4. Mã VietQR ở góc dưới để quét chuyển khoản ngay!
 */
private fun renderMultiExpenseSummaryBitmap(
    title: String,
    expenses: List<ExpenseEntry>,
    balances: List<MemberBalanceSummary>,
    transfers: List<DebtSettlementTransfer>,
    grandTotal: Long,
    bank: BankInfo,
    accountNumber: String,
    accountName: String
): Bitmap? {
    val width = 1040
    val hasQr = accountNumber.trim().length >= 4
    val expSectionH = if (expenses.isNotEmpty()) 70 + expenses.size * 68 else 0
    val balSectionH = 80 + balances.size * 84
    val transSectionH = if (transfers.isNotEmpty()) 80 + transfers.size * 76 else 70
    val footerH = if (hasQr) 390 else 100
    val height = (310 + expSectionH + balSectionH + transSectionH + footerH).coerceAtLeast(860)

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0xFF090D1A.toInt(), 0xFF111C38.toInt(), 0xFF0A0F1F.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val cardRect = RectF(40f, 40f, width - 40f, height - 40f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF151F38.toInt() }
    canvas.drawRoundRect(cardRect, 40f, 40f, cardPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(cardRect, 40f, 40f, strokePaint)

    // Header
    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        textSize = 25f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val dateStr = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(Date())
    canvas.drawText("BẢNG TỔNG KẾT CHIA TIỀN NHÓM • $dateStr", 80f, 104f, badgePaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 44f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(title.ifBlank { "Tổng kết nhóm" }.take(34), 80f, 162f, titlePaint)

    val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4ADE80.toInt()
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(
        "Tổng chi tiêu: ${formatCurrencyVnd(grandTotal)} (${expenses.size} khoản • ${balances.size} người)",
        80f,
        214f,
        totalLabelPaint
    )

    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF334155.toInt()
        strokeWidth = 2f
    }
    canvas.drawLine(80f, 240f, width - 80f, 240f, dividerPaint)

    var y = 285f
    val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFACC15.toInt()
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() }
    val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 29f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = 22f
    }
    val rightValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFACC15.toInt()
        textSize = 30f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }

    // 1. Vẽ danh sách các khoản chi
    if (expenses.isNotEmpty()) {
        canvas.drawText("1. CÁC KHOẢN ĐÃ CHI TRƯỚC:", 80f, y, sectionTitlePaint)
        y += 22f
        expenses.forEachIndexed { idx, exp ->
            val rRect = RectF(80f, y, width - 80f, y + 58f)
            canvas.drawRoundRect(rRect, 14f, 14f, rowBgPaint)
            canvas.drawText(
                "${idx + 1}. ${exp.title.take(26)} (${exp.paidBy} trả)",
                104f,
                y + 39f,
                primaryTextPaint
            )
            rightValuePaint.color = 0xFFFACC15.toInt()
            canvas.drawText(formatCurrencyVnd(exp.amount), width - 104f, y + 39f, rightValuePaint)
            y += 68f
        }
        y += 24f
    }

    // 2. Vẽ bảng công nợ từng thành viên
    sectionTitlePaint.color = 0xFF38BDF8.toInt()
    canvas.drawText("2. TỔNG HỢP CÔNG NỢ TỪNG NGƯỜI:", 80f, y, sectionTitlePaint)
    y += 22f

    balances.forEach { b ->
        val rRect = RectF(80f, y, width - 80f, y + 72f)
        canvas.drawRoundRect(rRect, 16f, 16f, rowBgPaint)
        canvas.drawText(b.name, 104f, y + 34f, primaryTextPaint)
        canvas.drawText(
            "Đã trả: ${formatCurrencyVnd(b.paidAmount)}  |  Phần đóng: ${formatCurrencyVnd(b.shareAmount)}",
            104f,
            y + 60f,
            subTextPaint
        )
        val statusStr = when {
            b.netBalance > 0L -> "Nhận lại +${formatCurrencyVnd(b.netBalance)}"
            b.netBalance < 0L -> "Còn nợ ${formatCurrencyVnd(b.netBalance)}"
            else -> "Đã cân bằng"
        }
        rightValuePaint.color = when {
            b.netBalance > 0L -> 0xFF4ADE80.toInt()
            b.netBalance < 0L -> 0xFFF87171.toInt()
            else -> 0xFF94A3B8.toInt()
        }
        canvas.drawText(statusStr, width - 104f, y + 46f, rightValuePaint)
        y += 84f
    }

    y += 26f

    // 3. Vẽ danh sách Chốt sổ: Ai chuyển cho Ai bao nhiêu
    sectionTitlePaint.color = 0xFF4ADE80.toInt()
    canvas.drawText("3. CHỐT SỔ THANH TOÁN (AI CHUYỂN CHO AI):", 80f, y, sectionTitlePaint)
    y += 22f

    if (transfers.isEmpty()) {
        canvas.drawText("Mọi người đã cân bằng công nợ, không cần chuyển thêm!", 104f, y + 34f, subTextPaint)
        y += 56f
    } else {
        val transferBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF064E3B.toInt() }
        transfers.forEach { t ->
            val rRect = RectF(80f, y, width - 80f, y + 64f)
            canvas.drawRoundRect(rRect, 16f, 16f, transferBg)
            canvas.drawText(
                "👉 ${t.fromMember}  ➔  chuyển cho ${t.toMember}",
                104f,
                y + 42f,
                primaryTextPaint
            )
            rightValuePaint.color = AndroidColor.WHITE
            canvas.drawText(formatCurrencyVnd(t.amount), width - 104f, y + 42f, rightValuePaint)
            y += 76f
        }
    }

    // 4. Footer VietQR ở dưới
    if (hasQr) {
        y += 18f
        val footerRect = RectF(80f, y, width - 80f, height - 70f)
        val footerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() }
        canvas.drawRoundRect(footerRect, 28f, 28f, footerBg)

        val payload = VietQrHelper.buildVietQrPayload(
            bankBin = bank.bin,
            accountNumber = accountNumber.trim(),
            amount = null,
            memo = VietQrHelper.stripVietnameseAccents(title).take(25)
        )
        val qrBmp = VietQrHelper.generateQrBitmap(payload, sizePx = 270)

        val infoLeft = 112f
        var infoY = y + 62f

        val bankTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4ADE80.toInt()
            textSize = 27f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("TÀI KHOẢN NHẬN TIỀN (VIETQR NAPAS 247):", infoLeft, infoY, bankTitlePaint)
        infoY += 48f

        val bankNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 35f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Ngân hàng: ${bank.shortName}", infoLeft, infoY, bankNamePaint)
        infoY += 46f

        val stkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF38BDF8.toInt()
            textSize = 37f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("STK: ${accountNumber.trim()}", infoLeft, infoY, stkPaint)
        infoY += 44f

        if (accountName.isNotBlank()) {
            val ownerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE2E8F0.toInt()
                textSize = 29f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(
                "Chủ TK: ${VietQrHelper.stripVietnameseAccents(accountName).uppercase(Locale.getDefault())}",
                infoLeft,
                infoY,
                ownerPaint
            )
            infoY += 40f
        }

        canvas.drawText("Quét mã QR góc phải bằng mọi App Ngân hàng / MoMo 👉", infoLeft, infoY, subTextPaint)

        if (qrBmp != null) {
            val qrLeft = width - 80f - qrBmp.width - 28f
            val qrTop = y + ((footerRect.height() - qrBmp.height) / 2f)
            val whitePad = RectF(qrLeft - 10f, qrTop - 10f, qrLeft + qrBmp.width + 10f, qrTop + qrBmp.height + 10f)
            val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
            canvas.drawRoundRect(whitePad, 20f, 20f, whitePaint)
            canvas.drawBitmap(qrBmp, qrLeft, qrTop, null)
        }
    }

    return bmp
}
