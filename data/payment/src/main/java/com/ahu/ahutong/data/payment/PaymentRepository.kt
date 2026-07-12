package com.ahu.ahutong.data.payment

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.model.BathroomTelInfo

/**
 * Campus payment (bathroom / card recharge) data API.
 */
interface PaymentRepository {
    suspend fun getBathroomInfo(bathroom: String, tel: String): AppResult<BathroomTelInfo>

    suspend fun getCardInfo(): AppResult<CardInfo>

    suspend fun createOrder(request: RequestBody): AppResult<PaymentHttpResult>

    suspend fun pay(request: RequestBody): AppResult<PaymentHttpResult>
}
