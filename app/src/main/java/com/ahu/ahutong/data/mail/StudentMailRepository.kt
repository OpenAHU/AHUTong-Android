package com.ahu.ahutong.data.mail

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A delivery may have reached the server even when its acknowledgement was lost. */
class MailDeliveryUncertain : IOException("发送结果尚未确认，请先检查已发送，避免重复发送")
class MailDraftSaveUncertain : IOException("草稿保存结果尚未确认，请先检查草稿箱，避免重复保存")
class MailMoveUncertain : IOException("邮件移动结果尚未确认，请刷新原文件夹和目标文件夹后再操作")
class MailSessionExpired : IOException("邮箱会话已失效，请重新连接")
/** Only locally written messages are shown; server responses and transport exception messages stay private. */
class MailServiceFailure(val publicMessage: String) : IOException(publicMessage)

internal class StudentMailApi(
    private val client: OkHttpClient,
    private val sid: String,
    private val baseUrl: HttpUrl = "https://mail.stu.ahu.edu.cn/".toHttpUrl(),
    private val deviceId: String = UUID.randomUUID().toString(),
    private val guard: () -> Unit = {}
) {
    suspend fun rpc(function: String, body: JsonObject, extra: Map<String, String> = emptyMap()): JsonObject =
        request("js6/s", extra + mapOf("func" to function), body)

    suspend fun request(path: String, query: Map<String, String> = emptyMap(), body: JsonObject? = null): JsonObject {
        guard()
        val movingMessage = query["func"] == "mbox:updateMessageInfos"
        val url = baseUrl.newBuilder().addPathSegments(path)
            .addQueryParameter("sid", sid)
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
            .apply {
                if (body != null) {
                    val encoded = body.toString().toRequestBody("application/json;charset=UTF-8".toMediaType())
                    // Mutations must not be replayed after the server may have accepted them.
                    post(if (query["func"] in setOf("mbox:compose", "mbox:restoreDraft", "mbox:updateMessageInfos") ||
                        query["action"] in setOf("save", "deliver")) object : RequestBody() {
                        override fun contentType() = encoded.contentType()
                        override fun contentLength() = encoded.contentLength()
                        override fun writeTo(sink: BufferedSink) = encoded.writeTo(sink)
                        override fun isOneShot() = true
                    } else encoded)
                }
                if (query["action"] == "deliver") {
                    header("mail-server-type", "QIYE_MAIL")
                    header("mail-server-location", "hz")
                }
            }.build()
        // Cancellation cancels the socket. No request bodies, response bodies, or URLs are logged.
        return coroutineScope {
            val requestClient = if (movingMessage) client.newBuilder()
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build() else client
            val call = requestClient.newCall(request)
            // Keep cancellation connected after headers arrive, while the response body is still reading.
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                call.awaitResponse().use {
                    withContext(Dispatchers.IO) {
                        if (it.code in listOf(301, 302, 303, 307, 308, 401, 403)) throw MailSessionExpired()
                        if (!it.isSuccessful) throw IOException("邮箱服务暂时不可用（HTTP ${it.code}）")
                        val text = it.body?.string().orEmpty()
                        val root = try {
                            JsonParser.parseString(text).asJsonObject
                        } catch (_: Exception) {
                            throw IOException("邮箱返回了无法识别的数据，请重新连接或使用网页版")
                        }
                        val code = root.get("code")?.let { element -> if (element.isJsonPrimitive) element.asString else "" }.orEmpty()
                        StudentMailDiagnostics.record("mail.api.response", "path=$path function=${query["func"].orEmpty()} status=${it.code} code=" +
                            code.takeIf { value -> value.matches(Regex("[A-Za-z0-9_-]{1,40}")) }.orEmpty() +
                            " keys=" + root.keySet().joinToString(","))
                        if (code.contains("SESSION", true) || code.contains("LOGIN", true) || code.contains("AUTH", true) ||
                            code.equals("S_INVALID_SID", true)) {
                            throw MailSessionExpired()
                        }
                        if (movingMessage) {
                            val value = root.get("code")
                            if (value?.isJsonPrimitive != true || !value.asJsonPrimitive.isString ||
                                !code.matches(Regex("[A-Z][A-Z0-9_.]{1,79}"))) throw MailMoveUncertain()
                            if (code != "S_OK") throw MailServiceFailure("邮件移动未成功，请刷新邮件列表后重试")
                        } else StudentMailProtocol.checkResponse(root)
                        guard()
                        root
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!movingMessage || error is MailSessionExpired || error is MailServiceFailure ||
                    error is MailMoveUncertain) throw error
                throw MailMoveUncertain()
            } finally {
                cancellation.cancelAndJoin()
            }
        }
    }
}

internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, discarded, _ -> discarded.close() }
        }
    })
}

class StudentMailRepository {
    private var session: MailSession? = null
    private var api: StudentMailApi? = null
    private var account: String = ""

    suspend fun connect(): String = read { bindSession() }

    private suspend fun bindSession(): String {
        val connected = StudentMailSession.connect()
        val service = StudentMailApi(connected.client, connected.sid, guard = connected::assertCurrent)
        session = connected
        api = service
        val email = connected.email ?: StudentMailProtocol.parseAccount(service.request(
                "cowork/api/biz/enter/accountInfo", mapOf("needUnitNamePath" to "false")
            ))
        if (connected.email == null) StudentMailDiagnostics.record("mail.account.verified", "email-present=true")
        connected.assertCurrent()
        connected.email = email
        account = email
        return account
    }

    private suspend fun <T> read(block: suspend () -> T): T = try {
        recoverMailRead(renew = {
            // A logout/account change must never replay an old screen's read against the new account.
            val previous = session
            previous?.assertCurrent()
            val identity = com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator.currentIdentityGeneration()
            invalidateSession()
            bindSession()
            if (identity != com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator.currentIdentityGeneration() ||
                (previous != null && previous.account != session?.account)) throw MailSessionExpired()
            StudentMailDiagnostics.record("mail.session.renewed", "reason=server-expired")
        }, read = {
            val result = block()
            session?.let { StudentMailSession.persist(it) }
            result
        })
    } catch (error: MailSessionExpired) {
        invalidateSession()
        throw error
    }

    private fun service() = api ?: throw MailSessionExpired()

    suspend fun folders() = read { StudentMailProtocol.parseFolders(service().rpc(
        "mbox:getAllFolders", StudentMailProtocol.foldersRequest()
    )) }

    suspend fun messages(folderId: Int, start: Int, unread: Boolean) = read { StudentMailProtocol.parsePage(service().rpc(
        "mbox:listMessages", StudentMailProtocol.listRequest(folderId, start, unread)
    )) }

    suspend fun detail(id: String) = read { StudentMailProtocol.parseDetail(service().rpc(
        "mbox:readMessage", StudentMailProtocol.readRequest(id), mapOf("l" to "read", "supportTNEF" to "true")
    ), id) }

    suspend fun moveMessage(id: String, targetFolder: Int) {
        val current = webSession()
        val body = StudentMailProtocol.moveMessageRequest(id, targetFolder, account)
        current.assertCurrent()
        // A state-changing request must never enter the automatic read/renew/retry path.
        service().rpc("mbox:updateMessageInfos", body)
        current.assertCurrent()
        current.overview = null
    }

    suspend fun contacts(): List<MailContact> = read {
        val personal = StudentMailProtocol.parseContacts(service().request(
            "cowork/api/biz/person/personContactList", mapOf("email" to account, "lastUpdateTime" to "0")
        ))
        val recent = StudentMailProtocol.parseRecentContacts(service().request(
            "recent/api/biz/recent/recentContactList",
            mapOf("page" to "1", "pageSize" to "30", "conditionType" to "1", "contactType" to "1", "_account" to account)
        ))
        (personal + recent).distinctBy { it.email.lowercase() }
    }

    suspend fun attachments() = read { StudentMailProtocol.parseAttachmentList(service().rpc(
        "mbox:listAttachments", StudentMailProtocol.attachmentListRequest()
    )) }

    suspend fun beginDraft(draft: MailDraft): MailDraft {
        val current = webSession()
        if (draft.id != null && draft.sessionKey == current.key) return draft
        requireReusableDraft(draft, current.key)
        val root = service().rpc("mbox:compose",
            StudentMailProtocol.composeRequest(draft.copy(id = null), account, "continue"))
        current.assertCurrent()
        return draft.copy(id = StudentMailProtocol.parseComposeId(root), sessionKey = current.key)
    }

    suspend fun restoreDraft(id: String): MailDraft = read {
        val current = webSession()
        val root = service().rpc("mbox:restoreDraft", StudentMailProtocol.restoreDraftRequest(id))
        current.assertCurrent()
        StudentMailProtocol.parseDraft(root, draftId = id, sessionKey = current.key)
    }

