package com.ahu.ahutong

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.ahu.ahutong.appwidget.WidgetUpdateScheduler
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.sdk.LocalServiceClient
import com.ahu.ahutong.sdk.RustSDK
import com.ahu.ahutong.ui.component.ApkMirrorSourceDialog
import com.ahu.ahutong.ui.component.ApkUpdateDialog
import com.ahu.ahutong.ui.screen.Main
import com.ahu.ahutong.ui.state.AboutViewModel
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.state.MainViewModel
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.ahu.ahutong.personalization.diagnostics.DiagnosticsContribution
import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.action.ActionSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.session.AhuSession
import com.ahu.ahutong.data.update.ApkVerifier
import com.ahu.ahutong.data.debug.DebugClock
import java.time.LocalDate

private const val DEBUG_BUILD_NOTICE_DURATION_MS = 3_000L
private const val STARTUP_BACKGROUND_WORK_DELAY_MS = 250L
private const val DAY_WATCH_INTERVAL_MS = 60_000L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var behaviorRuntime: BehaviorPredictionRuntime
    @Inject lateinit var behaviorRecorder: BehaviorRecorder
    @Inject lateinit var diagnosticsContribution: DiagnosticsContribution
    @Inject lateinit var paymentQrCommands: PaymentQrOpenCommandStore
    @Inject lateinit var session: AhuSession

    val TAG = "MainActivity"

    private val mainViewModel: MainViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()
    private val discoveryViewModel: DiscoveryViewModel by viewModels()
    private val scheduleViewModel: ScheduleViewModel by viewModels()
    private val aboutViewModel: AboutViewModel by viewModels()
    private val preferencesViewModel: PreferencesViewModel by viewModels()

    /** 最近一次确认的日期：常开跨过午夜时靠它发现「该按新的一天重算了」。 */
    @Volatile
    private var lastKnownDate: LocalDate? = null


    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeActivityResultLauncher()
        if (intent?.data != null) behaviorRuntime.markNextNavigationSource(ActionSource.DEEPLINK)

        setContent {
            val appUiTheme by preferencesViewModel.appUiTheme.collectAsState()
            val themeColorHex by preferencesViewModel.themeColor.collectAsState()
            val themeMode by preferencesViewModel.appThemeMode.collectAsState()
            val isThemePreferenceReady by
                preferencesViewModel.isUiThemePreferenceReady.collectAsState()
            val componentSlotOverrides by
                preferencesViewModel.componentSlotOverrides.collectAsState()
            AHUTheme(
                config = AhuThemeConfig(
                    appUiTheme = appUiTheme,
                    themeColorHex = themeColorHex,
                    themeMode = themeMode,
                    isPreferenceReady = isThemePreferenceReady,
                    componentSlotOverrides = componentSlotOverrides
                )
            ) {
                val navController = rememberNavController()
                val sessionStatus by session.state.collectAsState()
                var dismissedExpiredSession by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(sessionStatus) {
                    if (sessionStatus != AhuSessionState.Status.Expired) {
                        dismissedExpiredSession = false
                    }
                }
                val isReLoginDialogShown =
                    sessionStatus == AhuSessionState.Status.Expired && !dismissedExpiredSession

                // 日界守望：前台常开跨过午夜时，每分钟对一次日期，跨天即按新的一天重算
                // （周次/星期几/今日课程/课程提醒排期都挂在 loadConfig 上）。
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(DAY_WATCH_INTERVAL_MS)
                        checkDayRollover()
                    }
                }

