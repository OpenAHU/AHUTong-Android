package com.ahu.ahutong.data.crawler.manager

import android.util.Log
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import okhttp3.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLDecoder

object TokenManager {

    val TAG = "TokenManager"

    private var token :String? = null


    @Synchronized
    fun getToken():String?{
        if (!token.isNullOrBlank()) return token

        Log.e(TAG, "getToken: token is null", )

        try {

            val loginResponse = YcardApi.API.login().execute()      //假设已经登陆过one.ahu.ehu.cn
            val redirectUrl = extractRedirectLocation(loginResponse)
                ?: loginResponse.raw().request.url.toString()

            val regex = Regex("[?&]ticket=([^&]+)")
            val match = regex.find(redirectUrl)
            val ticket = match?.groupValues?.get(1) ?: return null
            val decodedUsername = URLDecoder.decode(URLDecoder.decode(ticket, "UTF-8"), "UTF-8")

            val tokenResponse = YcardApi.API.getToken(
                username = decodedUsername,
                password = decodedUsername
            ).execute()

            if (tokenResponse.isSuccessful) {
                token = tokenResponse.body()?.access_token
                Log.i(TAG, "getToken: token acquired")
                return token
            }
        } catch (e: Exception) {
            Log.e(TAG, "getToken: request failed (${e.javaClass.simpleName})")
        }
        return null
    }

    suspend fun awaitToken(
        timeoutMillis: Long = 8_000L,
        retryDelayMillis: Long = 500L
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMillis

        while (System.currentTimeMillis() <= deadline) {
            val currentToken = withContext(Dispatchers.IO) {
                getToken()
            }
            if (!currentToken.isNullOrBlank()) {
                return currentToken
            }
            delay(retryDelayMillis)
        }

        return withContext(Dispatchers.IO) {
            getToken()
        }
    }

    private fun extractRedirectLocation(response: retrofit2.Response<*>): String? {
        var current: Response? = response.raw().priorResponse
        while (current != null) {
            current.header("Location")?.let { location ->
                if ("ticket=" in location) {
                    return location
                }
            }
            current = current.priorResponse
        }
        return response.raw().header("Location")
    }

    fun clear(){
        Log.e(TAG, "clear: Token", )
        token = null
    }

}
