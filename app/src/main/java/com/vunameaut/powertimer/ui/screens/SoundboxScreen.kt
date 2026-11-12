package com.vunameaut.powertimer.ui.screens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vunameaut.powertimer.service.MoneySoundboxService
import com.vunameaut.powertimer.service.ScannedNotificationItem
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundboxTool(onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onExit)

    var hasNotificationAccess by remember {
        mutableStateOf(MoneySoundboxService.isNotificationListenerGranted(context))
    }
    var isEnabled by remember { mutableStateOf(MoneySoundboxService.isEnabled(context)) }
    var boostVolume by remember { mutableStateOf(MoneySoundboxService.isBoostVolume(context)) }
    var allAppsMode by remember { mutableStateOf(MoneySoundboxService.isAllAppsMode(context)) }
    var speechRate by remember { mutableFloatStateOf(MoneySoundboxService.getSpeechRate(context)) }

    val transactions by MoneySoundboxService.transactionsFlow.collectAsState()
    val isServiceConnected by MoneySoundboxService.serviceConnectedFlow.collectAsState()

    var scannedNotifications by remember { mutableStateOf<List<ScannedNotificationItem>>(emptyList()) }
    var scanStatusMessage by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        MoneySoundboxService.ensureServiceRebound(context)
        MoneySoundboxService.loadTransactions(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = MoneySoundboxService.isNotificationListenerGranted(context)
                MoneySoundboxService.ensureServiceRebound(context)
                MoneySoundboxService.loadTransactions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todayTotal = remember(transactions) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        transactions.filter { it.timestamp >= startOfDay }.sumOf { it.amount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Loa Báo Nhận Tiền (Mini Soundbox)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Đọc to số tiền chuyển khoản Ngân hàng & MoMo tức thì",
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
            // 1. Thẻ Quyền truy cập thông báo (100% an toàn với Ngân hàng, không dùng Trợ năng!)
            if (!hasNotificationAccess) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AccentAmber.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = AccentAmber)
                            Text(
                                text = "Cần cấp quyền Đọc Thông Báo (An toàn Ngân hàng)",
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber,
                                fontSize = 14.5.sp
                            )
                        }
                        Text(
                            text = "Quyền Đọc thông báo (Notification Listener) hoàn toàn KHÔNG phải là Trợ năng nên các ứng dụng Ngân hàng & VNeID hoạt động bình thường 100%. Ứng dụng chỉ lắng nghe dòng thông báo cộng tiền (+VND) để phát loa.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                        Button(
                            onClick = { MoneySoundboxService.openNotificationListenerSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentAmber,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bật quyền Truy cập Thông báo cho Tools Hub", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isServiceConnected) AccentGreen.copy(alpha = 0.14f) else AccentAmber.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isServiceConnected) AccentGreen.copy(alpha = 0.4f) else AccentAmber.copy(alpha = 0.45f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (isServiceConnected) AccentGreen else AccentAmber
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isServiceConnected) {
                                    "Loa Soundbox đang trực chiến (Đã kết nối)!"
                                } else {
                                    "Đã cấp quyền (Đang đánh thức kết nối ngầm...)"
                                },
                                fontWeight = FontWeight.Bold,
                                color = if (isServiceConnected) AccentGreen else AccentAmber,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = if (isServiceConnected) {
                                    "Tự động phát chuông Ting-Ting & đọc to số tiền khi có thông báo chuyển khoản đến."
                                } else {
                                    "Nếu sau khi cập nhật app mà máy chưa tự đọc, hãy bấm nút Làm mới bên cạnh hoặc Tắt/Bật lại quyền Đọc thông báo 1 lần."
                                },
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                MoneySoundboxService.forceReconnectNotificationListener(context)
                                Toast.makeText(
                                    context,
                                    "🔄 Đã làm mới kết nối dịch vụ Đọc thông báo!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Làm mới kết nối",
                                tint = AccentCyan
                            )
                        }
                    }
                }
            }

            // 2. Thẻ Tổng doanh thu nhận trong ngày + Nút Nghe thử & Quét thông báo hiện tại
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = "TỔNG TIỀN NHẬN HÔM NAY",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = NumberFormat.getNumberInstance(Locale("vi", "VN")).format(todayTotal) + " đ",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = AccentGreen
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                MoneySoundboxService.announceMoneyReceived(
                                    context = context,
                                    amount = 150_000L,
                                    sourceApp = "MB Bank",
                                    rawMessage = "TK 9999xxxx +150,000 VND (Nghe thử loa Soundbox)",
                                    saveToHistory = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Nghe thử +150k",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                MoneySoundboxService.announceMoneyReceived(
                                    context = context,
                                    amount = 1_250_000L,
                                    sourceApp = "Vietcombank",
                                    rawMessage = "SD TK 0011xxxx +1,250,000 VND (Nghe thử loa Soundbox)",
                                    saveToHistory = true
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Thử +1.25 Triệu",
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Nút Bắn thử Thông báo Ngân hàng thật (+500.000đ)
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                triggerSimulatedBankNotification(context)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Bắn thử Thông báo Ngân hàng thật (+500.000đ)",
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // NÚT MỚI: Quét & Đọc Thông Báo Ngân Hàng Hiện Tại trên thanh trạng thái
                    Button(
                        onClick = {
                            if (!MoneySoundboxService.isNotificationListenerGranted(context)) {
                                Toast.makeText(
                                    context,
                                    "Vui lòng cấp quyền Đọc thông báo trước!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                MoneySoundboxService.openNotificationListenerSettings(context)
                                return@Button
                            }
                            scope.launch {
                                isScanning = true
                                scanStatusMessage = "🔍 Đang quét các thông báo hiện có trên thanh trạng thái..."
                                if (MoneySoundboxService.activeInstance == null) {
                                    MoneySoundboxService.forceReconnectNotificationListener(context)
                                    repeat(8) {
                                        if (MoneySoundboxService.activeInstance != null) return@repeat
                                        delay(150L)
                                    }
                                }

                                if (MoneySoundboxService.activeInstance == null) {
                                    isScanning = false
                                    scannedNotifications = emptyList()
                                    scanStatusMessage =
                                        "⚠️ Hệ điều hành đang tạm ngắt dịch vụ Đọc thông báo sau khi cập nhật app. Hãy bấm nút \"Mở Cài đặt Đọc thông báo\" bên dưới -> Tắt rồi Bật lại công tắc Tools Hub 1 lần là đọc được ngay!"
                                    return@launch
                                }

                                val found = MoneySoundboxService.scanAndReadCurrentNotifications(
                                    context = context,
                                    autoSpeakBestMatch = true
                                )
                                scannedNotifications = found
                                isScanning = false

                                val bankMatches = found.count { it.isIncomingMoney || it.isKnownBankApp || it.parsedAmount != null }
                                scanStatusMessage = when {
                                    found.isEmpty() ->
                                        "ℹ️ Hiện không có thông báo nào trên thanh trạng thái. Hãy thử bấm nút \"Bắn thử Thông báo Ngân hàng thật (+500.000đ)\" ở trên rồi bấm quét lại!"
                                    bankMatches > 0 ->
                                        "✅ Tìm thấy $bankMatches thông báo Ngân hàng / Số dư (trên tổng số ${found.size} thông báo hiện tại) và đang phát loa đọc!"
                                    else ->
                                        "ℹ️ Tìm thấy ${found.size} thông báo trên thanh trạng thái (chưa có thông báo biến động số dư ngân hàng). Bạn có thể bấm nút 🔊 bên cạnh bất kỳ thông báo nào bên dưới để nghe thử!"
                                }
                            }
                        },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isScanning) {
                                "Đang quét thông báo hiện tại..."
                            } else {
                                "Đọc Thông Báo Ngân Hàng Hiện Tại (Trên máy)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2.5. Kết quả quét Thông báo hiện tại trên thanh trạng thái
            if (scanStatusMessage != null || scannedNotifications.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentPurple.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
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
                                text = "📲 Thông báo hiện tại trên thanh trạng thái (${scannedNotifications.size})",
                                fontWeight = FontWeight.Bold,
                                color = AccentPurple,
                                fontSize = 13.5.sp
                            )
                            TextButton(
                                onClick = {
                                    MoneySoundboxService.openNotificationListenerSettings(context)
                                }
                            ) {
                                Text("Cài đặt quyền", color = AccentCyan, fontSize = 11.5.sp)
                            }
                        }

                        scanStatusMessage?.let { msg ->
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        scannedNotifications.forEach { item ->
                            val isHighlight = item.isIncomingMoney || item.isKnownBankApp || item.parsedAmount != null
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isHighlight) {
                                    AccentGreen.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isHighlight) AccentGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isHighlight) AccentGreen.copy(alpha = 0.22f) else AccentCyan.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = item.appName,
                                                    color = if (isHighlight) AccentGreen else AccentCyan,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (item.parsedAmount != null) {
                                                Text(
                                                    text = (if (item.isIncomingMoney) "+" else "") +
                                                        NumberFormat.getNumberInstance(Locale("vi", "VN")).format(item.parsedAmount) + " đ",
                                                    color = if (item.isIncomingMoney) AccentGreen else AccentAmber,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                        if (item.title.isNotBlank()) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = item.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (item.body.isNotBlank()) {
                                            Text(
                                                text = item.body,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (item.parsedAmount != null) {
                                                MoneySoundboxService.announceMoneyReceived(
                                                    context = context,
                                                    amount = item.parsedAmount,
                                                    sourceApp = item.appName,
                                                    rawMessage = "${item.title}: ${item.body}".trim(':', ' '),
                                                    saveToHistory = true
                                                )
                                            } else {
                                                MoneySoundboxService.announceCustomNotificationText(
                                                    context = context,
                                                    sourceApp = item.appName,
                                                    rawMessage = "${item.title}. ${item.body}".trim('.', ' ')
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isHighlight) AccentGreen else AccentCyan,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .height(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                            contentDescription = "Đọc thông báo này",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Đọc", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Cài đặt Loa Soundbox
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
                        text = "⚙️ Cấu hình Loa thông báo:",
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bật đọc số tiền tự động", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Tự động đọc khi nhận thông báo biến động +VND từ Ngân hàng / MoMo",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                isEnabled = it
                                MoneySoundboxService.setEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentGreen
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tự động kích âm lượng lớn (85%)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Đảm bảo nghe rõ trong môi trường quán ăn / cửa hàng đông người",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = boostVolume,
                            onCheckedChange = {
                                boostVolume = it
                                MoneySoundboxService.setBoostVolume(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentCyan
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nhận diện mọi App Ngân hàng & Ví", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Hỗ trợ VCB, MB, TCB, BIDV, CTG, ACB, TPB, VPB, Agribank, MoMo, ZaloPay, VNPay...",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allAppsMode,
                            onCheckedChange = {
                                allAppsMode = it
                                MoneySoundboxService.setAllAppsMode(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentPurple
                            )
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tốc độ giọng đọc:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = String.format(Locale.US, "%.2fx", speechRate),
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Slider(
                            value = speechRate,
                            onValueChange = {
                                speechRate = it
                                MoneySoundboxService.setSpeechRate(context, it)
                            },
                            valueRange = 0.8f..1.35f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan
                            )
                        )
                    }
                }
            }

            // 4. Lịch sử biến động nhận tiền
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🕘 Lịch sử nhận tiền gần đây (${transactions.size})",
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen,
                    fontSize = 14.sp
                )
                if (transactions.isNotEmpty()) {
                    TextButton(onClick = { MoneySoundboxService.clearTransactions(context) }) {
                        Text("Xóa lịch sử", color = AccentRed, fontSize = 12.sp)
                    }
                }
            }

            val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
            if (transactions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Chưa có giao dịch nhận tiền nào. Hãy bấm nút \"Nghe thử +150k\" hoặc \"Đọc Thông Báo Ngân Hàng Hiện Tại\" ở trên để kiểm tra loa!",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                transactions.forEach { tx ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentGreen.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = tx.sourceApp,
                                            color = AccentGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = fmt.format(Date(tx.timestamp)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "+" + NumberFormat.getNumberInstance(Locale("vi", "VN")).format(tx.amount) + " đ",
                                    color = AccentGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 17.sp
                                )
                                if (tx.rawMessage.isNotBlank()) {
                                    Text(
                                        text = tx.rawMessage,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    MoneySoundboxService.announceMoneyReceived(
                                        context = context,
                                        amount = tx.amount,
                                        sourceApp = tx.sourceApp,
                                        rawMessage = tx.rawMessage,
                                        saveToHistory = false
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Đọc lại",
                                    tint = AccentCyan
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

private suspend fun triggerSimulatedBankNotification(context: Context) {
    MoneySoundboxService.ensureServiceRebound(context)
    val beforeAnnounceMs = MoneySoundboxService.lastAnnouncedTimestampMs

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val simMessage = "TK 9999xxxx +500,000 VND. ND: Khach chuyen khoan"

    if (nm != null) {
        val channelId = "soundbox_sim_bank_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Giả lập Biến động số dư",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Biến động số dư MB Bank")
            .setContentText(simMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(simMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            nm.notify((System.currentTimeMillis() % 10000).toInt() + 5000, notif)
        }
    }

    // Chờ tối đa 650ms để NotificationListenerService bắt tự động;
    // nếu hệ điều hành (OriginOS/MIUI) chặn tự bắt thông báo nội bộ hoặc chưa kịp bind lại, tự động phát loa để đảm bảo 100% đọc to!
    delay(650L)
    if (MoneySoundboxService.lastAnnouncedTimestampMs <= beforeAnnounceMs) {
        MoneySoundboxService.forceReconnectNotificationListener(context)
        MoneySoundboxService.announceMoneyReceived(
            context = context,
            amount = 500_000L,
            sourceApp = "MB Bank",
            rawMessage = "Biến động số dư MB Bank: $simMessage",
            saveToHistory = true
        )
    }
}
