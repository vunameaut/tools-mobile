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
import android.view.HapticFeedbackConstants
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
        title = "Kiểu YouTube / Spotify (Ôm Camera)",
        subtitle = "Gắn liền camera trước giống YouTube + Đĩa xoay + Đồng hồ + Sóng nhạc"
    ),
    NATIVE_SYSTEM(
        id = "NATIVE_SYSTEM",
        title = "🌟 Đảo Hệ Thống Gốc (Media Capsule)",
        subtitle = "Dùng trực tiếp Dynamic Island tích hợp sẵn của máy (Fluid Cloud / OriginOS / HyperOS)"
    ),
    NEON_CYBER(
        id = "NEON_CYBER",
        title = "Kiểu Đồng Hồ Neon",
        subtitle = "Gắn liền camera trước + Mặt trăng + Số đếm ngược + Viền Neon đổi màu"
    ),
    LIQUID_WAVE(
        id = "LIQUID_WAVE",
        title = "Kiểu Sóng Nước Đa Sắc",
        subtitle = "Gắn liền camera trước + Dải sóng lượn mềm mại + Thời gian còn lại"
    ),
    MINIMAL_IOS(
        id = "MINIMAL_IOS",
        title = "Kiểu iOS Tối Giản",
        subtitle = "Viên thuốc đen tuyền ôm sát camera trước, hiển thị rõ số phút còn lại"
    );

    companion object {
        fun fromId(id: String?): IslandDisplayStyle {
            return entries.firstOrNull { it.id == id } ?: SPOTIFY_YOUTUBE
        }
    }
}

enum class IslandSizeMode {
    MINIMIZED, // Thu nhỏ siêu gọn
    COMPACT,   // Viên thuốc gắn liền camera trước giống YouTube
    EXPANDED   // Bảng điều khiển mở rộng bung thẳng từ camera trước
}

/**
 * Quản lý hiển thị Dynamic Island GẮN LIỀN VỚI CAMERA TRƯỚC (giống YouTube / Spotify):
 *
 * Kết hợp 2 cơ chế song song:
 * 1. `NativeMediaIslandBridge` (MediaSession + Notification.MediaStyle + Silent AudioTrack):
 *    Kích hoạt trực tiếp Dynamic Island gốc của hệ điều hành (ColorOS Fluid Cloud / OriginOS Atomic Island / HyperOS).
 * 2. Viên thuốc `FloatingIslandCanvasView` ôm sát lỗ đục Camera ở `y = 2dp` (`width = 152dp` gọn gàng không đè icon mạng/pin)
 *    kèm vùng nhận cảm ứng mở rộng (`height = 64dp`, vươn xuống dưới ranh giới chặn cảm ứng của Status Bar `36dp..64dp`)
 *    và toàn bộ nút bấm trong chế độ Mở rộng (`EXPANDED`) nằm ở `y = 42dp..200dp` giúp chạm nhạy 100% ngay lập tức!
 */
object SystemDynamicIslandManager {

