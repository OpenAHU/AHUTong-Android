package com.ahu.ahutong.data.payment

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.model.BathroomTelInfo

/**
 * YCard/network implementation. Bound in `:app`.
 */
interface PaymentRemoteSource {
    suspend fun fetchBathroomInfo(bathroom: String, tel: String): AppResult<BathroomTelInfo>

    suspend fun fetchCardInfo(): AppResult<CardInfo>

    suspend fun createOrder(request: RequestBody): AppResult<PaymentHttpResult>

    suspend fun pay(request: RequestBody): AppResult<PaymentHttpResult>

    /**
     * Generic YCard fee-item query used by electricity room selection (and similar flows).
     */
    suspend fun postFeeItemThirdData(fields: Map<String, String>): AppResult<PaymentHttpResult>

    /**
     * Generic YCard pay endpoint used by electricity order/pay steps.
     */
    suspend fun postPayForm(fields: Map<String, String>): AppResult<PaymentHttpResult>
}
