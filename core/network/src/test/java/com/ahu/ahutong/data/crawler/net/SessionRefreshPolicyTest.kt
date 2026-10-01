package com.ahu.ahutong.data.crawler.net

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

class SessionRefreshPolicyTest {
    private val requestUrl = "https://jw.ahu.edu.cn/student/for-std/lesson-search".toHttpUrl()

    @Test
    fun `routine refresh retains service identity while manual login and logout revoke it`() = kotlinx.coroutines.runBlocking {
        SessionRefreshCoordinator.onAuthenticated()
        val identity = SessionRefreshCoordinator.currentIdentityGeneration()
        val generation = SessionRefreshCoordinator.currentGeneration()
        assertTrue(SessionRefreshCoordinator.refreshIfNeeded(generation) { SessionRefreshCoordinator.RefreshOutcome.SUCCESS })
        assertEquals(generation + 1, SessionRefreshCoordinator.currentGeneration())
        assertEquals(identity, SessionRefreshCoordinator.currentIdentityGeneration())
        SessionRefreshCoordinator.onSignedOut()
        assertEquals(identity + 1, SessionRefreshCoordinator.currentIdentityGeneration())
        SessionRefreshCoordinator.onAuthenticated()
        assertEquals(identity + 2, SessionRefreshCoordinator.currentIdentityGeneration())
    }

    @Test
    fun `explicit sign out is distinguishable from cold startup and clears on authentication`() = kotlinx.coroutines.runBlocking {
        SessionRefreshCoordinator.onAuthenticated()
        try {
            assertFalse(SessionRefreshCoordinator.isExplicitlySignedOut())
            SessionRefreshCoordinator.onSignedOut()
            assertTrue(SessionRefreshCoordinator.isExplicitlySignedOut())
            SessionRefreshCoordinator.onAuthenticated()
            assertFalse(SessionRefreshCoordinator.isExplicitlySignedOut())
        } finally {
            SessionRefreshCoordinator.onAuthenticated()
        }
    }

    @Test
    fun `recognizes first party login redirect`() {
        assertTrue(
            SessionRefreshPolicy.isFirstPartyLoginRedirect(
                requestUrl,
                "https://one.ahu.edu.cn/cas/login?service=https%3A%2F%2Fjw.ahu.edu.cn"
            )
        )
        assertTrue(SessionRefreshPolicy.isFirstPartyLoginRedirect(requestUrl, "/tologin?refer=student"))
    }

    @Test
    fun `does not refresh for unrelated or external redirects`() {
        assertFalse(SessionRefreshPolicy.isFirstPartyLoginRedirect(requestUrl, "/notice?refer=home"))
        assertFalse(SessionRefreshPolicy.isFirstPartyLoginRedirect(requestUrl, "https://example.com/login"))
        assertFalse(SessionRefreshPolicy.isFirstPartyLoginRedirect(requestUrl, null))
    }

    @Test
    fun `request keeps the generation observed when it was dispatched`() {
        val generation = SessionRefreshCoordinator.currentGeneration()
        val request = Request.Builder().url(requestUrl).build()
        val tagged = SessionRefreshCoordinator.tagRequest(request)

        assertEquals(generation, SessionRefreshCoordinator.observedGeneration(tagged))
        assertSame(tagged, SessionRefreshCoordinator.tagRequest(tagged))
    }
}
