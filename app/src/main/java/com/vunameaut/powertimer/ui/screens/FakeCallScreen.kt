package com.vunameaut.powertimer.ui.screens

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.RecordVoiceOver
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.ui.theme.AccentAmber
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import com.vunameaut.powertimer.ui.theme.AccentPink
import com.vunameaut.powertimer.ui.theme.AccentRed
import com.vunameaut.powertimer.ui.theme.PowerTimerTheme
import kotlinx.coroutines.delay
import java.util.Locale

data class FakeCallerPreset(
    val label: String,
    val callerName: String,
    val phoneNumber: String,
    val carrierLabel: String,
    val avatarEmoji: String,
    val voiceScript: String
)

private val FAKE_CALLER_PRESETS = listOf(
    FakeCallerPreset(
        label = "👔 Sếp Tổng",
        callerName = "Sếp Hoàng (Giám đốc)",
        phoneNumber = "0912 388 999",
        carrierLabel = "Viettel 5G • Cuộc gọi thoại HD",
        avatarEmoji = "👔",
        voiceScript = "Alo em đang ở đâu đấy? Về văn phòng ngay nhé, khách hàng đang đợi ký hợp đồng gấp lắm rồi!"
    ),
    FakeCallerPreset(
        label = "👩 Mẹ Yêu",
        callerName = "Mẹ Yêu ❤️",
        phoneNumber = "0988 456 123",
        carrierLabel = "Viettel • Di động",
        avatarEmoji = "👩",
        voiceScript = "Alo con à, về nhà ngay mẹ bảo cái này gấp lắm, cả nhà đang chờ con ở nhà đấy nhé!"
    ),
    FakeCallerPreset(
        label = "💕 Vợ / Người Yêu",
        callerName = "Vợ Yêu 💕",
        phoneNumber = "0904 666 888",
        carrierLabel = "MobiFone 5G • Cuộc gọi HD",
        avatarEmoji = "💕",
        voiceScript = "Alo anh đang ngồi ở đâu đấy? Về đón em ngay đi, muộn lắm rồi em đang đứng đợi đây này!"
    ),
    FakeCallerPreset(
        label = "📦 Shipper Giao Hàng",
        callerName = "Shipper Giao Hàng Nhanh",
        phoneNumber = "0973 555 247",
        carrierLabel = "VinaPhone • Di động",
        avatarEmoji = "📦",
        voiceScript = "Alo anh ơi em giao đơn hàng đứng ở dưới cổng nhà mình rồi nhé, anh về nhận giúp em với ạ!"
    ),
    FakeCallerPreset(
        label = "🏢 Bảo Vệ Tòa Nhà",
        callerName = "Bảo Vệ Hầm Xe",
        phoneNumber = "0936 112 911",
        carrierLabel = "Viettel • Cuộc gọi khẩn",
        avatarEmoji = "🏢",
        voiceScript = "Alo anh ơi xuống hầm dắt lại xe giúp em ngay nhé, xe đang đỗ chắn lối ra vào rồi ạ!"
    )
)

object FakeCallScheduler {
    const val PREF_NAME = "fake_call_prefs"
    const val KEY_SCHEDULED_AT = "scheduled_trigger_ms"
    const val KEY_CALLER_NAME = "caller_name"
    const val KEY_CALLER_NUMBER = "caller_number"
    const val KEY_CARRIER = "caller_carrier"
    const val KEY_AVATAR = "caller_avatar"
    const val KEY_VOICE_SCRIPT = "caller_voice_script"
    const val KEY_AUTO_SPEAK = "caller_auto_speak"
    private const val ALARM_REQ_CODE = 88991
    const val CHANNEL_ID = "fake_incoming_call_channel"

    fun scheduleFakeCall(
        context: Context,
        delaySeconds: Int,
        callerName: String,
        phoneNumber: String,
        carrier: String,
        avatarEmoji: String,
        voiceScript: String,
        autoSpeakOnAnswer: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        prefs.edit()
            .putLong(KEY_SCHEDULED_AT, if (delaySeconds > 0) triggerAt else 0L)
            .putString(KEY_CALLER_NAME, callerName)
            .putString(KEY_CALLER_NUMBER, phoneNumber)
            .putString(KEY_CARRIER, carrier)
            .putString(KEY_AVATAR, avatarEmoji)
            .putString(KEY_VOICE_SCRIPT, voiceScript)
            .putBoolean(KEY_AUTO_SPEAK, autoSpeakOnAnswer)
            .apply()

        if (delaySeconds <= 0) {
            launchIncomingCallNow(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FakeCallAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }.onFailure {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelScheduledCall(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_SCHEDULED_AT, 0L).apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FakeCallAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ALARM_REQ_CODE)
    }

    fun launchIncomingCallNow(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_SCHEDULED_AT, 0L).apply()

        val callerName = prefs.getString(KEY_CALLER_NAME, "Sếp Hoàng (Giám đốc)") ?: "Sếp Hoàng"
        val callerNumber = prefs.getString(KEY_CALLER_NUMBER, "0912 388 999") ?: "0912 388 999"

        val callIntent = Intent(context, FakeCallIncomingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

        // Gửi kèm Full-Screen Notification để đánh thức màn hình khóa trên Android 10-15
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel(
                    CHANNEL_ID,
                    "Cuộc gọi đến giả lập",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Hiển thị giao diện cuộc gọi đến toàn màn hình"
                    setSound(null, null)
                }
                nm.createNotificationChannel(chan)
            }
            val fullScreenPi = PendingIntent.getActivity(
                context,
                ALARM_REQ_CODE,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(callerName)
                .setContentText("Cuộc gọi đến: $callerNumber")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPi, true)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()
            nm.notify(ALARM_REQ_CODE, notif)
        }

