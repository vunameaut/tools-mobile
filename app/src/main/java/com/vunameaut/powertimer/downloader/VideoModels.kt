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
        displayName = "Web Media",
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

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE
}

enum class DownloadPreference(
    val title: String,
    val shortLabel: String
) {
    AUTO_HD("Video HD / Full Ảnh", "🎬 HD / Full Ảnh"),
    VIDEO_SD("Video SD (Nhẹ máy)", "🎞️ Video SD"),
    AUDIO_MP3("Nhạc MP3 / Audio", "🎵 Nhạc MP3"),
    ASK_FORMAT("Hỏi chất lượng mỗi lần", "🎛️ Tự chọn")
}

data class VideoFormatOption(
    val id: String,
    val label: String,
    val qualityBadge: String,
    val downloadUrl: String,
    val mediaType: MediaType = MediaType.VIDEO,
    val ext: String = "mp4",
    val extractAudioFromVideo: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val sizeBytes: Long = 0L
)

data class ExtractedMediaItem(
    val url: String,
    val mediaType: MediaType,
    val ext: String = when (mediaType) {
        MediaType.IMAGE -> "jpg"
        MediaType.AUDIO -> "mp3"
        MediaType.VIDEO -> "mp4"
    },
    val titleSuffix: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val headers: Map<String, String> = emptyMap()
)

class StoryLoginRequiredException(
    val platform: VideoPlatform,
    val storyUrl: String,
    message: String = "Story ${platform.displayName} yêu cầu đăng nhập 1 lần để xem & tải. Hãy bấm nút \"Đăng nhập ${platform.displayName}\" bên dưới để tải tự động!"
) : IllegalStateException(message)

data class ExtractedVideo(
    val sourceUrl: String,
    val downloadUrl: String,
    val title: String,
    val author: String = "",
    val durationSec: Long = 0L,
    val thumbnailUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val platform: VideoPlatform = VideoPlatform.detect(sourceUrl),
    val engineUsed: String = "On-Device Direct",
    val mediaType: MediaType = MediaType.VIDEO,
    val formats: List<VideoFormatOption> = emptyList(),
    val mediaItems: List<ExtractedMediaItem> = emptyList()
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
    val title: String = "Đang phân tích link...",
    val author: String = "",
    val engineUsed: String = "Trực tiếp trên thiết bị",
    val status: DownloadStatus = DownloadStatus.EXTRACTING,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val mediaType: MediaType = MediaType.VIDEO,
    val qualityLabel: String = "",
    val totalItems: Int = 1,
    val completedItems: Int = 0,
    val savedUri: String? = null,
    val savedUris: List<String> = emptyList(),
    val savedFilePath: String? = null,
    val savedFileName: String? = null,
    val errorMessage: String? = null,
    val requiresLogin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
