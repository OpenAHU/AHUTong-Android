package com.ahu.ahutong.data.auth

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheAuthSessionStore @Inject constructor() : AuthSessionStore {
    override fun saveRustCookies(cookiesJson: String) {
        AHUCache.saveRustCookies(cookiesJson)
    }

    override fun getRustCookies(): String = AHUCache.getRustCookies()

    override fun clearPersistedSession() {
        AHUCache.clearAll()
    }

    override fun persistLoginSuccess(user: User, wisdomPassword: String) {
        AHUCache.saveCurrentUser(user)
        AHUCache.saveWisdomPassword(wisdomPassword)
        AHUCache.setAgreementAccepted()
        AHUCache.setBusinessAccepted()
        AHUCache.setPrivacyAccepted()
    }
}
