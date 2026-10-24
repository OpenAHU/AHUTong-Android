package com.ahu.ahutong.data.crawler.manager

import android.util.Log
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.session.SecureCredentialVault
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLDecoder
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.security.SecureStorageUnavailableException

object TokenManager {

    val TAG = "TokenManager"

    @Volatile
    private var token: CachedToken? = null
    private val tokenLock = Any()
    private var tokenVersion = 0L
    private val refreshMutex = Mutex()

    /** Returns the current identity's in-memory snapshot and never performs network I/O. */
    fun getToken(): String? = token?.takeIf {
        it.identityGeneration == SessionRefreshCoordinator.currentIdentityGeneration() &&
            !SessionRefreshCoordinator.isExplicitlySignedOut()
    }?.value

    private fun fetchToken(): CampusCardTokenAttempt {
        return try {
            val loginResult = probeCampusCardLogin()
            if (loginResult.ticketUrl == null) {
                return CampusCardTokenAttempt(
                    casLoginUrl = loginResult.casLoginUrl
                )
            }

            val ticket = extractCampusCardCredential(loginResult.ticketUrl)
                ?: return CampusCardTokenAttempt()

            val tokenResponse = YcardApi.API.getToken(
                username = ticket,
                password = ticket
            ).execute()

            if (tokenResponse.isSuccessful) {
                val refreshed = tokenResponse.body()?.access_token
                Log.i(TAG, "getToken: token acquired")
                CampusCardTokenAttempt(token = refreshed)
            } else {
                Log.w(TAG, "getToken: credential exchange failed (${tokenResponse.code()})")
                tokenResponse.errorBody()?.close()
                CampusCardTokenAttempt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getToken: request failed (${e.javaClass.simpleName})")
            CampusCardTokenAttempt()
        }
    }

    /**
     * Walk the campus-card SSO redirects ourselves so the one-shot CAS ticket is captured
     * before loginTransit has a chance to send the request back to the SSO entry point.
     */
    private fun probeCampusCardLogin(): CampusCardLoginProbe {
        var currentUrl = YcardApi.BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments("berserker-auth/cas/redirect/neusoftCas")
            .addQueryParameter("targetUrl", YcardApi.LOGIN_TARGET_URL)
            .build()
        var casLoginUrl: String? = null

        repeat(MAX_LOGIN_REDIRECTS) {
            val request = Request.Builder().url(currentUrl).get().build()
            YcardApi.loginRedirectClient.newCall(request).execute().use { response ->
                val responseUrl = response.request.url
                if (hasCampusCardCredential(responseUrl.toString())) {
                    return CampusCardLoginProbe(ticketUrl = responseUrl.toString())
                }
                if (isCasLoginUrl(responseUrl.toString())) {
                    casLoginUrl = responseUrl.toString()
                }

                val nextUrl = response.header("Location")
                    ?.let(responseUrl::resolve)
                    ?: return CampusCardLoginProbe(casLoginUrl = casLoginUrl)
                if (hasCampusCardCredential(nextUrl.toString())) {
                    return CampusCardLoginProbe(ticketUrl = nextUrl.toString())
                }
                if (isCasLoginUrl(nextUrl.toString())) {
                    casLoginUrl = nextUrl.toString()
                }
                currentUrl = nextUrl
            }
        }
        return CampusCardLoginProbe(casLoginUrl = casLoginUrl)
    }

    suspend fun awaitToken(): String? {
        getToken()?.let { return it }
        return refreshMutex.withLock {
            getToken()?.let { return@withLock it }
            fetchUsableToken()
        }
    }

    /**
     * Refreshes a token rejected by the server. Concurrent 401 responses share the same
     * refresh; a request that arrives after another request has refreshed simply reuses it.
     */
    suspend fun refreshAfterUnauthorized(rejectedToken: String?): String? =
        refreshMutex.withLock {
            getToken()?.takeIf { current ->
                current.isNotBlank() && rejectedToken != null && current != rejectedToken
            }?.let { return@withLock it }

            clear()
            fetchUsableToken()
        }

    private suspend fun fetchUsableToken(): String? {
        if (SessionRefreshCoordinator.isExplicitlySignedOut()) return null
        val observedIdentity = SessionRefreshCoordinator.currentIdentityGeneration()
        val observedTokenVersion = synchronized(tokenLock) { tokenVersion }
        val observedGeneration = SessionRefreshCoordinator.currentGeneration(SessionRefreshCoordinator.Scope.CENTRAL_CAS)
        fun isCurrentRequest(): Boolean = synchronized(tokenLock) {
            tokenVersion == observedTokenVersion &&
                SessionRefreshCoordinator.currentIdentityGeneration() == observedIdentity &&
                !SessionRefreshCoordinator.isExplicitlySignedOut()
        }
        val refreshedToken = fetchCampusCardTokenWithRecovery(
            fetchToken = {
                if (isCurrentRequest()) {
                    withContext(Dispatchers.IO) { fetchToken() }
                } else {
                    CampusCardTokenAttempt()
                }
            },
            refreshSession = { casLoginUrl ->
                isCurrentRequest() && refreshStoredSession(observedGeneration, casLoginUrl)
            }
        ) ?: return null
        return synchronized(tokenLock) {
            // A blocking exchange can finish after clear(), sign-out, or an account change.
            if (tokenVersion != observedTokenVersion ||
                SessionRefreshCoordinator.currentIdentityGeneration() != observedIdentity ||
                SessionRefreshCoordinator.isExplicitlySignedOut()
            ) {
                null
            } else {
                token = CachedToken(refreshedToken, observedIdentity)
                refreshedToken
            }
        }
    }

    private suspend fun refreshStoredSession(
        observedGeneration: Long,
        casLoginUrl: String?
    ): Boolean {
        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            observedGeneration, scope = SessionRefreshCoordinator.Scope.CENTRAL_CAS
        ) {
            val (user, password) = try {
                val user = SessionStore.currentUserForRefresh()
                    ?: return@refreshIfNeeded SessionRefreshCoordinator.RefreshOutcome.REJECTED
                val password = SecureCredentialVault.wisdomPasswordForRefresh()?.takeIf { it.isNotBlank() }
                    ?: return@refreshIfNeeded SessionRefreshCoordinator.RefreshOutcome.REJECTED
                user to password
            } catch (_: SecureStorageUnavailableException) {
                return@refreshIfNeeded SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
            }

            val serviceLoginUrl = casLoginUrl
                ?: return@refreshIfNeeded SessionRefreshCoordinator.RefreshOutcome.REJECTED

            Log.i(TAG, "Refreshing central CAS session for campus-card token")
            // CAS 刷新只回 Boolean，失败按瞬时处理（多为网络/协议抖动），冷却窗外可再试。
            if (AHURepository.refreshCentralCasSession(
                    username = user.xh.toString(),
                    password = password,
                    casLoginUrl = serviceLoginUrl
                )
            ) {
                SessionRefreshCoordinator.RefreshOutcome.SUCCESS
            } else {
                SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
            }
        }
        // Central CAS is a derived service lane: its outcome must not rewrite the global
        // login state. A campus-card/CAS failure surfaces on the payment screen instead.
        return refreshed
    }

