package com.ahu.ahutong.data.payment

/**
 * Ensures ycard token readiness before payment calls.
 * Bound in `:app` over [com.ahu.ahutong.data.crawler.manager.TokenManager].
 */
interface PaymentCredentialGate {
    suspend fun isReady(): Boolean
}
