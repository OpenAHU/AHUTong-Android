package com.ahu.ahutong.feedback

import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import com.google.gson.JsonParser
import java.util.Collections
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercises the pinned Android SDK's Java core and actual feedback envelope, without a network. */
class SentryFeedbackTransportTest {
    private val envelopes = Collections.synchronizedList(mutableListOf<SentryEnvelope>())
    @Before fun setup() {
        Sentry.init(SentryOptions().apply {
            dsn = "https://public@example.invalid/1"
            isEnableUncaughtExceptionHandler = false
            isEnableShutdownHook = false
            isSendClientReports = false
            setTransportFactory { _, _ -> object : ITransport {
                override fun send(envelope: SentryEnvelope, hint: Hint) { envelopes.add(envelope) }
                override fun flush(timeoutMillis: Long) = Unit
                override fun close() = Unit
                override fun close(isRestarting: Boolean) = Unit
                override fun getRateLimiter(): RateLimiter? = null
                override fun isHealthy() = true
            } }
        })
    }
    @After fun close() { Sentry.close() }

    @Test fun feedbackAssociatesExactEventAndAttachmentsDoNotLeakToOtherEvents() {
        Sentry.configureScope { it.addAttachment(Attachment("global secret".toByteArray(), "global.txt")) }
        val transport = SentryFeedbackTransport()
        val errorId = transport.captureError("parser failed password=synthetic-password")!!
        val feedbackId = transport.captureFeedback("无法打开页面", errorId,
            listOf(FeedbackAttachment("runtime.txt", "safe runtime")))!!
        val feedbackEnvelope = envelopes.single { it.header.eventId.toString() == feedbackId }
        val serialized = feedbackEnvelope.items.first { it.header.type != SentryItemType.Attachment }.data.toString(Charsets.UTF_8)
        val json = JsonParser.parseString(serialized).asJsonObject
        assertEquals(errorId, json["contexts"].asJsonObject["feedback"].asJsonObject["associated_event_id"].asString)
        assertEquals(listOf("runtime.txt"), feedbackEnvelope.items.filter { it.header.type == SentryItemType.Attachment }.map { it.header.fileName })
        val errorEnvelope = envelopes.single { it.header.eventId.toString() == errorId }
        assertFalse(errorEnvelope.items.any { it.header.type == SentryItemType.Attachment })
        assertFalse(errorEnvelope.items.joinToString { it.data.toString(Charsets.UTF_8) }.contains("synthetic-password"))
        val unrelatedId = Sentry.captureMessage("unrelated event")
        val unrelated = envelopes.single { it.header.eventId == unrelatedId }
        assertEquals(listOf("global.txt"), unrelated.items.filter { it.header.type == SentryItemType.Attachment }.map { it.header.fileName })
    }
}