    private fun isCasLoginUrl(url: String): Boolean =
        url.contains("one.ahu.edu.cn/cas/login", ignoreCase = true)

    /**
     * The central CAS `ST-*` is a one-shot service ticket that must be followed back into
     * ycard. It is not the encoded campus-card credential accepted by the OAuth endpoint.
     */
    private fun hasCampusCardCredential(url: String): Boolean =
        extractCampusCardCredential(url) != null

    internal fun extractCampusCardCredential(url: String): String? {
        val rawTicket = Regex("[?&]ticket=([^&#]+)")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        // A ticket is an opaque URI component: decode once and preserve literal plus signs.
        val credential = runCatching {
            URLDecoder.decode(rawTicket.replace("+", "%2B"), "UTF-8")
        }.getOrNull() ?: return null
        return credential.takeUnless {
            it.startsWith("ST-", ignoreCase = true) ||
                it.startsWith("PT-", ignoreCase = true)
        }
    }

    fun clear() {
        Log.e(TAG, "clear: Token", )
        synchronized(tokenLock) {
            tokenVersion += 1
            token = null
        }
    }

    private class CachedToken(val value: String, val identityGeneration: Long)

    private data class CampusCardLoginProbe(
        val ticketUrl: String? = null,
        val casLoginUrl: String? = null
    )

    private const val MAX_LOGIN_REDIRECTS = 12

}
