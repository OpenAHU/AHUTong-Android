package com.ahu.ahutong.data.auth

import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.manager.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrawlerAuthRuntimeReset @Inject constructor() : AuthRuntimeReset {
    override fun resetRuntimeCredentials() {
        AHUApplication.sessionExpired = true
        CookieManager.cookieJar.clear()
        TokenManager.clear()
    }
}
