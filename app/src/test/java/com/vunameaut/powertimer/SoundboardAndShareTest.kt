package com.vunameaut.powertimer

import com.vunameaut.powertimer.downloader.DirectVideoExtractor
import com.vunameaut.powertimer.downloader.MediaType
import com.vunameaut.powertimer.downloader.VideoPlatform
import com.vunameaut.powertimer.service.VideoDownloadService
import com.vunameaut.powertimer.ui.screens.CustomMemeSound
import com.vunameaut.powertimer.ui.screens.MemeSoundboardStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundboardAndShareTest {

    @Test
    fun cleanDisplayNameFromFileName_normalizesExtensionsAndSeparators() {
        assertEquals(
            "Tieng cuoi ba dao",
            MemeSoundboardStorage.cleanDisplayNameFromFileName("Tieng_cuoi-ba_dao.mp3")
        )
        assertEquals(
            "Âm thanh của tôi",
            MemeSoundboardStorage.cleanDisplayNameFromFileName("   .wav")
        )
        assertEquals(
            "Meme Sound Effect 2026",
            MemeSoundboardStorage.cleanDisplayNameFromFileName("Meme   Sound_Effect-2026.m4a")
        )
    }

    @Test
    fun customSoundJsonRoundTrip_preservesVietnameseMetadata() {
        val original = listOf(
            CustomMemeSound(
                id = "custom_01",
                emoji = "😂",
                title = "Cười Té Ghế",
                subtitle = "MP3 • Tự tải lên",
                filePath = "/data/user/0/app/files/custom_meme_sounds/custom_01.mp3",
                accentIndex = 3
            ),
            CustomMemeSound(
                id = "custom_02",
                emoji = "🚨",
                title = "Còi Xe Cấp Cứu",
                subtitle = "WAV • Tự tải lên",
                filePath = "/data/user/0/app/files/custom_meme_sounds/custom_02.wav",
                accentIndex = 5
            )
        )

        val serialized = MemeSoundboardStorage.serializeCustomSounds(original)
        val parsed = MemeSoundboardStorage.parseCustomSounds(serialized)

        assertEquals(original, parsed)
        assertTrue(MemeSoundboardStorage.parseCustomSounds("").isEmpty())
        assertTrue(MemeSoundboardStorage.parseCustomSounds("not json").isEmpty())
    }

    @Test
    fun pendingSharedUrl_queuesForFormatSelectionAndClearsById() {
        VideoDownloadService.clearPendingSharedUrl()
        assertNull(VideoDownloadService.pendingSharedDownload.value)

        VideoDownloadService.submitPendingSharedUrl("Xem video này trên TikTok https://vt.tiktok.com/ZS12345/ nhé!")
        val first = VideoDownloadService.pendingSharedDownload.value
        assertNotNull(first)
        assertEquals("https://vt.tiktok.com/ZS12345/", first?.url)

        // Mismatching ID does not clear newer request
        VideoDownloadService.clearPendingSharedUrl(first!!.requestId + 999L)
        assertNotNull(VideoDownloadService.pendingSharedDownload.value)

        // Matching ID clears cleanly
        VideoDownloadService.clearPendingSharedUrl(first.requestId)
        assertNull(VideoDownloadService.pendingSharedDownload.value)
    }

    @Test
    fun extractFirstUrl_handlesSharedTextFromSocialApps() {
        val ytUrl = VideoPlatform.extractFirstUrl("Nghe thử bài này https://youtu.be/dQw4w9WgXcQ!")
        assertEquals("https://youtu.be/dQw4w9WgXcQ", ytUrl)
        assertEquals(VideoPlatform.YOUTUBE, VideoPlatform.detect(ytUrl!!))

        val fbUrl = VideoPlatform.extractFirstUrl("https://www.facebook.com/reel/1234567890?s=yWDuG2")
        assertEquals(VideoPlatform.FACEBOOK, VideoPlatform.detect(fbUrl!!))
    }

    @Test
    fun tiktokStoryUrlNormalization_extractsIdAndRewritesToVideoCandidates() {
        val storyUrl = "https://www.tiktok.com/@minhanh99/story/7412345678901234567?is_from_webapp=1"
        assertEquals("7412345678901234567", DirectVideoExtractor.extractTikTokItemId(storyUrl))

        val candidates = DirectVideoExtractor.buildTikTokStoryCandidateUrls(
            rawUrl = "https://vt.tiktok.com/ZSY99999/",
            resolvedUrl = storyUrl
        )
        assertTrue(candidates.contains("https://www.tiktok.com/@minhanh99/video/7412345678901234567"))
        assertTrue(candidates.contains("https://www.tiktok.com/video/7412345678901234567"))
        assertTrue(candidates.contains("https://m.tiktok.com/v/7412345678901234567.html"))
    }

    @Test
    fun tiktokWebJsonParser_extractsNestedPlayAddrUrlListVideoStory() {
        val fakeHtml = """
            <html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
            {"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{"desc":"Story cuoi tuan","video":{"bitrateInfo":[{"PlayAddr":{"UrlList":["https:\/\/v16-webapp.tiktokcdn.com\/video\/tos\/story123.mp4"]}}]},"music":{"playUrl":"https:\/\/sf16.tiktokcdn.com\/music.mp3"}}}}}}
            </script></html>
        """.trimIndent()

        val extracted = DirectVideoExtractor.parseTikTokWebJson(
            sourceUrl = "https://www.tiktok.com/@user/story/7412345678901234567",
            htmlOrJson = fakeHtml,
            dlHeaders = mapOf("Referer" to "https://www.tiktok.com/")
        )
        assertNotNull(extracted)
        assertEquals(MediaType.VIDEO, extracted!!.mediaType)
        assertEquals("https://v16-webapp.tiktokcdn.com/video/tos/story123.mp4", extracted.downloadUrl)
        assertTrue(extracted.formats.any { it.mediaType == MediaType.AUDIO && it.downloadUrl == "https://sf16.tiktokcdn.com/music.mp3" })
    }

    @Test
    fun tiktokWebJsonParser_extractsPhotoStoryImagePost() {
        val fakeHtml = """
            <html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
            {"desc":"Story anh dep","music":{"playUrl":"https:\/\/sf16.tiktokcdn.com\/bgm.mp3"},"imagePost":{"images":[{"imageURL":{"urlList":["https:\/\/p16.tiktokcdn.com\/img1.jpeg"]}},{"imageURL":{"urlList":["https:\/\/p16.tiktokcdn.com\/img2.jpeg"]}}]}}
            </script></html>
        """.trimIndent()

        val extracted = DirectVideoExtractor.parseTikTokWebJson(
            sourceUrl = "https://www.tiktok.com/@user/story/7412345678901234568",
            htmlOrJson = fakeHtml,
            dlHeaders = emptyMap()
        )
        assertNotNull(extracted)
        assertEquals(MediaType.IMAGE, extracted!!.mediaType)
        assertEquals(2, extracted.mediaItems.size)
        assertEquals("https://p16.tiktokcdn.com/img1.jpeg", extracted.mediaItems[0].url)
    }
}

