package com.ahu.ahutong.ui.state

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.util.Log
import com.ahu.ahutong.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.mail.*
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.ui.screen.main.StudentMailWebActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MailScreen { MESSAGES, DETAIL, COMPOSE, CONTACTS, ATTACHMENTS }

data class MailTransferProgress(val name: String, val bytes: Long = 0, val total: Long = 0, val uploading: Boolean)

data class StudentMailUiState(
    val connected: Boolean = false, val loading: Boolean = false, val sending: Boolean = false,
    val error: String? = null, val notice: String? = null, val account: String = "",
    val folders: List<MailFolder> = emptyList(), val folderId: Int = 1,
    val messages: List<MailMessage> = emptyList(), val total: Int = 0, val unreadOnly: Boolean = false,
    val contacts: List<MailContact> = emptyList(), val detail: MailDetail? = null,
    val attachments: List<MailAttachment> = emptyList(), val draft: MailDraft = MailDraft(),
    val screen: MailScreen = MailScreen.MESSAGES, val sendUncertain: Boolean = false,
    val saving: Boolean = false, val draftDirty: Boolean = false, val draftSaveUncertain: Boolean = false,
    val transfer: MailTransferProgress? = null, val mailMoveUncertain: Boolean = false
)

class StudentMailViewModel : ViewModel() {
    private val repository = StudentMailRepository()
    private val mutableState = MutableStateFlow(StudentMailSession.cachedOverview()?.let { overview ->
        StudentMailUiState(connected = true, account = overview.account, folders = overview.folders,
            folderId = overview.folderId, messages = overview.page.messages, total = overview.page.total,
            unreadOnly = overview.unreadOnly)
    } ?: StudentMailUiState())
    val state: StateFlow<StudentMailUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var operationRevision = 0L
    private var pendingUploadKey: String? = null
    private var pendingDownload: Pair<MailAttachment, String>? = null

    private fun busy() = state.value.let { it.loading || it.sending || it.saving || it.transfer != null }

    init {
        connect()
        viewModelScope.launch {
            AhuSessionState.status.collect { status ->
                if (status == AhuSessionState.Status.Anonymous && !canResumePersistedCampusSession(
                        SessionRefreshCoordinator.isExplicitlySignedOut(), status, SessionStore.isLoggedIn())) {
                    operation?.cancel()
                    pendingUploadKey = null
                    pendingDownload = null
                    repository.disconnect()
                    mutableState.value = StudentMailUiState(error = "请先登录智慧安大")
                }
            }
        }
    }

