package com.ahu.ahutong.cli

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Short-lived file exchange. Never accepts a path or an arbitrary content URI from the caller. */
internal object CliFiles {
    private data class Entry(val file: File, val owner: Long, val expiresAt: Long, val upload: Boolean, val sealed: Boolean = false)
    private val entries = mutableMapOf<String, Entry>()
    private const val MAX_BYTES = 50L * 1024 * 1024

    @Synchronized
    fun create(context: Context, upload: Boolean = false): Pair<String, File> {
        val now = System.currentTimeMillis()
        entries.entries.removeAll { (_, value) ->
            (value.expiresAt < now).also { if (it) value.file.delete() }
        }
        require(entries.size < 16) { "Too many pending files" }
        val directory = File(context.cacheDir, "cli-exchange").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile && it.lastModified() < now - 600_000 }
            ?.forEach { it.delete() }
        val id = UUID.randomUUID().toString()
        val file = File(directory, id)
        entries[id] = Entry(file, SessionRefreshCoordinator.currentIdentityGeneration(), now + 600_000, upload)
        return id to file
    }

    @Synchronized
    private fun entry(id: String): Entry = requireNotNull(entries[id]).also {
        require(it.expiresAt > System.currentTimeMillis() && it.owner == SessionRefreshCoordinator.currentIdentityGeneration())
    }

    @Synchronized
    fun open(id: String, mode: String): ParcelFileDescriptor {
        val item = entry(id)
        val writable = item.upload && !item.sealed
        require(if (writable) mode == "w" else mode == "r")
        return ParcelFileDescriptor.open(item.file, if (writable)
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE
        else ParcelFileDescriptor.MODE_READ_ONLY)
    }

    @Synchronized
    fun info(context: Context, id: String): Map<String, Any> {
        val item = entry(id)
        require(item.file.length() > 0 && (!item.upload || item.file.length() <= MAX_BYTES))
        if (item.upload) entries[id] = item.copy(sealed = true)
        return mapOf("id" to id, "uri" to "content://${context.packageName}.cli/file/$id",
            "bytes" to item.file.length(), "sha256" to sha256(item.file))
    }

    @Synchronized
    fun upload(context: Context): Map<String, String> {
        val (id, _) = create(context, upload = true)
        return mapOf("id" to id, "uri" to "content://${context.packageName}.cli/file/$id")
    }

    @Synchronized
    fun uploaded(id: String): File = entry(id).also {
        require(it.upload && it.sealed && it.file.length() in 1..MAX_BYTES)
    }.file

    @Synchronized
    fun discard(id: String) {
        entry(id)
        entries.remove(id)?.file?.delete()
    }

    private fun sha256(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(65536)
            while (true) { val n = stream.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}
