package com.ahu.ahutong.ui.state

import android.content.Context
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppVersion
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.data.server.ApkSegmentDownloadPolicy
import com.ahu.ahutong.data.server.ApkUpdatePolicy
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.PriorityQueue

class MainViewModel : ViewModel() {

    companion object {
        private val apkDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val gson = Gson()
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_MIN_INTERVAL_MS = 1_000L
        private const val PROGRESS_MIN_DELTA = 0.01f
        private const val MIRROR_PROMPT_DELAY_MS = 5_000L
        private const val MIRROR_PROMPT_PROGRESS_THRESHOLD = 0.30f
        private const val META_MIN_DELTA_BYTES = 256 * 1024L
        private const val SEGMENT_LOG_INTERVAL_MS = 3_000L
        private const val ADAPT_WINDOW_MS = 1_000L
        private const val MAX_RANGE_ATTEMPTS = 8
        private const val CONCURRENCY_GAIN_THRESHOLD = 0.15
        private const val CONCURRENCY_LOW_GAIN_THRESHOLD = 0.10
        private const val CONCURRENCY_DROP_THRESHOLD = -0.20
        private const val SPLIT_ROLLBACK_EWMA_RATIO = 0.85
        private const val SPLIT_ROLLBACK_WINDOW_RATIO = 0.80
        private const val SPLIT_ACCEPT_EWMA_RATIO = 0.95
        private const val SPLIT_TRIAL_MAX_MS = 5_000L
        private const val SPLIT_COOLDOWN_MS = 10_000L
        private const val SPLIT_MIN_RUNNING_MS = 1_000L
        private const val TAIL_RESCUE_PROGRESS_THRESHOLD = 0.75f
        private const val TAIL_RESCUE_LATE_PROGRESS_THRESHOLD = 0.90f
        private const val MIN_ACTIVE_RANGE_WORKERS = 8
        private const val MAX_CONCURRENT_SPLIT_TRIALS = 3
        private const val HTTP_PARTIAL_CONTENT = 206
    }

    private class RangeUnsupportedException(message: String) : IOException(message)

    private data class ContentRange(
        val start: Long,
        val end: Long,
        val totalBytes: Long
    )

    private data class DownloadProbe(
        val finalUrl: String,
        val totalBytes: Long,
        val etag: String?,
        val lastModified: String?
    )

    private data class SplitCandidate(
        val rangeId: String,
        val reason: String,
        val baselineEwmaBytesPerSec: Double,
        val baselineWindowBytesPerSec: Double,
        val baselineTargetConcurrency: Int
    )

    private data class SplitRollbackRequest(
        val trialId: String,
        val childRangeIds: List<String>,
        val reason: String
    )

    private data class SplitTrial(
        val id: String,
        val parentRangeId: String,
        val childRangeIds: List<String>,
        val baselineEwmaBytesPerSec: Double,
        val baselineWindowBytesPerSec: Double,
        val baselineTargetConcurrency: Int,
        val startedAt: Long,
        val evaluateAfterWindows: Int = 2,
        val status: SplitTrialStatus = SplitTrialStatus.RUNNING
    )

    private enum class SplitTrialStatus {
        RUNNING,
        ACCEPTED,
        ROLLED_BACK
    }

    // App update UI states
    var showApkUpdateDialog = mutableStateOf(false)
    var apkUpdateInfo = mutableStateOf<ApkUpdateInfo?>(null)
    var apkDownloading = mutableStateOf(false)
    var apkProgress = mutableStateOf<Float?>(null)
    var apkActiveRangeCount = mutableStateOf<Int?>(null)
    var apkDownloadSegments = mutableStateOf<List<ApkDownloadSegment>>(emptyList())
    var apkDownloadElapsedText = mutableStateOf<String?>(null)
    var apkErrorText = mutableStateOf<String?>(null)
    var downloadedApkFile = mutableStateOf<File?>(null)
    var apkUpdateChecking = mutableStateOf(false)
    var showApkMirrorPrompt = mutableStateOf(false)
    private val apkUsingMirrorSource = mutableStateOf(false)
    /** 本地已存在目标版本 APK，可直接安装 */
    var apkLocalReady = mutableStateOf(false)

    private var apkDownloadJob: Job? = null
    private var installAfterApkDownload = false
    private var showDialogWhenApkDownloadCompletes = false

    private val apkFileRegex = Regex("""^update-(\d+)\.apk(?:\.(?:part|meta))?$""")

    /** 计算文件 SHA-256，返回小写 hex，必须在 IO 线程调用 */
    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 启动时检查云端更新、清理残留 APK、检测本地缓存
     * 全部在 IO 线程执行，不阻塞主线程
     */
    suspend fun checkApkUpdate(context: Context) = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir

        // 1. 清理版本号 <= 当前版本的残留 APK（安全校验文件名）
        cleanStaleApks(dir)

        // 2. 从云端获取最新版本信息
        val info = runCatching { AhuTong.API.getApkUpdateInfo() }
            .onFailure { Log.w("ApkUpdate", "startup update check request failed", it) }
            .getOrNull() ?: return@withContext
        val update = ApkUpdatePolicy.validate(info, AppVersion.code()).getOrElse {
            Log.w("ApkUpdate", "ignore invalid APK update metadata: ${it.message}")
            return@withContext
        }

        // 3. 检查本地是否已有该版本的 APK，并校验 sha256
        val localApk = File(dir, "update-${update.info.versionCode}.apk")
        val localReady = if (localApk.exists() && localApk.length() > 0) {
            verifyCachedApk(localApk, update.sha256, "local APK")
        } else false

