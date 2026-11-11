package com.vunameaut.powertimer.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.data.model.ShutdownMode
import com.vunameaut.powertimer.data.model.TimerState
import com.vunameaut.powertimer.manager.ShutdownManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class IslandDisplayStyle(
    val id: String,
    val title: String,
    val subtitle: String
) {
    SPOTIFY_YOUTUBE(
        id = "SPOTIFY_YOUTUBE",
        title = "Kiểu YouTube / Spotify",
        subtitle = "Đĩa nhạc xoay bên trái + Đồng hồ đếm ngược + Sóng âm 5 cột bên phải"
    ),
    NEON_CYBER(
        id = "NEON_CYBER",
        title = "Kiểu Đồng Hồ Neon",
        subtitle = "Biểu tượng mặt trăng + Số đếm ngược trực tiếp + Viền Neon đổi màu"
    ),
    LIQUID_WAVE(
        id = "LIQUID_WAVE",
        title = "Kiểu Sóng Nước Đa Sắc",
        subtitle = "Dải sóng lượn mềm mại dưới chân giọt nước + Thời gian còn lại"
    ),
    MINIMAL_IOS(
        id = "MINIMAL_IOS",
        title = "Kiểu iOS Tối Giản",
        subtitle = "Viên thuốc đen tuyền ôm sát camera, hiển thị rõ số phút còn lại"
    );

    companion object {
        fun fromId(id: String?): IslandDisplayStyle {
            return entries.firstOrNull { it.id == id } ?: SPOTIFY_YOUTUBE
        }
    }
}

enum class IslandSizeMode {
    MINIMIZED, // Thu nhỏ siêu gọn ở góc/mép để không ảnh hưởng trải nghiệm dùng điện thoại
    COMPACT,   // Viên thuốc chuẩn quanh camera trước
    EXPANDED   // Bảng điều khiển mở rộng đầy đủ nút bấm
}

/**
 * Quản lý hiển thị Dynamic Island NỔI TOÀN HỆ THỐNG (ngoài màn hình chính & đè lên thanh trạng thái
 * của mọi ứng dụng khác giống YouTube / Spotify).
 *
 * Hỗ trợ:
 * - Hiển thị rõ thời gian còn lại (MM:SS) ở cả 4 kiểu giao diện và khi thu nhỏ.
 * - Mở nhanh lại App 1 chạm / chạm đúp từ bất kỳ màn hình nào (vượt rào cản Background Activity Launch).
 * - Thu nhỏ gọn (Minimized Mode) + Kéo thả tự do (Drag-to-move) để không bao giờ cản trở việc dùng điện thoại.
 */
object SystemDynamicIslandManager {

    private const val PREF_NAME = "waterdrop_island_prefs"
    private const val KEY_ENABLED = "always_show_waterdrop"
    private const val KEY_SHOW_OUTSIDE_APP = "show_island_outside_app"
    private const val KEY_STYLE = "island_display_style"
    private const val KEY_OFFSET_X_DP = "island_offset_x_dp"
    private const val KEY_OFFSET_Y_DP = "island_offset_y_dp"
    private const val KEY_MINIMIZED = "island_is_minimized"

