package com.vunameaut.powertimer.ui.screens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.app.NotificationCompat
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vunameaut.powertimer.service.MoneySoundboxService
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPurple
import com.vunameaut.powertimer.ui.theme.AccentRed
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
    BackHandler(onBack = onExit)

    var hasNotificationAccess by remember {
        mutableStateOf(MoneySoundboxService.isNotificationListenerGranted(context))
    }
    var isEnabled by remember { mutableStateOf(MoneySoundboxService.isEnabled(context)) }
    var boostVolume by remember { mutableStateOf(MoneySoundboxService.isBoostVolume(context)) }
    var allAppsMode by remember { mutableStateOf(MoneySoundboxService.isAllAppsMode(context)) }
    var speechRate by remember { mutableFloatStateOf(MoneySoundboxService.getSpeechRate(context)) }

    val transactions by MoneySoundboxService.transactionsFlow.collectAsState()

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
                    color = AccentGreen.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentGreen)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Loa Soundbox đang trực chiến!",
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Tự động phát chuông Ting-Ting & đọc to số tiền khi có thông báo chuyển khoản đến.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Thẻ Tổng doanh thu nhận trong ngày + Nút Nghe thử Loa
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

                    OutlinedButton(
                        onClick = {
                            triggerSimulatedBankNotification(context)
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
                        text = "Chưa có giao dịch nhận tiền nào. Hãy bấm nút \"Nghe thử +150k\" ở trên để kiểm tra loa!",
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

private fun triggerSimulatedBankNotification(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
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
        .setContentText("TK 9999xxxx +500,000 VND. ND: Khach chuyen khoan")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    nm.notify((System.currentTimeMillis() % 10000).toInt() + 5000, notif)

    // Nếu người dùng chưa bật quyền Đọc thông báo, vẫn phát trực tiếp để kiểm tra loa
    if (!MoneySoundboxService.isNotificationListenerGranted(context)) {
        MoneySoundboxService.announceMoneyReceived(
            context = context,
            amount = 500_000L,
            sourceApp = "MB Bank",
            rawMessage = "TK 9999xxxx +500,000 VND. ND: Khach chuyen khoan",
            saveToHistory = true
        )
    }
}


