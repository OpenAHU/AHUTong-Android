package com.ahu.ahutong.data.mail

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import org.junit.Assert.*
import org.junit.Test

class StudentMailSessionStoreTest {
    private val host = "mail.stu.ahu.edu.cn"
    private fun record(cookies: List<Cookie>) = StoredMailSession("fixture-account", "fixture-sid", "sender@example.test", cookies)
    private fun cookie() = Cookie.Builder().name("Coremail").value("synthetic-only")
        .hostOnlyDomain(host).path("/").secure().httpOnly().build()

    @Test fun `round trip preserves cookie scope flags and session cookie semantics`() {
        val sessionCookie = cookie()
        val expiring = Cookie.Builder().name("api").value("fixture").domain(host).path("/js6")
            .secure().expiresAt(2_000_000_000_000L).build()
        val restored = MailSessionCodec.decode(MailSessionCodec.encode(record(listOf(sessionCookie, expiring))), "fixture-account")!!
        assertEquals("fixture-sid", restored.sid)
        assertEquals("sender@example.test", restored.email)
        assertTrue(restored.cookies[0].hostOnly)
        assertTrue(restored.cookies[0].httpOnly)
        assertTrue(restored.cookies[0].secure)
        assertFalse(restored.cookies[0].persistent)
        assertFalse(restored.cookies[1].hostOnly)
        assertEquals("/js6", restored.cookies[1].path)
        assertEquals(expiring.expiresAt, restored.cookies[1].expiresAt)
    }

    @Test fun `other account malformed records and unknown versions cannot restore credentials`() {
        val encoded = MailSessionCodec.encode(record(listOf(cookie())))
        assertNull(MailSessionCodec.decode(encoded, "other-account"))
        assertNull(MailSessionCodec.decode(encoded, ""))
        assertNull(MailSessionCodec.decode("invalid-json", "fixture-account"))
        assertNull(MailSessionCodec.decode(encoded.replace("\"version\":1", "\"version\":2"), "fixture-account"))
    }

    @Test fun `expired cookies and SSO cookies never restore as mailbox credentials`() {
        val expired = Cookie.Builder().name("Coremail").value("fixture").hostOnlyDomain(host).expiresAt(1_000).build()
        val entry = Cookie.Builder().name("ENTRY").value("fixture").hostOnlyDomain("entryhz.qiye.163.com").build()
        assertNull(MailSessionCodec.decode(MailSessionCodec.encode(record(listOf(expired, entry))), "fixture-account", 2_000))
        val valid = MailSessionCodec.decode(MailSessionCodec.encode(record(listOf(expired, entry, cookie()))), "fixture-account", 2_000)!!
        assertEquals(listOf("Coremail"), valid.cookies.map { it.name })
    }

    @Test fun `expired read renews once and uses its new result`() = runBlocking {
        var renewals = 0
        var reads = 0
        val result = recoverMailRead(renew = { renewals++ }) {
            if (++reads == 1) throw MailSessionExpired()
            "fresh-result"
        }
        assertEquals("fresh-result", result)
        assertEquals(1, renewals)
        assertEquals(2, reads)
    }

    @Test fun `repeated rejection stops after a single renewal`() = runBlocking {
        var renewals = 0
        var reads = 0
        val failure = runCatching { recoverMailRead(renew = { renewals++ }) {
            reads++
            throw MailSessionExpired()
        } }.exceptionOrNull()
        assertTrue(failure is MailSessionExpired)
        assertEquals(1, renewals)
        assertEquals(2, reads)
    }

    @Test fun `transport failures and cancellation never trigger authentication renewal`() = runBlocking {
        listOf(IOException("synthetic network failure"), CancellationException("cancelled")).forEach { original ->
            var renewals = 0
            val failure = runCatching { recoverMailRead(renew = { renewals++ }) { throw original } }.exceptionOrNull()
            assertSame(original, failure)
            assertEquals(0, renewals)
        }
    }
}
