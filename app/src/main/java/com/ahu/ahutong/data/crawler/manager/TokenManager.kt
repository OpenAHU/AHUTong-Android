package com.ahu.ahutong.data.crawler.manager

import android.util.Log
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.session.SecureCredentialVault
import com.ahu.ahutong.data.session.AhuSessionState
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLDecoder
import com.ahu.ahutong.data.session.SessionStore

object TokenManager {

    val TAG = "TokenManager"

    @Volatile
    private var token: String? = null
    private val refreshMutex = Mutex()

    /** Returns the current in-memory snapshot and never performs network I/O. */
    fun getToken(): String? = token

    private fun fetchToken(): TokenFetchResult {
        return try {
            val loginResult = probeCampusCardLogin()
            if (loginResult.ticketUrl == null) {
                return TokenFetchResult(
                    requiresSessionRefresh = loginResult.casLoginUrl != null,
                    casLoginUrl = loginResult.casLoginUrl
                )
            }

            val ticket = extractCampusCardCredential(loginResult.ticketUrl)
                ?: return TokenFetchResult()
            val decodedUsername = URLDecoder.decode(URLDecoder.decode(ticket, "UTF-8"), "UTF-8")

            val tokenResponse = YcardApi.API.getToken(
                username = decodedUsername,
                password = decodedUsername
            ).execute()

            if (tokenResponse.isSuccessful) {
                val refreshed = tokenResponse.body()?.access_token
                Log.i(TAG, "getToken: token acquired")
                TokenFetchResult(token = refreshed)
            } else {
                Log.w(TAG, "getToken: credential exchange failed (${tokenResponse.code()})")
                TokenFetchResult()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getToken: request failed (${e.javaClass.simpleName})")
            TokenFetchResult()
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
        token?.takeIf { it.isNotBlank() }?.let { return it }
        return refreshMutex.withLock {
            token?.takeIf { it.isNotBlank() }?.let { return@withLock it }
            fetchUsableToken()
        }
    }

    /**
     * Refreshes a token rejected by the server. Concurrent 401 responses share the same
     * refresh; a request that arrives after another request has refreshed simply reuses it.
     */
    suspend fun refreshAfterUnauthorized(rejectedToken: String?): String? =
        refreshMutex.withLock {
            token?.takeIf { current ->
                current.isNotBlank() && rejectedToken != null && current != rejectedToken
            }?.let { return@withLock it }

            token = null
            fetchUsableToken()
        }

    private suspend fun fetchUsableToken(): String? {
        val observedGeneration = SessionRefreshCoordinator.currentGeneration()
        val firstAttempt = withContext(Dispatchers.IO) { fetchToken() }
        val result = if (firstAttempt.requiresSessionRefresh &&
            refreshStoredSession(observedGeneration, firstAttempt.casLoginUrl)
        ) {
            withContext(Dispatchers.IO) { fetchToken() }
        } else {
            firstAttempt
        }
        return result.token?.takeIf { it.isNotBlank() }?.also { token = it }
    }

    private suspend fun refreshStoredSession(
        observedGeneration: Long,
        casLoginUrl: String?
    ): Boolean {
        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            observedGeneration, scope = SessionRefreshCoordinator.Scope.CENTRAL_CAS
        ) {
            val user = SessionStore.currentUser()
                ?: return@refreshIfNeeded SessionRefreshCoordinator.RefreshOutcome.REJECTED
            val password = SecureCredentialVault.wisdomPassword()?.takeIf { it.isNotBlank() }
                ?: return@refreshIfNeeded SessionRefreshCoordinator.RefreshOutcome.REJECTED

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
        // 与 RepositoryAhuSession 同一约定：续期成功由会话层写登录态。
        if (refreshed) {
            SessionRefreshCoordinator.commitIfCurrent(observedGeneration + 1) {
                if (AhuSessionState.status.value != AhuSessionState.Status.Anonymous) {
                    AhuSessionState.markAuthenticated()
                }
            }
        } else {
            val rejected = SessionRefreshCoordinator.failureKindOf(
                observedGeneration, SessionRefreshCoordinator.Scope.CENTRAL_CAS
            ) == SessionRefreshCoordinator.FailureKind.REJECTED
            SessionRefreshCoordinator.commitIfCurrent(observedGeneration) {
                // 只有凭据被明确拒绝才宣告过期；瞬时失败保持静默，冷却窗外自愈。
                // 若手动登录已推进代号，这个旧失败不会进入提交块，也就不能覆盖新会话。
                if (rejected && AhuSessionState.status.value != AhuSessionState.Status.Anonymous) {
                    AhuSessionState.markExpired()
                }
            }
        }
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

    private fun extractCampusCardCredential(url: String): String? {
        val rawTicket = Regex("[?&]ticket=([^&]+)")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        val decodedOnce = runCatching { URLDecoder.decode(rawTicket, "UTF-8") }
            .getOrDefault(rawTicket)
        return rawTicket.takeUnless {
            decodedOnce.startsWith("ST-", ignoreCase = true) ||
                decodedOnce.startsWith("PT-", ignoreCase = true)
        }
    }

    fun clear() {
        Log.e(TAG, "clear: Token", )
        token = null
    }

    private data class TokenFetchResult(
        val token: String? = null,
        val requiresSessionRefresh: Boolean = false,
        val casLoginUrl: String? = null
    )

    private data class CampusCardLoginProbe(
        val ticketUrl: String? = null,
        val casLoginUrl: String? = null
    )

    private const val MAX_LOGIN_REDIRECTS = 12

}
