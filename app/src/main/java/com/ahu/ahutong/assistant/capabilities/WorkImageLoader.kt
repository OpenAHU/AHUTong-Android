package com.ahu.ahutong.assistant.capabilities

import android.util.Base64
import com.ahu.ahutong.assistant.model.AssistantImage
import com.ahu.ahutong.data.network.AhuHttp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 读取题目图片并转成 data URL。只在模型支持视觉时调用；
 * 学习通图床只认 Referer，不依赖 cookie，所以这里不带会话。
 */
internal object WorkImageLoader {

    suspend fun load(refs: List<Pair<String, String>>): List<AssistantImage> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<AssistantImage>()
            val seen = mutableSetOf<String>()
            var totalBytes = 0
            for ((rawUrl, label) in refs) {
                if (result.size >= MAX_IMAGES || totalBytes >= MAX_TOTAL_BYTES) break
                val url = resolveImageUrl(rawUrl) ?: continue
                if (!seen.add(url)) continue
                if (url.startsWith("data:image/")) {
                    if (url.length <= MAX_DATA_URL_LENGTH) {
                        result += AssistantImage(url, label)
                        totalBytes += url.length * 3 / 4
                    }
                    continue
                }
                val bytes = fetch(url) ?: continue
                if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) continue
                if (totalBytes + bytes.size > MAX_TOTAL_BYTES) continue
                val mime = mimeOf(url, bytes) ?: continue
                val dataUrl = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                result += AssistantImage(dataUrl, label)
                totalBytes += bytes.size
            }
            result
        }

    internal fun resolveImageUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        return when {
            value.startsWith("data:image/") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("https://") || value.startsWith("http://") -> value
            value.startsWith("/") -> "https://mooc1.chaoxing.com$value"
            else -> "https://mooc1.chaoxing.com/$value"
        }
    }

    private fun fetch(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Accept", "image/*,*/*;q=0.8")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val declared = response.header("Content-Length")?.toLongOrNull()
                if (declared != null && declared > MAX_IMAGE_BYTES) return null
                response.body?.bytes()
            }
        }.getOrNull()
    }

    private fun mimeOf(url: String, bytes: ByteArray): String? {
        sniffMime(bytes)?.let { return it }
        return when (url.substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> null
        }
    }

    private fun sniffMime(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        return when {
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() -> "image/gif"
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "image/webp"
            else -> null
        }
    }

    private val client: OkHttpClient by lazy {
        AhuHttp.plain(
            connectTimeoutSeconds = 10,
            readTimeoutSeconds = 20,
            captureDiagnostics = false,
        ).build()
    }

    private const val REFERER = "https://mooc1.chaoxing.com/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val MAX_IMAGES = 8
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 6 * 1024 * 1024
    private const val MAX_DATA_URL_LENGTH = 3_000_000
}
