package com.ahu.ahutong.data.payment.internal

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.payment.PaymentCredentialGate
import com.ahu.ahutong.data.payment.PaymentHttpResult
import com.ahu.ahutong.data.payment.PaymentRemoteSource
import com.ahu.ahutong.data.payment.PaymentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultPaymentRepository @Inject constructor(
    private val remoteSource: PaymentRemoteSource,
    private val credentialGate: PaymentCredentialGate,
) : PaymentRepository {

    override suspend fun getBathroomInfo(
        bathroom: String,
        tel: String,
    ): AppResult<BathroomTelInfo> = withContext(Dispatchers.IO) {
        remoteSource.fetchBathroomInfo(bathroom, tel)
    }

    override suspend fun getCardInfo(): AppResult<CardInfo> = withContext(Dispatchers.IO) {
        if (!credentialGate.isReady()) {
            return@withContext AppResult.error("校园卡登录凭证暂未就绪，请稍后重试")
        }
        remoteSource.fetchCardInfo()
    }

    override suspend fun createOrder(request: RequestBody): AppResult<PaymentHttpResult> =
        withContext(Dispatchers.IO) {
            if (!credentialGate.isReady()) {
                return@withContext AppResult.error("校园卡登录凭证暂未就绪，请稍后重试")
            }
            remoteSource.createOrder(request)
        }

    override suspend fun pay(request: RequestBody): AppResult<PaymentHttpResult> =
        withContext(Dispatchers.IO) {
            if (!credentialGate.isReady()) {
                return@withContext AppResult.error("校园卡登录凭证暂未就绪，请稍后重试")
            }
            remoteSource.pay(request)
        }
}