        withContext(Dispatchers.Main) {
            apkUpdateInfo.value = update.info
            apkErrorText.value = null
            apkLocalReady.value = localReady
            if (!localReady) {
                apkDownloadElapsedText.value = null
            }
            showApkUpdateDialog.value = true
            if (!localReady && !apkDownloading.value) {
                startApkDownload(context.applicationContext, installAfterDownload = false)
            }
        }
    }

    /**
     * 清理版本号 <= 当前版本的 APK，跳过不符合命名规范的文件
     */
    private fun cleanStaleApks(dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            val match = apkFileRegex.matchEntire(file.name) ?: continue
            val versionCode = match.groupValues[1].toIntOrNull() ?: continue
            if (versionCode <= AppVersion.code()) {
                Log.i("ApkUpdate", "deleting stale APK: ${file.name}")
                file.delete()
            }
        }
    }

    /**
     * 直接安装本地已缓存的 APK（用户点击"安装"按钮）
     */
    fun installLocalApk(context: Context) {
        val update = selectedValidatedUpdate() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val localApk = File(dir, "update-${update.info.versionCode}.apk")
            if (!localApk.exists() || localApk.length() <= 0) {
                withContext(Dispatchers.Main) {
                    apkLocalReady.value = false
                    apkDownloadElapsedText.value = null
                    apkErrorText.value = "本地文件已丢失，请重新下载"
                }
                return@launch
            }
            if (!verifyCachedApk(localApk, update.sha256, "install APK")) {
                withContext(Dispatchers.Main) {
                    apkLocalReady.value = false
                    apkDownloadElapsedText.value = null
                    apkErrorText.value = "本地文件已损坏，请重新下载"
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                downloadedApkFile.value = localApk
            }
        }
    }

    fun startApkDownload(
        context: Context,
        forceRedownload: Boolean = false,
        installAfterDownload: Boolean = false
    ) {
        val update = selectedValidatedUpdate() ?: return
        if (apkDownloading.value) return
        apkDownloading.value = true
        apkErrorText.value = null
        apkProgress.value = null
        apkActiveRangeCount.value = null
        apkDownloadSegments.value = emptyList()
        apkDownloadElapsedText.value = null
        showApkMirrorPrompt.value = false
        apkUsingMirrorSource.value = false
        installAfterApkDownload = installAfterDownload
        val appContext = context.applicationContext

        if (!forceRedownload) {
            // 检查本地是否已存在该版本 APK 并校验完整性（IO 安全）
            apkDownloadScope.launch {
                val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
                val existingApk = File(dir, "update-${update.info.versionCode}.apk")
                if (existingApk.exists() && existingApk.length() > 0) {
                    if (!verifyCachedApk(existingApk, update.sha256, "cached APK")) {
                        withContext(Dispatchers.Main) {
                            apkLocalReady.value = false
                            doApkDownload(appContext, update)
                        }
                        return@launch
                    }
                    Log.i("ApkUpdate", "APK already exists locally: ${existingApk.absolutePath}")
                    withContext(Dispatchers.Main) {
                        apkDownloading.value = false
                        apkProgress.value = null
                        apkActiveRangeCount.value = null
                        apkDownloadSegments.value = emptyList()
                        apkDownloadElapsedText.value = null
                        showApkMirrorPrompt.value = false
                        apkUsingMirrorSource.value = false
                        apkLocalReady.value = true
                        if (installAfterApkDownload) {
                            downloadedApkFile.value = existingApk
                        } else if (showDialogWhenApkDownloadCompletes) {
                            showDialogWhenApkDownloadCompletes = false
                            showApkUpdateDialog.value = true
                        }
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) { doApkDownload(appContext, update) }
            }
        } else {
            // 强制重新下载：先删除本地缓存
            apkDownloadScope.launch {
                val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
                val existingApk = File(dir, "update-${update.info.versionCode}.apk")
                existingApk.delete()
                File(dir, "${existingApk.name}.part").delete()
                File(dir, "${existingApk.name}.meta").delete()
                withContext(Dispatchers.Main) {
                    apkLocalReady.value = false
                    apkDownloadElapsedText.value = null
                    doApkDownload(appContext, update)
                }
            }
        }
    }

    private fun doApkDownload(
        context: Context,
        update: ApkUpdatePolicy.ValidatedUpdate,
        useMirrorSource: Boolean = false
    ) {
        apkDownloading.value = true
        apkErrorText.value = null
        apkProgress.value = null
        apkActiveRangeCount.value = null
        apkDownloadSegments.value = emptyList()
        apkDownloadElapsedText.value = null
        showApkMirrorPrompt.value = false
        apkUsingMirrorSource.value = useMirrorSource

        apkDownloadJob = apkDownloadScope.launch {
            val downloadStartedAt = System.currentTimeMillis()
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val outFile = File(dir, "update-${update.info.versionCode}.apk")
            val partFile = File(dir, "${outFile.name}.part")
            val metaFile = File(dir, "${outFile.name}.meta")
            var mirrorPromptJob: Job? = null
            try {
                val downloadUrl = if (useMirrorSource) {
                    ApkUpdatePolicy.mirrorDownloadUrl(update.downloadUrl).getOrElse {
                        throw SecurityException("镜像下载地址无效")
                    }
                } else {
                    update.downloadUrl
                }

                if (!useMirrorSource) {
                    mirrorPromptJob = launch {
                        delay(MIRROR_PROMPT_DELAY_MS)
                        withContext(Dispatchers.Main.immediate) {
                            val progressForPrompt = apkProgress.value ?: 0f
                            if (apkDownloading.value &&
                                !apkUsingMirrorSource.value &&
                                !showApkMirrorPrompt.value &&
                                progressForPrompt < MIRROR_PROMPT_PROGRESS_THRESHOLD
                            ) {
                                showApkMirrorPrompt.value = true
                            }
                        }
                    }
                }

                Log.i(
                    "ApkUpdate",
                    "apk download start version=${update.info.versionCode}, " +
                        "url=$downloadUrl, mirror=$useMirrorSource, partExists=${partFile.exists()}, " +
                        "partBytes=${partFile.length()}, metaExists=${metaFile.exists()}"
                )
                val downloadedFile = try {
                    downloadApkWithAdaptiveRanges(
                        update = update,
                        downloadUrl = downloadUrl,
                        allowMirrorHost = useMirrorSource,
                        partFile = partFile,
                        metaFile = metaFile
                    )
                } catch (e: RangeUnsupportedException) {
                    Log.w("ApkUpdate", "range download unavailable, fallback to single stream: ${e.message}")
                    deletePartialDownload(partFile, metaFile)
                    downloadApkSingleStream(
                        update = update,
                        downloadUrl = downloadUrl,
                        allowMirrorHost = useMirrorSource,
                        partFile = partFile
                    )
                }

                replaceDownloadedApk(downloadedFile, outFile, update.sha256)
                metaFile.delete()
                Log.i(
                    "ApkUpdate",
                    "apk download verified version=${update.info.versionCode}, bytes=${outFile.length()}"
                )

                withContext(Dispatchers.Main) {
                    apkDownloading.value = false
                    apkProgress.value = null
                    apkActiveRangeCount.value = null
                    apkDownloadSegments.value = emptyList()
                    apkDownloadElapsedText.value = formatDownloadElapsed(System.currentTimeMillis() - downloadStartedAt)
                    showApkMirrorPrompt.value = false
                    apkUsingMirrorSource.value = false
                    apkLocalReady.value = true
                    if (installAfterApkDownload) {
                        downloadedApkFile.value = outFile
                    } else if (showDialogWhenApkDownloadCompletes) {
                        showDialogWhenApkDownloadCompletes = false
                        showApkUpdateDialog.value = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is SecurityException) {
                    deletePartialDownload(partFile, metaFile)
                }
                Log.w("ApkUpdate", "apk download failed", e)
                withContext(Dispatchers.Main) {
                    apkDownloading.value = false
                    apkProgress.value = null
                    apkActiveRangeCount.value = null
                    apkDownloadSegments.value = emptyList()
                    apkDownloadElapsedText.value = null
                    showApkMirrorPrompt.value = false
                    apkUsingMirrorSource.value = false
                    apkErrorText.value = e.message ?: "下载失败"
                    if (showDialogWhenApkDownloadCompletes) {
                        showDialogWhenApkDownloadCompletes = false
                        showApkUpdateDialog.value = true
                    }
                }
            } finally {
                mirrorPromptJob?.cancel()
            }
        }
    }

    fun keepPrimaryApkDownload() {
        showApkMirrorPrompt.value = false
    }

    fun switchApkDownloadToMirror(context: Context) {
        val update = selectedValidatedUpdate() ?: return
        showApkMirrorPrompt.value = false
        if (!apkDownloading.value || apkUsingMirrorSource.value) return

        val appContext = context.applicationContext
        apkDownloadScope.launch {
            apkDownloadJob?.cancelAndJoin()
            withContext(Dispatchers.Main) {
                if (apkLocalReady.value) {
                    apkDownloading.value = false
                    apkProgress.value = null
                    apkActiveRangeCount.value = null
                    apkDownloadSegments.value = emptyList()
                    apkUsingMirrorSource.value = false
                } else {
                    doApkDownload(appContext, update, useMirrorSource = true)
                }
            }
        }
    }

    private inner class AdaptiveRangeDownloadState(
        private val update: ApkUpdatePolicy.ValidatedUpdate,
        private val probe: DownloadProbe,
        private val metaFile: File,
        initialRanges: List<ApkSegmentDownloadPolicy.DownloadRange>
    ) {
        private val mutex = Mutex()
        private val pendingRanges = PriorityQueue(
            compareByDescending<ApkSegmentDownloadPolicy.DownloadRange> { it.remaining }
                .thenBy { it.start }
        )
        private val rangesById = linkedMapOf<String, ApkSegmentDownloadPolicy.DownloadRange>()
        private val activeStartedAt = mutableMapOf<String, Long>()
        private val splitRequested = mutableSetOf<String>()
        private val splitTrialsById = linkedMapOf<String, SplitTrial>()
        private val rollbackRequestedTrialIds = mutableSetOf<String>()
        private val rollbackRequestedRangeIds = mutableSetOf<String>()
        private val splitCooldownUntilByRangeKey = mutableMapOf<String, Long>()
        private val retryAttempts = mutableMapOf<String, Int>()
        private val retryNotBefore = mutableMapOf<String, Long>()
        private val rangeWindowBytes = mutableMapOf<String, Long>()

        private val minChunkBytes = ApkSegmentDownloadPolicy.MIN_CHUNK_BYTES
        private val maxConcurrency = ApkSegmentDownloadPolicy.MAX_CONCURRENCY
        private var targetConcurrency = ApkSegmentDownloadPolicy.INITIAL_CONCURRENCY
        private var nextRangeSequence = 0
        private var fatalError: Throwable? = null
        private var lastProgress = 0f
        private var lastProgressEmitAt = System.currentTimeMillis()
        private var lastMetaSavedAt = 0L
        private var bytesSinceMetaSave = 0L
        private var lastWindowAt = System.currentTimeMillis()
        private var lastWindowDownloaded = 0L
        private var ewmaSpeedBytesPerSec = 0.0
        private var lastWindowSpeedBytesPerSec = 0.0
        private var lastWindowErrors = 0
        private var completedRangesSinceWindow = 0
        private var lastWindowCompletedRanges = 0
        private var lowGainWindows = 0
        private var dropWindows = 0
        private var recentErrors = 0

        init {
            val now = System.currentTimeMillis()
            val normalized = ApkSegmentDownloadPolicy.normalizeForResume(initialRanges, now)
            normalized.forEach { range ->
                rangesById[range.id] = range
                if (!range.isComplete) {
                    pendingRanges.add(range)
                }
            }
            nextRangeSequence = (normalized.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: -1) + 1
            targetConcurrency = minOf(
                maxConcurrency,
                maxOf(1, minOf(ApkSegmentDownloadPolicy.INITIAL_CONCURRENCY, normalized.size))
            )
            lastProgress = ApkSegmentDownloadPolicy.progress(normalized, probe.totalBytes) ?: 0f
            lastWindowDownloaded = ApkSegmentDownloadPolicy.totalDownloaded(normalized)
            lastMetaSavedAt = now
        }

        suspend fun writeMetadataNow() {
            mutex.withLock {
                writeMetadataLocked()
            }
        }

        suspend fun emitSegmentsSnapshot() {
            val segments = mutex.withLock { buildDownloadSegmentsLocked() }
            emitApkDownloadSegments(segments)
        }

        suspend fun snapshotRanges(): List<ApkSegmentDownloadPolicy.DownloadRange> {
            return mutex.withLock {
                rangesById.values.sortedBy { it.start }
            }
        }

        suspend fun targetConcurrency(): Int {
            return mutex.withLock { targetConcurrency }
        }

        suspend fun desiredWorkerCount(): Int {
            return mutex.withLock { desiredWorkerCountLocked() }
        }

        fun totalBytes(): Long = probe.totalBytes

        suspend fun allowedWriteBytes(
            rangeId: String,
            absolutePosition: Long,
            requestedBytes: Int
        ): Int {
            if (requestedBytes <= 0) return 0
            return mutex.withLock {
                val range = rangesById[rangeId] ?: return@withLock 0
                if (range.status != ApkSegmentDownloadPolicy.Status.RUNNING || range.isComplete) {
                    return@withLock 0
                }
                val remaining = range.end - absolutePosition + 1L
                if (remaining <= 0L) {
                    0
                } else {
                    minOf(requestedBytes.toLong(), remaining).toInt()
                }
            }
        }

        suspend fun rangeIsComplete(rangeId: String): Boolean {
            return mutex.withLock {
                rangesById[rangeId]?.isComplete == true
            }
        }

        suspend fun hasWork(): Boolean {
            return mutex.withLock {
                rangesById.values.any { !it.isComplete } || activeStartedAt.isNotEmpty()
            }
        }

        suspend fun fatalOrNull(): Throwable? {
            return mutex.withLock { fatalError }
        }

        suspend fun isComplete(): Boolean {
            return mutex.withLock {
                fatalError == null &&
                    activeStartedAt.isEmpty() &&
                    ApkSegmentDownloadPolicy.pendingRanges(rangesById.values.toList()).isEmpty()
            }
        }

        suspend fun shouldEvaluateWindow(): Boolean {
            return mutex.withLock {
                System.currentTimeMillis() - lastWindowAt >= ADAPT_WINDOW_MS
            }
        }

        suspend fun takePendingRange(): ApkSegmentDownloadPolicy.DownloadRange? {
            var activeCountToEmit: Int? = null
            var segmentsToEmit: List<ApkDownloadSegment>? = null
            val rangeToRun = mutex.withLock {
                val now = System.currentTimeMillis()
                val deferred = mutableListOf<ApkSegmentDownloadPolicy.DownloadRange>()
                while (pendingRanges.isNotEmpty()) {
                    val candidate = pendingRanges.poll() ?: break
                    val latest = rangesById[candidate.id] ?: continue
                    if (latest.status != ApkSegmentDownloadPolicy.Status.PENDING || latest.isComplete) {
                        continue
                    }
                    val retryAt = retryNotBefore[latest.id] ?: 0L
                    if (retryAt > now) {
                        deferred.add(latest)
                        continue
                    }

                    val running = latest.copy(
                        status = ApkSegmentDownloadPolicy.Status.RUNNING,
                        updatedAt = now
                    )
                    rangesById[latest.id] = running
                    activeStartedAt[latest.id] = now
                    activeCountToEmit = activeStartedAt.size
                    segmentsToEmit = buildDownloadSegmentsLocked()
                    deferred.forEach { pendingRanges.add(it) }
                    return@withLock running
                }
                deferred.forEach { pendingRanges.add(it) }
                null
            }
            activeCountToEmit?.let { emitApkActiveRangeCount(it) }
            segmentsToEmit?.let { emitApkDownloadSegments(it) }
            return rangeToRun
        }

        suspend fun recordBytes(rangeId: String, bytesRead: Long) {
            if (bytesRead <= 0L) return

            var progressToEmit: Float? = null
            var segmentsToEmit: List<ApkDownloadSegment>? = null
            mutex.withLock {
                val range = rangesById[rangeId] ?: return@withLock
                if (range.status != ApkSegmentDownloadPolicy.Status.RUNNING) return@withLock

                val now = System.currentTimeMillis()
                val downloaded = (range.completedBytes + bytesRead).coerceAtMost(range.length)
                val updated = range.copy(
                    downloaded = downloaded,
                    status = if (downloaded >= range.length) {
                        ApkSegmentDownloadPolicy.Status.COMPLETED
                    } else {
                        ApkSegmentDownloadPolicy.Status.RUNNING
                    },
                    updatedAt = now
                )
                rangesById[rangeId] = updated
                rangeWindowBytes[rangeId] = (rangeWindowBytes[rangeId] ?: 0L) + bytesRead
                bytesSinceMetaSave += bytesRead

                val snapshot = rangesById.values.toList()
                val progress = ApkSegmentDownloadPolicy.progress(snapshot, probe.totalBytes) ?: 0f
                if (bytesSinceMetaSave >= META_MIN_DELTA_BYTES ||
                    now - lastMetaSavedAt >= PROGRESS_MIN_INTERVAL_MS ||
                    updated.isComplete
                ) {
                    writeMetadataLocked()
                }

                if (progress - lastProgress >= PROGRESS_MIN_DELTA ||
                    now - lastProgressEmitAt >= PROGRESS_MIN_INTERVAL_MS ||
                    progress >= 1f
                ) {
                    lastProgress = progress
                    lastProgressEmitAt = now
                    progressToEmit = progress
                    segmentsToEmit = buildDownloadSegmentsLocked()
                }
            }
            progressToEmit?.let { emitApkProgress(it) }
            segmentsToEmit?.let { emitApkDownloadSegments(it) }
        }

        suspend fun completeRange(rangeId: String) {
            var progressToEmit: Float? = null
            var activeCountToEmit: Int? = null
            var segmentsToEmit: List<ApkDownloadSegment>? = null
            mutex.withLock {
                val range = rangesById[rangeId] ?: return@withLock
                val now = System.currentTimeMillis()
                val completed = range.copy(
                    downloaded = range.length,
                    status = ApkSegmentDownloadPolicy.Status.COMPLETED,
                    updatedAt = now
                )
                rangesById[rangeId] = completed
                activeStartedAt.remove(rangeId)
                splitRequested.remove(rangeId)
                retryAttempts.remove(rangeId)
                retryNotBefore.remove(rangeId)
                rangeWindowBytes.remove(rangeId)
                rollbackRequestedRangeIds.remove(rangeId)
                completedRangesSinceWindow += 1
                activeCountToEmit = activeStartedAt.size
                segmentsToEmit = buildDownloadSegmentsLocked()
                writeMetadataLocked()

                val progress = ApkSegmentDownloadPolicy.progress(rangesById.values.toList(), probe.totalBytes) ?: 0f
                if (progress > lastProgress) {
                    lastProgress = progress
                    lastProgressEmitAt = now
                    progressToEmit = progress
                }
            }
            progressToEmit?.let { emitApkProgress(it) }
            activeCountToEmit?.let { emitApkActiveRangeCount(it) }
            segmentsToEmit?.let { emitApkDownloadSegments(it) }
        }

        suspend fun finishCancelledRange(rangeId: String) {
            var activeCountToEmit: Int? = null
            var segmentsToEmit: List<ApkDownloadSegment>? = null
            mutex.withLock {
                val range = rangesById[rangeId] ?: return@withLock
                val now = System.currentTimeMillis()
                activeStartedAt.remove(rangeId)
                rangeWindowBytes.remove(rangeId)
                activeCountToEmit = activeStartedAt.size
                if (range.isComplete) {
                    rangesById[rangeId] = range.copy(
                        downloaded = range.length,
                        status = ApkSegmentDownloadPolicy.Status.COMPLETED,
                        updatedAt = now
                    )
                    completedRangesSinceWindow += 1
                } else if (rangeId in splitRequested) {
                    rangesById[rangeId] = range.copy(
                        status = ApkSegmentDownloadPolicy.Status.CANCELLED,
                        updatedAt = now
                    )
                } else if (rangeId in rollbackRequestedRangeIds) {
                    rangesById[rangeId] = range.copy(
                        status = ApkSegmentDownloadPolicy.Status.CANCELLED,
                        updatedAt = now
                    )
                } else {
                    val pending = range.copy(
                        status = ApkSegmentDownloadPolicy.Status.PENDING,
                        updatedAt = now
                    )
                    rangesById[rangeId] = pending
                    pendingRanges.add(pending)
                }
                segmentsToEmit = buildDownloadSegmentsLocked()
                writeMetadataLocked()
            }
            activeCountToEmit?.let { emitApkActiveRangeCount(it) }
            segmentsToEmit?.let { emitApkDownloadSegments(it) }
        }

        suspend fun requeueFailedRange(rangeId: String, error: IOException): Boolean {
            var activeCountToEmit: Int? = null
            var segmentsToEmit: List<ApkDownloadSegment>? = null
            val shouldRetry = mutex.withLock {
                val range = rangesById[rangeId] ?: return@withLock false
                val now = System.currentTimeMillis()
                activeStartedAt.remove(rangeId)
                rangeWindowBytes.remove(rangeId)
                activeCountToEmit = activeStartedAt.size
                recentErrors += 1

                if (!isRetryableDownloadError(error)) {
                    rangesById[rangeId] = range.copy(
                        status = ApkSegmentDownloadPolicy.Status.FAILED,
                        updatedAt = now
                    )
                    fatalError = error
                    writeMetadataLocked()
                    return@withLock false
                }

                val attempts = (retryAttempts[rangeId] ?: 0) + 1
                retryAttempts[rangeId] = attempts
                if (attempts > MAX_RANGE_ATTEMPTS) {
                    rangesById[rangeId] = range.copy(
                        status = ApkSegmentDownloadPolicy.Status.FAILED,
                        updatedAt = now
                    )
                    fatalError = error
                    writeMetadataLocked()
                    return@withLock false
                }

                val pending = range.copy(
                    status = if (range.isComplete) {
                        ApkSegmentDownloadPolicy.Status.COMPLETED
                    } else {
                        ApkSegmentDownloadPolicy.Status.PENDING
                    },
                    updatedAt = now
                )
                rangesById[rangeId] = pending
                if (!pending.isComplete) {
                    retryNotBefore[rangeId] = now + retryDelayMillis(attempts)
                    pendingRanges.add(pending)
                }
                segmentsToEmit = buildDownloadSegmentsLocked()
                writeMetadataLocked()
                !pending.isComplete
            }
            activeCountToEmit?.let { emitApkActiveRangeCount(it) }
            segmentsToEmit?.let { emitApkDownloadSegments(it) }
            return shouldRetry
        }

        suspend fun setFatal(error: Throwable) {
            mutex.withLock {
                fatalError = error
            }
        }

        suspend fun chooseSplitCandidate(): SplitCandidate? {
            return mutex.withLock {
                val now = System.currentTimeMillis()
                splitCooldownUntilByRangeKey.entries.removeAll { it.value <= now }
                if (runningSplitTrialCountLocked() >= MAX_CONCURRENT_SPLIT_TRIALS ||
                    rollbackRequestedTrialIds.isNotEmpty() ||
                    dropWindows > 0 ||
                    recentErrors > 0
                ) {
                    return@withLock null
                }
                val hasEligiblePending = rangesById.values.any { range ->
                    range.status == ApkSegmentDownloadPolicy.Status.PENDING &&
                        !range.isComplete &&
                        (retryNotBefore[range.id] ?: 0L) <= now
                }
                if (hasEligiblePending || activeStartedAt.size >= desiredWorkerCountLocked()) {
                    return@withLock null
                }

                val splitTrialChildIds = runningSplitTrialChildIdsLocked()
                val running = activeStartedAt.keys.mapNotNull { rangesById[it] }
                val candidates = running.filter {
                    it.status == ApkSegmentDownloadPolicy.Status.RUNNING &&
                        it.completedBytes > 0L &&
                        it.lastSpeedBytesPerSec > 0L &&
                        it.id !in splitRequested &&
                        it.id !in rollbackRequestedRangeIds &&
                        it.id !in splitTrialChildIds &&
                        (now - (activeStartedAt[it.id] ?: now)) >= SPLIT_MIN_RUNNING_MS &&
                        (splitCooldownUntilByRangeKey[rangeCooldownKey(it)] ?: 0L) <= now &&
                        ApkSegmentDownloadPolicy.canSplit(it, minChunkBytes)
                }
                if (candidates.isEmpty()) return@withLock null

                val speeds = running.map { it.lastSpeedBytesPerSec }.filter { it > 0L }.sorted()
                val medianSpeed = if (speeds.isEmpty()) 0L else speeds[speeds.size / 2]

                val candidate = candidates.maxByOrNull { range ->
                    val speed = range.lastSpeedBytesPerSec.coerceAtLeast(1L)
                    range.remaining / speed
                } ?: return@withLock null

                splitRequested.add(candidate.id)
                val etaSeconds = candidate.remaining.toDouble() / candidate.lastSpeedBytesPerSec.coerceAtLeast(1L)
                val reason = "remaining=${candidate.remaining}, speed=${candidate.lastSpeedBytesPerSec}, " +
                    "median=$medianSpeed, eta=${String.format(Locale.US, "%.1fs", etaSeconds)}, " +
                    "trials=${runningSplitTrialCountLocked()}/$MAX_CONCURRENT_SPLIT_TRIALS"
                SplitCandidate(
                    rangeId = candidate.id,
                    reason = reason,
                    baselineEwmaBytesPerSec = ewmaSpeedBytesPerSec,
                    baselineWindowBytesPerSec = lastWindowSpeedBytesPerSec,
                    baselineTargetConcurrency = targetConcurrency
                )
            }
        }

        suspend fun rescueTailRange(): Boolean {
            var logMessage: String? = null
            var segmentsToEmit: List<ApkDownloadSegment>? = null
            val rescued = mutex.withLock {
                val now = System.currentTimeMillis()
                splitCooldownUntilByRangeKey.entries.removeAll { it.value <= now }

                if (rollbackRequestedTrialIds.isNotEmpty() || recentErrors > 0) {
                    return@withLock false
                }
                val splitTrialChildIds = runningSplitTrialChildIdsLocked()

                val progress = ApkSegmentDownloadPolicy.progress(rangesById.values.toList(), probe.totalBytes) ?: 0f
                val minWorkers = minOf(MIN_ACTIVE_RANGE_WORKERS, maxConcurrency)
                if (progress < TAIL_RESCUE_PROGRESS_THRESHOLD && activeStartedAt.size >= minWorkers) {
                    return@withLock false
                }
                if (activeStartedAt.size >= desiredWorkerCountLocked()) {
                    return@withLock false
                }
                val tailMinChunkBytes = tailRescueMinChunkBytesLocked(progress)

                val hasEligiblePending = rangesById.values.any { range ->
                    range.status == ApkSegmentDownloadPolicy.Status.PENDING &&
                        !range.isComplete &&
                        (retryNotBefore[range.id] ?: 0L) <= now
                }
                if (hasEligiblePending) return@withLock false

                val running = activeStartedAt.keys.mapNotNull { rangesById[it] }
                val candidates = running.filter { range ->
                    range.status == ApkSegmentDownloadPolicy.Status.RUNNING &&
                        range.completedBytes > 0L &&
                        range.lastSpeedBytesPerSec > 0L &&
                        range.id !in splitRequested &&
                        range.id !in rollbackRequestedRangeIds &&
                        range.id !in splitTrialChildIds &&
                        (now - (activeStartedAt[range.id] ?: now)) >= SPLIT_MIN_RUNNING_MS &&
                        (splitCooldownUntilByRangeKey[rangeCooldownKey(range)] ?: 0L) <= now &&
                        ApkSegmentDownloadPolicy.canStealTail(
                            range = range,
                            minChunkBytes = tailMinChunkBytes
                        )
                }
                if (candidates.isEmpty()) return@withLock false

                val speeds = running.map { it.lastSpeedBytesPerSec }.filter { it > 0L }.sorted()
                val medianSpeed = if (speeds.isEmpty()) 0L else speeds[speeds.size / 2]
                val candidate = candidates.maxByOrNull { range ->
                    val speed = range.lastSpeedBytesPerSec.coerceAtLeast(1L)
                    range.remaining / speed
                } ?: return@withLock false

                val helperId = nextRangeId()
                val (parent, helper) = ApkSegmentDownloadPolicy.stealTailRange(
                    range = candidate,
                    helperId = helperId,
                    minChunkBytes = tailMinChunkBytes,
                    now = now
                ) ?: return@withLock false

                rangesById[parent.id] = parent
                rangesById[helper.id] = helper
                pendingRanges.add(helper)
                segmentsToEmit = buildDownloadSegmentsLocked()
                writeMetadataLocked()

                val etaSeconds = candidate.remaining.toDouble() / candidate.lastSpeedBytesPerSec.coerceAtLeast(1L)
                logMessage = "tail rescue parent=${candidate.id}, helper=${helper.id}, " +
                    "parent=${parent.start}-${parent.end}, tail=${helper.start}-${helper.end}, " +
                    "progress=${progressPercent(progress)}, active=${activeStartedAt.size}, " +
                    "target=$targetConcurrency, desired=${desiredWorkerCountLocked()}, " +
                    "tailMin=$tailMinChunkBytes, speed=${candidate.lastSpeedBytesPerSec}, median=$medianSpeed, " +
                    "eta=${String.format(Locale.US, "%.1fs", etaSeconds)}"
                true
            }
            segmentsToEmit?.let { emitApkDownloadSegments(it) }
            logMessage?.let { Log.i("ApkUpdate", it) }
            return rescued
        }

        suspend fun splitCancelledRange(candidate: SplitCandidate) {
            var logMessage: String? = null
            var activeCountToEmit: Int? = null
            var segmentsToEmit: List<ApkDownloadSegment>? = null
            mutex.withLock {
                val rangeId = candidate.rangeId
                val range = rangesById[rangeId] ?: return@withLock
                val now = System.currentTimeMillis()
                activeStartedAt.remove(rangeId)
                rangeWindowBytes.remove(rangeId)
                retryAttempts.remove(rangeId)
                retryNotBefore.remove(rangeId)
                splitRequested.remove(rangeId)
                activeCountToEmit = activeStartedAt.size

                if (!ApkSegmentDownloadPolicy.canSplit(range, minChunkBytes)) {
                    if (!range.isComplete) {
                        val pending = range.copy(
                            status = ApkSegmentDownloadPolicy.Status.PENDING,
                            updatedAt = now
                        )
                        rangesById[rangeId] = pending
                        pendingRanges.add(pending)
                    }
                    writeMetadataLocked()
                    return@withLock
                }

                rangesById.remove(rangeId)
                val firstId = nextRangeId()
                val secondId = nextRangeId()
                val split = ApkSegmentDownloadPolicy.splitRemainingRange(
                    range = range.copy(status = ApkSegmentDownloadPolicy.Status.CANCELLED),
                    firstPendingId = firstId,
                    secondPendingId = secondId,
                    now = now
                )
                split.forEach { newRange ->
                    val normalized = if (newRange.isComplete) {
                        newRange.copy(status = ApkSegmentDownloadPolicy.Status.COMPLETED)
                    } else {
                        newRange.copy(status = ApkSegmentDownloadPolicy.Status.PENDING)
                    }
                    rangesById[normalized.id] = normalized
                    if (!normalized.isComplete) {
                        pendingRanges.add(normalized)
                    }
                }
                val childRangeIds = listOf(firstId, secondId)
                val trialId = "split-${now}-$rangeId"
                splitTrialsById[trialId] = SplitTrial(
                    id = trialId,
                    parentRangeId = rangeId,
                    childRangeIds = childRangeIds,
                    baselineEwmaBytesPerSec = candidate.baselineEwmaBytesPerSec,
                    baselineWindowBytesPerSec = candidate.baselineWindowBytesPerSec,
                    baselineTargetConcurrency = candidate.baselineTargetConcurrency,
                    startedAt = now
                )
                segmentsToEmit = buildDownloadSegmentsLocked()
                writeMetadataLocked()
                logMessage = "split trial start id=$trialId, parent=$rangeId, " +
                    "baselineEwma=${speedText(candidate.baselineEwmaBytesPerSec.toLong(), 1000L)}, " +
                    "baselineWindow=${speedText(candidate.baselineWindowBytesPerSec.toLong(), 1000L)}, " +
                    "target=${candidate.baselineTargetConcurrency}, " +
                    "child=${childRangeIds.joinToString()}, " +
                    "ranges=${split.joinToString { "${it.id}:${it.start}-${it.end}" }}"
            }
            activeCountToEmit?.let { emitApkActiveRangeCount(it) }
            segmentsToEmit?.let { emitApkDownloadSegments(it) }
            logMessage?.let { Log.i("ApkUpdate", it) }
        }

        suspend fun prepareRollbackTrial(): SplitRollbackRequest? {
            var logMessage: String? = null
            val request = mutex.withLock {
                val now = System.currentTimeMillis()
                val trial = splitTrialsById.values.firstOrNull {
                    it.status == SplitTrialStatus.RUNNING &&
                        it.id !in rollbackRequestedTrialIds &&
                        now - it.startedAt >= it.evaluateAfterWindows * ADAPT_WINDOW_MS
                } ?: return@withLock null

                val childRanges = trial.childRangeIds.mapNotNull { rangesById[it] }
                if (childRanges.isEmpty()) {
                    logMessage = acceptSplitTrialLocked(trial, now, "children missing")
                    return@withLock null
                }

                val remainingBytes = childRanges.sumOf { it.remaining }
                val elapsed = now - trial.startedAt
                if (childRanges.all { it.isComplete } || remainingBytes <= minChunkBytes / 4L) {
                    logMessage = acceptSplitTrialLocked(trial, now, "children nearly complete")
                    return@withLock null
                }

                val baselineEwma = trial.baselineEwmaBytesPerSec
                val baselineWindow = trial.baselineWindowBytesPerSec
                val hasBaseline = baselineEwma > 0.0 && baselineWindow > 0.0
                val ewmaDrop = if (baselineEwma > 0.0) {
                    (ewmaSpeedBytesPerSec - baselineEwma) / baselineEwma
                } else {
                    0.0
                }
                val windowDrop = if (baselineWindow > 0.0) {
                    (lastWindowSpeedBytesPerSec - baselineWindow) / baselineWindow
                } else {
                    0.0
                }
                val noNaturalCompletionInterference = lastWindowErrors == 0 && lastWindowCompletedRanges == 0
                val shouldRollback = hasBaseline &&
                    noNaturalCompletionInterference &&
                    ewmaSpeedBytesPerSec < baselineEwma * SPLIT_ROLLBACK_EWMA_RATIO &&
                    lastWindowSpeedBytesPerSec < baselineWindow * SPLIT_ROLLBACK_WINDOW_RATIO

                if (shouldRollback) {
                    rollbackRequestedTrialIds += trial.id
                    rollbackRequestedRangeIds += childRanges.filterNot { it.isComplete }.map { it.id }
                    return@withLock SplitRollbackRequest(
                        trialId = trial.id,
                        childRangeIds = childRanges.map { it.id },
                        reason = "ewmaDrop=${percentText(ewmaDrop)}, windowDrop=${percentText(windowDrop)}"
                    )
                }

                val ewmaAccepted = hasBaseline &&
                    ewmaSpeedBytesPerSec >= baselineEwma * SPLIT_ACCEPT_EWMA_RATIO
                if (ewmaAccepted) {
                    logMessage = acceptSplitTrialLocked(
                        trial = trial,
                        now = now,
                        reason = "ewma=${percentText(ewmaDrop)}"
                    )
                } else if (elapsed >= SPLIT_TRIAL_MAX_MS) {
                    logMessage = acceptSplitTrialLocked(
                        trial = trial,
                        now = now,
                        reason = "timeout no negative gain, ewma=${percentText(ewmaDrop)}"
                    )
                }
                null
            }
            logMessage?.let { Log.i("ApkUpdate", it) }
            return request
        }

        suspend fun rollbackSplitTrial(trialId: String) {
            var logMessage: String? = null
            var cooldownLogMessage: String? = null
            var activeCountToEmit: Int? = null
            var segmentsToEmit: List<ApkDownloadSegment>? = null
            mutex.withLock {
                val trial = splitTrialsById[trialId] ?: return@withLock
                val now = System.currentTimeMillis()
                val childRanges = trial.childRangeIds.mapNotNull { rangesById[it] }.sortedBy { it.start }
                if (childRanges.isEmpty()) {
                    splitTrialsById[trialId] = trial.copy(status = SplitTrialStatus.ROLLED_BACK)
                    rollbackRequestedTrialIds.remove(trialId)
                    writeMetadataLocked()
                    return@withLock
                }

                val keptBytes = childRanges.sumOf { it.completedBytes }
                val replacementRanges = ApkSegmentDownloadPolicy.rollbackSplitChildren(
                    childRanges = childRanges,
                    nextPendingId = { nextRangeId() },
                    now = now
                )

                childRanges.forEach { range ->
                    rangesById.remove(range.id)
                    activeStartedAt.remove(range.id)
                    splitRequested.remove(range.id)
                    rollbackRequestedRangeIds.remove(range.id)
                    retryAttempts.remove(range.id)
                    retryNotBefore.remove(range.id)
                    rangeWindowBytes.remove(range.id)
                }

                replacementRanges.forEach { range ->
                    rangesById[range.id] = range
                    if (range.status == ApkSegmentDownloadPolicy.Status.PENDING && !range.isComplete) {
                        pendingRanges.add(range)
                    }
                }

                val cooldownUntil = now + SPLIT_COOLDOWN_MS
                val rollbackKey = rangeCooldownKey(childRanges.first().start, childRanges.last().end)
                splitCooldownUntilByRangeKey[rollbackKey] = cooldownUntil
                replacementRanges
                    .filter { it.status == ApkSegmentDownloadPolicy.Status.PENDING && !it.isComplete }
                    .forEach { splitCooldownUntilByRangeKey[rangeCooldownKey(it)] = cooldownUntil }

                splitTrialsById[trialId] = trial.copy(status = SplitTrialStatus.ROLLED_BACK)
                rollbackRequestedTrialIds.remove(trialId)
                activeCountToEmit = activeStartedAt.size
                segmentsToEmit = buildDownloadSegmentsLocked()
                writeMetadataLocked()

                val mergedPending = replacementRanges.count {
                    it.status == ApkSegmentDownloadPolicy.Status.PENDING && !it.isComplete
                }
                val baselineEwma = trial.baselineEwmaBytesPerSec
                val ewmaDrop = if (baselineEwma > 0.0) {
                    (ewmaSpeedBytesPerSec - baselineEwma) / baselineEwma
                } else {
                    0.0
                }
                logMessage = "split trial rollback id=$trialId, ewmaDrop=${percentText(ewmaDrop)}, " +
                    "keptBytes=$keptBytes, mergedPending=$mergedPending"
                cooldownLogMessage = "split cooldown parent=${trial.parentRangeId}, key=$rollbackKey, until=$cooldownUntil"
            }
            activeCountToEmit?.let { emitApkActiveRangeCount(it) }
            segmentsToEmit?.let { emitApkDownloadSegments(it) }
            logMessage?.let { Log.i("ApkUpdate", it) }
            cooldownLogMessage?.let { Log.i("ApkUpdate", it) }
        }

        suspend fun evaluateWindow() {
            var logMessage = ""
            mutex.withLock {
                val now = System.currentTimeMillis()
                val elapsed = now - lastWindowAt
                if (elapsed < ADAPT_WINDOW_MS) return@withLock

                activeStartedAt.keys.forEach { id ->
                    val range = rangesById[id] ?: return@forEach
                    val bytes = rangeWindowBytes[id] ?: 0L
                    val speed = if (elapsed > 0L) bytes * 1000L / elapsed else 0L
                    rangesById[id] = range.copy(
                        lastSpeedBytesPerSec = speed,
                        updatedAt = now
                    )
                    rangeWindowBytes[id] = 0L
                }

                val totalDownloaded = ApkSegmentDownloadPolicy.totalDownloaded(rangesById.values.toList())
                val windowBytes = (totalDownloaded - lastWindowDownloaded).coerceAtLeast(0L)
                val currentSpeed = if (elapsed > 0L) windowBytes * 1000.0 / elapsed else 0.0
                lastWindowSpeedBytesPerSec = currentSpeed
                lastWindowErrors = recentErrors
                lastWindowCompletedRanges = completedRangesSinceWindow
                val previousEwma = ewmaSpeedBytesPerSec
                ewmaSpeedBytesPerSec = if (previousEwma <= 0.0) {
                    currentSpeed
                } else {
                    previousEwma * 0.7 + currentSpeed * 0.3
                }
                val gain = if (previousEwma > 0.0) {
                    (ewmaSpeedBytesPerSec - previousEwma) / previousEwma
                } else {
                    null
                }

                var decision = "keep"
                if (recentErrors >= 2 && targetConcurrency > 1) {
                    targetConcurrency -= 1
                    lowGainWindows = 0
                    dropWindows = 0
                    decision = "decrease after errors=$recentErrors"
                } else if (gain != null && gain <= CONCURRENCY_DROP_THRESHOLD) {
                    dropWindows += 1
                    if (dropWindows >= 2 && targetConcurrency > 1) {
                        targetConcurrency -= 1
                        lowGainWindows = 0
                        dropWindows = 0
                        decision = "decrease after speed drop ${percentText(gain)}"
                    }
                } else {
                    dropWindows = 0
                    if (hasMoreWorkLocked() && targetConcurrency < maxConcurrency) {
                        when {
                            gain == null -> {
                                targetConcurrency += 1
                                decision = "increase warmup"
                            }
                            gain >= CONCURRENCY_GAIN_THRESHOLD -> {
                                targetConcurrency += 1
                                lowGainWindows = 0
                                decision = "increase gain=${percentText(gain)}"
                            }
                            gain < CONCURRENCY_LOW_GAIN_THRESHOLD -> {
                                lowGainWindows += 1
                                if (lowGainWindows < 2) {
                                    targetConcurrency += 1
                                    decision = "probe gain=${percentText(gain)}"
                                } else {
                                    decision = "stop growth gain=${percentText(gain)}"
                                }
                            }
                            else -> {
                                targetConcurrency += 1
                                lowGainWindows = 0
                                decision = "increase modest gain=${percentText(gain)}"
                            }
                        }
                    }
                }

                val progress = ApkSegmentDownloadPolicy.progress(rangesById.values.toList(), probe.totalBytes) ?: 0f
                val pendingCount = rangesById.values.count {
                    it.status == ApkSegmentDownloadPolicy.Status.PENDING && !it.isComplete
                }
                logMessage = "adaptive window progress=${progressPercent(progress)}, " +
                    "speed=${speedText(windowBytes, elapsed)}, ewma=${speedText(ewmaSpeedBytesPerSec.toLong(), 1000L)}, " +
                    "target=$targetConcurrency, active=${activeStartedAt.size}, pending=$pendingCount, " +
                    "errors=$recentErrors, decision=$decision"

                recentErrors = 0
                completedRangesSinceWindow = 0
                lastWindowDownloaded = totalDownloaded
                lastWindowAt = now
                writeMetadataLocked()
            }
            Log.i("ApkUpdate", logMessage)
        }

        private fun hasMoreWorkLocked(): Boolean {
            val hasPending = rangesById.values.any {
                it.status == ApkSegmentDownloadPolicy.Status.PENDING && !it.isComplete
            }
            val canSplitActive = activeStartedAt.keys.any { id ->
                rangesById[id]?.let { ApkSegmentDownloadPolicy.canSplit(it, minChunkBytes) } == true
            }
            val progress = ApkSegmentDownloadPolicy.progress(rangesById.values.toList(), probe.totalBytes) ?: 0f
            val tailMinChunkBytes = tailRescueMinChunkBytesLocked(progress)
            val canTailRescueActive = activeStartedAt.keys.any { id ->
                rangesById[id]?.let {
                    ApkSegmentDownloadPolicy.canStealTail(
                        range = it,
                        minChunkBytes = tailMinChunkBytes
                    )
                } == true
            }
            return hasPending || canSplitActive || canTailRescueActive
        }

        private fun tailRescueMinChunkBytesLocked(progress: Float): Long {
            return if (progress >= TAIL_RESCUE_LATE_PROGRESS_THRESHOLD) {
                ApkSegmentDownloadPolicy.LATE_TAIL_RESCUE_MIN_CHUNK_BYTES
            } else {
                ApkSegmentDownloadPolicy.TAIL_RESCUE_MIN_CHUNK_BYTES
            }
        }

        private fun desiredWorkerCountLocked(): Int {
            if (rangesById.values.none { !it.isComplete }) return targetConcurrency

            val minWorkers = minOf(MIN_ACTIVE_RANGE_WORKERS, maxConcurrency)
            return maxOf(targetConcurrency, minWorkers)
        }

        private fun hasRunningSplitTrialLocked(): Boolean {
            return splitTrialsById.values.any { it.status == SplitTrialStatus.RUNNING }
        }

        private fun runningSplitTrialCountLocked(): Int {
            return splitTrialsById.values.count { it.status == SplitTrialStatus.RUNNING }
        }

        private fun runningSplitTrialChildIdsLocked(): Set<String> {
            return splitTrialsById.values
                .filter { it.status == SplitTrialStatus.RUNNING }
                .flatMap { it.childRangeIds }
                .toSet()
        }

        private fun buildDownloadSegmentsLocked(): List<ApkDownloadSegment> {
            val totalBytes = probe.totalBytes.takeIf { it > 0L } ?: return emptyList()
            return rangesById.values.sortedBy { it.start }.mapNotNull { range ->
                val running = range.status == ApkSegmentDownloadPolicy.Status.RUNNING
                val downloadedEnd = range.start + range.completedBytes
                if (!running && downloadedEnd <= range.start) {
                    return@mapNotNull null
                }
                ApkDownloadSegment(
                    startFraction = (range.start.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat(),
                    endFraction = ((range.end + 1L).toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat(),
                    downloadedEndFraction = (downloadedEnd.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat(),
                    running = running
                )
            }
        }

        private fun acceptSplitTrialLocked(
            trial: SplitTrial,
            now: Long,
            reason: String
        ): String {
            splitTrialsById[trial.id] = trial.copy(status = SplitTrialStatus.ACCEPTED)
            rollbackRequestedTrialIds.remove(trial.id)
            trial.childRangeIds.forEach { rollbackRequestedRangeIds.remove(it) }
            val baselineEwma = trial.baselineEwmaBytesPerSec
            val gain = if (baselineEwma > 0.0) {
                (ewmaSpeedBytesPerSec - baselineEwma) / baselineEwma
            } else {
                0.0
            }
            return "split trial accept id=${trial.id}, gain=${percentText(gain)}, " +
                "elapsedMs=${now - trial.startedAt}, reason=$reason"
        }

        private fun rangeCooldownKey(range: ApkSegmentDownloadPolicy.DownloadRange): String {
            return rangeCooldownKey(range.start, range.end)
        }

        private fun rangeCooldownKey(start: Long, end: Long): String {
            return "$start-$end"
        }

        private fun nextRangeId(): String {
            return (nextRangeSequence++).toString()
        }

        private fun writeMetadataLocked() {
            compactRangesLocked()
            writeAdaptiveMetadata(
                metaFile = metaFile,
                update = update,
                probe = probe,
                ranges = rangesById.values.sortedBy { it.start }
            )
            lastMetaSavedAt = System.currentTimeMillis()
            bytesSinceMetaSave = 0L
        }

        private fun compactRangesLocked() {
            if (hasRunningSplitTrialLocked() || rollbackRequestedTrialIds.isNotEmpty()) return

            val now = System.currentTimeMillis()
            splitCooldownUntilByRangeKey.entries.removeAll { it.value <= now }
            val before = rangesById.values.sortedBy { it.start }
            val compacted = ApkSegmentDownloadPolicy.compactRanges(
                ranges = before,
                now = now,
                canMergeCompleted = { left, right -> canMergeCompletedRangesLocked(left, right) },
                canMergePending = { left, right -> canMergePendingRangesLocked(left, right, now) }
            )
            if (compacted.size == before.size) return

            val keptIds = compacted.map { it.id }.toSet()
            rangesById.clear()
            compacted.forEach { rangesById[it.id] = it }
            retryAttempts.keys.removeAll { it !in keptIds }
            retryNotBefore.keys.removeAll { it !in keptIds }
            rangeWindowBytes.keys.removeAll { it !in keptIds }
            splitRequested.removeAll { it !in keptIds }
            rollbackRequestedRangeIds.removeAll { it !in keptIds }
            rebuildPendingQueueLocked()
            Log.i("ApkUpdate", "metadata ranges compacted ${before.size}->${compacted.size}")
        }

        private fun canMergeCompletedRangesLocked(
            left: ApkSegmentDownloadPolicy.DownloadRange,
            right: ApkSegmentDownloadPolicy.DownloadRange
        ): Boolean {
            return left.id !in activeStartedAt &&
                right.id !in activeStartedAt &&
                left.id !in splitRequested &&
                right.id !in splitRequested &&
                left.id !in rollbackRequestedRangeIds &&
                right.id !in rollbackRequestedRangeIds
        }

        private fun canMergePendingRangesLocked(
            left: ApkSegmentDownloadPolicy.DownloadRange,
            right: ApkSegmentDownloadPolicy.DownloadRange,
            now: Long
        ): Boolean {
            if (left.generation != right.generation) return false
            if (left.generation <= 0) return false
            if (retryAttempts.containsKey(left.id) || retryAttempts.containsKey(right.id)) return false
            if (retryNotBefore.containsKey(left.id) || retryNotBefore.containsKey(right.id)) return false

            val leftCooldown = splitCooldownUntilByRangeKey[rangeCooldownKey(left)] ?: 0L
            val rightCooldown = splitCooldownUntilByRangeKey[rangeCooldownKey(right)] ?: 0L
            val mergedCooldown = splitCooldownUntilByRangeKey[rangeCooldownKey(left.start, right.end)] ?: 0L
            return leftCooldown <= now && rightCooldown <= now && mergedCooldown <= now
        }

        private fun rebuildPendingQueueLocked() {
            pendingRanges.clear()
            rangesById.values.forEach { range ->
                if (range.status == ApkSegmentDownloadPolicy.Status.PENDING && !range.isComplete) {
                    pendingRanges.add(range)
                }
            }
        }
    }

    private suspend fun downloadApkWithAdaptiveRanges(
        update: ApkUpdatePolicy.ValidatedUpdate,
        downloadUrl: String,
        allowMirrorHost: Boolean,
        partFile: File,
        metaFile: File
    ): File {
        var probe = probeApkDownload(downloadUrl, allowMirrorHost)
        val rangeTotalBytes = ensureRangeDownloadSupported(probe, allowMirrorHost)
        if (probe.totalBytes <= 0L) {
            probe = probe.copy(totalBytes = rangeTotalBytes)
        }
        if (probe.totalBytes <= 0L) {
            throw RangeUnsupportedException("无法获取安装包大小")
        }
        if (rangeTotalBytes > 0L && rangeTotalBytes != probe.totalBytes) {
            throw RangeUnsupportedException("Range 总大小不一致")
        }
        if (probe.totalBytes > ApkUpdatePolicy.MAX_APK_BYTES) {
            throw IOException("安装包过大，请稍后重试")
        }

        val now = System.currentTimeMillis()
        val storedMetadata = loadSegmentMetadata(metaFile)
        val canResume = storedMetadata != null &&
            partFile.exists() &&
            partFile.length() == probe.totalBytes &&
            runCatching {
                ApkSegmentDownloadPolicy.isResumeCompatible(
                    metadata = storedMetadata,
                    versionCode = update.info.versionCode,
                    url = probe.finalUrl,
                    sha256 = update.sha256,
                    totalBytes = probe.totalBytes,
                    etag = probe.etag,
                    lastModified = probe.lastModified
                )
            }.getOrDefault(false)
        if (storedMetadata != null && !canResume) {
            Log.i(
                "ApkUpdate",
                "ignore apk range metadata version=${update.info.versionCode}, " +
                    "partExists=${partFile.exists()}, partBytes=${partFile.length()}, " +
                    "remoteBytes=${probe.totalBytes}, storedUrl=${storedMetadata.url}, remoteUrl=${probe.finalUrl}"
            )
        }

        val initialRanges = if (storedMetadata != null && canResume) {
            ApkSegmentDownloadPolicy.normalizeForResume(storedMetadata.ranges, now)
        } else {
            deletePartialDownload(partFile, metaFile)
            ApkSegmentDownloadPolicy.createInitialRanges(probe.totalBytes, now = now)
        }
        if (initialRanges.isEmpty()) {
            throw RangeUnsupportedException("无法创建下载区间")
        }

        val initialProgress = ApkSegmentDownloadPolicy.progress(initialRanges, probe.totalBytes) ?: 0f
        Log.i(
            "ApkUpdate",
            "adaptive range download prepared version=${update.info.versionCode}, " +
                "url=${probe.finalUrl}, totalBytes=${probe.totalBytes}, ranges=${initialRanges.size}, " +
                "resumed=$canResume, minChunk=${ApkSegmentDownloadPolicy.MIN_CHUNK_BYTES}, " +
                "maxConcurrency=${ApkSegmentDownloadPolicy.MAX_CONCURRENCY}, " +
                "progress=${progressPercent(initialProgress)}"
        )

        RandomAccessFile(partFile, "rw").use { file ->
            file.setLength(probe.totalBytes)
        }

        val state = AdaptiveRangeDownloadState(
            update = update,
            probe = probe,
            metaFile = metaFile,
            initialRanges = initialRanges
        )
        state.writeMetadataNow()
        emitApkActiveRangeCount(0)
        state.emitSegmentsSnapshot()
        emitApkProgress(initialProgress)

        val startedAt = System.currentTimeMillis()
        runAdaptiveRangeDownload(
            finalUrl = probe.finalUrl,
            allowMirrorHost = allowMirrorHost,
            partFile = partFile,
            ifRangeHeader = probe.etag ?: probe.lastModified,
            state = state
        )

        val finalRanges = state.snapshotRanges()
        writeAdaptiveMetadata(
            metaFile = metaFile,
            update = update,
            probe = probe,
            ranges = finalRanges
        )
        if (!ApkSegmentDownloadPolicy.rangesCoverFile(finalRanges, probe.totalBytes) ||
            ApkSegmentDownloadPolicy.pendingRanges(finalRanges).isNotEmpty()
        ) {
            throw IOException("下载不完整，请重试")
        }
        if (partFile.length() != probe.totalBytes) {
            throw IOException("下载文件大小异常（${partFile.length()}/${probe.totalBytes}）")
        }

        val hash = runCatching { sha256Of(partFile) }.getOrNull()
        if (!hash.equals(update.sha256, ignoreCase = true)) {
            Log.w("ApkUpdate", "download sha256 mismatch: expected=${update.sha256}, got=$hash")
            deletePartialDownload(partFile, metaFile)
            throw SecurityException("文件校验失败，请重试")
        }

        val elapsed = System.currentTimeMillis() - startedAt
        Log.i(
            "ApkUpdate",
            "adaptive range download complete bytes=${probe.totalBytes}, elapsedMs=$elapsed, " +
                "avg=${speedText(probe.totalBytes, elapsed)}, sha256=$hash"
        )
        return partFile
    }

    private suspend fun runAdaptiveRangeDownload(
        finalUrl: String,
        allowMirrorHost: Boolean,
        partFile: File,
        ifRangeHeader: String?,
        state: AdaptiveRangeDownloadState
    ) = supervisorScope {
        val activeJobs = linkedMapOf<String, Job>()
        val startRangeJob: (ApkSegmentDownloadPolicy.DownloadRange) -> Job = { range ->
            launch {
                try {
                    downloadAdaptiveRange(
                        finalUrl = finalUrl,
                        allowMirrorHost = allowMirrorHost,
                        partFile = partFile,
                        range = range,
                        ifRangeHeader = ifRangeHeader,
                        state = state
                    )
                    state.completeRange(range.id)
                } catch (e: CancellationException) {
                    state.finishCancelledRange(range.id)
                    throw e
                } catch (e: RangeUnsupportedException) {
                    state.setFatal(e)
                } catch (e: SecurityException) {
                    state.setFatal(e)
                } catch (e: IOException) {
                    val retrying = state.requeueFailedRange(range.id, e)
                    Log.w(
                        "ApkUpdate",
                        "range ${range.id} failed: ${e.javaClass.simpleName}: ${e.message}, retry=$retrying",
                        e
                    )
                } catch (e: Exception) {
                    state.setFatal(e)
                }
            }
        }

        try {
            while (isActive) {
                activeJobs.entries.removeAll { !it.value.isActive }
                state.fatalOrNull()?.let { throw it }
                if (state.isComplete()) break

                if (state.shouldEvaluateWindow()) {
                    state.evaluateWindow()
                }

                var desiredWorkerCount = state.desiredWorkerCount()
                while (activeJobs.size < desiredWorkerCount) {
                    val range = state.takePendingRange() ?: break
                    activeJobs[range.id] = startRangeJob(range)
                }

                val rollbackRequest = state.prepareRollbackTrial()
                if (rollbackRequest != null) {
                    Log.i(
                        "ApkUpdate",
                        "split trial rollback request id=${rollbackRequest.trialId}, " +
                            "child=${rollbackRequest.childRangeIds.joinToString()}, reason=${rollbackRequest.reason}"
                    )
                    rollbackRequest.childRangeIds.forEach { childRangeId ->
                        activeJobs[childRangeId]?.cancelAndJoin()
                        activeJobs.remove(childRangeId)
                    }
                    state.rollbackSplitTrial(rollbackRequest.trialId)
                    continue
                }

                desiredWorkerCount = state.desiredWorkerCount()
                if (activeJobs.size < desiredWorkerCount) {
                    if (state.rescueTailRange()) {
                        continue
                    }

                    val splitCandidate = state.chooseSplitCandidate()
                    if (splitCandidate != null) {
                        Log.i(
                            "ApkUpdate",
                            "adaptive split request range=${splitCandidate.rangeId}, reason=${splitCandidate.reason}"
                        )
                        activeJobs[splitCandidate.rangeId]?.cancelAndJoin()
                        activeJobs.remove(splitCandidate.rangeId)
                        state.splitCancelledRange(splitCandidate)
                        continue
                    }
                }

                if (!state.hasWork()) {
                    break
                }
                delay(200L)
            }
            state.fatalOrNull()?.let { throw it }
        } finally {
            activeJobs.values.forEach { job ->
                if (job.isActive) {
                    job.cancelAndJoin()
                }
            }
        }
    }

    private suspend fun downloadAdaptiveRange(
        finalUrl: String,
        allowMirrorHost: Boolean,
        partFile: File,
        range: ApkSegmentDownloadPolicy.DownloadRange,
        ifRangeHeader: String?,
        state: AdaptiveRangeDownloadState
    ) {
        val start = range.nextStart
        if (start > range.end) return

        val expectedBytes = range.end - start + 1L
        val rangeHeader = "bytes=$start-${range.end}"
        var written = 0L
        val startedAt = System.currentTimeMillis()
        var lastLogAt = startedAt
        var lastLoggedBytes = 0L

        Log.i(
            "ApkUpdate",
            "range ${range.id} start $rangeHeader, resume=${range.completedBytes}/${range.length}, " +
                "expectedBytes=$expectedBytes, ifRange=${ifRangeHeader != null}"
        )

        try {
            val response = openApkDownloadResponse(finalUrl, rangeHeader, ifRangeHeader, allowMirrorHost)
            val code = response.code()
            if (code != HTTP_PARTIAL_CONTENT) {
                closeDownloadResponse(response)
                if (code == 429 || code == 503 || code in 500..599) {
                    throw IOException("分片请求失败：HTTP $code")
                }
                throw RangeUnsupportedException("分片请求未返回 206")
            }

            val body = response.body() ?: run {
                closeDownloadResponse(response)
                throw IOException("分片下载内容为空")
            }
            val contentRange = parseContentRange(response.headers()["Content-Range"])
            Log.i(
                "ApkUpdate",
                "range ${range.id} response code=$code, length=${body.contentLength()}, " +
                    "contentRange=${response.headers()["Content-Range"]}"
            )
            if (contentRange == null ||
                contentRange.start != start ||
                contentRange.end != range.end ||
                contentRange.totalBytes != state.totalBytes()
            ) {
                body.close()
                throw RangeUnsupportedException("Content-Range 与请求区间不一致")
            }
            if (body.contentLength() > expectedBytes) {
                body.close()
                throw IOException("分片响应长度异常")
            }

            body.use { responseBody ->
                responseBody.byteStream().use { input ->
                    RandomAccessFile(partFile, "rw").use { output ->
                        output.seek(start)
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            val absolutePosition = start + written
                            val bytesToWrite = state.allowedWriteBytes(
                                rangeId = range.id,
                                absolutePosition = absolutePosition,
                                requestedBytes = read
                            )
                            if (bytesToWrite <= 0) {
                                break
                            }
                            if (written + bytesToWrite.toLong() > expectedBytes) {
                                throw IOException("分片下载大小异常")
                            }
                            output.write(buffer, 0, bytesToWrite)
                            written += bytesToWrite
                            state.recordBytes(range.id, bytesToWrite.toLong())

                            val now = System.currentTimeMillis()
                            if (now - lastLogAt >= SEGMENT_LOG_INTERVAL_MS) {
                                val intervalBytes = written - lastLoggedBytes
                                Log.i(
                                    "ApkUpdate",
                                    "range ${range.id} progress $written/$expectedBytes, " +
                                        "interval=${speedText(intervalBytes, now - lastLogAt)}, " +
                                        "avg=${speedText(written, now - startedAt)}"
                                )
                                lastLogAt = now
                                lastLoggedBytes = written
                            }
                            if (bytesToWrite < read) {
                                break
                            }
                            read = input.read(buffer)
                        }
                    }
                }
            }

            if (!state.rangeIsComplete(range.id)) {
                throw IOException("分片下载不完整（${written}/${expectedBytes}）")
            }
            val elapsed = System.currentTimeMillis() - startedAt
            Log.i(
                "ApkUpdate",
                "range ${range.id} complete bytes=$written, plannedBytes=$expectedBytes, elapsedMs=$elapsed, " +
                    "avg=${speedText(written, elapsed)}"
            )
        } catch (e: CancellationException) {
            val elapsed = System.currentTimeMillis() - startedAt
            Log.i(
                "ApkUpdate",
                "range ${range.id} cancelled after ${elapsed}ms, written=$written/$expectedBytes, " +
                    "avg=${speedText(written, elapsed)}"
            )
            throw e
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startedAt
            Log.w(
                "ApkUpdate",
                "range ${range.id} failed after ${elapsed}ms, written=$written/$expectedBytes, " +
                    "avg=${speedText(written, elapsed)}",
                e
            )
            throw e
        }
    }

    private suspend fun probeApkDownload(initialUrl: String, allowMirrorHost: Boolean): DownloadProbe {
        val response = openApkDownloadResponse(initialUrl, allowMirrorHost = allowMirrorHost)
        try {
            if (!response.isSuccessful) {
                throw IOException("下载失败：HTTP ${response.code()}")
            }
            val body = response.body() ?: throw IOException("下载内容为空")
            return DownloadProbe(
                finalUrl = response.raw().request.url.toString(),
                totalBytes = body.contentLength(),
                etag = response.headers()["ETag"],
                lastModified = response.headers()["Last-Modified"]
            ).also {
                Log.i(
                    "ApkUpdate",
                    "apk probe ok code=${response.code()}, url=${it.finalUrl}, " +
                        "totalBytes=${it.totalBytes}, etag=${it.etag}, lastModified=${it.lastModified}"
                )
            }
        } finally {
            closeDownloadResponse(response)
        }
    }

    private suspend fun ensureRangeDownloadSupported(probe: DownloadProbe, allowMirrorHost: Boolean): Long {
        val response = openApkDownloadResponse(
            initialUrl = probe.finalUrl,
            rangeHeader = "bytes=0-0",
            allowMirrorHost = allowMirrorHost
        )
        try {
            val contentRange = parseContentRange(response.headers()["Content-Range"])
            Log.i(
                "ApkUpdate",
                "range probe code=${response.code()}, length=${response.body()?.contentLength()}, " +
                    "contentRange=${response.headers()["Content-Range"]}"
            )
            if (response.code() != HTTP_PARTIAL_CONTENT) {
                throw RangeUnsupportedException("Range 请求未返回 206")
            }
            val body = response.body() ?: throw RangeUnsupportedException("Range 响应内容为空")
            if (body.contentLength() <= 0L) {
                throw RangeUnsupportedException("Range 响应长度无效")
            }
            if (contentRange == null ||
                contentRange.start != 0L ||
                contentRange.end != 0L ||
                contentRange.totalBytes <= 0L
            ) {
                throw RangeUnsupportedException("Range 响应 Content-Range 无效")
            }
            if (probe.totalBytes > 0L && probe.totalBytes != contentRange.totalBytes) {
                throw RangeUnsupportedException("Range 总大小不一致")
            }
            return contentRange.totalBytes
        } finally {
            closeDownloadResponse(response)
        }
    }

    private suspend fun downloadApkSingleStream(
        update: ApkUpdatePolicy.ValidatedUpdate,
        downloadUrl: String,
        allowMirrorHost: Boolean,
        partFile: File
    ): File {
        val response = openApkDownloadResponse(downloadUrl, allowMirrorHost = allowMirrorHost)
        if (!response.isSuccessful) {
            closeDownloadResponse(response)
            throw IOException("下载失败：HTTP ${response.code()}")
        }

        val body = response.body() ?: run {
            closeDownloadResponse(response)
            throw IOException("下载内容为空")
        }

        val total = body.contentLength()
        Log.i(
            "ApkUpdate",
            "single stream response code=${response.code()}, totalBytes=$total, " +
                "url=${response.raw().request.url}"
        )
        if (total > ApkUpdatePolicy.MAX_APK_BYTES) {
            body.close()
            throw IOException("安装包过大，请稍后重试")
        }

        withContext(Dispatchers.Main) {
            apkProgress.value = if (total > 0) 0f else null
        }
        emitApkActiveRangeCount(1)

        partFile.delete()
        var completed = 0L
        var lastEmit = System.currentTimeMillis()
        var lastSpeedLog = lastEmit
        var lastSpeedBytes = 0L
        val startedAt = lastEmit
        var lastProgress = 0f
        if (total > 0) {
            emitApkProgress(0f)
        }
        body.use { responseBody ->
            responseBody.byteStream().use { input: InputStream ->
                BufferedOutputStream(FileOutputStream(partFile), DOWNLOAD_BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        completed += read
                        if (completed > ApkUpdatePolicy.MAX_APK_BYTES) {
                            throw IOException("安装包超过大小限制")
                        }
                        if (total > 0) {
                            val now = System.currentTimeMillis()
                            val progress = (completed.toDouble() / total.toDouble())
                                .coerceIn(0.0, 1.0)
                                .toFloat()
                            if (now - lastSpeedLog >= SEGMENT_LOG_INTERVAL_MS) {
                                val intervalBytes = completed - lastSpeedBytes
                                Log.i(
                                    "ApkUpdate",
                                    "single stream progress $completed/$total, " +
                                        "interval=${speedText(intervalBytes, now - lastSpeedLog)}, " +
                                        "avg=${speedText(completed, now - startedAt)}"
                                )
                                lastSpeedLog = now
                                lastSpeedBytes = completed
                            }
                            if (progress - lastProgress >= PROGRESS_MIN_DELTA ||
                                now - lastEmit >= PROGRESS_MIN_INTERVAL_MS ||
                                completed == total
                            ) {
                                emitApkProgress(progress)
                                lastProgress = progress
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
            throw IOException("下载不完整（${completed}/${total}），请重试")
        }
        if (!partFile.exists() || partFile.length() <= 0L) {
            throw IOException("下载内容为空")
        }
        val elapsed = System.currentTimeMillis() - startedAt
        Log.i(
            "ApkUpdate",
            "single stream complete bytes=$completed, elapsedMs=$elapsed, " +
                "avg=${speedText(completed, elapsed)}"
        )

        val hash = runCatching { sha256Of(partFile) }.getOrNull()
        if (!hash.equals(update.sha256, ignoreCase = true)) {
            Log.w("ApkUpdate", "download sha256 mismatch: expected=${update.sha256}, got=$hash")
            partFile.delete()
            throw SecurityException("文件校验失败，请重试")
        }
        return partFile
    }

    private fun loadSegmentMetadata(metaFile: File): ApkSegmentDownloadPolicy.Metadata? {
        if (!metaFile.exists() || metaFile.length() <= 0L) return null
        return runCatching {
            val metadata = gson.fromJson(metaFile.readText(), ApkSegmentDownloadPolicy.Metadata::class.java)
            if (metadata.ranges.isEmpty()) null else metadata
        }.getOrNull()
    }

    private fun writeAdaptiveMetadata(
        metaFile: File,
        update: ApkUpdatePolicy.ValidatedUpdate,
        probe: DownloadProbe,
        ranges: List<ApkSegmentDownloadPolicy.DownloadRange>
    ) {
        val metadata = ApkSegmentDownloadPolicy.Metadata(
            versionCode = update.info.versionCode,
            url = probe.finalUrl,
            sha256 = update.sha256,
            totalBytes = probe.totalBytes,
            etag = probe.etag,
            lastModified = probe.lastModified,
            minChunkBytes = ApkSegmentDownloadPolicy.MIN_CHUNK_BYTES,
            maxConcurrency = ApkSegmentDownloadPolicy.MAX_CONCURRENCY,
            ranges = ranges
        )
        val tempFile = File(metaFile.parentFile, "${metaFile.name}.tmp")
        tempFile.writeText(gson.toJson(metadata))
        metaFile.delete()
        if (!tempFile.renameTo(metaFile)) {
            tempFile.copyTo(metaFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun deletePartialDownload(partFile: File, metaFile: File) {
        partFile.delete()
        metaFile.delete()
        File(metaFile.parentFile, "${metaFile.name}.tmp").delete()
    }

    private suspend fun openApkDownloadResponse(
        initialUrl: String,
        rangeHeader: String? = null,
        ifRangeHeader: String? = null,
        allowMirrorHost: Boolean = false
    ): Response<ResponseBody> {
        var currentUrl = initialUrl
        repeat(ApkUpdatePolicy.MAX_DOWNLOAD_REDIRECTS + 1) { redirectCount ->
            val response = AhuTong.APK_DOWNLOAD_API.downloadByUrl(currentUrl, rangeHeader, ifRangeHeader)
            val finalUrl = response.raw().request.url.toString()
            ApkUpdatePolicy.validateDownloadUrl(finalUrl, allowMirrorHost = allowMirrorHost).getOrElse {
                closeDownloadResponse(response)
                throw SecurityException("下载地址不受信任")
            }

            val code = response.code()
            if (code in 300..399) {
                val location = response.headers()["Location"]
                closeDownloadResponse(response)
                if (location.isNullOrBlank()) {
                    throw IOException("下载重定向地址为空")
                }
                if (redirectCount >= ApkUpdatePolicy.MAX_DOWNLOAD_REDIRECTS) {
                    throw IOException("下载重定向次数过多")
                }
                currentUrl = ApkUpdatePolicy.validateDownloadUrl(
                    rawUrl = location,
                    baseUrl = finalUrl,
                    allowMirrorHost = allowMirrorHost
                ).getOrElse {
                    throw SecurityException("下载重定向到不受信任地址")
                }
                return@repeat
            }

            return response
        }
        throw IOException("下载重定向次数过多")
    }

    private fun closeDownloadResponse(response: Response<ResponseBody>) {
        response.body()?.close()
        response.errorBody()?.close()
    }

    private fun parseContentRange(value: String?): ContentRange? {
        if (value.isNullOrBlank()) return null
        val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+)""", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim())
            ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        if (end < start || total <= 0L) return null
        return ContentRange(start = start, end = end, totalBytes = total)
    }

    private fun isRetryableDownloadError(error: IOException): Boolean {
        return error !is RangeUnsupportedException
    }

    private fun retryDelayMillis(attempt: Int): Long {
        return (500L * (1 shl (attempt - 1).coerceIn(0, 4))).coerceAtMost(8_000L)
    }

    private fun speedText(bytes: Long, elapsedMillis: Long): String {
        if (elapsedMillis <= 0L) return "n/a"
        val kibPerSecond = bytes * 1000.0 / elapsedMillis / 1024.0
        return String.format(Locale.US, "%.1f KiB/s", kibPerSecond)
    }

    private fun progressPercent(progress: Float): String {
        return String.format(Locale.US, "%.1f%%", progress.coerceIn(0f, 1f) * 100f)
    }

    private fun formatDownloadElapsed(elapsedMillis: Long): String {
        val totalSeconds = ((elapsedMillis.coerceAtLeast(0L) + 999L) / 1000L).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "${minutes}分${seconds}秒"
    }

    private fun percentText(value: Double): String {
        return String.format(Locale.US, "%.1f%%", value * 100.0)
    }

    private fun replaceDownloadedApk(partFile: File, outFile: File, expectedSha256: String) {
        if (outFile.exists() && !outFile.delete()) {
            throw IOException("无法替换旧安装包")
        }

        if (!partFile.renameTo(outFile)) {
            partFile.inputStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (!partFile.delete()) {
                Log.w("ApkUpdate", "failed to delete temporary APK: ${partFile.name}")
            }
        }

        if (!verifyCachedApk(outFile, expectedSha256, "downloaded APK")) {
            throw SecurityException("文件校验失败，请重试")
        }
    }

    private suspend fun emitApkProgress(progress: Float) {
        withContext(Dispatchers.Main.immediate) {
            apkProgress.value = progress.coerceIn(0f, 1f)
        }
    }

    private suspend fun emitApkActiveRangeCount(activeRangeCount: Int?) {
        withContext(Dispatchers.Main.immediate) {
            apkActiveRangeCount.value = activeRangeCount
        }
    }

    private suspend fun emitApkDownloadSegments(segments: List<ApkDownloadSegment>) {
        withContext(Dispatchers.Main.immediate) {
            apkDownloadSegments.value = segments
        }
    }

    fun continueApkDownloadInBackground() {
        if (apkDownloading.value) {
            showDialogWhenApkDownloadCompletes = true
        }
        showApkUpdateDialog.value = false
    }

    fun checkApkUpdateManually(
        context: Context,
        onResult: (String) -> Unit
    ) {
        if (apkUpdateChecking.value) {
            onResult("正在检查更新")
            return
        }

        apkUpdateChecking.value = true
        val appContext = context.applicationContext

        viewModelScope.launch(Dispatchers.IO) {
            val resultText = try {
                val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
                cleanStaleApks(dir)

                val info = AhuTong.API.getApkUpdateInfo()
                val update = ApkUpdatePolicy.validate(info, AppVersion.code()).getOrElse { error ->
                    return@launch finishManualUpdateCheck(
                        if (ApkUpdatePolicy.isNoUpdateFailure(error)) {
                            "已是最新版本"
                        } else {
                            "检查更新失败：${error.message ?: "更新信息无效"}"
                        },
                        onResult
                    )
                }

                val localApk = File(dir, "update-${update.info.versionCode}.apk")
                val localReady = if (localApk.exists() && localApk.length() > 0L) {
                    verifyCachedApk(localApk, update.sha256, "manual check local APK")
                } else {
                    false
                }

                withContext(Dispatchers.Main) {
                    apkUpdateInfo.value = update.info
                    apkErrorText.value = null
                    apkLocalReady.value = localReady
                    showApkUpdateDialog.value = true
                }

                "发现新版本 ${update.info.versionName}"
            } catch (e: Exception) {
                Log.w("ApkUpdate", "manual update check failed", e)
                "检查更新失败：${e.message ?: "请稍后重试"}"
            }

            finishManualUpdateCheck(resultText, onResult)
        }
    }

    private suspend fun finishManualUpdateCheck(
        resultText: String,
        onResult: (String) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            apkUpdateChecking.value = false
            onResult(resultText)
        }
    }

    private fun selectedValidatedUpdate(): ApkUpdatePolicy.ValidatedUpdate? {
        val info = apkUpdateInfo.value ?: return null
        return ApkUpdatePolicy.validate(info, AppVersion.code()).getOrElse {
            apkErrorText.value = it.message ?: "更新信息校验失败"
            apkLocalReady.value = false
            Log.w("ApkUpdate", "invalid APK update metadata: ${it.message}")
            null
        }
    }

    private fun verifyCachedApk(file: File, expectedSha256: String, label: String): Boolean {
        val hash = runCatching { sha256Of(file) }.getOrNull()
        val match = hash.equals(expectedSha256, ignoreCase = true)
        if (!match) {
            Log.w("ApkUpdate", "$label sha256 mismatch, expected=$expectedSha256, got=$hash, deleting")
            file.delete()
        }
        return match
    }

    fun reportApkInstallError(message: String) {
        apkLocalReady.value = false
        apkErrorText.value = message
    }

    fun markInstallHandled() {
        downloadedApkFile.value = null
    }

    fun logout() {
        AHUCache.logout()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
