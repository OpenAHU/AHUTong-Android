package com.ahu.ahutong.ui.state

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.data.payment.PaymentQrRepository
import com.ahu.ahutong.data.payment.PaymentQrOwner
import com.ahu.ahutong.data.session.AhuSession
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.context.BalanceBucket
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.ahu.ahutong.core.common.onSuccess

/**
 * @Author Simon
 * @Date 2021/8/3-22:12
 * @Email 330771794@qq.com
 */


@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val paymentQrRepository: PaymentQrRepository,
    private val behaviorRuntime: BehaviorPredictionRuntime,
    private val session: AhuSession
) : ViewModel() {

    val TAG = DiscoveryViewModel::class.java.simpleName

    val bathroom = mutableStateMapOf<String, String>()
    var balance by mutableStateOf(0.0)
    var transitionBalance by mutableStateOf(0.0)

    val qrcode = MutableStateFlow<Bitmap?>(null)
    val state = MutableStateFlow(false)
    val qrStatusText = MutableStateFlow("")
    val qrRefreshing = MutableStateFlow(false)
    private val qrLoader = PaymentQrLoader(paymentQrRepository, viewModelScope, ::refreshCardBalance)
    private var qrWarmJob: Job? = null
    private var warmOwner: PaymentQrOwner? = null
    private var renderedQrKey: Pair<String, Long>? = null
    private var qrRenderFailed = false

    init {
        viewModelScope.launch {
            session.state.collectLatest { status ->
                if (status == AhuSessionState.Status.Anonymous && paymentQrRepository.currentOwner() == null) {
                    clearQrCode()
                    qrWarmJob?.cancel()
                    warmOwner = null
                }
            }
        }
        viewModelScope.launch {
            qrLoader.state.collectLatest { value ->
                val snapshot = value.snapshot
                val key = snapshot?.let { it.payload to it.fetchedAtEpochMs }
                if (key != renderedQrKey) {
                    qrRenderFailed = false
                    val bitmap = try {
                        snapshot?.let {
                            withContext(Dispatchers.IO) {
                                BarcodeEncoder().encodeBitmap(
                                    it.payload, BarcodeFormat.QR_CODE, 400, 400,
                                    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 1)
                                )
                            }
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        qrRenderFailed = true
                        Log.w("QR", "Unable to render payment QR")
                        null
                    }
                    qrcode.value = bitmap
                    renderedQrKey = key
                }
                qrStatusText.value = if (qrRenderFailed) "二维码数据暂不可用，请重试" else value.statusText
                qrRefreshing.value = value.loading
                state.value = value.attempted || snapshot != null
            }
        }
    }

    /** Start before profile/native initialization and let the ordinary balance work wait. */
    fun preparePaymentQr() {
        if (AHUCache.getMockData()) return
        val owner = paymentQrRepository.currentOwner() ?: return
        if (warmOwner == owner) return
        warmOwner = owner
        qrWarmJob = viewModelScope.launchSafe { paymentQrRepository.prepareForForeground() }
    }

    suspend fun awaitPaymentQrPreparation() { qrWarmJob?.join() }

    fun loadActivityBean() {
        preparePaymentQr()
        // 优先加载缓存
        if (!AHUCache.getMockData()) {
            AHUCache.getCardBalance()?.let {
                balance = it
            }
        }

        viewModelScope.launchSafe {
            qrWarmJob?.join()
            val (cardResult, bathroomResult) = coroutineScope {
                val card = async { AHURepository.getCardMoney() }
                val bathrooms = async { AHURepository.getBathRooms() }
                card.await() to bathrooms.await()
            }
            cardResult.onSuccess {
                applyCardBalance(it.balance, it.transitionBalance)
            }
            bathroomResult.onSuccess {
                bathroom.clear()
                it.forEach { room ->
                    bathroom += room.bathroom to room.openStatus
                }
            }
        }
    }

    fun refreshCardBalance() {
        if (!AHUCache.getMockData()) {
            AHUCache.getCardBalance()?.let {
                balance = it
            }
        }

        viewModelScope.launchSafe {
            qrWarmJob?.join()
            AHURepository.getCardMoney().onSuccess {
                applyCardBalance(it.balance, it.transitionBalance)
            }
        }
    }

    private fun applyCardBalance(balanceValue: Double?, transitionBalanceValue: Double?) {
        val newBalance = balanceValue ?: 0.0
        balance = newBalance
        AHUCache.saveCardBalance(newBalance)
        transitionBalance = transitionBalanceValue ?: transitionBalance
        behaviorRuntime.onBusinessContextChanged(
            newBalanceBucket = when {
                newBalance < 5.0 -> BalanceBucket.ZERO_TO_FIVE
                newBalance < 10.0 -> BalanceBucket.FIVE_TO_TEN
                newBalance < 20.0 -> BalanceBucket.TEN_TO_TWENTY
                newBalance < 50.0 -> BalanceBucket.TWENTY_TO_FIFTY
                else -> BalanceBucket.FIFTY_PLUS
            },
            newBalanceFresh = true
        )
    }

    fun loadQrCode(forceRefresh: Boolean = false) {
        qrLoader.start(forceRefresh)
    }

    fun clearQrCode() {
        qrLoader.stop()
        renderedQrKey = null
        qrRenderFailed = false
        qrcode.value = null
        state.value = false
        qrStatusText.value = ""
        qrRefreshing.value = false
    }

}
