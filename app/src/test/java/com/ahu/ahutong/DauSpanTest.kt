package com.ahu.ahutong

import android.content.SharedPreferences
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.TransactionOptions
import io.sentry.protocol.SentryTransaction
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import java.io.StringReader
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercise the real application entry point and pinned SDK with an in-memory transport. */
class DauSpanTest {
    private val envelopes = Collections.synchronizedList(mutableListOf<SentryEnvelope>())
    private lateinit var options: SentryOptions

    @Before fun configureSentry() {
        options = SentryOptions().apply {
            dsn = "https://public@example.invalid/1"
            tracesSampleRate = 0.0
            isEnableUncaughtExceptionHandler = false
            isEnableShutdownHook = false
            isSendClientReports = false
            setTransportFactory(io.sentry.ITransportFactory { _, _ ->
                object : ITransport {
                    override fun send(envelope: SentryEnvelope, hint: Hint) { envelopes.add(envelope) }
                    override fun flush(timeoutMillis: Long) = Unit
                    override fun getRateLimiter(): RateLimiter? = null
                    override fun close() = Unit
                    override fun close(isRestarting: Boolean) = Unit
                }
            })
        }
        Sentry.init(options)
    }

    @After fun closeSentry() { Sentry.close() }

    @Test fun `DAU uses a sampled independent span and leaves the screen trace untouched`() {
        val app = TestApplication()
        val parent = Sentry.startTransaction("MainActivity", "ui.load", TransactionOptions().apply { isBindToScope = true })
        try {
            assertEquals(false, parent.isSampled)
            app.reportDauIfNeeded()
            assertSame(parent, Sentry.getSpan())
            val transaction = transactions().single()
            val trace = transaction.contexts.trace!!
            assertEquals("AHUTong DAU", transaction.transaction)
            assertEquals("app.dau", trace.operation)
            assertEquals(SpanStatus.OK, trace.status)
            assertNull(trace.parentSpanId)
            assertNotEquals(parent.spanContext.traceId, trace.traceId)
            assertFalse(transaction.tags?.get("dau_id").isNullOrBlank())
            assertEquals(LocalDate.now().toString(), trace.data?.get("dau_date"))
            assertEquals(app.values["dau_id"], trace.data?.get("dau_id"))
            assertFalse(envelopes.flatMap { it.items }.any { it.header.type == SentryItemType.Event })
        } finally { parent.finish() }
    }

    @Test fun `same day foreground resumes deduplicate and the next day keeps the installation id`() {
        val app = TestApplication()
        app.reportDauIfNeeded()
        app.reportDauIfNeeded()
        assertEquals(1, transactions().size)
        val id = app.values["dau_id"]
        app.values["dau_last_span_report_date"] = LocalDate.now().minusDays(1).toString()
        app.reportDauIfNeeded()
        assertEquals(2, transactions().size)
        assertEquals(id, app.values["dau_id"])
    }

    @Test fun `a legacy message today does not suppress the first migrated span`() {
        val app = TestApplication()
        app.values["dau_last_report_date"] = LocalDate.now().toString()
        app.values["dau_id"] = "existing-installation"
        app.reportDauIfNeeded()
        assertEquals("existing-installation", transactions().single().tags?.get("dau_id"))
    }

    @Test fun `disabled SDK does not consume the daily report`() {
        val app = TestApplication()
        Sentry.close()
        app.reportDauIfNeeded()
        assertFalse(app.values.containsKey("dau_last_span_report_date"))
        configureSentry()
        app.reportDauIfNeeded()
        assertEquals(1, transactions().size)
    }

    @Test fun `concurrent resume work only emits one daily span`() {
        val app = TestApplication()
        val workers = Executors.newFixedThreadPool(4)
        try {
            val calls = (1..12).map { workers.submit { app.reportDauIfNeeded() } }
            calls.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, transactions().size)
        } finally { workers.shutdownNow() }
    }

    private fun transactions(): List<SentryTransaction> = envelopes.flatMap { it.items }
        .filter { it.header.type == SentryItemType.Transaction }
        .map { options.serializer.deserialize(StringReader(String(it.data, Charsets.UTF_8)), SentryTransaction::class.java)!! }

    private class TestApplication : AHUApplication() {
        val values = ConcurrentHashMap<String, String>()
        private val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args!![0] as String] = args[1] as String; proxy }
                "apply" -> null
                else -> error("Unexpected preference write: ${method.name}")
            }
        } as SharedPreferences.Editor
        private val preferences = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preference read: ${method.name}")
            }
        } as SharedPreferences
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
    }
}
