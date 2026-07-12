package com.ahu.ahutong.data.auth

import com.ahu.ahutong.data.dao.AHUCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheAuthSessionStore @Inject constructor() : AuthSessionStore {
    override fun saveRustCookies(cookiesJson: String) {
        AHUCache.saveRustCookies(cookiesJson)
    }

    override fun getRustCookies(): String = AHUCache.getRustCookies()
}
