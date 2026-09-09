package com.ahu.ahutong.data.crawler.model.ycard

import com.ahu.ahutong.data.crawler.utils.generateNonce
import com.ahu.ahutong.data.crawler.utils.getTimestamp

class BathroomPayPrepareRequest(
    orderId: String,
    timestamp: String = getTimestamp(),
    nonce: String = generateNonce()
) : RequestBody(), BathroomPaymentRequest {
    init {
        addParams(
            signedPaymentParams(
                params = mapOf(
                    "orderid" to orderId,
                    "paystep" to "2",
                    "paytype" to "ACCOUNTTSM",
                    "paytypeid" to "64"
                ),
                timestamp = timestamp,
                nonce = nonce
            )
        )
    }
}

class BathroomPayRequest(
    orderId: String,
    plaintext: String,
    uuid: String,
    passwordMap: String,
    timestamp: String = getTimestamp(),
    nonce: String = generateNonce()
) : RequestBody(), BathroomPaymentRequest {
    init {
        require(passwordMap.length == 10 && passwordMap.toSet() == DIGITS.toSet()) {
            "Invalid bathroom payment password map"
        }
        val cipherText = plaintext.map { digit ->
            val mappedDigit = passwordMap.indexOf(digit)
            require(mappedDigit >= 0) { "Payment password must contain digits only" }
            mappedDigit.digitToChar()
        }.joinToString("")

        addParams(
            signedPaymentParams(
                params = mapOf(
                    "orderid" to orderId,
                    "paystep" to "2",
                    "paytype" to "ACCOUNTTSM",
                    "paytypeid" to "64",
                    "userAgent" to "wechat-mp",
                    "ccctype" to "000",
                    "password" to cipherText,
                    "uuid" to uuid,
                    "isWX" to "1"
                ),
                timestamp = timestamp,
                nonce = nonce
            )
        )
    }

    private companion object {
        const val DIGITS = "0123456789"
    }
}
