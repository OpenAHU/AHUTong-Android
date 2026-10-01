package com.ahu.ahutong.data.mail

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.jsoup.Jsoup
import java.net.URI

data class MailFolder(val id: Int, val name: String, val total: Int, val unread: Int)
data class MailMessage(
    val id: String, val folderId: Int, val subject: String, val from: String,
    val to: List<String>, val summary: String, val date: String, val read: Boolean,
    val attachments: List<MailAttachment>
)
data class MailDetail(
    val id: String, val subject: String, val from: List<String>, val to: List<String>,
    val cc: List<String>, val text: String, val html: String, val attachments: List<MailAttachment>
)
data class MailAttachment(
    val id: String, val name: String, val size: Long, val downloadPath: String? = null,
    val messageId: String? = null, val partId: String? = null,
    val contentId: String? = null, val contentLocation: String? = null,
    val contentType: String = "", val inlined: Boolean = false
)
data class MailDraftAttachment(
    val id: String, val name: String, val size: Long,
    val contentType: String = "application/octet-stream", val inlined: Boolean = false
)
data class MailContact(val id: String, val name: String, val email: String, val group: String = "")
data class MailPage(val messages: List<MailMessage>, val total: Int)
data class MailOverview(val account: String, val folders: List<MailFolder>, val folderId: Int,
    val page: MailPage, val unreadOnly: Boolean)
data class MailDraft(
    val to: String = "", val cc: String = "", val bcc: String = "", val subject: String = "",
    val body: String = "", val id: String? = null,
    val draftId: String? = null, val sessionKey: String? = null,
    val attachments: List<MailDraftAttachment> = emptyList(), val originalHtml: String? = null,
    val unsupportedResources: Boolean = false
)

/** Only implements operations observed in the sanitized HAR contract. */
object StudentMailProtocol {
    fun foldersRequest() = JsonObject().apply { addProperty("order", "custom_virtual") }

    fun listRequest(folderId: Int, start: Int = 0, unreadOnly: Boolean = false) = JsonObject().apply {
        require(start >= 0)
        addProperty("limit", 30)
        addProperty("start", start)
        addProperty("summaryWindowSize", 30)
        listOf("returnTotal", "returnTid", "returnTag", "returnAttachments").forEach { addProperty(it, true) }
        addProperty("order", if (folderId == -3) "deferredDate" else "date")
        addProperty("desc", folderId != -3)
        addProperty("skipLockedFolders", unreadOnly || folderId < 0)
        add("filter", JsonObject().apply {
            if (unreadOnly) add("flags", JsonObject().apply { addProperty("read", false) })
            if (folderId == -1) addProperty("label0", 1)
            if (folderId == -3) addProperty("defer", ":22000101")
        })
        when {
            unreadOnly -> add("fids", JsonArray().apply { add(folderId) })
            folderId == -1 -> add("fids", JsonArray().apply { listOf(1, 3, -1, -9, -3, 2).forEach { add(it) } })
            folderId == -3 -> Unit
            else -> {
                require(folderId > 0) { "暂不支持该虚拟文件夹" }
                addProperty("fid", folderId)
                if (folderId in listOf(1, 2, 3)) addProperty("topFlag", "top")
            }
        }
    }

    fun readRequest(id: String) = JsonObject().apply {
        addProperty("id", id)
        addProperty("level", 32)
        addProperty("mode", "html")
        addProperty("markRead", false)
        add("returnHeaders", JsonObject().apply {
            addProperty("Resent-From", "A")
            addProperty("Sender", "A")
        })
    }

    fun moveMessageRequest(id: String, targetFolder: Int, account: String): JsonObject {
        require(targetFolder == 1 || targetFolder == 4) { "仅支持移到收件箱或已删除" }
        require(id.length in 1..512 && id.none { it.isWhitespace() || it.isISOControl() }) {
            "邮件标识无效，请刷新邮件列表"
        }
        require(account.length in 1..320 && account.none { it.isWhitespace() || it.isISOControl() }) {
            "邮箱账号无效，请重新连接"
        }
        return JsonObject().apply {
            add("ids", JsonArray().apply { add(id) })
            add("attrs", JsonObject().apply { addProperty("fid", targetFolder) })
            addProperty("needFilter", false)
            addProperty("_account", account)
            addProperty("riskHitIntercept", true)
        }
    }

    fun splitAddresses(value: String): List<String> = value.split(';', '；', ',', '，', '\n', '\r')
        .map(String::trim).filter(String::isNotEmpty).distinct()

    fun composeRequest(draft: MailDraft, account: String, deliver: Boolean) =
        composeRequest(draft, account, if (deliver) "deliver" else "continue")

