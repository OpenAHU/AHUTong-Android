package com.ahu.ahutong.data.crawler.login

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CasLoginNavigationTest {
    @Test fun `returns guide redirect without following its later WebVPN redirect`() {
        val redirect = response("https://one.ahu.edu.cn/cas/login", "CASTGC=fixture; Path=/cas/")
            .newBuilder().header("Location", "/tp_tpass/h5?act=tpass/guide").build()
        val page = casPostLoginRedirect(redirect, hasIssuedCasSession(redirect))!!
        assertEquals(302, page.status)
        assertTrue(page.casSessionIssued)
        assertTrue(isCasPostLoginUrl(page.url))
    }

    @Test fun `existing session guide redirects can request bounded SSO continuation`() {
        val redirect = response("https://one.ahu.edu.cn/cas/login", "unrelated=fixture")
            .newBuilder().header("Location", "/tp_up/view?m=up").build()
        assertFalse(casPostLoginRedirect(redirect, false)!!.casSessionIssued)
    }

    @Test fun `untrusted destinations and failed responses are not guide redirects`() {
        val redirect = response("https://one.ahu.edu.cn/cas/login", "CASTGC=fixture; Path=/cas/")
            .newBuilder().header("Location", "https://wvpn.ahu.edu.cn/portal").build()
        assertNull(casPostLoginRedirect(redirect, true))
        assertNull(casPostLoginRedirect(redirect.newBuilder().code(503)
            .header("Location", "/tp_up/view").build(), true))
        assertNull(casPostLoginRedirect(redirect.newBuilder().request(
            Request.Builder().url("https://example.com/cas/login").build()
        ).header("Location", "https://one.ahu.edu.cn/tp_up/view").build(), true))
    }
    @Test
    fun `recognizes a live CAS credential without accepting revoked cookies`() {
        assertTrue(hasIssuedCasSession(response("https://one.ahu.edu.cn/cas/login", "CASTGC=fixture; Path=/cas/")))
        assertFalse(hasIssuedCasSession(response("https://one.ahu.edu.cn/cas/login", "CASTGC=; Path=/cas/")))
        assertFalse(hasIssuedCasSession(response("https://one.ahu.edu.cn/cas/login", "CASTGC=fixture; Path=/cas/; Max-Age=0")))
        assertFalse(hasIssuedCasSession(response("https://one.ahu.edu.cn/cas/login", "CASTGC=fixture; Path=/tp_tpass/")))
    }

    @Test
    fun `only the HTTPS central CAS origin can issue a credential signal`() {
        for (url in listOf(
            "http://one.ahu.edu.cn/cas/login",
            "https://one.ahu.edu.cn:444/cas/login",
            "https://one.ahu.edu.cn.example.com/cas/login",
            "https://jw.ahu.edu.cn/cas/login"
        )) {
            assertFalse(hasIssuedCasSession(response(url, "CASTGC=fixture; Path=/cas/")))
        }
    }

    private fun response(url: String, cookie: String) = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1).code(302).message("Found")
        .addHeader("Set-Cookie", cookie).build()

    @Test
    fun `recognizes phone guide and portal destinations`() {
        assertTrue(isCasPostLoginUrl("https://one.ahu.edu.cn/tp_tpass/h5?act=tpass%2Fguide&ticket=ST-fixture"))
        assertTrue(isCasPostLoginUrl("https://one.ahu.edu.cn/tp_tpass/tpass/guide?item_id=fixture"))
        assertTrue(isCasPostLoginUrl("https://one.ahu.edu.cn/tp_up/view?m=up#act=portal/viewhome"))
    }

    @Test
    fun `rejects unrelated pages and untrusted origins`() {
        assertFalse(isCasPostLoginUrl("https://one.ahu.edu.cn/cas/login"))
        assertFalse(isCasPostLoginUrl("https://one.ahu.edu.cn/tp_tpass/h5?act=other"))
        assertFalse(isCasPostLoginUrl("http://one.ahu.edu.cn/tp_up/view?m=up"))
        assertFalse(isCasPostLoginUrl("https://one.ahu.edu.cn.example.com/tp_up/view?m=up"))
        assertFalse(isCasPostLoginUrl("https://one.ahu.edu.cn:444/tp_up/view?m=up"))
        assertFalse(isCasPostLoginUrl("not a url"))
        assertFalse(isCasPostLoginUrl(null))
    }
}
