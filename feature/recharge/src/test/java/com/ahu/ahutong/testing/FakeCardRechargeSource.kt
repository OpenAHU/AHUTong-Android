package com.ahu.ahutong.testing

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.PayResponse
import com.ahu.ahutong.data.model.CardRechargeBank
import com.ahu.ahutong.data.recharge.CardRechargeSource
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [CardRechargeSource] 的假实现：三步各自可编排，并记下调用次数与参数。
 *
 * 它们让校园卡充值的三段流程第一次能在 JVM 上走完——迁移前那段逻辑直接拿着
 * AHURepository 与 OkHttp 的 Response，只能在真机上点。
 */
class FakeCardRechargeSource(
    var accountResult: AhuResult<CardInfo> = AhuResult.Success(fakeCardInfo()),
    var openOrderResult: AhuResult<String?> = AhuResult.Success("order-1"),
    var payResult: AhuResult<PayResponse?> =
        AhuResult.Success(PayResponse(code = 200, `data` = "order-1", msg = "ok", success = true))
) : CardRechargeSource {

    var accountCalls = 0
        private set
    var openOrderCalls = 0
        private set
    var payCalls = 0
        private set

    var lastAmount: String? = null
        private set
    var lastCardType: String? = null
        private set
    var lastOrderId: String? = null
        private set
    var lastBank: CardRechargeBank? = null
        private set

    /** 本机记住的充值方式：saveRechargeBank 写它，rechargeBank 读它。 */
    var bank: CardRechargeBank? = null
        private set

    var mockData = false

    val mockRevisions = MutableStateFlow(0L)

    override suspend fun account(): AhuResult<CardInfo> {
        accountCalls++
        return accountResult
    }

    override suspend fun openOrder(amount: String, cardType: String): AhuResult<String?> {
        openOrderCalls++
        lastAmount = amount
        lastCardType = cardType
        return openOrderResult
    }

    override suspend fun pay(orderId: String, bank: CardRechargeBank): AhuResult<PayResponse?> {
        payCalls++
        lastOrderId = orderId
        lastBank = bank
        return payResult
    }

    override fun rechargeBank(): CardRechargeBank? = bank

    override fun saveRechargeBank(bank: CardRechargeBank) {
        this.bank = bank
    }

    override fun usesMockData(): Boolean = mockData

    override fun mockRefreshRevisions(): Flow<Long> = mockRevisions
}

/** 只提供 ViewModel 使用的卡类型和余额；协议解析由 data/recharge 的测试覆盖。 */
fun fakeCardInfo(cardType: String = "1", balance: Int = 12345): CardInfo =
    Gson().fromJson(
        """{"code":0,"msg":"ok","success":true,"data":{"retcode":"0","card":[
            {"account":"2021001","name":"张三","sno":"2021001","cardtype":"$cardType",
             "accinfo":[{"name":"电子账户","type":"$cardType","balance":$balance}]}
        ]}}""",
        CardInfo::class.java
    )
