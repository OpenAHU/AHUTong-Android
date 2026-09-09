package com.ahu.ahutong.data.crawler.model.ycard

import com.ahu.ahutong.data.crawler.utils.generateNonce
import com.ahu.ahutong.data.crawler.utils.getTimestamp

interface BathroomPaymentRequest

class BathroomRequest(
    bathroom: String,
    amount: String,
    thirdPartyJson: String,
    timestamp: String = getTimestamp(),
    nonce: String = generateNonce()
) : RequestBody(), BathroomPaymentRequest {

    init {
        var feeitemid :String? = null
        when(bathroom){
            "竹园/龙河"->{
                feeitemid = "409"
            }
            "桔园/蕙园"->{
                feeitemid = "430"
            }
            else -> {
                throw IllegalArgumentException("Unknown bathroom.")
            }
        }

        feeitemid.let{
            addParams(
                signedPaymentParams(
                    params = mapOf(
                        "feeitemid" to it,
                        "tranamt" to amount,
                        "flag" to "choose",
                        "source" to "app",
                        "paystep" to "0",
                        "abstracts" to "",
                        "redirect_url" to "https://ycard.ahu.edu.cn/plat",
                        "third_party" to thirdPartyJson
                    ),
                    timestamp = timestamp,
                    nonce = nonce
                )
            )
        }


    }
}
