                                                                                                                               package com.vunameaut.powertimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.Toast
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.qr.VietQrHelper
import com.vunameaut.powertimer.service.QuickTileActions
import com.vunameaut.powertimer.service.TimerForegroundService
import com.vunameaut.powertimer.ui.screens.VietnameseLunarCalendar
import com.vunameaut.powertimer.weather.WeatherAtmosphere
import com.vunameaut.powertimer.weather.WeatherManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Tiện ích (AppWidget) 4 nút bấm nhanh đặt trực tiếp ngoài Màn hình chính điện thoại:
 * 1. Bật / Tắt Hẹn giờ màn hình (30 phút)
 * 2. Mở thẳng máy quét QR / OCR
 * 3. Mở thẻ Mã VietQR cá nhân
 * 4. Tải ngay Video từ link đang copy trong Clipboard
 */
class ToolsHubAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_TIMER = "com.vunameaut.powertimer.widget.ACTION_TOGGLE_TIMER"
        const val ACTION_PIN_WIDGET_SUCCESS = "com.vunameaut.powertimer.widget.ACTION_PIN_WIDGET_SUCCESS"
        internal const val EXTRA_WIDGET_LABEL = "extra_widget_label"

        fun updateAllWidgets(context: Context) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx)
            val ids = manager.getAppWidgetIds(ComponentName(appCtx, ToolsHubAppWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                ids.forEach { id -> updateSingleWidget(appCtx, manager, id) }
            }
            val qrIds = manager.getAppWidgetIds(ComponentName(appCtx, VietQrAppWidgetProvider::class.java))
            if (qrIds.isNotEmpty()) {
                qrIds.forEach { id -> VietQrAppWidgetProvider.updateSingleQrWidget(appCtx, manager, id) }
            }
            val lunarIds = manager.getAppWidgetIds(ComponentName(appCtx, LunarCalendarAppWidgetProvider::class.java))
            if (lunarIds.isNotEmpty()) {
                lunarIds.forEach { id -> LunarCalendarAppWidgetProvider.updateSingleLunarWidget(appCtx, manager, id) }
            }
        }

        internal fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_tools_hub)
            val timerState = TimerForegroundService.timerFlow.value

            if (timerState.isRunning) {
                views.setTextViewText(R.id.widget_timer_emoji, "⏳")
                views.setTextViewText(R.id.widget_timer_label, timerState.formattedRemaining)
            } else {
                views.setTextViewText(R.id.widget_timer_emoji, "🌙")
                views.setTextViewText(R.id.widget_timer_label, "Hẹn giờ")
            }

            // Nút 1: Toggle Hẹn giờ
            val toggleTimerIntent = Intent(context, ToolsHubAppWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_TIMER
            }
            val toggleTimerPi = PendingIntent.getBroadcast(
                context,
                1001,
                toggleTimerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_timer, toggleTimerPi)

            // Nút 2: Mở Quét QR / OCR
            views.setOnClickPendingIntent(
                R.id.widget_btn_scanner,
                buildMainActivityPendingIntent(context, 1002, QuickTileActions.ACTION_OPEN_QR_SCANNER)
            )

            // Nút 3: Mở Mã VietQR Cá nhân
            views.setOnClickPendingIntent(
                R.id.widget_btn_vietqr,
                buildMainActivityPendingIntent(context, 1003, QuickTileActions.ACTION_SHOW_MY_VIETQR)
            )

            // Nút 4: Tải ngay Video từ Clipboard
            views.setOnClickPendingIntent(
                R.id.widget_btn_video,
                buildMainActivityPendingIntent(context, 1004, QuickTileActions.ACTION_DOWNLOAD_CLIPBOARD_VIDEO)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        internal fun buildMainActivityPendingIntent(
            context: Context,
            requestCode: Int,
            quickAction: String
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(QuickTileActions.EXTRA_QUICK_ACTION, quickAction)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        internal fun buildOpenToolPendingIntent(
            context: Context,
            requestCode: Int,
            toolId: String
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("OPEN_TOOL_ID", toolId)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun requestPinWidgetToHomeScreen(
            context: Context,
            providerClass: Class<out AppWidgetProvider>,
            label: String,
            showToastOnRequest: Boolean = false
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    val myProvider = ComponentName(context, providerClass)
                    val successIntent = Intent(context, ToolsHubAppWidgetProvider::class.java).apply {
                        action = ACTION_PIN_WIDGET_SUCCESS
                        putExtra(EXTRA_WIDGET_LABEL, label)
                    }
                    val successCallback = PendingIntent.getBroadcast(
                        context,
                        label.hashCode(),
                        successIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val pinned = runCatching {
                        appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                    }.getOrDefault(false)
                    if (pinned) {
                        return true
                    }
                }
            }
            return false
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_TIMER -> {
                val current = TimerForegroundService.timerFlow.value
                if (current.isRunning) {
                    TimerForegroundService.stopTimer(context)
                    Toast.makeText(context, "Đã tắt hẹn giờ màn hình!", Toast.LENGTH_SHORT).show()
                } else {
                    TimerForegroundService.startTimer(
                        context = context,
                        seconds = 30 * 60L,
                        mode = ShutdownMode.BANK_SAFE_SLEEP,
                        shakeToExtend = false
                    )
                    Toast.makeText(context, "Đã bật hẹn giờ tắt màn hình 30 phút!", Toast.LENGTH_SHORT).show()
                }
                updateAllWidgets(context)
            }
            ACTION_PIN_WIDGET_SUCCESS -> {
                val label = intent.getStringExtra(EXTRA_WIDGET_LABEL) ?: "Tiện ích"
                updateAllWidgets(context)
                Toast.makeText(
                    context,
                    "✅ Đã ghim \"$label\" ra Màn hình chính thành công!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

/**
 * Tiện ích (AppWidget) hiển thị trực tiếp Thẻ Mã VietQR Cá Nhân ngoài Màn hình chính.
 */
class VietQrAppWidgetProvider : AppWidgetProvider() {

    companion object {
        internal fun updateSingleQrWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_my_vietqr)
            val profile = VietQrHelper.loadMyVietQrProfile(context)
            if (profile.isValid) {
                val bank = VietQrHelper.findBankByBin(profile.bankBin)
                val amt = profile.defaultAmount.replace(Regex("[^0-9]"), "").toLongOrNull()
                val payload = VietQrHelper.buildVietQrPayload(
                    bankBin = profile.bankBin,
                    accountNumber = profile.accountNumber,
                    amount = amt,
                    memo = profile.defaultMemo
                )
                val qrBmp = VietQrHelper.generateQrBitmap(payload, sizePx = 320)
                views.setTextViewText(R.id.widget_vietqr_title, "🏦 ${bank.shortName.uppercase()}")
                views.setTextViewText(R.id.widget_vietqr_subtitle, "STK: ${profile.accountNumber}")
                if (qrBmp != null) {
                    views.setImageViewBitmap(R.id.widget_vietqr_image, qrBmp)
                }
            } else {
                views.setTextViewText(R.id.widget_vietqr_title, "🏦 VIETQR CỦA TÔI")
                views.setTextViewText(R.id.widget_vietqr_subtitle, "Chạm để cài đặt STK")
            }

            views.setOnClickPendingIntent(
                R.id.widget_vietqr_root,
                ToolsHubAppWidgetProvider.buildMainActivityPendingIntent(
                    context,
                    2001,
                    QuickTileActions.ACTION_SHOW_MY_VIETQR
                )
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleQrWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

/**
 * Phong cách màu sắc Widget phù hợp cho nhiều lứa tuổi & sở thích.
 */
enum class LunarWidgetStyle(
    val key: String,
    val label: String,
    val audienceHint: String
) {
    DARK("dark", "Tối Hiện Đại", "Giới trẻ / Dễ nhìn ban đêm"),
    GLASS("glass", "Kính Mờ", "Tối giản / Hợp mọi hình nền"),
    LIGHT("light", "Sáng Rõ Nét", "Trung niên / Văn phòng"),
    BLOC("bloc", "Lịch Bloc Đỏ", "Người lớn tuổi / Truyền thống");

    companion object {
        fun fromKey(key: String?): LunarWidgetStyle =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}

/**
 * Mức cỡ chữ trên Widget Lịch Âm (điều chỉnh độc lập cho từng lứa tuổi).
 */
enum class LunarWidgetFontSize(
    val key: String,
    val label: String,
    val scale: Float
) {
    SMALL("small", "Nhỏ", 0.88f),
    MEDIUM("medium", "Vừa", 1.0f),
    LARGE("large", "Lớn", 1.18f),
    EXTRA_LARGE("xlarge", "Cực Lớn", 1.36f);

    companion object {
        fun fromKey(key: String?): LunarWidgetFontSize =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

object LunarWidgetPrefs {
    private const val PREF_NAME = "lunar_widget_appearance_prefs"
    private const val KEY_STYLE = "widget_style"
    private const val KEY_FONT_SIZE = "widget_font_size"

    fun getStyle(context: Context): LunarWidgetStyle {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return LunarWidgetStyle.fromKey(prefs.getString(KEY_STYLE, LunarWidgetStyle.DARK.key))
    }

    fun setStyle(context: Context, style: LunarWidgetStyle) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.key)
            .apply()
        LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
    }

    fun getFontSize(context: Context): LunarWidgetFontSize {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return LunarWidgetFontSize.fromKey(prefs.getString(KEY_FONT_SIZE, LunarWidgetFontSize.MEDIUM.key))
    }

    fun setFontSize(context: Context, fontSize: LunarWidgetFontSize) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FONT_SIZE, fontSize.key)
            .apply()
        LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
    }
}

/**
 * Tiện ích (AppWidget) Lịch Âm - Dương Tháng ngoài Màn hình chính (tương tác chuyển tháng giống Google Calendar).
 */
class LunarCalendarAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PREV_MONTH = "com.vunameaut.powertimer.widget.ACTION_LUNAR_PREV_MONTH"
        const val ACTION_NEXT_MONTH = "com.vunameaut.powertimer.widget.ACTION_LUNAR_NEXT_MONTH"
        const val ACTION_TODAY_MONTH = "com.vunameaut.powertimer.widget.ACTION_LUNAR_TODAY_MONTH"
        private const val PREF_WIDGET = "lunar_calendar_widget_prefs"
        private const val KEY_OFFSET_MONTHS = "viewing_month_offset"

        private val CELL_IDS = intArrayOf(
            R.id.cell_0, R.id.cell_1, R.id.cell_2, R.id.cell_3, R.id.cell_4, R.id.cell_5, R.id.cell_6
        )
        private val SOLAR_IDS = intArrayOf(
            R.id.solar_0, R.id.solar_1, R.id.solar_2, R.id.solar_3, R.id.solar_4, R.id.solar_5, R.id.solar_6
        )
        private val LUNAR_IDS = intArrayOf(
            R.id.lunar_0, R.id.lunar_1, R.id.lunar_2, R.id.lunar_3, R.id.lunar_4, R.id.lunar_5, R.id.lunar_6
        )

        private val DOW_IDS = intArrayOf(
            R.id.widget_lunar_dow_0,
            R.id.widget_lunar_dow_1,
            R.id.widget_lunar_dow_2,
            R.id.widget_lunar_dow_3,
            R.id.widget_lunar_dow_4,
            R.id.widget_lunar_dow_5,
            R.id.widget_lunar_dow_6
        )

        internal fun updateSingleLunarWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREF_WIDGET, Context.MODE_PRIVATE)
            val monthOffset = prefs.getInt(KEY_OFFSET_MONTHS, 0)
            val style = LunarWidgetPrefs.getStyle(context)
            val s = LunarWidgetPrefs.getFontSize(context).scale
            val isLightBg = (style == LunarWidgetStyle.LIGHT || style == LunarWidgetStyle.BLOC)

            val todayCal = Calendar.getInstance()
            val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
            val todayMonth = todayCal.get(Calendar.MONTH) + 1
            val todayYear = todayCal.get(Calendar.YEAR)
            val todayInfo = VietnameseLunarCalendar.getFullDayInfo(todayDay, todayMonth, todayYear)

            val viewCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, monthOffset)
            }
            val viewMonth = viewCal.get(Calendar.MONTH) + 1
            val viewYear = viewCal.get(Calendar.YEAR)
            val midMonthLunar = VietnameseLunarCalendar.getFullDayInfo(15, viewMonth, viewYear)

            val views = RemoteViews(context.packageName, R.layout.widget_lunar_calendar)

            // Áp dụng nền & màu chữ theo Phong cách (Theme) đã chọn
            val cardBgRes = when (style) {
                LunarWidgetStyle.DARK -> R.drawable.widget_lunar_card_bg
                LunarWidgetStyle.GLASS -> R.drawable.widget_lunar_card_glass
                LunarWidgetStyle.LIGHT -> R.drawable.widget_lunar_card_light
                LunarWidgetStyle.BLOC -> R.drawable.widget_lunar_card_bloc
            }
            views.setInt(R.id.widget_lunar_root, "setBackgroundResource", cardBgRes)
            views.setInt(
                R.id.widget_lunar_footer_box,
                "setBackgroundResource",
                if (isLightBg) R.drawable.widget_footer_pill_light_bg else R.drawable.widget_footer_pill_bg
            )
            val btnBgRes = if (isLightBg) R.drawable.widget_btn_light_bg else R.drawable.widget_btn_bg
            views.setInt(R.id.widget_lunar_btn_prev, "setBackgroundResource", btnBgRes)
            views.setInt(R.id.widget_lunar_btn_today, "setBackgroundResource", btnBgRes)
            views.setInt(R.id.widget_lunar_btn_next, "setBackgroundResource", btnBgRes)

            val titleColor = when (style) {
                LunarWidgetStyle.BLOC -> 0xFFDC2626.toInt()
                LunarWidgetStyle.LIGHT -> 0xFF0F172A.toInt()
                else -> 0xFFF8FAFC.toInt()
            }
            val subColor = when (style) {
                LunarWidgetStyle.BLOC -> 0xFFB45309.toInt()
                LunarWidgetStyle.LIGHT -> 0xFF0284C7.toInt()
                else -> 0xFF38BDF8.toInt()
            }
            val btnTextColor = if (isLightBg) 0xFF0F172A.toInt() else 0xFFF8FAFC.toInt()
            val todayBtnColor = if (isLightBg) 0xFF15803D.toInt() else 0xFF4ADE80.toInt()

            views.setTextColor(R.id.widget_lunar_month_title, titleColor)
            views.setTextColor(R.id.widget_lunar_today_subtitle, subColor)
            views.setTextColor(R.id.widget_lunar_btn_prev, btnTextColor)
            views.setTextColor(R.id.widget_lunar_btn_next, btnTextColor)
            views.setTextColor(R.id.widget_lunar_btn_today, todayBtnColor)

            // Áp dụng cỡ chữ động (Font Size Scale)
            views.setTextViewTextSize(R.id.widget_lunar_month_title, TypedValue.COMPLEX_UNIT_SP, 13.5f * s)
            views.setTextViewTextSize(R.id.widget_lunar_today_subtitle, TypedValue.COMPLEX_UNIT_SP, 10.5f * s)
            views.setTextViewTextSize(R.id.widget_lunar_btn_today, TypedValue.COMPLEX_UNIT_SP, 10f * s)

            DOW_IDS.forEachIndexed { col, dowId ->
                views.setTextViewTextSize(dowId, TypedValue.COMPLEX_UNIT_SP, 10.5f * s)
                val dowColor = when (col) {
                    6 -> if (isLightBg) 0xFFDC2626.toInt() else 0xFFFB7185.toInt()
                    5 -> if (isLightBg) 0xFF0284C7.toInt() else 0xFF38BDF8.toInt()
                    else -> if (isLightBg) 0xFF475569.toInt() else 0xFF94A3B8.toInt()
                }
                views.setTextColor(dowId, dowColor)
            }

            views.setTextViewText(
                R.id.widget_lunar_month_title,
                "🗓️ Tháng $viewMonth / $viewYear • Năm ${midMonthLunar.yearCanChi}"
            )
            views.setTextViewText(
                R.id.widget_lunar_today_subtitle,
                String.format(
                    "Hôm nay: %02d/%02d DL • %02d/%02d ÂL (%s)",
                    todayDay,
                    todayMonth,
                    todayInfo.lunarDay,
                    todayInfo.lunarMonth,
                    if (todayInfo.isHoangDaoDay) "Hoàng Đạo 🟢" else todayInfo.dayCanChi
                )
            )

            // 1. Tạo lưới lịch tháng bằng 100% Vector TextView nguyên bản (Không dùng Bitmap để chữ siêu nét)
            views.removeAllViews(R.id.widget_lunar_grid_container)
            val openLunarToolPi = ToolsHubAppWidgetProvider.buildOpenToolPendingIntent(
                context,
                3004,
                "lunar_calendar"
            )

            val monthCal = Calendar.getInstance().apply {
                set(viewYear, viewMonth - 1, 1, 12, 0, 0)
            }
            val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDow = monthCal.get(Calendar.DAY_OF_WEEK)
            val leadingEmpty = if (firstDow == Calendar.SUNDAY) 6 else (firstDow - Calendar.MONDAY)
            val totalCells = leadingEmpty + daysInMonth
            val numRows = if (totalCells > 35) 6 else 5

            for (row in 0 until numRows) {
                val rowViews = RemoteViews(context.packageName, R.layout.widget_lunar_row)
                for (col in 0..6) {
                    val cellIdx = row * 7 + col
                    val day = cellIdx - leadingEmpty + 1
                    val cellId = CELL_IDS[col]
                    val solarId = SOLAR_IDS[col]
                    val lunarId = LUNAR_IDS[col]

                    if (day in 1..daysInMonth) {
                        val info = VietnameseLunarCalendar.getFullDayInfo(day, viewMonth, viewYear)
                        val isToday = (day == todayDay && viewMonth == todayMonth && viewYear == todayYear)
                        val isMung1OrRam = (info.lunarDay == 1 || info.lunarDay == 15)

                        rowViews.setTextViewText(solarId, day.toString())
                        val lunarBase = if (info.lunarDay == 1 || day == 1) {
                            "${info.lunarDay}/${info.lunarMonth}"
                        } else {
                            info.lunarDay.toString()
                        }
                        val lunarDisplay = if (info.isHoangDaoDay && !isToday && !isMung1OrRam) {
                            "$lunarBase•"
                        } else {
                            lunarBase
                        }
                        rowViews.setTextViewText(lunarId, lunarDisplay)

                        val bgRes = when {
                            isToday -> R.drawable.widget_cell_today_bg
                            isMung1OrRam -> R.drawable.widget_cell_mung1_bg
                            else -> R.drawable.widget_cell_normal_bg
                        }
                        rowViews.setInt(cellId, "setBackgroundResource", bgRes)

                        val solarColor = when {
                            isToday -> 0xFFFFFFFF.toInt()
                            col == 6 -> if (isLightBg) 0xFFDC2626.toInt() else 0xFFFB7185.toInt()
                            col == 5 -> if (isLightBg) 0xFF0284C7.toInt() else 0xFF38BDF8.toInt()
                            isMung1OrRam -> if (isLightBg) 0xFFB45309.toInt() else 0xFFFDE68A.toInt()
                            else -> if (isLightBg) 0xFF0F172A.toInt() else 0xFFF8FAFC.toInt()
                        }
                        val lunarColor = when {
                            isToday -> 0xFFFEF08A.toInt()
                            isMung1OrRam -> if (isLightBg) 0xFFD97706.toInt() else 0xFFFBBF24.toInt()
                            info.isHoangDaoDay -> if (isLightBg) 0xFF15803D.toInt() else 0xFF86EFAC.toInt()
                            else -> if (isLightBg) 0xFF475569.toInt() else 0xFFCBD5E1.toInt()
                        }
                        rowViews.setTextColor(solarId, solarColor)
                        rowViews.setTextColor(lunarId, lunarColor)
                        rowViews.setTextViewTextSize(solarId, TypedValue.COMPLEX_UNIT_SP, 12.5f * s)
                        rowViews.setTextViewTextSize(lunarId, TypedValue.COMPLEX_UNIT_SP, 8.6f * s)
                        rowViews.setOnClickPendingIntent(cellId, openLunarToolPi)
                    } else {
                        rowViews.setTextViewText(solarId, "")
                        rowViews.setTextViewText(lunarId, "")
                        rowViews.setInt(cellId, "setBackgroundResource", R.drawable.widget_cell_normal_bg)
                    }
                }
                views.addView(R.id.widget_lunar_grid_container, rowViews)
            }

            // 2. Cập nhật Thanh Chân Trang bằng Vector TextView sắc nét
            views.setTextViewText(
                R.id.widget_lunar_footer_canchi,
                "${todayInfo.zodiacAnimalEmoji} Ngày ${todayInfo.dayCanChi} • Tháng ${todayInfo.monthCanChi} • Năm ${todayInfo.yearCanChi}"
            )
            views.setTextViewText(
                R.id.widget_lunar_footer_hours,
                "✨ Giờ tốt: " + todayInfo.auspiciousHours.take(4).joinToString(", ")
            )
            views.setTextColor(
                R.id.widget_lunar_footer_canchi,
                if (isLightBg) 0xFF15803D.toInt() else 0xFF4ADE80.toInt()
            )
            views.setTextColor(
                R.id.widget_lunar_footer_hours,
                if (isLightBg) 0xFF334155.toInt() else 0xFFE2E8F0.toInt()
            )
            views.setTextViewTextSize(R.id.widget_lunar_footer_canchi, TypedValue.COMPLEX_UNIT_SP, 10.5f * s)
            views.setTextViewTextSize(R.id.widget_lunar_footer_hours, TypedValue.COMPLEX_UNIT_SP, 9.8f * s)

            // Nút Tháng trước (◀)
            val prevIntent = Intent(context, LunarCalendarAppWidgetProvider::class.java).apply {
                action = ACTION_PREV_MONTH
            }
            views.setOnClickPendingIntent(
                R.id.widget_lunar_btn_prev,
                PendingIntent.getBroadcast(
                    context,
                    3001,
                    prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Nút Hôm nay
            val todayIntent = Intent(context, LunarCalendarAppWidgetProvider::class.java).apply {
                action = ACTION_TODAY_MONTH
            }
            views.setOnClickPendingIntent(
                R.id.widget_lunar_btn_today,
                PendingIntent.getBroadcast(
                    context,
                    3002,
                    todayIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Nút Tháng sau (▶)
            val nextIntent = Intent(context, LunarCalendarAppWidgetProvider::class.java).apply {
                action = ACTION_NEXT_MONTH
            }
            views.setOnClickPendingIntent(
                R.id.widget_lunar_btn_next,
                PendingIntent.getBroadcast(
                    context,
                    3003,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Chạm vào tiêu đề hoặc chân trang -> Mở công cụ Lịch Âm - Dương trong App
            views.setOnClickPendingIntent(R.id.widget_lunar_header_click, openLunarToolPi)
            views.setOnClickPendingIntent(R.id.widget_lunar_footer_box, openLunarToolPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private const val SHORTCUT_ID_LUNAR = "lunar_calendar_live_shortcut"

        fun refreshAllLunarWidgets(context: Context) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx)
            val ids = manager.getAppWidgetIds(ComponentName(appCtx, LunarCalendarAppWidgetProvider::class.java))
            ids.forEach { id -> updateSingleLunarWidget(appCtx, manager, id) }
            val blocIds = manager.getAppWidgetIds(ComponentName(appCtx, LunarDayBlocAppWidgetProvider::class.java))
            blocIds.forEach { id -> LunarDayBlocAppWidgetProvider.updateSingleBlocWidget(appCtx, manager, id) }
            val stripIds = manager.getAppWidgetIds(ComponentName(appCtx, LunarCompactStripAppWidgetProvider::class.java))
            stripIds.forEach { id -> LunarCompactStripAppWidgetProvider.updateSingleStripWidget(appCtx, manager, id) }
            val lwIds = manager.getAppWidgetIds(ComponentName(appCtx, LunarWeatherAppWidgetProvider::class.java))
            lwIds.forEach { id -> LunarWeatherAppWidgetProvider.updateSingleLunarWeatherWidget(appCtx, manager, id) }
            updateDynamicLunarShortcutIfPinned(appCtx)
        }

        fun getActiveWidgetCount(context: Context): Int {
            return runCatching {
                val appCtx = context.applicationContext
                val manager = AppWidgetManager.getInstance(appCtx)
                manager.getAppWidgetIds(ComponentName(appCtx, LunarCalendarAppWidgetProvider::class.java)).size +
                    manager.getAppWidgetIds(ComponentName(appCtx, LunarDayBlocAppWidgetProvider::class.java)).size +
                    manager.getAppWidgetIds(ComponentName(appCtx, LunarCompactStripAppWidgetProvider::class.java)).size +
                    manager.getAppWidgetIds(ComponentName(appCtx, LunarWeatherAppWidgetProvider::class.java)).size
            }.getOrDefault(0)
        }

        fun isLunarShortcutPinned(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return runCatching {
                val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
                sm.pinnedShortcuts.any { it.id == SHORTCUT_ID_LUNAR }
            }.getOrDefault(false)
        }

        private fun buildLiveLunarShortcutInfo(context: Context): ShortcutInfo? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
            val todayCal = Calendar.getInstance()
            val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
            val todayMonth = todayCal.get(Calendar.MONTH) + 1
            val todayYear = todayCal.get(Calendar.YEAR)
            val todayInfo = VietnameseLunarCalendar.getFullDayInfo(todayDay, todayMonth, todayYear)

            val iconBmp = VietnameseLunarCalendar.renderLiveShortcutIconBitmap()
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("OPEN_TOOL_ID", "lunar_calendar")
            }
            val shortLabel = String.format("Lịch Âm %02d/%02d", todayInfo.lunarDay, todayInfo.lunarMonth)
            val longLabel = String.format(
                "Lịch Âm %02d/%02d (%02d/%02d DL)",
                todayInfo.lunarDay,
                todayInfo.lunarMonth,
                todayDay,
                todayMonth
            )
            return ShortcutInfo.Builder(context, SHORTCUT_ID_LUNAR)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(Icon.createWithBitmap(iconBmp))
                .setIntent(launchIntent)
                .build()
        }

        fun updateDynamicLunarShortcutIfPinned(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            runCatching {
                val sm = context.getSystemService(ShortcutManager::class.java) ?: return
                if (sm.pinnedShortcuts.any { it.id == SHORTCUT_ID_LUNAR }) {
                    val info = buildLiveLunarShortcutInfo(context) ?: return
                    sm.updateShortcuts(listOf(info))
                }
            }
        }

        /**
         * Ghim Tờ Lịch Âm - Dương Động (biểu tượng hiển thị trực tiếp ngày Âm/Dương hôm nay)
         * ra Màn hình chính thông qua quyền "Tạo lối tắt trên màn hình chính" (ShortcutManager + INSTALL_SHORTCUT).
         */
        fun pinLunarShortcutToHomeScreen(context: Context): Boolean {
            var pinnedViaShortcutManager = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val sm = context.getSystemService(ShortcutManager::class.java)
                val info = buildLiveLunarShortcutInfo(context)
                if (sm != null && info != null) {
                    runCatching { sm.updateShortcuts(listOf(info)) }
                    if (sm.isRequestPinShortcutSupported && !sm.pinnedShortcuts.any { it.id == SHORTCUT_ID_LUNAR }) {
                        val successIntent = Intent(context, ToolsHubAppWidgetProvider::class.java).apply {
                            action = ToolsHubAppWidgetProvider.ACTION_PIN_WIDGET_SUCCESS
                            putExtra(ToolsHubAppWidgetProvider.EXTRA_WIDGET_LABEL, "Lịch Âm - Dương")
                        }
                        val callback = PendingIntent.getBroadcast(
                            context,
                            SHORTCUT_ID_LUNAR.hashCode(),
                            successIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        pinnedViaShortcutManager = runCatching {
                            sm.requestPinShortcut(info, callback.intentSender)
                        }.getOrDefault(false)
                    } else if (sm.pinnedShortcuts.any { it.id == SHORTCUT_ID_LUNAR }) {
                        pinnedViaShortcutManager = true
                    }
                }
            }
            // Gửi đồng thời broadcast INSTALL_SHORTCUT cho các Launcher OEM (Vivo OriginOS / FuntouchOS / MIUI)
            val broadcastOk = runCatching {
                val todayCal = Calendar.getInstance()
                val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
                val todayMonth = todayCal.get(Calendar.MONTH) + 1
                val todayYear = todayCal.get(Calendar.YEAR)
                val todayInfo = VietnameseLunarCalendar.getFullDayInfo(todayDay, todayMonth, todayYear)
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("OPEN_TOOL_ID", "lunar_calendar")
                }
                @Suppress("DEPRECATION")
                val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                    putExtra("duplicate", false)
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, String.format("Lịch Âm %02d/%02d", todayInfo.lunarDay, todayInfo.lunarMonth))
                    putExtra(Intent.EXTRA_SHORTCUT_ICON, VietnameseLunarCalendar.renderLiveShortcutIconBitmap())
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                }
                context.sendBroadcast(addIntent)
                true
            }.getOrDefault(false)
            return pinnedViaShortcutManager || broadcastOk
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleLunarWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences(PREF_WIDGET, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_PREV_MONTH -> {
                val cur = prefs.getInt(KEY_OFFSET_MONTHS, 0)
                prefs.edit().putInt(KEY_OFFSET_MONTHS, (cur - 1).coerceIn(-120, 120)).apply()
                refreshAllLunarWidgets(context)
            }
            ACTION_NEXT_MONTH -> {
                val cur = prefs.getInt(KEY_OFFSET_MONTHS, 0)
                prefs.edit().putInt(KEY_OFFSET_MONTHS, (cur + 1).coerceIn(-120, 120)).apply()
                refreshAllLunarWidgets(context)
            }
            ACTION_TODAY_MONTH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                prefs.edit().putInt(KEY_OFFSET_MONTHS, 0).apply()
                refreshAllLunarWidgets(context)
            }
        }
    }
}

/**
 * Kiểu Widget 2: Tờ Lịch Bloc Hôm Nay (2x2 - Chữ Cực To Rõ Ràng cho Người Lớn Tuổi / Bố Mẹ).
 */
class LunarDayBlocAppWidgetProvider : AppWidgetProvider() {

    companion object {
        internal fun updateSingleBlocWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val style = LunarWidgetPrefs.getStyle(context)
            val s = LunarWidgetPrefs.getFontSize(context).scale
            val isLightBg = (style == LunarWidgetStyle.LIGHT || style == LunarWidgetStyle.BLOC)

            val todayCal = Calendar.getInstance()
            val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
            val todayMonth = todayCal.get(Calendar.MONTH) + 1
            val todayYear = todayCal.get(Calendar.YEAR)
            val info = VietnameseLunarCalendar.getFullDayInfo(todayDay, todayMonth, todayYear)

            val views = RemoteViews(context.packageName, R.layout.widget_lunar_bloc_day)

            val bgRes = when (style) {
                LunarWidgetStyle.DARK -> R.drawable.widget_lunar_card_bg
                LunarWidgetStyle.GLASS -> R.drawable.widget_lunar_card_glass
                LunarWidgetStyle.LIGHT -> R.drawable.widget_lunar_card_light
                LunarWidgetStyle.BLOC -> R.drawable.widget_lunar_card_bloc
            }
            views.setInt(R.id.widget_bloc_root, "setBackgroundResource", bgRes)

            val topColor = when (style) {
                LunarWidgetStyle.BLOC -> 0xFFDC2626.toInt()
                LunarWidgetStyle.LIGHT -> 0xFF0284C7.toInt()
                else -> 0xFF38BDF8.toInt()
            }
            val dayColor = if (isLightBg) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt()
            val lunarColor = if (isLightBg) 0xFFB45309.toInt() else 0xFFFBBF24.toInt()
            val canChiColor = if (isLightBg) 0xFF15803D.toInt() else 0xFF4ADE80.toInt()
            val hoursColor = if (isLightBg) 0xFF475569.toInt() else 0xFFCBD5E1.toInt()

            views.setTextColor(R.id.widget_bloc_top_header, topColor)
            views.setTextColor(R.id.widget_bloc_solar_day, dayColor)
            views.setTextColor(R.id.widget_bloc_lunar_date, lunarColor)
            views.setTextColor(R.id.widget_bloc_canchi, canChiColor)
            views.setTextColor(R.id.widget_bloc_good_hours, hoursColor)

            views.setTextViewTextSize(R.id.widget_bloc_top_header, TypedValue.COMPLEX_UNIT_SP, 11.5f * s)
            views.setTextViewTextSize(R.id.widget_bloc_solar_day, TypedValue.COMPLEX_UNIT_SP, 44f * s)
            views.setTextViewTextSize(R.id.widget_bloc_lunar_date, TypedValue.COMPLEX_UNIT_SP, 12.5f * s)
            views.setTextViewTextSize(R.id.widget_bloc_canchi, TypedValue.COMPLEX_UNIT_SP, 11f * s)
            views.setTextViewTextSize(R.id.widget_bloc_good_hours, TypedValue.COMPLEX_UNIT_SP, 10f * s)

            views.setTextViewText(
                R.id.widget_bloc_top_header,
                "${info.dayOfWeekLabel.uppercase()} • THÁNG $todayMonth/$todayYear"
            )
            views.setTextViewText(R.id.widget_bloc_solar_day, String.format("%02d", todayDay))
            views.setTextViewText(
                R.id.widget_bloc_lunar_date,
                String.format("Âm lịch: %02d/%02d (%s)", info.lunarDay, info.lunarMonth, info.monthCanChi)
            )
            views.setTextViewText(
                R.id.widget_bloc_canchi,
                "${info.zodiacAnimalEmoji} Ngày ${info.dayCanChi} • ${if (info.isHoangDaoDay) "Hoàng Đạo" else info.starName}"
            )
            views.setTextViewText(
                R.id.widget_bloc_good_hours,
                "Giờ tốt: " + info.auspiciousHours.take(3).joinToString(", ")
            )

            val openPi = ToolsHubAppWidgetProvider.buildOpenToolPendingIntent(context, 3101, "lunar_calendar")
            views.setOnClickPendingIntent(R.id.widget_bloc_root, openPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateSingleBlocWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
            }
        }
    }
}

