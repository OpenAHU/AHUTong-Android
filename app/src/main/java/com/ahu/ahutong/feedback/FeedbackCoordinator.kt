package com.ahu.ahutong.feedback

import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer
import com.ahu.ahutong.ui.screen.settings.FeedbackUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-local draft survives leaving the page and returning after reproducing a problem. */
internal class FeedbackCoordinator(
    private val transport: FeedbackTransport,
    private val snapshot: (DiagnosticCategory) -> String,
    private val environment: () -> String,
    private val generation: () -> Long
) {
    private val mutableState = MutableStateFlow(FeedbackUiState())
    val state = mutableState.asStateFlow()
    private var accountGeneration = generation()
    private var errorDetails: String? = null
    private var errorEventId: String? = null
    private var previewSubmission: Submission? = null

    @Synchronized fun setDescription(value: String) {
        ensureAccount()
        if (state.value.submitting) return
        previewSubmission = null
        mutableState.value = state.value.copy(description = value.take(4096), status = null, feedbackId = null, preview = null)
    }

    @Synchronized fun setCategory(category: DiagnosticCategory, selected: Boolean) {
        ensureAccount()
        if (state.value.submitting) return
        previewSubmission = null
        val categories = state.value.selectedCategories.toMutableSet()
        if (selected) categories.add(category) else categories.remove(category)
        mutableState.value = state.value.copy(selectedCategories = categories, preview = null, status = null, feedbackId = null)
    }

    @Synchronized fun attachError(details: String?, eventId: String?) {
        ensureAccount()
        if (state.value.submitting || details.isNullOrBlank()) return
        val safe = DiagnosticSanitizer.safe(details).take(48_000)
        if (errorDetails == safe) {
            eventId?.takeIf(::isSentryEventId)?.let { errorEventId = it }
            return
        }
        errorDetails = safe
        errorEventId = eventId?.takeIf(::isSentryEventId)
        previewSubmission = null
        mutableState.value = state.value.copy(status = null, feedbackId = null, preview = null)
    }

    @Synchronized fun beginEntry(details: String?, eventId: String?) {
        ensureAccount()
        if (state.value.feedbackId != null) clear()
        attachError(details, eventId)
    }

    @Synchronized fun showPreview() {
        ensureAccount()
        if (state.value.submitting) return
        val selectedAttachments = attachments()
        val text = selectedAttachments.joinToString("\n\n") { "【${logLabel(it.filename)}】\n${it.text}" }
            .ifBlank { "未选择日志，将只提交问题描述。" }
        val message = DiagnosticSanitizer.passwords(state.value.description)
        previewSubmission = Submission(message, selectedAttachments,
            errorDetails.takeIf { DiagnosticCategory.ERROR in state.value.selectedCategories },
            errorEventId, accountGeneration)
        val preview = "问题描述：\n$message\n\n$COLLECTION_SCOPE\n\n$text"
        mutableState.value = state.value.copy(preview = preview.take(PREVIEW_LIMIT) +
            if (preview.length > PREVIEW_LIMIT) "\n[预览已截断]" else "")
    }

    @Synchronized fun dismissPreview() {
        if (state.value.submitting) return
        previewSubmission = null
        mutableState.value = state.value.copy(preview = null)
    }

    @Synchronized fun reportExportStatus(message: String) {
        ensureAccount()
        if (!state.value.submitting) mutableState.value = state.value.copy(exportStatus = message)
    }

    fun submit() {
        val submission = synchronized(this) {
            ensureAccount()
            if (state.value.submitting || state.value.feedbackId != null) return
            if (state.value.description.isBlank()) {
                mutableState.value = state.value.copy(status = "请先填写问题描述")
                return
            }
            val selectedError = errorDetails.takeIf { DiagnosticCategory.ERROR in state.value.selectedCategories }
            val current = previewSubmission?.takeIf { it.generation == accountGeneration }
                ?.copy(errorId = errorEventId)
                ?: Submission(DiagnosticSanitizer.passwords(state.value.description), attachments(), selectedError, errorEventId, accountGeneration)
            mutableState.value = state.value.copy(submitting = true, status = "正在提交…")
            current
        }
        try {
            val associatedId = if (submission.error != null) {
                submission.errorId ?: runCatching { transport.captureError(submission.error) }.getOrNull()
            } else null
            synchronized(this) {
                if (!isCurrent(submission.generation)) return
                if (submission.error != null) errorEventId = associatedId
            }
            val id = transport.captureFeedback(submission.message, associatedId, submission.attachments)
            synchronized(this) {
                if (!isCurrent(submission.generation)) return
                mutableState.value = state.value.copy(submitting = false, feedbackId = id,
                    status = if (id != null) "已受理，联网后发送。" else "暂时无法提交，请重试。")
                if (id != null) {
                    errorDetails = null
                    errorEventId = null
                }
            }
        } catch (error: Exception) {
            synchronized(this) {
                if (isCurrent(submission.generation)) mutableState.value = state.value.copy(
                    submitting = false, status = "提交失败，请重试。"
                )
            }
            if (error is CancellationException) throw error
        }
    }

    @Synchronized fun exportText(): String? {
        ensureAccount()
        if (state.value.submitting) return null
        mutableState.value = state.value.copy(submitting = true, exportStatus = "正在加密…", preview = null)
        return buildString {
            appendLine("AHUTong 问题反馈日志")
            appendLine(COLLECTION_SCOPE)
            attachments().forEach { appendLine("\n【${logLabel(it.filename)}】"); appendLine(it.text) }
        }
    }

    @Synchronized fun finishExport(success: Boolean, expectedGeneration: Long): Boolean {
        if (!isCurrent(expectedGeneration)) return false
        mutableState.value = state.value.copy(submitting = false,
            exportStatus = if (success) "日志已加密。" else "加密失败，请重试。")
        return true
    }

    @Synchronized fun clear() {
        accountGeneration = generation()
        errorDetails = null
        errorEventId = null
        previewSubmission = null
        mutableState.value = FeedbackUiState()
    }

    private fun attachments(): List<FeedbackAttachment> = DiagnosticCategory.entries
        .filter { it in state.value.selectedCategories }
        .map { category ->
            val text = buildString {
                if (category == DiagnosticCategory.RUNTIME) appendLine(environment())
                append(snapshot(category))
                if (category == DiagnosticCategory.ERROR) errorDetails?.let { append("\n\n$it") }
            }.ifBlank { "暂无记录" }
            FeedbackAttachment(when (category) {
                DiagnosticCategory.RUNTIME -> "runtime.txt"
                DiagnosticCategory.ERROR -> "errors.txt"
                DiagnosticCategory.NETWORK -> "network.txt"
            }, if (category == DiagnosticCategory.NETWORK) DiagnosticSanitizer.passwords(text) else DiagnosticSanitizer.safe(text))
        }

    private fun ensureAccount() { if (accountGeneration != generation()) clear() }
    private fun logLabel(filename: String): String = when (filename) {
        "runtime.txt" -> "运行记录"
        "errors.txt" -> "错误信息"
        "network.txt" -> "网络详情"
        else -> filename
    }
    private fun isCurrent(expected: Long): Boolean {
        ensureAccount()
        return expected == accountGeneration
    }

    private data class Submission(val message: String, val attachments: List<FeedbackAttachment>, val error: String?, val errorId: String?, val generation: Long)
    private companion object {
        const val PREVIEW_LIMIT = 32_000
        const val COLLECTION_SCOPE = "采集范围：应用与 Rust 日志及 OkHttp 请求；不含 WebView / Rust 内部完整网络。"
    }
}
