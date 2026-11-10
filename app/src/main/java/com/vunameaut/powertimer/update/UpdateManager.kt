package com.vunameaut.powertimer.update

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.vunameaut.powertimer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Thông tin một bản phát hành mới trên GitHub Releases. */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long
)

/**
 * Kiểm tra / tải / cài đặt bản cập nhật dựa trên GitHub Releases
 * (được GitHub Actions tự động build & publish mỗi lần push lên `main`).
 */
object UpdateManager {

    const val REPO = "vunameaut/tools-mobile"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"

    /** Trả về `null` nếu đang là bản mới nhất. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = "$LATEST_API?t=${System.currentTimeMillis()}"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ToolsHub-Updater/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode == 404) return@withContext null // chưa có release nào
            if (conn.responseCode != 200) error("GitHub trả về mã ${conn.responseCode}")

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val remoteVersion = json.getString("tag_name").removePrefix("v").trim()
            if (!isNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@withContext null

            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.getString("browser_download_url")
                    size = asset.optLong("size", 0L)
                    break
                }
            }
            if (apkUrl == null) return@withContext null

            UpdateInfo(
                versionName = remoteVersion,
                notes = json.optString("body", "").trim(),
                apkUrl = apkUrl,
                sizeBytes = size
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Tải APK về cache với tên file theo phiên bản, kiểm tra tính toàn vẹn trước khi cài. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val safeVer = info.versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val target = File(dir, "ToolsHub-v$safeVer.apk")

        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "ToolsHub-Updater/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode != 200) error("Tải thất bại (HTTP ${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastReport > 80) {
                            lastReport = now
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            if (target.length() < 1_000_000L || (info.sizeBytes > 0L && target.length() != info.sizeBytes)) {
                val downloadedBytes = target.length()
                target.delete()
                error("File APK tải về chưa hoàn chỉnh ($downloadedBytes / ${info.sizeBytes} bytes)")
            }
            val archiveInfo = context.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
            if (archiveInfo == null) {
                target.delete()
                error("File APK tải về bị lỗi cấu trúc gói cài đặt")
            }
            onProgress(1f)
            target
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Mở trình cài đặt hệ thống. Trả về `false` nếu cần cấp quyền
     * "Cài ứng dụng không rõ nguồn gốc" trước (đã mở sẵn màn hình cấp quyền).
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() <= 0L) return false

        if (!context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(permIntent)
            return false
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("", uri)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(installIntent)
        return true
    }

    /** So sánh phiên bản dạng 1.0.12 (bỏ qua hậu tố). */
    internal fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
