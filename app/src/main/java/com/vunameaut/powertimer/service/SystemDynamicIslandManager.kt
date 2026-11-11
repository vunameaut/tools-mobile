package com.vunameaut.powertimer.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import kotlin.math.sin

enum class IslandDisplayStyle(
    val id: String,
    val title: String,
    val subtitle: String
) {
    SPOTIFY_YOUTUBE(
        id = "SPOTIFY_YOUTUBE",
        title = "Kiểu YouTube / Spotify (Giống ảnh mẫu)",
        subtitle = "Bìa đĩa tròn xoay bên trái camera + Sóng âm 5 cột nhịp nhàng bên phải"
    ),
    NEON_CYBER(
        id = "NEON_CYBER",
        title = "Kiểu Đồng Hồ Neon Đếm Ngược",
        subtitle = "Biểu tượng mặt trăng + Số đếm ngược trực tiếp + Viền Neon đổi màu"
    ),
    LIQUID_WAVE(
        id = "LIQUID_WAVE",
        title = "Kiểu Sóng Nước Đa Sắc",
        subtitle = "Dải sóng lượn mềm mại dưới chân giọt nước + Thời gian còn lại"
    ),
    MINIMAL_IOS(
        id = "MINIMAL_IOS",
        title = "Kiểu iOS Dynamic Island Tối Giản",
        subtitle = "Viên thuốc đen tuyền ôm sát camera, viền mảnh tinh tế"
    );

    companion object {
        fun fromId(id: String?): IslandDisplayStyle {
            return entries.firstOrNull { it.id == id } ?: SPOTIFY_YOUTUBE
        }
    }
}

/**
 * Quản lý hiển thị Dynamic Island NỔI TOÀN HỆ THỐNG (ngoài màn hình chính & đè lên thanh trạng thái
 * của mọi ứng dụng khác giống hệt YouTube / Spotify trong ảnh).
 */
object SystemDynamicIslandManager {

    private const val PREF_NAME = "waterdrop_island_prefs"
    private const val KEY_ENABLED = "always_show_waterdrop"
    private const val KEY_SHOW_OUTSIDE_APP = "show_island_outside_app"
    private const val KEY_STYLE = "island_display_style"
    private const val KEY_OFFSET_Y_DP = "island_offset_y_dp"
    private const val KEY_WIDTH_SCALE = "island_width_scale"

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

