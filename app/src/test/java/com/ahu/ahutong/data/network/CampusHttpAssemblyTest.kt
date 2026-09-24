package com.ahu.ahutong.data.network

import com.ahu.ahutong.data.crawler.net.AutoLoginInterceptor
import com.ahu.ahutong.data.crawler.net.SessionExpiryHook
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.crawler.net.TokenAuthenticator
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 第一方客户端装配的契约测试：共享装配必须真的把 Cookie、登录跳转识别、会话续期装进客户端。
 * 这是纯 JVM 可跑的（构造这些对象不触发 Android 运行时），因此能在 CI 里拦住"漏配"。
 *
 * 装配本身不认识业务：Cookie 与续期实现都由调用方注入，因此这里用 fake 就能完整驱动。
 */
class CampusHttpAssemblyTest {

    private val fakeCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
    }

    /** [SessionExpiryHook] 的 fake：传输层只经它通知会话层，测试因此能观察到这一跳。 */
    private class FakeSessionExpiryHook : SessionExpiryHook {
        var expiredCount = 0
            private set

        override suspend fun refresh(observedGeneration: Long): Boolean = false

        override suspend fun onExpired(observedGeneration: Long) {
            expiredCount++
        }
    }

    @Test
    fun `campus cookies applies the injected jar and redirect policy`() {
        val client = OkHttpClient.Builder()
            .campusCookies(
                cookieJar = fakeCookieJar,
                followRedirects = false,
                followSslRedirects = false
            )
            .build()

        assertSame(fakeCookieJar, client.cookieJar)
        assertTrue(!client.followRedirects)
        assertTrue(!client.followSslRedirects)
    }

    @Test
    fun `campus session refresh wires auto login and session authenticator`() {
        val hook = FakeSessionExpiryHook()
        val client = OkHttpClient.Builder()
            .campusAutoLogin()
            .campusSessionRefresh(hook)
            .build()

        assertTrue(client.networkInterceptors.any { it is AutoLoginInterceptor })
        assertTrue(client.authenticator is TokenAuthenticator)
    }

    /**
     * 被重定向回校内登录页时，装配必须（1）向会话层报告失效、（2）把响应改写成明确的 401。
     * 这正是"网络层不认识登录态"这条约束的对外表现。
     */
    @Test
    fun `a campus login redirect notifies the session layer and surfaces as 401`() {
        val hook = FakeSessionExpiryHook()
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "https://one.ahu.edu.cn/cas/login?service=x")
            )
            val client = OkHttpClient.Builder()
                .campusCookies(fakeCookieJar)
                .campusAutoLogin()
                .campusSessionRefresh(hook)
                .build()

            client.newCall(
                Request.Builder().url(server.url("/student/for-std/lesson-search")).build()
            ).execute().use { response ->
                assertEquals(401, response.code)
                // 值与 SessionRefreshPolicy.EXPIRED_RESPONSE_HEADER 相同；该常量属于模块内部实现。
                assertEquals("1", response.header("X-AHUTong-Session-Expired"))
            }
            assertEquals(1, hook.expiredCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a successful automatic refresh does not report session expiry`() {
        val hook = object : SessionExpiryHook {
            var refreshCount = 0
            var expiredCount = 0

            override suspend fun refresh(observedGeneration: Long): Boolean {
                refreshCount++
                return true
            }

            override suspend fun onExpired(observedGeneration: Long) {
                expiredCount++
            }
        }
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302)
                .setHeader("Location", "https://one.ahu.edu.cn/cas/login?service=x"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val client = OkHttpClient.Builder()
                .campusCookies(fakeCookieJar)
                .campusAutoLogin()
                .campusSessionRefresh(hook)
                .campusSessionRefresh(hook)
                .build()

            client.newCall(Request.Builder().url(server.url("/student/data")).build())
                .execute().use { response -> assertEquals(200, response.code) }

            assertEquals(1, hook.refreshCount)
            assertEquals(0, hook.expiredCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a second login redirect reports expiry after the refresh attempt`() {
        val hook = object : SessionExpiryHook {
            var refreshCount = 0
            var expiredGeneration: Long? = null

            override suspend fun refresh(observedGeneration: Long): Boolean {
                refreshCount++
                SessionRefreshCoordinator.onAuthenticated()
                return true
            }

            override suspend fun onExpired(observedGeneration: Long) {
                expiredGeneration = observedGeneration
            }
        }
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) {
                server.enqueue(MockResponse().setResponseCode(302)
                    .setHeader("Location", "https://one.ahu.edu.cn/cas/login?service=x"))
            }
            val client = OkHttpClient.Builder()
                .campusCookies(fakeCookieJar)
                .campusAutoLogin()
                .campusSessionRefresh(hook)
                .build()

            client.newCall(Request.Builder().url(server.url("/student/data")).build())
                .execute().use { response -> assertEquals(401, response.code) }

            assertEquals(1, hook.refreshCount)
            assertEquals(SessionRefreshCoordinator.currentGeneration(), hook.expiredGeneration)
        } finally {
            server.shutdown()
        }
    }
}

