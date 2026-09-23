package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.recharge.analytics.CardAnalyticsReport
import com.ahu.ahutong.data.recharge.analytics.toAnalyticsReport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 账单统计：拉全「服务器当月」流水（分页循环），后台线程跑分析引擎。
 * 口径与账单页一致（不传时间参数 = 服务器当月）。
 */
@HiltViewModel
class BillingStatsViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 20

        /** 进程内暂存上次统计结果：进入页面先秒显旧数据，后台静默重算后覆盖。 */
        @Volatile
        private var cachedReport: CardAnalyticsReport? = null
    }

    sealed interface StatsState {
        data object Loading : StatsState
        data class Ready(val report: CardAnalyticsReport) : StatsState
        data class Error(val message: String) : StatsState
    }

    private val _state = MutableStateFlow<StatsState>(
        cachedReport?.let { StatsState.Ready(it) } ?: StatsState.Loading
    )
    val state: StateFlow<StatsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        // 有缓存：保持展示旧数据静默重算；无缓存：才进加载态干等
        if (cachedReport == null) {
            _state.value = StatsState.Loading
        }
        viewModelScope.launch {
            val records = fetchAllPages()
            if (records == null) {
                // 有缓存时静默失败（旧数据继续展示）；无缓存才报错
                if (cachedReport == null) {
                    _state.value = StatsState.Error("账单统计加载失败，请稍后重试")
                }
                return@launch
            }
            val report = withContext(Dispatchers.Default) {
                records.toAnalyticsReport()
            }
            cachedReport = report
            _state.value = StatsState.Ready(report)
        }
    }

    /** 分页循环拉全当月流水（orderId 去重）；任何一页失败即整体失败。 */
    private suspend fun fetchAllPages(): List<TurnoverRecord>? {
        val all = mutableListOf<TurnoverRecord>()
        val seen = HashSet<String>()
        var page = 1
        while (page <= MAX_PAGES) {
            when (val result = AHURepository.getBillPage(page = page, size = PAGE_SIZE)) {
                is AhuResult.Success -> {
                    val pageData = result.value
                    val rows = pageData.records.orEmpty()
                    all += rows.filter { seen.add(it.orderId) }
                    val totalPages = pageData.pages ?: 1
                    if (rows.isEmpty() || page >= totalPages) return all
                    page++
                }
                is AhuResult.Failure -> return null
            }
        }
        return all
    }
}