    fun composeRequest(draft: MailDraft, account: String, action: String) = JsonObject().apply {
        require(action in setOf("continue", "save", "deliver"))
        check(!draft.unsupportedResources) { "该草稿含暂不支持的内嵌或云附件，请在网页版继续编辑" }
        addProperty("delayTime", 0)
        addProperty("returnInfo", true)
        addProperty("action", action)
        addProperty("mailTrace", false)
        addProperty("cloudAttachTrace", false)
        // Preserve the observed initial value; the send-time true value has undocumented semantics.
        addProperty("riskHitIntercept", false)
        addProperty("xMailerExt", "Sirius_WEB_WIN_1.66.2")
        draft.id?.let { addProperty("id", it) }
        add("noticeSenderReceivers", JsonArray())
        add("attrs", JsonObject().apply {
            addProperty("subject", draft.subject)
            addProperty("requestReadReceipt", false)
            add("to", addresses(splitAddresses(draft.to)))
            add("cc", addresses(splitAddresses(draft.cc)))
            add("bcc", addresses(splitAddresses(draft.bcc)))
            // Compose input is plain text. Escaping before HTML keeps typed markup inert.
            addProperty("content", draft.originalHtml ?: "<div>" + escapeHtml(draft.body).replace("\n", "<br>") + "</div>")
            addProperty("account", account)
            addProperty("isHtml", true)
            addProperty("saveSentCopy", true)
            add("attachments", draftAttachments(draft.attachments))
            add("cloudattachments", JsonArray())
        })
    }

    fun restoreDraftRequest(id: String) = JsonObject().apply { addProperty("id", id) }

    fun syncAttachmentsRequest(draft: MailDraft, account: String) = JsonObject().apply {
        check(!draft.unsupportedResources) { "该草稿含暂不支持的内嵌或云附件，请在网页版继续编辑" }
        addProperty("id", requireNotNull(draft.id))
        addProperty("action", "continue")
        addProperty("delayTime", 0)
        addProperty("mailTrace", false)
        addProperty("returnInfo", true)
        addProperty("xMailerExt", "Sirius_WEB_WIN_1.66.2")
        add("attrs", JsonObject().apply {
            addProperty("account", account)
            add("attachments", draftAttachments(draft.attachments))
        })
    }

    /** The official client removes one attachment using a delta, rather than omitting it from a list. */
    fun removeAttachmentRequest(draft: MailDraft, account: String, attachmentId: String): JsonObject {
        val id = attachmentId.toLongOrNull()
        require(id != null && id > 0 && draft.attachments.any { it.id == attachmentId }) { "附件编号无效" }
        return syncAttachmentsRequest(draft, account).apply {
            getAsJsonObject("attrs").add("attachments", JsonArray().apply {
                add(JsonObject().apply { addProperty("id", id); addProperty("deleted", true) })
            })
        }
    }

    fun attachmentListRequest(start: Int = 0) = JsonObject().apply {
        addProperty("order", "date")
        addProperty("desc", true)
        addProperty("start", start)
        addProperty("limit", 10)
        addProperty("returnTotal", true)
        addProperty("skipLockedFolders", true)
    }

    fun checkResponse(root: JsonObject) {
        val code = root.string("code")
        val success = if (code == "S_OK") true else code in setOf("0", "200") && root.boolean("success")
        // Response messages can echo addresses or session values, so do not propagate them.
        check(success) { "邮箱服务请求未成功，请刷新登录后重试" }
    }

    fun parseFolders(root: JsonObject): List<MailFolder> {
        checkResponse(root)
        return root.array("var").objects().map {
            val stats = it.obj("stats")
            MailFolder(it.int("id"), it.string("name"), stats.int("messageCount"), stats.int("unreadMessageCount"))
        }
    }

    fun parsePage(root: JsonObject): MailPage {
        checkResponse(root)
        return MailPage(root.array("var").objects().map {
            MailMessage(it.string("id"), it.int("fid"), it.string("subject"), it.string("from"),
                it.strings("to"), it.string("summary"), it.string("receivedDate").ifBlank { it.string("sentDate") },
                it.obj("flags").boolean("read"), parseAttachments(it.array("attachments"), it.string("id")))
        }, root.int("total"))
    }

    fun parseDetail(root: JsonObject, id: String): MailDetail {
        checkResponse(root)
        val item = root.obj("var")
        // Despite encoding=base64 metadata, HAR html.content is already decoded HTML.
        val html = item.obj("html").string("content")
        val text = item.obj("text").string("content").ifBlank { Jsoup.parse(html).wholeText() }
        return MailDetail(id, item.string("subject"), item.strings("from"), item.strings("to"),
            item.strings("cc"), text, html, parseAttachments(item.array("attachments"), id))
    }

    fun parseContacts(root: JsonObject): List<MailContact> {
        checkResponse(root)
        val data = root.obj("data")
        check(data.int("statusCode") == 0) { "联系人同步未成功" }
        return data.array("personContactVOList").objects().flatMap { item ->
            item.strings("email").map { email -> MailContact(item.string("cid"),
                item.string("qiyeAccountName").ifBlank { email }, email) }
        }
    }

    fun parseRecentContacts(root: JsonObject): List<MailContact> {
        checkResponse(root)
        return root.obj("data").array("contactList").objects().map {
            MailContact(it.string("accountId").ifBlank { it.string("email") },
                it.string("nickname").ifBlank { it.string("name") }, it.string("email"))
        }
    }

