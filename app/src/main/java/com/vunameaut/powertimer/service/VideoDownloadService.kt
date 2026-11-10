package com.vunameaut.powertimer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
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
import com.vunameaut.powertimer.downloader.DownloadPreference
import com.vunameaut.powertimer.downloader.DownloadStatus
import com.vunameaut.powertimer.downloader.DownloadTaskState
import com.vunameaut.powertimer.downloader.ExtractedMediaItem
import com.vunameaut.powertimer.downloader.ExtractedVideo
import com.vunameaut.powertimer.downloader.MediaType
import com.vunameaut.powertimer.downloader.StoryLoginRequiredException
import com.vunameaut.powertimer.downloader.VideoFormatOption
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
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VideoDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private lateinit var notificationManager: NotificationManager

    private data class PreExtractedRequest(
        val extracted: ExtractedVideo,
        val selectedFormat: VideoFormatOption?,
        val downloadAllPhotos: Boolean
    )

    companion object {
        private const val TAG = "VideoDownloadService"

        const val ACTION_START_DOWNLOAD = "com.vunameaut.powertimer.action.START_VIDEO_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.vunameaut.powertimer.action.CANCEL_VIDEO_DOWNLOAD"
        const val EXTRA_URL = "extra_video_url"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_PREF_OVERRIDE = "extra_pref_override"
        const val EXTRA_PRE_EXTRACTED_KEY = "extra_pre_extracted_key"
        const val EXTRA_OPEN_DOWNLOADER = "extra_open_video_downloader"

        private const val PROGRESS_CHANNEL_ID = "video_download_progress_channel"
        private const val RESULT_CHANNEL_ID = "video_download_result_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 4100

        private const val HISTORY_PREFS = "video_download_history_prefs"
        private const val KEY_HISTORY_JSON = "history_json"

        private val preExtractedQueue = ConcurrentHashMap<String, PreExtractedRequest>()

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
                            val urisArr = obj.optJSONArray("savedUris")
                            val savedUrisList = mutableListOf<String>()
                            if (urisArr != null) {
                                for (j in 0 until urisArr.length()) {
                                    val u = urisArr.optString(j)
                                    if (u.isNotBlank()) savedUrisList.add(u)
                                }
                            }
                            val singleUri = obj.optString("savedUri").takeIf { it.isNotBlank() }
                            if (savedUrisList.isEmpty() && singleUri != null) {
                                savedUrisList.add(singleUri)
                            }

                            loaded.add(
                                DownloadTaskState(
                                    id = obj.optString("id", UUID.randomUUID().toString()),
                                    sourceUrl = obj.optString("sourceUrl"),
                                    platform = runCatching {
                                        VideoPlatform.valueOf(obj.optString("platform", "OTHER"))
                                    }.getOrDefault(VideoPlatform.OTHER),
                                    title = obj.optString("title", "Media"),
                                    author = obj.optString("author", ""),
                                    engineUsed = obj.optString("engineUsed", "On-Device"),
                                    status = runCatching {
                                        DownloadStatus.valueOf(obj.optString("status", "COMPLETED"))
                                    }.getOrDefault(DownloadStatus.COMPLETED),
                                    progress = obj.optDouble("progress", 1.0).toFloat(),
                                    downloadedBytes = obj.optLong("downloadedBytes", 0L),
                                    totalBytes = obj.optLong("totalBytes", 0L),
                                    mediaType = runCatching {
                                        MediaType.valueOf(obj.optString("mediaType", "VIDEO"))
                                    }.getOrDefault(MediaType.VIDEO),
                                    qualityLabel = obj.optString("qualityLabel", ""),
                                    totalItems = obj.optInt("totalItems", 1),
                                    completedItems = obj.optInt("completedItems", 1),
                                    savedUri = singleUri,
                                    savedUris = savedUrisList,
                                    savedFilePath = obj.optString("savedFilePath").takeIf { it.isNotBlank() },
                                    savedFileName = obj.optString("savedFileName").takeIf { it.isNotBlank() },
                                    errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                                    requiresLogin = obj.optBoolean("requiresLogin", false),
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
                    val urisArr = JSONArray()
                    item.savedUris.forEach { urisArr.put(it) }
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
                            put("mediaType", item.mediaType.name)
                            put("qualityLabel", item.qualityLabel)
                            put("totalItems", item.totalItems)
                            put("completedItems", item.completedItems)
                            put("savedUri", item.savedUri.orEmpty())
                            put("savedUris", urisArr)
                            put("savedFilePath", item.savedFilePath.orEmpty())
                            put("savedFileName", item.savedFileName.orEmpty())
                            put("errorMessage", item.errorMessage.orEmpty())
                            put("requiresLogin", item.requiresLogin)
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

        fun startDownload(
            context: Context,
            rawUrl: String,
            preferenceOverride: DownloadPreference? = null
        ) {
            val cleanUrl = VideoPlatform.extractFirstUrl(rawUrl) ?: rawUrl.trim()
            if (cleanUrl.isBlank()) return

            ensureHistoryLoaded(context)
            val intent = Intent(context, VideoDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, cleanUrl)
                if (preferenceOverride != null) {
                    putExtra(EXTRA_PREF_OVERRIDE, preferenceOverride.name)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startDownloadWithFormat(
            context: Context,
            extracted: ExtractedVideo,
            selectedFormat: VideoFormatOption? = null,
            downloadAllPhotos: Boolean = false
        ) {
            ensureHistoryLoaded(context)
            val key = UUID.randomUUID().toString()
            preExtractedQueue[key] = PreExtractedRequest(
                extracted = extracted,
                selectedFormat = selectedFormat,
                downloadAllPhotos = downloadAllPhotos
            )
            val intent = Intent(context, VideoDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, extracted.sourceUrl)
                putExtra(EXTRA_PRE_EXTRACTED_KEY, key)
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
                val prefRaw = intent.getStringExtra(EXTRA_PREF_OVERRIDE)
                val prefOverride = prefRaw?.let {
                    runCatching { DownloadPreference.valueOf(it) }.getOrNull()
                }
                val preKey = intent.getStringExtra(EXTRA_PRE_EXTRACTED_KEY)
                val preReq = if (!preKey.isNullOrBlank()) preExtractedQueue.remove(preKey) else null

                if (url.isNotBlank() || preReq != null) {
                    enqueueDownload(url, prefOverride, preReq)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
                cancelTaskInternal(taskId)
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueueDownload(
        url: String,
        prefOverride: DownloadPreference?,
        preReq: PreExtractedRequest?
    ) {
        val taskId = UUID.randomUUID().toString()
        val sourceUrl = preReq?.extracted?.sourceUrl ?: url
        val platform = preReq?.extracted?.platform ?: VideoPlatform.detect(sourceUrl)
        val initialTask = DownloadTaskState(
            id = taskId,
            sourceUrl = sourceUrl,
            platform = platform,
            title = preReq?.extracted?.title ?: "Đang phân tích link ${platform.displayName}...",
            engineUsed = preReq?.extracted?.engineUsed ?: "Đang dò luồng trực tiếp...",
            status = DownloadStatus.EXTRACTING,
            progress = 0f
        )

        _tasks.update { listOf(initialTask) + it }
        promoteToForeground(initialTask)

        val job = serviceScope.launch {
            try {
                val extracted = if (preReq != null) {
                    preReq.extracted
                } else {
                    val extractionResult = DirectVideoExtractor.extract(
                        context = applicationContext,
                        rawUrl = sourceUrl,
                        onStatusUpdate = { statusMsg ->
                            updateTask(taskId) { it.copy(title = statusMsg) }
                            getTask(taskId)?.let { updateProgressNotification(it) }
                        }
                    )
                    extractionResult.getOrThrow()
                }

                val effectivePref = prefOverride ?: DirectVideoExtractor.getDownloadPreference(applicationContext)

                // Kiểm tra xem có phải tải bộ nhiều ảnh / Carousel không
                val shouldDownloadPhotoItems = when {
                    preReq?.downloadAllPhotos == true -> true
                    preReq?.selectedFormat != null -> false
                    extracted.mediaItems.isNotEmpty() && effectivePref != DownloadPreference.AUDIO_MP3 -> true
                    extracted.mediaType == MediaType.IMAGE && extracted.mediaItems.isNotEmpty() -> true
                    else -> false
                }

                if (shouldDownloadPhotoItems && extracted.mediaItems.isNotEmpty()) {
                    downloadMultipleMediaItems(taskId, extracted, extracted.mediaItems)
                } else {
                    val chosenFormat = preReq?.selectedFormat ?: selectFormatForPreference(extracted, effectivePref)
                    downloadSingleFormat(taskId, extracted, chosenFormat)
                }
            } catch (ce: CancellationException) {
                updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.CANCELLED,
                        errorMessage = "Đã huỷ tải xuống",
                        speedBytesPerSec = 0L
                    )
                }
            } catch (loginEx: StoryLoginRequiredException) {
                Log.w(TAG, "Story login required for $sourceUrl: ${loginEx.message}")
                val failedTask = updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = loginEx.message,
                        requiresLogin = true,
                        speedBytesPerSec = 0L
                    )
                }
                persistHistory(applicationContext)
                if (failedTask != null) {
                    showFailedNotification(failedTask)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Download failed for $sourceUrl", e)
                val isLoginErr = e.message?.contains("login.php", ignoreCase = true) == true ||
                    e.message?.contains("đăng nhập", ignoreCase = true) == true
                val failedTask = updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Lỗi không xác định khi tải",
                        requiresLogin = isLoginErr,
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

    private fun selectFormatForPreference(
        extracted: ExtractedVideo,
        pref: DownloadPreference
    ): VideoFormatOption {
        val formats = extracted.formats
        return when (pref) {
            DownloadPreference.AUDIO_MP3 -> {
                formats.firstOrNull { it.mediaType == MediaType.AUDIO }
                    ?: VideoFormatOption(
                        id = "audio_mp3",
                        label = "🎵 Nhạc MP3 / Audio",
                        qualityBadge = "MP3",
                        downloadUrl = extracted.downloadUrl,
                        mediaType = MediaType.AUDIO,
                        ext = "m4a",
                        extractAudioFromVideo = true,
                        headers = extracted.headers
                    )
            }
            DownloadPreference.VIDEO_SD -> {
                formats.lastOrNull { it.mediaType == MediaType.VIDEO }
                    ?: VideoFormatOption(
                        id = "video_sd",
                        label = "Video SD",
                        qualityBadge = "SD",
                        downloadUrl = extracted.downloadUrl,
                        mediaType = MediaType.VIDEO,
                        ext = "mp4",
                        headers = extracted.headers
                    )
            }
            else -> {
                formats.firstOrNull { it.mediaType == MediaType.VIDEO }
                    ?: VideoFormatOption(
                        id = "video_hd",
                        label = "Video HD",
                        qualityBadge = "HD",
                        downloadUrl = extracted.downloadUrl,
                        mediaType = extracted.mediaType,
                        ext = if (extracted.mediaType == MediaType.IMAGE) "jpg" else "mp4",
                        headers = extracted.headers
                    )
            }
        }
    }

    // ========================================================================
    // TẢI HÀNG LOẠT ẢNH / CAROUSEL (Instagram nhiều ảnh, TikTok ảnh, FB Album, X)
    // ========================================================================
    private fun downloadMultipleMediaItems(
        taskId: String,
        extracted: ExtractedVideo,
        items: List<ExtractedMediaItem>
    ) {
        val total = items.size
        updateTask(taskId) {
            it.copy(
                title = extracted.title,
                author = extracted.author,
                engineUsed = extracted.engineUsed,
                mediaType = MediaType.IMAGE,
                qualityLabel = "$total ảnh gốc",
                totalItems = total,
                completedItems = 0,
                status = DownloadStatus.DOWNLOADING,
                progress = 0.02f
            )
        }
        getTask(taskId)?.let { updateProgressNotification(it) }

        val savedUris = mutableListOf<String>()
        var lastSavedFile: SavedMediaFile? = null
        var cumulativeBytes = 0L

        for ((index, item) in items.withIndex()) {
            val job = activeJobs[taskId]
            if (job != null && !job.isActive) {
                throw CancellationException("Download cancelled")
            }

            val currentNum = index + 1
            updateTask(taskId) {
                it.copy(
                    completedItems = index,
                    progress = (index.toFloat() / total.toFloat()).coerceIn(0.02f, 0.98f)
                )
            }
            getTask(taskId)?.let { updateProgressNotification(it) }

            val itemHeaders = if (item.headers.isNotEmpty()) item.headers else extracted.headers
            val saved = downloadSingleStreamToMediaStore(
                taskId = taskId,
                streamUrl = item.url,
                headers = itemHeaders,
                platform = extracted.platform,
                baseTitle = "${extracted.title}_${item.titleSuffix.ifBlank { "Anh_$currentNum" }}",
                mediaType = item.mediaType,
                ext = item.ext,
                extractAudioFromVideo = false,
                itemIndex = index,
                totalItems = total
            )
            savedUris.add(saved.uriString)
            lastSavedFile = saved
            cumulativeBytes += saved.bytesWritten
        }

        val finishedTask = updateTask(taskId) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                completedItems = total,
                totalItems = total,
                downloadedBytes = cumulativeBytes,
                totalBytes = cumulativeBytes,
                savedUri = savedUris.firstOrNull(),
                savedUris = savedUris,
                savedFilePath = lastSavedFile?.filePath,
                savedFileName = if (total > 1) "${total} ảnh trong Pictures/ToolsHub" else lastSavedFile?.fileName,
                speedBytesPerSec = 0L
            )
        }
        persistHistory(applicationContext)
        if (finishedTask != null) {
            showCompletedNotification(finishedTask)
        }
    }

    // ========================================================================
    // TẢI ĐƠN LẺ VIDEO (HD / SD) HOẶC TẢI / TÁCH NHẠC MP3 / AUDIO
    // ========================================================================
    private fun downloadSingleFormat(
        taskId: String,
        extracted: ExtractedVideo,
        format: VideoFormatOption
    ) {
        updateTask(taskId) {
            it.copy(
                title = extracted.title,
                author = extracted.author,
                engineUsed = extracted.engineUsed,
                mediaType = format.mediaType,
                qualityLabel = format.qualityBadge,
                totalItems = 1,
                completedItems = 0,
                status = DownloadStatus.DOWNLOADING,
                progress = 0.02f
            )
        }
        getTask(taskId)?.let { updateProgressNotification(it) }

        val headers = if (format.headers.isNotEmpty()) format.headers else extracted.headers
        val savedResult = try {
            downloadSingleStreamToMediaStore(
                taskId = taskId,
                streamUrl = format.downloadUrl,
                headers = headers,
                platform = extracted.platform,
                baseTitle = extracted.title,
                mediaType = format.mediaType,
                ext = format.ext,
                extractAudioFromVideo = format.extractAudioFromVideo,
                itemIndex = 0,
                totalItems = 1
            )
        } catch (streamErr: Throwable) {
            if (streamErr is CancellationException) throw streamErr
            Log.w(TAG, "Direct stream download failed (${streamErr.message}), trying Server Cloud proxy...")
            val fallbackUrl = "${DirectVideoExtractor.getServerUrl(applicationContext)}/api/proxy-url"
            val cookie = DirectVideoExtractor.getPlatformCookie(applicationContext, extracted.platform)
            val serverExtracted = fallbackExtractFromServer(extracted.sourceUrl, extracted.platform, fallbackUrl, cookie)
                ?: throw streamErr
            updateTask(taskId) {
                it.copy(engineUsed = serverExtracted.engineUsed)
            }
            downloadSingleStreamToMediaStore(
                taskId = taskId,
                streamUrl = serverExtracted.downloadUrl,
                headers = serverExtracted.headers,
                platform = extracted.platform,
                baseTitle = extracted.title,
                mediaType = format.mediaType,
                ext = format.ext,
                extractAudioFromVideo = format.extractAudioFromVideo,
                itemIndex = 0,
                totalItems = 1
            )
        }

        val finishedTask = updateTask(taskId) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                completedItems = 1,
                downloadedBytes = savedResult.bytesWritten,
                totalBytes = savedResult.bytesWritten,
                savedUri = savedResult.uriString,
                savedUris = listOf(savedResult.uriString),
                savedFilePath = savedResult.filePath,
                savedFileName = savedResult.fileName,
                speedBytesPerSec = 0L
            )
        }
        persistHistory(applicationContext)
        if (finishedTask != null) {
            showCompletedNotification(finishedTask)
        }
    }

    private fun fallbackExtractFromServer(
        url: String,
        platform: VideoPlatform,
        endpoint: String,
        cookie: String
    ): ExtractedVideo? {
        return try {
            val baseUrl = DirectVideoExtractor.getServerUrl(applicationContext)
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 25000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("url", url)
                if (cookie.isNotBlank()) put("cookies", cookie)
            }.toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val directUrl = json.optString("direct_url")
            val rawDlUrl = json.optString("download_url")
            val dlUrl = when {
                directUrl.startsWith("http") -> directUrl
                rawDlUrl.startsWith("http") -> rawDlUrl
                rawDlUrl.startsWith("/") -> "$baseUrl$rawDlUrl"
                else -> ""
            }
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

    private data class SavedMediaFile(
        val uriString: String,
        val filePath: String?,
        val fileName: String,
        val bytesWritten: Long
    )

    private fun downloadSingleStreamToMediaStore(
        taskId: String,
        streamUrl: String,
        headers: Map<String, String>,
        platform: VideoPlatform,
        baseTitle: String,
        mediaType: MediaType,
        ext: String,
        extractAudioFromVideo: Boolean,
        itemIndex: Int,
        totalItems: Int
    ): SavedMediaFile {
        val conn = openStreamConnection(streamUrl, headers)
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("Máy chủ trả về mã HTTP $responseCode")
        }

        val contentType = conn.contentType?.lowercase().orEmpty()
        if (contentType.contains("text/html") || contentType.contains("application/json")) {
            throw IllegalStateException("Luồng trả về không phải file đa phương tiện ($contentType)")
        }

        val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: -1L
        val safeBaseName = DirectVideoExtractor.sanitizeTitle(baseTitle)
            .replace(' ', '_')
            .take(65)

        // Nếu cần tách âm thanh từ luồng video MP4 (khi người dùng chọn tải MP3 trên FB/IG/YT/X)
        if (mediaType == MediaType.AUDIO && extractAudioFromVideo && !contentType.contains("audio/mpeg")) {
            val tempVideoFile = File(cacheDir, "temp_src_${UUID.randomUUID()}.mp4")
            val tempAudioFile = File(cacheDir, "temp_aud_${UUID.randomUUID()}.m4a")
            try {
                FileOutputStream(tempVideoFile).use { outStream ->
                    conn.inputStream.use { inStream ->
                        copyWithProgress(taskId, inStream, outStream, totalBytes, itemIndex, totalItems)
                    }
                }
                val demuxed = extractAudioTrackFromMp4(tempVideoFile, tempAudioFile)
                val finalSourceFile = if (demuxed && tempAudioFile.exists() && tempAudioFile.length() > 512L) {
                    tempAudioFile
                } else {
                    tempVideoFile
                }
                val fileName = "${platform.displayName}_${safeBaseName}_${System.currentTimeMillis() / 1000}.mp3"
                return saveLocalFileToMediaStore(
                    sourceFile = finalSourceFile,
                    fileName = fileName,
                    mimeType = "audio/mp4",
                    mediaType = MediaType.AUDIO
                )
            } finally {
                runCatching { tempVideoFile.delete() }
                runCatching { tempAudioFile.delete() }
            }
        }

        val finalExt = when (mediaType) {
            MediaType.IMAGE -> when {
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                else -> "jpg"
            }
            MediaType.AUDIO -> if (contentType.contains("mpeg") || ext == "mp3") "mp3" else "mp3"
            MediaType.VIDEO -> "mp4"
        }
        val mimeType = when (mediaType) {
            MediaType.IMAGE -> when (finalExt) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            MediaType.AUDIO -> if (contentType.contains("mpeg")) "audio/mpeg" else "audio/mp4"
            MediaType.VIDEO -> "video/mp4"
        }
        val fileName = "${platform.displayName}_${safeBaseName}_${System.currentTimeMillis() / 1000}.$finalExt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val (collection, relPath) = when (mediaType) {
                MediaType.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_PICTURES}/ToolsHub"
                MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MUSIC}/ToolsHub"
                MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MOVIES}/ToolsHub"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Không thể tạo file trong thư mục $relPath")

            try {
                var written = 0L
                resolver.openOutputStream(itemUri)?.use { outStream ->
                    conn.inputStream.use { inStream ->
                        written = copyWithProgress(taskId, inStream, outStream, totalBytes, itemIndex, totalItems)
                    }
                } ?: throw IllegalStateException("Không thể mở luồng ghi MediaStore")

                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
                return SavedMediaFile(
                    uriString = itemUri.toString(),
                    filePath = "$relPath/$fileName",
                    fileName = fileName,
                    bytesWritten = written
                )
            } catch (e: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw e
            }
        } else {
            val baseEnvDir = when (mediaType) {
                MediaType.IMAGE -> Environment.DIRECTORY_PICTURES
                MediaType.AUDIO -> Environment.DIRECTORY_MUSIC
                MediaType.VIDEO -> Environment.DIRECTORY_MOVIES
            }
            val publicDir = Environment.getExternalStoragePublicDirectory(baseEnvDir)
            val targetDir = File(publicDir, "ToolsHub").apply { mkdirs() }
            val outFile = if (targetDir.canWrite()) {
                File(targetDir, fileName)
            } else {
                val appDir = File(getExternalFilesDir(baseEnvDir), "ToolsHub").apply { mkdirs() }
                File(appDir, fileName)
            }

            try {
                var written = 0L
                FileOutputStream(outFile).use { outStream ->
                    conn.inputStream.use { inStream ->
                        written = copyWithProgress(taskId, inStream, outStream, totalBytes, itemIndex, totalItems)
                    }
                }
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(outFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                val fileUri = FileProvider.getUriForFile(
                    applicationContext,
                    "${applicationContext.packageName}.fileprovider",
                    outFile
                )
                return SavedMediaFile(
                    uriString = fileUri.toString(),
                    filePath = outFile.absolutePath,
                    fileName = fileName,
                    bytesWritten = written
                )
            } catch (e: Throwable) {
                runCatching { outFile.delete() }
                throw e
            }
        }
    }

    private fun saveLocalFileToMediaStore(
        sourceFile: File,
        fileName: String,
        mimeType: String,
        mediaType: MediaType
    ): SavedMediaFile {
        val bytesLen = sourceFile.length()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val (collection, relPath) = when (mediaType) {
                MediaType.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_PICTURES}/ToolsHub"
                MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MUSIC}/ToolsHub"
                MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MOVIES}/ToolsHub"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Không thể lưu file vào $relPath")
            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceFile.inputStream().use { inp -> inp.copyTo(out) }
                }
                val pub = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(itemUri, pub, null, null)
                return SavedMediaFile(itemUri.toString(), "$relPath/$fileName", fileName, bytesLen)
            } catch (e: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw e
            }
        } else {
            val baseEnvDir = if (mediaType == MediaType.AUDIO) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            val targetDir = File(Environment.getExternalStoragePublicDirectory(baseEnvDir), "ToolsHub").apply { mkdirs() }
            val outFile = File(targetDir, fileName)
            sourceFile.copyTo(outFile, overwrite = true)
            MediaScannerConnection.scanFile(applicationContext, arrayOf(outFile.absolutePath), arrayOf(mimeType), null)
            val fileUri = FileProvider.getUriForFile(applicationContext, "${applicationContext.packageName}.fileprovider", outFile)
            return SavedMediaFile(fileUri.toString(), outFile.absolutePath, fileName, bytesLen)
        }
    }

    /**
     * Tách riêng rãnh âm thanh (AAC) từ file MP4 bằng phần cứng Android MediaExtractor + MediaMuxer
     * (Siêu nhanh < 0.3s, không làm tăng dung lượng APK).
     */
    @SuppressLint("WrongConstant")
    private fun extractAudioTrackFromMp4(inputMp4: File, outputM4a: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(inputMp4.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(outputM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxInputSize = runCatching { audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                .getOrDefault(256 * 1024)
                .coerceAtLeast(64 * 1024)
            val buffer = ByteBuffer.allocate(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                extractor.advance()
            }
            muxer.stop()
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Hardware audio demux fallback: ${e.message}")
            false
        } finally {
            runCatching { extractor.release() }
            runCatching { muxer?.release() }
        }
    }

    private fun openStreamConnection(
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
        totalBytes: Long,
        itemIndex: Int,
        totalItems: Int
    ): Long {
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
            if (now - lastUiUpdateMs >= 220L) {
                val elapsedSec = ((now - windowStartMs).coerceAtLeast(1L)) / 1000.0
                val speed = ((downloaded - bytesAtLastWindow) / elapsedSec).toLong().coerceAtLeast(0L)
                if (now - windowStartMs >= 1000L) {
                    windowStartMs = now
                    bytesAtLastWindow = downloaded
                }

                val itemPct = if (totalBytes > 0L) {
                    (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 0.99f)
                } else {
                    0.5f
                }
                val overallPct = if (totalItems > 1) {
                    ((itemIndex.toFloat() + itemPct) / totalItems.toFloat()).coerceIn(0.01f, 0.99f)
                } else {
                    itemPct
                }

                val updated = updateTask(taskId) {
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = overallPct,
                        completedItems = itemIndex,
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
        if (downloaded < 256L) {
            throw IllegalStateException("File tải về quá nhỏ hoặc rỗng ($downloaded bytes)")
        }
        return downloaded
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

        val isIndeterminate = task.status == DownloadStatus.EXTRACTING || (task.totalBytes <= 0L && task.totalItems <= 1)
        val pctInt = (task.progress * 100).toInt().coerceIn(0, 100)

        val subtitle = when (task.status) {
            DownloadStatus.EXTRACTING -> "Đang giải mã link ${task.platform.displayName}..."
            DownloadStatus.DOWNLOADING -> {
                if (task.totalItems > 1) {
                    "Đang tải ảnh ${task.completedItems + 1}/${task.totalItems} ($pctInt%)"
                } else {
                    val speedText = if (task.speedBytesPerSec > 0) {
                        " • ${formatBytes(task.speedBytesPerSec)}/s"
                    } else ""
                    if (task.totalBytes > 0L) {
                        "$pctInt% • ${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}$speedText"
                    } else {
                        "Đã tải ${formatBytes(task.downloadedBytes)}$speedText"
                    }
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

        val typeWord = when (task.mediaType) {
            MediaType.IMAGE -> if (task.totalItems > 1) "${task.totalItems} ảnh" else "ảnh"
            MediaType.AUDIO -> "nhạc MP3"
            MediaType.VIDEO -> "video"
        }
        val folderName = when (task.mediaType) {
            MediaType.IMAGE -> "Pictures/ToolsHub"
            MediaType.AUDIO -> "Music/ToolsHub"
            MediaType.VIDEO -> "Movies/ToolsHub"
        }

        val builder = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Đã tải xong $typeWord ${task.platform.displayName}!")
            .setContentText("${task.title} (${formatBytes(task.downloadedBytes)})")
            .setSubText("Lưu tại: $folderName")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)

        if (!task.savedUri.isNullOrBlank()) {
            val mediaUri = Uri.parse(task.savedUri)
            val mime = when (task.mediaType) {
                MediaType.IMAGE -> "image/*"
                MediaType.AUDIO -> "audio/*"
                MediaType.VIDEO -> "video/mp4"
            }
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(mediaUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val viewPending = PendingIntent.getActivity(
                this,
                task.id.hashCode() + 1,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Mở xem", viewPending)
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
            .setContentTitle("Tải ${task.platform.displayName} cần chú ý")
            .setContentText(task.errorMessage ?: "Không thể tải nội dung từ link này")
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
                "Tiến trình tải Đa phương tiện",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị phần trăm và tốc độ khi đang tải video/ảnh/nhạc"
                setShowBadge(false)
            }

            val resultChannel = NotificationChannel(
                RESULT_CHANNEL_ID,
                "Thông báo hoàn tất tải",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi tải thành công hoặc gặp lỗi"
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
