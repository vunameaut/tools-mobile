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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
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
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

data class BillMember(
    val id: Long = System.nanoTime(),
    val name: String,
    val extraAmount: Long = 0L
)

data class BillDishItem(
    val id: Long = System.nanoTime(),
    val name: String,
    val price: Long,
    val consumerNames: Set<String>
)

data class MemberSplitResult(
    val name: String,
    val detailNote: String,
    val amount: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillSplitterTool(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    val savedVietQr = remember { VietQrHelper.loadMyVietQrProfile(context) }

    var billTitle by remember { mutableStateOf("Hóa đơn ăn uống / Tụ tập") }
    var splitByDishMode by remember { mutableStateOf(false) }
    var totalBillInput by remember { mutableStateOf("600000") }
    var vatPercentInput by remember { mutableStateOf("0") }
    var discountInput by remember { mutableStateOf("0") }

    val members = remember {
        mutableStateListOf(
            BillMember(name = "Bạn 1"),
            BillMember(name = "Bạn 2"),
            BillMember(name = "Bạn 3"),
            BillMember(name = "Bạn 4")
        )
    }
    var newMemberName by remember { mutableStateOf("") }

    val dishes = remember {
        mutableStateListOf<BillDishItem>()
    }
    var newDishName by remember { mutableStateOf("") }
    var newDishPrice by remember { mutableStateOf("") }

    // Thông tin ngân hàng nhận tiền (tự lấy từ VietQR cá nhân đã lưu)
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
                if (parsedItems.isNotEmpty()) {
                    dishes.clear()
                    val allMemberNames = members.map { it.name }.toSet()
                    parsedItems.forEach { item ->
                        dishes.add(
                            BillDishItem(
                                name = item.name,
                                price = item.price,
                                consumerNames = allMemberNames
                            )
                        )
                    }
                    splitByDishMode = true
                    if (parsedTotal != null && parsedTotal > 0L) {
                        totalBillInput = parsedTotal.toString()
                    }
                    Toast.makeText(
                        context,
                        "Đã tự động đọc ${parsedItems.size} món từ hóa đơn!",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (parsedTotal != null && parsedTotal > 0L) {
                    totalBillInput = parsedTotal.toString()
                    Toast.makeText(context, "Đã điền tổng hóa đơn: $parsedTotal đ", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "Không tách được món tự động, bạn hãy kiểm tra lại ảnh chụp hóa đơn nhé!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure {
                Toast.makeText(context, "Lỗi quét hóa đơn: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
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

    // Tính toán số tiền từng thành viên
    val vatPct = (vatPercentInput.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
    val discountAmt = discountInput.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L

    val splitResults: List<MemberSplitResult> = remember(
        splitByDishMode,
        totalBillInput,
        vatPct,
        discountAmt,
        members.toList(),
        dishes.toList()
    ) {
        if (members.isEmpty()) return@remember emptyList()
        if (!splitByDishMode || dishes.isEmpty()) {
            val rawTotal = totalBillInput.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
            val afterTaxAndDiscount = ((rawTotal * (1.0 + vatPct / 100.0)).roundToLong() - discountAmt).coerceAtLeast(0L)
            val totalExtra = members.sumOf { it.extraAmount }
            val sharedPool = (afterTaxAndDiscount - totalExtra).coerceAtLeast(0L)
            val basePerPerson = sharedPool / members.size
            // Làm tròn đến hàng trăm đồng cho đẹp
            members.map { m ->
                val rawAmt = basePerPerson + m.extraAmount
                val rounded = ((rawAmt + 50L) / 100L) * 100L
                val note = if (m.extraAmount > 0L) {
                    "Chia đều + phụ thu ${formatCurrencyVnd(m.extraAmount)}"
                } else {
                    "Chia đều (${members.size} người)"
                }
                MemberSplitResult(name = m.name, detailNote = note, amount = rounded)
            }
        } else {
            val memberSubtotals = mutableMapOf<String, Long>()
            val memberDishLabels = mutableMapOf<String, MutableList<String>>()
            members.forEach {
                memberSubtotals[it.name] = it.extraAmount
                memberDishLabels[it.name] = mutableListOf()
            }
            dishes.forEach { dish ->
                val activeConsumers = dish.consumerNames.intersect(members.map { it.name }.toSet())
                if (activeConsumers.isNotEmpty()) {
                    val share = dish.price / activeConsumers.size
                    activeConsumers.forEach { cName ->
                        memberSubtotals[cName] = (memberSubtotals[cName] ?: 0L) + share
                        memberDishLabels[cName]?.add(dish.name)
                    }
                }
            }
            val subtotalAll = memberSubtotals.values.sum().coerceAtLeast(1L)
            val totalAfterTax = ((subtotalAll * (1.0 + vatPct / 100.0)).roundToLong() - discountAmt).coerceAtLeast(0L)

            members.map { m ->
                val sub = memberSubtotals[m.name] ?: 0L
                val finalAmt = ((sub.toDouble() / subtotalAll.toDouble()) * totalAfterTax).roundToLong()
                val rounded = ((finalAmt + 50L) / 100L) * 100L
                val dishListStr = memberDishLabels[m.name].orEmpty().joinToString(", ").ifBlank { "Chia đều" }
                MemberSplitResult(
                    name = m.name,
                    detailNote = dishListStr.take(38),
                    amount = rounded
                )
            }
        }
    }

    val grandTotal = remember(splitResults) { splitResults.sumOf { it.amount } }
    val selectedBank = remember(selectedBankBin) { VietQrHelper.findBankByBin(selectedBankBin) }

    val summaryPreviewBitmap = remember(
        billTitle,
        splitResults,
        grandTotal,
        selectedBank,
        accountNumber,
        accountName
    ) {
        renderBillSplitSummaryBitmap(
            title = billTitle,
            results = splitResults,
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
                            text = "Chia Tiền Nhóm + VietQR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Quét bill OCR • Chia đều hoặc theo món • Xuất ảnh kèm VietQR",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Nút Quét Hóa đơn bằng OCR
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AccentPurple.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentPurple.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = AccentCyan)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📷 Quét hóa đơn ăn uống tự động (Quick OCR)",
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "Chụp hoặc chọn ảnh bill nhà hàng để tự động tách tên món + giá tiền",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                                if (granted) cameraLauncher.launch(null)
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chụp Bill", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chọn ảnh Bill", color = AccentCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isScanningOcr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AccentCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Đang đọc các món trên hóa đơn...", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 2. Thông tin chung của Hóa đơn
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
                        value = billTitle,
                        onValueChange = { billTitle = it },
                        label = { Text("Tên buổi đi ăn / hóa đơn") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !splitByDishMode,
                            onClick = { splitByDishMode = false },
                            label = { Text("⚖️ Chia đều đầu người", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen,
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = splitByDishMode,
                            onClick = { splitByDishMode = true },
                            label = { Text("🍲 Chia theo từng món", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentAmber,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }

                    if (!splitByDishMode) {
                        OutlinedTextField(
                            value = totalBillInput,
                            onValueChange = { totalBillInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Tổng tiền hóa đơn (VNĐ)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = vatPercentInput,
                            onValueChange = { vatPercentInput = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Thuế VAT / Phí (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = discountInput,
                            onValueChange = { discountInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Giảm giá (VNĐ)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Danh sách thành viên
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
                        text = "👥 Danh sách thành viên (${members.size} người):",
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newMemberName,
                            onValueChange = { newMemberName = it },
                            placeholder = { Text("Nhập tên thành viên (VD: Minh, Lan...)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val trimmed = newMemberName.trim()
                                if (trimmed.isNotEmpty() && members.none { it.name.equals(trimmed, true) }) {
                                    members.add(BillMember(name = trimmed))
                                    newMemberName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = "Thêm")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        members.forEach { member ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(50))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    if (members.size > 1) {
                                        IconButton(
                                            onClick = { members.remove(member) },
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

            // 4. Nếu chọn Chia theo món -> Quản lý từng món & ai ăn món nào
            if (splitByDishMode) {
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
                            text = "🍲 Danh sách các món ăn (${dishes.size} món):",
                            fontWeight = FontWeight.Bold,
                            color = AccentAmber
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newDishName,
                                onValueChange = { newDishName = it },
                                label = { Text("Tên món") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = newDishPrice,
                                onValueChange = { newDishPrice = it.filter { c -> c.isDigit() } },
                                label = { Text("Giá (VNĐ)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val price = newDishPrice.toLongOrNull() ?: 0L
                                    if (newDishName.isNotBlank() && price > 0L) {
                                        dishes.add(
                                            BillDishItem(
                                                name = newDishName.trim(),
                                                price = price,
                                                consumerNames = members.map { it.name }.toSet()
                                            )
                                        )
                                        newDishName = ""
                                        newDishPrice = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentAmber,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Thêm món")
                            }
                        }

                        dishes.forEachIndexed { idx, dish ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${dish.name}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = formatCurrencyVnd(dish.price),
                                                color = AccentAmber,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.5.sp
                                            )
                                            IconButton(
                                                onClick = { dishes.removeAt(idx) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Chạm chọn người dùng món này:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        members.forEach { m ->
                                            val checked = m.name in dish.consumerNames
                                            FilterChip(
                                                selected = checked,
                                                onClick = {
                                                    val nextSet = dish.consumerNames.toMutableSet()
                                                    if (checked && nextSet.size > 1) nextSet.remove(m.name)
                                                    else nextSet.add(m.name)
                                                    dishes[idx] = dish.copy(consumerNames = nextSet)
                                                },
                                                label = { Text(m.name, fontSize = 11.5.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = AccentCyan,
                                                    selectedLabelColor = Color.Black
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Tài khoản ngân hàng nhận tiền (Gắn mã VietQR vào góc ảnh)
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
                        text = "🏦 Tài khoản nhận tiền (Tự động gắn mã VietQR vào ảnh tổng kết):",
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
                                label = { Text(bank.shortName) },
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
                            label = { Text("Số tài khoản (${selectedBank.shortName})") },
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

            // 6. Ảnh tổng hợp Hóa đơn + VietQR xuất trực tiếp
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
                            text = "📸 Ảnh Tổng Hợp Chia Tiền + Mã VietQR:",
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
                                    // Lưu luôn thông tin VietQR nếu người dùng vừa nhập mới
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
                                        "Bảng chia tiền: $billTitle"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Gửi vào Nhóm Chat", fontWeight = FontWeight.ExtraBold)
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
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lưu ảnh", color = AccentCyan, fontWeight = FontWeight.Bold)
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
 * Vẽ bức ảnh Tổng kết Chia Tiền Nhóm sắc nét gồm:
 * - Header tiêu đề + Ngày giờ + Tổng hóa đơn
 * - Danh sách từng người và số tiền cần đóng
 * - Khung tài khoản thụ hưởng + Mã VietQR chuẩn NAPAS ở góc dưới để quét chuyển khoản ngay!
 */
private fun renderBillSplitSummaryBitmap(
    title: String,
    results: List<MemberSplitResult>,
    grandTotal: Long,
    bank: BankInfo,
    accountNumber: String,
    accountName: String
): Bitmap? {
    val width = 1000
    val rowHeight = 96
    val hasQr = accountNumber.trim().length >= 4
    val footerHeight = if (hasQr) 400 else 140
    val height = (340 + results.size * rowHeight + footerHeight).coerceAtLeast(780)

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Nền Gradient tối sang trọng
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0xFF090D1A.toInt(), 0xFF111C38.toInt(), 0xFF0A0F1F.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Khung Card chính
    val cardRect = RectF(44f, 44f, width - 44f, height - 44f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF151F38.toInt()
    }
    canvas.drawRoundRect(cardRect, 40f, 40f, cardPaint)

    // Viền sáng
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(cardRect, 40f, 40f, strokePaint)

    // Header
    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val dateStr = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(Date())
    canvas.drawText("BẢNG CHIA TIỀN NHÓM • $dateStr", 88f, 110f, badgePaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(title.ifBlank { "Hóa đơn nhóm" }.take(32), 88f, 170f, titlePaint)

    val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4ADE80.toInt()
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(
        "Tổng cộng: ${formatCurrencyVnd(grandTotal)} (${results.size} người)",
        88f,
        228f,
        totalLabelPaint
    )

    // Đường kẻ ngang
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF334155.toInt()
        strokeWidth = 2f
    }
    canvas.drawLine(84f, 260f, width - 84f, 260f, dividerPaint)

    // Vẽ từng dòng thành viên
    var y = 290f
    val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E293B.toInt()
    }
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = 23f
    }
    val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFACC15.toInt()
        textSize = 36f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }

    results.forEachIndexed { idx, item ->
        val rRect = RectF(84f, y, width - 84f, y + 80f)
        canvas.drawRoundRect(rRect, 20f, 20f, rowBgPaint)
        canvas.drawText("${idx + 1}. ${item.name}", 112f, y + 38f, namePaint)
        canvas.drawText(item.detailNote, 112f, y + 66f, notePaint)
        canvas.drawText(formatCurrencyVnd(item.amount), width - 112f, y + 52f, amountPaint)
        y += rowHeight
    }

    // Khu vực Footer VietQR ở dưới
    if (hasQr) {
        y += 20f
        val footerRect = RectF(84f, y, width - 84f, height - 80f)
        val footerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F172A.toInt()
        }
        canvas.drawRoundRect(footerRect, 28f, 28f, footerBg)

        // Tạo mã VietQR ở góc phải dưới
        val defaultPerPersonAmt = if (results.map { it.amount }.distinct().size == 1) {
            results.firstOrNull()?.amount
        } else null
        val payload = VietQrHelper.buildVietQrPayload(
            bankBin = bank.bin,
            accountNumber = accountNumber.trim(),
            amount = defaultPerPersonAmt,
            memo = VietQrHelper.stripVietnameseAccents(title).take(25)
        )
        val qrBmp = VietQrHelper.generateQrBitmap(payload, sizePx = 280)

        val infoLeft = 118f
        var infoY = y + 65f

        val bankTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4ADE80.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("QUÉT MÃ VIETQR ĐỂ CHUYỂN KHOẢN:", infoLeft, infoY, bankTitlePaint)
        infoY += 50f

        val bankNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Ngân hàng: ${bank.shortName}", infoLeft, infoY, bankNamePaint)
        infoY += 48f

        val stkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF38BDF8.toInt()
            textSize = 38f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("STK: ${accountNumber.trim()}", infoLeft, infoY, stkPaint)
        infoY += 46f

        if (accountName.isNotBlank()) {
            val ownerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE2E8F0.toInt()
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(
                "Chủ TK: ${VietQrHelper.stripVietnameseAccents(accountName).uppercase(Locale.getDefault())}",
                infoLeft,
                infoY,
                ownerPaint
            )
            infoY += 42f
        }

        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 23f
        }
        canvas.drawText("Mở App Ngân hàng / MoMo bất kỳ để quét mã góc phải 👉", infoLeft, infoY, hintPaint)

        if (qrBmp != null) {
            val qrLeft = width - 84f - qrBmp.width - 28f
            val qrTop = y + ((footerRect.height() - qrBmp.height) / 2f)
            val whitePad = RectF(qrLeft - 10f, qrTop - 10f, qrLeft + qrBmp.width + 10f, qrTop + qrBmp.height + 10f)
            val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
            canvas.drawRoundRect(whitePad, 20f, 20f, whitePaint)
            canvas.drawBitmap(qrBmp, qrLeft, qrTop, null)
        }
    }

    return bmp
}

