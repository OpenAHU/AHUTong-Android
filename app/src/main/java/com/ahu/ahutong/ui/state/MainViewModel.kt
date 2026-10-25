package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.core.storage.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.ahu.ahutong.data.update.ApkDownloadEvent
import com.ahu.ahutong.data.update.ApkUpdateChecker
import com.ahu.ahutong.data.update.UpdateCheck
import com.ahu.ahutong.data.update.UpdateCheckEntry
import com.ahu.ahutong.data.update.ApkDownloader
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    private val downloader: ApkDownloader,
    private val updateChecker: ApkUpdateChecker,
    private val settings: SettingsStore
) : ViewModel() {

    /** 导航栏「一键扫码」入口开关（默认关；设置→偏好里打开才出现该按钮）。 */
    val scanShortcutEnabled: StateFlow<Boolean> = settings.scanShortcutEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // 下载器说的话翻译成界面状态：这里只做映射，规则（校验、镜像、重定向）都在 :data:update 里。
        viewModelScope.launch {
            downloader.events.collect { event ->
                if (selectedUpdate == null || downloadRequestedFor !== selectedUpdate) return@collect
                when (event) {
                    ApkDownloadEvent.Started -> {
                        apkDownloading.value = true
                        apkDownloadFailed.value = false
                        apkErrorText.value = null
                        apkProgress.value = null
                        apkDownloadElapsedText.value = null
                    }
                    is ApkDownloadEvent.Progress -> apkProgress.value = event.fraction
                    is ApkDownloadEvent.Succeeded -> {
                        val update = selectedUpdate ?: return@collect
                        val revision = updateChannelRevision
                        val verified = try {
                            withContext(Dispatchers.IO) {
                                downloader.cachedApk(update.info.versionCode, update.sha256)?.takeIf {
                                    it.canonicalFile == event.apkFile.canonicalFile
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        if (revision != updateChannelRevision || selectedUpdate !== update) return@collect
                        if (verified == null) {
                            onApkDownloadFailed("文件校验失败，请重新下载", fromDownloader = false)
                            return@collect
                        }
                        downloadRequestedFor = null
                        consecutiveAutoRetries = 0
                        retryJob?.cancel()
                        retryJob = null
                        apkDownloading.value = false
                        apkProgress.value = null
                        apkDownloadElapsedText.value = event.elapsedText
                        apkLocalReady.value = true
                        apkDownloadFailed.value = false
                        apkErrorText.value = null
                        // Installation and reopening the dialog both require a subsequent user/lifecycle action.
                    }
                    is ApkDownloadEvent.Failed -> {
                        onApkDownloadFailed(event.message)
                    }
                }
            }
        }
    }

    // App update UI states
    var showApkUpdateDialog = mutableStateOf(false)
    var apkUpdateInfo = mutableStateOf<ApkUpdateInfo?>(null)
    var apkDownloading = mutableStateOf(false)
    val apkDownloadFailed = mutableStateOf(false)
    var apkProgress = mutableStateOf<Float?>(null)
    var apkDownloadElapsedText = mutableStateOf<String?>(null)
    var apkErrorText = mutableStateOf<String?>(null)
    var downloadedApkFile = mutableStateOf<File?>(null)
    var apkUpdateChecking = mutableStateOf(false)
    /** 本地已存在目标版本 APK，可直接安装 */
    var apkLocalReady = mutableStateOf(false)

    private var selectedUpdate: UpdateCheck.Available? = null
    private var downloadRequestedFor: UpdateCheck.Available? = null
    private var appInForeground = false
    private val forcedCountdown = ApkUpdateForegroundCountdown()
    private var forcedDownloadJob: Job? = null
    private var retryJob: Job? = null
    private var consecutiveAutoRetries = 0
    @Volatile private var updateChannelRevision = 0L

    /** Membership and account changes invalidate any update offered by the previous channel. */
    fun onUpdateChannelChanged() {
        updateChannelRevision++
        downloader.cancel()
        cancelScheduledDownloads()
        selectedUpdate = null
        downloadRequestedFor = null
        consecutiveAutoRetries = 0
        showApkUpdateDialog.value = false
        apkUpdateInfo.value = null
        apkDownloading.value = false
        apkDownloadFailed.value = false
        apkLocalReady.value = false
        apkProgress.value = null
        apkErrorText.value = null
        apkDownloadElapsedText.value = null
        downloadedApkFile.value = null
        viewModelScope.launch { checkApkUpdate() }
    }

    /**
     * 启动时检查云端更新、清理残留 APK、检测本地缓存
     * 全部在 IO 线程执行，不阻塞主线程
     */
    suspend fun checkApkUpdate() = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) {
            Log.i("ApkUpdate", "startup update check disabled for debug build")
            return@withContext
        }
        val revision = updateChannelRevision
        when (val check = updateChecker.check(BuildConfig.VERSION_CODE, UpdateCheckEntry.STARTUP)) {
            is UpdateCheck.Available -> withContext(Dispatchers.Main) {
                if (revision != updateChannelRevision) return@withContext
                acceptApkUpdate(check, manual = false)
            }
            // 启动检查对"没有更新"与"检查失败"都保持安静（与迁移前一致）。
            UpdateCheck.UpToDate, is UpdateCheck.Failed -> Unit
        }
    }

    private fun acceptApkUpdate(check: UpdateCheck.Available, manual: Boolean) {
        val previous = selectedUpdate
        val sameOffer = previous != null && previous.info.versionCode == check.info.versionCode &&
            previous.sha256 == check.sha256 && previous.downloadUrl == check.downloadUrl &&
            previous.info.force == check.info.force && previous.info.channel == check.info.channel
        if (!sameOffer) {
            if (apkDownloading.value) downloader.cancel()
            cancelScheduledDownloads()
            selectedUpdate = check
            downloadRequestedFor = null
            consecutiveAutoRetries = 0
            apkDownloading.value = false
            apkDownloadFailed.value = false
            apkProgress.value = null
            apkDownloadElapsedText.value = null
            downloadedApkFile.value = null
            apkLocalReady.value = check.localApk != null
            forcedCountdown.reset(if (check.info.force && check.localApk == null) 3_000L else null)
        } else if (check.localApk != null && !apkDownloading.value) {
            apkLocalReady.value = true
            apkDownloadFailed.value = false
            consecutiveAutoRetries = 0
            retryJob?.cancel()
            retryJob = null
            forcedCountdown.reset(null)
            forcedDownloadJob?.cancel()
            forcedDownloadJob = null
        }
        apkUpdateInfo.value = check.info
        apkErrorText.value = null
        showApkUpdateDialog.value = manual || (check.info.force && apkLocalReady.value && appInForeground)
        scheduleForcedDownload()
    }

    fun onAppForeground() {
        if (appInForeground) return
        appInForeground = true
        if (selectedUpdate?.info?.force == true && apkLocalReady.value) {
            showApkUpdateDialog.value = true
        }
        scheduleForcedDownload()
    }

    fun onAppBackground() {
        if (!appInForeground) return
        appInForeground = false
        forcedCountdown.pause()
        forcedDownloadJob?.cancel()
        forcedDownloadJob = null
    }

    fun openApkUpdateDialog() {
        if (selectedUpdate != null) showApkUpdateDialog.value = true
    }

    fun dismissApkUpdateDialog() {
        if (selectedUpdate?.info?.force == true) return
        showApkUpdateDialog.value = false
    }

    private fun scheduleForcedDownload() {
        val update = selectedUpdate ?: return
        if (!appInForeground || !update.info.force || apkLocalReady.value || apkDownloading.value ||
            forcedDownloadJob?.isActive == true
        ) return
        val remaining = forcedCountdown.resume() ?: return
        val revision = updateChannelRevision
        forcedDownloadJob = viewModelScope.launch {
            delay(remaining)
            forcedDownloadJob = null
            if (appInForeground && revision == updateChannelRevision && selectedUpdate === update &&
                !apkLocalReady.value && !apkDownloading.value
            ) startApkDownloadAttempt(forceRedownload = false, resetRetries = false)
        }
    }

    private fun cancelScheduledDownloads() {
        forcedDownloadJob?.cancel()
        forcedDownloadJob = null
        forcedCountdown.reset(null)
        retryJob?.cancel()
        retryJob = null
    }

    private fun onApkDownloadFailed(message: String, fromDownloader: Boolean = true) {
        val update = selectedUpdate ?: return
        downloadRequestedFor = null
        apkDownloading.value = false
        apkDownloadFailed.value = fromDownloader
        apkLocalReady.value = false
        apkProgress.value = null
        apkDownloadElapsedText.value = null
        apkErrorText.value = message
        if (consecutiveAutoRetries >= 3 || !canAutomaticallyRetryApkDownload(message)) return
        consecutiveAutoRetries++
        retryJob?.cancel()
        val revision = updateChannelRevision
        retryJob = viewModelScope.launch {
            delay(1_000)
            retryJob = null
            if (revision == updateChannelRevision && selectedUpdate === update &&
                !apkDownloading.value && !apkLocalReady.value
            ) startApkDownloadAttempt(forceRedownload = false, resetRetries = false)
        }
    }
    /**
     * 直接安装本地已缓存的 APK（用户点击"安装"按钮）
     */
    fun installLocalApk() {
        val update = selectedUpdate ?: return
        val revision = updateChannelRevision
        viewModelScope.launch(Dispatchers.IO) {
            val localApk = downloader.cachedApk(update.info.versionCode, update.sha256)
            if (localApk == null) {
                withContext(Dispatchers.Main) {
                    if (revision != updateChannelRevision || selectedUpdate !== update) return@withContext
                    apkLocalReady.value = false
                    apkDownloadFailed.value = false
                    apkDownloadElapsedText.value = null
                    apkErrorText.value = "本地文件已丢失或损坏，请重新下载"
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (revision == updateChannelRevision && selectedUpdate === update) {
                    showApkUpdateDialog.value = false
                    downloadedApkFile.value = localApk
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun startApkDownload(
        forceRedownload: Boolean = false,
        installAfterDownload: Boolean = false
    ) {
        // Kept for existing callers: downloading always ends at ready, including the old "installAfterDownload" flow.
        startApkDownloadAttempt(forceRedownload, resetRetries = true)
    }

    private fun startApkDownloadAttempt(forceRedownload: Boolean, resetRetries: Boolean) {
        val update = selectedUpdate ?: return
        if (apkDownloading.value) return
        showApkUpdateDialog.value = false
        cancelScheduledDownloads()
        if (resetRetries) consecutiveAutoRetries = 0
        downloadRequestedFor = update
        downloadedApkFile.value = null
        // 同步立起界面状态（与迁移前一致）：下载器随后用事件接管后续变化。
        apkDownloading.value = true
        apkDownloadFailed.value = false
        apkErrorText.value = null
        apkProgress.value = null
        apkDownloadElapsedText.value = null
        // 强制重下会先删掉本地包：立刻把「可安装」按下去，否则失败的下载会让界面继续
        // 提供「安装」（点下去只会得到「本地文件已丢失」）。
        if (forceRedownload) {
            apkLocalReady.value = false
        }
        downloader.start(
            versionCode = update.info.versionCode,
            downloadUrl = update.downloadUrl,
            sha256 = update.sha256,
            forceRedownload = forceRedownload
        )
    }

    fun continueApkDownloadInBackground() {
        showApkUpdateDialog.value = false
    }

    /** Stop the current download and silence automatic prompts for this exact versionCode. */
    fun skipCurrentApkVersion() {
        val update = selectedUpdate ?: return
        if (update.info.force) return

        downloader.cancel()
        cancelScheduledDownloads()
        downloadRequestedFor = null
        consecutiveAutoRetries = 0
        apkDownloading.value = false
        apkDownloadFailed.value = false
        apkProgress.value = null
        downloadedApkFile.value = null

        val revision = updateChannelRevision
        viewModelScope.launch(Dispatchers.IO) {
            val saved = updateChecker.skipVersion(update.info.versionCode)
            withContext(Dispatchers.Main) {
                if (revision != updateChannelRevision || selectedUpdate !== update) return@withContext
                if (!saved) {
                    apkErrorText.value = "保存跳过设置失败，请重试"
                } else {
                    selectedUpdate = null
                    apkUpdateInfo.value = null
                    apkLocalReady.value = false
                    apkDownloadElapsedText.value = null
                    apkErrorText.value = null
                    showApkUpdateDialog.value = false
                }
            }
        }
    }

    fun checkApkUpdateManually(
        onResult: (String) -> Unit
    ) {
        if (apkUpdateChecking.value) {
            onResult("正在检查更新")
            return
        }

        apkUpdateChecking.value = true
        val revision = updateChannelRevision

        viewModelScope.launch(Dispatchers.IO) {
            val resultText = try {
                when (
                    val check = updateChecker.check(
                        BuildConfig.VERSION_CODE,
                        UpdateCheckEntry.MANUAL
                    )
                ) {
                    is UpdateCheck.Available -> {
                        withContext(Dispatchers.Main) {
                            if (revision != updateChannelRevision) return@withContext
                            acceptApkUpdate(check, manual = true)
                        }
                        "发现新版本 ${check.info.versionName}"
                    }
                    UpdateCheck.UpToDate -> {
                        "已是最新版本"
                    }
                    is UpdateCheck.Failed -> {
                        "检查更新失败：" + (
                            check.reason
                                ?: if (check.invalidMetadata) "更新信息无效" else "请稍后重试"
                            )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("ApkUpdate", "manual update check failed", e)
                "检查更新失败：${e.message ?: "请稍后重试"}"
            }

            finishManualUpdateCheck(
                if (revision == updateChannelRevision) resultText else "更新渠道已变更，请重新检查",
                onResult
            )
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

    fun reportApkInstallError(message: String) {
        apkLocalReady.value = false
        apkDownloadFailed.value = false
        apkErrorText.value = message
    }

    fun markInstallHandled() {
        downloadedApkFile.value = null
    }

    override fun onCleared() {
        cancelScheduledDownloads()
        downloader.cancel()
        super.onCleared()
    }
}
