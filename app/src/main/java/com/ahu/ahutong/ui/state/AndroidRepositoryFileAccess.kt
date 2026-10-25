package com.ahu.ahutong.ui.state

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.repository.RepositoryManager
import com.ahu.ahutong.data.repository.DownloadedFile
import com.ahu.ahutong.data.repository.isStoragePermissionFailure
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 检查本地可读性后再读取/共享文件；失权与文件删除分别返回，不清除下载记录。 */
@Singleton
class AndroidRepositoryFileAccess @Inject constructor() : RepositoryFileAccess {

    private val context: Context get() = AppEnvironmentHolder.context()

    override fun downloadDirectoryUri(): String? = RepositoryManager.downloadDirectoryUri()

    override fun rememberDownloadDirectory(uri: String) {
        RepositoryManager.rememberDownloadDirectory(context, uri)
    }

    override fun recoverDownloadedFile(file: DownloadedFile): DownloadedFile? =
        if (availability(file) == Availability.READABLE) file
        else RepositoryManager.recoverDownloadedFile(file.path, context)

    override fun read(file: DownloadedFile): FileReadOutcome {
        when (availability(file)) {
            Availability.MISSING -> return FileReadOutcome.Missing
            Availability.PERMISSION_REQUIRED -> return FileReadOutcome.PermissionRequired
            Availability.UNAVAILABLE -> return FileReadOutcome.Failed("文件暂不可读，请稍后重试")
            Availability.READABLE -> Unit
        }
        return try {
            val text = file.uri?.let { value ->
                context.contentResolver.openInputStream(Uri.parse(value))?.bufferedReader()?.use { it.readText() }
            } ?: if (file.uri == null) File(file.localPath).readText() else null
            text?.let { FileReadOutcome.Text(it) } ?: FileReadOutcome.Failed("无法读取本地 Markdown")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            FileReadOutcome.PermissionRequired
        } catch (error: Exception) {
            if (error.isStoragePermissionFailure()) return FileReadOutcome.PermissionRequired
            Log.e("RepoViewer", "读取失败", error)
            FileReadOutcome.Failed(error.message)
        }
    }

    override fun open(file: DownloadedFile): FileOpenOutcome {
        when (availability(file)) {
            Availability.MISSING -> return FileOpenOutcome.Missing
            Availability.PERMISSION_REQUIRED -> return FileOpenOutcome.PermissionRequired
            Availability.UNAVAILABLE -> return FileOpenOutcome.Failed("文件暂不可读，请稍后重试")
            Availability.READABLE -> Unit
        }
        val uri = file.uri?.let(Uri::parse) ?: try {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(file.localPath))
        } catch (_: SecurityException) {
            return FileOpenOutcome.PermissionRequired
        } catch (error: Exception) {
            return FileOpenOutcome.Failed(error.message)
        }
        return startViewer(uri, file.name)
    }

    private enum class Availability { READABLE, MISSING, PERMISSION_REQUIRED, UNAVAILABLE }

    private fun availability(file: DownloadedFile): Availability {
        val uri = file.uri?.let(Uri::parse)
        if (uri == null) {
            val local = File(file.localPath)
            return when {
                local.exists() && local.canRead() -> Availability.READABLE
                local.exists() || local.parentFile?.canRead() != true -> Availability.PERMISSION_REQUIRED
                else -> Availability.MISSING
            }
        }
        if (DocumentsContract.isTreeUri(uri)) {
            val tree = DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri))
            if (context.contentResolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) {
                return Availability.PERMISSION_REQUIRED
            }
        }
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { Availability.READABLE }
                ?: Availability.UNAVAILABLE
        } catch (_: SecurityException) {
            Availability.PERMISSION_REQUIRED
        } catch (error: FileNotFoundException) {
            if (error.isStoragePermissionFailure()) return Availability.PERMISSION_REQUIRED
            // 元数据仍在但内容暂不可读（例如离线文档提供器），不能据此删除记录。
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) Availability.UNAVAILABLE else Availability.MISSING
                } ?: Availability.UNAVAILABLE
            } catch (_: SecurityException) {
                Availability.PERMISSION_REQUIRED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error.isStoragePermissionFailure()) Availability.PERMISSION_REQUIRED else Availability.UNAVAILABLE
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error.isStoragePermissionFailure()) Availability.PERMISSION_REQUIRED else Availability.UNAVAILABLE
        }
    }

    private fun startViewer(uri: Uri, fileName: String): FileOpenOutcome {
        return try {
            val mimeType = RepositoryViewModel.getMimeType(fileName)
            val opened = startFileViewer(uri, fileName, mimeType) ||
                (mimeType != "*/*" && startFileViewer(uri, fileName, "*/*"))
            if (opened) FileOpenOutcome.Opened else FileOpenOutcome.NoViewerApp
        } catch (_: SecurityException) {
            FileOpenOutcome.PermissionRequired
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e("RepoViewer", "打开失败: " + e.message, e)
            FileOpenOutcome.Failed(e.message)
        }
    }

    private fun startFileViewer(uri: Uri, fileName: String, mimeType: String): Boolean {
        val clipData = ClipData.newUri(context.contentResolver, fileName, uri)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addCategory(Intent.CATEGORY_DEFAULT)
            this.clipData = clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(viewIntent, "选择打开方式").apply {
            this.clipData = clipData
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(chooserIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
