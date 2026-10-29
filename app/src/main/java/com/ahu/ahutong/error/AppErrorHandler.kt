package com.ahu.ahutong.error

import android.os.Process
import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import io.sentry.Attachment
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.hints.Cached
import io.sentry.protocol.SentryId
import io.sentry.util.HintUtils
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** One capture owner: Sentry handles fatal events; this layer adds bounded, redacted diagnostics. */
object AppErrorHandler {
    private const val LOG_LIMIT = 96_000

    fun install() {
        FeedbackDiagnostics.setRecordListener { category, message ->
            if (Sentry.isEnabled()) Sentry.addBreadcrumb(Breadcrumb().apply {
                this.category = "app.diagnostics.${category.name.lowercase()}"
                this.message = message
                level = if (category == DiagnosticCategory.ERROR) SentryLevel.ERROR else SentryLevel.INFO
            })
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous !is DiagnosticUncaughtExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(DiagnosticUncaughtExceptionHandler(
                previous = previous,
                record = { thread, error -> record(error, "uncaught / ${thread.name}") },
                terminate = {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            ))
        }
    }

    fun clearBreadcrumbs() {
        Sentry.configureScope { it.clearBreadcrumbs() }
    }

    fun captureHandled(error: Throwable, source: String): String? {
        if (error is CancellationException) return null
        return try {
            record(error, source)
            if (!Sentry.isEnabled()) return null
            Sentry.captureException(error) { it.setTag("error.source", source) }
                .takeUnless { it == SentryId.EMPTY_ID }?.toString()
        } catch (_: Throwable) {
            // Diagnostic failures must never replace the original failure or break the UI handler.
            null
        }
    }

    private fun record(error: Throwable, source: String) {
        FeedbackDiagnostics.recordError(DiagnosticSanitizer.safe(
            UnexpectedErrorReportFormatter.format(error, "错误入口：$source")
        ))
    }

    fun beforeSend(event: SentryEvent, hint: Hint): SentryEvent {
        try {
            // Backfilled crashes/ANRs carry their own SDK-persisted context. Current process logs
            // would describe the restart, not the process that failed.
            if (HintUtils.hasType(hint, Cached::class.java) || event.exceptions.orEmpty().any {
                    it.mechanism?.type in setOf("AppExitInfo", "HistoricalAppExitInfo", "Tombstone", "TombstoneMerged")
                }) return event
            if (event.throwable == null && event.exceptions.isNullOrEmpty()) return event
            event.exceptions?.forEach { exception ->
                exception.value = exception.value?.let(DiagnosticSanitizer::safe)
            }
            listOf(DiagnosticCategory.RUNTIME to "crash-runtime.txt", DiagnosticCategory.ERROR to "crash-errors.txt")
                .forEach { (category, filename) ->
                    val snapshot = FeedbackDiagnostics.snapshot(category, LOG_LIMIT)
                    if (snapshot.isNotBlank()) hint.addAttachment(
                        Attachment(snapshot.toByteArray(Charsets.UTF_8), filename, "text/plain")
                    )
                }
        } catch (_: Throwable) {
            // Includes OOM and a broken formatter: returning the event preserves SDK crash handling.
        }
        return event
    }
}

internal class DiagnosticUncaughtExceptionHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val record: (Thread, Throwable) -> Unit,
    private val terminate: () -> Unit
) : Thread.UncaughtExceptionHandler {
    private val recording = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            if (recording.compareAndSet(false, true)) record(thread, error)
        } catch (_: Throwable) {
            // Never recursively report a failure in the crash reporter.
        } finally {
            try {
                previous?.uncaughtException(thread, error)
            } finally {
                // Android normally terminates inside its handler; also cover an absent/broken chain.
                terminate()
            }
        }
    }
}
