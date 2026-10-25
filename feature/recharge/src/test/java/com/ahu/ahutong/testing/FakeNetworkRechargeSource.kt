package com.ahu.ahutong.testing

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.recharge.NetworkRechargeSnapshot
import com.ahu.ahutong.data.recharge.NetworkRechargeSource
import com.ahu.ahutong.data.recharge.RechargeReceipt

class FakeNetworkRechargeSource(
    var loadResult: AhuResult<NetworkRechargeSnapshot>,
    var payResult: AhuResult<RechargeReceipt>
) : NetworkRechargeSource {

    data class Payment(val snapshot: NetworkRechargeSnapshot, val amount: String, val password: String)
    val payments = mutableListOf<Payment>()

    override suspend fun load(): AhuResult<NetworkRechargeSnapshot> = loadResult

    override suspend fun pay(
        snapshot: NetworkRechargeSnapshot,
        amount: String,
        password: String
    ): AhuResult<RechargeReceipt> {
        payments += Payment(snapshot, amount, password)
        return payResult
    }
}
