package com.ahu.ahutong.data.update

import android.util.Log
import com.ahu.ahutong.data.server.ApkUpdatePolicy
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateDispatcher

/** 下载过程中对外说的事：进度与结果。文案仍由展示侧决定。 */
sealed interface ApkDownloadEvent {

    data object Started : ApkDownloadEvent

    /** [fraction] 为 null 表示服务端没给长度（与迁移前一致：进度条转圈而不显示百分比）。 */
    data class Progress(val fraction: Float?) : ApkDownloadEvent

    /** 本地已有校验通过的包，[elapsedText] 为 null。 */
    data class Succeeded(
        val apkFile: File,
        val elapsedText: String?,
        val alreadyLocal: Boolean
    ) : ApkDownloadEvent

    data class Failed(val message: String) : ApkDownloadEvent
}

/**
 * APK 下载器：统一从镜像流式下载到本地，完成后校验。
 *
 * 它原先长在 MainViewModel 里（约四百行，与界面状态交织）。搬到这里之后，下载与校验的规则
 * 不再受"谁在调用"影响，也能被单测直接驱动（见 ApkDownloadArchitectureTest 的搬迁）。
 */
interface ApkDownloader {

    val events: SharedFlow<ApkDownloadEvent>

    /** 本地已有且校验通过的目标版本包；没有或校验不过（会被删掉）则返回 null。 */
    suspend fun cachedApk(versionCode: Int, sha256: String): File?

    fun start(versionCode: Int, downloadUrl: String, sha256: String, forceRedownload: Boolean = false)

    fun cancel()
}

