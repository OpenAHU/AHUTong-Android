package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.CanteenWindowStore
import com.ahu.ahutong.data.canteen.CanteenGateway
import com.ahu.ahutong.data.canteen.InsightRankingItem
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
                UiState.Ready(p, data.withLearnedFloors())
            } else {
                UiState.Error(p, "榜单加载失败，请检查网络后重试")
            }
        }
    }

    /** 标题栏刷新：绕过端侧缓存。 */
    fun refresh() = load(period, forceRefresh = true)
}

/**
 * 楼层兜底：服务端条目的 floor 来自映射表（管理员踩点填写），未收录窗口为空——
 * 用本地从账单学习的「终端→楼层」补（你去过的窗口都有楼层）。零服务端改动。
 */
private fun InsightsResponse.withLearnedFloors(): InsightsResponse {
    fun InsightRankingItem.patch(): InsightRankingItem =
        if (floor != null) this else copy(floor = CanteenWindowStore.learnedFloorOf(terminal))
    return copy(
        ranking = ranking?.map { it.patch() },
        superlatives = superlatives?.let { s ->
            s.copy(
                topWindow = s.topWindow?.patch(),
                lunchTopWindow = s.lunchTopWindow?.patch(),
                dinnerTopWindow = s.dinnerTopWindow?.patch(),
                mostConsistent = s.mostConsistent?.patch(),
                leastPopular = s.leastPopular?.patch(),
                mostImproved = s.mostImproved?.patch()
            )
        }
    )
}