    private fun load(block: suspend () -> Unit) {
        if (busy()) return
        mutableState.update { it.copy(loading = true, error = null) }
        val revision = ++operationRevision
        operation = viewModelScope.launch {
            try {
                block()
                repository.webSession().assertCurrent()
                state.value.takeIf { it.connected }?.let { current ->
                    repository.rememberOverview(MailOverview(current.account, current.folders, current.folderId,
                        MailPage(current.messages, current.total), current.unreadOnly))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportFailure(error)
                if (error is MailSessionExpired) {
                    repository.invalidateSession()
                    mutableState.update { it.copy(connected = false, account = "", folders = emptyList(),
                        messages = emptyList(), detail = null, contacts = emptyList(), attachments = emptyList()) }
                }
                mutableState.update { it.copy(error = safeError(error)) }
            } finally {
                if (revision == operationRevision) mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun connect() = load {
        val email = repository.connect()
        val folders = repository.folders().filter { it.id > 0 }
        val folder = folders.firstOrNull { it.id == state.value.folderId }?.id ?: folders.firstOrNull()?.id ?: 1
        val page = repository.messages(folder, 0, state.value.unreadOnly)
        mutableState.update { it.copy(connected = true, account = email, folders = folders,
            folderId = folder, messages = page.messages, total = page.total, screen = MailScreen.MESSAGES) }
    }

    fun refresh() {
        if (!state.value.connected) { connect(); return }
        when (state.value.screen) {
            MailScreen.MESSAGES -> load {
                val folders = repository.folders().filter { it.id > 0 }
                val page = repository.messages(state.value.folderId, 0, state.value.unreadOnly)
                mutableState.update { it.copy(folders = folders, messages = page.messages, total = page.total, mailMoveUncertain = false) }
            }
            MailScreen.CONTACTS -> showContacts()
            MailScreen.ATTACHMENTS -> showAttachments()
            MailScreen.DETAIL -> state.value.detail?.let { detail -> load {
                val refreshed = repository.detail(detail.id)
                mutableState.update { it.copy(detail = refreshed) }
            } }
            MailScreen.COMPOSE -> Unit
        }
    }

    fun selectFolder(id: Int) = load {
        val page = repository.messages(id, 0, state.value.unreadOnly)
        mutableState.update { it.copy(folderId = id, messages = page.messages, total = page.total, screen = MailScreen.MESSAGES, mailMoveUncertain = false) }
    }

    fun setUnreadOnly(unread: Boolean) = load {
        val page = repository.messages(state.value.folderId, 0, unread)
        mutableState.update { it.copy(unreadOnly = unread, messages = page.messages, total = page.total) }
    }

    fun loadMore() = load {
        val page = repository.messages(state.value.folderId, state.value.messages.size, state.value.unreadOnly)
        mutableState.update { it.copy(messages = (it.messages + page.messages).distinctBy(MailMessage::id), total = page.total) }
    }

    fun openMessage(message: MailMessage) {
        if (message.folderId == 2 || state.value.folderId == 2) { editDraft(message.id); return }
        load {
            val detail = repository.detail(message.id)
            mutableState.update { it.copy(detail = detail, screen = MailScreen.DETAIL) }
        }
    }

    fun moveMessage(id: String, targetFolder: Int) = load {
        val snapshot = state.value
        val deletingDraft = snapshot.draft.draftId == id
        try {
            repository.moveMessage(id, targetFolder)
        } catch (error: MailMoveUncertain) {
            mutableState.update { it.copy(mailMoveUncertain = true, screen = MailScreen.MESSAGES, detail = null,
                draftSaveUncertain = it.draftSaveUncertain || deletingDraft) }
            throw error
        }
        mutableState.update { current ->
            val wasListed = current.messages.any { it.id == id }
            current.copy(screen = MailScreen.MESSAGES, detail = null,
                messages = current.messages.filterNot { it.id == id },
                total = (current.total - if (wasListed) 1 else 0).coerceAtLeast(0),
                draft = if (deletingDraft) MailDraft() else current.draft,
                draftDirty = if (deletingDraft) false else current.draftDirty,
                draftSaveUncertain = if (deletingDraft) false else current.draftSaveUncertain,
                mailMoveUncertain = false, notice = if (targetFolder == 4) "邮件已移到已删除" else "邮件已移到收件箱")
        }
        try {
            val folders = repository.folders().filter { it.id > 0 }
            val page = repository.messages(snapshot.folderId, 0, snapshot.unreadOnly)
            mutableState.update { it.copy(folders = folders, messages = page.messages, total = page.total) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error is MailSessionExpired) throw error
            // The move was acknowledged; a failed list refresh must not be reported as another failed move.
            reportFailure(error)
            mutableState.update { it.copy(error = "邮件已移动，但列表更新失败，请刷新文件夹") }
        }
    }

    fun editDraft(id: String) {
        if (busy()) return
        if (state.value.draftDirty) {
            mutableState.update { it.copy(notice = "已有未保存的编辑内容，请先保存或清空后再打开其他草稿", screen = MailScreen.COMPOSE) }
            return
        }
        load {
            val draft = repository.restoreDraft(id)
            mutableState.update { it.copy(draft = draft, draftDirty = false, draftSaveUncertain = false,
                sendUncertain = false, screen = MailScreen.COMPOSE, notice = "已打开服务器草稿") }
        }
    }

    fun showDrafts() = selectFolder(2)

    fun previewSavedDraft() {
        val id = state.value.draft.draftId ?: return
        load {
            val detail = repository.detail(id)
            mutableState.update { it.copy(detail = detail, screen = MailScreen.DETAIL) }
        }
    }

    fun bodyImages(detail: MailDetail, allowExternal: Boolean): StudentMailBodyImages? = runCatching {
        val session = repository.webSession()
        StudentMailBodyImages(session, detail.id, detail.attachments, allowExternal, session::assertCurrent)
    }.getOrNull()

    fun showContacts() = load {
        val contacts = repository.contacts()
        mutableState.update { it.copy(contacts = contacts, screen = MailScreen.CONTACTS) }
    }

    fun showAttachments() = load {
        val attachments = repository.attachments()
        mutableState.update { it.copy(attachments = attachments, screen = MailScreen.ATTACHMENTS) }
    }

    fun startCompose(contact: MailContact? = null) {
        if (busy()) return
        mutableState.update { current ->
            val draft = if (contact == null) current.draft else current.draft.copy(to =
                (StudentMailProtocol.splitAddresses(current.draft.to) + contact.email).distinct().joinToString("; "))
            current.copy(draft = draft, draftDirty = current.draftDirty || draft != current.draft, screen = MailScreen.COMPOSE)
        }
    }

    fun updateDraft(draft: MailDraft) {
        if (!busy() && !state.value.sendUncertain && !state.value.draftSaveUncertain) mutableState.update {
            val updated = if (draft.body != it.draft.body) draft.copy(originalHtml = null) else draft
            it.copy(draft = updated, draftDirty = it.draftDirty || updated != it.draft, notice = null)
        }
    }

    fun discardDraft() {
        if (!busy()) mutableState.update {
            it.copy(draft = MailDraft(), draftDirty = false, draftSaveUncertain = false,
                sendUncertain = false, error = null, notice = null, screen = MailScreen.COMPOSE)
        }
    }

    private fun quote(detail: MailDetail) = "\n\n---------- 原邮件 ----------\n发件人：${detail.from.joinToString("; ")}\n主题：${detail.subject}\n${StudentMailBody.replyText(detail)}"
    private fun hasDraft() = state.value.draft.let { it.attachments.isNotEmpty() || listOf(it.to, it.cc, it.bcc, it.subject, it.body).any(String::isNotBlank) }

    fun saveDraft(onSaved: (() -> Unit)? = null) {
        val snapshot = state.value
        if (busy() || snapshot.sendUncertain || snapshot.draftSaveUncertain || snapshot.draft.unsupportedResources) return
        mutableState.update { it.copy(saving = true, error = null) }
        operation = viewModelScope.launch {
            try {
                val saved = repository.saveDraft(snapshot.draft)
                repository.webSession().assertCurrent()
                mutableState.update { it.copy(draft = saved, draftDirty = false, notice = "草稿已保存，可在草稿箱继续编辑") }
                onSaved?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportFailure(error)
                if (error is MailSessionExpired) repository.invalidateSession()
                mutableState.update { it.copy(error = safeError(error), draftSaveUncertain = error is MailDraftSaveUncertain,
                    connected = it.connected && error !is MailSessionExpired) }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    fun removeDraftAttachment(id: String) {
        val current = state.value
        if (busy() || current.sendUncertain || current.draftSaveUncertain) return
        load {
            val draft = repository.removeDraftAttachment(current.draft, id)
            mutableState.update { it.copy(draft = draft, draftDirty = true, notice = null) }
        }
    }

    fun prepareUpload(): Boolean {
        if (busy() || !state.value.connected || state.value.sendUncertain || state.value.draftSaveUncertain ||
            state.value.draft.unsupportedResources) return false
        return try { pendingUploadKey = repository.webSession().key; true } catch (_: Exception) { false }
    }

    fun uploadAttachments(context: Context, uris: List<Uri>) {
        val key = pendingUploadKey
        pendingUploadKey = null
        if (key == null || uris.isEmpty() || busy()) return
        mutableState.update { it.copy(transfer = MailTransferProgress("正在读取所选文件", uploading = true), error = null) }
        operation = viewModelScope.launch {
            try {
                ensurePickerSession(key)
                uris.forEach { uri ->
                    val file = StudentMailFiles.prepare(context.applicationContext, uri)
                    try {
                        ensurePickerSession(key)
                        mutableState.update { it.copy(transfer = MailTransferProgress(file.name, total = file.file.length(), uploading = true)) }
                        val draft = repository.uploadAttachment(state.value.draft, file.file, file.name, file.mimeType, progress(file.name, true))
                        ensurePickerSession(key)
                        mutableState.update { it.copy(draft = draft, draftDirty = true) }
                    } finally { file.file.delete() }
                }
                mutableState.update { it.copy(notice = "附件已添加，保存草稿或发送时会随邮件提交") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                transferFailure(error)
            } finally {
                mutableState.update { it.copy(transfer = null) }
            }
        }
    }

    fun prepareDownload(attachment: MailAttachment): String? {
        if (busy() || attachment.messageId.isNullOrBlank() || attachment.partId.isNullOrBlank()) return null
        return try {
            pendingDownload = attachment to repository.webSession().key
            mailFileName(attachment.name)
        } catch (_: Exception) { null }
    }

    fun downloadAttachment(context: Context, destination: Uri?) {
        val pending = pendingDownload
        pendingDownload = null
        if (destination == null || pending == null) return
        val attachment = pending.first
        val appContext = context.applicationContext
        if (busy()) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { appContext.contentResolver.delete(destination, null, null) } }
            return
        }
        mutableState.update { it.copy(transfer = MailTransferProgress(attachment.name, uploading = false), error = null) }
        operation = viewModelScope.launch {
            var saved = false
            try {
                ensurePickerSession(pending.second)
                val session = repository.webSession()
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                        val update = progress(attachment.name, false)
                        StudentMailDownload(session).download(attachment.messageId!!, attachment.partId!!,
                            output) { bytes, total -> update(bytes, total ?: attachment.size.coerceAtLeast(0)) }
                    } ?: throw MailServiceFailure("无法写入所选位置，请重新选择")
                }
                ensurePickerSession(pending.second)
                saved = true
                mutableState.update { it.copy(notice = "附件已保存到所选位置") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                transferFailure(error)
            } finally {
                if (!saved) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    runCatching { appContext.contentResolver.delete(destination, null, null) }
                }
                mutableState.update { it.copy(transfer = null) }
            }
        }
    }

    private fun ensurePickerSession(key: String) {
        if (repository.webSession().key != key) throw MailServiceFailure("邮箱登录已变化，请重新选择附件")
    }

    private fun progress(name: String, uploading: Boolean): (Long, Long) -> Unit {
        val lastUpdate = AtomicLong(0)
        return { bytes, total ->
            val now = System.currentTimeMillis()
            if (bytes == total || now - lastUpdate.get() >= 100) {
                lastUpdate.set(now)
                mutableState.update { current -> if (current.transfer != null) current.copy(
                    transfer = MailTransferProgress(name, bytes, total, uploading)) else current }
            }
        }
    }

    private fun transferFailure(error: Exception) {
        reportFailure(error)
        if (error is MailSessionExpired) repository.invalidateSession()
        mutableState.update { it.copy(error = safeError(error), connected = it.connected && error !is MailSessionExpired) }
    }

    fun cancelTransfer() {
        if (state.value.transfer != null) {
            operation?.cancel()
            mutableState.update { it.copy(notice = "附件传输已取消") }
        }
    }

    fun reply() {
        val detail = state.value.detail ?: return
        if (hasDraft()) { mutableState.update { it.copy(notice = "已有未发送内容，请先继续编辑或清空后再回复", screen = MailScreen.COMPOSE) }; return }
        updateDraft(MailDraft(to = detail.from.map(::mailAddressOf).joinToString("; "),
            subject = if (detail.subject.startsWith("Re:", true)) detail.subject else "Re: ${detail.subject}", body = quote(detail)))
        startCompose()
    }

    fun forward() {
        val detail = state.value.detail ?: return
        if (hasDraft()) { mutableState.update { it.copy(notice = "已有未发送内容，请先继续编辑或清空后再转发", screen = MailScreen.COMPOSE) }; return }
        updateDraft(MailDraft(subject = "Fwd: ${detail.subject}", body = quote(detail)))
        startCompose()
    }

    fun send() {
        val snapshot = state.value
        if (busy() || snapshot.sendUncertain || snapshot.draftSaveUncertain || snapshot.draft.unsupportedResources) return
        val addresses = listOf(snapshot.draft.to, snapshot.draft.cc, snapshot.draft.bcc).flatMap(StudentMailProtocol::splitAddresses)
        if (addresses.isEmpty() || addresses.any { !isMailAddress(it) }) {
            mutableState.update { it.copy(error = "请填写有效邮箱地址，多个地址用分号分隔") }; return
        }
        mutableState.update { it.copy(sending = true, error = null) }
        operation = viewModelScope.launch {
            try {
                repository.send(snapshot.draft)
                mutableState.update { it.copy(draft = MailDraft(), draftDirty = false, screen = MailScreen.MESSAGES,
                    notice = "邮件已发送，可刷新已发送文件夹查看") }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(sendUncertain = true) }
                throw cancelled
            } catch (error: Exception) {
                reportFailure(error)
                if (error is MailSessionExpired) {
                    repository.invalidateSession()
                    mutableState.update { it.copy(connected = false, account = "") }
                }
                mutableState.update { it.copy(error = safeError(error), sendUncertain = error is MailDeliveryUncertain) }
            } finally {
                mutableState.update { it.copy(sending = false) }
            }
        }
    }

    fun back(): Boolean {
        if (state.value.sending || state.value.saving) return true
        if (state.value.transfer != null) { cancelTransfer(); return true }
        if (state.value.loading) {
            ++operationRevision
            operation?.cancel()
            mutableState.update { it.copy(loading = false) }
        }
        if (state.value.screen == MailScreen.MESSAGES) return false
        if (state.value.screen == MailScreen.DETAIL && state.value.detail?.id == state.value.draft.draftId) {
            mutableState.update { it.copy(screen = MailScreen.COMPOSE) }
            return true
        }
        mutableState.update { it.copy(screen = MailScreen.MESSAGES) }
        return true
    }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }

    fun openWebsite(context: Context) {
        if (busy()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            mutableState.update { it.copy(error = "应用内网页版需要 Android 9 或以上；当前仍可使用原生邮箱功能") }; return
        }
        try {
            val lease = StudentMailWebLease.open(repository.webSession())
            context.startActivity(Intent(context, StudentMailWebActivity::class.java).putExtra(StudentMailWebLease.KEY_LEASE, lease))
        } catch (_: Exception) {
            mutableState.update { it.copy(error = "邮箱会话已失效，请重新连接后打开网页版") }
        }
    }

    override fun onCleared() {
        repository.disconnect()
        super.onCleared()
    }

    private fun safeError(error: Exception): String {
        val message = when (error) {
            is MailDeliveryUncertain -> "发送结果尚未确认，请先检查已发送，避免重复发送"
            is MailDraftSaveUncertain -> "草稿保存结果尚未确认，请先查看草稿箱，避免重复保存"
            is MailMoveUncertain -> "邮件移动结果尚未确认，请刷新原文件夹并检查目标文件夹，避免重复操作"
            is MailSessionExpired -> "邮箱登录已过期，请重新连接"
            is MailServiceFailure -> error.publicMessage
            is java.net.UnknownHostException -> "无法解析邮箱服务地址，请检查网络连接"
            is java.net.SocketTimeoutException -> "邮箱连接超时，请检查网络后重试"
            is javax.net.ssl.SSLException -> "邮箱安全连接失败，请检查网络或稍后重试"
            else -> "邮箱操作未完成，请检查网络或重新登录智慧安大；也可使用网页版重试"
        }
        if (!BuildConfig.DEBUG) return message
        val source = error.stackTrace.firstOrNull { it.className.startsWith("com.ahu.ahutong") }
        return "$message\n调试信息：${error.javaClass.simpleName} · ${source?.fileName ?: "network"}:${source?.lineNumber ?: 0}"
    }

    private fun reportFailure(error: Exception) {
        if (BuildConfig.DEBUG) {
            // Log only exception type and source locations, never exception messages or request URLs.
            Log.w("StudentMail", "${error.javaClass.simpleName}: " + error.stackTrace.take(6)
                .joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" })
        }
    }
}

internal fun isMailAddress(value: String): Boolean =
    Regex("^[^\\s<>@,;]+@[^\\s<>@,;]+\\.[^\\s<>@,;]+$").matches(mailAddressOf(value))

internal fun mailAddressOf(value: String): String = Regex("<([^<>]+)>\\s*$").find(value)?.groupValues?.get(1)?.trim() ?: value.trim()
