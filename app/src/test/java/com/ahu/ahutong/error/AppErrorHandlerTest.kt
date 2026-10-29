package com.ahu.ahutong.error

import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.SentryEvent
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import com.google.gson.JsonParser
import java.util.Collections
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppErrorHandlerTest {
    private val envelopes = Collections.synchronizedList(mutableListOf<SentryEnvelope>())

    @Before fun setup() {
        FeedbackDiagnostics.clear()
        Sentry.init(options())
    }

    private fun options(uncaught: Boolean = false) = SentryOptions().apply {
        dsn = "https://public@example.invalid/1"
        isEnableUncaughtExceptionHandler = uncaught
        isEnableShutdownHook = false
        isSendClientReports = false
        flushTimeoutMillis = 10
        setBeforeSend(AppErrorHandler::beforeSend)
        setTransportFactory { _, _ -> object : ITransport {
            override fun send(envelope: SentryEnvelope, hint: Hint) { envelopes.add(envelope) }
            override fun flush(timeoutMillis: Long) = Unit
            override fun close() = Unit
            override fun close(isRestarting: Boolean) = Unit
            override fun getRateLimiter(): RateLimiter? = null
            override fun isHealthy() = true
        } }
    }

    @After fun close() {
        FeedbackDiagnostics.setRecordListener(null)
        FeedbackDiagnostics.clear()
        Sentry.close()
    }

    @Test fun handledFailureIncludesCauseAndSafeLogsWithoutNetworkBodies() {
        FeedbackDiagnostics.recordRuntime("opening schedule token=private-token")
        FeedbackDiagnostics.recordNetwork("network summary", "private-network-body")
        val cause = IllegalArgumentException("password=private-password")
        val error = IllegalStateException("parser failed", cause)
        error.addSuppressed(IllegalStateException("suppressed marker"))
        val id = AppErrorHandler.captureHandled(error, "coroutine")
        assertNotNull(id)
        val envelope = envelopes.single { it.header.eventId.toString() == id }
        val attachments = envelope.items.filter { it.header.type == SentryItemType.Attachment }
        assertEquals(setOf("crash-runtime.txt", "crash-errors.txt"), attachments.map { it.header.fileName }.toSet())
        val text = envelope.items.joinToString { it.data.toString(Charsets.UTF_8) }
        assertTrue(text.contains("opening schedule"))
        assertTrue(text.contains("suppressed marker"))
        assertTrue(text.contains("IllegalArgumentException"))
        assertFalse(text.contains("private-token"))
        assertFalse(text.contains("private-password"))
        assertFalse(text.contains("private-network-body"))
        Sentry.captureMessage("unrelated message")
        assertFalse(envelopes.last().items.any { it.header.type == SentryItemType.Attachment })
    }

    @Test fun cancellationDoesNotCreateAnErrorOrLog() {
        assertNull(AppErrorHandler.captureHandled(CancellationException("normal cancellation"), "coroutine"))
        assertTrue(envelopes.isEmpty())
        assertEquals("", FeedbackDiagnostics.snapshot(DiagnosticCategory.ERROR))
    }

    @Test fun actualSdkFatalChainCapturesOnceAndKeepsOriginalFailure() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val error = IllegalStateException("fatal sdk marker")
        var delegated: Throwable? = null
        var terminated = false
        try {
            Sentry.close()
            Thread.setDefaultUncaughtExceptionHandler { _, actual -> delegated = actual }
            Sentry.init(options(uncaught = true))
            DiagnosticUncaughtExceptionHandler(
                Thread.getDefaultUncaughtExceptionHandler(),
                record = { _, actual ->
                    FeedbackDiagnostics.recordError(UnexpectedErrorReportFormatter.format(actual, "uncaught"))
                },
                terminate = { terminated = true }
            ).uncaughtException(Thread.currentThread(), error)
            assertSame(error, delegated)
            assertTrue(terminated)
            assertEquals(1, envelopes.size)
            val envelope = envelopes.single()
            val event = JsonParser.parseString(envelope.items.first { it.header.type == SentryItemType.Event }
                .data.toString(Charsets.UTF_8)).asJsonObject
            assertEquals("fatal", event["level"].asString)
            assertFalse(event["exception"].asJsonObject["values"].asJsonArray.last().asJsonObject
                ["mechanism"].asJsonObject["handled"].asBoolean)
            assertTrue(envelope.items.any { it.header.fileName == "crash-errors.txt" })
        } finally {
            Sentry.close()
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }

    @Test fun installedBridgeKeepsNativeSummariesAndClearsAccountBreadcrumbs() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        try {
            AppErrorHandler.install()
            val installed = Thread.getDefaultUncaughtExceptionHandler()
            AppErrorHandler.install()
            assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
            FeedbackDiagnostics.setOnClearListener { AppErrorHandler.clearBreadcrumbs() }
            FeedbackDiagnostics.recordRuntime("old account marker token=private-token")
            FeedbackDiagnostics.recordNative(FeedbackDiagnostics.currentGeneration(), 1, "ahutong_rs", "native summary {private-body}")
            val id = Sentry.captureException(IllegalStateException("first error"))
            val event = envelopes.single { it.header.eventId == id }.items.first { it.header.type == SentryItemType.Event }
                .data.toString(Charsets.UTF_8)
            assertTrue(event.contains("old account marker"))
            assertTrue(event.contains("native summary"))
            assertFalse(event.contains("private-token"))
            assertFalse(event.contains("private-body"))
            FeedbackDiagnostics.clear()
            val next = Sentry.captureException(IllegalArgumentException("new account error"))
            val nextEvent = envelopes.single { it.header.eventId == next }.items.first { it.header.type == SentryItemType.Event }
                .data.toString(Charsets.UTF_8)
            assertFalse(nextEvent.contains("old account marker"))
            assertFalse(nextEvent.contains("native summary"))
        } finally {
            FeedbackDiagnostics.setOnClearListener(null)
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }

    @Test fun previousProcessAnrDoesNotReceiveLogsFromCurrentStartup() {
        FeedbackDiagnostics.recordRuntime("new process startup")
        for (type in listOf("AppExitInfo", "HistoricalAppExitInfo", "Tombstone", "TombstoneMerged")) {
            val event = SentryEvent().apply {
                exceptions = listOf(SentryException().apply { mechanism = Mechanism().apply { this.type = type } })
            }
            val hint = Hint()
            assertSame(event, AppErrorHandler.beforeSend(event, hint))
            assertTrue(hint.attachments.isEmpty())
        }
    }

    @Test fun uncaughtFailureIsRecordedThenDelegatedOnceAndTerminated() {
        val error = AssertionError("fatal marker")
        val thread = Thread.currentThread()
        val calls = mutableListOf<String>()
        val handler = DiagnosticUncaughtExceptionHandler(
            previous = Thread.UncaughtExceptionHandler { actualThread, actualError ->
                assertSame(thread, actualThread)
                assertSame(error, actualError)
                calls += "delegate"
            },
            record = { _, actualError -> assertSame(error, actualError); calls += "record" },
            terminate = { calls += "terminate" }
        )
        handler.uncaughtException(thread, error)
        assertEquals(listOf("record", "delegate", "terminate"), calls)
    }

    @Test fun diagnosticFailureCannotPreventOriginalHandlerOrTermination() {
        val original = OutOfMemoryError("original")
        var delegated: Throwable? = null
        var terminated = false
        DiagnosticUncaughtExceptionHandler(
            Thread.UncaughtExceptionHandler { _, error -> delegated = error },
            record = { _, _ -> throw OutOfMemoryError("diagnostics exhausted") },
            terminate = { terminated = true }
        ).uncaughtException(Thread.currentThread(), original)
        assertSame(original, delegated)
        assertTrue(terminated)
    }

    @Test fun missingOrBrokenPredecessorStillTerminates() {
        var terminations = 0
        DiagnosticUncaughtExceptionHandler(null, { _, _ -> }, { terminations++ })
            .uncaughtException(Thread.currentThread(), Error("fatal"))
        val broken = IllegalStateException("broken predecessor")
        try {
            DiagnosticUncaughtExceptionHandler(
                Thread.UncaughtExceptionHandler { _, _ -> throw broken }, { _, _ -> }, { terminations++ }
            ).uncaughtException(Thread.currentThread(), Error("fatal"))
            fail("expected predecessor failure")
        } catch (actual: IllegalStateException) { assertSame(broken, actual) }
        assertEquals(2, terminations)
    }
}
