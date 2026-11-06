package com.ahu.ahutong.feedback

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Shares the same encrypted export and SAF behavior between the main host and error entry. */
@Composable
internal fun rememberFeedbackLogActions(viewModel: FeedbackViewModel): Pair<FeedbackLogActions, () -> Unit> {
    val context = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity
    val actions = remember(context, activity, viewModel) {
        FeedbackLogActions(context, viewModel, activity.lifecycleScope)
    }
    val exportDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
        actions::onDocumentResult
    )
    return actions to { actions.exportLogs { exportDocument.launch(it) } }
}

internal class FeedbackLogActions(
    private val context: Context,
    private val feedbackViewModel: FeedbackViewModel,
    private val scope: CoroutineScope
) {
    fun onDocumentResult(uri: Uri?) {
        val export = feedbackViewModel.pendingExport
        feedbackViewModel.pendingExport = null
        if (uri != null) {
            if (export == null || !feedbackViewModel.isExportCurrent(export)) {
                discardExpiredDocument(uri)
            } else saveExport(uri, export)
        }
    }

    fun exportLogs(launchDocument: (String) -> Unit) {
        scope.launch {
            val export = feedbackViewModel.createEncryptedExport() ?: return@launch
            if (!feedbackViewModel.isExportCurrent(export)) {
                notice(EXPIRED_EXPORT)
                return@launch
            }
            feedbackViewModel.pendingExport = export
            runCatching { launchDocument(export.filename) }.onFailure {
                feedbackViewModel.pendingExport = null
                notice("无法打开文件保存位置，请重试")
            }
        }
    }

    private fun saveExport(uri: Uri, export: EncryptedFeedbackExport) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!feedbackViewModel.isExportCurrent(export)) {
                    deleteDocument(uri)
                    return@withContext ExportSaveResult.Expired
                }
                val saved = runCatching {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
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
        scope.launch(Dispatchers.IO) { deleteDocument(uri) }
    }

    private fun deleteDocument(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    fun shareLogs() {
        scope.launch {
            val export = feedbackViewModel.createEncryptedExport() ?: return@launch
            if (!feedbackViewModel.isExportCurrent(export)) {
                notice(EXPIRED_EXPORT)
                return@launch
            }
            val staged = withContext(Dispatchers.IO) {
                if (!feedbackViewModel.isExportCurrent(export)) return@withContext null
                var destination: File? = null
                runCatching {
                    val directory = File(context.cacheDir, "feedback-exports").canonicalFile
                    check(directory.parentFile == context.cacheDir.canonicalFile)
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
                    PreparedShare(file, FileProvider.getUriForFile(context,
                        "${context.packageName}.fileprovider", file))
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
                clipData = ClipData.newUri(context.contentResolver, "AHUTong 加密日志", staged.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                check(feedbackViewModel.isExportCurrent(export))
                context.startActivity(Intent.createChooser(share, "分享加密日志"))
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
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val EXPIRED_EXPORT = "日志已失效，请重新导出"
    }
}

private const val FEEDBACK_GROUP = "1006203134"

internal fun openFeedbackGroup(context: Context) {
    val opened = runCatching {
        context.startActivity(Intent(
            Intent.ACTION_VIEW,
            "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$FEEDBACK_GROUP&card_type=group&source=qrcode".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }.isSuccess
    if (!opened) {
        val copied = runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AHUTong 反馈群", FEEDBACK_GROUP))
        }.isSuccess
        Toast.makeText(context,
            "请在 QQ 或 TIM 搜索群号 $FEEDBACK_GROUP。" + if (copied) "群号已复制。" else "",
            Toast.LENGTH_LONG).show()
    }
}