    suspend fun saveDraft(draft: MailDraft): MailDraft {
        val prepared = syncDraftAttachments(beginDraft(draft))
        assertDraftSession(prepared)
        val body = StudentMailProtocol.composeRequest(prepared, account, "save")
        try {
            val root = service().rpc("mbox:compose", body, mapOf("l" to "compose", "action" to "save"))
            val id = StudentMailProtocol.parseComposeId(root)
            check(id == prepared.id) { "邮箱返回了不匹配的写信会话" }
            val savedId = StudentMailProtocol.parseSavedDraftId(root)
            assertDraftSession(prepared)
            return prepared.copy(draftId = savedId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A timeout can occur after the server saved the draft; never create a duplicate automatically.
            throw MailDraftSaveUncertain()
        }
    }

    suspend fun syncDraftAttachments(draft: MailDraft): MailDraft {
        assertDraftSession(draft)
        val root = service().rpc("mbox:compose", StudentMailProtocol.syncAttachmentsRequest(draft, account))
        val synced = StudentMailProtocol.parseDraft(root, draft.draftId, draft.sessionKey)
        check(synced.id == draft.id && synced.attachments.map { it.id }.toSet() == draft.attachments.map { it.id }.toSet()) {
            "附件同步未确认，请重新打开草稿或刷新后重试"
        }
        assertDraftSession(draft)
        return draft.copy(attachments = synced.attachments, unsupportedResources = synced.unsupportedResources)
    }

    suspend fun removeDraftAttachment(draft: MailDraft, attachmentId: String): MailDraft {
        assertDraftSession(draft)
        val root = service().rpc("mbox:compose", StudentMailProtocol.removeAttachmentRequest(draft, account, attachmentId))
        val returned = root.getAsJsonObject("var")?.getAsJsonArray("attachments")?.mapNotNull {
            it.takeIf { value -> value.isJsonObject }?.asJsonObject
        }.orEmpty()
        StudentMailDiagnostics.record("mail.attachment.removed", "returned=${returned.size} deleted=" +
            returned.count { it.get("deleted")?.let { value -> value.isJsonPrimitive && value.asString == "true" } == true })
        val synced = StudentMailProtocol.parseDraft(root, draft.draftId, draft.sessionKey)
        val expected = draft.attachments.filterNot { it.id == attachmentId }.map { it.id }.toSet()
        check(synced.id == draft.id && synced.attachments.map { it.id }.toSet() == expected) {
            "附件移除未确认，请重新打开草稿或刷新后重试"
        }
        assertDraftSession(draft)
        return draft.copy(attachments = synced.attachments, unsupportedResources = synced.unsupportedResources)
    }

    suspend fun uploadAttachment(
        draft: MailDraft, file: File, name: String, contentType: String,
        onProgress: (Long, Long) -> Unit
    ): MailDraft {
        val prepared = syncDraftAttachments(beginDraft(draft))
        val current = assertDraftSession(prepared)
        val uploaded = StudentMailUploader(current).upload(requireNotNull(prepared.id), file, name, contentType, onProgress)
        assertDraftSession(prepared)
        return syncDraftAttachments(prepared.copy(attachments = prepared.attachments + uploaded))
    }

    private fun assertDraftSession(draft: MailDraft): MailSession = webSession().also {
        if (draft.id == null || draft.sessionKey != it.key) {
            throw MailServiceFailure("写信会话已变化，请重新打开已保存草稿；未保存附件需重新上传")
        }
    }

    suspend fun send(draft: MailDraft) {
        // Attachments belong to their compose session. Reuse it only within the same mailbox session.
        val prepared = syncDraftAttachments(beginDraft(draft))
        assertDraftSession(prepared)
        val body = StudentMailProtocol.composeRequest(prepared, account, true)
        try {
            val root = service().rpc("mbox:compose", body, mapOf(
                "l" to "compose", "action" to "deliver", "xMailerExt" to "Sirius_WEB_WIN_1.66.2"
            ))
            val sent = root.getAsJsonObject("savedSent")?.get("mid")?.asString
            if (sent.isNullOrBlank()) throw MailDeliveryUncertain()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Even an HTTP/protocol error cannot prove that the mail was not delivered.
            throw MailDeliveryUncertain()
        }
    }

    fun disconnect() {
        session = null
        api = null
        account = ""
    }

    fun invalidateSession() {
        session?.let(StudentMailSession::invalidate)
        disconnect()
    }

    fun rememberOverview(overview: MailOverview) {
        session?.let { StudentMailSession.rememberOverview(it, overview) }
    }

    fun webSession(): MailSession = (session ?: throw MailSessionExpired()).also { it.assertCurrent() }
}

internal fun requireReusableDraft(draft: MailDraft, sessionKey: String) {
    if (draft.sessionKey != sessionKey && (draft.attachments.isNotEmpty() || draft.draftId != null)) {
        throw MailServiceFailure("写信会话已变化，请重新打开已保存草稿；未保存附件需重新上传")
    }
}
