package com.vunameaut.powertimer.update

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
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ToolsHub-Updater")
        }
        try {
            if (conn.responseCode == 404) return@withContext null // chưa có release nào
            if (conn.responseCode != 200) error("GitHub trả về mã ${conn.responseCode}")

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val remoteVersion = json.getString("tag_name").removePrefix("v")
            if (!isNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@withContext null

            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
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

    /** Tải APK về cache, báo tiến độ 0f..1f. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(dir, "update.apk")

        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ToolsHub-Updater")
        }
        try {
            if (conn.responseCode != 200) error("Tải thất bại (mã ${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastReport > 100) {
                            lastReport = now
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
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
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
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
