package com.ahu.ahutong.data.payment

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.FormBody

@Singleton
class YcardPaymentRemoteSource @Inject constructor() : PaymentRemoteSource {
    private val gson = Gson()

    override suspend fun fetchBathroomInfo(
        bathroom: String,
        tel: String,
    ): AppResult<BathroomTelInfo> {
        val feeitemid = when (bathroom) {
            "竹园/龙河" -> "409"
            "桔园/蕙园" -> "430"
            else -> return AppResult.error("目前没有这个浴室啊")
        }

        return try {
            val formBody = FormBody.Builder()
                .add("feeitemid", feeitemid)
                .add("type", "IEC")
                .add("level", "1")
                .add("telPhone", tel)
                .build()
            val res = YcardApi.API.getFeeItemThirdData(formBody)
            if (!res.isSuccessful) {
                return AppResult.error("请求接口失败", code = res.code())
            }
            val json = res.body()?.string()
            val bathroomInfo = gson.fromJson(json, BathroomTelInfo::class.java)
                ?: return AppResult.error("数据返回错误")
            AppResult.success(bathroomInfo)
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取浴室账户失败", t)
        }
    }

    override suspend fun fetchCardInfo(): AppResult<CardInfo> {
        return try {
            AppResult.success(YcardApi.API.loadCardRecharge())
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取校园卡账户失败", t)
        }
    }

    override suspend fun createOrder(request: RequestBody): AppResult<PaymentHttpResult> {
        return try {
            val response = YcardApi.API.getOrderThirdData(request.toFormBody())
            val body = response.body()?.string().orEmpty()
            val url = response.raw().request.url.toString()
            if (!response.isSuccessful) {
                AppResult.error("创建订单失败", code = response.code())
            } else {
                AppResult.success(
                    PaymentHttpResult(
                        body = body,
                        requestUrl = url,
                        httpCode = response.code(),
                    ),
                )
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "创建订单失败", t)
        }
    }

    override suspend fun pay(request: RequestBody): AppResult<PaymentHttpResult> {
        return try {
            val response = YcardApi.API.pay(request.toFormBody())
            val body = response.body()?.string().orEmpty()
            val url = response.raw().request.url.toString()
            if (!response.isSuccessful) {
                AppResult.error("支付失败", code = response.code())
            } else {
                AppResult.success(
                    PaymentHttpResult(
                        body = body,
                        requestUrl = url,
                        httpCode = response.code(),
                    ),
                )
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "支付失败", t)
        }
    }
}