    fun parseAccount(root: JsonObject): String {
        checkResponse(root)
        val data = root.obj("data")
        return data.obj("defaultSender").string("email").ifBlank { data.string("email") }
            .also { check(it.isNotBlank()) { "邮箱账号信息为空" } }
    }

    fun parseComposeId(root: JsonObject): String {
        checkResponse(root)
        return root.obj("var").string("id").also { check(it.isNotBlank()) { "邮箱未返回写信会话" } }
    }

    fun parseAttachmentList(root: JsonObject): List<MailAttachment> {
        checkResponse(root)
        return root.array("var").objects().map {
            MailAttachment(it.string("id") + ":" + it.string("partId"), it.string("attn"), it.long("attsize"),
                messageId = it.string("id"), partId = it.string("partId"))
        }
    }

    fun parseDraft(root: JsonObject, draftId: String? = null, sessionKey: String? = null): MailDraft {
        checkResponse(root)
        val item = root.obj("var")
        val html = item.string("content")
        val isHtml = item.boolean("isHtml")
        val text = if (isHtml) Jsoup.parse(html).apply { select("script,style").remove() }.wholeText() else html
        val attachmentItems = item.array("attachments").objects().filterNot { it.boolean("deleted") }
        val unsupported = item.boolean("inlineResources") || item.array("cloudattachments").size() > 0 ||
            attachmentItems.any { it.boolean("inlined") || it.string("type") !in setOf("upload", "internal") }
        return MailDraft(
            to = item.strings("to").joinToString("; "), cc = item.strings("cc").joinToString("; "),
            bcc = item.strings("bcc").joinToString("; "), subject = item.string("subject"),
            body = text, id = parseComposeId(root), draftId = root.string("draftId").takeIf(String::isNotBlank) ?: draftId,
            sessionKey = sessionKey,
            attachments = attachmentItems.map { MailDraftAttachment(it.string("id"), it.string("name"),
                it.long("size"), it.string("contentType").ifBlank { "application/octet-stream" }, it.boolean("inlined")) },
            originalHtml = html.takeIf { isHtml }, unsupportedResources = unsupported
        )
    }

    fun parseSavedDraftId(root: JsonObject): String {
        checkResponse(root)
        return root.string("draftId").also { check(it.isNotBlank()) { "邮箱未确认草稿保存，请先检查草稿箱" } }
    }

    /** Extracts inert markup; caller must validate allowed HTTPS hosts before following the result. */
    fun extractRedirect(html: String, baseUrl: String): String? {
        val document = Jsoup.parse(html, baseUrl)
        val meta = document.select("meta[http-equiv]").firstOrNull { it.attr("http-equiv").equals("refresh", true) }
        val target = meta?.attr("content")?.let {
            Regex("(?i)(?:^|;)\\s*url\\s*=\\s*(.+)$").find(it)?.groupValues?.get(1)?.trim()?.trim('\'', '"')
        } ?: Regex("window\\.location\\.replace\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)")
            .find(html)?.groupValues?.get(1)
        return target?.let { runCatching { URI(baseUrl).resolve(it).toString() }.getOrNull() }
    }

    private fun parseAttachments(values: JsonArray, messageId: String? = null) = values.objects().map {
        MailAttachment(it.string("id"), it.string("filename"), it.long("estimateSize").takeIf { size -> size > 0 }
            ?: it.long("contentLength"), messageId = messageId, partId = it.string("id"),
            contentId = it.string("contentId").ifBlank { it.string("cid") }.takeIf(String::isNotBlank),
            contentLocation = it.string("contentLocation").takeIf(String::isNotBlank),
            contentType = it.string("contentType"), inlined = it.boolean("inlined"))
    }
    private fun draftAttachments(values: List<MailDraftAttachment>) = JsonArray().apply {
        values.forEach { attachment ->
            val id = attachment.id.toLongOrNull()
            require(id != null && id > 0) { "附件编号无效，请重新上传附件" }
            add(JsonObject().apply {
                addProperty("id", id)
                addProperty("name", attachment.name)
                addProperty("inlined", attachment.inlined)
                addProperty("deleted", false)
            })
        }
    }
    private fun addresses(values: List<String>) = JsonArray().apply { values.forEach { add(it) } }
    private fun escapeHtml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;").replace("\r\n", "\n")
    private fun JsonObject.string(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun JsonObject.int(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: 0
    private fun JsonObject.long(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
    private fun JsonObject.boolean(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } ?: false
    private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
    private fun JsonObject.array(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    private fun JsonArray.objects() = mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
    private fun JsonObject.strings(key: String): List<String> = when (val value = get(key)) {
        null -> emptyList()
        else -> when {
            value.isJsonArray -> value.asJsonArray.mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }
            value.isJsonPrimitive -> listOf(value.asString).filter(String::isNotBlank)
            else -> emptyList()
        }
    }
}
