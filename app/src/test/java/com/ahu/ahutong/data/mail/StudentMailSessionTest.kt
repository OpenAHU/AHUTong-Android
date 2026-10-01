package com.ahu.ahutong.data.mail

import java.io.IOException
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentMailSessionTest {
    @Test fun `fresh portal ticket is only exchanged through the observed school proxy`() {
        val proxy = "https://wvpn.ahu.edu.cn/https/77726476706e69737468656265737421fff944d226387d1e7b0c9ce29b5b/tp_up/view?m=up".toHttpUrl()
        val callback = "https://one.ahu.edu.cn/tp_up/view?m=up&ticket=ST-fixture".toHttpUrl()
        val target = StudentMailSession.proxyPortalCallback(proxy, callback)
        assertEquals(proxy.host, target.host)
        assertEquals(callback.encodedQuery, target.encodedQuery)
        listOf(
            proxy.newBuilder().host("attacker.example").build(),
            proxy.newBuilder().encodedPath("/https/untrusted/tp_up/view").build(),
            proxy.newBuilder().scheme("http").build()
        ).forEach { untrusted ->
            assertThrows(IOException::class.java) { StudentMailSession.proxyPortalCallback(untrusted, callback) }
        }
        assertThrows(IOException::class.java) {
            StudentMailSession.proxyPortalCallback(proxy, callback.newBuilder().host("attacker.example").build())
        }
        assertThrows(IOException::class.java) { StudentMailSession.proxyPortalCallback(proxy, null) }
    }

    @Test fun `portal jar replaces RFC cookie identity and never shares CAS with VPN`() {
        val campus = "https://one.ahu.edu.cn/cas/login".toHttpUrl()
        val hostCookie = cookie("CASTGC", "fresh", campus.host, "/cas/")
        val domainCookie = Cookie.Builder().name("CASTGC").value("old").domain(campus.host).path("/cas/").build()
        val jar = PortalMemoryCookieJar(listOf(domainCookie, hostCookie)) {}
        assertEquals("fresh", jar.loadForRequest(campus).single().value)
        jar.saveFromResponse(campus, listOf(cookie("CASTGC", "new", campus.host, "/cas/")))
        assertEquals("new", jar.loadForRequest(campus).single().value)
        val vpn = "https://wvpn.ahu.edu.cn/".toHttpUrl()
        assertTrue(jar.loadForRequest(vpn).isEmpty())
        jar.saveFromResponse(vpn, listOf(cookie("vpn", "fixture", vpn.host)))
        assertEquals(listOf("vpn"), jar.loadForRequest(vpn).map { it.name })
        assertEquals(listOf("CASTGC"), jar.loadForRequest(campus).map { it.name })
        assertTrue(jar.loadForRequest("https://attacker.example/".toHttpUrl()).isEmpty())
    }
    @Test fun `parent domain cookies stay with their response origin even when identities overlap`() {
        val campus = "https://one.ahu.edu.cn/tp_up/view".toHttpUrl()
        val vpn = "https://wvpn.ahu.edu.cn/".toHttpUrl()
        fun sharedDomainCookie(name: String, value: String) = Cookie.Builder()
            .name(name).value(value).domain("ahu.edu.cn").path("/").secure().build()
        val jar = PortalMemoryCookieJar(listOf(sharedDomainCookie("CASTGC", "campus-seed"))) {}
        assertEquals("campus-seed", jar.loadForRequest(campus).single().value)
        assertTrue(jar.loadForRequest(vpn).isEmpty())

        jar.saveFromResponse(campus, listOf(sharedDomainCookie("SESSION", "campus-session")))
        jar.saveFromResponse(vpn, listOf(sharedDomainCookie("SESSION", "vpn-session")))
        assertEquals(
            mapOf("CASTGC" to "campus-seed", "SESSION" to "campus-session"),
            jar.loadForRequest(campus).associate { it.name to it.value }
        )
        assertEquals(mapOf("SESSION" to "vpn-session"), jar.loadForRequest(vpn).associate { it.name to it.value })

        val deleted = Cookie.Builder().name("SESSION").value("").domain("ahu.edu.cn")
            .path("/").secure().expiresAt(0).build()
        jar.saveFromResponse(campus, listOf(deleted))
        assertEquals(listOf("CASTGC"), jar.loadForRequest(campus).map { it.name })
        assertEquals("vpn-session", jar.loadForRequest(vpn).single().value)
    }

    @Test fun `portal authentication begins at CAS instead of the public portal view`() {
        val login = StudentMailSession.portalCasLoginUrl()
        assertEquals("/cas/login", login.encodedPath)
        assertEquals("https://one.ahu.edu.cn/tp_up/view?m=up", login.queryParameter("service"))
    }
    @Test fun `persisted campus account may bootstrap after cold start but not after sign out`() {
        assertTrue(canResumePersistedCampusSession(false, com.ahu.ahutong.data.session.AhuSessionState.Status.Anonymous, true))
        assertFalse(canResumePersistedCampusSession(true, com.ahu.ahutong.data.session.AhuSessionState.Status.Anonymous, true))
        assertFalse(canResumePersistedCampusSession(false, com.ahu.ahutong.data.session.AhuSessionState.Status.Anonymous, false))
    }
    private val mailbox = "https://mail.stu.ahu.edu.cn/redirect".toHttpUrl()

    @Test fun `captured HTTP mail template is upgraded before any network request`() {
        val url = StudentMailSession.secureUrl("http://mail.stu.ahu.edu.cn/redirect?sid=fixture", false)
        assertEquals("https", url.scheme)
        assertEquals(443, url.port)
        assertEquals("fixture", url.queryParameter("sid"))
    }

    @Test fun `SSO rejects lookalike hosts credentials and insecure campus requests`() {
        listOf(
            "https://mail.stu.ahu.edu.cn.attacker.example/redirect",
            "https://mail.stu.ahu.edu.cn@attacker.example/redirect",
            "https://user:password@mail.stu.ahu.edu.cn/redirect",
            "http://entryhz.qiye.163.com/domain/oa/Entry",
            "https://mail.stu.ahu.edu.cn:8443/redirect"
        ).forEach { url ->
            assertThrows(IOException::class.java) { StudentMailSession.secureUrl(url, false) }
        }
        assertThrows(IOException::class.java) {
            StudentMailSession.secureUrl("http://one.ahu.edu.cn/cas/login", true)
        }
        assertThrows(IOException::class.java) {
            StudentMailSession.secureUrl("https://entryhz.qiye.163.com/", true)
        }
        assertThrows(IOException::class.java) {
            StudentMailSession.secureUrl("https://one.ahu.edu.cn/cas/login", false)
        }
    }

    @Test fun `mail cookie jar cannot send campus credentials or send mail credentials to SSO host`() {
        val jar = MailMemoryCookieJar()
        val campus = "https://one.ahu.edu.cn/".toHttpUrl()
        jar.saveFromResponse(campus, listOf(cookie("SESSION", "campus", "one.ahu.edu.cn")))
        jar.saveFromResponse(mailbox, listOf(cookie("Coremail", "mail", mailbox.host)))
        assertEquals(listOf("Coremail"), jar.snapshot().map { it.name })
        assertTrue(jar.loadForRequest(campus).isEmpty())
        assertTrue(jar.loadForRequest("https://entryhz.qiye.163.com/".toHttpUrl()).isEmpty())
        assertEquals("mail", jar.loadForRequest(mailbox).single().value)
    }

    @Test fun `response may set narrower path cookie and deletion replaces old value`() {
        val jar = MailMemoryCookieJar()
        val scoped = cookie("api", "fixture", mailbox.host, "/js6")
        jar.saveFromResponse(mailbox, listOf(scoped))
        assertTrue(jar.loadForRequest(mailbox).isEmpty())
        assertEquals(1, jar.loadForRequest("https://mail.stu.ahu.edu.cn/js6/s".toHttpUrl()).size)
        val deleted = Cookie.Builder().name("api").value("").hostOnlyDomain(mailbox.host)
            .path("/js6").secure().expiresAt(0).build()
        jar.saveFromResponse(mailbox, listOf(deleted))
        assertTrue(jar.snapshot().isEmpty())
    }

    @Test fun `entry cookies support door handshake but never enter mailbox snapshots`() {
        val jar = MailMemoryCookieJar()
        val entry = "https://entryhz.qiye.163.com/entry/door".toHttpUrl()
        jar.saveFromResponse(entry, listOf(cookie("QIYE_SESS", "entry-fixture", entry.host)))
        jar.saveFromResponse(mailbox, listOf(cookie("QIYE_SESS", "mail-fixture", mailbox.host)))
        assertEquals("entry-fixture", jar.loadForRequest(entry).single().value)
        assertEquals("mail-fixture", jar.snapshot().single().value)
        assertEquals("mail-fixture", jar.loadForRequest(mailbox).single().value)
    }

    @Test fun `account or generation change invalidates session and clears credentials`() {
        val jar = MailMemoryCookieJar()
        jar.saveFromResponse(mailbox, listOf(cookie("Coremail", "fixture", mailbox.host)))
        var identityCurrent = true
        val session = MailSession("fixture-sid", mailbox.host, OkHttpClient(), "fixture-account", { identityCurrent }, jar)
        session.assertCurrent()
        assertFalse(session.toString().contains("fixture-sid"))
        identityCurrent = false
        assertThrows(MailSessionExpired::class.java) { session.assertCurrent() }
        assertTrue(jar.snapshot().isEmpty())
        identityCurrent = true
        assertThrows(MailSessionExpired::class.java) { session.assertCurrent() }
    }

    private fun cookie(name: String, value: String, domain: String, path: String = "/") =
        Cookie.Builder().name(name).value(value).hostOnlyDomain(domain).path(path).secure().build()
}
