package com.ahu.ahutong.ui.state

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.model.ycard.CardBalanceRequest
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.CardPayRequest
import com.ahu.ahutong.data.crawler.model.ycard.PayResponse
import com.ahu.ahutong.data.payment.PaymentLocalStore
import com.ahu.ahutong.data.payment.PaymentRepository
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.feature.payment.R
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@HiltViewModel
class CardBalanceDepositViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val paymentLocalStore: PaymentLocalStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val TAG = "CardBalanceDepositViewModel"

    private val _cardInfo = MutableStateFlow<CardInfo?>(null)
    val cardInfo: StateFlow<CardInfo?> = _cardInfo

    private val _accountState = MutableStateFlow<CardAccountState>(CardAccountState.Loading)
    val accountState: StateFlow<CardAccountState> = _accountState

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState

    fun load() = viewModelScope.launchSafe {
        _accountState.value = CardAccountState.Loading
        when (val response = paymentRepository.getCardInfo()) {
            is AppResult.Success -> {
                _cardInfo.value = response.data
                _accountState.value = CardAccountState.Ready(response.data)
            }
            is AppResult.Error -> {
                _cardInfo.value = null
                _accountState.value = CardAccountState.Error(response.message)
            }
        }
    }

    fun charge(value: String) = viewModelScope.launchSafe {
        withContext(Dispatchers.IO) {
            _paymentState.value = PaymentState.Loading

            val accountInfo = when (val state = accountState.value) {
                is CardAccountState.Ready ->
                    state.cardInfo.data.card.getOrNull(0)?.accinfo?.getOrNull(0)
                else -> null
            }

            if (accountInfo == null) {
                _paymentState.value = PaymentState.Error(
                    appContext.getString(R.string.error_user_info_not_obtained)
                )
                return@withContext
            }

            val orderRequest = CardBalanceRequest(value, accountInfo.type)
            when (val orderResponse = paymentRepository.createOrder(orderRequest)) {
                is AppResult.Success -> {
                    val regex = Regex("[?]orderid=([^&]+)")
                    val orderId = regex.find(orderResponse.data.requestUrl)?.groupValues?.get(1)
                    if (orderId == null) {
                        _paymentState.value = PaymentState.Error(
                            appContext.getString(R.string.error_order_id_not_obtained)
                        )
                        return@withContext
                    }

                    try {
                        when (val payResponse = paymentRepository.pay(CardPayRequest(orderId))) {
                            is AppResult.Success -> {
                                val parsed = Gson().fromJson(
                                    payResponse.data.body,
                                    PayResponse::class.java,
                                )
                                if (parsed?.code == 200) {
                                    _paymentState.value = PaymentState.Success(parsed.data)
                                    load()
                                } else {
                                    _paymentState.value = PaymentState.Error(
                                        parsed?.msg
                                            ?: appContext.getString(R.string.payment_failed_simple)
                                    )
                                }
                            }
                            is AppResult.Error -> {
                                _paymentState.value = PaymentState.Error(payResponse.message)
                            }
                        }
                    } catch (e: Exception) {
                        _paymentState.value = PaymentState.Error(
                            appContext.getString(R.string.error_with_message, e.message)
                        )
                    }
                }
                is AppResult.Error -> {
                    _paymentState.value = PaymentState.Error(orderResponse.message)
                }
            }
        }
    }

    fun resetPaymentState() {
        _paymentState.value = PaymentState.Idle
    }

    fun isMockMode(): Boolean = paymentLocalStore.isMockMode()

    fun getCurrentUserId(): String? = paymentLocalStore.getCurrentUserId()

    fun getCurrentUserName(): String? = paymentLocalStore.getCurrentUserName()
}

sealed class CardAccountState {
    object Loading : CardAccountState()
    data class Ready(val cardInfo: CardInfo) : CardAccountState()
    data class Error(val message: String) : CardAccountState()
}

sealed class PaymentState {
    object Idle : PaymentState()
    object Loading : PaymentState()
    data class Success(val orderId: String) : PaymentState()
    data class Error(val message: String) : PaymentState()
}
