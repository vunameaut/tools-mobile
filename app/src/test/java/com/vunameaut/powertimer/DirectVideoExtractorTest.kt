package com.vunameaut.powertimer

import com.vunameaut.powertimer.downloader.DirectVideoExtractor
import com.vunameaut.powertimer.downloader.ExtractedVideo
import com.vunameaut.powertimer.downloader.VideoPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class DirectVideoExtractorTest {

    private fun verifyMp4Stream(extracted: ExtractedVideo) {
        println("Testing stream for [${extracted.platform.displayName}] (${extracted.engineUsed}): ${extracted.title}")
        println(" -> Stream URL: ${extracted.downloadUrl.take(100)}...")

        var currentUrl = extracted.downloadUrl
        var conn: HttpURLConnection? = null
        for (i in 0 until 5) {
            val c = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Range", "bytes=0-65535")
                extracted.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = c.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val loc = c.getHeaderField("Location") ?: break
                currentUrl = URL(URL(currentUrl), loc).toString()
            } else {
                conn = c
                break
            }
        }
        assertNotNull("Connection must not be null", conn)
        val statusCode = conn!!.responseCode
        println(" -> HTTP Status: $statusCode, Content-Type: ${conn.contentType}")
        assertTrue("Expected HTTP 200 or 206, got $statusCode", statusCode == 200 || statusCode == 206)

        val bytes = conn.inputStream.use { it.readNBytes(16384) }
        println(" -> Read ${bytes.size} bytes, header=${bytes.take(12).map { it.toInt().and(0xFF).toString(16) }}")
        assertTrue("Expected at least 1024 bytes, got ${bytes.size}", bytes.size >= 1024)
        val headerAscii = String(bytes.take(32).toByteArray(), Charsets.ISO_8859_1)
        assertTrue("Expected MP4 'ftyp' signature in header", headerAscii.contains("ftyp"))
    }

    @Test
    fun testShareTextUrlExtractionAndPlatformDetection() {
        val tiktokShare = "Xem video này cực hay trên TikTok nhé! https://vt.tiktok.com/ZSY12345/."
        val extracted = VideoPlatform.extractFirstUrl(tiktokShare)
        assertEquals("https://vt.tiktok.com/ZSY12345/", extracted)
        assertEquals(VideoPlatform.TIKTOK, VideoPlatform.detect(extracted!!))

        val fbShare = "Hãy xem video trên Facebook: https://fb.watch/abcXYZ123/"
        val fbUrl = VideoPlatform.extractFirstUrl(fbShare)
        assertEquals("https://fb.watch/abcXYZ123/", fbUrl)
        assertEquals(VideoPlatform.FACEBOOK, VideoPlatform.detect(fbUrl!!))
    }

    @Test
    fun testTikTokDirectExtractionAndDownload() {
        val res = DirectVideoExtractor.extractTikTokDirect("https://www.tiktok.com/@tiktok/video/7106594312292453675")
        assertNotNull("TikTok direct extraction should succeed", res)
        verifyMp4Stream(res!!)
    }

    @Test
    fun testFacebookDirectExtractionAndDownload() {
        val res = DirectVideoExtractor.extractFacebookDirect("https://www.facebook.com/facebookapp/videos/10153231379946729/")
        assertNotNull("Facebook direct extraction should succeed", res)
        verifyMp4Stream(res!!)
    }

    @Test
    fun testInstagramReelAndPostDirectExtractionAndDownload() {
        val reel = DirectVideoExtractor.extractInstagramDirect("https://www.instagram.com/reel/Chunk8-jurw/")
        assertNotNull("Instagram Reel direct extraction should succeed", reel)
        verifyMp4Stream(reel!!)

        val post = DirectVideoExtractor.extractInstagramDirect("https://www.instagram.com/p/BQ0eAlwhDrw/")
        assertNotNull("Instagram Post direct extraction should succeed", post)
        verifyMp4Stream(post!!)
    }

    @Test
    fun testTwitterDirectExtractionAndDownload() {
        val res = DirectVideoExtractor.extractTwitterDirect("https://x.com/MesoMax919/status/1575560063510810624")
        assertNotNull("X/Twitter direct extraction should succeed", res)
        verifyMp4Stream(res!!)
    }

    @Test
    fun testYouTubeDirectExtractionAndDownload() {
        val res = DirectVideoExtractor.extractYouTubeDirect("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        assertNotNull("YouTube direct extraction should succeed", res)
        verifyMp4Stream(res!!)
    }
}

