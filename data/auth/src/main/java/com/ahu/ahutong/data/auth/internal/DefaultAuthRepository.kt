package com.ahu.ahutong.data.auth.internal

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.core.sdk.CampusNativeGateway
import com.ahu.ahutong.data.auth.AuthCookieSyncer
import com.ahu.ahutong.data.auth.AuthCrawlerSource
import com.ahu.ahutong.data.auth.AuthRepository
import com.ahu.ahutong.data.auth.AuthSessionStore
import com.ahu.ahutong.data.model.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultAuthRepository @Inject constructor(
    private val gateway: CampusNativeGateway,
    private val sessionStore: AuthSessionStore,
    private val crawlerSource: AuthCrawlerSource,
    private val cookieSyncer: AuthCookieSyncer,
) : AuthRepository {

    override suspend fun login(username: String, password: String): AppResult<User> =
        withContext(Dispatchers.IO) {
            if (gateway.isLocalServiceReady()) {
                when (val http = tryHttpLogin(username, password)) {
                    is AppResult.Success -> return@withContext http
                    is AppResult.Error -> Log.w(TAG, "http login failed: ${http.message}")
                }
            }

            if (gateway.isNativeLoaded()) {
                when (val jni = tryJniLogin(username, password)) {
                    is AppResult.Success -> return@withContext jni
                    is AppResult.Error -> Log.w(TAG, "jni login failed: ${jni.message}")
                }
            }

            Log.d(TAG, "fallback to Android crawler login")
            crawlerSource.login(username, password)
        }

    private suspend fun tryHttpLogin(username: String, password: String): AppResult<User> {
        gateway.httpInit("")
        sessionStore.saveRustCookies("")

        return when (val result = gateway.httpLogin(username, password)) {
            is AppResult.Success -> {
                persistCookiesFromHttp()
                syncCookies()
                result
            }
            is AppResult.Error -> result
        }
    }

    private suspend fun tryJniLogin(username: String, password: String): AppResult<User> {
        gateway.initSafe("")
        sessionStore.saveRustCookies("")

        return when (val result = gateway.login(username, password)) {
            is AppResult.Success -> {
                persistCookiesFromNative()
                syncCookies()
                result
            }
            is AppResult.Error -> result
        }
    }

    private suspend fun persistCookiesFromHttp() {
        when (val cookies = gateway.httpDumpCookies()) {
            is AppResult.Success -> {
                sessionStore.saveRustCookies(cookies.data)
                Log.d(TAG, "Persisted HTTP cookies: ${cookies.data.length} bytes")
            }
            is AppResult.Error -> Log.w(TAG, "Failed to persist HTTP cookies: ${cookies.message}")
        }
    }

    private fun persistCookiesFromNative() {
        if (!gateway.isNativeLoaded()) return
        runCatching {
            val cookies = gateway.dumpCookies().orEmpty()
            sessionStore.saveRustCookies(cookies)
            Log.d(TAG, "Persisted JNI cookies: ${cookies.length} bytes")
        }.onFailure {
            Log.w(TAG, "Failed to persist JNI cookies", it)
        }
    }

    private suspend fun syncCookies() {
        runCatching { cookieSyncer.syncFromNative() }
            .onFailure { Log.w(TAG, "cookie sync failed", it) }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
