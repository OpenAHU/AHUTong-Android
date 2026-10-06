package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.CanteenWindowStore
import com.ahu.ahutong.data.canteen.CanteenGateway
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.recharge.analytics.CanteenFootprint
import com.ahu.ahutong.data.recharge.analytics.toCanteenFootprint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 干饭足迹页状态源：拉近 6 个月全量流水 → 后台线程跑 [toCanteenFootprint]。
 * 窗口改名（求认领标注）不重新拉网，用暂存流水本地重算。
 */
@HiltViewModel
class CanteenFootprintViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 60
        private const val RANGE_DAYS = 180L

        /** 进程内暂存：再进页面秒显；标注窗口名后直接本地重算。 */
        @Volatile
        private var cachedRecords: List<TurnoverRecord>? = null

        @Volatile
        private var cachedFootprint: CanteenFootprint? = null
    }

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val footprint: CanteenFootprint) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(
        cachedFootprint?.let { UiState.Ready(it) } ?: UiState.Loading
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (cachedFootprint == null) _state.value = UiState.Loading
        viewModelScope.launch {
            val records = fetchAllPages()
            if (records == null) {
                if (cachedFootprint == null) {
                    _state.value = UiState.Error("账单加载失败，请稍后重试")
                }
                return@launch
            }
            cachedRecords = records
            // 先同步服务器映射表（版本比对，失败则沿用本地缓存），再重算足迹
            CanteenGateway.syncWindowMap()
            recompute(records)
            // 顺带把最近 30 天聚合量匿名上传（幂等可重传，静默失败）
            CanteenGateway.uploadStats(records)
        }
    }

    private suspend fun recompute(records: List<TurnoverRecord>) {
        val fp = withContext(Dispatchers.Default) {
            records.toCanteenFootprint(windowNames = CanteenWindowStore.all())
        }
        cachedFootprint = fp
        _state.value = UiState.Ready(fp)
    }

    /** 近 6 个月全量分页拉取（orderId 去重），任一失败即整体失败。 */
    private suspend fun fetchAllPages(): List<TurnoverRecord>? {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val timeFrom = fmt.format(Date(System.currentTimeMillis() - RANGE_DAYS * 24 * 3600 * 1000))
        val timeTo = fmt.format(Date())
        val all = mutableListOf<TurnoverRecord>()
        val seen = HashSet<String>()
        var page = 1
        while (page <= MAX_PAGES) {
            when (
                val result = AHURepository.getBillPage(
                    page = page, size = PAGE_SIZE, timeFrom = timeFrom, timeTo = timeTo
                )
            ) {
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
