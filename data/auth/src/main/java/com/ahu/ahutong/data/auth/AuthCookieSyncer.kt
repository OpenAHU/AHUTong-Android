package com.ahu.ahutong.data.auth

/**
 * Syncs native cookies into Android OkHttp cookie stores.
 * Implemented in `:app` using existing crawler CookieManager logic.
 */
interface AuthCookieSyncer {
    suspend fun syncFromNative()
}
