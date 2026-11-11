package com.vunameaut.powertimer.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.data.model.ShutdownMode

object QuickTileActions {
    const val EXTRA_QUICK_ACTION = "com.vunameaut.powertimer.EXTRA_QUICK_ACTION"
    const val ACTION_OPEN_QR_SCANNER = "ACTION_OPEN_QR_SCANNER"
    const val ACTION_OPEN_QUICK_OCR = "ACTION_OPEN_QUICK_OCR"
    const val ACTION_SHOW_MY_VIETQR = "ACTION_SHOW_MY_VIETQR"
    const val ACTION_DOWNLOAD_CLIPBOARD_VIDEO = "ACTION_DOWNLOAD_CLIPBOARD_VIDEO"

    @SuppressLint("WrongConstant")
    fun launchMainActivityFromTile(tileService: TileService, quickAction: String) {
        val intent = Intent(tileService, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_QUICK_ACTION, quickAction)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                tileService,
                quickAction.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            tileService.startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            tileService.startActivityAndCollapse(intent)
        }
    }

    /**
     * Gợi ý thêm nhanh ô Quick Settings lên thanh trạng thái (Hỗ trợ Android 13 / API 33+).
     */
    fun requestAddTileToStatusBar(
        context: Context,
        serviceClass: Class<out TileService>,
        tileLabel: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            if (statusBarManager != null) {
                statusBarManager.requestAddTileService(
                    ComponentName(context, serviceClass),
                    tileLabel,
                    Icon.createWithResource(context, R.mipmap.ic_launcher),
                    context.mainExecutor
                ) { result ->
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Toast.makeText(
                            context,
                            "Nút \"$tileLabel\" đã có sẵn trên thanh trạng thái của bạn!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        Toast.makeText(
                            context,
                            "Đã thêm \"$tileLabel\" lên thanh trạng thái!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return
            }
        }
        Toast.makeText(
            context,
            "Hãy kéo thanh thông báo xuống -> Bấm biểu tượng Cây bút (Chỉnh sửa) -> Kéo ô \"$tileLabel\" vào bảng nút nhanh!",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Ô Quick Settings 1: Bật / Tắt nhanh Hẹn giờ tắt màn hình (30 phút - Chuẩn Ngân Hàng).
 */
class SleepTimerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val current = TimerForegroundService.timerFlow.value
        if (current.isRunning) {
            TimerForegroundService.stopTimer(applicationContext)
            Toast.makeText(applicationContext, "Đã tắt hẹn giờ màn hình!", Toast.LENGTH_SHORT).show()
            updateTileUi(isRunning = false, subtitle = "Đã tắt")
        } else {
            TimerForegroundService.startTimer(
                context = applicationContext,
                seconds = 30 * 60L,
                mode = ShutdownMode.BANK_SAFE_SLEEP
            )
            Toast.makeText(applicationContext, "Đã bật hẹn giờ tắt màn hình 30 phút!", Toast.LENGTH_SHORT).show()
            updateTileUi(isRunning = true, subtitle = "Đang chạy 30p")
        }
    }

    private fun syncTileState() {
        val current = TimerForegroundService.timerFlow.value
        updateTileUi(
            isRunning = current.isRunning,
            subtitle = if (current.isRunning) "Còn ${current.formattedRemaining}" else "Chạm bật 30p"
        )
    }

    private fun updateTileUi(isRunning: Boolean, subtitle: String) {
        val tile = qsTile ?: return
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Hẹn giờ Màn hình"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }
}

/**
 * Ô Quick Settings 2: Mở thẳng màn hình Quét QR / Quick OCR.
 */
class ScannerOcrTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Quét QR / OCR"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Mở nhanh máy quét"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        QuickTileActions.launchMainActivityFromTile(this, QuickTileActions.ACTION_OPEN_QR_SCANNER)
    }
}

/**
 * Ô Quick Settings 3: Bật ngay thẻ Mã VietQR cá nhân để nhận tiền chuyển khoản.
 */
class MyVietQrTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Mã VietQR Của Tôi"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Hiện QR nhận tiền"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        QuickTileActions.launchMainActivityFromTile(this, QuickTileActions.ACTION_SHOW_MY_VIETQR)
    }
}

/**
 * Ô Quick Settings 4: Tải ngay Video từ đường link đang copy trong Clipboard.
 */
class ClipboardVideoTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Tải Video Clipboard"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Tải link vừa copy"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        QuickTileActions.launchMainActivityFromTile(this, QuickTileActions.ACTION_DOWNLOAD_CLIPBOARD_VIDEO)
    }
}

