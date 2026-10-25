package com.ahu.ahutong.testing

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.recharge.BathroomDepositSource
import com.ahu.ahutong.data.recharge.BathroomPayment
import com.ahu.ahutong.data.recharge.RechargeReceipt

/**
 * [BathroomDepositSource] 的 fake：feature 只看见查询和一次完整支付。
 */
class FakeBathroomDepositSource : BathroomDepositSource {

    var infoHook: (suspend (String, String) -> AhuResult<BathroomTelInfo>)? = null

    var paymentHook: (suspend (BathroomPayment) -> AhuResult<RechargeReceipt>)? = null

    val payments = mutableListOf<BathroomPayment>()

    var savedPhoneValue: String? = null

    override suspend fun bathroomInfo(bathroom: String, tel: String): AhuResult<BathroomTelInfo> {
        return requireNotNull(infoHook) { "infoHook 未设置" }.invoke(bathroom, tel)
    }

    override suspend fun pay(payment: BathroomPayment): AhuResult<RechargeReceipt> {
        payments += payment
        return requireNotNull(paymentHook) { "paymentHook 未设置" }.invoke(payment)
    }

    override fun savedPhone(): String? = savedPhoneValue
}
