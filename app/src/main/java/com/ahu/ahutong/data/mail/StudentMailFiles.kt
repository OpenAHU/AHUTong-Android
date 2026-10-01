package com.ahu.ahutong.data.mail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Only accesses files picked through SAF; temporary upload copies remain in app-private cache. */
internal object StudentMailFiles {
    const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024

    suspend fun prepare(context: Context, uri: Uri): MailUploadFile {
        var staged: File? = null
        return try { withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var name = "attachment"
            var declaredSize = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
                }
            }
            if (declaredSize > MAX_UPLOAD_BYTES) throw MailServiceFailure("原生邮箱支持单个附件不超过 50 MB，请使用邮箱完整版处理更大的文件")
            clearStaleUploads(context)
            val directory = File(context.cacheDir, "student-mail-upload").apply { mkdirs() }
            val file = File.createTempFile("upload-", ".tmp", directory).also { staged = it }
            try {
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_UPLOAD_BYTES) throw MailServiceFailure("原生邮箱支持单个附件不超过 50 MB")
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw MailServiceFailure("无法读取所选文件，请重新选择")
                if (file.length() == 0L) throw MailServiceFailure("请选择有内容的附件")
                MailUploadFile(file, mailFileName(name), resolver.getType(uri) ?: "application/octet-stream")
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
        } } catch (error: Throwable) {
            // Covers cancellation while dispatching the completed file back to the caller as well.
            staged?.delete()
            throw error
        }
    }

    private fun clearStaleUploads(context: Context) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        File(context.cacheDir, "student-mail-upload").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}

internal class MailUploadFile(val file: File, val name: String, val mimeType: String)

internal fun mailFileName(value: String): String = value.substringAfterLast('/').substringAfterLast('\\')
    .replace(Regex("[\\p{Cntrl}]"), "").trim().take(160).ifBlank { "attachment" }
