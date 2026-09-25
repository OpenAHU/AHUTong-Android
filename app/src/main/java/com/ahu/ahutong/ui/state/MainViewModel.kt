package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.ahu.ahutong.BuildConfig
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    private val downloader: ApkDownloader,
    private val updateChecker: ApkUpdateChecker
) : ViewModel() {

    init {
        // 下载器说的话翻译成界面状态：这里只做映射，规则（校验、镜像、重定向）都在 :data:update 里。
        viewModelScope.launch {
            downloader.events.collect { event ->
                if (selectedUpdate == null) return@collect
                when (event) {
                    ApkDownloadEvent.Started -> {
                        apkDownloading.value = true
                        apkErrorText.value = null
                        apkProgress.value = null
                        apkDownloadElapsedText.value = null
                        showApkMirrorPrompt.value = false
                    }
                    is ApkDownloadEvent.Progress -> apkProgress.value = event.fraction
                    ApkDownloadEvent.MirrorSuggested -> showApkMirrorPrompt.value = true
                    is ApkDownloadEvent.Succeeded -> {
                        apkDownloading.value = false
                        apkProgress.value = null
                        apkDownloadElapsedText.value = event.elapsedText
                        showApkMirrorPrompt.value = false
                        apkUsingMirrorSource.value = false
                        apkLocalReady.value = true
                        if (installAfterApkDownload) {
                            downloadedApkFile.value = event.apkFile
                        } else if (showDialogWhenApkDownloadCompletes) {
                            showDialogWhenApkDownloadCompletes = false
                            showApkUpdateDialog.value = true
                        }
                    }
                    is ApkDownloadEvent.Failed -> {
                        apkDownloading.value = false
                        apkProgress.value = null
                        apkDownloadElapsedText.value = null
                        showApkMirrorPrompt.value = false
                        apkUsingMirrorSource.value = false
                        apkErrorText.value = event.message
                        if (showDialogWhenApkDownloadCompletes) {
                            showDialogWhenApkDownloadCompletes = false
                            showApkUpdateDialog.value = true
                        }
                    }
                }
            }
        }
    }

    // App update UI states
    var showApkUpdateDialog = mutableStateOf(false)
    var apkUpdateInfo = mutableStateOf<ApkUpdateInfo?>(null)
    var apkDownloading = mutableStateOf(false)
    var apkProgress = mutableStateOf<Float?>(null)
    var apkDownloadElapsedText = mutableStateOf<String?>(null)
    var apkErrorText = mutableStateOf<String?>(null)
    var downloadedApkFile = mutableStateOf<File?>(null)
    var apkUpdateChecking = mutableStateOf(false)
    var showApkMirrorPrompt = mutableStateOf(false)
    private val apkUsingMirrorSource = mutableStateOf(false)
    /** 本地已存在目标版本 APK，可直接安装 */
    var apkLocalReady = mutableStateOf(false)

    private var installAfterApkDownload = false
    private var showDialogWhenApkDownloadCompletes = false
    private var selectedUpdate: UpdateCheck.Available? = null

    /**
     * 启动时检查云端更新、清理残留 APK、检测本地缓存
     * 全部在 IO 线程执行，不阻塞主线程
     */
    suspend fun checkApkUpdate() = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) {
            Log.i("ApkUpdate", "startup update check disabled for debug build")
            return@withContext
        }
        when (val check = updateChecker.check(BuildConfig.VERSION_CODE, UpdateCheckEntry.STARTUP)) {
            is UpdateCheck.Available -> withContext(Dispatchers.Main) {
                selectedUpdate = check
                apkUpdateInfo.value = check.info
                apkErrorText.value = null
                apkLocalReady.value = check.localApk != null
                if (check.localApk == null) {
                    apkDownloadElapsedText.value = null
                }
                showApkUpdateDialog.value = true
                if (check.localApk == null && !apkDownloading.value) {
                    startApkDownload(installAfterDownload = false)
                }
            }
            // 启动检查对"没有更新"与"检查失败"都保持安静（与迁移前一致）。
            UpdateCheck.UpToDate, is UpdateCheck.Failed -> Unit
        }
    }
    /**
     * 直接安装本地已缓存的 APK（用户点击"安装"按钮）
     */
    fun installLocalApk() {
        val update = selectedUpdate ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val localApk = downloader.cachedApk(update.info.versionCode, update.sha256)
            if (localApk == null) {
                withContext(Dispatchers.Main) {
                    apkLocalReady.value = false
                    apkDownloadElapsedText.value = null
                    apkErrorText.value = "本地文件已丢失或损坏，请重新下载"
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                downloadedApkFile.value = localApk
            }
        }
    }

    fun startApkDownload(
        forceRedownload: Boolean = false,
        installAfterDownload: Boolean = false
    ) {
        val update = selectedUpdate ?: return
        if (apkDownloading.value) return
        // 同步立起界面状态（与迁移前一致）：下载器随后用事件接管后续变化。
        apkDownloading.value = true
        apkErrorText.value = null
        apkProgress.value = null
        apkDownloadElapsedText.value = null
        showApkMirrorPrompt.value = false
        apkUsingMirrorSource.value = false
        // 强制重下会先删掉本地包：立刻把「可安装」按下去，否则失败的下载会让界面继续
        // 提供「安装」（点下去只会得到「本地文件已丢失」）。
        if (forceRedownload) {
            apkLocalReady.value = false
        }
        installAfterApkDownload = installAfterDownload
        downloader.start(
            versionCode = update.info.versionCode,
            downloadUrl = update.downloadUrl,
            sha256 = update.sha256,
            forceRedownload = forceRedownload
        )
    }

    fun keepPrimaryApkDownload() {
        showApkMirrorPrompt.value = false
    }

    fun switchApkDownloadToMirror() {
        val update = selectedUpdate ?: return
        showApkMirrorPrompt.value = false
        if (!apkDownloading.value || apkUsingMirrorSource.value) return
        apkUsingMirrorSource.value = true
        downloader.switchToMirror(
            versionCode = update.info.versionCode,
            downloadUrl = update.downloadUrl,
            sha256 = update.sha256
        )
    }
    fun continueApkDownloadInBackground() {
        if (apkDownloading.value) {
            showDialogWhenApkDownloadCompletes = true
        }
        showApkUpdateDialog.value = false
    }

    /** Stop the current download and silence automatic prompts for this exact versionCode. */
    fun skipCurrentApkVersion() {
        val update = selectedUpdate ?: return
        if (update.info.force) return

        downloader.cancel()
        installAfterApkDownload = false
        showDialogWhenApkDownloadCompletes = false
        apkDownloading.value = false
        apkProgress.value = null
        showApkMirrorPrompt.value = false
        downloadedApkFile.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val saved = updateChecker.skipVersion(update.info.versionCode)
            withContext(Dispatchers.Main) {
                if (!saved) {
                    apkErrorText.value = "保存跳过设置失败，请重试"
                } else if (selectedUpdate?.info?.versionCode == update.info.versionCode) {
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
                            selectedUpdate = check
                            apkUpdateInfo.value = check.info
                            apkErrorText.value = null
                            apkLocalReady.value = check.localApk != null
                            showApkUpdateDialog.value = true
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

    fun reportApkInstallError(message: String) {
        apkLocalReady.value = false
        apkErrorText.value = message
    }

    fun markInstallHandled() {
        downloadedApkFile.value = null
    }

    override fun onCleared() {
        downloader.cancel()
        super.onCleared()
    }
}
