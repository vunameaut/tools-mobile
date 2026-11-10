package com.vunameaut.powertimer.downloader

import android.content.Context
import android.util.Log
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
     * Giải mã & lấy link stream video trực tiếp siêu nhẹ (không nhúng Python nặng vào app):
     * - Tầng 1: Pure Kotlin On-Device Direct Extractor (TikTok, Facebook, Instagram, YouTube, X/Twitter, Threads) -> < 1.5s, 0 MB thư viện.
     * - Tầng 2: Online Server API (`video-downloader-api-backend.vercel.app`) -> gọi API trực tuyến khi cần hoặc khi người dùng chọn ưu tiên Server API.
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

        val preferOnDevice = isPreferOnDevice(context)

        suspend fun tryOnDevice(): ExtractedVideo? {
            onStatusUpdate("Đang trích xuất nhanh (${platform.displayName})...")
            return try {
                val direct = when (platform) {
                    VideoPlatform.TIKTOK -> extractTikTokDirect(cleanUrl)
                    VideoPlatform.FACEBOOK -> extractFacebookDirect(cleanUrl)
                    VideoPlatform.INSTAGRAM -> extractInstagramDirect(cleanUrl)
                    VideoPlatform.TWITTER -> extractTwitterDirect(cleanUrl)
                    VideoPlatform.YOUTUBE -> extractYouTubeDirect(cleanUrl)
                    VideoPlatform.THREADS -> extractThreadsDirect(cleanUrl)
                    else -> null
                }
                if (direct != null && direct.downloadUrl.startsWith("http")) {
                    Log.i(TAG, "On-Device Direct success (${direct.engineUsed}): ${direct.title}")
                    direct
                } else null
            } catch (e: Throwable) {
                Log.w(TAG, "On-Device direct extractor failed for $cleanUrl: ${e.message}")
                errors.add("Direct: ${e.message}")
                null
            }
        }

        suspend fun tryServerApi(): ExtractedVideo? {
            onStatusUpdate("Đang gọi Server API (${platform.displayName})...")
            return try {
                val serverResult = extractViaServerApi(context, cleanUrl, platform)
                if (serverResult != null && serverResult.downloadUrl.startsWith("http")) {
                    Log.i(TAG, "Cloud Server API success: ${serverResult.title}")
                    serverResult
                } else null
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

            val formats = json.optJSONObject("streamingData")?.optJSONArray("formats") ?: return null
            var bestMp4Url: String? = null
            for (i in formats.length() - 1 downTo 0) {
                val fmt = formats.optJSONObject(i) ?: continue
                val u = fmt.optString("url")
                if (u.startsWith("http")) {
                    bestMp4Url = u
                    break
                }
            }
            if (bestMp4Url.isNullOrBlank()) return null

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
                headers = mapOf("User-Agent" to ytAndroidUa),
                platform = VideoPlatform.YOUTUBE,
                engineUsed = "On-Device YouTube Direct"
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

        val directUrl = json.optString("direct_url")
        val rawDownloadUrl = json.optString("download_url")
        val resolvedDownloadUrl = when {
            directUrl.startsWith("http") -> directUrl
            rawDownloadUrl.startsWith("http") -> rawDownloadUrl
            rawDownloadUrl.startsWith("/") -> "$baseUrl$rawDownloadUrl"
            else -> ""
        }
        if (resolvedDownloadUrl.isBlank()) return null

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

        return ExtractedVideo(
            sourceUrl = url,
            downloadUrl = resolvedDownloadUrl,
            title = sanitizeTitle(json.optString("title").ifBlank { "${platform.displayName}_Video" }),
            author = json.optString("uploader").ifBlank { json.optString("author") },
            durationSec = json.optLong("duration", 0L),
            thumbnailUrl = json.optString("thumbnail"),
            headers = headers,
            platform = platform,
            engineUsed = "Server API (${baseUrl.substringAfter("://").substringBefore("/")})"
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

