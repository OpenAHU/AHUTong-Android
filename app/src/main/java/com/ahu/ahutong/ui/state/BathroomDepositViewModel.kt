package com.ahu.ahutong.ui.state

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.crawler.model.ycard.BathroomOrderResponse
import com.ahu.ahutong.data.crawler.model.ycard.BathroomCurrentTimeRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayInfoRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayPrepareRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayPrepareResponse
import com.ahu.ahutong.data.crawler.model.ycard.BathroomRequest
import com.ahu.ahutong.data.crawler.model.ycard.PayResponse
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.ext.launchSafe
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response

class BathroomDepositViewModel(
    private val queryBathroomInfo: suspend (String, String) -> AHUResponse<BathroomTelInfo> =
        { bathroom, tel -> AHURepository.getBathroomInfo(bathroom, tel) },
    private val queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val submitBathroomPayment: suspend (RequestBody) -> AHUResponse<Response<ResponseBody>> =
        { request -> AHURepository.pay(request) },
    private val paymentDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    val TAG = "BathroomDepositViewModel"

    private  val _info = MutableStateFlow<AHUResponse<BathroomTelInfo>?>(null)

    val info: StateFlow<AHUResponse<BathroomTelInfo>?> = _info

    private val _isQuerying = MutableStateFlow(false)
    val isQuerying: StateFlow<Boolean> = _isQuerying

    private val _queryError = MutableStateFlow<String?>(null)
    val queryError: StateFlow<String?> = _queryError

    private var queryJob: Job? = null
    private var queryGeneration = 0L

    var _payState = MutableStateFlow<PayState>(PayState.Idle)

    val payState : StateFlow<PayState> = _payState

    fun resetPaymentState() {
        _payState.value = PayState.Idle
    }

    fun clearBathroomInfo() {
        queryGeneration++
        queryJob?.cancel()
        queryJob = null
        _isQuerying.value = false
        _info.value = null
        _queryError.value = null
    }

    fun getBathroomInfo(bathroom: String, tel: String) {
        if (tel.length != 11) {
            clearBathroomInfo()
            return
        }
        val generation = ++queryGeneration
        queryJob?.cancel()
        _isQuerying.value = true
        _info.value = null
        _queryError.value = null
        queryJob = viewModelScope.launch {
            try {
                val response = withContext(queryDispatcher) {
                    queryBathroomInfo(bathroom, tel)
                }
                if (generation == queryGeneration) {
                    if (response.isSuccessful && response.data != null) {
                        _info.value = response
                    } else {
                        _queryError.value = response.msg?.takeIf { it.isNotBlank() }
                            ?: "未查询到浴室账户，请重试"
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == queryGeneration) {
                    _queryError.value = when (error) {
                        is UnknownHostException -> "网络不可用，请检查网络连接"
                        is SocketTimeoutException -> "请求超时，请重试"
                        is IOException -> "网络连接失败，请重试"
                        else -> "浴室账户查询失败，请重试"
                    }
                }
            } finally {
                if (generation == queryGeneration) {
                    _isQuerying.value = false
                }
            }
        }
    }



    val paymentSuccessEvent = MutableLiveData<Unit>()
    fun pay(bathroom: String, amount: String, password: String) {
        _payState.value = PayState.InProgress
        val accountData = info.value?.data?.map?.data
        if (accountData == null) {
            _payState.value = PayState.Failed("请先查询有效的浴室账户")
            return
        }
        val paymentQueryGeneration = queryGeneration
        var paymentStage = "创建订单"

        viewModelScope.launchSafe {
            try {
                withContext(paymentDispatcher) {
                    accountData.myCustomInfo = "房间：${accountData.telPhone}"
                    val thirdPartyJson = Gson().toJson(accountData)

                    val orderResponse = Gson().fromJson(
                        submitPayment(BathroomRequest(bathroom, amount, thirdPartyJson)),
                        BathroomOrderResponse::class.java
                    )
                    val orderId = orderResponse.data?.orderid
                    if (orderResponse.code != 200 || !orderResponse.success || orderId.isNullOrBlank()) {
                        throw BathroomPaymentException(orderResponse.msg.ifBlank { "创建浴室缴费订单失败" })
                    }

                    paymentStage = "初始化支付通道"
                    requireSuccessfulBusinessResponse(
                        submitPayment(BathroomPayInfoRequest(orderId)),
                        "初始化支付通道失败"
                    )

                    paymentStage = "获取支付密码映射"
                    val prepareResponse = Gson().fromJson(
                        submitPayment(BathroomPayPrepareRequest(orderId)),
                        BathroomPayPrepareResponse::class.java
                    )
                    val passwordMapping = prepareResponse.data?.passwordMap
                        ?.entries
                        ?.singleOrNull()
                    if (prepareResponse.code != 200 || !prepareResponse.success || passwordMapping == null) {
                        throw BathroomPaymentException(prepareResponse.msg.ifBlank { "获取支付密码映射失败" })
                    }

                    paymentStage = "同步支付时间"
                    requireSuccessfulBusinessResponse(
                        submitPayment(BathroomCurrentTimeRequest()),
                        "同步支付时间失败"
                    )

                    paymentStage = "提交扣款"
                    val payResponse = Gson().fromJson(
                        submitPayment(
                            BathroomPayRequest(
                                orderId = orderId,
                                plaintext = password,
                                uuid = passwordMapping.key,
                                passwordMap = passwordMapping.value
                            )
                        ),
                        PayResponse::class.java
                    )
                    if (payResponse.code != 200 || !payResponse.success) {
                        throw BathroomPaymentException(payResponse.msg.ifBlank { "浴室缴费失败" })
                    }

                    AHUCache.savePhone(accountData.telPhone)
                    _payState.value = PayState.Succeeded(message = payResponse.data.ifBlank { orderId })
                    paymentSuccessEvent.postValue(Unit)
                }
                delay(1_000)
                if (paymentQueryGeneration == queryGeneration) {
                    getBathroomInfo(bathroom, accountData.telPhone)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _payState.value = PayState.Failed(
                    when (error) {
                        is UnknownHostException -> "网络不可用，请检查网络连接"
                        is SocketTimeoutException -> "$paymentStage 超时，请先核对余额后再重试"
                        is IOException -> "网络连接失败，请重试"
                        else -> error.message?.takeIf { it.isNotBlank() } ?: "浴室缴费失败，请重试"
                    }
                )
            }
        }
    }

    private suspend fun submitPayment(request: RequestBody): String {
        val result = submitBathroomPayment(request)
        val httpResponse = result.data
            ?: throw BathroomPaymentException(result.msg?.takeIf { it.isNotBlank() } ?: "支付接口无响应")
        val responseBody = if (httpResponse.isSuccessful) {
            httpResponse.body()?.string()
        } else {
            httpResponse.errorBody()?.string()
        }
        if (!httpResponse.isSuccessful) {
            throw BathroomPaymentException(
                responseBody?.takeIf { it.isNotBlank() }
                    ?: result.msg?.takeIf { it.isNotBlank() }
                    ?: "支付接口请求失败（${httpResponse.code()}）"
            )
        }
        return responseBody?.takeIf { it.isNotBlank() }
            ?: throw BathroomPaymentException("支付接口返回空数据")
    }

    private fun requireSuccessfulBusinessResponse(json: String, fallbackMessage: String) {
        val response = JsonParser.parseString(json).asJsonObject
        val code = response.get("code")?.asInt
        if (code != 200) {
            val message = response.get("msg")
                ?.takeUnless { it.isJsonNull }
                ?.asString
                ?.takeIf { it.isNotBlank() }
            throw BathroomPaymentException(message ?: fallbackMessage)
        }
    }

    private class BathroomPaymentException(message: String) : Exception(message)

}