//                if (showHotUpdateDialog) {
//                    HotUpdateDialog(
//                        isDownloading = isHotUpdateDownloading,
//                        onConfirm = {
//                            // 调用 SDK 的重启逻辑
//                            RustSDK.restartApp(this)
//                        }
//                    )
//                }
                if (mainViewModel.showApkUpdateDialog.value && mainViewModel.apkUpdateInfo.value != null) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    ApkUpdateDialog(
                        info = mainViewModel.apkUpdateInfo.value!!,
                        downloading = mainViewModel.apkDownloading.value,
                        progress = mainViewModel.apkProgress.value,
                        downloadElapsedText = mainViewModel.apkDownloadElapsedText.value,
                        errorText = mainViewModel.apkErrorText.value,
                        apkLocalReady = mainViewModel.apkLocalReady.value,
                        onConfirm = {
                            mainViewModel.startApkDownload(
                                installAfterDownload = true
                            )
                        },
                        onInstallLocal = {
                            mainViewModel.installLocalApk()
                        },
                        onRedownload = {
                            mainViewModel.startApkDownload(forceRedownload = true)
                        },
                        onDismiss = {
                            mainViewModel.showApkUpdateDialog.value = false
                        },
                        onCancel = {
                            mainViewModel.continueApkDownloadInBackground()
                            Toast.makeText(this@MainActivity, "已转到后台下载", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                if (mainViewModel.showApkMirrorPrompt.value) {
                    ApkMirrorSourceDialog(
                        onUseMirror = {
                            mainViewModel.switchApkDownloadToMirror()
                        },
                        onKeepOriginal = {
                            mainViewModel.keepPrimaryApkDownload()
                        }
                    )
                }

                val downloaded = mainViewModel.downloadedApkFile.value
                if (downloaded != null) {
                    androidx.compose.runtime.LaunchedEffect(downloaded) {
                        ensureInstallPermissionThen {
                            installApk(downloaded)
                        }
                        mainViewModel.markInstallHandled()
                    }
                }

                Main(
                    navController = navController,
                    loginViewModel = loginViewModel,
                    discoveryViewModel = discoveryViewModel,
                    scheduleViewModel = scheduleViewModel,
                    aboutViewModel = aboutViewModel,
                    behaviorRuntime = behaviorRuntime,
                    behaviorRecorder = behaviorRecorder,
                    diagnosticsContribution = diagnosticsContribution,
                    paymentQrCommands = paymentQrCommands,
                    isReLoginShown = isReLoginDialogShown,
                    onReLoginDismiss = { dismissedExpiredSession = true }
                )
            }
        }

        init()
        showDebugBuildNotice(savedInstanceState)
    }

    private fun init() {
        lifecycleScope.launchSafe {
            // Let Compose draw the cached first screen before starting native services,
            // widget scheduling and network refreshes.
            delay(STARTUP_BACKGROUND_WORK_DELAY_MS)
            if (AHUCache.isPrivacyAccepted()) {
                SessionStore.currentUser()?.xh?.takeIf { it.isNotBlank() }?.let { behaviorRuntime.startProfile(it) }
            }

            val storageInitialized = withContext(Dispatchers.IO) {
                WidgetUpdateScheduler.scheduleNext(this@MainActivity)
                RustSDK.loadLibrary(context = applicationContext)
                startLocalService()
            }
            if (!storageInitialized) {
                restoreRustCookies()
            }

            if (SessionStore.isLoggedIn() || AHUCache.getMockData()) {
//                val user = SessionStore.currentUser()
//                val pwd = AHUCache.getWisdomPassword()

                discoveryViewModel.loadActivityBean()
                if (AHUCache.canUseUndergraduateAcademics()) {
                    scheduleViewModel.loadConfig()
                    scheduleViewModel.refreshSchedule()
                }
            }

            if (!BuildConfig.DEBUG) {
                mainViewModel.checkApkUpdate()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        behaviorRuntime.setForeground(true, true)
    }

    override fun onResume() {
        super.onResume()
        // 回到前台立即对一次日期：后台挂过午夜的用户回来的第一眼就该是新的一天。
        checkDayRollover()
    }

    /**
     * 跨天 rollover：scheduleConfig 只随 loadConfig 写入，不触发它就会把「今天」停在昨天。
     * CurrentWeekResolver 本身按当天无状态推算，这里只负责「跨天了就重载」这个触发。
     * loadConfig 内部会顺带重排课程提醒。
     */
    private fun checkDayRollover() {
        val today = DebugClock.nowLocalDate()
        if (lastKnownDate == null) {
            lastKnownDate = today
            return
        }
        if (today == lastKnownDate) return
        lastKnownDate = today
        if ((SessionStore.isLoggedIn() || AHUCache.getMockData()) &&
            AHUCache.canUseUndergraduateAcademics()
        ) {
            scheduleViewModel.loadConfig()
        }
    }

    override fun onStop() {
        behaviorRuntime.setForeground(false, false)
        paymentQrCommands.clear()
        discoveryViewModel.clearQrCode()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data != null) behaviorRuntime.markNextNavigationSource(ActionSource.DEEPLINK)
    }

    private fun showDebugBuildNotice(savedInstanceState: Bundle?) {
        if (!BuildConfig.DEBUG || savedInstanceState != null) return

        val toast = Toast.makeText(
            this,
            R.string.debug_build_notice,
            Toast.LENGTH_LONG
        )
        toast.show()
        lifecycleScope.launchSafe {
            delay(DEBUG_BUILD_NOTICE_DURATION_MS)
            toast.cancel()
        }
    }

//    private fun checkApkUpdateOnStartup() {
//        Log.i("ApkUpdate", "start checkApkUpdateOnStartup")
//
//        lifecycleScope.launch(Dispatchers.IO) {
//            if (!RustSDK.isNativeLoaded()) {
//                Log.w("ApkUpdate", "native not loaded, skip apk update check")
//                return@launch
//            }
//
//            val result = RustSDK.checkApkUpdateSafe(this@MainActivity)
//            result.onSuccess { info ->
//                Log.i(
//                    "ApkUpdate",
//                    "check result: update=${info.update}, force=${info.force}, " +
//                            "remoteVersionCode=${info.versionCode}, versionName=${info.versionName}"
//                )
//                if (info.update) {
//                    withContext(Dispatchers.Main) {
//                        apkUpdateInfo = info
//                        apkErrorText = null
//                        showApkUpdateDialog = true
//                    }
//                }
//            }.onFailure { e ->
//                Log.w("ApkUpdate", "checkApkUpdateSafe failed: ${e.message}", e)
//            }
//        }
//    }
//

    private lateinit var requestInstallPermissionLauncher: ActivityResultLauncher<Intent>

    private fun initializeActivityResultLauncher() {
        requestInstallPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                // Settings 页面返回的 resultCode 在不同 ROM 上不可靠，按真实权限状态判断
                Log.i("ApkUpdate", "permission resultCode=${result.resultCode}")
                val canInstall = packageManager.canRequestPackageInstalls()
                Log.i("ApkUpdate", "canRequestPackageInstalls after permission=$canInstall")
                if (canInstall) {
                    pendingInstallAction?.invoke()
                    pendingInstallAction = null
                } else {
                    Toast.makeText(this, "未授权安装权限，安装失败", Toast.LENGTH_SHORT).show()
                }
            }
    }
    private var pendingInstallAction: (() -> Unit)? = null

    // 3. 处理安装权限请求
    private fun ensureInstallPermissionThen(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            action()
            return
        }

        val canInstall = packageManager.canRequestPackageInstalls()
        Log.i("ApkUpdate", "canRequestPackageInstalls=$canInstall")

        if (canInstall) {
            action()
            return
        }

        // 保存待执行的action
        pendingInstallAction = action

        // 请求权限
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:$packageName")
        }

        // 使用 ActivityResultLauncher 启动设置页面
        requestInstallPermissionLauncher.launch(intent)
    }

    // Update download logic moved into MainViewModel

    private fun installApk(apkFile: File) {
        Log.i("ApkUpdate", "installApk called, file=${apkFile.absolutePath}")

        ApkVerifier.verifyBeforeInstall(apkFile)?.let { error ->
            Log.w("ApkUpdate", "blocked APK install: $error")
            mainViewModel.reportApkInstallError(error)
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            apkFile.delete()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.i("ApkUpdate", "install intent started")
        } catch (e: Exception) {
            Log.e("ApkUpdate", "start install activity failed", e)
            val message = "无法打开系统安装器，请稍后重试"
            mainViewModel.reportApkInstallError(message)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
    private fun startLocalService(): Boolean {
        if (!RustSDK.isNativeLoaded()) {
            Log.w("MainActivity", "Native library not loaded, skipping local service start")
            return false
        }

        try {
            val storagePath = File(filesDir, "rust-sdk").absolutePath
            val seedCookies = SessionStore.rustCookies()
            var usedStorageStartup = true
            val result = try {
                RustSDK.startServerWithStorage(0, storagePath, seedCookies)
            } catch (e: UnsatisfiedLinkError) {
                Log.w("MainActivity", "startServerWithStorage missing, fallback to startServer", e)
                usedStorageStartup = false
                RustSDK.startServer(0)
            }
            Log.i("MainActivity", "Local service startup completed (credentials suppressed)")

            if (result.contains("\"error\"")) {
                Log.e("MainActivity", "Failed to start local server (response body suppressed)")
                return false
            }

            val json = org.json.JSONObject(result)
            val port = json.getInt("port")
            val token = json.getString("token")

            LocalServiceClient.initialize(port, token)
            Log.i("MainActivity", "Local service started on port: $port")
            return usedStorageStartup
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start local service", e)
            return false
        }
    }

    private suspend fun restoreRustCookies() {
        val cookies = SessionStore.rustCookies()
        if (cookies.isEmpty()) {
            Log.i("MainActivity", "No persisted Rust cookies to restore")
            return
        }

        val client = LocalServiceClient.getInstance()
        if (client == null) {
            Log.w("MainActivity", "Local service client missing, skip Rust cookie restore")
            return
        }

        val result = client.init(cookies)
        result
            .onSuccess {
                Log.i("MainActivity", "Restored Rust cookies: ${cookies.length} bytes")
            }
            .onFailure {
                Log.w("MainActivity", "Failed to restore Rust cookies", it)
            }
    }

    // update check moved into MainViewModel


}
