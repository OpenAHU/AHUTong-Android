package com.ahu.ahutong.data.auth

/**
 * Persists native cookies and related session seeds.
 * App binds an implementation over [com.ahu.ahutong.data.dao.AHUCache].
 */
interface AuthSessionStore {
    fun saveRustCookies(cookiesJson: String)

    fun getRustCookies(): String
}
