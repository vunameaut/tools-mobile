package com.vunameaut.powertimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.vunameaut.powertimer.MainActivity
import com.vunameaut.powertimer.R
import com.vunameaut.powertimer.downloader.DirectVideoExtractor
import com.vunameaut.powertimer.downloader.DownloadStatus
import com.vunameaut.powertimer.downloader.DownloadTaskState
import com.vunameaut.powertimer.downloader.ExtractedVideo
import com.vunameaut.powertimer.downloader.VideoPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VideoDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "VideoDownloadService"

        const val ACTION_START_DOWNLOAD = "com.vunameaut.powertimer.action.START_VIDEO_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.vunameaut.powertimer.action.CANCEL_VIDEO_DOWNLOAD"
        const val EXTRA_URL = "extra_video_url"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_OPEN_DOWNLOADER = "extra_open_video_downloader"

        private const val PROGRESS_CHANNEL_ID = "video_download_progress_channel"
        private const val RESULT_CHANNEL_ID = "video_download_result_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 4100

        private const val HISTORY_PREFS = "video_download_history_prefs"
        private const val KEY_HISTORY_JSON = "history_json"

        private val _tasks = MutableStateFlow<List<DownloadTaskState>>(emptyList())
        val tasks: StateFlow<List<DownloadTaskState>> = _tasks.asStateFlow()

        @Volatile
        private var historyLoaded = false

        fun ensureHistoryLoaded(context: Context) {
            if (historyLoaded) return
            synchronized(this) {
                if (historyLoaded) return
                val prefs = context.applicationContext.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_HISTORY_JSON, null)
                if (!raw.isNullOrBlank()) {
                    try {
                        val arr = JSONArray(raw)
                        val loaded = mutableListOf<DownloadTaskState>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            loaded.add(
                                DownloadTaskState(
                                    id = obj.optString("id", UUID.randomUUID().toString()),
                                    sourceUrl = obj.optString("sourceUrl"),
                                    platform = runCatching {
                                        VideoPlatform.valueOf(obj.optString("platform", "OTHER"))
                                    }.getOrDefault(VideoPlatform.OTHER),
                                    title = obj.optString("title", "Video"),
                                    author = obj.optString("author", ""),
                                    engineUsed = obj.optString("engineUsed", "On-Device"),
                                    status = runCatching {
                                        DownloadStatus.valueOf(obj.optString("status", "COMPLETED"))
                                    }.getOrDefault(DownloadStatus.COMPLETED),
                                    progress = obj.optDouble("progress", 1.0).toFloat(),
                                    downloadedBytes = obj.optLong("downloadedBytes", 0L),
                                    totalBytes = obj.optLong("totalBytes", 0L),
                                    savedUri = obj.optString("savedUri").takeIf { it.isNotBlank() },
                                    savedFilePath = obj.optString("savedFilePath").takeIf { it.isNotBlank() },
                                    savedFileName = obj.optString("savedFileName").takeIf { it.isNotBlank() },
                                    errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                                )
                            )
                        }
                        _tasks.update { current ->
                            val existingIds = current.map { it.id }.toSet()
                            current + loaded.filterNot { it.id in existingIds }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to load download history: ${e.message}")
                    }
                }
                historyLoaded = true
            }
        }

        private fun persistHistory(context: Context) {
            try {
                val completedOrFailed = _tasks.value
                    .filter { it.status in listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED) }
                    .take(35)
                val arr = JSONArray()
                for (item in completedOrFailed) {
                    arr.put(
                        JSONObject().apply {
                            put("id", item.id)
                            put("sourceUrl", item.sourceUrl)
                            put("platform", item.platform.name)
                            put("title", item.title)
                            put("author", item.author)
                            put("engineUsed", item.engineUsed)
                            put("status", item.status.name)
                            put("progress", item.progress.toDouble())
                            put("downloadedBytes", item.downloadedBytes)
                            put("totalBytes", item.totalBytes)
                            put("savedUri", item.savedUri.orEmpty())
                            put("savedFilePath", item.savedFilePath.orEmpty())
                            put("savedFileName", item.savedFileName.orEmpty())
                            put("errorMessage", item.errorMessage.orEmpty())
                            put("createdAt", item.createdAt)
                        }
                    )
                }
                context.applicationContext.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_HISTORY_JSON, arr.toString())
                    .apply()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to persist history: ${e.message}")
            }
        }

        fun startDownload(context: Context, rawUrl: String) {
            val cleanUrl = VideoPlatform.extractFirstUrl(rawUrl) ?: rawUrl.trim()
            if (cleanUrl.isBlank()) return

            ensureHistoryLoaded(context)
            val intent = Intent(context, VideoDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, cleanUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, taskId: String) {
            val intent = Intent(context, VideoDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }

        fun removeTaskFromHistory(context: Context, taskId: String) {
            _tasks.update { list -> list.filterNot { it.id == taskId } }
            persistHistory(context)
        }

        fun clearCompletedHistory(context: Context) {
            _tasks.update { list ->
                list.filter { it.status == DownloadStatus.EXTRACTING || it.status == DownloadStatus.DOWNLOADING }
            }
            persistHistory(context)
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 KB"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                String.format("%.0f KB", bytes.toDouble() / 1024.0)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        ensureHistoryLoaded(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
                if (url.isNotBlank()) {
                    enqueueDownload(url)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
                cancelTaskInternal(taskId)
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueueDownload(url: String) {
        val taskId = UUID.randomUUID().toString()
        val platform = VideoPlatform.detect(url)
        val initialTask = DownloadTaskState(
            id = taskId,
            sourceUrl = url,
            platform = platform,
            title = "Đang phân tích link ${platform.displayName}...",
            engineUsed = "Đang dò luồng trực tiếp...",
            status = DownloadStatus.EXTRACTING,
            progress = 0f
        )

        _tasks.update { listOf(initialTask) + it }
        promoteToForeground(initialTask)

        val job = serviceScope.launch {
            try {
                val extractionResult = DirectVideoExtractor.extract(
                    context = applicationContext,
                    rawUrl = url,
                    onStatusUpdate = { statusMsg ->
                        updateTask(taskId) {
                            it.copy(title = statusMsg)
                        }
                        getTask(taskId)?.let { updateProgressNotification(it) }
                    }
                )

                val extracted = extractionResult.getOrThrow()
                updateTask(taskId) {
                    it.copy(
                        title = extracted.title,
                        author = extracted.author,
                        engineUsed = extracted.engineUsed,
                        status = DownloadStatus.DOWNLOADING,
                        progress = 0.02f
                    )
                }
                getTask(taskId)?.let { updateProgressNotification(it) }

                // Thực hiện tải file về bộ nhớ máy (Movies/ToolsHub)
                val savedResult = try {
                    downloadStreamToMediaStore(taskId, extracted)
                } catch (streamErr: Throwable) {
                    if (streamErr is CancellationException) throw streamErr
                    Log.w(TAG, "Direct stream download failed (${streamErr.message}), trying Server Cloud proxy...")
                    // Nếu link trực tiếp bị CDN chặn giữa chừng, tự động fallback qua Server Proxy
                    val fallbackUrl = "${DirectVideoExtractor.getServerUrl(applicationContext)}/api/proxy-url"
                    val serverExtracted = fallbackExtractFromServer(url, platform, fallbackUrl)
                        ?: throw streamErr
                    updateTask(taskId) {
                        it.copy(engineUsed = serverExtracted.engineUsed)
                    }
                    downloadStreamToMediaStore(taskId, serverExtracted)
                }

                val finishedTask = updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        savedUri = savedResult.uriString,
                        savedFilePath = savedResult.filePath,
                        savedFileName = savedResult.fileName,
                        speedBytesPerSec = 0L
                    )
                }
                persistHistory(applicationContext)
                if (finishedTask != null) {
                    showCompletedNotification(finishedTask)
                }
            } catch (ce: CancellationException) {
                updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.CANCELLED,
                        errorMessage = "Đã huỷ tải xuống",
                        speedBytesPerSec = 0L
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Download failed for $url", e)
                val failedTask = updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Lỗi không xác định khi tải video",
                        speedBytesPerSec = 0L
                    )
                }
                persistHistory(applicationContext)
                if (failedTask != null) {
                    showFailedNotification(failedTask)
                }
            } finally {
                activeJobs.remove(taskId)
                checkAndStopForegroundIfIdle()
            }
        }

        activeJobs[taskId] = job
    }

    private fun fallbackExtractFromServer(
        url: String,
        platform: VideoPlatform,
        endpoint: String
    ): ExtractedVideo? {
        return try {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 25000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().put("url", url).toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val dlUrl = json.optString("download_url")
            if (dlUrl.isBlank()) return null
            ExtractedVideo(
                sourceUrl = url,
                downloadUrl = dlUrl,
                title = DirectVideoExtractor.sanitizeTitle(json.optString("title", "Video")),
                author = json.optString("author"),
                platform = platform,
                engineUsed = "Server Cloud Proxy"
            )
        } catch (_: Throwable) {
            null
        }
    }

    private data class SavedVideoFile(
        val uriString: String,
        val filePath: String?,
        val fileName: String
    )

    private fun downloadStreamToMediaStore(
        taskId: String,
        extracted: ExtractedVideo
    ): SavedVideoFile {
        val conn = openVideoStreamConnection(extracted.downloadUrl, extracted.headers)
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("Máy chủ trả về mã HTTP $responseCode")
        }

        val contentType = conn.contentType?.lowercase().orEmpty()
        if (contentType.contains("text/html") || contentType.contains("application/json")) {
            throw IllegalStateException("Luồng trả về không phải file video ($contentType)")
        }

        val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: -1L
        val safeBaseName = DirectVideoExtractor.sanitizeTitle(extracted.title)
            .replace(' ', '_')
            .take(65)
        val fileName = "${extracted.platform.displayName}_${safeBaseName}_${System.currentTimeMillis() / 1000}.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ToolsHub")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Không thể tạo file trong thư mục Movies/ToolsHub")

            try {
                resolver.openOutputStream(itemUri)?.use { outStream ->
                    conn.inputStream.use { inStream ->
                        copyWithProgress(taskId, inStream, outStream, totalBytes)
                    }
                } ?: throw IllegalStateException("Không thể mở luồng ghi MediaStore")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
                return SavedVideoFile(
                    uriString = itemUri.toString(),
                    filePath = "${Environment.DIRECTORY_MOVIES}/ToolsHub/$fileName",
                    fileName = fileName
                )
            } catch (e: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw e
            }
        } else {
            val publicMovies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val targetDir = File(publicMovies, "ToolsHub").apply { mkdirs() }
            val outFile = if (targetDir.canWrite()) {
                File(targetDir, fileName)
            } else {
                val appMovies = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ToolsHub").apply { mkdirs() }
                File(appMovies, fileName)
            }

            try {
                FileOutputStream(outFile).use { outStream ->
                    conn.inputStream.use { inStream ->
                        copyWithProgress(taskId, inStream, outStream, totalBytes)
                    }
                }
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(outFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                val fileUri = FileProvider.getUriForFile(
                    applicationContext,
                    "${applicationContext.packageName}.fileprovider",
                    outFile
                )
                return SavedVideoFile(
                    uriString = fileUri.toString(),
                    filePath = outFile.absolutePath,
                    fileName = fileName
                )
            } catch (e: Throwable) {
                runCatching { outFile.delete() }
                throw e
            }
        }
    }

    private fun openVideoStreamConnection(
        streamUrl: String,
        headers: Map<String, String>,
        maxRedirects: Int = 6
    ): HttpURLConnection {
        var currentUrl = streamUrl
        repeat(maxRedirects) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location") ?: return conn
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        return (URL(currentUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 30000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
    }

    private fun copyWithProgress(
        taskId: String,
        input: InputStream,
        output: OutputStream,
        totalBytes: Long
    ) {
        val buffer = ByteArray(32 * 1024)
        var downloaded = 0L
        var lastUiUpdateMs = 0L
        var bytesAtLastWindow = 0L
        var windowStartMs = System.currentTimeMillis()

        while (true) {
            val job = activeJobs[taskId]
            if (job != null && !job.isActive) {
                throw CancellationException("Download cancelled")
            }

            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            downloaded += read

            val now = System.currentTimeMillis()
            if (now - lastUiUpdateMs >= 250L) {
                val elapsedSec = ((now - windowStartMs).coerceAtLeast(1L)) / 1000.0
                val speed = ((downloaded - bytesAtLastWindow) / elapsedSec).toLong().coerceAtLeast(0L)
                if (now - windowStartMs >= 1000L) {
                    windowStartMs = now
                    bytesAtLastWindow = downloaded
                }

                val pct = if (totalBytes > 0L) {
                    (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 0.99f)
                } else {
                    0.5f
                }

                val updated = updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = pct,
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes.coerceAtLeast(0L),
                        speedBytesPerSec = speed
                    )
                }
                if (updated != null) {
                    updateProgressNotification(updated)
                }
                lastUiUpdateMs = now
            }
        }
        output.flush()
        if (downloaded < 1024L) {
            throw IllegalStateException("File tải về quá nhỏ hoặc rỗng ($downloaded bytes)")
        }
    }

    private fun cancelTaskInternal(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        updateTask(taskId) {
            it.copy(
                status = DownloadStatus.CANCELLED,
                errorMessage = "Đã huỷ bởi người dùng",
                speedBytesPerSec = 0L
            )
        }
        checkAndStopForegroundIfIdle()
    }

    private fun getTask(taskId: String): DownloadTaskState? =
        _tasks.value.firstOrNull { it.id == taskId }

    private fun updateTask(
        taskId: String,
        transform: (DownloadTaskState) -> DownloadTaskState
    ): DownloadTaskState? {
        var updatedItem: DownloadTaskState? = null
        _tasks.update { list ->
            list.map { item ->
                if (item.id == taskId) {
                    transform(item).also { updatedItem = it }
                } else {
                    item
                }
            }
        }
        return updatedItem
    }

    private fun promoteToForeground(task: DownloadTaskState) {
        val notification = buildProgressNotification(task)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "startForeground failed, falling back to notify: ${e.message}")
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun updateProgressNotification(task: DownloadTaskState) {
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildProgressNotification(task))
    }

    private fun buildProgressNotification(task: DownloadTaskState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DOWNLOADER, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VideoDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            task.id.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isIndeterminate = task.status == DownloadStatus.EXTRACTING || task.totalBytes <= 0L
        val pctInt = (task.progress * 100).toInt().coerceIn(0, 100)

        val subtitle = when (task.status) {
            DownloadStatus.EXTRACTING -> "Đang giải mã link ${task.platform.displayName}..."
            DownloadStatus.DOWNLOADING -> {
                val speedText = if (task.speedBytesPerSec > 0) {
                    " • ${formatBytes(task.speedBytesPerSec)}/s"
                } else ""
                if (task.totalBytes > 0L) {
                    "$pctInt% • ${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}$speedText"
                } else {
                    "Đã tải ${formatBytes(task.downloadedBytes)}$speedText"
                }
            }
            else -> task.title
        }

        return NotificationCompat.Builder(this, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("[${task.platform.displayName}] ${task.title}")
            .setContentText(subtitle)
            .setSubText(task.engineUsed)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, pctInt, isIndeterminate)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Huỷ",
                cancelPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun showCompletedNotification(task: DownloadTaskState) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DOWNLOADER, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            task.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Đã tải xong video ${task.platform.displayName}!")
            .setContentText("${task.title} (${formatBytes(task.downloadedBytes)})")
            .setSubText("Lưu tại: Movies/ToolsHub")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)

        if (!task.savedUri.isNullOrBlank()) {
            val videoUri = Uri.parse(task.savedUri)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(videoUri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val viewPending = PendingIntent.getActivity(
                this,
                task.id.hashCode() + 1,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Mở video", viewPending)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Chia sẻ video")
            val sharePending = PendingIntent.getActivity(
                this,
                task.id.hashCode() + 2,
                chooser,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_share, "Chia sẻ", sharePending)
        }

        notificationManager.notify(task.id.hashCode(), builder.build())
    }

    private fun showFailedNotification(task: DownloadTaskState) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DOWNLOADER, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            task.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tải video ${task.platform.displayName} thất bại")
            .setContentText(task.errorMessage ?: "Không thể tải video từ link này")
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.errorMessage))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(task.id.hashCode(), notification)
    }

    private fun checkAndStopForegroundIfIdle() {
        if (activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            val activeTask = _tasks.value.firstOrNull {
                it.status == DownloadStatus.EXTRACTING || it.status == DownloadStatus.DOWNLOADING
            }
            if (activeTask != null) {
                updateProgressNotification(activeTask)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "Tiến trình tải Video",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị phần trăm và tốc độ khi đang tải video"
                setShowBadge(false)
            }

            val resultChannel = NotificationChannel(
                RESULT_CHANNEL_ID,
                "Thông báo hoàn tất tải Video",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi video tải thành công hoặc gặp lỗi"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(resultChannel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

