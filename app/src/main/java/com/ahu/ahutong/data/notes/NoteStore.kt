package com.ahu.ahutong.data.notes

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import com.ahu.ahutong.data.session.SessionStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 笔记相机的本地存储：科目 + 照片元数据。
 *
 * 目录按账号隔离（`filesDir/notes/<学号|local>/<subjectId>/`），换账号看不到别人的笔记；
 * 元数据是两份 JSON，写入走"临时文件 + rename"的原子替换，避免进程被杀留下半个文件。
 * 所有入口都是 `@Synchronized` 的同步调用，调用方负责放到 IO 线程。
 */
object NoteStore {
    private const val ROOT = "notes"
    private val gson = Gson()
    private val subjectListType = object : TypeToken<List<NoteSubject>>() {}.type
    private val photoListType = object : TypeToken<List<NotePhoto>>() {}.type

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        rootDir().mkdirs()
    }

    private fun accountKey(): String =
        SessionStore.currentUser()?.xh?.takeIf { it.isNotBlank() } ?: "local"

    private fun accountDir(): File =
        File(File(appContext.filesDir, ROOT), accountKey()).apply { mkdirs() }

    private fun rootDir(): File = accountDir()

    private fun subjectsFile(): File = File(accountDir(), "note_subjects.json")
    private fun photosFile(): File = File(accountDir(), "note_photos.json")

    @Synchronized
    fun subjects(): List<NoteSubject> {
        val stored = readSubjects().sortedBy { it.order }
        return if (stored.any { it.id == UNFILED_SUBJECT_ID }) stored
        else stored + NoteSubject(UNFILED_SUBJECT_ID, UNFILED_SUBJECT_NAME, 0L, Int.MAX_VALUE)
    }

    @Synchronized
    fun subject(id: String): NoteSubject? = subjects().firstOrNull { it.id == id }

    /** 同名科目复用；课程名自动建科目就走这里。"未归档"不允许被业务创建。 */
    @Synchronized
    fun ensureSubject(name: String): NoteSubject {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == UNFILED_SUBJECT_NAME) return unfiledSubject()
        val existing = readSubjects().firstOrNull { it.name == trimmed }
        if (existing != null) return existing
        val list = readSubjects()
        val subject = NoteSubject(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            order = (list.maxOfOrNull { it.order } ?: 0) + 1
        )
        writeSubjects(list + subject)
        return subject
    }

    @Synchronized
    fun unfiledSubject(): NoteSubject {
        val stored = readSubjects().firstOrNull { it.id == UNFILED_SUBJECT_ID }
        return stored ?: NoteSubject(UNFILED_SUBJECT_ID, UNFILED_SUBJECT_NAME, 0L, Int.MAX_VALUE)
    }

    @Synchronized
    fun renameSubject(id: String, newName: String): Boolean {
        if (id == UNFILED_SUBJECT_ID) return false
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == UNFILED_SUBJECT_NAME) return false
        val list = readSubjects()
        if (list.any { it.id != id && it.name == trimmed }) return false
        if (list.none { it.id == id }) return false
        writeSubjects(list.map { if (it.id == id) it.copy(name = trimmed) else it })
        return true
    }

    /** 删科目：照片统一落到"未归档"，不删文件。 */
    @Synchronized
    fun deleteSubject(id: String) {
        if (id == UNFILED_SUBJECT_ID) return
        // Keep all paths in the same account even if the session changes during file IO.
        val account = accountDir()
        val subjectsIndex = File(account, "note_subjects.json")
        val photosIndex = File(account, "note_photos.json")
        val subjects = read<NoteSubject>(subjectsIndex, subjectListType)
        if (subjects.none { it.id == id }) return
        val photos = read<NotePhoto>(photosIndex, photoListType)
        val sourceDirectory = File(account, id)
        val copies = mutableListOf<Pair<NotePhoto, File>>()
        var photoIndexChanged = false
        try {
            val updated = photos.map { photo ->
                if (photo.subjectId != id) photo else {
                    val source = File(sourceDirectory, photo.fileName)
                    if (!source.isFile) throw IOException("笔记照片不存在，未删除科目")
                    val targetDirectory = File(account, UNFILED_SUBJECT_ID)
                    if (!targetDirectory.isDirectory && !targetDirectory.mkdirs()) {
                        throw IOException("无法访问未归档文件夹，未删除科目")
                    }
                    val destination = reservePhotoFile(targetDirectory, photo.fileName)
                    copies += photo to destination
                    source.inputStream().use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (source.length() != destination.length()) {
                        throw IOException("笔记照片复制不完整，未删除科目")
                    }
                    photo.copy(
                        subjectId = UNFILED_SUBJECT_ID,
                        fileName = destination.name,
                        source = NoteSource.UNFILED
                    )
                }
            }
            if (copies.isNotEmpty()) {
                write(photosIndex, gson.toJson(updated, photoListType))
                photoIndexChanged = true
            }
            write(subjectsIndex, gson.toJson(subjects.filter { it.id != id }, subjectListType))
        } catch (failure: Exception) {
            val restored = !photoIndexChanged || runCatching {
                write(photosIndex, gson.toJson(photos, photoListType))
            }.onFailure { failure.addSuppressed(it) }.isSuccess
            // If rollback itself fails, retain both copies so either index still points to valid bytes.
            if (restored) copies.forEach { (_, destination) -> runCatching { destination.delete() } }
            throw failure
        }
        // Only remove known originals after both indexes are saved; unregistered files are preserved.
        copies.forEach { (photo, _) -> runCatching { File(sourceDirectory, photo.fileName).delete() } }
        runCatching { sourceDirectory.delete() }
    }

    private fun reservePhotoFile(directory: File, originalName: String): File {
        var candidate = File(directory, originalName)
        while (!candidate.createNewFile()) {
            val original = File(originalName)
            val suffix = original.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
            candidate = File(directory, "${original.nameWithoutExtension}_${UUID.randomUUID()}$suffix")
        }
        return candidate
    }

    fun subjectDir(subjectId: String): File =
        File(accountDir(), subjectId).apply { mkdirs() }

    fun photoFile(photo: NotePhoto): File = File(subjectDir(photo.subjectId), photo.fileName)

    @Synchronized
    fun photos(subjectId: String? = null): List<NotePhoto> {
        val all = readPhotos().sortedByDescending { it.takenAt }
        return if (subjectId == null) all else all.filter { it.subjectId == subjectId }
    }

    /**
     * 登记一张已经落盘的照片。
     *
     * 调用方负责把 JPEG 写进 [subjectDir]；这里只补元数据，同时校验文件存在。
     */
    @Synchronized
    fun addPhoto(
        file: File,
        subjectId: String,
        source: NoteSource,
        courseName: String? = null,
        window: IntRange? = null
    ): NotePhoto? {
        if (!file.exists() || file.length() <= 0L) return null
        val photo = NotePhoto(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            fileName = file.name,
            takenAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
            sizeBytes = file.length(),
            source = source,
            courseName = courseName,
            windowStart = window?.first,
            windowEnd = window?.last
        )
        writePhotos(readPhotos() + photo)
        return photo
    }

    /** 改归档：物理移动文件 + 更新元数据。 */
    @Synchronized
    fun movePhotos(ids: Set<String>, targetSubjectId: String): Int {
        if (ids.isEmpty()) return 0
        val photos = readPhotos()
        var moved = 0
        val updated = photos.map { photo ->
            if (photo.id !in ids || photo.subjectId == targetSubjectId) photo
            else {
                val src = photoFile(photo)
                val dst = File(subjectDir(targetSubjectId), photo.fileName)
                val ok = runCatching {
                    if (src.exists()) {
                        src.copyTo(dst, overwrite = true)
                        src.delete()
                    }
                    dst.exists()
                }.getOrDefault(false)
                if (ok) {
                    moved++
                    photo.copy(subjectId = targetSubjectId, source = NoteSource.MANUAL)
                } else photo
            }
        }
        if (moved > 0) writePhotos(updated)
        return moved
    }

    @Synchronized
    fun deletePhotos(ids: Set<String>): Int {
        if (ids.isEmpty()) return 0
        val photos = readPhotos()
        val toDelete = photos.filter { it.id in ids }
        toDelete.forEach { runCatching { photoFile(it).delete() } }
        if (toDelete.isNotEmpty()) writePhotos(photos.filter { it.id !in ids })
        return toDelete.size
    }

    /** 导出到系统相册；文件名沿用随机名，不带课程信息。 */
    @Synchronized
    @WorkerThread
    fun exportToGallery(ids: Set<String>): Int {
        var ok = 0
        photos().filter { it.id in ids }.forEach { photo ->
            val file = photoFile(photo)
            if (!file.exists()) return@forEach
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, photo.fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/AHUTong"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = appContext.contentResolver
                val uri: Uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@forEach
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                ok++
            }
        }
        return ok
    }

    fun countPhotos(subjectId: String? = null): Int = photos(subjectId).size

    /** 新照片文件名：时间戳 + 随机后缀，便于排序又不泄露内容。 */
    fun newPhotoFileName(now: Long = System.currentTimeMillis()): String =
        "${now}_${UUID.randomUUID().toString().take(4)}.jpg"

    private fun readSubjects(): List<NoteSubject> = read(subjectsFile(), subjectListType)
    private fun readPhotos(): List<NotePhoto> = read(photosFile(), photoListType)
    private fun writeSubjects(list: List<NoteSubject>) = write(subjectsFile(), gson.toJson(list, subjectListType))
    private fun writePhotos(list: List<NotePhoto>) = write(photosFile(), gson.toJson(list, photoListType))

    private fun <T> read(file: File, type: java.lang.reflect.Type): List<T> = try {
        if (!file.exists()) emptyList() else gson.fromJson<List<T>>(file.readText().removePrefix("\uFEFF"), type)
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun write(file: File, json: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("无法保存笔记索引")
        }
    }
}