    private var windowManager: WindowManager? = null
    private var floatingView: FloatingIslandCanvasView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAdded = false

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        refreshOverlay(context)
    }

    fun isShowOutsideApp(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_OUTSIDE_APP, true)
    }

    fun setShowOutsideApp(context: Context, showOutside: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_OUTSIDE_APP, showOutside)
            .apply()
        refreshOverlay(context)
    }

    fun isMinimized(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MINIMIZED, false)
    }

    fun setMinimized(context: Context, minimized: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MINIMIZED, minimized)
            .apply()
        floatingView?.setSizeMode(if (minimized) IslandSizeMode.MINIMIZED else IslandSizeMode.COMPACT)
        refreshOverlay(context)
    }

    fun getStyle(context: Context): IslandDisplayStyle {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, IslandDisplayStyle.SPOTIFY_YOUTUBE.id)
        return IslandDisplayStyle.fromId(raw)
    }

    fun setStyle(context: Context, style: IslandDisplayStyle) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.id)
            .apply()
        floatingView?.setDisplayStyle(style)
        refreshOverlay(context)
    }

    fun getOffsetXDp(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OFFSET_X_DP, 0)
    }

    fun setOffsetXDp(context: Context, offsetXDp: Int) {
        val clamped = offsetXDp.coerceIn(-160, 160)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OFFSET_X_DP, clamped)
            .apply()
        updateWindowPosition(context)
    }

    fun getOffsetYDp(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OFFSET_Y_DP, 4)
    }

    fun setOffsetYDp(context: Context, offsetDp: Int) {
        val clamped = offsetDp.coerceIn(-10, 260)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OFFSET_Y_DP, clamped)
            .apply()
        updateWindowPosition(context)
    }

    fun resetPositionAndSize(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OFFSET_X_DP, 0)
            .putInt(KEY_OFFSET_Y_DP, 4)
            .putBoolean(KEY_MINIMIZED, false)
            .apply()
        floatingView?.setSizeMode(IslandSizeMode.COMPACT)
        refreshOverlay(context)
    }

    internal fun onDragDeltaPx(context: Context, dxPx: Float, dyPx: Float) {
        val params = layoutParams ?: return
        val wm = windowManager ?: return
        val view = floatingView ?: return
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val newX = (params.x + dxPx.roundToInt()).coerceIn(dpToPx(context, -155f), dpToPx(context, 155f))
        val newY = (params.y + dyPx.roundToInt()).coerceIn(dpToPx(context, -8f), dpToPx(context, 300f))
        if (newX != params.x || newY != params.y) {
            params.x = newX
            params.y = newY
            runCatching { wm.updateViewLayout(view, params) }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_OFFSET_X_DP, (newX / density).roundToInt())
                .putInt(KEY_OFFSET_Y_DP, (newY / density).roundToInt())
                .apply()
        }
    }

    fun refreshOverlay(context: Context, state: TimerState = TimerForegroundService.timerFlow.value) {
        val appCtx = context.applicationContext
        val shouldShow = isEnabled(appCtx) &&
            canDrawOverlays(appCtx) &&
            (state.isRunning || isShowOutsideApp(appCtx))

        if (!shouldShow) {
            hideOverlay()
            return
        }
        showOrUpdateOverlay(appCtx, state)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOrUpdateOverlay(context: Context, state: TimerState) {
        if (!canDrawOverlays(context)) return
        val wm = (windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
        windowManager = wm

        val style = getStyle(context)
        val view = floatingView ?: FloatingIslandCanvasView(context).also {
            val savedMin = isMinimized(context)
            it.setSizeMode(if (savedMin) IslandSizeMode.MINIMIZED else IslandSizeMode.COMPACT)
            floatingView = it
        }
        view.setDisplayStyle(style)
        view.updateTimerState(state)

        val mode = view.sizeMode
        val widthDp = when (mode) {
            IslandSizeMode.MINIMIZED -> 88f
            IslandSizeMode.COMPACT -> 228f
            IslandSizeMode.EXPANDED -> 344f
        }
        val heightDp = when (mode) {
            IslandSizeMode.MINIMIZED -> 28f
            IslandSizeMode.COMPACT -> 36f
            IslandSizeMode.EXPANDED -> 192f
        }
        val widthPx = dpToPx(context, widthDp)
        val heightPx = dpToPx(context, heightDp)

        val savedXDp = getOffsetXDp(context)
        // Khi ở chế độ Thu nhỏ (MINIMIZED) mà người dùng chưa kéo sang ngang, tự động xếp gọn sang góc phải để không che camera!
        val effectiveXDp = when (mode) {
            IslandSizeMode.EXPANDED -> 0
            IslandSizeMode.MINIMIZED -> if (savedXDp == 0) 115 else savedXDp
            IslandSizeMode.COMPACT -> savedXDp
        }
        val xPx = dpToPx(context, effectiveXDp.toFloat())
        val yPx = dpToPx(context, getOffsetYDp(context).toFloat())

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        if (!isAdded || layoutParams == null) {
            val params = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = xPx
                y = yPx
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
            layoutParams = params
            runCatching {
                wm.addView(view, params)
                isAdded = true
            }
        } else {
            layoutParams?.let { params ->
                if (params.width != widthPx || params.height != heightPx || params.x != xPx || params.y != yPx) {
                    params.width = widthPx
                    params.height = heightPx
                    params.x = xPx
                    params.y = yPx
                    runCatching { wm.updateViewLayout(view, params) }
                }
            }
        }
    }

    internal fun onSizeModeChanged(context: Context, newMode: IslandSizeMode) {
        val appCtx = context.applicationContext
        if (newMode != IslandSizeMode.EXPANDED) {
            appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MINIMIZED, newMode == IslandSizeMode.MINIMIZED)
                .apply()
        }
        showOrUpdateOverlay(appCtx, TimerForegroundService.timerFlow.value)
    }

    private fun updateWindowPosition(context: Context) {
        if (isAdded) {
            showOrUpdateOverlay(context.applicationContext, TimerForegroundService.timerFlow.value)
        }
    }

    /**
     * Mở nhanh lại ứng dụng Tools Hub (màn hình PowerTimer) từ lớp phủ Dynamic Island
     * bất kể người dùng đang ở màn hình chính hay trong ứng dụng khác.
     */
    fun openAppFromOverlay(context: Context, toolId: String = "power_timer") {
        val appCtx = context.applicationContext
        val intent = Intent(appCtx, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("OPEN_TOOL_ID", toolId)
        }

        val optionsBundle = if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
            }.getOrNull()
        } else null

        // Bước 1: Đưa Task hiện có của App lên tiền cảnh nếu đang nằm trong Recent Tasks
        runCatching {
            val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.firstOrNull()?.moveToFront()
        }

        // Bước 2: Gửi qua PendingIntent với quyền Background Activity Start
        val sentViaPendingIntent = runCatching {
            val pi = PendingIntent.getActivity(
                appCtx,
                9012,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= 34 && optionsBundle != null) {
                pi.send(appCtx, 0, intent, null, null, null, optionsBundle)
            } else {
                pi.send()
            }
            true
        }.getOrDefault(false)

        // Bước 3: Fallback gọi trực tiếp startActivity
        if (!sentViaPendingIntent) {
            runCatching {
                if (optionsBundle != null) {
                    appCtx.startActivity(intent, optionsBundle)
                } else {
                    appCtx.startActivity(intent)
                }
            }
        } else {
            // Vẫn gọi startActivity để đảm bảo onNewIntent nhận được OPEN_TOOL_ID ngay lập tức
            runCatching {
                if (optionsBundle != null) {
                    appCtx.startActivity(intent, optionsBundle)
                } else {
                    appCtx.startActivity(intent)
                }
            }
        }
    }

    fun hideOverlay() {
        val wm = windowManager
        val view = floatingView
        if (isAdded && wm != null && view != null) {
            runCatching { wm.removeView(view) }
        }
        floatingView?.stopAnimation()
        floatingView = null
        layoutParams = null
        isAdded = false
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}

