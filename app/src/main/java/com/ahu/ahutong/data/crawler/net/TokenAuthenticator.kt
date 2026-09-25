package com.ahu.ahutong.data.crawler.net

import android.util.Log
import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.manager.TokenManager
import com.ahu.ahutong.data.dao.AHUCache
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null
        if (!SessionRefreshPolicy.isMarkedExpired(
                response.header(SessionRefreshPolicy.EXPIRED_RESPONSE_HEADER)
            )
        ) return null

        val observedGeneration = SessionRefreshCoordinator.observedGeneration(response.request)
        return runBlocking {
            val refreshed = SessionRefreshCoordinator.refreshIfNeeded(observedGeneration) {
                val user = AHUCache.getCurrentUser() ?: return@refreshIfNeeded false
                val password = AHUCache.getWisdomPassword()?.takeIf { it.isNotBlank() }
                    ?: return@refreshIfNeeded false

                Log.i(TAG, "Refreshing expired first-party session")
                val loginResponse = AHURepository.loginWithCrawler(
                    username = user.xh.toString(),
                    password = password
                )
                if (!loginResponse.isSuccessful) {
                    AHUApplication.sessionExpired = true
                    Log.w(TAG, "Session refresh failed")
                    return@refreshIfNeeded false
                }

                TokenManager.clear()
                AHUCache.saveCurrentUser(loginResponse.data)
                true
            }
            if (!refreshed) return@runBlocking null

            response.request.newBuilder()
                .removeHeader("Cookie")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val TAG = "TokenAuthenticator"
        const val MAX_ATTEMPTS = 2
    }
}