/**
 * Kiểu Widget 3: Thanh Lịch Âm Hôm Nay Gọn (4x1 - Tối giản cho Dân Văn Phòng / Giới Trẻ).
 */
class LunarCompactStripAppWidgetProvider : AppWidgetProvider() {

    companion object {
        internal fun updateSingleStripWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val style = LunarWidgetPrefs.getStyle(context)
            val s = LunarWidgetPrefs.getFontSize(context).scale
            val isLightBg = (style == LunarWidgetStyle.LIGHT || style == LunarWidgetStyle.BLOC)

            val todayCal = Calendar.getInstance()
            val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
            val todayMonth = todayCal.get(Calendar.MONTH) + 1
            val todayYear = todayCal.get(Calendar.YEAR)
            val info = VietnameseLunarCalendar.getFullDayInfo(todayDay, todayMonth, todayYear)

            val views = RemoteViews(context.packageName, R.layout.widget_lunar_compact_strip)

            val bgRes = when (style) {
                LunarWidgetStyle.DARK -> R.drawable.widget_lunar_card_bg
                LunarWidgetStyle.GLASS -> R.drawable.widget_lunar_card_glass
                LunarWidgetStyle.LIGHT -> R.drawable.widget_lunar_card_light
                LunarWidgetStyle.BLOC -> R.drawable.widget_lunar_card_bloc
            }
            views.setInt(R.id.widget_compact_root, "setBackgroundResource", bgRes)

            val lunarColor = if (isLightBg) 0xFFB45309.toInt() else 0xFFFBBF24.toInt()
            val canChiColor = if (isLightBg) 0xFF15803D.toInt() else 0xFF4ADE80.toInt()
            val hoursColor = if (isLightBg) 0xFF334155.toInt() else 0xFFCBD5E1.toInt()

            views.setTextColor(R.id.widget_compact_lunar_title, lunarColor)
            views.setTextColor(R.id.widget_compact_canchi, canChiColor)
            views.setTextColor(R.id.widget_compact_hours, hoursColor)

            views.setTextViewTextSize(R.id.widget_compact_dow, TypedValue.COMPLEX_UNIT_SP, 10f * s)
            views.setTextViewTextSize(R.id.widget_compact_solar_day, TypedValue.COMPLEX_UNIT_SP, 22f * s)
            views.setTextViewTextSize(R.id.widget_compact_lunar_title, TypedValue.COMPLEX_UNIT_SP, 13f * s)
            views.setTextViewTextSize(R.id.widget_compact_canchi, TypedValue.COMPLEX_UNIT_SP, 11f * s)
            views.setTextViewTextSize(R.id.widget_compact_hours, TypedValue.COMPLEX_UNIT_SP, 10.5f * s)

            val shortDow = when (todayCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "CN"
                Calendar.MONDAY -> "T2"
                Calendar.TUESDAY -> "T3"
                Calendar.WEDNESDAY -> "T4"
                Calendar.THURSDAY -> "T5"
                Calendar.FRIDAY -> "T6"
                else -> "T7"
            }
            views.setTextViewText(R.id.widget_compact_dow, shortDow)
            views.setTextViewText(R.id.widget_compact_solar_day, String.format("%02d", todayDay))
            views.setTextViewText(
                R.id.widget_compact_lunar_title,
                String.format("Âm lịch: %02d/%02d • Tháng %d/%d", info.lunarDay, info.lunarMonth, todayMonth, todayYear)
            )
            views.setTextViewText(
                R.id.widget_compact_canchi,
                "${info.zodiacAnimalEmoji} Ngày ${info.dayCanChi} • Năm ${info.yearCanChi} (${if (info.isHoangDaoDay) "Hoàng Đạo" else info.starName})"
            )
            views.setTextViewText(
                R.id.widget_compact_hours,
                "✨ Giờ tốt: " + info.auspiciousHours.take(3).joinToString(", ")
            )

            val openPi = ToolsHubAppWidgetProvider.buildOpenToolPendingIntent(context, 3201, "lunar_calendar")
            views.setOnClickPendingIntent(R.id.widget_compact_root, openPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateSingleStripWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
            }
        }
    }
}

