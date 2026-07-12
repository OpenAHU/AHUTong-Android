package com.ahu.ahutong.data.payment

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.crawler.manager.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenPaymentCredentialGate @Inject constructor() : PaymentCredentialGate {
    override suspend fun isReady(): Boolean {
        if (AHUCache.getMockData()) return true
        return !TokenManager.awaitToken().isNullOrBlank()
    }
}
