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
import android.widget.RemoteViews
import android.widget.Toast
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.qr.VietQrHelper
import com.vunameaut.powertimer.service.QuickTileActions
import com.vunameaut.powertimer.service.TimerForegroundService
import com.vunameaut.powertimer.ui.screens.VietnameseLunarCalendar
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
                        mode = ShutdownMode.BANK_SAFE_SLEEP
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
 * Tiện ích (AppWidget) Lịch Âm - Dương Tháng ngoài Màn hình chính (tương tác chuyển tháng giống Google Calendar).
 */
class LunarCalendarAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PREV_MONTH = "com.vunameaut.powertimer.widget.ACTION_LUNAR_PREV_MONTH"
        const val ACTION_NEXT_MONTH = "com.vunameaut.powertimer.widget.ACTION_LUNAR_NEXT_MONTH"
        const val ACTION_TODAY_MONTH = "com.vunameaut.powertimer.widget.ACTION_LUNAR_TODAY_MONTH"
        private const val PREF_WIDGET = "lunar_calendar_widget_prefs"
        private const val KEY_OFFSET_MONTHS = "viewing_month_offset"

        internal fun updateSingleLunarWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREF_WIDGET, Context.MODE_PRIVATE)
            val monthOffset = prefs.getInt(KEY_OFFSET_MONTHS, 0)

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
            views.setTextViewText(
                R.id.widget_lunar_month_title,
                "🗓️ THÁNG $viewMonth / $viewYear • Năm ${midMonthLunar.yearCanChi}"
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

            val gridBmp = VietnameseLunarCalendar.renderMonthWidgetBitmap(viewYear, viewMonth)
            views.setImageViewBitmap(R.id.widget_lunar_grid_image, gridBmp)

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

            // Chạm vào tiêu đề hoặc bảng lịch -> Mở công cụ Lịch Âm - Dương trong App
            val openLunarToolPi = ToolsHubAppWidgetProvider.buildOpenToolPendingIntent(
                context,
                3004,
                "lunar_calendar"
            )
            views.setOnClickPendingIntent(R.id.widget_lunar_header_click, openLunarToolPi)
            views.setOnClickPendingIntent(R.id.widget_lunar_grid_image, openLunarToolPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private const val SHORTCUT_ID_LUNAR = "lunar_calendar_live_shortcut"

        fun refreshAllLunarWidgets(context: Context) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx)
            val ids = manager.getAppWidgetIds(ComponentName(appCtx, LunarCalendarAppWidgetProvider::class.java))
            ids.forEach { id -> updateSingleLunarWidget(appCtx, manager, id) }
            updateDynamicLunarShortcutIfPinned(appCtx)
        }

        fun getActiveWidgetCount(context: Context): Int {
            return runCatching {
                val appCtx = context.applicationContext
                val manager = AppWidgetManager.getInstance(appCtx)
                manager.getAppWidgetIds(ComponentName(appCtx, LunarCalendarAppWidgetProvider::class.java)).size
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val sm = context.getSystemService(ShortcutManager::class.java)
                val info = buildLiveLunarShortcutInfo(context)
                if (sm != null && info != null) {
                    runCatching { sm.updateShortcuts(listOf(info)) }
                    if (sm.pinnedShortcuts.any { it.id == SHORTCUT_ID_LUNAR }) {
                        return true
                    }
                    if (sm.isRequestPinShortcutSupported) {
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
                        val requested = runCatching {
                            sm.requestPinShortcut(info, callback.intentSender)
                        }.getOrDefault(false)
                        if (requested) return true
                    }
                }
            }
            return runCatching {
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