/** 独立 IO 作用域；取消时同时关闭传输，打断正在读取的响应体。 */
class DefaultApkDownloader @Inject constructor(
    private val directory: ApkDirectory,
    private val transport: ApkDownloadTransport,
    @UpdateDispatcher dispatcher: CoroutineDispatcher
) : ApkDownloader {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _events = MutableSharedFlow<ApkDownloadEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<ApkDownloadEvent> = _events.asSharedFlow()

    private var downloadJob: Job? = null

    override suspend fun cachedApk(versionCode: Int, sha256: String): File? {
        val existing = ApkIntegrity.apkFile(apkDir(), versionCode)
        if (!existing.exists() || existing.length() <= 0) return null
        if (!ApkIntegrity.verifySha256(existing, sha256, "cached APK")) return null
        Log.i(TAG, "APK already exists locally: ${existing.absolutePath}")
        return existing
    }

    override fun start(
        versionCode: Int,
        downloadUrl: String,
        sha256: String,
        forceRedownload: Boolean
    ) {
        val previous = downloadJob
        val previousWasActive = previous?.isActive == true
        previous?.cancel()
        if (previousWasActive) transport.cancelAll()
        downloadJob = scope.launch {
            previous?.join()
            emit(ApkDownloadEvent.Started)
            try {
                // 入口也归一化，避免旧调用方或重试意外访问主站。
                val mirrorUrl = ApkUpdatePolicy.mirrorDownloadUrl(downloadUrl).getOrElse {
                    throw SecurityException("镜像下载地址无效", it)
                }
                val dir = apkDir()
                val outFile = ApkIntegrity.apkFile(dir, versionCode)
                val partFile = File(dir, "${outFile.name}.part")

                if (forceRedownload) {
                    outFile.delete()
                    partFile.delete()
                } else {
                    val existing = cachedApk(versionCode, sha256)
                    if (existing != null) {
                        emit(ApkDownloadEvent.Succeeded(existing, elapsedText = null, alreadyLocal = true))
                        return@launch
                    }
                }

                val startedAt = System.currentTimeMillis()
                Log.i(
                    TAG,
                    "apk download start version=$versionCode, url=$mirrorUrl, mirror=true, " +
                        "partExists=${partFile.exists()}, partBytes=${partFile.length()}"
                )
                val downloadedFile = downloadSingleStream(mirrorUrl, partFile)
                currentCoroutineContext().ensureActive()
                val verifiedSha256 = replaceDownloadedApk(downloadedFile, outFile, sha256)
                Log.i(
                    TAG,
                    "apk download verified version=$versionCode, " +
                        "bytes=${outFile.length()}, sha256=$verifiedSha256"
                )
                emit(
                    ApkDownloadEvent.Succeeded(
                        apkFile = outFile,
                        elapsedText = formatElapsed(System.currentTimeMillis() - startedAt),
                        alreadyLocal = false
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "apk download failed", e)
                emit(ApkDownloadEvent.Failed(e.message ?: "下载失败"))
            }
        }
    }

    override fun cancel() {
        downloadJob?.cancel()
        transport.cancelAll()
    }

    private suspend fun downloadSingleStream(
        downloadUrl: String,
        partFile: File
    ): File {
        val response = openDownloadResponse(downloadUrl)
        if (!response.isSuccessful) {
            closeResponse(response)
            throw IOException("下载失败：HTTP ${response.code()}")
        }
        val body = response.body() ?: run {
            closeResponse(response)
            throw IOException("下载内容为空")
        }
        // ESA/透明 gzip 解压后 Content-Length 可能缺失或只描述压缩传输大小。
        // X-File-Size 描述最终写入磁盘的文件大小，应优先用它计算进度与检查完整性。
        val total = try {
            val fileSize = response.headers()["X-File-Size"]
            if (fileSize != null) {
                fileSize.trim().toLongOrNull()?.takeIf { it > 0 }
                    ?: throw IOException("安装包大小信息无效")
            } else {
                body.contentLength()
            }
        } catch (e: Exception) {
            body.close()
            throw e
        }
        Log.i(
            TAG,
            "single stream response code=${response.code()}, totalBytes=$total, " +
                "bodyBytes=${body.contentLength()}, protocol=${response.raw().protocol}, " +
                "url=${response.raw().request.url}"
        )
        if (total > ApkUpdatePolicy.MAX_APK_BYTES) {
            body.close()
            throw IOException("安装包过大，请稍后重试")
        }

        var completed = 0L
        val startedAt = System.currentTimeMillis()
        body.use { responseBody ->
            emit(ApkDownloadEvent.Progress(if (total > 0) 0f else null))
            partFile.delete()
            var lastEmit = startedAt
            var lastSpeedLog = startedAt
            var lastSpeedBytes = 0L
            var lastReported = 0f
            responseBody.byteStream().use { input: InputStream ->
                BufferedOutputStream(FileOutputStream(partFile), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, read)
                        completed += read
                        if (completed > ApkUpdatePolicy.MAX_APK_BYTES) {
                            throw IOException("安装包超过大小限制")
                        }
                        if (total > 0 && completed > total) {
                            throw IOException("安装包超过声明大小，请重试")
                        }
                        if (total > 0) {
                            val now = System.currentTimeMillis()
                            val progress = (completed.toDouble() / total.toDouble())
                                .coerceIn(0.0, 1.0)
                                .toFloat()
                            if (now - lastSpeedLog >= DOWNLOAD_LOG_INTERVAL_MS) {
                                val intervalBytes = completed - lastSpeedBytes
                                Log.i(
                                    TAG,
                                    "single stream progress $completed/$total, " +
                                        "interval=${speedText(intervalBytes, now - lastSpeedLog)}, " +
                                        "avg=${speedText(completed, now - startedAt)}"
                                )
                                lastSpeedLog = now
                                lastSpeedBytes = completed
                            }
                            if (progress - lastReported >= PROGRESS_MIN_DELTA ||
                                now - lastEmit >= PROGRESS_MIN_INTERVAL_MS ||
                                completed == total
                            ) {
                                emit(ApkDownloadEvent.Progress(progress))
                                lastReported = progress
                                lastEmit = now
                            }
                        }
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }
        }

        if (total > 0 && completed != total) {
            throw IOException("下载不完整（${completed}/$total），请重试")
        }
        if (!partFile.exists() || partFile.length() <= 0L) {
            throw IOException("下载内容为空")
        }
        val elapsed = System.currentTimeMillis() - startedAt
        Log.i(
            TAG,
            "single stream complete bytes=$completed, elapsedMs=$elapsed, " +
                "avg=${speedText(completed, elapsed)}"
        )
        return partFile
    }

    private suspend fun openDownloadResponse(
        initialUrl: String
    ): Response<ResponseBody> {
        var currentUrl = initialUrl
        repeat(ApkUpdatePolicy.MAX_DOWNLOAD_REDIRECTS + 1) { redirectCount ->
            val response = transport.download(currentUrl)
            val finalUrl = response.raw().request.url.toString()
            ApkUpdatePolicy.validateDownloadUrl(finalUrl, mirrorOnly = true).getOrElse {
                closeResponse(response)
                throw SecurityException("下载地址不受信任")
            }

            val code = response.code()
            if (code in 300..399) {
                val location = response.headers()["Location"]
                closeResponse(response)
                if (location.isNullOrBlank()) {
                    throw IOException("下载重定向地址为空")
                }
                if (redirectCount >= ApkUpdatePolicy.MAX_DOWNLOAD_REDIRECTS) {
                    throw IOException("下载重定向次数过多")
                }
                currentUrl = ApkUpdatePolicy.validateDownloadUrl(
                    rawUrl = location,
                    baseUrl = finalUrl,
                    mirrorOnly = true
                ).getOrElse {
                    throw SecurityException("下载重定向到不受信任地址")
                }
                return@repeat
            }
            return response
        }
        throw IOException("下载重定向次数过多")
    }

    private fun closeResponse(response: Response<ResponseBody>) {
        response.body()?.close()
        response.errorBody()?.close()
    }

    private fun replaceDownloadedApk(partFile: File, outFile: File, expectedSha256: String): String {
        val sourceHash = runCatching { ApkIntegrity.sha256Of(partFile) }.getOrNull()
            ?: throw SecurityException("文件校验失败，请重试")
        if (!sourceHash.equals(expectedSha256, ignoreCase = true)) {
            Log.w(TAG, "download sha256 mismatch: expected=$expectedSha256, got=$sourceHash")
            partFile.delete()
            throw SecurityException("文件校验失败，请重试")
        }
        if (outFile.exists() && !outFile.delete()) {
            throw IOException("无法替换旧安装包")
        }
        val renamed = partFile.renameTo(outFile)
        if (!renamed) {
            partFile.inputStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (!partFile.delete()) {
                Log.w(TAG, "failed to delete temporary APK: ${partFile.name}")
            }
            // 跨文件系统的复制不常见，但它的产物同样要独立校验；原子重命名那条路复用同一个摘要。
            if (!ApkIntegrity.verifySha256(outFile, expectedSha256, "copied APK")) {
                throw SecurityException("文件校验失败，请重试")
            }
        }
        return sourceHash
    }

    private suspend fun emit(event: ApkDownloadEvent) {
        currentCoroutineContext().ensureActive()
        _events.emit(event)
    }

    private fun apkDir(): File = directory.dir()

    private fun speedText(bytes: Long, elapsedMillis: Long): String {
        if (elapsedMillis <= 0L) return "n/a"
        val kibPerSecond = bytes * 1000.0 / elapsedMillis / 1024.0
        return String.format(Locale.US, "%.1f KiB/s", kibPerSecond)
    }

    private fun formatElapsed(elapsedMillis: Long): String {
        val totalSeconds = ((elapsedMillis.coerceAtLeast(0L) + 999L) / 1000L).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "${minutes}分${seconds}秒"
    }

    private companion object {
        const val TAG = "ApkUpdate"
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_MIN_INTERVAL_MS = 1_000L
        const val PROGRESS_MIN_DELTA = 0.01f
        const val DOWNLOAD_LOG_INTERVAL_MS = 3_000L
    }
}
