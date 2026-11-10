package com.vunameaut.powertimer.downloader

import androidx.compose.ui.graphics.Color

enum class VideoPlatform(
    val displayName: String,
    val badgeColor: Color,
    val hostKeywords: List<String>
) {
    TIKTOK(
        displayName = "TikTok",
        badgeColor = Color(0xFFFF0050),
        hostKeywords = listOf("tiktok.com", "douyin.com")
    ),
    FACEBOOK(
        displayName = "Facebook",
        badgeColor = Color(0xFF1877F2),
        hostKeywords = listOf("facebook.com", "fb.watch", "fb.gg", "fb.com")
    ),
    INSTAGRAM(
        displayName = "Instagram",
        badgeColor = Color(0xFFE1306C),
        hostKeywords = listOf("instagram.com", "instagr.am")
    ),
    YOUTUBE(
        displayName = "YouTube",
        badgeColor = Color(0xFFFF0033),
        hostKeywords = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")
    ),
    TWITTER(
        displayName = "X / Twitter",
        badgeColor = Color(0xFF1DA1F2),
        hostKeywords = listOf("twitter.com", "x.com", "t.co", "fxtwitter.com", "vxtwitter.com")
    ),
    THREADS(
        displayName = "Threads",
        badgeColor = Color(0xFFA855F7),
        hostKeywords = listOf("threads.net", "threads.com")
    ),
    OTHER(
        displayName = "Web Video",
        badgeColor = Color(0xFF22D3EE),
        hostKeywords = emptyList()
    );

    companion object {
        fun detect(url: String): VideoPlatform {
            val lower = url.lowercase()
            return entries.firstOrNull { platform ->
                platform.hostKeywords.any { lower.contains(it) }
            } ?: OTHER
        }

        /**
         * Trích xuất URL hợp lệ đầu tiên từ đoạn text được chia sẻ
         * (VD: "Xem video này trên TikTok https://vt.tiktok.com/ZS...").
         */
        fun extractFirstUrl(rawText: String?): String? {
            if (rawText.isNullOrBlank()) return null
            val regex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
            val match = regex.find(rawText.trim()) ?: return null
            return match.value.trimEnd('.', ',', ';', ')', ']', '!', '?')
        }
    }
}

data class ExtractedVideo(
    val sourceUrl: String,
    val downloadUrl: String,
    val title: String,
    val author: String = "",
    val durationSec: Long = 0L,
    val thumbnailUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val platform: VideoPlatform = VideoPlatform.detect(sourceUrl),
    val engineUsed: String = "On-Device Direct"
)

enum class DownloadStatus {
    EXTRACTING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTaskState(
    val id: String,
    val sourceUrl: String,
    val platform: VideoPlatform,
    val title: String = "Đang phân tích link video...",
    val author: String = "",
    val engineUsed: String = "Trực tiếp trên thiết bị",
    val status: DownloadStatus = DownloadStatus.EXTRACTING,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val savedUri: String? = null,
    val savedFilePath: String? = null,
    val savedFileName: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

