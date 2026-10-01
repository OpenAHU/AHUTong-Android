package com.ahu.ahutong.appwidget

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class WidgetCookieJarTest {
    private val url = "https://gmis.ahu.edu.cn/gmis5/student/default/index".toHttpUrl()
    private fun cookie(value: String, domain: String = "gmis.ahu.edu.cn") =
        Cookie.Builder().name("session").value(value).hostOnlyDomain(domain).path("/").build()

    @Test fun responseCookiesNeverMutateSeedOrAnotherWidgetSession() {
        val seed = mutableListOf(cookie("original"))
        val first = WidgetCookieJar(seed)
        val second = WidgetCookieJar(seed)
        first.saveFromResponse(url, listOf(cookie("renewed")))
        assertEquals("original", seed.single().value)
        assertEquals("original", second.loadForRequest(url).single().value)
        assertEquals("renewed", first.loadForRequest(url).single().value)
        seed.clear()
        assertEquals("original", second.loadForRequest(url).single().value)
    }

    @Test fun expiredAndOtherServiceCookiesAreNotSent() {
        val expired = cookie("expired").newBuilder().expiresAt(1).build()
        val jar = WidgetCookieJar(listOf(expired, cookie("payment", "card.ahu.edu.cn")))
        assertTrue(jar.loadForRequest(url).isEmpty())
        assertTrue(jar.loadForRequest("https://card.ahu.edu.cn/".toHttpUrl()).isEmpty())
    }
}