/**
 * Kiểu Widget 4 (MỚI): Tiện Ích Tích Hợp Thời Tiết + Lịch Âm Dương (4x3).
 * - Phần trên hiển thị Thời tiết hiện tại (chạm để mở thẳng Trang Thời Tiết).
 * - Phần dưới là bảng Lịch Âm - Dương Tháng đầy đủ như cũ.
 * - Background toàn Widget tự động thay đổi theo đúng thời tiết hiện tại.
 */
class LunarWeatherAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WEATHER = "com.vunameaut.powertimer.widget.ACTION_LW_REFRESH_WEATHER"
        const val ACTION_PREV_MONTH = "com.vunameaut.powertimer.widget.ACTION_LW_PREV_MONTH"
        const val ACTION_NEXT_MONTH = "com.vunameaut.powertimer.widget.ACTION_LW_NEXT_MONTH"
        const val ACTION_TODAY_MONTH = "com.vunameaut.powertimer.widget.ACTION_LW_TODAY_MONTH"
        private const val PREF_LW = "lunar_weather_widget_prefs"
        private const val KEY_OFFSET_MONTHS = "viewing_month_offset"

        private val CELL_IDS = intArrayOf(
            R.id.cell_0, R.id.cell_1, R.id.cell_2, R.id.cell_3, R.id.cell_4, R.id.cell_5, R.id.cell_6
        )
        private val SOLAR_IDS = intArrayOf(
            R.id.solar_0, R.id.solar_1, R.id.solar_2, R.id.solar_3, R.id.solar_4, R.id.solar_5, R.id.solar_6
        )
        private val LUNAR_IDS = intArrayOf(
            R.id.lunar_0, R.id.lunar_1, R.id.lunar_2, R.id.lunar_3, R.id.lunar_4, R.id.lunar_5, R.id.lunar_6
        )
        private val DOW_IDS = intArrayOf(
            R.id.widget_lw_dow_0, R.id.widget_lw_dow_1, R.id.widget_lw_dow_2,
            R.id.widget_lw_dow_3, R.id.widget_lw_dow_4, R.id.widget_lw_dow_5, R.id.widget_lw_dow_6
        )

        fun backgroundResForAtmosphere(atmosphere: WeatherAtmosphere): Int {
            return when (atmosphere) {
                WeatherAtmosphere.CLEAR_DAY -> R.drawable.widget_weather_bg_clear_day
                WeatherAtmosphere.HOT_SUNNY -> R.drawable.widget_weather_bg_hot
                WeatherAtmosphere.CLEAR_NIGHT -> R.drawable.widget_weather_bg_clear_night
                WeatherAtmosphere.PARTLY_CLOUDY -> R.drawable.widget_weather_bg_partly_cloudy
                WeatherAtmosphere.CLOUDY -> R.drawable.widget_weather_bg_cloudy
                WeatherAtmosphere.FOG -> R.drawable.widget_weather_bg_fog
                WeatherAtmosphere.RAIN -> R.drawable.widget_weather_bg_rain
                WeatherAtmosphere.STORM -> R.drawable.widget_weather_bg_storm
            }
        }

        internal fun updateSingleLunarWeatherWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREF_LW, Context.MODE_PRIVATE)
            val monthOffset = prefs.getInt(KEY_OFFSET_MONTHS, 0)
            val s = LunarWidgetPrefs.getFontSize(context).scale
            val weather = WeatherManager.getCachedOrDefaultSnapshot(context)

            val todayCal = Calendar.getInstance()
            val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
            val todayMonth = todayCal.get(Calendar.MONTH) + 1
            val todayYear = todayCal.get(Calendar.YEAR)
            val todayInfo = VietnameseLunarCalendar.getFullDayInfo(todayDay, todayMonth, todayYear)

            val viewCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, monthOffset)
            }
            val viewMonth = viewCal.get(Calendar.MONTH) + 1
            val viewYear = viewCal.get(Calendar.YEAR)
            val midMonthLunar = VietnameseLunarCalendar.getFullDayInfo(15, viewMonth, viewYear)

            val views = RemoteViews(context.packageName, R.layout.widget_lunar_weather)

            // 1. Background động theo đúng thời tiết hiện tại
            views.setInt(R.id.widget_lunar_weather_root, "setBackgroundResource", backgroundResForAtmosphere(weather.atmosphere))

            // 2. Phần Thời Tiết phía trên
            views.setTextViewText(R.id.widget_weather_emoji, weather.emoji)
            views.setTextViewText(R.id.widget_weather_temp, "${weather.tempC}°")
            views.setTextViewText(R.id.widget_weather_city_cond, "${weather.cityName} • ${weather.conditionText}")
            views.setTextViewText(
                R.id.widget_weather_details,
                "${weather.minTodayC}°/${weather.maxTodayC}° • 💧${weather.humidityPct}% • 💨${weather.windKmh}km/h"
            )
            views.setTextViewTextSize(R.id.widget_weather_temp, TypedValue.COMPLEX_UNIT_SP, 24f * s)
            views.setTextViewTextSize(R.id.widget_weather_city_cond, TypedValue.COMPLEX_UNIT_SP, 12f * s)
            views.setTextViewTextSize(R.id.widget_weather_details, TypedValue.COMPLEX_UNIT_SP, 10f * s)

            // Chạm vào phần Thời Tiết -> Mở thẳng Trang Thời Tiết hiện tại
            val openWeatherPi = ToolsHubAppWidgetProvider.buildOpenToolPendingIntent(context, 3301, "weather_forecast")
            views.setOnClickPendingIntent(R.id.widget_weather_header_card, openWeatherPi)

            // Nút làm mới thời tiết trên Widget
            val refreshIntent = Intent(context, LunarWeatherAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WEATHER
            }
            views.setOnClickPendingIntent(
                R.id.widget_weather_btn_refresh,
                PendingIntent.getBroadcast(
                    context,
                    3302,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // 3. Phần Lịch Âm - Dương phía dưới như cũ
            views.setTextViewText(
                R.id.widget_lw_month_title,
                "🗓️ Tháng $viewMonth / $viewYear • Năm ${midMonthLunar.yearCanChi}"
            )
            views.setTextViewText(
                R.id.widget_lw_today_subtitle,
                String.format(
                    "Hôm nay: %02d/%02d DL • %02d/%02d ÂL (%s)",
                    todayDay,
                    todayMonth,
                    todayInfo.lunarDay,
                    todayInfo.lunarMonth,
                    if (todayInfo.isHoangDaoDay) "Hoàng Đạo 🟢" else todayInfo.dayCanChi
                )
            )
            views.setTextViewTextSize(R.id.widget_lw_month_title, TypedValue.COMPLEX_UNIT_SP, 12.5f * s)
            views.setTextViewTextSize(R.id.widget_lw_today_subtitle, TypedValue.COMPLEX_UNIT_SP, 10f * s)
            views.setTextViewTextSize(R.id.widget_lw_btn_today, TypedValue.COMPLEX_UNIT_SP, 9.5f * s)

            DOW_IDS.forEach { dowId ->
                views.setTextViewTextSize(dowId, TypedValue.COMPLEX_UNIT_SP, 10f * s)
            }

            val openCalendarPi = ToolsHubAppWidgetProvider.buildOpenToolPendingIntent(context, 3303, "lunar_calendar")
            views.removeAllViews(R.id.widget_lw_grid_container)

            val monthCal = Calendar.getInstance().apply {
                set(viewYear, viewMonth - 1, 1, 12, 0, 0)
            }
            val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDow = monthCal.get(Calendar.DAY_OF_WEEK)
            val leadingEmpty = if (firstDow == Calendar.SUNDAY) 6 else (firstDow - Calendar.MONDAY)
            val totalCells = leadingEmpty + daysInMonth
            val numRows = if (totalCells > 35) 6 else 5

            for (row in 0 until numRows) {
                val rowViews = RemoteViews(context.packageName, R.layout.widget_lunar_row)
                for (col in 0..6) {
                    val cellIdx = row * 7 + col
                    val day = cellIdx - leadingEmpty + 1
                    val cellId = CELL_IDS[col]
                    val solarId = SOLAR_IDS[col]
                    val lunarId = LUNAR_IDS[col]

                    if (day in 1..daysInMonth) {
                        val info = VietnameseLunarCalendar.getFullDayInfo(day, viewMonth, viewYear)
                        val isToday = (day == todayDay && viewMonth == todayMonth && viewYear == todayYear)
                        val isMung1OrRam = (info.lunarDay == 1 || info.lunarDay == 15)

                        rowViews.setTextViewText(solarId, day.toString())
                        val lunarBase = if (info.lunarDay == 1 || day == 1) "${info.lunarDay}/${info.lunarMonth}" else info.lunarDay.toString()
                        val lunarDisplay = if (info.isHoangDaoDay && !isToday && !isMung1OrRam) "$lunarBase•" else lunarBase
                        rowViews.setTextViewText(lunarId, lunarDisplay)

                        val bgRes = when {
                            isToday -> R.drawable.widget_cell_today_bg
                            isMung1OrRam -> R.drawable.widget_cell_mung1_bg
                            else -> R.drawable.widget_cell_normal_bg
                        }
                        rowViews.setInt(cellId, "setBackgroundResource", bgRes)

                        val solarColor = when {
                            isToday -> 0xFFFFFFFF.toInt()
                            col == 6 -> 0xFFFDA4AF.toInt()
                            col == 5 -> 0xFF7DD3FC.toInt()
                            isMung1OrRam -> 0xFFFDE68A.toInt()
                            else -> 0xFFF8FAFC.toInt()
                        }
                        val lunarColor = when {
                            isToday -> 0xFFFEF08A.toInt()
                            isMung1OrRam -> 0xFFFBBF24.toInt()
                            info.isHoangDaoDay -> 0xFF86EFAC.toInt()
                            else -> 0xFFE2E8F0.toInt()
                        }
                        rowViews.setTextColor(solarId, solarColor)
                        rowViews.setTextColor(lunarId, lunarColor)
                        rowViews.setTextViewTextSize(solarId, TypedValue.COMPLEX_UNIT_SP, 12f * s)
                        rowViews.setTextViewTextSize(lunarId, TypedValue.COMPLEX_UNIT_SP, 8.2f * s)
                        rowViews.setOnClickPendingIntent(cellId, openCalendarPi)
                    } else {
                        rowViews.setTextViewText(solarId, "")
                        rowViews.setTextViewText(lunarId, "")
                        rowViews.setInt(cellId, "setBackgroundResource", R.drawable.widget_cell_normal_bg)
                    }
                }
                views.addView(R.id.widget_lw_grid_container, rowViews)
            }

            views.setTextViewText(
                R.id.widget_lw_footer_canchi,
                "${todayInfo.zodiacAnimalEmoji} Ngày ${todayInfo.dayCanChi} • Tháng ${todayInfo.monthCanChi} • Năm ${todayInfo.yearCanChi}"
            )
            views.setTextViewText(
                R.id.widget_lw_footer_hours,
                "✨ Giờ tốt: " + todayInfo.auspiciousHours.take(4).joinToString(", ")
            )
            views.setTextViewTextSize(R.id.widget_lw_footer_canchi, TypedValue.COMPLEX_UNIT_SP, 10f * s)
            views.setTextViewTextSize(R.id.widget_lw_footer_hours, TypedValue.COMPLEX_UNIT_SP, 9.2f * s)

            // Nút chuyển tháng
            views.setOnClickPendingIntent(
                R.id.widget_lw_btn_prev,
                PendingIntent.getBroadcast(
                    context,
                    3304,
                    Intent(context, LunarWeatherAppWidgetProvider::class.java).apply { action = ACTION_PREV_MONTH },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_lw_btn_today,
                PendingIntent.getBroadcast(
                    context,
                    3305,
                    Intent(context, LunarWeatherAppWidgetProvider::class.java).apply { action = ACTION_TODAY_MONTH },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_lw_btn_next,
                PendingIntent.getBroadcast(
                    context,
                    3306,
                    Intent(context, LunarWeatherAppWidgetProvider::class.java).apply { action = ACTION_NEXT_MONTH },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            views.setOnClickPendingIntent(R.id.widget_lw_calendar_header_click, openCalendarPi)
            views.setOnClickPendingIntent(R.id.widget_lw_footer_box, openCalendarPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateSingleLunarWeatherWidget(context, appWidgetManager, id) }
        // Tự động cập nhật lại thời tiết nền nếu cache đã quá 15 phút
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WeatherManager.refreshWeather(context.applicationContext, forceNetwork = false)
                LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences(PREF_LW, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_REFRESH_WEATHER -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WeatherManager.refreshWeather(context.applicationContext, forceNetwork = true)
                        LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context.applicationContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_PREV_MONTH -> {
                val cur = prefs.getInt(KEY_OFFSET_MONTHS, 0)
                prefs.edit().putInt(KEY_OFFSET_MONTHS, (cur - 1).coerceIn(-120, 120)).apply()
                LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
            }
            ACTION_NEXT_MONTH -> {
                val cur = prefs.getInt(KEY_OFFSET_MONTHS, 0)
                prefs.edit().putInt(KEY_OFFSET_MONTHS, (cur + 1).coerceIn(-120, 120)).apply()
                LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
            }
            ACTION_TODAY_MONTH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                prefs.edit().putInt(KEY_OFFSET_MONTHS, 0).apply()
                LunarCalendarAppWidgetProvider.refreshAllLunarWidgets(context)
            }
        }
    }
}