        runCatching {
            context.startActivity(callIntent)
        }
    }
}

class FakeCallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        FakeCallScheduler.launchIncomingCallNow(context)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeCallTool(onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)

    val prefs = remember { context.getSharedPreferences(FakeCallScheduler.PREF_NAME, Context.MODE_PRIVATE) }

    var selectedPresetIdx by remember { mutableIntStateOf(0) }
    var callerName by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_CALLER_NAME, FAKE_CALLER_PRESETS[0].callerName) ?: FAKE_CALLER_PRESETS[0].callerName)
    }
    var phoneNumber by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_CALLER_NUMBER, FAKE_CALLER_PRESETS[0].phoneNumber) ?: FAKE_CALLER_PRESETS[0].phoneNumber)
    }
    var carrierLabel by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_CARRIER, FAKE_CALLER_PRESETS[0].carrierLabel) ?: FAKE_CALLER_PRESETS[0].carrierLabel)
    }
    var avatarEmoji by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_AVATAR, FAKE_CALLER_PRESETS[0].avatarEmoji) ?: FAKE_CALLER_PRESETS[0].avatarEmoji)
    }
    var voiceScript by remember {
        mutableStateOf(prefs.getString(FakeCallScheduler.KEY_VOICE_SCRIPT, FAKE_CALLER_PRESETS[0].voiceScript) ?: FAKE_CALLER_PRESETS[0].voiceScript)
    }
    var autoSpeakOnAnswer by remember {
        mutableStateOf(prefs.getBoolean(FakeCallScheduler.KEY_AUTO_SPEAK, true))
    }

    var selectedDelaySec by remember { mutableIntStateOf(60) } // Mặc định 1 phút (60s) theo yêu cầu
    var scheduledTargetMs by remember { mutableLongStateOf(prefs.getLong(FakeCallScheduler.KEY_SCHEDULED_AT, 0L)) }
    var remainingCountdownSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(scheduledTargetMs) {
        while (scheduledTargetMs > System.currentTimeMillis()) {
            val diff = ((scheduledTargetMs - System.currentTimeMillis() + 999L) / 1000L).toInt()
            remainingCountdownSec = diff
            delay(500L)
        }
        remainingCountdownSec = 0
        if (scheduledTargetMs != 0L && System.currentTimeMillis() >= scheduledTargetMs) {
            scheduledTargetMs = 0L
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Cuộc Gọi Giả (Fake Call)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Hẹn giờ Sếp / Mẹ gọi đến • Giao diện & Nhạc chuông như thật",
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Trạng thái đang đếm ngược chờ đổ chuông
            if (remainingCountdownSec > 0) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = AccentGreen.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, AccentGreen, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⏳ ĐANG HẸN GIỜ CUỘC GỌI ĐẾN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AccentGreen
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%02d:%02d",
                                remainingCountdownSec / 60,
                                remainingCountdownSec % 60
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Người gọi: $callerName ($phoneNumber)\nBạn có thể thoát ra màn hình chính hoặc úp điện thoại xuống bàn, đúng giờ máy sẽ tự đổ chuông!",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                FakeCallScheduler.cancelScheduledCall(context)
                                scheduledTargetMs = 0L
                                remainingCountdownSec = 0
                                Toast.makeText(context, "Đã hủy hẹn giờ cuộc gọi giả!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hủy cuộc gọi hẹn giờ", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 1. Chọn nhanh kịch bản người gọi
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
                        text = "1. Chọn nhanh Người Gọi Đến (Hoặc tự nhập):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = AccentCyan
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(FAKE_CALLER_PRESETS) { idx, preset ->
                            val selected = selectedPresetIdx == idx
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedPresetIdx = idx
                                    callerName = preset.callerName
                                    phoneNumber = preset.phoneNumber
                                    carrierLabel = preset.carrierLabel
                                    avatarEmoji = preset.avatarEmoji
                                    voiceScript = preset.voiceScript
                                },
                                label = {
                                    Text(
                                        text = preset.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = callerName,
                        onValueChange = { callerName = it },
                        label = { Text("Tên người gọi hiển thị trên màn hình") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Số điện thoại") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = carrierLabel,
                            onValueChange = { carrierLabel = it },
                            label = { Text("Nhà mạng / Phụ đề") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = voiceScript,
                        onValueChange = { voiceScript = it },
                        label = { Text("Lời thoại giả lập phát ra loa khi bấm Nghe máy") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🗣️ Phát giọng nói từ đầu dây bên kia khi Nghe máy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "Khi bạn ấn Trả lời, điện thoại sẽ đọc câu thoại trên để người ngồi cạnh nghe thấy như có người gọi thật",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSpeakOnAnswer,
                            onCheckedChange = { autoSpeakOnAnswer = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AccentGreen
                            )
                        )
                    }
                }
            }

            // 2. Chọn thời gian hẹn giờ đổ chuông
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
                        text = "2. Hẹn thời gian điện thoại bắt đầu đổ chuông:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = AccentAmber
                    )

                    val delayOptions = listOf(
                        5 to "Sau 5 giây",
                        15 to "Sau 15 giây",
                        30 to "Sau 30 giây",
                        60 to "⭐ Sau 1 phút",
                        180 to "Sau 3 phút",
                        300 to "Sau 5 phút"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        delayOptions.forEach { (sec, title) ->
                            val isSelected = selectedDelaySec == sec
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedDelaySec = sec },
                                label = {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentAmber,
                                    selectedLabelColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                FakeCallScheduler.scheduleFakeCall(
                                    context = context,
                                    delaySeconds = selectedDelaySec,
                                    callerName = callerName.ifBlank { "Sếp Tổng" },
                                    phoneNumber = phoneNumber.ifBlank { "0912 388 999" },
                                    carrier = carrierLabel.ifBlank { "Viettel 5G" },
                                    avatarEmoji = avatarEmoji,
                                    voiceScript = voiceScript,
                                    autoSpeakOnAnswer = autoSpeakOnAnswer
                                )
                                scheduledTargetMs = System.currentTimeMillis() + selectedDelaySec * 1000L
                                Toast.makeText(
                                    context,
                                    "📞 Đã hẹn giờ! Điện thoại sẽ đổ chuông sau $selectedDelaySec giây.",
                                    Toast.LENGTH_LONG
                                ).show()
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
                            Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Hẹn giờ gọi (${selectedDelaySec}s)",
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                FakeCallScheduler.scheduleFakeCall(
                                    context = context,
                                    delaySeconds = 0,
                                    callerName = callerName.ifBlank { "Sếp Tổng" },
                                    phoneNumber = phoneNumber.ifBlank { "0912 388 999" },
                                    carrier = carrierLabel.ifBlank { "Viettel 5G" },
                                    avatarEmoji = avatarEmoji,
                                    voiceScript = voiceScript,
                                    autoSpeakOnAnswer = autoSpeakOnAnswer
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Rounded.PhoneCallback, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Đổ chuông ngay",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Activity toàn màn hình mô phỏng giao diện Cuộc Gọi Đến & Đang Nghe Máy y hệt cuộc gọi thật.
 */
class FakeCallIncomingActivity : ComponentActivity() {

    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences(FakeCallScheduler.PREF_NAME, Context.MODE_PRIVATE)
        val callerName = prefs.getString(FakeCallScheduler.KEY_CALLER_NAME, "Sếp Hoàng (Giám đốc)") ?: "Sếp Hoàng"
        val callerNumber = prefs.getString(FakeCallScheduler.KEY_CALLER_NUMBER, "0912 388 999") ?: "0912 388 999"
        val carrier = prefs.getString(FakeCallScheduler.KEY_CARRIER, "Viettel 5G • Cuộc gọi thoại HD") ?: "Viettel 5G"
        val avatarEmoji = prefs.getString(FakeCallScheduler.KEY_AVATAR, "👔") ?: "👔"
        val voiceScript = prefs.getString(FakeCallScheduler.KEY_VOICE_SCRIPT, "") ?: ""
        val autoSpeak = prefs.getBoolean(FakeCallScheduler.KEY_AUTO_SPEAK, true)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
                tts?.setSpeechRate(1.05f)
                ttsReady = true
            }
        }

        startRingingAndVibration()

        setContent {
            PowerTimerTheme {
                FakeIncomingCallScreenContent(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    carrierLabel = carrier,
                    avatarEmoji = avatarEmoji,
                    onAnswer = {
                        stopRingingAndVibration()
                        cancelCallNotification()
                        if (autoSpeak && voiceScript.isNotBlank()) {
                            speakCallerScript(voiceScript, useSpeaker = false)
                        }
                    },
                    onToggleSpeaker = { speakerOn ->
                        if (voiceScript.isNotBlank()) {
                            speakCallerScript(voiceScript, useSpeaker = speakerOn)
                        }
                    },
                    onReplayVoice = { speakerOn ->
                        if (voiceScript.isNotBlank()) {
                            speakCallerScript(voiceScript, useSpeaker = speakerOn)
                        }
                    },
                    onEndCall = {
                        stopRingingAndVibration()
                        cancelCallNotification()
                        finish()
                    }
                )
            }
        }
    }

    private fun startRingingAndVibration() {
        runCatching {
            val ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtonePlayer = MediaPlayer().apply {
                setDataSource(this@FakeCallIncomingActivity, ringUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }

        runCatching {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 1000, 800, 1000, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun speakCallerScript(script: String, useSpeaker: Boolean) {
        if (!ttsReady) return
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = useSpeaker
            val params = Bundle().apply {
                putInt(
                    TextToSpeech.Engine.KEY_PARAM_STREAM,
                    if (useSpeaker) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
                )
            }
            tts?.speak(script, TextToSpeech.QUEUE_FLUSH, params, "fake_caller_voice")
        }
    }

    private fun stopRingingAndVibration() {
        runCatching {
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
        }
        ringtonePlayer = null
        runCatching { vibrator?.cancel() }
    }

    private fun cancelCallNotification() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(88991)
        }
    }

    override fun onDestroy() {
        stopRingingAndVibration()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}

@Composable
private fun FakeIncomingCallScreenContent(
    callerName: String,
    callerNumber: String,
    carrierLabel: String,
    avatarEmoji: String,
    onAnswer: () -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onReplayVoice: (Boolean) -> Unit,
    onEndCall: () -> Unit
) {
    var isConnected by remember { mutableStateOf(false) }
    var callDurationSec by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isOnHold by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                delay(1000L)
                callDurationSec++
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_ring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF090D16),
                        Color(0xFF111827),
                        Color(0xFF1E293B),
                        Color(0xFF090D16)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        // Phần trên: Thông tin người gọi
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    text = carrierLabel,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(26.dp))

            // Avatar với vòng sóng âm
            Box(contentAlignment = Alignment.Center) {
                if (!isConnected) {
                    Box(
                        modifier = Modifier
                            .size(134.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(alpha = 0.14f))
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(106.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF334155), Color(0xFF1E293B))
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Text(text = avatarEmoji, fontSize = 46.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = callerName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = callerNumber,
                color = Color(0xFFCBD5E1),
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (!isConnected) {
                    "Đang gọi đến..."
                } else if (isOnHold) {
                    "Đang giữ cuộc gọi"
                } else {
                    String.format(
                        Locale.US,
                        "%02d:%02d • HD Voice",
                        callDurationSec / 60,
                        callDurationSec % 60
                    )
                },
                color = if (isConnected) AccentGreen else Color(0xFF94A3B8),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Phần dưới: Nút điều khiển cuộc gọi
        if (!isConnected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onEndCall() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Nhắc tôi", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onEndCall() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Message,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Nhắn tin", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Từ chối
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .clickable { onEndCall() }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "Từ chối",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Từ chối",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Nút Trả lời
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(74.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                                .clickable {
                                    isConnected = true
                                    onAnswer()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Trả lời",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Trả lời",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // Giao diện Đang Nghe Máy (6 nút chức năng + Nút Cúp Máy)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InCallActionButton(
                        icon = Icons.Rounded.MicOff,
                        label = "Tắt tiếng",
                        active = isMuted,
                        onClick = { isMuted = !isMuted }
                    )
                    InCallActionButton(
                        icon = Icons.Rounded.Dialpad,
                        label = "Bàn phím",
                        active = false,
                        onClick = {}
                    )
                    InCallActionButton(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "Loa ngoài",
                        active = isSpeakerOn,
                        onClick = {
                            isSpeakerOn = !isSpeakerOn
                            onToggleSpeaker(isSpeakerOn)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InCallActionButton(
                        icon = Icons.Rounded.PersonAdd,
                        label = "Thêm gọi",
                        active = false,
                        onClick = {}
                    )
                    InCallActionButton(
                        icon = Icons.Rounded.Pause,
                        label = "Giữ máy",
                        active = isOnHold,
                        onClick = { isOnHold = !isOnHold }
                    )
                    InCallActionButton(
                        icon = Icons.Rounded.RecordVoiceOver,
                        label = "Phát thoại",
                        active = true,
                        onClick = { onReplayVoice(isSpeakerOn) }
                    )
                }

                Spacer(Modifier.height(6.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .clickable { onEndCall() }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "Kết thúc",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InCallActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.12f))
                .clickable { onClick() }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF0F172A) else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

