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
    private val generation: () -> Long,
    private val snapshotPreSanitized: Boolean = false
) {
    internal data class PreviewRequest(
        val description: String,
        val categories: Set<DiagnosticCategory>,
        val errorDetails: String?,
        val errorEventId: String?,
        val generation: Long
    )

    internal data class SubmitRequest(
        val description: String,
        val categories: Set<DiagnosticCategory>,
        val errorDetails: String?,
        val errorEventId: String?,
        val prebuiltSubmission: Submission?,
        val generation: Long
    )

    internal data class ExportRequest(
        val categories: Set<DiagnosticCategory>,
        val errorDetails: String?,
        val generation: Long
    )

    private val mutableState = MutableStateFlow(FeedbackUiState())
    val state = mutableState.asStateFlow()
    private var accountGeneration = generation()
    private var errorDetails: String? = null
    private var errorEventId: String? = null
    private var previewSubmission: Submission? = null

    private fun isBusy(): Boolean = state.value.submitting || state.value.previewLoading

    @Synchronized fun setDescription(value: String) {
        ensureAccount()
        if (isBusy()) return
        previewSubmission = null
        mutableState.value = state.value.copy(description = value.take(4096), status = null, feedbackId = null, preview = null)
    }

    @Synchronized fun setCategory(category: DiagnosticCategory, selected: Boolean) {
        ensureAccount()
        if (isBusy()) return
        previewSubmission = null
        val categories = state.value.selectedCategories.toMutableSet()
        if (selected) categories.add(category) else categories.remove(category)
        mutableState.value = state.value.copy(selectedCategories = categories, preview = null, status = null, feedbackId = null)
    }

    @Synchronized fun attachError(details: String?, eventId: String?) {
        ensureAccount()
        if (isBusy() || details.isNullOrBlank()) return
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

    @Synchronized fun beginPreview(): PreviewRequest? {
        ensureAccount()
        if (isBusy()) return null
        if (state.value.description.isBlank()) {
            previewSubmission = null
            mutableState.value = state.value.copy(preview = null, status = "请先填写问题描述")
            return null
        }
        mutableState.value = state.value.copy(previewLoading = true, status = null)
        return PreviewRequest(
            description = state.value.description,
            categories = state.value.selectedCategories.toSet(),
            errorDetails = errorDetails,
            errorEventId = errorEventId,
            generation = accountGeneration
        )
    }

    fun completePreview(request: PreviewRequest) {
        try {
            val selectedAttachments = attachments(request.categories, request.errorDetails)
            val text = selectedAttachments.joinToString("\n\n") { "【${logLabel(it.filename)}】\n${it.text}" }
                .ifBlank { "未选择日志，将只提交问题描述。" }
            val message = DiagnosticSanitizer.passwords(request.description)
            val submission = Submission(
                message,
                selectedAttachments,
                request.errorDetails.takeIf { DiagnosticCategory.ERROR in request.categories },
                request.errorEventId,
                request.generation
            )
            val preview = "问题描述：\n$message\n\n$COLLECTION_SCOPE\n\n$text"
            val truncated = preview.take(PREVIEW_LIMIT) +
                if (preview.length > PREVIEW_LIMIT) "\n[预览已截断]" else ""
            synchronized(this) {
                if (!isCurrent(request.generation)) return
                previewSubmission = submission
                mutableState.value = state.value.copy(previewLoading = false, preview = truncated)
            }
        } catch (error: Exception) {
            synchronized(this) {
                if (isCurrent(request.generation)) {
                    mutableState.value = state.value.copy(previewLoading = false)
                }
            }
            if (error is CancellationException) throw error
        }
    }

    fun showPreview() {
        val request = beginPreview() ?: return
        completePreview(request)
    }

    @Synchronized fun dismissPreview() {
        if (isBusy()) return
        previewSubmission = null
        mutableState.value = state.value.copy(preview = null)
    }

    @Synchronized fun reportExportStatus(message: String) {
        ensureAccount()
        if (!state.value.submitting) mutableState.value = state.value.copy(exportStatus = message)
    }

    @Synchronized fun beginSubmit(): SubmitRequest? {
        ensureAccount()
        if (isBusy() || state.value.feedbackId != null) return null
        if (state.value.description.isBlank()) {
            mutableState.value = state.value.copy(status = "请先填写问题描述")
            return null
        }
        val prebuilt = previewSubmission?.takeIf { it.generation == accountGeneration }
            ?.copy(errorId = errorEventId)
        mutableState.value = state.value.copy(submitting = true, status = "正在提交…")
        return SubmitRequest(
            description = state.value.description,
            categories = state.value.selectedCategories.toSet(),
            errorDetails = errorDetails,
            errorEventId = errorEventId,
            prebuiltSubmission = prebuilt,
            generation = accountGeneration
        )
    }

    fun completeSubmit(request: SubmitRequest) {
        try {
            val submission = request.prebuiltSubmission ?: Submission(
                DiagnosticSanitizer.passwords(request.description),
                attachments(request.categories, request.errorDetails),
                request.errorDetails.takeIf { DiagnosticCategory.ERROR in request.categories },
                request.errorEventId,
                request.generation
            )
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
                mutableState.value = state.value.copy(
                    submitting = false,
                    feedbackId = id,
                    status = if (id != null) "已受理，联网后发送。" else "暂时无法提交，请重试。"
                )
                if (id != null) {
                    errorDetails = null
                    errorEventId = null
                }
            }
        } catch (error: Exception) {
            synchronized(this) {
                if (isCurrent(request.generation)) {
                    mutableState.value = state.value.copy(submitting = false, status = "提交失败，请重试。")
                }
            }
            if (error is CancellationException) throw error
        }
    }

    fun submit() {
        val request = beginSubmit() ?: return
        completeSubmit(request)
    }

    @Synchronized fun beginExport(): ExportRequest? {
        ensureAccount()
        if (isBusy()) return null
        mutableState.value = state.value.copy(submitting = true, exportStatus = "正在加密…", preview = null)
        return ExportRequest(
            categories = state.value.selectedCategories.toSet(),
            errorDetails = errorDetails,
            generation = accountGeneration
        )
    }

    fun buildExportText(request: ExportRequest): String = buildString {
        appendLine("AHUTong 问题反馈日志")
        appendLine(COLLECTION_SCOPE)
        attachments(request.categories, request.errorDetails).forEach {
            appendLine("\n【${logLabel(it.filename)}】")
            appendLine(it.text)
        }
    }

    fun exportText(): String? {
        val request = beginExport() ?: return null
        return buildExportText(request)
    }

    @Synchronized fun finishExport(
        success: Boolean,
        expectedGeneration: Long,
        keepSubmitting: Boolean = false
    ): Boolean {
        if (!isCurrent(expectedGeneration)) return false
        mutableState.value = state.value.copy(
            submitting = keepSubmitting && success,
            exportStatus = if (success) "日志已加密。" else "加密失败，请重试。"
        )
        return true
    }

    @Synchronized fun finishExportAction(expectedGeneration: Long, message: String? = null) {
        if (!isCurrent(expectedGeneration)) return
        mutableState.value = state.value.copy(
            submitting = false,
            exportStatus = message ?: state.value.exportStatus
        )
    }

    @Synchronized fun clear() {
        accountGeneration = generation()
        errorDetails = null
        errorEventId = null
        previewSubmission = null
        mutableState.value = FeedbackUiState()
    }

    private fun attachments(
        categories: Set<DiagnosticCategory>,
        capturedError: String?
    ): List<FeedbackAttachment> = DiagnosticCategory.entries
        .filter { it in categories }
        .map { category ->
            val snapshotText = snapshot(category)
            val content = if (snapshotPreSanitized) {
                buildString {
                    if (category == DiagnosticCategory.RUNTIME) {
                        appendLine(DiagnosticSanitizer.safe(environment()))
                    }
                    if (snapshotText.isNotEmpty()) {
                        append(DiagnosticSanitizer.redactKnownPasswords(snapshotText))
                    }
                    if (category == DiagnosticCategory.ERROR) {
                        capturedError?.let { append("\n\n").append(DiagnosticSanitizer.redactKnownPasswords(it)) }
                    }
                }.ifBlank { "暂无记录" }
            } else {
                val raw = buildString {
                    if (category == DiagnosticCategory.RUNTIME) appendLine(environment())
                    append(snapshotText)
                    if (category == DiagnosticCategory.ERROR) capturedError?.let { append("\n\n$it") }
                }.ifBlank { "暂无记录" }
                if (category == DiagnosticCategory.NETWORK) DiagnosticSanitizer.passwords(raw)
                else DiagnosticSanitizer.safe(raw)
            }
            FeedbackAttachment(
                when (category) {
                    DiagnosticCategory.RUNTIME -> "runtime.txt"
                    DiagnosticCategory.ERROR -> "errors.txt"
                    DiagnosticCategory.NETWORK -> "network.txt"
                },
                content
            )
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

    internal data class Submission(
        val message: String,
        val attachments: List<FeedbackAttachment>,
        val error: String?,
        val errorId: String?,
        val generation: Long
    )

    private companion object {
        const val PREVIEW_LIMIT = 32_000
        const val COLLECTION_SCOPE = "采集范围：应用与 Rust 日志及 OkHttp 请求；不含 WebView / Rust 内部完整网络。"
    }
}