    private const val PREF_NAME = "waterdrop_island_prefs"
    private const val KEY_ENABLED = "always_show_waterdrop"
    private const val KEY_SHOW_OUTSIDE_APP = "show_island_outside_app"
    private const val KEY_STYLE = "island_display_style"
    private const val KEY_OFFSET_X_DP = "island_camera_offset_x_dp"
    private const val KEY_OFFSET_Y_DP = "island_camera_offset_y_dp"
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
        val state = TimerForegroundService.timerFlow.value
        if (enabled && state.isRunning) {
            NativeMediaIslandBridge.startOrUpdate(
                context,
                state.remainingSeconds,
                state.totalSeconds,
                state.selectedMode
            )
        } else if (!enabled) {
            NativeMediaIslandBridge.stop()
        }
        hideOverlay()
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
        hideOverlay()
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
        hideOverlay()
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
        val state = TimerForegroundService.timerFlow.value
        if (state.isRunning && isEnabled(context)) {
            NativeMediaIslandBridge.startOrUpdate(
                context,
                state.remainingSeconds,
                state.totalSeconds,
                state.selectedMode
            )
        }
        hideOverlay()
    }

    fun getOffsetXDp(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OFFSET_X_DP, 0)
            .coerceIn(-140, 140)
    }

    fun setOffsetXDp(context: Context, offsetXDp: Int) {
        val clamped = offsetXDp.coerceIn(-140, 140)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OFFSET_X_DP, clamped)
            .apply()
        updateWindowPosition(context)
    }

    fun getOffsetYDp(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OFFSET_Y_DP, 2)
            .coerceIn(-4, 120)
    }

    fun setOffsetYDp(context: Context, offsetDp: Int) {
        val clamped = offsetDp.coerceIn(-4, 120)
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
            .putInt(KEY_OFFSET_Y_DP, 2)
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

        val newX = (params.x + dxPx.roundToInt()).coerceIn(dpToPx(context, -140f), dpToPx(context, 140f))
        val newY = (params.y + dyPx.roundToInt()).coerceIn(dpToPx(context, -4f), dpToPx(context, 120f))
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
        // Luôn ẩn hoàn toàn lớp phủ WindowManager phụ để KHÔNG BAO GIỜ bị vẽ đè 2 viên thuốc lên nhau;
        // Chỉ sử dụng DUY NHẤT Dynamic Island gốc của hệ thống (qua NativeMediaIslandBridge giống YouTube)!
        hideOverlay()
        if (isEnabled(appCtx) && state.isRunning) {
            NativeMediaIslandBridge.startOrUpdate(
                appCtx,
                state.remainingSeconds,
                state.totalSeconds,
                state.selectedMode
            )
        } else if (!state.isRunning || !isEnabled(appCtx)) {
            NativeMediaIslandBridge.stop()
        }
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
        // Chiều rộng viên thuốc COMPACT = 152dp (vừa khít quanh camera trước giống YouTube 12:55:29,
        // hoàn toàn không chạm vào đồng hồ bên trái hay biểu tượng tốc độ mạng / Wi-Fi / 5G bên phải!).
        // Chiều cao cửa sổ COMPACT = 64dp:
        // - 0..32dp đầu: Vẽ viên thuốc đen ôm sát camera trước (y = 2dp).
        // - 32..64dp dưới: Vùng nhận cảm ứng trong suốt nằm ngay dưới mép Status Bar (36..64dp),
        //   giúp ngón tay chạm vào viên thuốc luôn ăn cảm ứng 100% mà không bị Status Bar chặn!
        val widthDp = when (mode) {
            IslandSizeMode.MINIMIZED -> 76f
            IslandSizeMode.COMPACT -> 152f
            IslandSizeMode.EXPANDED -> 346f
        }
        val heightDp = when (mode) {
            IslandSizeMode.MINIMIZED -> 58f
            IslandSizeMode.COMPACT -> 64f
            IslandSizeMode.EXPANDED -> 208f
        }
        val widthPx = dpToPx(context, widthDp)
        val heightPx = dpToPx(context, heightDp)

        val savedXDp = getOffsetXDp(context)
        val effectiveXDp = when (mode) {
            IslandSizeMode.EXPANDED -> 0
            IslandSizeMode.MINIMIZED -> savedXDp
            IslandSizeMode.COMPACT -> savedXDp
        }
        val xPx = dpToPx(context, effectiveXDp.toFloat())
        val yPx = dpToPx(context, getOffsetYDp(context).toFloat())

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
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

        runCatching {
            val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.firstOrNull()?.moveToFront()
        }

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

        if (!sentViaPendingIntent) {
            runCatching {
                if (optionsBundle != null) {
                    appCtx.startActivity(intent, optionsBundle)
                } else {
                    appCtx.startActivity(intent)
                }
            }
        } else {
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
 * Custom View vẽ viên thuốc Dynamic Island GẮN LIỀN VỚI CAMERA TRƯỚC (giống YouTube / Spotify).
 *
 * Bí quyết kỹ thuật vượt rào cản cảm ứng Status Bar của Android mà không cần Accessibility:
 * - Phần hình ảnh viên thuốc (Visual Pill) cao `31dp` nằm ở đỉnh cửa sổ (`y = 0..31dp`),
 *   ôm khít lỗ đục camera trước và có bề ngang gọn `152dp` (không chạm vào chữ đồng hồ hay icon mạng/Wi-Fi).
 * - Phần đáy cửa sổ (`y = 31..64dp`) là vùng đệm bắt cảm ứng trong suốt vươn qua ranh giới Status Bar (`> 36dp`),
 *   kèm một thanh gờ phát sáng siêu mảnh ngay dưới đáy viên thuốc. Khi ngón tay người dùng chạm vào
 *   Dynamic Island quanh camera hoặc vuốt nhẹ xuống, phần đệm ngón tay luôn chạm vào vùng `34..64dp`
 *   nên ăn cảm ứng 100% ngay từ lần chạm đầu tiên!
 */
internal class FloatingIslandCanvasView(context: Context) : View(context) {

    var sizeMode: IslandSizeMode = IslandSizeMode.COMPACT
        private set

    private var style: IslandDisplayStyle = IslandDisplayStyle.SPOTIFY_YOUTUBE
    private var timerState: TimerState = TimerState()
    private var phase: Float = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#04060C")
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
    private val lipRect = RectF()
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
        isClickable = true
        isFocusable = false
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
        val pad = dp(1.2f)

        // Chiều cao phần hình viên thuốc thực tế:
        // - Khi ở chế độ MINIMIZED (28dp) hoặc COMPACT (31dp), viên thuốc nằm gọn ở phần đỉnh (0..31dp) ôm sát camera,
        //   còn phần dưới (31..64dp) trong suốt để bắt cảm ứng ngón tay bên dưới Status Bar!
        // - Khi ở chế độ EXPANDED, bung toàn bộ khung 208dp từ camera xuống.
        val visualPillHeight = when (sizeMode) {
            IslandSizeMode.MINIMIZED -> dp(28f)
            IslandSizeMode.COMPACT -> dp(31f)
            IslandSizeMode.EXPANDED -> h - pad
        }

        pillRect.set(pad, pad, w - pad, visualPillHeight)
        val corner = when (sizeMode) {
            IslandSizeMode.EXPANDED -> dp(28f)
            else -> visualPillHeight / 2f
        }

        bgPaint.color = Color.parseColor("#04060C")
        canvas.drawRoundRect(pillRect, corner, corner, bgPaint)

        borderPaint.strokeWidth = dp(if (sizeMode == IslandSizeMode.EXPANDED) 1.6f else 1.2f)
        when (style) {
            IslandDisplayStyle.SPOTIFY_YOUTUBE, IslandDisplayStyle.NATIVE_SYSTEM -> {
                borderPaint.shader = LinearGradient(
                    0f, 0f, w, visualPillHeight,
                    intArrayOf(
                        Color.parseColor("#22C55E"),
                        Color.parseColor("#1E293B"),
                        Color.parseColor("#06B6D4")
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            IslandDisplayStyle.NEON_CYBER -> {
                borderPaint.shader = LinearGradient(
                    0f, 0f, w, visualPillHeight,
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

        // Vẽ gờ cảm ứng phát sáng tinh tế ngay dưới chân viên thuốc (ở y = 33..36.5dp, ngay ranh giới dưới Status Bar)
        // để người dùng chạm hoặc kéo xuống cực kỳ nhạy!
        if (sizeMode != IslandSizeMode.EXPANDED) {
            val lipW = dp(34f)
            val lipTop = visualPillHeight + dp(2f)
            val lipBottom = lipTop + dp(3f)
            lipRect.set(w / 2f - lipW / 2f, lipTop, w / 2f + lipW / 2f, lipBottom)
            barPaint.shader = null
            barPaint.color = Color.parseColor("#6638BDF8")
            canvas.drawRoundRect(lipRect, dp(2f), dp(2f), barPaint)
        }

        when (sizeMode) {
            IslandSizeMode.MINIMIZED -> drawMinimizedIsland(canvas, w, visualPillHeight)
            IslandSizeMode.COMPACT -> drawCompactIsland(canvas, w, visualPillHeight)
            IslandSizeMode.EXPANDED -> drawExpandedIsland(canvas, w, h)
        }
    }

    /**
     * Chế độ Thu Nhỏ Siêu Gọn (76x28dp) ôm sát camera:
     * Hiển thị chấm trạng thái bên trái camera và số phút bên phải camera.
     */
    private fun drawMinimizedIsland(canvas: Canvas, w: Float, pillH: Float) {
        val cy = pillH / 2f
        val mins = ((timerState.remainingSeconds + 59) / 60).coerceAtLeast(1L)

        barPaint.shader = null
        barPaint.color = if (timerState.isRunning) {
            Color.parseColor("#22C55E")
        } else {
            Color.parseColor("#38BDF8")
        }
        val pulseR = dp(3.5f) + sin(phase).toFloat() * dp(0.6f)
        canvas.drawCircle(dp(13f), cy, pulseR, barPaint)

        textPaint.textSize = dp(10.5f)
        textPaint.color = if (timerState.isRunning) Color.parseColor("#4ADE80") else Color.WHITE
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${mins}p", w - dp(9f), cy + dp(3.8f), textPaint)
    }

    /**
     * Chế độ Viên Thuốc Chuẩn Ôm Camera (152x31dp — giống hệt YouTube ở ảnh 12:55:29):
     * - Bên trái lỗ đục Camera (0..62dp): Đĩa xoay + Thời gian đếm ngược (MM:SS).
     * - Chính giữa (62..90dp): Khoảng đen tuyền trùng khít với lỗ đục Camera trước.
     * - Bên phải lỗ đục Camera (92..144dp): 5 cột sóng nhạc động (Equalizer) giống YouTube!
     * - Hoàn toàn không tràn sang vùng đồng hồ hệ thống bên trái hay vùng tốc độ mạng / Wi-Fi bên phải!
     */
    private fun drawCompactIsland(canvas: Canvas, w: Float, pillH: Float) {
        val cy = pillH / 2f
        val timeStr = currentFormattedTime().take(5)
        val timeColor = if (timerState.isRunning) Color.parseColor("#4ADE80") else Color.parseColor("#38BDF8")

        // Phần bên trái Camera (0..62dp): Biểu tượng nhỏ + Số đếm ngược MM:SS
        val discCenterX = dp(14f)
        val discRadius = dp(7.5f)
        barPaint.shader = LinearGradient(
            discCenterX - discRadius, cy - discRadius,
            discCenterX + discRadius, cy + discRadius,
            intArrayOf(Color.parseColor("#22C55E"), Color.parseColor("#06B6D4"), Color.parseColor("#3B82F6")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(discCenterX, cy, discRadius, barPaint)
        barPaint.shader = null

        val dotX = discCenterX + cos(phase) * dp(3.5f)
        val dotY = cy + sin(phase) * dp(3.5f)
        barPaint.color = Color.WHITE
        canvas.drawCircle(dotX, dotY, dp(1.5f), barPaint)
        barPaint.color = Color.parseColor("#04060C")
        canvas.drawCircle(discCenterX, cy, dp(2.2f), barPaint)

        // Thời gian đếm ngược nằm ngay cạnh đĩa xoay (24dp..62dp, bên trái lỗ camera giữa 76dp)
        textPaint.textSize = dp(10.5f)
        textPaint.color = timeColor
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(timeStr, dp(24.5f), cy + dp(3.8f), textPaint)

        // Phần bên phải Camera (96..142dp): 5 cột sóng âm giống hệt YouTube!
        val barCount = 5
        val barW = dp(2.4f)
        val gap = dp(2.4f)
        val totalWaveW = barCount * barW + (barCount - 1) * gap
        val startX = w - dp(12f) - totalWaveW

        val waveColors = when (style) {
            IslandDisplayStyle.NEON_CYBER -> intArrayOf(
                Color.parseColor("#22C55E"),
                Color.parseColor("#00E5FF"),
                Color.parseColor("#A855F7"),
                Color.parseColor("#EC4899"),
                Color.parseColor("#38BDF8")
            )
            IslandDisplayStyle.LIQUID_WAVE -> intArrayOf(
                Color.parseColor("#EC4899"),
                Color.parseColor("#8B5CF6"),
                Color.parseColor("#6366F1"),
                Color.parseColor("#06B6D4"),
                Color.parseColor("#22C55E")
            )
            else -> intArrayOf(
                Color.WHITE,
                Color.WHITE,
                Color.WHITE,
                Color.WHITE,
                Color.WHITE
            )
        }

        for (i in 0 until barCount) {
            barPaint.color = waveColors[i % waveColors.size]
            val ratio = 0.25f + 0.75f * ((sin(phase * 1.4f + i * 1.05f) + 1f) / 2f)
            val barH = dp(13f) * ratio
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

    /**
     * Bảng điều khiển mở rộng bung thẳng từ Camera trước (346x208dp):
     * - Vùng đỉnh 0..36dp: Ôm sát lỗ đục camera trước kèm sóng nhạc 2 bên camera.
     * - Toàn bộ nút bấm tương tác ([Mở App], [Thu nhỏ], [▲], Khung đồng hồ, 4 nút dưới đáy)
     *   đều nằm từ y = 40dp..198dp (bên dưới vùng Status Bar 38dp) nên bấm nhạy 100% mọi nút!
     */
    private fun drawExpandedIsland(canvas: Canvas, w: Float, h: Float) {
        val timeStr = currentFormattedTime()

        // Vùng đỉnh quanh camera (y = 18dp): Sóng nhạc trang trí 2 bên lỗ camera
        val topCy = dp(18f)
        barPaint.shader = null
        barPaint.color = Color.parseColor("#22C55E")
        canvas.drawCircle(dp(22f), topCy, dp(4f), barPaint)
        subTextPaint.textSize = dp(10.5f)
        subTextPaint.color = Color.parseColor("#94A3B8")
        subTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Tools Hub • PowerTimer", dp(32f), topCy + dp(3.5f), subTextPaint)

        // Hàng điều khiển trên (y = 38dp..64dp — nằm dưới Status Bar nên bấm ăn 100%!)
        val statusLabel = if (timerState.isRunning) {
            "Đang đếm ngược • Chạm đồng hồ mở App"
        } else {
            "Sẵn sàng • Chọn mốc hẹn hoặc Mở App"
        }
        subTextPaint.textSize = dp(10.5f)
        subTextPaint.color = Color.parseColor("#38BDF8")
        subTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(statusLabel, dp(16f), dp(54f), subTextPaint)

        val collapseRight = w - dp(14f)
        val collapseLeft = collapseRight - dp(32f)
        topCollapseRect.set(collapseLeft, dp(38f), collapseRight, dp(64f))
        btnBgPaint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(topCollapseRect, dp(8f), dp(8f), btnBgPaint)
        subTextPaint.color = Color.WHITE
        subTextPaint.textSize = dp(10.5f)
        subTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("▲", topCollapseRect.centerX(), topCollapseRect.centerY() + dp(3.8f), subTextPaint)

        val minRight = collapseLeft - dp(6f)
        val minLeft = minRight - dp(66f)
        topMinimizeRect.set(minLeft, dp(38f), minRight, dp(64f))
        btnBgPaint.color = Color.parseColor("#334155")
        canvas.drawRoundRect(topMinimizeRect, dp(8f), dp(8f), btnBgPaint)
        subTextPaint.color = Color.parseColor("#FACC15")
        subTextPaint.textSize = dp(10f)
        canvas.drawText("🗕 Thu nhỏ", topMinimizeRect.centerX(), topMinimizeRect.centerY() + dp(3.5f), subTextPaint)

        // Khung đồng hồ lớn (y = 70dp..126dp — chạm vào mở thẳng App!)
        clockBodyRect.set(dp(16f), dp(70f), w - dp(16f), dp(126f))
        btnBgPaint.color = Color.parseColor("#0D1326")
        canvas.drawRoundRect(clockBodyRect, dp(14f), dp(14f), btnBgPaint)

        textPaint.textSize = dp(26f)
        textPaint.color = if (timerState.isRunning) Color.parseColor("#4ADE80") else Color.WHITE
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(timeStr, dp(28f), dp(106f), textPaint)

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
        canvas.drawText(detailTime, dp(138f), dp(93f), subTextPaint)

        // Nút [🚀 Mở App ngay]
        topOpenAppRect.set(dp(138f), dp(99f), dp(232f), dp(121f))
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
        val waveCy = dp(98f)
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

        // Thanh tiến trình (y = 134dp..139dp)
        val barTop = dp(134f)
        val barBottom = dp(139f)
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

        // 4 Nút bấm tương tác dưới đáy (y = 148dp..194dp)
        val btnTop = dp(148f)
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
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                // Nếu đang ở chế độ COMPACT/MINIMIZED và người dùng vuốt nhẹ xuống (pull-down gesture giống YouTube/iOS):
                // Bung ngay bảng điều khiển lớn!
                if (sizeMode != IslandSizeMode.EXPANDED && dy > dp(14f) && abs(dy) > abs(dx) * 1.3f) {
                    isDragging = true
                    runCatching { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                    sizeMode = IslandSizeMode.EXPANDED
                    SystemDynamicIslandManager.onSizeModeChanged(context.applicationContext, sizeMode)
                    return true
                }
                if (sizeMode != IslandSizeMode.EXPANDED && (abs(dx) > dp(16f) || abs(dy) > dp(16f))) {
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
                    runCatching { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                    val now = System.currentTimeMillis()
                    if (sizeMode != IslandSizeMode.EXPANDED && (now - lastTapTimeMs) < 320L) {
                        // Chạm đúp (Double-tap): Mở thẳng vào lại App ngay lập tức!
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
            MotionEvent.ACTION_CANCEL -> {
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
                sizeMode = IslandSizeMode.COMPACT
                SystemDynamicIslandManager.onSizeModeChanged(appCtx, sizeMode)
                return
            }
            IslandSizeMode.COMPACT -> {
                // Chạm 1 lần vào viên thuốc quanh camera -> Bung ngay bảng điều khiển mở rộng!
                sizeMode = IslandSizeMode.EXPANDED
                SystemDynamicIslandManager.onSizeModeChanged(appCtx, sizeMode)
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
