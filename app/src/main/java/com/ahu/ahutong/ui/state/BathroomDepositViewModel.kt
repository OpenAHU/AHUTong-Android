package com.ahu.ahutong.ui.state

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomRequest
import com.ahu.ahutong.data.crawler.model.ycard.PayResponse
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.payment.PaymentRepository
import com.ahu.ahutong.ext.launchSafe
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@HiltViewModel
class BathroomDepositViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    val TAG = "BathroomDepositViewModel"

    private val _info = MutableStateFlow<AHUResponse<BathroomTelInfo>?>(null)
    val info: StateFlow<AHUResponse<BathroomTelInfo>?> = _info

    var _payState = MutableStateFlow<PayState>(PayState.Idle)
    val payState: StateFlow<PayState> = _payState

    fun resetPaymentState() {
        _payState.value = PayState.Idle
    }

    fun getBathroomInfo(bathroom: String, tel: String) {
        viewModelScope.launchSafe {
            withContext(Dispatchers.IO) {
                _info.value = when (
                    val result = paymentRepository.getBathroomInfo(bathroom = bathroom, tel = tel)
                ) {
                    is AppResult.Success -> AHUResponse<BathroomTelInfo>().apply {
                        code = 0
                        data = result.data
                        msg = "success"
                    }
                    is AppResult.Error -> AHUResponse<BathroomTelInfo>().apply {
                        code = result.code ?: -1
                        msg = result.message
                    }
                }
            }
        }
    }

    val paymentSuccessEvent = MutableLiveData<Unit>()

    fun pay(bathroom: String, amount: String, password: String) {
        _payState.value = PayState.InProgress
        paymentSuccessEvent.value = Unit

        if (info.value == null) return

        viewModelScope.launchSafe {
            withContext(Dispatchers.Default) {
                info.value!!.data.map!!.data?.let { data ->
                    data.myCustomInfo = "手机号：${data.telPhone}"
                    val thirdPartyJson = Gson().toJson(data)
                    val request = BathroomRequest(bathroom, amount, thirdPartyJson)

                    when (val orderResult = paymentRepository.pay(request)) {
                        is AppResult.Success -> {
                            val jsonString = orderResult.data.body
                            val regex = """"orderid"\s*:\s*"([^"]+)"""".toRegex()
                            val orderId = regex.find(jsonString)?.groups?.get(1)?.value
                            if (orderId == null) {
                                _payState.value = PayState.Failed(message = "未获取到订单号")
                                return@withContext
                            }

                            val payRequest = BathroomPayRequest(orderId, password)
                            when (val payResult = paymentRepository.pay(payRequest)) {
                                is AppResult.Success -> {
                                    val payResponse = Gson().fromJson(
                                        payResult.data.body,
                                        PayResponse::class.java,
                                    )
                                    if (payResponse?.code == 200) {
                                        when (
                                            val refreshed = paymentRepository.getBathroomInfo(
                                                bathroom = bathroom,
                                                tel = data.telPhone,
                                            )
                                        ) {
                                            is AppResult.Success -> {
                                                _info.value = AHUResponse<BathroomTelInfo>().apply {
                                                    code = 0
                                                    this.data = refreshed.data
                                                    msg = "success"
                                                }
                                            }
                                            is AppResult.Error -> Unit
                                        }
                                        AHUCache.savePhone(data.telPhone)
                                        _payState.value =
                                            PayState.Succeeded(message = payResponse.data)
                                    } else {
                                        _payState.value = PayState.Failed(
                                            message = payResponse?.msg ?: "未知错误",
                                        )
                                    }
                                }
                                is AppResult.Error -> {
                                    _payState.value = PayState.Failed(message = payResult.message)
                                }
                            }
                        }
                        is AppResult.Error -> {
                            _payState.value = PayState.Failed(message = orderResult.message)
                        }
                    }
                }
            }
        }
    }
}
