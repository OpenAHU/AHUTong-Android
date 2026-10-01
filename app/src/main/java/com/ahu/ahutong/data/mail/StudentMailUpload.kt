package com.ahu.ahutong.data.mail

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

/** Implements the ordinary prepare/directData upload observed in the mailbox HAR. */
internal class StudentMailUploader(
    client: OkHttpClient,
    private val sid: String,
    private val guard: () -> Unit,
    private val baseUrl: HttpUrl = "https://mail.stu.ahu.edu.cn/".toHttpUrl(),
    private val deviceId: String = UUID.randomUUID().toString()
) {
    constructor(session: MailSession) : this(session.client, session.sid, session::assertCurrent)

    private val client = client.newBuilder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .readTimeout(3, TimeUnit.MINUTES)
        .writeTimeout(3, TimeUnit.MINUTES)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    suspend fun upload(
        composeId: String,
        file: File,
        name: String,
        contentType: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): MailDraftAttachment = withContext(Dispatchers.IO) {
        guard()
        if (composeId.isBlank() || !file.isFile || name.isBlank()) {
            throw MailServiceFailure("附件文件不可用，请重新选择")
        }
        val size = file.length()
        val mime = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()!!
        val preparation = JsonObject().apply {
            addProperty("attachmentId", -1)
            addProperty("composeId", composeId)
            addProperty("contentType", mime.toString())
            addProperty("fileName", name)
            addProperty("offset", 0)
            addProperty("size", size)
        }
        val prepared = request("upload:prepare", emptyMap(), oneShot(
            preparation.toString().toRequestBody("application/json;charset=UTF-8".toMediaTypeOrNull())
        ))
        val attachmentId = validDescriptor(prepared, composeId, size, 0)
        guard()
        onProgress(0, size)
        val uploaded = request("upload:directData", mapOf(
            "composeId" to composeId, "attachmentId" to attachmentId.toString(), "offset" to "0"
        ), MailUploadFileBody(file, mime, size, guard, onProgress))
        val completedId = validDescriptor(uploaded, composeId, size, size)
        if (completedId != attachmentId) throw invalidUpload()
        guard()
        onProgress(size, size)
        StudentMailDiagnostics.record("mail.attachment.uploaded", "complete=true")
        MailDraftAttachment(attachmentId.toString(), name, size, mime.toString())
    }

    private suspend fun request(function: String, query: Map<String, String>, body: RequestBody): JsonObject {
        guard()
        val url = baseUrl.newBuilder().addPathSegments("js6/s")
            .addQueryParameter("sid", sid)
            .addQueryParameter("func", function)
            .addQueryParameter("_host", "mail.stu.ahu.edu.cn")
            .addQueryParameter("p", "web")
            .addQueryParameter("_appName", "sirius-web")
            .addQueryParameter("_version", "1.66.2")
            .addQueryParameter("_deviceId", deviceId)
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("lingxi-language", "zh")
            .header("Origin", "https://mail.stu.ahu.edu.cn")
            .header("Referer", "https://mail.stu.ahu.edu.cn/static/sirius-web/")
            .post(body).build()
        return coroutineScope {
            val call = client.newCall(request)
            // awaitResponse covers pending headers; keep cancellation linked while consuming the acknowledgement too.
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                call.awaitResponse().use { response ->
                    if (response.code in listOf(301, 302, 303, 307, 308, 401, 403)) throw MailSessionExpired()
                    if (!response.isSuccessful) throw MailServiceFailure("附件上传失败，请重试")
                    val root = try {
                        JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                    } catch (_: Exception) { throw invalidUpload() }
                    val code = root.get("code")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    if (code.contains("SESSION", true) || code.contains("LOGIN", true) ||
                        code.contains("AUTH", true) || code.equals("S_INVALID_SID", true)) throw MailSessionExpired()
                    StudentMailProtocol.checkResponse(root)
                    guard()
                    root
                }
            } finally { cancellation.cancelAndJoin() }
        }
    }

    private fun validDescriptor(root: JsonObject, composeId: String, size: Long, actualSize: Long): Long {
        return try {
            val descriptor = root.getAsJsonObject("var") ?: throw invalidUpload()
            val id = descriptor["attachmentId"].asString.toLong()
            if (id < 0 || descriptor["composeId"].asString != composeId ||
                descriptor["size"].asString.toLong() != size || descriptor["actualSize"].asString.toLong() != actualSize) {
                throw invalidUpload()
            }
            id
        } catch (failure: MailServiceFailure) { throw failure }
        catch (_: Exception) { throw invalidUpload() }
    }

    private fun invalidUpload() = MailServiceFailure("附件上传结果未确认，请重新添加附件")
}

private fun oneShot(body: RequestBody) = object : RequestBody() {
    override fun contentType() = body.contentType()
    override fun contentLength() = body.contentLength()
    override fun isOneShot() = true
    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
}

/** The selected document is copied to a private temporary file before upload, never buffered in memory. */
internal class MailUploadFileBody(
    private val file: File,
    private val mime: MediaType,
    private val size: Long,
    private val guard: () -> Unit,
    private val onProgress: (Long, Long) -> Unit
) : RequestBody() {
    override fun contentType() = mime
    override fun contentLength() = size
    override fun isOneShot() = true
    override fun writeTo(sink: BufferedSink) {
        guard()
        if (!file.isFile || file.length() != size) throw IOException("附件文件已变化，请重新选择")
        var written = 0L
        val buffer = ByteArray(32 * 1024)
        file.inputStream().use { source ->
            while (true) {
                guard()
                val count = source.read(buffer)
                if (count < 0) break
                if (written + count > size) throw IOException("附件文件已变化，请重新选择")
                sink.write(buffer, 0, count)
                written += count
                onProgress(written, size)
            }
        }
        if (written != size) throw IOException("附件文件已变化，请重新选择")
        guard()
    }
}
