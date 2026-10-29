package com.ahu.ahutong

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.ahu.ahutong.feedback.EncryptedFeedbackExport
import com.ahu.ahutong.feedback.FeedbackViewModel
import com.ahu.ahutong.ui.screen.settings.FeedbackScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackHubScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackExportScreen
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A main-process host shared by settings and error dialogs, including isolated activities. */
@AndroidEntryPoint
class FeedbackActivity : ComponentActivity() {
    private val feedbackViewModel: FeedbackViewModel by viewModels()
    private val preferencesViewModel: PreferencesViewModel by viewModels()
    private val exportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val export = feedbackViewModel.pendingExport
        feedbackViewModel.pendingExport = null
        if (uri != null) {
            if (export == null || !feedbackViewModel.isExportCurrent(export)) {
                discardExpiredDocument(uri)
            } else saveExport(uri, export)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        feedbackViewModel.attachError(
            intent.getStringExtra(EXTRA_ERROR_DETAILS),
            intent.getStringExtra(EXTRA_EVENT_ID)
        )
        val initialRoute = if (intent.getStringExtra(EXTRA_ERROR_DETAILS).isNullOrBlank()) "hub" else "feedback"
        setContent {
            val uiTheme by preferencesViewModel.appUiTheme.collectAsState()
            val themeColor by preferencesViewModel.themeColor.collectAsState()
            val themeMode by preferencesViewModel.appThemeMode.collectAsState()
            val themeReady by preferencesViewModel.isUiThemePreferenceReady.collectAsState()
            val overrides by preferencesViewModel.componentSlotOverrides.collectAsState()
            val state by feedbackViewModel.state.collectAsState()
            var route by rememberSaveable { mutableStateOf(initialRoute) }
            val navigateBack = {
                if (route == "hub") finish() else {
                    feedbackViewModel.dismissPreview()
                    route = "hub"
                }
            }
            BackHandler(onBack = navigateBack)
            AHUTheme(AhuThemeConfig(
                appUiTheme = uiTheme,
                themeColorHex = themeColor,
                themeMode = themeMode,
                isPreferenceReady = themeReady,
                componentSlotOverrides = overrides
            )) {
                AnimatedContent(
                    targetState = route,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        // Match the secondary forward/pop motion in utils.animatedComposable.
                        if (targetState == "hub") {
                            (fadeIn(tween(180)) + slideInHorizontally(
                                animationSpec = tween(260), initialOffsetX = { -it / 12 }
                            )) togetherWith (fadeOut(tween(160)) + slideOutHorizontally(
                                animationSpec = tween(260), targetOffsetX = { it / 4 }
                            ))
                        } else {
                            (fadeIn(tween(180)) + slideInHorizontally(
                                animationSpec = tween(240), initialOffsetX = { it / 4 }
                            )) togetherWith (fadeOut(tween(140)) + slideOutHorizontally(
                                animationSpec = tween(220), targetOffsetX = { -it / 12 }
                            ))
                        }
                    },
                    label = "Feedback navigation"
                ) { visibleRoute ->
                    when (visibleRoute) {
                        "feedback" -> FeedbackScreen(
                            state = state,
                            onDescriptionChange = feedbackViewModel::setDescription,
                            onCategoryChange = feedbackViewModel::setCategory,
                            onPreview = feedbackViewModel::showPreview,
                            onDismissPreview = feedbackViewModel::dismissPreview,
                            onSubmit = feedbackViewModel::submit,
                            onBack = navigateBack
                        )
                        "export" -> FeedbackExportScreen(
                            state = state,
                            onCategoryChange = feedbackViewModel::setCategory,
                            onExport = ::exportLogs,
                            onShare = ::shareLogs,
                            onBack = navigateBack
                        )
                        else -> FeedbackHubScreen(
                            onFeedback = { route = "feedback" },
                            onExport = {
                                feedbackViewModel.dismissPreview()
                                feedbackViewModel.reportExportStatus("")
                                route = "export"
                            },
                            onOpenQq = ::openFeedbackGroup,
                            onBack = navigateBack
                        )
                    }
                }
            }
        }
    }

    private fun exportLogs() {
        lifecycleScope.launch {
            val export = feedbackViewModel.createEncryptedExport() ?: return@launch
            if (!feedbackViewModel.isExportCurrent(export)) {
                notice(EXPIRED_EXPORT)
                return@launch
            }
            feedbackViewModel.pendingExport = export
            runCatching { exportDocument.launch(export.filename) }.onFailure {
                feedbackViewModel.pendingExport = null
                notice("无法打开文件保存位置，请重试")
            }
        }
    }

    private fun saveExport(uri: Uri, export: EncryptedFeedbackExport) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!feedbackViewModel.isExportCurrent(export)) {
                    deleteDocument(uri)
                    return@withContext ExportSaveResult.Expired
                }
                val saved = runCatching {
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")) {
                        "Document provider did not return an output stream"
                    }.use {
                        check(feedbackViewModel.isExportCurrent(export))
                        it.write(export.bytes)
                    }
                }.isSuccess
                if (!feedbackViewModel.isExportCurrent(export)) {
                    deleteDocument(uri)
                    ExportSaveResult.Expired
                } else if (saved) ExportSaveResult.Saved else ExportSaveResult.Failed
            }
            notice(when (result) {
                ExportSaveResult.Saved -> "加密日志已保存"
                ExportSaveResult.Expired -> EXPIRED_EXPORT
                ExportSaveResult.Failed -> "保存失败，请重新选择保存位置"
            })
        }
    }

    private fun discardExpiredDocument(uri: Uri) {
        notice(EXPIRED_EXPORT)
        lifecycleScope.launch(Dispatchers.IO) { deleteDocument(uri) }
    }

    private fun deleteDocument(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
    }

    private fun shareLogs() {
        lifecycleScope.launch {
            val export = feedbackViewModel.createEncryptedExport() ?: return@launch
            if (!feedbackViewModel.isExportCurrent(export)) {
                notice(EXPIRED_EXPORT)
                return@launch
            }
            val staged = withContext(Dispatchers.IO) {
                if (!feedbackViewModel.isExportCurrent(export)) return@withContext null
                var destination: File? = null
                runCatching {
                    val directory = File(cacheDir, "feedback-exports").canonicalFile
                    check(directory.parentFile == cacheDir.canonicalFile)
                    check(directory.isDirectory || directory.mkdirs())
                    val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    directory.listFiles()?.filter {
                        it.isFile && it.extension == "ahulog" && it.lastModified() < expiry
                    }?.forEach { it.delete() }
                    val filename = export.filename.removeSuffix(".ahulog") + "-${UUID.randomUUID()}.ahulog"
                    val file = File(directory, filename).canonicalFile
                    check(file.parentFile == directory)
                    destination = file
                    check(feedbackViewModel.isExportCurrent(export))
                    file.outputStream().use {
                        check(feedbackViewModel.isExportCurrent(export))
                        it.write(export.bytes)
                    }
                    check(feedbackViewModel.isExportCurrent(export))
                    PreparedShare(file, FileProvider.getUriForFile(this@FeedbackActivity,
                        "$packageName.fileprovider", file))
                }.onFailure { destination?.delete() }.getOrNull()
            }
            if (staged == null) {
                notice(if (feedbackViewModel.isExportCurrent(export)) "无法准备加密日志，请重试" else EXPIRED_EXPORT)
                return@launch
            }
            if (!feedbackViewModel.isExportCurrent(export)) {
                withContext(Dispatchers.IO) { staged.file.delete() }
                notice(EXPIRED_EXPORT)
                return@launch
            }
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, staged.uri)
                clipData = ClipData.newUri(contentResolver, "AHUTong 加密日志", staged.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                check(feedbackViewModel.isExportCurrent(export))
                startActivity(Intent.createChooser(share, "分享加密日志"))
            }.onFailure {
                notice(if (feedbackViewModel.isExportCurrent(export))
                    "没有可用的分享应用，请使用导出日志" else EXPIRED_EXPORT)
            }
        }
    }

    private enum class ExportSaveResult { Saved, Expired, Failed }
    private data class PreparedShare(val file: File, val uri: Uri)

    private fun notice(message: String) {
        feedbackViewModel.reportExportStatus(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openFeedbackGroup() {
        val opened = runCatching {
            startActivity(Intent(
                Intent.ACTION_VIEW,
                "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$FEEDBACK_GROUP&card_type=group&source=qrcode".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }.isSuccess
        if (!opened) {
            val copied = runCatching {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AHUTong 反馈群", FEEDBACK_GROUP))
            }.isSuccess
            Toast.makeText(this,
                "请在 QQ 或 TIM 搜索群号 $FEEDBACK_GROUP。" + if (copied) "群号已复制。" else "",
                Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_ERROR_DETAILS = "feedback_error_details"
        const val EXTRA_EVENT_ID = "feedback_event_id"
        private const val FEEDBACK_GROUP = "1006203134"
        private const val EXPIRED_EXPORT = "日志已失效，请重新导出"

        fun intent(context: Context, details: String? = null, eventId: String? = null): Intent =
            Intent(context, FeedbackActivity::class.java).apply {
                details?.let { putExtra(EXTRA_ERROR_DETAILS, it) }
                eventId?.let { putExtra(EXTRA_EVENT_ID, it) }
            }
    }
}
