package com.vunameaut.powertimer.downloader

import android.content.Context
import android.util.Log
import com.vunameaut.powertimer.PowerTimerApplication
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object DirectVideoExtractor {

    init {
        runCatching {
            System.setProperty("sun.net.http.allowRestrictedHeaders", "true")
        }
    }

    private const val TAG = "DirectVideoExtractor"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

    const val DEFAULT_SERVER_URL = "https://video-downloader-api-backend.vercel.app"
    private const val PREFS_NAME = "video_downloader_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_PREFER_ON_DEVICE = "prefer_on_device"

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

    /**
     * Giải mã & lấy link stream video trực tiếp theo chiến lược 3 tầng:
     * - Tầng 1: On-Device Direct Kotlin Extractor (TikTok, Facebook, Instagram, X/Twitter, Threads, Direct MP4) -> < 1.5s, không cần server.
     * - Tầng 2: On-Device YoutubeDL Engine (YouTube, Shorts, Reddit, Vimeo, +1000 trang) -> chạy trực tiếp trên điện thoại, không cần server.
     * - Tầng 3: Cloud Server API Fallback (`video-downloader-api-backend.vercel.app`) -> dự phòng tự động.
     */
    suspend fun extract(
        context: Context,
        rawUrl: String,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<ExtractedVideo> = withContext(Dispatchers.IO) {
        val cleanUrl = VideoPlatform.extractFirstUrl(rawUrl) ?: rawUrl.trim()
        val platform = VideoPlatform.detect(cleanUrl)
        val errors = mutableListOf<String>()

        // 0. Nếu là link file .mp4 trực tiếp
        if (cleanUrl.substringBefore('?').lowercase().endsWith(".mp4")) {
            return@withContext Result.success(
                ExtractedVideo(
                    sourceUrl = cleanUrl,
                    downloadUrl = cleanUrl,
                    title = "Video_${System.currentTimeMillis() / 1000}",
                    platform = platform,
                    headers = mapOf("User-Agent" to DESKTOP_UA),
                    engineUsed = "Direct MP4"
                )
            )
        }

        // 1. Tầng 1: On-Device Direct Extractors (nhanh nhất, IP gốc của điện thoại)
        onStatusUpdate("Đang trích xuất trực tiếp (${platform.displayName})...")
        try {
            val direct = when (platform) {
                VideoPlatform.TIKTOK -> extractTikTokDirect(cleanUrl)
                VideoPlatform.FACEBOOK -> extractFacebookDirect(cleanUrl)
                VideoPlatform.INSTAGRAM -> extractInstagramDirect(cleanUrl)
                VideoPlatform.TWITTER -> extractTwitterDirect(cleanUrl)
                VideoPlatform.THREADS -> extractThreadsDirect(cleanUrl)
                else -> null
            }
            if (direct != null && direct.downloadUrl.startsWith("http")) {
                Log.i(TAG, "Tier 1 success (${direct.engineUsed}): ${direct.title}")
                return@withContext Result.success(direct)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Tier 1 direct extractor failed for $cleanUrl: ${e.message}")
            errors.add("Direct: ${e.message}")
        }

        // 2. Tầng 2: On-Device yt-dlp Engine (đặc biệt cho YouTube & fallback trên máy)
        onStatusUpdate("Đang phân tích bằng nhân On-Device yt-dlp...")
        try {
            val onDeviceYtdlp = extractWithOnDeviceYoutubeDL(context, cleanUrl, platform)
            if (onDeviceYtdlp != null && onDeviceYtdlp.downloadUrl.startsWith("http")) {
                Log.i(TAG, "Tier 2 on-device yt-dlp success: ${onDeviceYtdlp.title}")
                return@withContext Result.success(onDeviceYtdlp)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Tier 2 on-device YoutubeDL failed for $cleanUrl: ${e.message}")
            errors.add("OnDevice-YTDLP: ${e.message}")
        }

        // 3. Tầng 3: Cloud Server API Fallback
        onStatusUpdate("Đang kết nối Server Cloud dự phòng...")
        try {
            val serverResult = extractViaServerApi(context, cleanUrl, platform)
            if (serverResult != null && serverResult.downloadUrl.startsWith("http")) {
                Log.i(TAG, "Tier 3 cloud server success: ${serverResult.title}")
                return@withContext Result.success(serverResult)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Tier 3 server fallback failed for $cleanUrl: ${e.message}")
            errors.add("Server: ${e.message}")
        }

        Result.failure(
            IllegalStateException(
                "Không thể trích xuất video từ link này (${platform.displayName}). Chi tiết: ${
                    errors.joinToString(" | ").ifBlank { "Không tìm thấy luồng MP4" }
                }"
            )
        )
    }

    // ========================================================================
    // TIER 1A: TIKTOK (TikWM API + Web Rehydration + Cookie Forwarding)
    // ========================================================================
    internal fun extractTikTokDirect(url: String): ExtractedVideo? {
        // Cách 1: TikWM API (trả về MP4 HD không logo, không cần cookie)
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
                        val playUrl = sequenceOf(
                            data.optString("play"),
                            data.optString("hdplay"),
                            data.optString("wmplay")
                        ).firstOrNull { it.isNotBlank() && !it.endsWith(".mp3") }

                        if (!playUrl.isNullOrBlank()) {
                            val finalPlayUrl = if (playUrl.startsWith("/")) {
                                "https://www.tikwm.com$playUrl"
                            } else {
                                playUrl
                            }
                            val authorObj = data.optJSONObject("author")
                            val authorName = authorObj?.optString("nickname").orEmpty()
                                .ifBlank { authorObj?.optString("unique_id").orEmpty() }
                            val rawTitle = data.optString("title").ifBlank {
                                "TikTok_${data.optString("id", "video")}"
                            }
                            return ExtractedVideo(
                                sourceUrl = url,
                                downloadUrl = finalPlayUrl,
                                title = sanitizeTitle(rawTitle),
                                author = authorName,
                                durationSec = data.optLong("duration", 0L),
                                thumbnailUrl = data.optString("cover"),
                                headers = mapOf(
                                    "User-Agent" to DESKTOP_UA,
                                    "Referer" to "https://www.tikwm.com/"
                                ),
                                platform = VideoPlatform.TIKTOK,
                                engineUsed = "On-Device TikTok Direct"
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "TikWM direct failed: ${e.message}")
        }

        // Cách 2: Fetch trực tiếp trang TikTok + parse __UNIVERSAL_DATA_FOR_REHYDRATION__ kèm Cookie
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
                        engineUsed = "On-Device TikTok Web"
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "TikTok web rehydration failed: ${e.message}")
        }

        return null
    }

    // ========================================================================
    // TIER 1B: FACEBOOK (Browser Navigation HTML + Video Embed Plugin)
    // ========================================================================
    internal fun extractFacebookDirect(url: String): ExtractedVideo? {
        val navHeaders = mapOf(
            "User-Agent" to DESKTOP_UA,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9,vi;q=0.8",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Upgrade-Insecure-Requests" to "1"
        )

        val patterns = listOf(
            Regex(""""browser_native_hd_url"\s*:\s*"([^"]+)""""),
            Regex(""""playable_url_quality_hd"\s*:\s*"([^"]+)""""),
            Regex("""hd_src_no_ratelimit\s*:\s*"([^"]+)""""),
            Regex(""""hd_src"\s*:\s*"([^"]+)""""),
            Regex(""""browser_native_sd_url"\s*:\s*"([^"]+)""""),
            Regex(""""playable_url"\s*:\s*"([^"]+)""""),
            Regex("""sd_src_no_ratelimit\s*:\s*"([^"]+)""""),
            Regex(""""sd_src"\s*:\s*"([^"]+)"""")
        )

        try {
            val resolved = fetchUrlFollowingRedirects(url, navHeaders)
            val candidates = mutableListOf(resolved.body)

            // Nếu trang chính chưa khớp, thử thêm trang plugins/video.php
            val encodedHref = URLEncoder.encode(resolved.finalUrl, "UTF-8")
            val pluginUrl = "https://www.facebook.com/plugins/video.php?href=$encodedHref&show_text=0"

            for (attempt in 0..1) {
                val html = if (attempt == 0) {
                    candidates[0]
                } else {
                    fetchUrlFollowingRedirects(pluginUrl, navHeaders).body
                }

                for (pat in patterns) {
                    val m = pat.find(html)
                    if (m != null) {
                        val candidateUrl = unescapeJsonString(m.groupValues[1])
                        if (candidateUrl.startsWith("http")) {
                            val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
                                .find(html)
                            val rawTitle = titleMatch?.groupValues?.get(1)?.let { decodeHtmlEntities(it) }
                                ?.takeIf { it.isNotBlank() && !it.equals("Facebook", ignoreCase = true) }
                                ?: "Facebook_Video_${System.currentTimeMillis() / 1000}"

                            return ExtractedVideo(
                                sourceUrl = url,
                                downloadUrl = candidateUrl,
                                title = sanitizeTitle(rawTitle),
                                headers = mapOf(
                                    "User-Agent" to DESKTOP_UA,
                                    "Referer" to "https://www.facebook.com/"
                                ),
                                platform = VideoPlatform.FACEBOOK,
                                engineUsed = "On-Device Facebook Direct"
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Facebook direct failed: ${e.message}")
        }
        return null
    }

    // ========================================================================
    // TIER 1C: INSTAGRAM (RelayPrefetchedStreamCache video_versions + Embed)
    // ========================================================================
    internal fun extractInstagramDirect(url: String): ExtractedVideo? {
        val navHeaders = mapOf(
            "User-Agent" to DESKTOP_UA,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-Fetch-Mode" to "navigate"
        )

        try {
            // Nếu là link /share/..., giải mã redirect trước để lấy shortcode
            val resolvedUrl = if (url.contains("/share/")) {
                fetchUrlFollowingRedirects(url, navHeaders).finalUrl
            } else {
                url
            }

            val shortcodeMatch = Regex("""/(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)""").find(resolvedUrl)
            val shortcode = shortcodeMatch?.groupValues?.get(1) ?: return null

            // Cách 1: Fetch trang /p/{shortcode}/ với header navigate để lấy RelayPrefetchedStreamCache video_versions
            val postPage = fetchUrlFollowingRedirects("https://www.instagram.com/p/$shortcode/", navHeaders)
            val videoVersionsMatch = Regex(""""video_versions"\s*:\s*\[\{[^}]*"url"\s*:\s*"([^"]+)"""")
                .find(postPage.body)
            if (videoVersionsMatch != null) {
                val videoUrl = decodeHtmlEntities(unescapeJsonString(videoVersionsMatch.groupValues[1]))
                if (videoUrl.startsWith("http")) {
                    val titleMatch = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(postPage.body)
                    val rawTitle = titleMatch?.groupValues?.get(1)?.let { decodeHtmlEntities(it) }
                        ?: "Instagram_$shortcode"
                    return ExtractedVideo(
                        sourceUrl = url,
                        downloadUrl = videoUrl,
                        title = sanitizeTitle(rawTitle),
                        headers = mapOf(
                            "User-Agent" to DESKTOP_UA,
                            "Referer" to "https://www.instagram.com/"
                        ),
                        platform = VideoPlatform.INSTAGRAM,
                        engineUsed = "On-Device Instagram Direct"
                    )
                }
            }

            // Cách 2: Trang /embed/captioned/
            val embedPage = fetchUrlFollowingRedirects(
                "https://www.instagram.com/p/$shortcode/embed/captioned/",
                navHeaders
            )
            val embedPatterns = listOf(
                Regex("""\\"video_url\\"\s*:\s*\\"([^\\"]+)\\""""),
                Regex(""""video_url"\s*:\s*"([^"]+)""""),
                Regex("""<video[^>]+src="([^"]+)"""")
            )
            for (pat in embedPatterns) {
                val m = pat.find(embedPage.body)
                if (m != null) {
                    val raw = m.groupValues[1].replace("\\\\/", "/").replace("\\/", "/")
                    val videoUrl = decodeHtmlEntities(unescapeJsonString(raw))
                    if (videoUrl.startsWith("http")) {
                        return ExtractedVideo(
                            sourceUrl = url,
                            downloadUrl = videoUrl,
                            title = "Instagram_$shortcode",
                            headers = mapOf(
                                "User-Agent" to DESKTOP_UA,
                                "Referer" to "https://www.instagram.com/"
                            ),
                            platform = VideoPlatform.INSTAGRAM,
                            engineUsed = "On-Device Instagram Embed"
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Instagram direct failed: ${e.message}")
        }
        return null
    }

    // ========================================================================
    // TIER 1D: X / TWITTER (FxTwitter + VxTwitter API)
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

            // Cách 1: FxTwitter API
            try {
                val fxPage = fetchUrlFollowingRedirects(
                    "https://api.fxtwitter.com/Twitter/status/$tweetId",
                    mapOf("User-Agent" to DESKTOP_UA, "Accept" to "application/json")
                )
                val json = JSONObject(fxPage.body)
                val tweet = json.optJSONObject("tweet")
                if (tweet != null) {
                    val media = tweet.optJSONObject("media")
                    val videos = media?.optJSONArray("videos") ?: media?.optJSONArray("all")
                    if (videos != null && videos.length() > 0) {
                        for (i in 0 until videos.length()) {
                            val item = videos.optJSONObject(i) ?: continue
                            val vUrl = item.optString("url")
                            if (vUrl.startsWith("http") && vUrl.contains(".mp4")) {
                                val author = tweet.optJSONObject("author")?.optString("name").orEmpty()
                                val text = tweet.optString("text").ifBlank { "X_Video_$tweetId" }
                                return ExtractedVideo(
                                    sourceUrl = url,
                                    downloadUrl = vUrl,
                                    title = sanitizeTitle(text),
                                    author = author,
                                    durationSec = item.optLong("duration", 0L),
                                    thumbnailUrl = item.optString("thumbnail_url"),
                                    headers = mapOf("User-Agent" to DESKTOP_UA),
                                    platform = VideoPlatform.TWITTER,
                                    engineUsed = "On-Device X/Twitter Direct"
                                )
                            }
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
                if (mediaExtended != null) {
                    for (i in 0 until mediaExtended.length()) {
                        val item = mediaExtended.optJSONObject(i) ?: continue
                        val vUrl = item.optString("url")
                        if (vUrl.startsWith("http") && item.optString("type") in listOf("video", "gif")) {
                            val text = json.optString("text").ifBlank { "X_Video_$tweetId" }
                            return ExtractedVideo(
                                sourceUrl = url,
                                downloadUrl = vUrl,
                                title = sanitizeTitle(text),
                                author = json.optString("user_name"),
                                thumbnailUrl = item.optString("thumbnail_url"),
                                headers = mapOf("User-Agent" to DESKTOP_UA),
                                platform = VideoPlatform.TWITTER,
                                engineUsed = "On-Device VxTwitter Direct"
                            )
                        }
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
    // TIER 1E: THREADS
    // ========================================================================
    private fun extractThreadsDirect(url: String): ExtractedVideo? {
        try {
            val page = fetchUrlFollowingRedirects(
                url,
                mapOf(
                    "User-Agent" to DESKTOP_UA,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Sec-Fetch-Mode" to "navigate"
                )
            )
            val m = Regex(""""video_versions"\s*:\s*\[\{[^}]*"url"\s*:\s*"([^"]+)"""").find(page.body)
            if (m != null) {
                val videoUrl = decodeHtmlEntities(unescapeJsonString(m.groupValues[1]))
                if (videoUrl.startsWith("http")) {
                    return ExtractedVideo(
                        sourceUrl = url,
                        downloadUrl = videoUrl,
                        title = "Threads_${System.currentTimeMillis() / 1000}",
                        headers = mapOf("User-Agent" to DESKTOP_UA),
                        platform = VideoPlatform.THREADS,
                        engineUsed = "On-Device Threads Direct"
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Threads direct failed: ${e.message}")
        }
        return null
    }

    // ========================================================================
    // TIER 2: ON-DEVICE YOUTUBEDL-ANDROID (YouTube, Shorts, 1000+ sites)
    // ========================================================================
    private fun extractWithOnDeviceYoutubeDL(
        context: Context,
        url: String,
        platform: VideoPlatform
    ): ExtractedVideo? {
        if (!PowerTimerApplication.isYoutubeDLReady) {
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
            } catch (e: Throwable) {
                Log.w(TAG, "Lazy YoutubeDL init failed: ${e.message}")
                return null
            }
        }

        val request = YoutubeDLRequest(url).apply {
            addOption("-f", "best[ext=mp4][protocol^=http][height<=1080]/best[ext=mp4][protocol^=http]/best[protocol^=http]/best")
            addOption("--no-playlist")
            addOption("--geo-bypass")
            addOption("--no-check-certificates")
        }

        val info = YoutubeDL.getInstance().getInfo(request)
        var directUrl = info.url

        if (directUrl.isNullOrBlank() || directUrl.contains(".m3u8")) {
            val formats = info.formats.orEmpty()
            val progressiveMp4 = formats.filter { fmt ->
                val u = fmt.url.orEmpty()
                u.startsWith("http") &&
                    !u.contains(".m3u8") &&
                    fmt.ext == "mp4" &&
                    fmt.vcodec != "none" &&
                    fmt.acodec != "none"
            }.maxByOrNull { it.height }

            val anyProgressive = progressiveMp4 ?: formats.firstOrNull { fmt ->
                val u = fmt.url.orEmpty()
                u.startsWith("http") && !u.contains(".m3u8") && fmt.vcodec != "none"
            }
            if (anyProgressive != null && !anyProgressive.url.isNullOrBlank()) {
                directUrl = anyProgressive.url
            }
        }

        if (directUrl.isNullOrBlank()) return null

        val headers = mutableMapOf("User-Agent" to DESKTOP_UA)
        info.httpHeaders?.forEach { (k, v) ->
            if (!k.isNullOrBlank() && !v.isNullOrBlank()) {
                headers[k] = v
            }
        }

        return ExtractedVideo(
            sourceUrl = url,
            downloadUrl = directUrl,
            title = sanitizeTitle(info.title ?: "${platform.displayName}_Video"),
            author = info.uploader.orEmpty(),
            durationSec = info.duration.toLong(),
            thumbnailUrl = info.thumbnail.orEmpty(),
            headers = headers,
            platform = platform,
            engineUsed = "On-Device yt-dlp Engine"
        )
    }

    // ========================================================================
    // TIER 3: CLOUD SERVER API FALLBACK (Vercel / FastAPI Backend)
    // ========================================================================
    private fun extractViaServerApi(
        context: Context,
        url: String,
        platform: VideoPlatform
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
        }.toString()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }

        val code = conn.responseCode
        val respText = readStream(conn)
        if (code !in 200..299) {
            throw IllegalStateException("Server HTTP $code: ${respText.take(160)}")
        }

        val json = JSONObject(respText)
        if (!json.optBoolean("success", false)) {
            throw IllegalStateException(json.optString("detail", "Server trả về thất bại"))
        }

        val streamUrl = json.optString("download_url")
            .ifBlank { json.optString("direct_url") }
        if (streamUrl.isBlank()) return null

        return ExtractedVideo(
            sourceUrl = url,
            downloadUrl = streamUrl,
            title = sanitizeTitle(json.optString("title").ifBlank { "${platform.displayName}_Video" }),
            author = json.optString("author"),
            durationSec = json.optLong("duration", 0L),
            thumbnailUrl = json.optString("thumbnail"),
            headers = mapOf("User-Agent" to DESKTOP_UA),
            platform = platform,
            engineUsed = "Server Cloud (${json.optString("engine", "API")})"
        )
    }

    // ========================================================================
    // HTTP & PARSING HELPERS
    // ========================================================================
    private data class HttpPageResult(
        val finalUrl: String,
        val body: String,
        val cookies: String
    )

    private fun fetchUrlFollowingRedirects(
        targetUrl: String,
        headers: Map<String, String>,
        maxRedirects: Int = 6
    ): HttpPageResult {
        var currentUrl = targetUrl
        val cookieMap = LinkedHashMap<String, String>()

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
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                val body = readStream(conn)
                val cookieHeader = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                return HttpPageResult(currentUrl, body, cookieHeader)
            }
        }

        val finalConn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
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
    }

    fun sanitizeTitle(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
        return if (cleaned.isBlank()) {
            "Video_${System.currentTimeMillis() / 1000}"
        } else {
            cleaned.take(90)
        }
    }
}

