package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.canteen.CanteenGateway
import com.ahu.ahutong.data.canteen.InsightsResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 必吃榜页状态源：period 切换走网关 5 分钟缓存。 */
@HiltViewModel
class CanteenRankingViewModel @Inject constructor() : ViewModel() {

    enum class Period(val api: String, val label: String) {
        WEEK("week", "本周"), MONTH("month", "本月"), ALL("all", "全部")
    }

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val period: Period, val data: InsightsResponse) : UiState
        data class Error(val period: Period, val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var period = Period.WEEK

    init {
        // 每次进入页面都强制拉新（用户要求）；缓存只挡页面内切周期的重发
        load(Period.WEEK, forceRefresh = true)
    }

    fun load(p: Period, forceRefresh: Boolean = false) {
        period = p
        val cached = _state.value
        if (cached !is UiState.Ready) _state.value = UiState.Loading
        viewModelScope.launch {
            val data = CanteenGateway.insights(period = p.api, limit = 50, forceRefresh = forceRefresh)
            _state.value = if (data != null) {
                UiState.Ready(p, data)
            } else {
                UiState.Error(p, "榜单加载失败，请检查网络后重试")
            }
        }
    }

    /** 标题栏刷新：绕过端侧缓存。 */
    fun refresh() = load(period, forceRefresh = true)
}
