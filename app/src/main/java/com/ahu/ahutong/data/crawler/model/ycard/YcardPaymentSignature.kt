package com.ahu.ahutong.data.crawler.model.ycard

import com.ahu.ahutong.data.crawler.utils.generateNonce
import com.ahu.ahutong.data.crawler.utils.getTimestamp
import com.ahu.ahutong.data.crawler.utils.sha256

private const val PAYMENT_APP_ID = "56321"
private const val PAYMENT_SECRET_KEY = "0osTIhce7uPvDKHz6aa67bhCukaKoYl4"

internal fun signedPaymentParams(
    params: Map<String, Any>,
    timestamp: String = getTimestamp(),
    nonce: String = generateNonce()
): Map<String, Any> {
    val signedParams = linkedMapOf<String, Any>().apply {
        putAll(params)
        put("APP_ID", PAYMENT_APP_ID)
        put("TIMESTAMP", timestamp)
        put("SIGN_TYPE", "SHA256")
        put("NONCE", nonce)
    }
    val signaturePayload = signedParams.entries
        .asSequence()
        .filter { (_, value) -> value.toString().isNotEmpty() }
        .sortedBy { (key, _) -> key }
        .joinToString("&") { (key, value) -> "$key=$value" }

    signedParams["SIGN"] = sha256("$signaturePayload&SECRET_KEY=$PAYMENT_SECRET_KEY").uppercase()
    return signedParams
}
