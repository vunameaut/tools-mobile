package com.vunameaut.powertimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.qr.VietQrHelper
import com.vunameaut.powertimer.service.QuickTileActions
import com.vunameaut.powertimer.service.TimerForegroundService

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

        fun requestPinWidgetToHomeScreen(
            context: Context,
            providerClass: Class<out AppWidgetProvider>,
            label: String
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    val myProvider = ComponentName(context, providerClass)
                    val pinned = appWidgetManager.requestPinAppWidget(myProvider, null, null)
                    if (pinned) {
                        Toast.makeText(
                            context,
                            "Xác nhận để thêm tiện ích \"$label\" ra Màn hình chính!",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                }
            }
            Toast.makeText(
                context,
                "Nhấn giữ khoảng trống ngoài Màn hình chính -> Chọn Tiện ích (Widgets) -> Kéo \"Tools Hub\" ra ngoài!",
                Toast.LENGTH_LONG
            ).show()
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
        if (intent.action == ACTION_TOGGLE_TIMER) {
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

