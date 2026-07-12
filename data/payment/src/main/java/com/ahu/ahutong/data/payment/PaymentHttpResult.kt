package com.ahu.ahutong.data.payment

/**
 * Decouples payment call sites from OkHttp [okhttp3.Response].
 */
data class PaymentHttpResult(
    val body: String,
    val requestUrl: String = "",
    val httpCode: Int = 200,
)
