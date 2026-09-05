package com.ahu.ahutong.ui.state

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomRequest
import com.ahu.ahutong.data.crawler.model.ycard.PayResponse
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.ext.launchSafe
import com.google.gson.Gson
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

class BathroomDepositViewModel(
    private val queryBathroomInfo: suspend (String, String) -> AHUResponse<BathroomTelInfo> =
        { bathroom, tel -> AHURepository.getBathroomInfo(bathroom, tel) },
    private val queryDispatcher: CoroutineDispatcher = Dispatchers.IO
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
    fun pay(bathroom:String,amount: String,password: String){

        _payState.value = PayState.InProgress
        val accountData = info.value?.data?.map?.data
        if (accountData == null) {
            _payState.value = PayState.Failed("请先查询有效的浴室账户")
            return
        }
        val paymentQueryGeneration = queryGeneration

        viewModelScope.launchSafe {
            withContext(Dispatchers.IO){
                accountData.let { data ->
                    data.myCustomInfo = "手机号：${data.telPhone}"

                    val thirdPartyJson = Gson().toJson(data)

                    val request = BathroomRequest(bathroom,amount,thirdPartyJson)


                    var res = AHURepository.pay(request).data
                    val jsonString = res.body()!!.string()

                    val regex = """"orderid"\s*:\s*"([^"]+)"""".toRegex()
                    val match = regex.find(jsonString)
                    val orderId = match?.groups?.get(1)?.value


                    orderId?.let{ orderId ->
                        val payRequest = BathroomPayRequest(orderId,password)
                        val res = AHURepository.pay(payRequest)


                        val payResponse: PayResponse? = res.data.body()?.string()?.let {
                            Gson().fromJson(it, PayResponse::class.java)
                        }

                        if(payResponse?.code == 200){
                            AHUCache.savePhone(data.telPhone)
                            _payState.value = PayState.Succeeded(message = payResponse.data)
                            paymentSuccessEvent.postValue(Unit)
                            delay(1_000)
                            withContext(Dispatchers.Main) {
                                if (paymentQueryGeneration == queryGeneration) {
                                    getBathroomInfo(bathroom, data.telPhone)
                                }
                            }
                        }else{
                            _payState.value = PayState.Failed(message = payResponse?.msg?:"未知错误")
                        }

                    }



                }
            }

        }
    }

}


