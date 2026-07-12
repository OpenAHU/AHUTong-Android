package com.ahu.ahutong.data.auth

/**
 * Resets in-memory crawler credentials (cookies/tokens) and session flags
 * before a fresh login. Bound in `:app` over CookieManager / TokenManager.
 */
interface AuthRuntimeReset {
    fun resetRuntimeCredentials()
}
