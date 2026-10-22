package com.ahu.ahutong.data.xuexiaotong

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

class PersistentCookieJarTest {
    private var now = 1_000_000L
    private val persisted = AtomicReference<String?>(null)
    private val loginUrl = "https://passport2.chaoxing.com/".toHttpUrl()
    private val courseUrl = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction".toHttpUrl()
    private fun jar() = PersistentCookieJar(persisted::get, persisted::set) { now }

    @Test fun `cold restore retains secure host only and path matching`() {
        val original = Cookie.Builder().name("auth").value("token")
            .hostOnlyDomain(courseUrl.host).path("/mooc2-ans").secure().httpOnly()
            .expiresAt(now + 60_000).build()
        jar().saveFromResponse(courseUrl, listOf(original))
        val restarted = jar()

        assertEquals(listOf(original), restarted.loadForRequest(courseUrl))
        assertTrue(restarted.loadForRequest("http://mooc2-ans.chaoxing.com/mooc2-ans/visit".toHttpUrl()).isEmpty())
        assertTrue(restarted.loadForRequest("https://child.mooc2-ans.chaoxing.com/mooc2-ans/visit".toHttpUrl()).isEmpty())
        assertTrue(restarted.loadForRequest("https://mooc2-ans.chaoxing.com/other".toHttpUrl()).isEmpty())
    }

    @Test fun `cookies shared across response hosts replace by domain path and name`() {
        val subject = jar()
        val first = Cookie.Builder().name("auth").value("old").domain("chaoxing.com").build()
        subject.saveFromResponse(loginUrl, listOf(first))
        val refreshed = first.newBuilder().value("new").build()
        subject.saveFromResponse(courseUrl, listOf(refreshed))
        assertEquals(listOf(refreshed), subject.loadForRequest(courseUrl))
        assertEquals(listOf(refreshed), jar().loadForRequest(loginUrl))
    }

    @Test fun `deletion from another host removes a domain cookie permanently`() {
        val subject = jar()
        val cookie = Cookie.Builder().name("auth").value("old").domain("chaoxing.com").build()
        subject.saveFromResponse(loginUrl, listOf(cookie))
        subject.saveFromResponse(courseUrl, listOf(cookie.newBuilder().value("").expiresAt(0).build()))
        assertTrue(subject.loadForRequest(loginUrl).isEmpty())
        assertTrue(jar().loadForRequest(courseUrl).isEmpty())
    }

    @Test fun `same name cookies retain their paths and deleting one does not remove the other`() {
        val subject = jar()
        val root = Cookie.Builder().name("auth").value("root").domain("chaoxing.com").path("/").build()
        val specific = root.newBuilder().value("specific").path("/mooc2-ans").build()
        subject.saveFromResponse(courseUrl, listOf(root, specific))
        assertEquals(listOf(specific, root), jar().loadForRequest(courseUrl))
        subject.saveFromResponse(courseUrl, listOf(specific.newBuilder().expiresAt(0).build()))
        assertEquals(listOf(root), jar().loadForRequest(courseUrl))
    }

    @Test fun `expiration removes memory and disk entries instead of extending their lifetime`() {
        val subject = jar()
        val cookie = Cookie.Builder().name("auth").value("old").domain("chaoxing.com")
            .expiresAt(now + 100).build()
        subject.saveFromResponse(loginUrl, listOf(cookie))
        now += 100
        assertTrue(subject.loadForRequest(loginUrl).isEmpty())
        assertTrue(ChaoxingCookieCodec.decode(requireNotNull(persisted.get()), now - 100).isEmpty())
        assertTrue(jar().loadForRequest(loginUrl).isEmpty())
    }

    @Test fun `failed relogin snapshot rollback preserves attributes and drops cookies expired in flight`() {
        val subject = jar()
        val cookie = Cookie.Builder().name("auth").value("token").hostOnlyDomain(courseUrl.host)
            .path("/mooc2-ans").expiresAt(now + 1_000).secure().httpOnly().build()
        val expiring = cookie.newBuilder().name("short").expiresAt(now + 100).build()
        subject.saveFromResponse(courseUrl, listOf(cookie, expiring))
        val backup = subject.snapshot()
        subject.clear()
        now += 100
        subject.restoreSnapshot(backup)

        assertEquals(listOf(cookie), subject.loadForRequest(courseUrl))
        assertEquals(listOf(cookie), jar().loadForRequest(courseUrl))
        assertTrue(subject.loadForRequest("https://child.mooc2-ans.chaoxing.com/mooc2-ans".toHttpUrl()).isEmpty())
    }

    @Test fun `legacy disk cache is rewritten once as version two`() {
        persisted.set("""{"passport2.chaoxing.com":"auth|chaoxing.com=legacy"}""")
        val subject = jar()
        val rewritten = persisted.get()
        assertTrue(requireNotNull(rewritten).contains("\"version\":2"))
        assertEquals("legacy", subject.loadForRequest(courseUrl).single().value)
        assertEquals(subject.snapshot(), jar().snapshot())
        assertEquals(rewritten, persisted.get())
    }

    @Test fun `clear removes both live and persisted cookies`() {
        val subject = jar()
        subject.saveFromResponse(loginUrl, listOf(Cookie.Builder().name("auth").value("token")
            .domain("chaoxing.com").build()))
        subject.clear()
        assertNull(persisted.get())
        assertTrue(subject.snapshot().isEmpty())
        assertTrue(jar().snapshot().isEmpty())
    }

    @Test fun `parallel response saves and reads retain every unique cookie without concurrent mutation`() {
        val subject = jar()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = (0..3).map { worker -> executor.submit {
                repeat(30) { index ->
                    val cookie = Cookie.Builder().name("cookie_${worker}_$index").value("value")
                        .domain("chaoxing.com").build()
                    subject.saveFromResponse(loginUrl, listOf(cookie))
                    subject.loadForRequest(courseUrl)
                }
            } }
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(120, subject.snapshot().size)
            assertEquals(120, jar().snapshot().size)
            assertFalse(persisted.get().isNullOrEmpty())
        } finally {
            executor.shutdownNow()
        }
    }
}
