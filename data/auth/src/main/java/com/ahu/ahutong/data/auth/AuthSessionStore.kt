package com.ahu.ahutong.data.auth

import com.ahu.ahutong.data.model.User

/**
 * Persists native cookies and related session seeds.
 * App binds an implementation over [com.ahu.ahutong.data.dao.AHUCache].
 */
interface AuthSessionStore {
    fun saveRustCookies(cookiesJson: String)

    fun getRustCookies(): String

    /** Clears persisted user/session boxes before a fresh login. */
    fun clearPersistedSession()

    /** Persists user, wisdom password, and agreement flags after successful login. */
    fun persistLoginSuccess(user: User, wisdomPassword: String)
}
