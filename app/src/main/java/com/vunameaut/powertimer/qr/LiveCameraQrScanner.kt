package com.vunameaut.powertimer.qr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vunameaut.powertimer.ui.theme.AccentCyan
import com.vunameaut.powertimer.ui.theme.AccentGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Trình quét mã QR / Barcode thời gian thực bằng Camera2 + TextureView.
 * Chỉ cần hướng camera về phía mã QR là tự động nhận diện ngay lập tức (không cần bấm chụp hay xác nhận).
 */
@Composable
fun LiveCameraQrScanner(
    onCodeScanned: (rawText: String, formatName: String, snapshot: Bitmap?) -> Unit,
    onPickFromGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onCodeScanned)

    var torchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }
    val controllerRef = remember { mutableStateOf<Camera2ScannerController?>(null) }

    DisposableEffect(context, lifecycleOwner) {
        val controller = Camera2ScannerController(
            context = context,
            onError = { err -> cameraError = err }
        )
        controllerRef.value = controller

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    textureViewRef.value?.let { tv ->
                        if (tv.isAvailable) {
                            controller.startCamera(tv, torchEnabled)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    controller.stopCamera()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
            controllerRef.value = null
        }
    }

    // Vòng lặp phân tích khung hình liên tục (mỗi 140ms) để nhận diện QR ngay lập tức
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(140L)
            val tv = textureViewRef.value
            if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0) {
                val frameBmp = withContext(Dispatchers.Main) {
                    runCatching { tv.getBitmap(600, 600) }.getOrNull()
                }
                if (frameBmp != null) {
                    val decoded = withContext(Dispatchers.Default) {
                        // Thử giải mã vùng trung tâm trước (nhanh và chính xác hơn khi đưa QR vào khung ngắm)
                        val centerCrop = runCatching {
                            val cropSize = (frameBmp.width * 0.72f).toInt().coerceAtLeast(200)
                            val startX = ((frameBmp.width - cropSize) / 2).coerceAtLeast(0)
                            val startY = ((frameBmp.height - cropSize) / 2).coerceAtLeast(0)
                            Bitmap.createBitmap(frameBmp, startX, startY, cropSize, cropSize)
                        }.getOrNull()

                        (if (centerCrop != null) VietQrHelper.decodeFromBitmap(centerCrop) else null)
                            ?: VietQrHelper.decodeFromBitmap(frameBmp)
                    }

                    if (decoded != null && decoded.first.isNotBlank()) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        runCatching {
                            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                                .startTone(ToneGenerator.TONE_PROP_ACK, 120)
                        }
                        currentOnScanned(decoded.first, decoded.second, frameBmp)
                        break
                    }
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "qr_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, AccentCyan.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = false
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                controllerRef.value?.startCamera(this@apply, torchEnabled)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                controllerRef.value?.configureTransform(this@apply, width, height)
                            }

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                controllerRef.value?.stopCamera()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                        }
                        textureViewRef.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Khung ngắm QR & Tia laser quét tự động
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxSize = size.minDimension * 0.66f
                val left = (size.width - boxSize) / 2f
                val top = (size.height - boxSize) / 2f
                val right = left + boxSize
                val bottom = top + boxSize
                val cornerLen = 32.dp.toPx()
                val strokeW = 4.dp.toPx()

                // Viền mờ khung ngắm
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(left, top),
                    size = ComposeSize(boxSize, boxSize),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 4 góc sáng màu xanh Cyan/Green
                val cornerColor = AccentGreen
                // Góc trên trái
                drawLine(cornerColor, Offset(left, top + cornerLen), Offset(left, top), strokeW, StrokeCap.Round)
                drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
                // Góc trên phải
                drawLine(cornerColor, Offset(right - cornerLen, top), Offset(right, top), strokeW, StrokeCap.Round)
                drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLen), strokeW, StrokeCap.Round)
                // Góc dưới trái
                drawLine(cornerColor, Offset(left, bottom - cornerLen), Offset(left, bottom), strokeW, StrokeCap.Round)
                drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW, StrokeCap.Round)
                // Góc dưới phải
                drawLine(cornerColor, Offset(right - cornerLen, bottom), Offset(right, bottom), strokeW, StrokeCap.Round)
                drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeW, StrokeCap.Round)

                // Tia laser chạy dọc khung ngắm
                val laserY = top + boxSize * laserProgress
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AccentCyan,
                            AccentGreen,
                            AccentCyan,
                            Color.Transparent
                        ),
                        startX = left + 10f,
                        endX = right - 10f
                    ),
                    start = Offset(left + 10f, laserY),
                    end = Offset(right - 10f, laserY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Nhãn hướng dẫn phía trên
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Hướng camera vào mã QR / Barcode để tự động đọc ngay",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (cameraError != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Text(
                        text = cameraError ?: "",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // Hàng nút điều khiển nhanh dưới khung Camera: Bật đèn Flash & Chọn ảnh từ Thư viện
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    torchEnabled = !torchEnabled
                    controllerRef.value?.setTorch(torchEnabled)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (torchEnabled) AccentGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (torchEnabled) Color.Black else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (torchEnabled) "Tắt đèn Flash" else "Bật đèn Flash",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onPickFromGallery,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Ảnh trong máy",
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private class Camera2ScannerController(
    private val context: Context,
    private val onError: (String?) -> Unit
) {
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSize: Size = Size(1280, 960)
    private var isTorchOn: Boolean = false

    private fun ensureThread() {
        if (cameraThread == null) {
            cameraThread = HandlerThread("QrCamera2Thread").apply { start() }
            cameraHandler = Handler(cameraThread!!.looper)
        }
    }

    @SuppressLint("MissingPermission")
    fun startCamera(textureView: TextureView, torch: Boolean) {
        stopCamera()
        ensureThread()
        isTorchOn = torch

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()
        }.getOrNull()

        if (cameraId == null) {
            onError("Không tìm thấy Camera trên thiết bị.")
            return
        }

        runCatching {
            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java)
            if (!outputSizes.isNullOrEmpty()) {
                previewSize = outputSizes
                    .filter { it.width in 640..1920 && it.height in 480..1920 }
                    .minByOrNull { abs(it.width * it.height - 1280 * 960) }
                    ?: outputSizes.first()
            }
            configureTransform(textureView, textureView.width, textureView.height)

            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        onError(null)
                        createPreviewSession(camera, textureView)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        onError("Không thể mở Camera (Mã lỗi $error)")
                    }
                },
                cameraHandler
            )
        }.onFailure {
            onError("Lỗi khởi tạo Camera: ${it.localizedMessage}")
        }
    }

    private fun createPreviewSession(camera: CameraDevice, textureView: TextureView) {
        runCatching {
            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)

            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                set(
                    CaptureRequest.FLASH_MODE,
                    if (isTorchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
                )
            }
            previewRequestBuilder = builder

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        runCatching {
                            session.setRepeatingRequest(builder.build(), null, cameraHandler)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError("Không thể cấu hình luồng xem trước Camera.")
                    }
                },
                cameraHandler
            )
        }
    }

    fun setTorch(enabled: Boolean) {
        isTorchOn = enabled
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        runCatching {
            builder.set(
                CaptureRequest.FLASH_MODE,
                if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
            )
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        }
    }

    fun configureTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        textureView.post {
            runCatching {
                val matrix = Matrix()
                val viewRectWidth = viewWidth.toFloat()
                val viewRectHeight = viewHeight.toFloat()
                // Camera ở chế độ dọc (portrait): chiều rộng và cao của cảm biến xoay 90 độ
                val bufferWidth = previewSize.height.toFloat()
                val bufferHeight = previewSize.width.toFloat()

                val scaleX = viewRectWidth / bufferWidth
                val scaleY = viewRectHeight / bufferHeight
                val maxScale = maxOf(scaleX, scaleY)

                val scaledX = (bufferWidth * maxScale) / viewRectWidth
                val scaledY = (bufferHeight * maxScale) / viewRectHeight

                matrix.setScale(scaledX, scaledY, viewRectWidth / 2f, viewRectHeight / 2f)
                textureView.setTransform(matrix)
            }
        }
    }

    fun stopCamera() {
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        previewRequestBuilder = null
    }

    fun release() {
        stopCamera()
        runCatching { cameraThread?.quitSafely() }
        cameraThread = null
        cameraHandler = null
    }
}

