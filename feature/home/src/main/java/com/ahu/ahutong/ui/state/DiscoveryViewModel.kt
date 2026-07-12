package com.ahu.ahutong.ui.state

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.campuscard.CampusCardLocalStore
import com.ahu.ahutong.data.campuscard.CampusCardRepository
import com.ahu.ahutong.data.home.HomePreferences
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.ScheduleWeekResolver
import com.ahu.ahutong.ext.launchSafe
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * @Author Simon
 * @Date 2021/8/3-22:12
 * @Email 330771794@qq.com
 */
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val campusCardRepository: CampusCardRepository,
    private val campusCardLocalStore: CampusCardLocalStore,
    private val homePreferences: HomePreferences,
    private val scheduleWeekResolver: ScheduleWeekResolver,
) : ViewModel() {

    val TAG = DiscoveryViewModel::class.java.simpleName

    val bathroom = mutableStateMapOf<String, String>()
    var balance by mutableStateOf(0.0)
    var transitionBalance by mutableStateOf(0.0)

    val visibilities = mutableStateListOf<Int>()

    var qrcode = MutableStateFlow<Bitmap?>(null)
    var state = MutableStateFlow(false)

    fun isLoggedIn(): Boolean = campusCardLocalStore.isLoggedIn()

    fun isMockMode(): Boolean = campusCardLocalStore.isMockMode()

    fun getHomeWidgetSlots(): List<String?> = homePreferences.getHomeWidgetSlots()

    fun saveHomeWidgetSlots(slots: List<String?>) {
        homePreferences.saveHomeWidgetSlots(slots)
    }

    fun getWeatherShowOnHome(): Boolean = homePreferences.getWeatherShowOnHome()

    fun currentMinutes(locale: Locale = Locale.CHINA): Int =
        scheduleWeekResolver.currentMinutes(locale)

    fun nowDate(): Date = scheduleWeekResolver.nowDate()

    fun resolveLocalScheduleConfig(): ScheduleConfigBean? =
        scheduleWeekResolver.resolveLocalConfig()?.config

    fun loadActivityBean() {
        // 优先加载缓存
        if (!campusCardLocalStore.isMockMode()) {
            campusCardLocalStore.getCachedBalance()?.let {
                balance = it
            }
        }

        viewModelScope.launchSafe {
            when (val result = campusCardRepository.getBalance()) {
                is AppResult.Success -> applyCardBalance(
                    result.data.balance,
                    result.data.transitionBalance,
                )
                is AppResult.Error -> Log.w(TAG, "load balance failed: ${result.message}")
            }

            when (val rooms = campusCardRepository.getBathrooms()) {
                is AppResult.Success -> {
                    bathroom.clear()
                    rooms.data.forEach { room ->
                        bathroom += room.bathroom to room.openStatus
                    }
                }
                is AppResult.Error -> Log.w(TAG, "load bathrooms failed: ${rooms.message}")
            }
        }
    }

    fun refreshCardBalance() {
        if (!campusCardLocalStore.isMockMode()) {
            campusCardLocalStore.getCachedBalance()?.let {
                balance = it
            }
        }

        viewModelScope.launchSafe {
            when (val result = campusCardRepository.getBalance(isRefresh = true)) {
                is AppResult.Success -> applyCardBalance(
                    result.data.balance,
                    result.data.transitionBalance,
                )
                is AppResult.Error -> Log.w(TAG, "refresh balance failed: ${result.message}")
            }
        }
    }

    private fun applyCardBalance(balanceValue: Double?, transitionBalanceValue: Double?) {
        val newBalance = balanceValue ?: 0.0
        balance = newBalance
        campusCardLocalStore.saveBalance(newBalance)
        transitionBalance = transitionBalanceValue ?: transitionBalance
    }

    fun loadQrCode() {
        viewModelScope.launchSafe {
            withContext(Dispatchers.IO) {
                state.value = false
                try {
                    when (val response = campusCardRepository.getQrcode()) {
                        is AppResult.Success -> {
                            val hints = HashMap<EncodeHintType, Any>()
                            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L
                            hints[EncodeHintType.MARGIN] = 1
                            val encoder = BarcodeEncoder()
                            qrcode.value = encoder.encodeBitmap(
                                response.data,
                                BarcodeFormat.QR_CODE,
                                400,
                                400,
                                hints,
                            )
                        }
                        is AppResult.Error -> {
                            Log.e("QR", "接口返回错误: ${response.message}", response.cause)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("QR", "未知异常", e)
                }
                state.value = true
            }
        }
    }
}