/**
 * Custom View vẽ trực tiếp viên thuốc Dynamic Island ngoài màn hình chính ở tốc độ 60fps.
 * Hỗ trợ 3 chế độ kích thước (MINIMIZED, COMPACT, EXPANDED), kéo thả tự do,
 * hiển thị rõ thời gian còn lại và mở nhanh lại App.
 */
internal class FloatingIslandCanvasView(context: Context) : View(context) {

    var sizeMode: IslandSizeMode = IslandSizeMode.COMPACT
        private set

    private var style: IslandDisplayStyle = IslandDisplayStyle.SPOTIFY_YOUTUBE
    private var timerState: TimerState = TimerState()
    private var phase: Float = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#05070E")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pillRect = RectF()
    private val compactMinimizeHitRect = RectF()
    private val topOpenAppRect = RectF()
    private val topMinimizeRect = RectF()
    private val topCollapseRect = RectF()
    private val clockBodyRect = RectF()
    private val btn1Rect = RectF()
    private val btn2Rect = RectF()
    private val btn3Rect = RectF()
    private val btn4Rect = RectF()

    private val animator = ValueAnimator.ofFloat(0f, (2.0 * Math.PI).toFloat()).apply {
        duration = 1500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    fun setSizeMode(newMode: IslandSizeMode) {
        sizeMode = newMode
        invalidate()
    }

    fun setDisplayStyle(newStyle: IslandDisplayStyle) {
        style = newStyle
        invalidate()
    }

    fun updateTimerState(state: TimerState) {
        timerState = state
        if (!animator.isStarted) animator.start()
        invalidate()
    }

    fun stopAnimation() {
        animator.cancel()
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }

    private fun currentFormattedTime(): String {
        val raw = timerState.formattedRemaining
        return if (raw.isNotBlank() && raw != "00:00") raw else "30:00"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(1.5f)
        pillRect.set(pad, pad, w - pad, h - pad)
        val corner = when (sizeMode) {
            IslandSizeMode.EXPANDED -> dp(26f)
            else -> h / 2f
        }

        // Nền viên thuốc (bán trong suốt nhẹ khi ở chế độ Thu nhỏ MINIMIZED để không che nội dung)
        bgPaint.color = if (sizeMode == IslandSizeMode.MINIMIZED) {
            Color.parseColor("#D905070E")
        } else {
            Color.parseColor("#05070E")
        }
        canvas.drawRoundRect(pillRect, corner, corner, bgPaint)

        // Viền tùy theo kiểu hiển thị
        borderPaint.strokeWidth = dp(if (sizeMode == IslandSizeMode.EXPANDED) 1.6f else 1.2f)
        when (style) {
            IslandDisplayStyle.SPOTIFY_YOUTUBE -> {
                borderPaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(
                        Color.parseColor("#22C55E"),
                        Color.parseColor("#2A324B"),
                        Color.parseColor("#06B6D4")
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            IslandDisplayStyle.NEON_CYBER -> {
                borderPaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(
                        Color.parseColor("#00E5FF"),
                        Color.parseColor("#A855F7"),
                        Color.parseColor("#22C55E")
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            IslandDisplayStyle.LIQUID_WAVE -> {
                borderPaint.shader = LinearGradient(
                    0f, 0f, w, 0f,
                    intArrayOf(
                        Color.parseColor("#EC4899"),
                        Color.parseColor("#6366F1"),
                        Color.parseColor("#06B6D4")
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            IslandDisplayStyle.MINIMAL_IOS -> {
                borderPaint.shader = null
                borderPaint.color = Color.parseColor("#334155")
            }
        }
        canvas.drawRoundRect(pillRect, corner, corner, borderPaint)

        when (sizeMode) {
            IslandSizeMode.MINIMIZED -> drawMinimizedIsland(canvas, w, h)
            IslandSizeMode.COMPACT -> drawCompactIsland(canvas, w, h)
            IslandSizeMode.EXPANDED -> drawExpandedIsland(canvas, w, h)
        }
    }

    /**
     * Chế độ Thu Nhỏ Siêu Gọn (88x28dp):
     * Không chiếm diện tích thanh trạng thái, vẫn hiển thị rõ thời gian còn lại (MM:SS).
     */
    private fun drawMinimizedIsland(canvas: Canvas, w: Float, h: Float) {
        val cy = h / 2f
        val timeStr = currentFormattedTime()

        // Chấm tròn trạng thái nhấp nháy bên trái
        barPaint.shader = null
        barPaint.color = if (timerState.isRunning) {
            Color.parseColor("#22C55E")
        } else {
            Color.parseColor("#38BDF8")
        }
        val pulseR = dp(3.8f) + sin(phase).toFloat() * dp(0.7f)
        canvas.drawCircle(dp(12f), cy, pulseR, barPaint)

        // Thời gian còn lại rõ nét ở giữa
        textPaint.textSize = dp(11.5f)
        textPaint.color = if (timerState.isRunning) Color.parseColor("#4ADE80") else Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(timeStr.take(5), w / 2f + dp(3f), cy + dp(4f), textPaint)
    }

    /**
     * Chế độ Viên Thuốc Chuẩn (228x36dp):
     * - Bên trái Camera: Đĩa xoay / Biểu tượng + Thời gian đếm ngược MM:SS RÕ NÉT.
     * - Chính giữa (92..136dp): Chừa khoảng trống cho Camera trước để không bao giờ che chữ.
     * - Bên phải Camera: Sóng nhạc động + Nút thu nhỏ nhanh (–).
     */
    private fun drawCompactIsland(canvas: Canvas, w: Float, h: Float) {
        val cy = h / 2f
        val timeStr = currentFormattedTime().take(5)
        val timeColor = if (timerState.isRunning) Color.parseColor("#4ADE80") else Color.parseColor("#38BDF8")

        // Vùng chạm góc phải cùng (30dp cuối) để Thu nhỏ nhanh 1 chạm
        compactMinimizeHitRect.set(w - dp(30f), 0f, w, h)

        when (style) {
            IslandDisplayStyle.SPOTIFY_YOUTUBE -> {
                // Bên trái camera: Đĩa nhạc xoay + Số phút đếm ngược rõ nét
                val discCenterX = dp(18f)
                val discRadius = dp(10.5f)
                barPaint.shader = LinearGradient(
                    discCenterX - discRadius, cy - discRadius,
                    discCenterX + discRadius, cy + discRadius,
                    intArrayOf(Color.parseColor("#22C55E"), Color.parseColor("#06B6D4"), Color.parseColor("#3B82F6")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(discCenterX, cy, discRadius, barPaint)
                barPaint.shader = null

                val dotAngle = phase
                val dotX = discCenterX + cos(dotAngle) * dp(5f)
                val dotY = cy + sin(dotAngle) * dp(5f)
                barPaint.color = Color.WHITE
                canvas.drawCircle(dotX, dotY, dp(2f), barPaint)
                barPaint.color = Color.parseColor("#05070E")
                canvas.drawCircle(discCenterX, cy, dp(2.8f), barPaint)

                // Đồng hồ đếm ngược (MM:SS) nằm bên trái camera (32dp..78dp, không chạm camera giữa 114dp!)
                textPaint.textSize = dp(11.5f)
                textPaint.color = timeColor
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(timeStr, dp(33f), cy + dp(4f), textPaint)

                // Bên phải camera: 5 cột sóng nhạc trắng + nút thu nhỏ (–)
                val barCount = 5
                val barW = dp(2.3f)
                val gap = dp(2.2f)
                val startX = w - dp(32f) - (barCount * barW + (barCount - 1) * gap)
                barPaint.color = Color.WHITE
                for (i in 0 until barCount) {
                    val ratio = 0.25f + 0.75f * ((sin(phase * 1.4f + i * 1.05f) + 1f) / 2f)
                    val barH = dp(13.5f) * ratio
                    val left = startX + i * (barW + gap)
                    canvas.drawRoundRect(
                        left,
                        cy - barH / 2f,
                        left + barW,
                        cy + barH / 2f,
                        barW / 2f,
                        barW / 2f,
                        barPaint
                    )
                }
            }

            IslandDisplayStyle.NEON_CYBER -> {
                barPaint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(dp(16f), cy, dp(6f), barPaint)
                barPaint.color = Color.parseColor("#05070E")
                canvas.drawCircle(dp(18.5f), cy - dp(1.5f), dp(5f), barPaint)

                textPaint.textSize = dp(11.5f)
                textPaint.color = Color.WHITE
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(timeStr, dp(28f), cy + dp(4f), textPaint)

                val colors = intArrayOf(
                    Color.parseColor("#22C55E"),
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#A855F7"),
                    Color.parseColor("#EC4899")
                )
                val barW = dp(2.5f)
                val gap = dp(2.3f)
                val startX = w - dp(32f) - (4 * barW + 3 * gap)
                for (i in 0 until 4) {
                    barPaint.color = colors[i]
                    val ratio = 0.3f + 0.7f * ((sin(phase + i * 1.1f) + 1f) / 2f)
                    val barH = dp(13.5f) * ratio
                    val left = startX + i * (barW + gap)
                    canvas.drawRoundRect(left, cy - barH / 2f, left + barW, cy + barH / 2f, barW / 2f, barW / 2f, barPaint)
                }
            }

            IslandDisplayStyle.LIQUID_WAVE -> {
                val path = Path()
                path.moveTo(dp(12f), cy + dp(5f))
                var x = dp(12f)
                while (x <= w - dp(30f)) {
                    val y = cy + dp(3f) + sin((x / w) * 4f * Math.PI + phase).toFloat() * dp(3.5f)
                    path.lineTo(x, y)
                    x += dp(6f)
                }
                borderPaint.strokeWidth = dp(1.8f)
                canvas.drawPath(path, borderPaint)

                textPaint.textSize = dp(11.5f)
                textPaint.color = timeColor
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(timeStr, dp(16f), cy + dp(3.5f), textPaint)
            }

            IslandDisplayStyle.MINIMAL_IOS -> {
                barPaint.color = if (timerState.isRunning) Color.parseColor("#22C55E") else Color.parseColor("#F59E0B")
                canvas.drawCircle(dp(16f), cy, dp(4.5f), barPaint)

                textPaint.textSize = dp(11.5f)
                textPaint.color = Color.WHITE
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(timeStr, dp(26f), cy + dp(4f), textPaint)
            }
        }

        // Vẽ nút thu nhỏ nhanh (–) ở góc phải của viên thuốc COMPACT
        btnBgPaint.color = Color.parseColor("#1E293B")
        val minCx = w - dp(15f)
        canvas.drawCircle(minCx, cy, dp(8.5f), btnBgPaint)
        subTextPaint.color = Color.parseColor("#94A3B8")
        subTextPaint.textSize = dp(11f)
        subTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("–", minCx, cy + dp(3.5f), subTextPaint)
    }

    /**
     * Bảng điều khiển mở rộng (344x192dp):
     * - Hàng đầu: Tiêu đề + Nút [Mở App] + Nút [Thu nhỏ] + Nút [▲]
     * - Khung giữa: Đồng hồ lớn (MM:SS) + Phần trăm thời gian còn lại + Sóng âm (Chạm vào mở thẳng App!)
     * - Thanh tiến trình
     * - Hàng 4 nút điều khiển nhanh dưới đáy
     */
    private fun drawExpandedIsland(canvas: Canvas, w: Float, h: Float) {
        val timeStr = currentFormattedTime()
        val statusLabel = if (timerState.isRunning) {
            "Đang đếm ngược • Chạm đồng hồ để mở App"
        } else {
            "Sẵn sàng • Chạm mốc bên dưới hoặc Mở App"
        }

        // Hàng trên cùng: Nút [Mở App] + Nút [Thu nhỏ] + Nút [▲]
        subTextPaint.textSize = dp(10.5f)
        subTextPaint.color = Color.parseColor("#38BDF8")
        subTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(statusLabel, dp(16f), dp(26f), subTextPaint)

        // Nút [Thu nhỏ] ở góc trên phải
        val collapseRight = w - dp(14f)
        val collapseLeft = collapseRight - dp(28f)
        topCollapseRect.set(collapseLeft, dp(10f), collapseRight, dp(34f))
        btnBgPaint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(topCollapseRect, dp(8f), dp(8f), btnBgPaint)
        subTextPaint.color = Color.WHITE
        subTextPaint.textSize = dp(10f)
        subTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("▲", topCollapseRect.centerX(), topCollapseRect.centerY() + dp(3.5f), subTextPaint)

        val minRight = collapseLeft - dp(6f)
        val minLeft = minRight - dp(62f)
        topMinimizeRect.set(minLeft, dp(10f), minRight, dp(34f))
        btnBgPaint.color = Color.parseColor("#334155")
        canvas.drawRoundRect(topMinimizeRect, dp(8f), dp(8f), btnBgPaint)
        subTextPaint.color = Color.parseColor("#FACC15")
        subTextPaint.textSize = dp(10f)
        canvas.drawText("🗕 Thu nhỏ", topMinimizeRect.centerX(), topMinimizeRect.centerY() + dp(3.5f), subTextPaint)

        // Khung đồng hồ lớn (chạm vào đây sẽ mở thẳng vào lại App!)
        clockBodyRect.set(dp(16f), dp(40f), w - dp(16f), dp(96f))
        btnBgPaint.color = Color.parseColor("#0D1326")
        canvas.drawRoundRect(clockBodyRect, dp(14f), dp(14f), btnBgPaint)

        textPaint.textSize = dp(26f)
        textPaint.color = if (timerState.isRunning) Color.parseColor("#4ADE80") else Color.WHITE
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(timeStr, dp(28f), dp(75f), textPaint)

        val pct = if (timerState.isRunning) {
            (timerState.progress * 100).toInt().coerceIn(0, 100)
        } else 100
        subTextPaint.textSize = dp(11f)
        subTextPaint.color = Color.parseColor("#38BDF8")
        subTextPaint.textAlign = Paint.Align.LEFT
        val remMins = (timerState.remainingSeconds / 60L).coerceAtLeast(0L)
        val detailTime = if (timerState.isRunning) {
            "Còn $remMins phút ($pct%)"
        } else {
            "Mức hẹn: $remMins phút"
        }
        canvas.drawText(detailTime, dp(138f), dp(64f), subTextPaint)

        // Nút [🚀 Mở App] nằm ngay trong khung đồng hồ để bấm cực nhạy
        topOpenAppRect.set(dp(138f), dp(70f), dp(228f), dp(91f))
        btnBgPaint.color = Color.parseColor("#0284C7")
        canvas.drawRoundRect(topOpenAppRect, dp(7f), dp(7f), btnBgPaint)
        subTextPaint.color = Color.WHITE
        subTextPaint.textSize = dp(10.5f)
        subTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("🚀 Mở App ngay", topOpenAppRect.centerX(), topOpenAppRect.centerY() + dp(3.5f), subTextPaint)

        // Sóng nhạc 5 cột bên phải khung đồng hồ
        val colors = intArrayOf(
            Color.parseColor("#22C55E"),
            Color.parseColor("#00E5FF"),
            Color.parseColor("#A855F7"),
            Color.parseColor("#EC4899"),
            Color.parseColor("#FACC15")
        )
        val barW = dp(3.8f)
        val gap = dp(3.2f)
        val startX = w - dp(28f) - (5 * barW + 4 * gap)
        val waveCy = dp(68f)
        for (i in 0 until 5) {
            barPaint.color = colors[i]
            val ratio = 0.25f + 0.75f * ((sin(phase * 1.3f + i * 0.9f) + 1f) / 2f)
            val barH = dp(26f) * ratio
            val left = startX + i * (barW + gap)
            canvas.drawRoundRect(
                left,
                waveCy - barH / 2f,
                left + barW,
                waveCy + barH / 2f,
                barW / 2f,
                barW / 2f,
                barPaint
            )
        }

        // Thanh tiến trình
        val barTop = dp(104f)
        val barBottom = dp(109f)
        btnBgPaint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(dp(16f), barTop, w - dp(16f), barBottom, dp(4f), dp(4f), btnBgPaint)
        val progress = if (timerState.isRunning) timerState.progress.coerceIn(0.03f, 1f) else 1f
        btnBgPaint.color = Color.parseColor("#00E5FF")
        canvas.drawRoundRect(
            dp(16f),
            barTop,
            dp(16f) + (w - dp(32f)) * progress,
            barBottom,
            dp(4f),
            dp(4f),
            btnBgPaint
        )

        // 4 Nút bấm tương tác dưới đáy
        val btnTop = dp(120f)
        val btnBottom = h - dp(14f)
        val totalPad = dp(32f)
        val spacing = dp(7f)
        val btnW = (w - totalPad - spacing * 3) / 4f

        val labels = if (timerState.isRunning) {
            listOf("+10 Phút", "Tắt màn", "Mở App", "Dừng")
        } else {
            listOf("Bật 15p", "Bật 30p", "Bật 60p", "Mở App")
        }
        val bgColors = if (timerState.isRunning) {
            listOf("#0284C7", "#D97706", "#2563EB", "#DC2626")
        } else {
            listOf("#0F766E", "#0284C7", "#6D28D9", "#2563EB")
        }

        val rects = listOf(btn1Rect, btn2Rect, btn3Rect, btn4Rect)
        for (i in 0 until 4) {
            val left = dp(16f) + i * (btnW + spacing)
            val rect = rects[i]
            rect.set(left, btnTop, left + btnW, btnBottom)
            btnBgPaint.color = Color.parseColor(bgColors[i])
            canvas.drawRoundRect(rect, dp(12f), dp(12f), btnBgPaint)

            subTextPaint.color = Color.WHITE
            subTextPaint.textSize = dp(11.5f)
            subTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(labels[i], rect.centerX(), rect.centerY() + dp(4f), subTextPaint)
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var isDragging = false
    private var lastTapTimeMs = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val totalDx = abs(event.rawX - downRawX)
                val totalDy = abs(event.rawY - downRawY)
                if (sizeMode != IslandSizeMode.EXPANDED && (totalDx > dp(10f) || totalDy > dp(10f))) {
                    isDragging = true
                    val stepDx = event.rawX - lastRawX
                    val stepDy = event.rawY - lastRawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    SystemDynamicIslandManager.onDragDeltaPx(context, stepDx, stepDy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val now = System.currentTimeMillis()
                    if (sizeMode != IslandSizeMode.EXPANDED && (now - lastTapTimeMs) < 320L) {
                        // Chạm đúp (Double-tap) vào viên thuốc: Mở thẳng vào lại App ngay lập tức!
                        lastTapTimeMs = 0L
                        SystemDynamicIslandManager.openAppFromOverlay(context)
                    } else {
                        lastTapTimeMs = now
                        handleTap(event.x, event.y)
                    }
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        val appCtx = context.applicationContext

        when (sizeMode) {
            IslandSizeMode.MINIMIZED -> {
                // Đang ở chế độ Thu nhỏ -> chạm 1 lần bung ra lại viên thuốc chuẩn
                sizeMode = IslandSizeMode.COMPACT
                SystemDynamicIslandManager.onSizeModeChanged(appCtx, sizeMode)
                return
            }
            IslandSizeMode.COMPACT -> {
                // Nếu bấm vào nút (–) ở góc phải viên thuốc -> Thu nhỏ ngay lập tức!
                if (compactMinimizeHitRect.contains(x, y)) {
                    sizeMode = IslandSizeMode.MINIMIZED
                    SystemDynamicIslandManager.onSizeModeChanged(appCtx, sizeMode)
                } else {
                    sizeMode = IslandSizeMode.EXPANDED
                    SystemDynamicIslandManager.onSizeModeChanged(appCtx, sizeMode)
                }
                return
            }
            IslandSizeMode.EXPANDED -> {
                val running = timerState.isRunning
                when {
                    topMinimizeRect.contains(x, y) -> {
                        sizeMode = IslandSizeMode.MINIMIZED
                        SystemDynamicIslandManager.onSizeModeChanged(appCtx, sizeMode)
                    }
                    topCollapseRect.contains(x, y) -> {
                        collapseToCompact()
                    }
                    topOpenAppRect.contains(x, y) || clockBodyRect.contains(x, y) -> {
                        collapseToCompact()
                        SystemDynamicIslandManager.openAppFromOverlay(appCtx)
                    }
                    btn1Rect.contains(x, y) -> {
                        if (running) {
                            TimerForegroundService.addTenMinutes(appCtx)
                        } else {
                            TimerForegroundService.startTimer(appCtx, 15 * 60L, ShutdownMode.BANK_SAFE_SLEEP)
                        }
                        collapseToCompact()
                    }
                    btn2Rect.contains(x, y) -> {
                        if (running) {
                            val mode = timerState.selectedMode
                            TimerForegroundService.stopTimer(appCtx)
                            collapseToCompact()
                            CoroutineScope(Dispatchers.Main).launch {
                                ShutdownManager(appCtx).executeShutdown(mode, fadeAudio = false, enableDnd = false)
                            }
                        } else {
                            TimerForegroundService.startTimer(appCtx, 30 * 60L, ShutdownMode.BANK_SAFE_SLEEP)
                            collapseToCompact()
                        }
                    }
                    btn3Rect.contains(x, y) -> {
                        if (running) {
                            collapseToCompact()
                            SystemDynamicIslandManager.openAppFromOverlay(appCtx)
                        } else {
                            TimerForegroundService.startTimer(appCtx, 60 * 60L, ShutdownMode.BANK_SAFE_SLEEP)
                            collapseToCompact()
                        }
                    }
                    btn4Rect.contains(x, y) -> {
                        if (running) {
                            TimerForegroundService.stopTimer(appCtx)
                            collapseToCompact()
                        } else {
                            collapseToCompact()
                            SystemDynamicIslandManager.openAppFromOverlay(appCtx)
                        }
                    }
                    else -> {
                        collapseToCompact()
                    }
                }
            }
        }
    }

    private fun collapseToCompact() {
        sizeMode = IslandSizeMode.COMPACT
        SystemDynamicIslandManager.onSizeModeChanged(context, sizeMode)
    }
}