    fun getOffsetYDp(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OFFSET_Y_DP, 4)
    }

    fun setOffsetYDp(context: Context, offsetDp: Int) {
        val clamped = offsetDp.coerceIn(-10, 40)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OFFSET_Y_DP, clamped)
            .apply()
        updateWindowPosition(context)
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
            floatingView = it
        }
        view.setDisplayStyle(style)
        view.updateTimerState(state)

        val isExpanded = view.isExpandedState
        val widthDp = if (isExpanded) 340f else 176f
        val heightDp = if (isExpanded) 172f else 36f
        val widthPx = dpToPx(context, widthDp)
        val heightPx = dpToPx(context, heightDp)
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
                x = 0
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
                if (params.width != widthPx || params.height != heightPx || params.y != yPx) {
                    params.width = widthPx
                    params.height = heightPx
                    params.y = yPx
                    runCatching { wm.updateViewLayout(view, params) }
                }
            }
        }
    }

    internal fun onExpandedChanged(context: Context) {
        showOrUpdateOverlay(context.applicationContext, TimerForegroundService.timerFlow.value)
    }

    private fun updateWindowPosition(context: Context) {
        if (isAdded) {
            showOrUpdateOverlay(context.applicationContext, TimerForegroundService.timerFlow.value)
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
 * Hỗ trợ 4 kiểu hiển thị (YouTube/Spotify, Đồng hồ Neon, Sóng Nước, Tối giản iOS) và Chạm để mở rộng.
 */
internal class FloatingIslandCanvasView(context: Context) : View(context) {

    var isExpandedState: Boolean = false
        private set

    private var style: IslandDisplayStyle = IslandDisplayStyle.SPOTIFY_YOUTUBE
    private var timerState: TimerState = TimerState()
    private var phase: Float = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#05060A")
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(2f)
        pillRect.set(pad, pad, w - pad, h - pad)
        val corner = if (isExpandedState) dp(26f) else h / 2f

        // Nền đen tuyền của viên thuốc
        canvas.drawRoundRect(pillRect, corner, corner, bgPaint)

        // Viền tùy theo kiểu hiển thị
        borderPaint.strokeWidth = dp(if (isExpandedState) 1.6f else 1.2f)
        when (style) {
            IslandDisplayStyle.SPOTIFY_YOUTUBE -> {
                borderPaint.shader = null
                borderPaint.color = Color.parseColor("#2A324B")
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
                borderPaint.color = Color.parseColor("#1E293B")
            }
        }
        canvas.drawRoundRect(pillRect, corner, corner, borderPaint)

        if (!isExpandedState) {
            drawCompactIsland(canvas, w, h)
        } else {
            drawExpandedIsland(canvas, w, h)
        }
    }

    private fun drawCompactIsland(canvas: Canvas, w: Float, h: Float) {
        val cy = h / 2f
        val timeStr = if (timerState.isRunning) timerState.formattedRemaining else "30:00"

        when (style) {
            IslandDisplayStyle.SPOTIFY_YOUTUBE -> {
                // Giống hệt ảnh mẫu: Vòng tròn đĩa nhạc xanh/tím bên trái + Khoảng camera giữa + 5 cột sóng âm trắng bên phải
                val discCenterX = dp(20f)
                val discRadius = dp(11.5f)
                barPaint.shader = LinearGradient(
                    discCenterX - discRadius, cy - discRadius,
                    discCenterX + discRadius, cy + discRadius,
                    intArrayOf(Color.parseColor("#22C55E"), Color.parseColor("#06B6D4"), Color.parseColor("#3B82F6")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(discCenterX, cy, discRadius, barPaint)
                barPaint.shader = null

                // Chấm xoay trên đĩa
                val dotAngle = phase
                val dotX = discCenterX + cos(dotAngle) * dp(5.5f)
                val dotY = cy + sin(dotAngle) * dp(5.5f)
                barPaint.color = Color.WHITE
                canvas.drawCircle(dotX, dotY, dp(2.2f), barPaint)
                barPaint.color = Color.parseColor("#05060A")
                canvas.drawCircle(discCenterX, cy, dp(3.2f), barPaint)

                // Nếu đang đếm ngược thì hiện số phút nhỏ gọn cạnh đĩa
                if (timerState.isRunning) {
                    textPaint.textSize = dp(9.5f)
                    textPaint.color = Color.parseColor("#4ADE80")
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(timeStr.take(5), dp(35f), cy + dp(3.5f), textPaint)
                }

                // Bên phải camera: 5 cột sóng nhạc trắng (như ảnh chụp màn hình của người dùng!)
                val barCount = 5
                val barW = dp(2.4f)
                val gap = dp(2.4f)
                val startX = w - dp(14f) - (barCount * barW + (barCount - 1) * gap)
                barPaint.color = Color.WHITE
                for (i in 0 until barCount) {
                    val ratio = 0.25f + 0.75f * ((sin(phase * 1.4f + i * 1.05f) + 1f) / 2f)
                    val barH = dp(14f) * ratio
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
                // Bên trái: Chấm mặt trăng xanh + Thời gian; Bên phải: Sóng âm màu Neon
                barPaint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(dp(16f), cy, dp(6f), barPaint)
                barPaint.color = Color.parseColor("#05060A")
                canvas.drawCircle(dp(18.5f), cy - dp(1.5f), dp(5f), barPaint)

                textPaint.textSize = dp(11f)
                textPaint.color = Color.WHITE
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(timeStr, dp(26f), cy + dp(4f), textPaint)

                val colors = intArrayOf(
                    Color.parseColor("#22C55E"),
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#A855F7"),
                    Color.parseColor("#EC4899")
                )
                val barW = dp(2.6f)
                val gap = dp(2.4f)
                val startX = w - dp(14f) - (4 * barW + 3 * gap)
                for (i in 0 until 4) {
                    barPaint.color = colors[i]
                    val ratio = 0.3f + 0.7f * ((sin(phase + i * 1.1f) + 1f) / 2f)
                    val barH = dp(14f) * ratio
                    val left = startX + i * (barW + gap)
                    canvas.drawRoundRect(left, cy - barH / 2f, left + barW, cy + barH / 2f, barW / 2f, barW / 2f, barPaint)
                }
            }

            IslandDisplayStyle.LIQUID_WAVE -> {
                // Vẽ sóng nước dưới đáy + Thời gian bên phải
                val path = Path()
                path.moveTo(dp(10f), cy + dp(4f))
                var x = dp(10f)
                while (x <= w - dp(10f)) {
                    val y = cy + sin((x / w) * 4f * Math.PI + phase).toFloat() * dp(4f)
                    path.lineTo(x, y)
                    x += dp(6f)
                }
                borderPaint.strokeWidth = dp(2f)
                canvas.drawPath(path, borderPaint)

                textPaint.textSize = dp(11f)
                textPaint.color = Color.parseColor("#38BDF8")
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(timeStr, w - dp(14f), cy + dp(4f), textPaint)
            }

            IslandDisplayStyle.MINIMAL_IOS -> {
                barPaint.color = if (timerState.isRunning) Color.parseColor("#22C55E") else Color.parseColor("#F59E0B")
                canvas.drawCircle(dp(16f), cy, dp(4.5f), barPaint)

                textPaint.textSize = dp(11f)
                textPaint.color = Color.WHITE
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(timeStr, w - dp(14f), cy + dp(4f), textPaint)
            }
        }
    }

    private fun drawExpandedIsland(canvas: Canvas, w: Float, h: Float) {
        val timeStr = if (timerState.isRunning) timerState.formattedRemaining else "30:00"
        val statusLabel = if (timerState.isRunning) {
            "Đang hẹn giờ • ${timerState.selectedMode.title}"
        } else {
            "Dynamic Island • Chạm mốc bên dưới để bật"
        }

        // Tiêu đề + Thời gian lớn
        subTextPaint.textSize = dp(11f)
        subTextPaint.color = Color.parseColor("#38BDF8")
        subTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(statusLabel, dp(18f), dp(34f), subTextPaint)

        textPaint.textSize = dp(26f)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(timeStr, dp(18f), dp(68f), textPaint)

        // Sóng nhạc bên phải
        val colors = intArrayOf(
            Color.parseColor("#22C55E"),
            Color.parseColor("#00E5FF"),
            Color.parseColor("#A855F7"),
            Color.parseColor("#EC4899"),
            Color.parseColor("#FACC15")
        )
        val barW = dp(4f)
        val gap = dp(3.5f)
        val startX = w - dp(22f) - (5 * barW + 4 * gap)
        val waveCy = dp(52f)
        for (i in 0 until 5) {
            barPaint.color = colors[i]
            val ratio = 0.25f + 0.75f * ((sin(phase * 1.3f + i * 0.9f) + 1f) / 2f)
            val barH = dp(28f) * ratio
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
        val barTop = dp(82f)
        val barBottom = dp(87f)
        btnBgPaint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(dp(18f), barTop, w - dp(18f), barBottom, dp(4f), dp(4f), btnBgPaint)
        val progress = if (timerState.isRunning) timerState.progress.coerceIn(0.03f, 1f) else 1f
        btnBgPaint.color = Color.parseColor("#00E5FF")
        canvas.drawRoundRect(
            dp(18f),
            barTop,
            dp(18f) + (w - dp(36f)) * progress,
            barBottom,
            dp(4f),
            dp(4f),
            btnBgPaint
        )

        // 4 Nút bấm tương tác ngay ngoài màn hình chính
        val btnTop = dp(102f)
        val btnBottom = h - dp(16f)
        val totalPad = dp(36f)
        val spacing = dp(8f)
        val btnW = (w - totalPad - spacing * 3) / 4f

        val labels = if (timerState.isRunning) {
            listOf("+10 Phút", "Khóa ngay", "Mở App", "Dừng")
        } else {
            listOf("Bật 15p", "Bật 30p", "Bật 60p", "Mở App")
        }
        val bgColors = if (timerState.isRunning) {
            listOf("#0284C7", "#D97706", "#334155", "#DC2626")
        } else {
            listOf("#0F766E", "#0284C7", "#6D28D9", "#334155")
        }

        val rects = listOf(btn1Rect, btn2Rect, btn3Rect, btn4Rect)
        for (i in 0 until 4) {
            val left = dp(18f) + i * (btnW + spacing)
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

    private var downX = 0f
    private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx < dp(18f) && dy < dp(18f)) {
                    handleTap(event.x, event.y)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        if (!isExpandedState) {
            isExpandedState = true
            SystemDynamicIslandManager.onExpandedChanged(context)
            return
        }

        val appCtx = context.applicationContext
        val running = timerState.isRunning

        when {
            btn1Rect.contains(x, y) -> {
                if (running) {
                    TimerForegroundService.addTenMinutes(appCtx)
                } else {
                    TimerForegroundService.startTimer(appCtx, 15 * 60L, ShutdownMode.BANK_SAFE_SLEEP)
                }
                collapse()
            }
            btn2Rect.contains(x, y) -> {
                if (running) {
                    val mode = timerState.selectedMode
                    TimerForegroundService.stopTimer(appCtx)
                    collapse()
                    CoroutineScope(Dispatchers.Main).launch {
                        ShutdownManager(appCtx).executeShutdown(mode, fadeAudio = false, enableDnd = false)
                    }
                } else {
                    TimerForegroundService.startTimer(appCtx, 30 * 60L, ShutdownMode.BANK_SAFE_SLEEP)
                    collapse()
                }
            }
            btn3Rect.contains(x, y) -> {
                if (running) {
                    openMainActivity(appCtx)
                } else {
                    TimerForegroundService.startTimer(appCtx, 60 * 60L, ShutdownMode.BANK_SAFE_SLEEP)
                }
                collapse()
            }
            btn4Rect.contains(x, y) -> {
                if (running) {
                    TimerForegroundService.stopTimer(appCtx)
                } else {
                    openMainActivity(appCtx)
                }
                collapse()
            }
            else -> {
                collapse()
            }
        }
    }

    private fun collapse() {
        isExpandedState = false
        SystemDynamicIslandManager.onExpandedChanged(context)
    }

    private fun openMainActivity(ctx: Context) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("OPEN_TOOL_ID", "power_timer")
        }
        runCatching { ctx.startActivity(intent) }
    }
}

