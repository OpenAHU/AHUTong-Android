package com.ahu.ahutong.feedback

import com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Feedback
import io.sentry.protocol.Message
import io.sentry.protocol.SentryId

internal data class FeedbackAttachment(val filename: String, val text: String)

internal interface FeedbackTransport {
    fun captureError(details: String): String?
    fun captureFeedback(message: String, errorId: String?, attachments: List<FeedbackAttachment>): String?
}

/** Attachments and scope changes belong only to this submission, including on failed retries. */
internal class SentryFeedbackTransport : FeedbackTransport {
    override fun captureError(details: String): String? {
        if (!Sentry.isEnabled()) return null
        val event = SentryEvent().apply {
            message = Message().apply { formatted = DiagnosticSanitizer.safe(details) }
            level = SentryLevel.ERROR
            setTag("source", "user_feedback")
        }
        return Sentry.captureEvent(event) { it.clear() }.validId()
    }

    override fun captureFeedback(
        message: String,
        errorId: String?,
        attachments: List<FeedbackAttachment>
    ): String? {
        if (!Sentry.isEnabled()) return null
        val feedback = Feedback(DiagnosticSanitizer.passwords(message)).apply {
            errorId?.takeIf(::isSentryEventId)?.let { setAssociatedEventId(SentryId(it)) }
        }
        val hint = Hint().apply {
            attachments.forEach {
                addAttachment(Attachment(it.text.toByteArray(Charsets.UTF_8), it.filename, "text/plain"))
            }
        }
        return Sentry.feedback().capture(feedback, hint) { scope -> scope.clear() }.validId()
    }

    private fun SentryId.validId(): String? = takeUnless { it == SentryId.EMPTY_ID }?.toString()
}

internal fun isSentryEventId(value: String): Boolean =
    value.matches(Regex("[0-9a-fA-F]{32}")) && value.any { it != '0' }
