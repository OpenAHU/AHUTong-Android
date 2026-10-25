package com.ahu.ahutong.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.tencent.mmkv.MMKV
import java.io.IOException

internal fun Throwable.isStoragePermissionFailure(): Boolean =
    generateSequence(this) { it.cause }.any {
        it is SecurityException || it is android.system.ErrnoException &&
            (it.errno == android.system.OsConstants.EACCES || it.errno == android.system.OsConstants.EPERM)
    }

internal inline fun <T> withRepositoryStorageAccess(block: () -> T): T = try {
    block()
} catch (error: Exception) {
    if (error is SecurityException) throw error
    if (error.isStoragePermissionFailure()) {
        throw SecurityException("文件夹访问权限已失效，请重新授权", error)
    }
    throw error
}

/** MediaStore 无法访问时使用用户明确授权的目录，沿用下载记录的 MMKV 存储。 */
internal object RepositoryDownloadDirectory {
    private const val DIRECTORY_URI_KEY = "download_directory_uri"
    private val permissions = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    private val kv: MMKV get() = MMKV.mmkvWithID("repository_downloads")

    fun selectedUri(): Uri? = kv.decodeString(DIRECTORY_URI_KEY)?.let(Uri::parse)

    fun remember(context: Context, uri: Uri) {
        require(uri.scheme == "content" && DocumentsContract.isTreeUri(uri)) { "请选择一个文件夹" }
        context.contentResolver.takePersistableUriPermission(uri, permissions)
        requireWritable(context, uri)
        kv.encode(DIRECTORY_URI_KEY, uri.toString())
    }

    fun checkDownloadAccess(context: Context) {
        val tree = selectedUri()
        if (tree != null) {
            requireWritable(context, tree)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            // 不再申请整个共享存储权限；交由系统目录选择器授予所需子目录。
            throw SecurityException("下载目录尚未授权，请选择一个下载子文件夹")
        }
    }

    fun createFile(context: Context, relativePath: String): Pair<Uri, String> {
        val tree = selectedUri() ?: throw SecurityException("请先授权学习资料下载目录")
        var directory = requireWritable(context, tree)
        val segments = relativePath.split('/').filter(String::isNotEmpty)
        for (name in segments.dropLast(1)) {
            val child = findChild(context, tree, directory, name)
            directory = child ?: DocumentsContract.createDocument(
                context.contentResolver, directory, DocumentsContract.Document.MIME_TYPE_DIR, name
            ) ?: throw IOException("无法创建下载子目录")
            requireDirectory(context, directory)
        }
        val uri = DocumentsContract.createDocument(
            context.contentResolver, directory, "application/octet-stream", segments.last()
        ) ?: throw IOException("无法在所选目录中创建文件")
        try {
            // 文档提供器可能为重名文件追加序号，恢复授权时必须查找实际保存的名称。
            val displayName = context.contentResolver.query(
                uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?.takeIf { it.isNotEmpty() && it != "." && it != ".." && '/' !in it }
                ?: throw IOException("无法读取新建文件的名称")
            return uri to (segments.dropLast(1) + displayName).joinToString("/")
        } catch (error: Exception) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    fun findFile(context: Context, relativePath: String): Uri? {
        val tree = selectedUri() ?: return null
        var current = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        for (name in relativePath.split('/').filter(String::isNotEmpty)) {
            current = findChild(context, tree, current, name) ?: return null
        }
        // 不能把同名目录绑定为下载文件，否则删除下载时会递归删除该目录。
        return context.contentResolver.query(
            current, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0) &&
                cursor.getString(0) != DocumentsContract.Document.MIME_TYPE_DIR
            ) current else null
        }
    }

    private fun requireWritable(context: Context, tree: Uri): Uri {
        val persisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
        if (!persisted) throw SecurityException("下载目录授权已失效，请重新选择原目录")
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        requireDirectory(context, root)
        return root
    }

    private fun requireDirectory(context: Context, uri: Uri) {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst() &&
                cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR &&
                (cursor.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0
            ) return
        }
        throw SecurityException("所选文件夹不可读写，请选择 Download 下的子文件夹")
    }

    private fun findChild(context: Context, tree: Uri, parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
            return null
        }
        throw IOException("无法读取所选文件夹，请稍后重试")
    }
}
