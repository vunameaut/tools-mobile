package com.vunameaut.powertimer.downloader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.coroutines.resume

object DirectVideoExtractor {

    init {
        runCatching {
            System.setProperty("sun.net.http.allowRestrictedHeaders", "true")
        }
    }

    private const val TAG = "DirectVideoExtractor"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val DEFAULT_SERVER_URL = "https://video-downloader-api-backend.vercel.app"
    private const val PREFS_NAME = "video_downloader_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_PREFER_ON_DEVICE = "prefer_on_device"
    private const val KEY_DOWNLOAD_PREF = "download_preference"
    private const val KEY_COOKIE_PREFIX = "cookie_platform_"

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)?.trimEnd('/')
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, url.trim().trimEnd('/'))
            .apply()
    }

    fun isPreferOnDevice(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREFER_ON_DEVICE, true)
    }

    fun setPreferOnDevice(context: Context, prefer: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFER_ON_DEVICE, prefer)
            .apply()
    }

    fun getDownloadPreference(context: Context): DownloadPreference {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DOWNLOAD_PREF, DownloadPreference.AUTO_HD.name)
        return runCatching { DownloadPreference.valueOf(raw ?: DownloadPreference.AUTO_HD.name) }
            .getOrDefault(DownloadPreference.AUTO_HD)
    }

    fun setDownloadPreference(context: Context, pref: DownloadPreference) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOWNLOAD_PREF, pref.name)
            .apply()
    }

    fun getPlatformCookie(context: Context?, platform: VideoPlatform): String {
        if (context == null) return ""
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_COOKIE_PREFIX${platform.name}", "")
            .orEmpty()
            .trim()

        val webViewCookie = runCatching {
            val cm = CookieManager.getInstance()
            when (platform) {
                VideoPlatform.FACEBOOK -> cm.getCookie("https://www.facebook.com").orEmpty()
                VideoPlatform.INSTAGRAM -> cm.getCookie("https://www.instagram.com").orEmpty()
                VideoPlatform.TIKTOK -> cm.getCookie("https://www.tiktok.com").orEmpty()
                VideoPlatform.TWITTER -> cm.getCookie("https://x.com").orEmpty()
                else -> ""
            }
        }.getOrDefault("").trim()

        return when {
            webViewCookie.contains("c_user=") || webViewCookie.contains("sessionid=") -> {
                if (webViewCookie != saved) {
                    setPlatformCookie(context, platform, webViewCookie)
                }
                webViewCookie
            }
            saved.isNotBlank() -> saved
            else -> webViewCookie
        }
    }

    fun setPlatformCookie(context: Context, platform: VideoPlatform, cookie: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_COOKIE_PREFIX${platform.name}", cookie.trim())
            .apply()
    }

    fun hasPlatformLogin(context: Context, platform: VideoPlatform): Boolean {
        val cookie = getPlatformCookie(context, platform)
        return when (platform) {
            VideoPlatform.FACEBOOK -> cookie.contains("c_user=") && cookie.contains("xs=")
            VideoPlatform.INSTAGRAM -> cookie.contains("sessionid=")
            else -> cookie.isNotBlank()
        }
    }

    fun clearPlatformCookie(context: Context, platform: VideoPlatform) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_COOKIE_PREFIX${platform.name}")
            .apply()
        runCatching {
            val cm = CookieManager.getInstance()
            val domain = when (platform) {
                VideoPlatform.FACEBOOK -> "https://www.facebook.com"
                VideoPlatform.INSTAGRAM -> "https://www.instagram.com"
                else -> null
            }
            if (domain != null) {
                val existing = cm.getCookie(domain).orEmpty()
                existing.split(";").forEach { part ->
                    val name = part.substringBefore("=").trim()
                    if (name.isNotBlank()) {
                        cm.setCookie(domain, "$name=; Max-Age=0; path=/")
                    }
                }
                cm.flush()
            }
        }
    }

    /**
     * Giải mã & lấy thông tin đầy đủ từ đường dẫn:
     * - Hỗ trợ Video nhiều chất lượng (1080p, 720p, SD) + Tải âm thanh MP3/M4A.
     * - Hỗ trợ bài đăng nhiều ảnh (Instagram Carousel, TikTok Slideshow, Facebook Album/Photo, X/Twitter Photos).
     * - Hỗ trợ Story Facebook & Instagram (kèm Cookie đăng nhập 1 lần qua WebView).
     */
    suspend fun extract(
        context: Context,
        rawUrl: String,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<ExtractedVideo> = withContext(Dispatchers.IO) {
        val cleanUrl = unwrapLoginRedirectUrl(VideoPlatform.extractFirstUrl(rawUrl) ?: rawUrl.trim())
        val platform = VideoPlatform.detect(cleanUrl)
        val errors = mutableListOf<String>()
        var loginRequiredError: StoryLoginRequiredException? = null

        // 0. Nếu là link file .mp4 / .mp3 / .jpg trực tiếp
        val pathLower = cleanUrl.substringBefore('?').lowercase()
        if (pathLower.endsWith(".mp4") || pathLower.endsWith(".mp3") || pathLower.endsWith(".jpg") || pathLower.endsWith(".png")) {
            val mediaType = when {
                pathLower.endsWith(".mp3") || pathLower.endsWith(".m4a") -> MediaType.AUDIO
                pathLower.endsWith(".jpg") || pathLower.endsWith(".png") || pathLower.endsWith(".webp") -> MediaType.IMAGE
                else -> MediaType.VIDEO
            }
            val headers = mapOf("User-Agent" to DESKTOP_UA)
            return@withContext Result.success(
                ExtractedVideo(
                    sourceUrl = cleanUrl,
                    downloadUrl = cleanUrl,
                    title = "Media_${System.currentTimeMillis() / 1000}",
                    platform = platform,
                    headers = headers,
                    engineUsed = "Direct File",
                    mediaType = mediaType,
                    formats = buildDefaultFormats(cleanUrl, null, null, headers, mediaType),
                    mediaItems = if (mediaType == MediaType.IMAGE) {
                        listOf(ExtractedMediaItem(url = cleanUrl, mediaType = MediaType.IMAGE, headers = headers))
                    } else emptyList()
                )
            )
        }

        val cookie = getPlatformCookie(context, platform)
        val preferOnDevice = isPreferOnDevice(context)

        suspend fun tryOnDevice(): ExtractedVideo? {
            onStatusUpdate("Đang trích xuất nhanh (${platform.displayName})...")
            return try {
                val direct = when (platform) {
                    VideoPlatform.TIKTOK -> extractTikTokDirect(cleanUrl)
                    VideoPlatform.FACEBOOK -> extractFacebookDirect(cleanUrl, cookie, context)
                    VideoPlatform.INSTAGRAM -> extractInstagramDirect(cleanUrl, cookie, context)
                    VideoPlatform.TWITTER -> extractTwitterDirect(cleanUrl)
                    VideoPlatform.YOUTUBE -> extractYouTubeDirect(cleanUrl)
                    VideoPlatform.THREADS -> extractThreadsDirect(cleanUrl)
                    else -> null
                }
                if (direct != null && (direct.downloadUrl.startsWith("http") || direct.mediaItems.isNotEmpty())) {
                    Log.i(TAG, "On-Device Direct success (${direct.engineUsed}): ${direct.title}")
                    direct
                } else null
            } catch (loginEx: StoryLoginRequiredException) {
                loginRequiredError = loginEx
                errors.add(loginEx.message ?: "Cần đăng nhập Story")
                null
            } catch (e: Throwable) {
                Log.w(TAG, "On-Device direct extractor failed for $cleanUrl: ${e.message}")
                errors.add("Direct: ${e.message}")
                null
            }
        }

        suspend fun tryServerApi(): ExtractedVideo? {
            onStatusUpdate("Đang gọi Server API (${platform.displayName})...")
            return try {
                val serverResult = extractViaServerApi(context, cleanUrl, platform, cookie)
                if (serverResult != null && (serverResult.downloadUrl.startsWith("http") || serverResult.mediaItems.isNotEmpty())) {
                    Log.i(TAG, "Cloud Server API success: ${serverResult.title}")
                    serverResult
                } else null
            } catch (loginEx: StoryLoginRequiredException) {
                loginRequiredError = loginEx
                errors.add(loginEx.message ?: "Cần đăng nhập Story")
                null
            } catch (e: Throwable) {
                Log.w(TAG, "Server API failed for $cleanUrl: ${e.message}")
                errors.add("Server API: ${e.message}")
                null
            }
        }

        val result = if (preferOnDevice) {
            tryOnDevice() ?: tryServerApi()
        } else {
            tryServerApi() ?: tryOnDevice()
        }

        if (result != null) {
            return@withContext Result.success(result)
        }

        if (loginRequiredError != null) {
            return@withContext Result.failure(loginRequiredError!!)
        }

        Result.failure(
            IllegalStateException(
                "Không thể trích xuất nội dung từ link này (${platform.displayName}). Chi tiết: ${
                    errors.joinToString(" | ").ifBlank { "Không tìm thấy video hoặc ảnh hợp lệ" }
                }"
            )
        )
    }

    // ========================================================================
    // TIER 1A: TIKTOK (Video HD/SD + MP3 Audio + Photo Mode Slideshow)
    // ========================================================================
    internal fun extractTikTokDirect(url: String): ExtractedVideo? {
        try {
            val apiConn = (URL("https://www.tikwm.com/api/").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("User-Agent", DESKTOP_UA)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }
            val body = "url=${URLEncoder.encode(url, "UTF-8")}&hd=1"
            OutputStreamWriter(apiConn.outputStream, Charsets.UTF_8).use { it.write(body) }

            if (apiConn.responseCode in 200..299) {
                val respText = readStream(apiConn)
                val json = JSONObject(respText)
                if (json.optInt("code", -1) == 0) {
                    val data = json.optJSONObject("data")
                    if (data != null) {
                        fun resolveTikwmUrl(raw: String): String {
                            if (raw.isBlank()) return ""
                            return if (raw.startsWith("/")) "https://www.tikwm.com$raw" else raw
                        }

                        val hdUrl = resolveTikwmUrl(data.optString("hdplay"))
                        val playUrl = resolveTikwmUrl(data.optString("play"))
                        val wmUrl = resolveTikwmUrl(data.optString("wmplay"))
                        val musicUrl = resolveTikwmUrl(
                            data.optString("music").ifBlank {
                                data.optJSONObject("music_info")?.optString("play").orEmpty()
                            }
                        )

                        val authorObj = data.optJSONObject("author")
                        val authorName = authorObj?.optString("nickname").orEmpty()
                            .ifBlank { authorObj?.optString("unique_id").orEmpty() }
                        val rawTitle = data.optString("title").ifBlank {
                            "TikTok_${data.optString("id", "media")}"
                        }
                        val cleanTitle = sanitizeTitle(rawTitle)
                        val headers = mapOf(
                            "User-Agent" to DESKTOP_UA,
                            "Referer" to "https://www.tikwm.com/"
                        )

                        // Kiểm tra nếu là bài đăng nhiều ảnh (TikTok Photo Mode / Slideshow)
                        val imagesArr = data.optJSONArray("images")
                        if (imagesArr != null && imagesArr.length() > 0) {
                            val photoItems = mutableListOf<ExtractedMediaItem>()
                            for (i in 0 until imagesArr.length()) {
                                val imgUrl = imagesArr.optString(i)
                                if (imgUrl.startsWith("http")) {
                                    photoItems.add(
                                        ExtractedMediaItem(
                                            url = imgUrl,
                                            mediaType = MediaType.IMAGE,
                                            ext = "jpg",
                                            titleSuffix = "Anh_${i + 1}",
                                            headers = headers
                                        )
                                    )
                                }
                            }
                            if (photoItems.isNotEmpty()) {
                                val formats = mutableListOf<VideoFormatOption>()
                                val audioCandidate = musicUrl.ifBlank { playUrl }
                                if (audioCandidate.startsWith("http")) {
                                    formats.add(
                                        VideoFormatOption(
                                            id = "audio_mp3",
                                            label = "🎵 Nhạc nền bài ảnh (MP3)",
                                            qualityBadge = "MP3",
                                            downloadUrl = audioCandidate,
                                            mediaType = MediaType.AUDIO,
                                            ext = "mp3",
                                            extractAudioFromVideo = false,
                                            headers = headers
                                        )
                                    )
                                }
                                return ExtractedVideo(
                                    sourceUrl = url,
                                    downloadUrl = photoItems.first().url,
                                    title = cleanTitle,
                                    author = authorName,
                                    durationSec = data.optLong("duration", 0L),
                                    thumbnailUrl = data.optString("cover").ifBlank { photoItems.first().url },
                                    headers = headers,
                                    platform = VideoPlatform.TIKTOK,
                                    engineUsed = "TikTok Photo Slideshow (${photoItems.size} ảnh)",
                                    mediaType = MediaType.IMAGE,
                                    formats = formats,
                                    mediaItems = photoItems
                                )
                            }
                        }

                        // Nếu là Video TikTok
                        val primaryVideoUrl = sequenceOf(playUrl, hdUrl, wmUrl)
                            .firstOrNull { it.isNotBlank() && !it.endsWith(".mp3") }

                        if (!primaryVideoUrl.isNullOrBlank()) {
                            val formats = mutableListOf<VideoFormatOption>()
                            if (hdUrl.startsWith("http") && !hdUrl.endsWith(".mp3")) {
                                formats.add(
                                    VideoFormatOption(
                                        id = "hd_1080p",
                                        label = "Video Full HD (Không logo)",
                                        qualityBadge = "1080p HD",
                                        downloadUrl = hdUrl,
                                        mediaType = MediaType.VIDEO,
                                        ext = "mp4",
                                        headers = headers,
                                        sizeBytes = data.optLong("hd_size", 0L)
                                    )
                                )
                            }
                            if (playUrl.startsWith("http") && !playUrl.endsWith(".mp3") && playUrl != hdUrl) {
                                formats.add(
                                    VideoFormatOption(
                                        id = "hd_720p",
                                        label = "Video HD Tiêu chuẩn (Không logo)",
                                        qualityBadge = "720p HD",
                                        downloadUrl = playUrl,
                                        mediaType = MediaType.VIDEO,
                                        ext = "mp4",
                                        headers = headers,
                                        sizeBytes = data.optLong("size", 0L)
                                    )
                                )
                            }
                            if (wmUrl.startsWith("http") && !wmUrl.endsWith(".mp3") && wmUrl != playUrl) {
                                formats.add(
                                    VideoFormatOption(
                                        id = "sd_wm",
                                        label = "Video SD Tiết kiệm (Có logo)",
                                        qualityBadge = "SD",
                                        downloadUrl = wmUrl,
                                        mediaType = MediaType.VIDEO,
                                        ext = "mp4",
                                        headers = headers,
                                        sizeBytes = data.optLong("wm_size", 0L)
                                    )
                                )
                            }
                            val audioSource = musicUrl.ifBlank { primaryVideoUrl }
                            val needsDemux = musicUrl.isBlank() || !musicUrl.startsWith("http")
                            formats.add(
                                VideoFormatOption(
                                    id = "audio_mp3",
                                    label = "🎵 Tải nhạc MP3 (Chỉ âm thanh)",
                                    qualityBadge = "MP3",
                                    downloadUrl = audioSource,
                                    mediaType = MediaType.AUDIO,
                                    ext = "mp3",
                                    extractAudioFromVideo = needsDemux,
                                    headers = headers
                                )
                            )

                            return ExtractedVideo(
                                sourceUrl = url,
                                downloadUrl = hdUrl.takeIf { it.startsWith("http") && !it.endsWith(".mp3") } ?: primaryVideoUrl,
                                title = cleanTitle,
                                author = authorName,
                                durationSec = data.optLong("duration", 0L),
                                thumbnailUrl = data.optString("cover"),
                                headers = headers,
                                platform = VideoPlatform.TIKTOK,
                                engineUsed = "On-Device TikTok Direct",
                                mediaType = MediaType.VIDEO,
                                formats = formats
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "TikWM direct failed: ${e.message}")
        }

        // Cách 2: Fetch trực tiếp trang TikTok + parse __UNIVERSAL_DATA_FOR_REHYDRATION__
        try {
            val page = fetchUrlFollowingRedirects(
                targetUrl = url,
                headers = mapOf(
                    "User-Agent" to DESKTOP_UA,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
            val scriptMatch = Regex(
                """<script[^>]+id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL
            ).find(page.body)
            if (scriptMatch != null) {
                val jsonText = scriptMatch.groupValues[1]
                val playAddrMatch = Regex(""""playAddr"\s*:\s*"([^"]+)"""").find(jsonText)
                    ?: Regex(""""downloadAddr"\s*:\s*"([^"]+)"""").find(jsonText)
                if (playAddrMatch != null) {
                    val videoUrl = unescapeJsonString(playAddrMatch.groupValues[1])
                    val descMatch = Regex(""""desc"\s*:\s*"([^"]*)"""").find(jsonText)
                    val title = descMatch?.groupValues?.get(1)?.let { unescapeJsonString(it) }
                        ?.takeIf { it.isNotBlank() } ?: "TikTok_Video"
                    val dlHeaders = mutableMapOf(
                        "User-Agent" to DESKTOP_UA,
                        "Referer" to "https://www.tiktok.com/"
                    )
                    if (page.cookies.isNotBlank()) {
                        dlHeaders["Cookie"] = page.cookies
                    }
                    return ExtractedVideo(
                        sourceUrl = url,
                        downloadUrl = videoUrl,
                        title = sanitizeTitle(title),
                        headers = dlHeaders,
                        platform = VideoPlatform.TIKTOK,
                        engineUsed = "On-Device TikTok Web",
                        formats = buildDefaultFormats(videoUrl, videoUrl, null, dlHeaders, MediaType.VIDEO)
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "TikTok web rehydration failed: ${e.message}")
        }

        return null
    }

    // ========================================================================
    // TIER 1B: FACEBOOK (Video HD/SD + Story + Multi-Photo Album + Single Photo)
    // ========================================================================
    internal suspend fun extractFacebookDirect(
        url: String,
        cookie: String = "",
        context: Context? = null
    ): ExtractedVideo? {
        val navHeaders = mutableMapOf(
            "User-Agent" to DESKTOP_UA,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9,vi;q=0.8",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Upgrade-Insecure-Requests" to "1"
        )
        if (cookie.isNotBlank()) {
            navHeaders["Cookie"] = cookie
        }

        val hdPatterns = listOf(
            Regex(""""browser_native_hd_url"\s*:\s*"([^"]+)""""),
            Regex(""""playable_url_quality_hd"\s*:\s*"([^"]+)""""),
            Regex("""hd_src_no_ratelimit\s*:\s*"([^"]+)""""),
            Regex(""""hd_src"\s*:\s*"([^"]+)""""),
            Regex(""""progressive_url"\s*:\s*"([^"]+)"\s*,\s*"failure_reason"\s*:\s*null\s*,\s*"metadata"\s*:\s*\{\s*"quality"\s*:\s*"HD"""")
        )
        val sdPatterns = listOf(
            Regex(""""browser_native_sd_url"\s*:\s*"([^"]+)""""),
            Regex(""""playable_url"\s*:\s*"([^"]+)""""),
            Regex("""sd_src_no_ratelimit\s*:\s*"([^"]+)""""),
            Regex(""""sd_src"\s*:\s*"([^"]+)""""),
            Regex(""""progressive_url"\s*:\s*"([^"]+)"\s*,\s*"failure_reason"\s*:\s*null\s*,\s*"metadata"\s*:\s*\{\s*"quality"\s*:\s*"SD"""")
        )

        val resolved = fetchUrlFollowingRedirects(url, navHeaders)
        val finalUrl = resolved.finalUrl
        val isStoryUrl = finalUrl.contains("/stories/") || url.contains("/stories/")

        // Nếu là Story và có mã định danh thẻ Story cụ thể trên URL, ưu tiên tìm trong phân đoạn của Story đó
        val storyToken = Regex("""/stories/[^/]+/([^/?#]+)""").find(finalUrl)?.groupValues?.get(1)
            ?: Regex("""/stories/[^/]+/([^/?#]+)""").find(url)?.groupValues?.get(1)
        val decodedStoryId = storyToken?.let { token ->
            runCatching {
                val raw = String(Base64.decode(token, Base64.DEFAULT), Charsets.UTF_8)
                Regex("""(\d{10,})""").find(raw)?.groupValues?.get(1)
            }.getOrNull()
        }

        val dlHeaders = mutableMapOf(
            "User-Agent" to DESKTOP_UA,
            "Referer" to "https://www.facebook.com/"
        )
        if (cookie.isNotBlank()) {
            dlHeaders["Cookie"] = cookie
        }

        val encodedHref = URLEncoder.encode(finalUrl, "UTF-8")
        val pluginUrl = "https://www.facebook.com/plugins/video.php?href=$encodedHref&show_text=0"

        for (attempt in 0..1) {
            if (attempt == 1 && isStoryUrl) break // plugins/video.php không áp dụng cho /stories/
            val html = if (attempt == 0) {
                resolved.body
            } else {
                runCatching { fetchUrlFollowingRedirects(pluginUrl, navHeaders).body }.getOrDefault("")
            }
            if (html.isBlank()) continue

            // Nếu là Story có storyToken / decodedStoryId, ưu tiên vùng JSON chứa storyId đó
            val searchZones = mutableListOf<String>()
            if (!storyToken.isNullOrBlank()) {
                val idx = html.indexOf(storyToken)
                if (idx >= 0) {
                    searchZones.add(html.substring((idx - 1500).coerceAtLeast(0), (idx + 35000).coerceAtMost(html.length)))
                }
            }
            if (!decodedStoryId.isNullOrBlank()) {
                val idx = html.indexOf(decodedStoryId)
                if (idx >= 0) {
                    searchZones.add(html.substring((idx - 1500).coerceAtLeast(0), (idx + 35000).coerceAtMost(html.length)))
                }
            }
            searchZones.add(html)

            var hdUrl: String? = null
            var sdUrl: String? = null

            for (zone in searchZones) {
                if (hdUrl == null) {
                    for (pat in hdPatterns) {
                        val m = pat.find(zone)
                        if (m != null) {
                            val candidate = unescapeJsonString(m.groupValues[1])
                            if (candidate.startsWith("http") && candidate != "null") {
                                hdUrl = candidate
                                break
                            }
                        }
                    }
                }
                if (sdUrl == null) {
                    for (pat in sdPatterns) {
                        val m = pat.find(zone)
                        if (m != null) {
                            val candidate = unescapeJsonString(m.groupValues[1])
                            if (candidate.startsWith("http") && candidate != "null") {
                                sdUrl = candidate
                                break
                            }
                        }
                    }
                }
                if (hdUrl != null || sdUrl != null) break
            }

            val bestVideoUrl = hdUrl ?: sdUrl
            if (bestVideoUrl != null) {
                val rawTitle = extractHtmlTitle(html, if (isStoryUrl) "Facebook_Story" else "Facebook_Video")
                return ExtractedVideo(
                    sourceUrl = url,
                    downloadUrl = bestVideoUrl,
                    title = sanitizeTitle(rawTitle),
                    headers = dlHeaders,
                    platform = VideoPlatform.FACEBOOK,
                    engineUsed = if (isStoryUrl) "On-Device Facebook Story" else "On-Device Facebook Direct",
                    mediaType = MediaType.VIDEO,
                    formats = buildDefaultFormats(bestVideoUrl, hdUrl, sdUrl, dlHeaders, MediaType.VIDEO)
                )
            }

            // Nếu không phải video, kiểm tra xem có phải bài đăng nhiều ảnh (all_subattachments / subattachments) hoặc ảnh đơn / Story ảnh không!
            if (attempt == 0) {
                val fbPhotos = extractFacebookPhotosFromHtml(html, searchZones.first(), dlHeaders, isStoryUrl)
                if (fbPhotos.isNotEmpty()) {
                    val rawTitle = extractHtmlTitle(html, if (isStoryUrl) "Facebook_Story_Photo" else "Facebook_Photos")
                    return ExtractedVideo(
                        sourceUrl = url,
                        downloadUrl = fbPhotos.first().url,
                        title = sanitizeTitle(rawTitle),
                        thumbnailUrl = fbPhotos.first().url,
                        headers = dlHeaders,
                        platform = VideoPlatform.FACEBOOK,
                        engineUsed = "Facebook Photo (${fbPhotos.size} ảnh)",
                        mediaType = MediaType.IMAGE,
                        mediaItems = fbPhotos
                    )
                }
            }
        }

        // Nếu là Story và người dùng đã đăng nhập Cookie, thử dùng Headless WebView trên thiết bị để bắt link Story
        if (isStoryUrl && cookie.isNotBlank() && context != null) {
            val webViewExtracted = extractStoryViaWebView(context, finalUrl, VideoPlatform.FACEBOOK, dlHeaders)
            if (webViewExtracted != null) {
                return webViewExtracted
            }
        }

        // Nếu là Story (hoặc bị chuyển hướng sang login.php) mà chưa có Cookie đăng nhập
        if (isStoryUrl || resolved.redirectedToLogin) {
            throw StoryLoginRequiredException(
                platform = VideoPlatform.FACEBOOK,
                storyUrl = finalUrl
            )
        }

        return null
    }

    private fun extractFacebookPhotosFromHtml(
        fullHtml: String,
        priorityZone: String,
        headers: Map<String, String>,
        isStoryUrl: Boolean
    ): List<ExtractedMediaItem> {
        val urls = LinkedHashSet<String>()

        // 1. Kiểm tra bài đăng nhiều ảnh ("all_subattachments" hoặc "subattachments")
        for (key in listOf(""""all_subattachments":""", """"subattachments":""")) {
            var searchFrom = 0
            while (true) {
                val idx = fullHtml.indexOf(key, searchFrom)
                if (idx < 0) break
                searchFrom = idx + key.length
                val objStart = fullHtml.indexOf('{', idx + key.length - 1)
                if (objStart < 0) continue
                val jsonSlice = extractBalancedJson(fullHtml, objStart, '{', '}') ?: continue
                runCatching {
                    val subObj = JSONObject(jsonSlice)
                    val nodes = subObj.optJSONArray("nodes") ?: return@runCatching
                    for (i in 0 until nodes.length()) {
                        val node = nodes.optJSONObject(i) ?: continue
                        val media = node.optJSONObject("media") ?: node
                        val viewerUri = media.optJSONObject("viewer_image")?.optString("uri").orEmpty()
                        val imageUri = media.optJSONObject("image")?.optString("uri").orEmpty()
                        val chosen = viewerUri.ifBlank { imageUri }
                        if (chosen.startsWith("http") && isHighResFacebookPhoto(chosen)) {
                            urls.add(decodeHtmlEntities(chosen))
                        }
                    }
                }
                if (urls.size > 1) break
            }
            if (urls.size > 1) break
        }

        // 2. Nếu chưa có all_subattachments, kiểm tra viewer_image / photo_image (ảnh đơn hoặc Story ảnh)
        if (urls.isEmpty()) {
            val viewerRegex = Regex(""""(?:viewer_image|photo_image)"\s*:\s*\{[^{}]*"uri"\s*:\s*"(https:[^"]+)"""")
            for (m in viewerRegex.findAll(priorityZone)) {
                val u = decodeHtmlEntities(unescapeJsonString(m.groupValues[1]))
                if (u.startsWith("http") && isHighResFacebookPhoto(u)) {
                    urls.add(u)
                    if (isStoryUrl) break
                }
            }
        }

        // 3. Nếu là trang /photo/ hoặc Story ảnh chỉ có "image":{"uri":"https:\/\/scontent..."}
        if (urls.isEmpty() && (isStoryUrl || fullHtml.contains("/photo") || fullHtml.contains(""""__typename":"Photo""""))) {
            val imgRegex = Regex(""""image"\s*:\s*\{\s*"uri"\s*:\s*"(https:\\/\\/scontent[^"]+)"""")
            for (m in imgRegex.findAll(priorityZone)) {
                val u = decodeHtmlEntities(unescapeJsonString(m.groupValues[1]))
                if (u.startsWith("http") && isHighResFacebookPhoto(u)) {
                    urls.add(u)
                    if (isStoryUrl) break
                }
            }
        }

        return urls.mapIndexed { idx, u ->
            ExtractedMediaItem(
                url = u,
                mediaType = MediaType.IMAGE,
                ext = "jpg",
                titleSuffix = "Anh_${idx + 1}",
                headers = headers
            )
        }
    }

    private fun isHighResFacebookPhoto(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.contains("scontent") && !lower.contains("fbcdn.net")) return false
        // Loại bỏ ảnh đại diện / icon nhỏ (s32x32, s40x40, s50x50, s100x100, s120x120, p50x50, t1.30497-1, t39.30808-1)
        val smallMarkers = listOf(
            "s24x24", "s32x32", "s40x40", "s50x50", "s60x60", "s80x80",
            "s100x100", "s120x120", "s160x160", "p50x50", "p100x100",
            "/t1.30497-1/", "/t39.30808-1/"
        )
        return smallMarkers.none { lower.contains(it) }
    }

    // ========================================================================
    // TIER 1C: INSTAGRAM (Reels, Videos, Multi-Photo Carousels, Single Photos, Stories)
    // ========================================================================
    internal suspend fun extractInstagramDirect(
        url: String,
        cookie: String = "",
        context: Context? = null
    ): ExtractedVideo? {
        val navHeaders = mutableMapOf(
            "User-Agent" to DESKTOP_UA,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-Fetch-Mode" to "navigate"
        )
        if (cookie.isNotBlank()) {
            navHeaders["Cookie"] = cookie
        }

        val dlHeaders = mutableMapOf(
            "User-Agent" to DESKTOP_UA,
            "Referer" to "https://www.instagram.com/"
        )
        if (cookie.isNotBlank()) {
            dlHeaders["Cookie"] = cookie
        }

        // Nếu là link /share/..., giải mã redirect trước
        val initialFetch = if (url.contains("/share/")) {
            fetchUrlFollowingRedirects(url, navHeaders)
        } else null
        val resolvedUrl = initialFetch?.finalUrl ?: url

        // 1. Xử lý Instagram Story (/stories/{username}/{story_id}/)
        if (resolvedUrl.contains("/stories/")) {
            val storyPage = initialFetch ?: fetchUrlFollowingRedirects(resolvedUrl, navHeaders)
            val storyItem = parseInstagramMediaFromHtml(storyPage.body, url, "Instagram_Story", dlHeaders)
            if (storyItem != null) {
                return storyItem.copy(engineUsed = "On-Device Instagram Story")
            }
            if (cookie.isNotBlank() && context != null) {
                val webViewStory = extractStoryViaWebView(context, resolvedUrl, VideoPlatform.INSTAGRAM, dlHeaders)
                if (webViewStory != null) return webViewStory
            }
            throw StoryLoginRequiredException(
                platform = VideoPlatform.INSTAGRAM,
                storyUrl = resolvedUrl
            )
        }

        val shortcodeMatch = Regex("""/(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)""").find(resolvedUrl)
        val shortcode = shortcodeMatch?.groupValues?.get(1) ?: return null

        // 2. Fetch trang /p/{shortcode}/ với header navigate
        try {
            val postPage = fetchUrlFollowingRedirects("https://www.instagram.com/p/$shortcode/", navHeaders)
            val parsedMain = parseInstagramMediaFromHtml(postPage.body, url, "Instagram_$shortcode", dlHeaders)
            if (parsedMain != null) {
                return parsedMain
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Instagram main page parse failed: ${e.message}")
        }

        // 3. Fallback qua trang /embed/captioned/
        try {
            val embedPage = fetchUrlFollowingRedirects(
                "https://www.instagram.com/p/$shortcode/embed/captioned/",
                navHeaders
            )
            val html = embedPage.body

            // Kiểm tra video trong embed
            val embedVideoPatterns = listOf(
                Regex("""\\"video_url\\"\s*:\s*\\"([^\\"]+)\\""""),
                Regex(""""video_url"\s*:\s*"([^"]+)""""),
                Regex("""<video[^>]+src="([^"]+)"""")
            )
            for (pat in embedVideoPatterns) {
                val m = pat.find(html)
                if (m != null) {
                    val raw = m.groupValues[1].replace("\\\\/", "/").replace("\\/", "/")
                    val videoUrl = decodeHtmlEntities(unescapeJsonString(raw))
                    if (videoUrl.startsWith("http")) {
                        return ExtractedVideo(
                            sourceUrl = url,
                            downloadUrl = videoUrl,
                            title = "Instagram_$shortcode",
                            headers = dlHeaders,
                            platform = VideoPlatform.INSTAGRAM,
                            engineUsed = "On-Device Instagram Embed",
                            mediaType = MediaType.VIDEO,
                            formats = buildDefaultFormats(videoUrl, videoUrl, null, dlHeaders, MediaType.VIDEO)
                        )
                    }
                }
            }

            // Kiểm tra nhiều ảnh hoặc ảnh đơn trong embed (display_url / EmbeddedMediaImage)
            val photoUrls = LinkedHashSet<String>()
            val displayUrlRegex = Regex("""\\?"display_url\\?"\s*:\s*\\?"(https:[^"\\]+)\\?"""")
            for (m in displayUrlRegex.findAll(html)) {
                val raw = m.groupValues[1].replace("\\\\/", "/").replace("\\/", "/")
                val imgUrl = decodeHtmlEntities(unescapeJsonString(raw))
                if (imgUrl.startsWith("http")) {
                    photoUrls.add(imgUrl)
                }
            }
            if (photoUrls.isEmpty()) {
                val imgTagRegex = Regex("""class="EmbeddedMediaImage"[^>]+src="([^"]+)"""")
                for (m in imgTagRegex.findAll(html)) {
                    val imgUrl = decodeHtmlEntities(m.groupValues[1])
                    if (imgUrl.startsWith("http")) {
                        photoUrls.add(imgUrl)
                    }
                }
            }
            if (photoUrls.isNotEmpty()) {
                val items = photoUrls.mapIndexed { index, u ->
                    ExtractedMediaItem(
                        url = u,
                        mediaType = MediaType.IMAGE,
                        ext = "jpg",
                        titleSuffix = "Anh_${index + 1}",
                        headers = dlHeaders
                    )
                }
                return ExtractedVideo(
                    sourceUrl = url,
                    downloadUrl = items.first().url,
                    title = "Instagram_$shortcode",
                    thumbnailUrl = items.first().url,
                    headers = dlHeaders,
                    platform = VideoPlatform.INSTAGRAM,
                    engineUsed = "Instagram Photo (${items.size} ảnh)",
                    mediaType = MediaType.IMAGE,
                    mediaItems = items
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Instagram embed fallback failed: ${e.message}")
        }

        return null
    }

    private fun parseInstagramMediaFromHtml(
        html: String,
        sourceUrl: String,
        fallbackTitle: String,
        dlHeaders: Map<String, String>
    ): ExtractedVideo? {
        if (html.isBlank()) return null

        val titleMatch = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(html)
        val rawTitle = titleMatch?.groupValues?.get(1)?.let { decodeHtmlEntities(it) } ?: fallbackTitle
        val cleanTitle = sanitizeTitle(rawTitle)

        // TH1: Bài đăng nhiều ảnh / Carousel ("carousel_media":[...])
        val carouselIdx = html.indexOf(""""carousel_media":[""")
        if (carouselIdx >= 0) {
            val arrStart = html.indexOf('[', carouselIdx)
            val jsonSlice = if (arrStart >= 0) extractBalancedJson(html, arrStart, '[', ']') else null
            if (!jsonSlice.isNullOrBlank()) {
                val items = mutableListOf<ExtractedMediaItem>()
                runCatching {
                    val arr = JSONArray(jsonSlice)
                    for (i in 0 until arr.length()) {
                        val slide = arr.optJSONObject(i) ?: continue
                        val videoVersions = slide.optJSONArray("video_versions")
                        if (videoVersions != null && videoVersions.length() > 0) {
                            val vUrl = videoVersions.optJSONObject(0)?.optString("url").orEmpty()
                            if (vUrl.startsWith("http")) {
                                items.add(
                                    ExtractedMediaItem(
                                        url = decodeHtmlEntities(vUrl),
                                        mediaType = MediaType.VIDEO,
                                        ext = "mp4",
                                        titleSuffix = "Slide_${i + 1}",
                                        headers = dlHeaders
                                    )
                                )
                                continue
                            }
                        }
                        val candidates = slide.optJSONObject("image_versions2")?.optJSONArray("candidates")
                        val imgUrl = candidates?.optJSONObject(0)?.optString("url").orEmpty()
                            .ifBlank { slide.optString("display_uri") }
                        if (imgUrl.startsWith("http")) {
                            items.add(
                                ExtractedMediaItem(
                                    url = decodeHtmlEntities(imgUrl),
                                    mediaType = MediaType.IMAGE,
                                    ext = "jpg",
                                    titleSuffix = "Anh_${i + 1}",
                                    headers = dlHeaders
                                )
                            )
                        }
                    }
                }
                if (items.isNotEmpty()) {
                    val allImages = items.all { it.mediaType == MediaType.IMAGE }
                    return ExtractedVideo(
                        sourceUrl = sourceUrl,
                        downloadUrl = items.first().url,
                        title = cleanTitle,
                        thumbnailUrl = items.first().url,
                        headers = dlHeaders,
                        platform = VideoPlatform.INSTAGRAM,
                        engineUsed = "Instagram Carousel (${items.size} mục)",
                        mediaType = if (allImages) MediaType.IMAGE else MediaType.VIDEO,
                        mediaItems = items
                    )
                }
            }
        }

        // TH2: Bài đăng Video / Reel ("video_versions":[...])
        val videoIdx = html.indexOf(""""video_versions":[""")
        if (videoIdx >= 0) {
            val arrStart = html.indexOf('[', videoIdx)
            val jsonSlice = if (arrStart >= 0) extractBalancedJson(html, arrStart, '[', ']') else null
            if (!jsonSlice.isNullOrBlank()) {
                runCatching {
                    val arr = JSONArray(jsonSlice)
                    if (arr.length() > 0) {
                        val hdUrl = decodeHtmlEntities(arr.optJSONObject(0)?.optString("url").orEmpty())
                        val sdUrl = decodeHtmlEntities(arr.optJSONObject(arr.length() - 1)?.optString("url").orEmpty())
                        if (hdUrl.startsWith("http")) {
                            return ExtractedVideo(
                                sourceUrl = sourceUrl,
                                downloadUrl = hdUrl,
                                title = cleanTitle,
                                headers = dlHeaders,
                                platform = VideoPlatform.INSTAGRAM,
                                engineUsed = "On-Device Instagram Direct",
                                mediaType = MediaType.VIDEO,
                                formats = buildDefaultFormats(
                                    primaryUrl = hdUrl,
                                    hdUrl = hdUrl,
                                    sdUrl = sdUrl.takeIf { it != hdUrl && it.startsWith("http") },
                                    headers = dlHeaders,
                                    mediaType = MediaType.VIDEO
                                )
                            )
                        }
                    }
                }
            }
        }

        // TH3: Bài đăng 1 ảnh đơn ("image_versions2":{"candidates":[{"url":"..."}]})
        val imgVerIdx = html.indexOf(""""image_versions2":{"candidates":[""")
        if (imgVerIdx >= 0) {
            val m = Regex(""""image_versions2"\s*:\s*\{\s*"candidates"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"([^"]+)"""")
                .find(html, imgVerIdx)
            if (m != null) {
                val imgUrl = decodeHtmlEntities(unescapeJsonString(m.groupValues[1]))
                if (imgUrl.startsWith("http")) {
                    val item = ExtractedMediaItem(
                        url = imgUrl,
                        mediaType = MediaType.IMAGE,
                        ext = "jpg",
                        titleSuffix = "Anh_1",
                        headers = dlHeaders
                    )
                    return ExtractedVideo(
                        sourceUrl = sourceUrl,
                        downloadUrl = imgUrl,
                        title = cleanTitle,
                        thumbnailUrl = imgUrl,
                        headers = dlHeaders,
                        platform = VideoPlatform.INSTAGRAM,
                        engineUsed = "Instagram Photo Direct",
                        mediaType = MediaType.IMAGE,
                        mediaItems = listOf(item)
                    )
                }
            }
        }

        return null
    }

    // ========================================================================
    // TIER 1D: X / TWITTER (Multi-Quality Video + MP3 + Multi-Photo Tweet)
    // ========================================================================
    internal fun extractTwitterDirect(url: String): ExtractedVideo? {
        try {
            val resolvedUrl = if (url.contains("t.co/")) {
                fetchUrlFollowingRedirects(url, mapOf("User-Agent" to DESKTOP_UA)).finalUrl
            } else {
                url
            }

            val idMatch = Regex("""status(?:es)?/(\d+)""").find(resolvedUrl)
            val tweetId = idMatch?.groupValues?.get(1) ?: return null
            val headers = mapOf("User-Agent" to DESKTOP_UA)

            // Cách 1: FxTwitter API
            try {
                val fxPage = fetchUrlFollowingRedirects(
                    "https://api.fxtwitter.com/Twitter/status/$tweetId",
                    mapOf("User-Agent" to DESKTOP_UA, "Accept" to "application/json")
                )
                val json = JSONObject(fxPage.body)
                val tweet = json.optJSONObject("tweet")
                if (tweet != null) {
                    val author = tweet.optJSONObject("author")?.optString("name").orEmpty()
                    val text = sanitizeTitle(tweet.optString("text").ifBlank { "X_Media_$tweetId" })
                    val media = tweet.optJSONObject("media")

                    // Kiểm tra Video trước
                    val videos = media?.optJSONArray("videos")
                    if (videos != null && videos.length() > 0) {
                        val firstVideo = videos.optJSONObject(0)
                        val vUrl = firstVideo?.optString("url").orEmpty()
                        if (vUrl.startsWith("http")) {
                            val formats = mutableListOf<VideoFormatOption>()
                            val variants = firstVideo?.optJSONArray("variants")
                            if (variants != null && variants.length() > 0) {
                                val mp4Variants = mutableListOf<Pair<Int, String>>()
                                for (i in 0 until variants.length()) {
                                    val vr = variants.optJSONObject(i) ?: continue
                                    val contentType = vr.optString("content_type")
                                    val u = vr.optString("url")
                                    val bitrate = vr.optInt("bitrate", 0)
                                    if (contentType.contains("mp4") && u.startsWith("http")) {
                                        mp4Variants.add(bitrate to u)
                                    }
                                }
                                mp4Variants.sortByDescending { it.first }
                                mp4Variants.distinctBy { it.second }.forEachIndexed { idx, (br, u) ->
                                    val resMatch = Regex("""/(\d+x\d+)/""").find(u)?.groupValues?.get(1)
                                    val badge = resMatch?.substringAfter('x')?.let { "${it}p" }
                                        ?: if (idx == 0) "HD" else "SD"
                                    formats.add(
                                        VideoFormatOption(
                                            id = "tw_$idx",
                                            label = "Video $badge (${resMatch ?: "${br / 1000} kbps"})",
                                            qualityBadge = badge,
                                            downloadUrl = u,
                                            mediaType = MediaType.VIDEO,
                                            ext = "mp4",
                                            headers = headers
                                        )
                                    )
                                }
                            }
                            if (formats.isEmpty()) {
                                formats.addAll(buildDefaultFormats(vUrl, vUrl, null, headers, MediaType.VIDEO))
                            } else {
                                formats.add(
                                    VideoFormatOption(
                                        id = "audio_mp3",
                                        label = "🎵 Tải nhạc MP3 / Audio",
                                        qualityBadge = "MP3",
                                        downloadUrl = vUrl,
                                        mediaType = MediaType.AUDIO,
                                        ext = "m4a",
                                        extractAudioFromVideo = true,
                                        headers = headers
                                    )
                                )
                            }

                            return ExtractedVideo(
                                sourceUrl = url,
                                downloadUrl = vUrl,
                                title = text,
                                author = author,
                                durationSec = firstVideo?.optLong("duration", 0L) ?: 0L,
                                thumbnailUrl = firstVideo?.optString("thumbnail_url").orEmpty(),
                                headers = headers,
                                platform = VideoPlatform.TWITTER,
                                engineUsed = "On-Device X/Twitter Direct",
                                mediaType = MediaType.VIDEO,
                                formats = formats
                            )
                        }
                    }

                    // Kiểm tra Bài đăng nhiều ảnh (Photos)
                    val photos = media?.optJSONArray("photos")
                    if (photos != null && photos.length() > 0) {
                        val photoItems = mutableListOf<ExtractedMediaItem>()
                        for (i in 0 until photos.length()) {
                            val p = photos.optJSONObject(i) ?: continue
                            val pUrl = p.optString("url")
                            if (pUrl.startsWith("http")) {
                                photoItems.add(
                                    ExtractedMediaItem(
                                        url = pUrl,
                                        mediaType = MediaType.IMAGE,
                                        ext = "jpg",
                                        titleSuffix = "Anh_${i + 1}",
                                        width = p.optInt("width", 0),
                                        height = p.optInt("height", 0),
                                        headers = headers
                                    )
                                )
                            }
                        }
                        if (photoItems.isNotEmpty()) {
                            return ExtractedVideo(
                                sourceUrl = url,
                                downloadUrl = photoItems.first().url,
                                title = text,
                                author = author,
                                thumbnailUrl = photoItems.first().url,
                                headers = headers,
                                platform = VideoPlatform.TWITTER,
                                engineUsed = "X/Twitter Photo (${photoItems.size} ảnh)",
                                mediaType = MediaType.IMAGE,
                                mediaItems = photoItems
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "FxTwitter failed: ${e.message}")
            }

            // Cách 2: VxTwitter API
            try {
                val vxPage = fetchUrlFollowingRedirects(
                    "https://api.vxtwitter.com/Twitter/status/$tweetId",
                    mapOf("User-Agent" to DESKTOP_UA, "Accept" to "application/json")
                )
                val json = JSONObject(vxPage.body)
                val mediaExtended = json.optJSONArray("media_extended")
                if (mediaExtended != null && mediaExtended.length() > 0) {
                    val text = sanitizeTitle(json.optString("text").ifBlank { "X_Media_$tweetId" })
                    val author = json.optString("user_name")
                    val photoItems = mutableListOf<ExtractedMediaItem>()

                    for (i in 0 until mediaExtended.length()) {
                        val item = mediaExtended.optJSONObject(i) ?: continue
                        val vUrl = item.optString("url")
                        val type = item.optString("type")
                        if (vUrl.startsWith("http") && type in listOf("video", "gif")) {
                            return ExtractedVideo(
                                sourceUrl = url,
                                downloadUrl = vUrl,
                                title = text,
                                author = author,
                                thumbnailUrl = item.optString("thumbnail_url"),
                                headers = headers,
                                platform = VideoPlatform.TWITTER,
                                engineUsed = "On-Device VxTwitter Direct",
                                mediaType = MediaType.VIDEO,
                                formats = buildDefaultFormats(vUrl, vUrl, null, headers, MediaType.VIDEO)
                            )
                        } else if (vUrl.startsWith("http") && type == "image") {
                            photoItems.add(
                                ExtractedMediaItem(
                                    url = vUrl,
                                    mediaType = MediaType.IMAGE,
                                    ext = "jpg",
                                    titleSuffix = "Anh_${photoItems.size + 1}",
                                    headers = headers
                                )
                            )
                        }
                    }
                    if (photoItems.isNotEmpty()) {
                        return ExtractedVideo(
                            sourceUrl = url,
                            downloadUrl = photoItems.first().url,
                            title = text,
                            author = author,
                            thumbnailUrl = photoItems.first().url,
                            headers = headers,
                            platform = VideoPlatform.TWITTER,
                            engineUsed = "VxTwitter Photo (${photoItems.size} ảnh)",
                            mediaType = MediaType.IMAGE,
                            mediaItems = photoItems
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "VxTwitter failed: ${e.message}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Twitter direct failed: ${e.message}")
        }
        return null
    }

    // ========================================================================
    // TIER 1E: THREADS (Video + Multi-Photo)
    // ========================================================================
    private fun extractThreadsDirect(url: String): ExtractedVideo? {
        try {
            val headers = mapOf(
                "User-Agent" to DESKTOP_UA,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Sec-Fetch-Mode" to "navigate"
            )
            val page = fetchUrlFollowingRedirects(url, headers)
            val parsed = parseInstagramMediaFromHtml(
                html = page.body,
                sourceUrl = url,
                fallbackTitle = "Threads_${System.currentTimeMillis() / 1000}",
                dlHeaders = mapOf("User-Agent" to DESKTOP_UA)
            )
            if (parsed != null) {
                return parsed.copy(
                    platform = VideoPlatform.THREADS,
                    engineUsed = "On-Device Threads Direct"
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Threads direct failed: ${e.message}")
        }
        return null
    }

    // ========================================================================
    // TIER 1F: YOUTUBE / SHORTS (Pure Kotlin ANDROID InnerTube API 21.26.364)
    // ========================================================================
    internal fun extractYouTubeDirect(url: String): ExtractedVideo? {
        try {
            val idMatch = Regex("""(?:v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""").find(url)
            val videoId = idMatch?.groupValues?.get(1) ?: return null
            val ytAndroidUa = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"

            var visitorData = ""
            runCatching {
                val watchPage = fetchUrlFollowingRedirects(
                    targetUrl = "https://www.youtube.com/watch?v=$videoId",
                    headers = mapOf(
                        "User-Agent" to DESKTOP_UA,
                        "Accept-Language" to "en-US,en;q=0.9"
                    )
                )
                val vm = Regex(""""visitorData"\s*:\s*"([^"]+)"""").find(watchPage.body)
                if (vm != null) {
                    visitorData = vm.groupValues[1]
                }
            }

            val clientObj = JSONObject().apply {
                put("clientName", "ANDROID")
                put("clientVersion", "21.26.364")
                put("androidSdkVersion", 30)
                put("userAgent", ytAndroidUa)
                put("osName", "Android")
                put("osVersion", "11")
                put("hl", "en")
                if (visitorData.isNotBlank()) {
                    put("visitorData", visitorData)
                }
            }

            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("context", JSONObject().put("client", clientObj))
            }.toString()

            val conn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("User-Agent", ytAndroidUa)
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("X-YouTube-Client-Name", "3")
                setRequestProperty("X-YouTube-Client-Version", "21.26.364")
                setRequestProperty("Origin", "https://www.youtube.com")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            if (conn.responseCode !in 200..299) return null

            val json = JSONObject(readStream(conn))
            val status = json.optJSONObject("playabilityStatus")?.optString("status")
            if (status != "OK") return null

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val formatsArr = streamingData.optJSONArray("formats") ?: return null
            val dlHeaders = mapOf("User-Agent" to ytAndroidUa)
            val formatOptions = mutableListOf<VideoFormatOption>()

            var bestMp4Url: String? = null
            for (i in formatsArr.length() - 1 downTo 0) {
                val fmt = formatsArr.optJSONObject(i) ?: continue
                val u = fmt.optString("url")
                if (u.startsWith("http")) {
                    if (bestMp4Url == null) bestMp4Url = u
                    val qLabel = fmt.optString("qualityLabel").ifBlank { "360p" }
                    val clen = fmt.optString("contentLength").toLongOrNull() ?: 0L
                    formatOptions.add(
                        VideoFormatOption(
                            id = "yt_${fmt.optInt("itag", i)}",
                            label = "Video MP4 ($qLabel)",
                            qualityBadge = qLabel,
                            downloadUrl = u,
                            mediaType = MediaType.VIDEO,
                            ext = "mp4",
                            headers = dlHeaders,
                            sizeBytes = clen
                        )
                    )
                }
            }
            if (bestMp4Url.isNullOrBlank()) return null

            // Kiểm tra xem có luồng adaptive audio trực tiếp không, nếu không thì tách âm thanh từ MP4
            var directAudioUrl: String? = null
            val adaptiveArr = streamingData.optJSONArray("adaptiveFormats")
            if (adaptiveArr != null) {
                for (i in 0 until adaptiveArr.length()) {
                    val af = adaptiveArr.optJSONObject(i) ?: continue
                    val mime = af.optString("mimeType")
                    val u = af.optString("url")
                    if (mime.startsWith("audio/mp4") && u.startsWith("http")) {
                        directAudioUrl = u
                        break
                    }
                }
            }

            formatOptions.add(
                VideoFormatOption(
                    id = "audio_mp3",
                    label = "🎵 Tải nhạc MP3 / Audio (Chỉ âm thanh)",
                    qualityBadge = "MP3",
                    downloadUrl = directAudioUrl ?: bestMp4Url,
                    mediaType = MediaType.AUDIO,
                    ext = "m4a",
                    extractAudioFromVideo = (directAudioUrl == null),
                    headers = dlHeaders
                )
            )

            val details = json.optJSONObject("videoDetails")
            val rawTitle = details?.optString("title").orEmpty().ifBlank { "YouTube_$videoId" }
            val author = details?.optString("author").orEmpty()
            val duration = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
            val thumbs = details?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
            } else ""

            return ExtractedVideo(
                sourceUrl = url,
                downloadUrl = bestMp4Url,
                title = sanitizeTitle(rawTitle),
                author = author,
                durationSec = duration,
                thumbnailUrl = thumbUrl,
                headers = dlHeaders,
                platform = VideoPlatform.YOUTUBE,
                engineUsed = "On-Device YouTube Direct",
                mediaType = MediaType.VIDEO,
                formats = formatOptions
            )
        } catch (e: Throwable) {
            Log.w(TAG, "YouTube direct failed: ${e.message}")
            return null
        }
    }

    // ========================================================================
    // TIER 2: ONLINE SERVER API (Vercel / FastAPI Backend)
    // ========================================================================
    private fun extractViaServerApi(
        context: Context,
        url: String,
        platform: VideoPlatform,
        cookie: String = ""
    ): ExtractedVideo? {
        val baseUrl = getServerUrl(context)
        val endpoint = "$baseUrl/api/proxy-url"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", DESKTOP_UA)
        }

        val payload = JSONObject().apply {
            put("url", url)
            if (cookie.isNotBlank()) {
                put("cookies", cookie)
            }
        }.toString()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }

        val code = conn.responseCode
        val respText = readStream(conn)
        if (code !in 200..299) {
            if (respText.contains("login.php") || respText.contains("LOGIN_REQUIRED")) {
                throw StoryLoginRequiredException(platform, url)
            }
            throw IllegalStateException("Server HTTP $code: ${respText.take(160)}")
        }

        val json = JSONObject(respText)
        if (!json.optBoolean("success", false)) {
            val detail = json.optString("detail", "Server trả về thất bại")
            if (detail.contains("login.php") || detail.contains("LOGIN_REQUIRED")) {
                throw StoryLoginRequiredException(platform, url)
            }
            throw IllegalStateException(detail)
        }

        val headers = mutableMapOf("User-Agent" to DESKTOP_UA)
        val serverHeaders = json.optJSONObject("http_headers")
        if (serverHeaders != null) {
            val keys = serverHeaders.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = serverHeaders.optString(k)
                if (k.isNotBlank() && v.isNotBlank()) {
                    headers[k] = v
                }
            }
        }

        val cleanTitle = sanitizeTitle(json.optString("title").ifBlank { "${platform.displayName}_Media" })
        val author = json.optString("uploader").ifBlank { json.optString("author") }
        val duration = json.optLong("duration", 0L)
        val thumb = json.optString("thumbnail")

        // Kiểm tra nếu Server trả về danh sách ảnh (images / media_items)
        val imagesArr = json.optJSONArray("images")
        if (imagesArr != null && imagesArr.length() > 0) {
            val items = mutableListOf<ExtractedMediaItem>()
            for (i in 0 until imagesArr.length()) {
                val imgUrl = imagesArr.optString(i)
                if (imgUrl.startsWith("http")) {
                    items.add(
                        ExtractedMediaItem(
                            url = imgUrl,
                            mediaType = MediaType.IMAGE,
                            ext = "jpg",
                            titleSuffix = "Anh_${i + 1}",
                            headers = headers
                        )
                    )
                }
            }
            if (items.isNotEmpty()) {
                return ExtractedVideo(
                    sourceUrl = url,
                    downloadUrl = items.first().url,
                    title = cleanTitle,
                    author = author,
                    durationSec = duration,
                    thumbnailUrl = thumb.ifBlank { items.first().url },
                    headers = headers,
                    platform = platform,
                    engineUsed = "Server API (${items.size} ảnh)",
                    mediaType = MediaType.IMAGE,
                    mediaItems = items
                )
            }
        }

        val directUrl = json.optString("direct_url")
        val rawDownloadUrl = json.optString("download_url")
        val resolvedDownloadUrl = when {
            directUrl.startsWith("http") -> directUrl
            rawDownloadUrl.startsWith("http") -> rawDownloadUrl
            rawDownloadUrl.startsWith("/") -> "$baseUrl$rawDownloadUrl"
            else -> ""
        }
        if (resolvedDownloadUrl.isBlank()) return null

        val hdUrl = json.optString("hd_url").takeIf { it.startsWith("http") } ?: resolvedDownloadUrl
        val sdUrl = json.optString("sd_url").takeIf { it.startsWith("http") }
        val audioUrl = json.optString("audio_url").takeIf { it.startsWith("http") }

        val formats = buildDefaultFormats(
            primaryUrl = resolvedDownloadUrl,
            hdUrl = hdUrl,
            sdUrl = sdUrl,
            headers = headers,
            mediaType = MediaType.VIDEO,
            directAudioUrl = audioUrl
        )

        return ExtractedVideo(
            sourceUrl = url,
            downloadUrl = resolvedDownloadUrl,
            title = cleanTitle,
            author = author,
            durationSec = duration,
            thumbnailUrl = thumb,
            headers = headers,
            platform = platform,
            engineUsed = "Server API (${baseUrl.substringAfter("://").substringBefore("/")})",
            mediaType = MediaType.VIDEO,
            formats = formats
        )
    }

    suspend fun checkServerHealth(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context)
            val conn = (URL("$baseUrl/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val body = readStream(conn)
            if (code in 200..299) {
                val json = JSONObject(body)
                val ver = json.optString("version", "2.2")
                val ytdlp = json.optString("yt_dlp_version", "latest")
                Result.success("Server Online (v$ver • yt-dlp $ytdlp)")
            } else {
                Result.failure(IllegalStateException("HTTP $code"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    // ========================================================================
    // HEADLESS WEBVIEW STORY EXTRACTOR (Khi Story tải qua JS với Cookie)
    // ========================================================================
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractStoryViaWebView(
        context: Context,
        storyUrl: String,
        platform: VideoPlatform,
        dlHeaders: Map<String, String>
    ): ExtractedVideo? = withTimeoutOrNull(14000L) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var completed = false
                val webView = WebView(context.applicationContext)
                val mainHandler = Handler(Looper.getMainLooper())

                fun finish(result: ExtractedVideo?) {
                    if (completed) return
                    completed = true
                    runCatching {
                        webView.stopLoading()
                        webView.destroy()
                    }
                    if (cont.isActive) {
                        cont.resume(result)
                    }
                }

                cont.invokeOnCancellation {
                    mainHandler.post {
                        runCatching {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = DESKTOP_UA
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val u = request?.url?.toString().orEmpty()
                        if (u.contains("/login.php") || u.contains("/accounts/login")) {
                            finish(null)
                            return true
                        }
                        return false
                    }

                    override fun onLoadResource(view: WebView?, resUrl: String?) {
                        super.onLoadResource(view, resUrl)
                        val u = resUrl.orEmpty()
                        if (!completed && u.contains(".mp4") && (u.contains("fbcdn.net") || u.contains("cdninstagram.com"))) {
                            val cleanMp4 = u.replace("&bytestart=", "&_ign_bs=").replace(Regex("""&bytestart=\d+"""), "")
                                .replace(Regex("""&byteend=\d+"""), "")
                            finish(
                                ExtractedVideo(
                                    sourceUrl = storyUrl,
                                    downloadUrl = cleanMp4,
                                    title = "${platform.displayName}_Story_${System.currentTimeMillis() / 1000}",
                                    headers = dlHeaders,
                                    platform = platform,
                                    engineUsed = "WebView Story Direct",
                                    mediaType = MediaType.VIDEO,
                                    formats = buildDefaultFormats(cleanMp4, cleanMp4, null, dlHeaders, MediaType.VIDEO)
                                )
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        mainHandler.postDelayed({
                            if (completed) return@postDelayed
                            val js = """
                                (function() {
                                    var v = document.querySelector('video');
                                    if (v && v.src && v.src.indexOf('http') === 0) return 'VIDEO:' + v.src;
                                    var html = document.documentElement.innerHTML;
                                    var m = html.match(/"playable_url_quality_hd"\s*:\s*"([^"]+)"/) ||
                                            html.match(/"browser_native_hd_url"\s*:\s*"([^"]+)"/) ||
                                            html.match(/"playable_url"\s*:\s*"([^"]+)"/) ||
                                            html.match(/"video_versions"\s*:\s*\[\{[^}]*"url"\s*:\s*"([^"]+)"/);
                                    if (m && m[1]) return 'VIDEO:' + m[1];
                                    var imgs = Array.from(document.querySelectorAll('img')).filter(function(i) {
                                        return i.naturalWidth >= 400 && i.src && i.src.indexOf('http') === 0;
                                    });
                                    if (imgs.length > 0) return 'IMAGE:' + imgs[0].src;
                                    return '';
                                })();
                            """.trimIndent()
                            webView.evaluateJavascript(js) { rawResult ->
                                val unquoted = rawResult?.trim('"')?.let { unescapeJsonString(it) }.orEmpty()
                                when {
                                    unquoted.startsWith("VIDEO:") -> {
                                        val vUrl = decodeHtmlEntities(unquoted.removePrefix("VIDEO:"))
                                        if (vUrl.startsWith("http")) {
                                            finish(
                                                ExtractedVideo(
                                                    sourceUrl = storyUrl,
                                                    downloadUrl = vUrl,
                                                    title = "${platform.displayName}_Story_${System.currentTimeMillis() / 1000}",
                                                    headers = dlHeaders,
                                                    platform = platform,
                                                    engineUsed = "WebView Story Direct",
                                                    mediaType = MediaType.VIDEO,
                                                    formats = buildDefaultFormats(vUrl, vUrl, null, dlHeaders, MediaType.VIDEO)
                                                )
                                            )
                                        } else finish(null)
                                    }
                                    unquoted.startsWith("IMAGE:") -> {
                                        val imgUrl = decodeHtmlEntities(unquoted.removePrefix("IMAGE:"))
                                        if (imgUrl.startsWith("http")) {
                                            val item = ExtractedMediaItem(
                                                url = imgUrl,
                                                mediaType = MediaType.IMAGE,
                                                ext = "jpg",
                                                titleSuffix = "Story_1",
                                                headers = dlHeaders
                                            )
                                            finish(
                                                ExtractedVideo(
                                                    sourceUrl = storyUrl,
                                                    downloadUrl = imgUrl,
                                                    title = "${platform.displayName}_Story_${System.currentTimeMillis() / 1000}",
                                                    thumbnailUrl = imgUrl,
                                                    headers = dlHeaders,
                                                    platform = platform,
                                                    engineUsed = "WebView Story Photo",
                                                    mediaType = MediaType.IMAGE,
                                                    mediaItems = listOf(item)
                                                )
                                            )
                                        } else finish(null)
                                    }
                                    else -> finish(null)
                                }
                            }
                        }, 2200L)
                    }
                }

                webView.loadUrl(storyUrl, dlHeaders)
            }
        }
    }

    // ========================================================================
    // FORMAT & PARSING HELPERS
    // ========================================================================
    private fun buildDefaultFormats(
        primaryUrl: String,
        hdUrl: String?,
        sdUrl: String?,
        headers: Map<String, String>,
        mediaType: MediaType,
        directAudioUrl: String? = null
    ): List<VideoFormatOption> {
        if (mediaType == MediaType.IMAGE) return emptyList()
        if (mediaType == MediaType.AUDIO) {
            return listOf(
                VideoFormatOption(
                    id = "audio_mp3",
                    label = "🎵 Âm thanh MP3 / Audio",
                    qualityBadge = "MP3",
                    downloadUrl = primaryUrl,
                    mediaType = MediaType.AUDIO,
                    ext = "mp3",
                    headers = headers
                )
            )
        }

        val list = mutableListOf<VideoFormatOption>()
        val resolvedHd = hdUrl?.takeIf { it.startsWith("http") } ?: primaryUrl
        list.add(
            VideoFormatOption(
                id = "video_hd",
                label = "🎬 Video HD (Chất lượng cao nhất)",
                qualityBadge = "HD",
                downloadUrl = resolvedHd,
                mediaType = MediaType.VIDEO,
                ext = "mp4",
                headers = headers
            )
        )
        if (!sdUrl.isNullOrBlank() && sdUrl.startsWith("http") && sdUrl != resolvedHd) {
            list.add(
                VideoFormatOption(
                    id = "video_sd",
                    label = "🎞️ Video SD (Nhẹ máy, tiết kiệm dung lượng)",
                    qualityBadge = "SD",
                    downloadUrl = sdUrl,
                    mediaType = MediaType.VIDEO,
                    ext = "mp4",
                    headers = headers
                )
            )
        }
        list.add(
            VideoFormatOption(
                id = "audio_mp3",
                label = "🎵 Tải nhạc MP3 / Audio (Chỉ âm thanh)",
                qualityBadge = "MP3",
                downloadUrl = directAudioUrl ?: (sdUrl?.takeIf { it.startsWith("http") } ?: resolvedHd),
                mediaType = MediaType.AUDIO,
                ext = if (directAudioUrl != null && directAudioUrl.contains(".mp3")) "mp3" else "m4a",
                extractAudioFromVideo = (directAudioUrl == null),
                headers = headers
            )
        )
        return list
    }

    internal fun unwrapLoginRedirectUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.contains("/login.php") || trimmed.contains("/accounts/login")) {
            val nextMatch = Regex("""[?&]next=([^&#]+)""").find(trimmed)
            if (nextMatch != null) {
                val decoded = runCatching { URLDecoder.decode(nextMatch.groupValues[1], "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank() && decoded.startsWith("http")) {
                    return decoded
                }
            }
        }
        return trimmed
    }

    private fun extractBalancedJson(text: String, startIdx: Int, openChar: Char, closeChar: Char): String? {
        if (startIdx < 0 || startIdx >= text.length || text[startIdx] != openChar) return null
        var depth = 0
        var inString = false
        var escaped = false
        val limit = (startIdx + 500_000).coerceAtMost(text.length)
        for (i in startIdx until limit) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == openChar) {
                    depth++
                } else if (ch == closeChar) {
                    depth--
                    if (depth == 0) {
                        return text.substring(startIdx, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractHtmlTitle(html: String, defaultTitle: String): String {
        val ogTitle = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)
        val tagTitle = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
        val chosen = (ogTitle ?: tagTitle)?.let { decodeHtmlEntities(it).replace(" | Facebook", "").trim() }
        return if (!chosen.isNullOrBlank() && !chosen.equals("Facebook", ignoreCase = true) && !chosen.equals("Instagram", ignoreCase = true)) {
            chosen
        } else {
            "${defaultTitle}_${System.currentTimeMillis() / 1000}"
        }
    }

    private data class HttpPageResult(
        val finalUrl: String,
        val body: String,
        val cookies: String,
        val redirectedToLogin: Boolean = false
    )

    private fun fetchUrlFollowingRedirects(
        targetUrl: String,
        headers: Map<String, String>,
        maxRedirects: Int = 6
    ): HttpPageResult {
        var currentUrl = unwrapLoginRedirectUrl(targetUrl)
        val cookieMap = LinkedHashMap<String, String>()
        headers["Cookie"]?.split(";")?.forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                cookieMap[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
            }
        }

        for (step in 0 until maxRedirects) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12000
                readTimeout = 15000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (cookieMap.isNotEmpty()) {
                    setRequestProperty(
                        "Cookie",
                        cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    )
                }
            }

            val code = conn.responseCode
            conn.headerFields["Set-Cookie"]?.forEach { rawCookie ->
                val pair = rawCookie.substringBefore(';').trim()
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    cookieMap[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
                }
            }

            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location") ?: break
                val nextUrl = URL(URL(currentUrl), location).toString()
                if (nextUrl.contains("/login.php") || nextUrl.contains("/accounts/login") || nextUrl.contains("/checkpoint/")) {
                    val unwrapped = unwrapLoginRedirectUrl(nextUrl)
                    val realTarget = if (unwrapped != nextUrl) unwrapped else currentUrl
                    val cookieHeader = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    return HttpPageResult(
                        finalUrl = realTarget,
                        body = "",
                        cookies = cookieHeader,
                        redirectedToLogin = true
                    )
                }
                currentUrl = nextUrl
            } else {
                val body = readStream(conn)
                val cookieHeader = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                return HttpPageResult(currentUrl, body, cookieHeader, redirectedToLogin = false)
            }
        }

        val finalConn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 12000
            readTimeout = 15000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val body = readStream(finalConn)
        val cookieHeader = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        return HttpPageResult(currentUrl, body, cookieHeader)
    }

    private fun readStream(conn: HttpURLConnection): String {
        val rawStream = try {
            if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
        } catch (_: Throwable) {
            conn.errorStream
        } ?: return ""
        val stream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
            java.util.zip.GZIPInputStream(rawStream)
        } else {
            rawStream
        }
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun unescapeJsonString(raw: String): String {
        return try {
            JSONObject("""{"v":"$raw"}""").optString("v", raw)
        } catch (_: Throwable) {
            raw.replace("\\\\/", "/")
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u0025", "%")
                .replace("\\u003d", "=")
                .replace("\\u003D", "=")
                .replace("\\u003c", "<")
                .replace("\\u003C", "<")
                .replace("\\u003e", ">")
                .replace("\\u003E", ">")
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x27;", "'")
            .replace("&#064;", "@")
            .replace("&#x201c;", "\"")
            .replace("&#x201d;", "\"")
            .replace("&#x2019;", "'")
    }

    fun sanitizeTitle(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
        return if (cleaned.isBlank()) {
            "Media_${System.currentTimeMillis() / 1000}"
        } else {
            cleaned.take(90)
        }
    }
}
