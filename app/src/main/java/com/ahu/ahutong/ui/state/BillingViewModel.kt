package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.recharge.analytics.displayMerchantText
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 账单页状态：本月收支汇总 + 流水分页（滚动加载更多 + 下拉/按钮刷新）。
 * 数据链路：YcardApi(401 单飞刷新) → DataSource → AHURepository（无 Rust fallback，流水接口仅 ycard 一条链）。
 */
@HiltViewModel
class BillingViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    sealed interface ListState {
        data object Loading : ListState
        data object Ready : ListState
        data class Error(val message: String) : ListState
    }

    /** 原始流水（未按金额过滤）。 */
    private val _records = MutableStateFlow<List<TurnoverRecord>>(emptyList())

    /** 金额区间筛选（分，null=不限），服务端无金额参数 → 本地过滤已加载记录。 */
    private val _amountRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val amountRange: StateFlow<Pair<Long?, Long?>> = _amountRange.asStateFlow()

    /** 类型筛选：true=支出 false=收入 null=全部。 */
    private val _typeFilter = MutableStateFlow<Boolean?>(null)
    val typeFilter: StateFlow<Boolean?> = _typeFilter.asStateFlow()

    /** 时间筛选：false=当月（不传参，服务器月份） true=全部时间（分页可达历史账单）。 */
    private val _allTime = MutableStateFlow(false)
    val allTime: StateFlow<Boolean> = _allTime.asStateFlow()

    /** 展示用流水 = 原始记录 ∩ 金额区间。 */
    val records: StateFlow<List<TurnoverRecord>> =
        combine(_records, _amountRange) { list, (min, max) ->
            list.filter { (min == null || it.tranamt >= min) && (max == null || it.tranamt <= max) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _listState = MutableStateFlow<ListState>(ListState.Loading)
    val listState: StateFlow<ListState> = _listState.asStateFlow()

    private val _summary = MutableStateFlow<TurnoverCount?>(null)
    val summary: StateFlow<TurnoverCount?> = _summary.asStateFlow()

    /** 是否还有下一页 */
    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var currentPage = 1
    private var totalPages = Int.MAX_VALUE

    /** 本月起止（YYYY-MM-DD，大小月/闰年由 Calendar 处理）——收支汇总用。 */
    private val currentMonthRange: Pair<String, String>
        get() {
            val cal = Calendar.getInstance(Locale.CHINA)
            val from = "%04d-%02d-01".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1
            )
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val to = "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            return from to to
        }

    init {
        refresh()
    }

    private fun billingErrorMessage(error: AhuError): String = when (error) {
        is AhuError.Network -> "网络连接失败，请检查校园网"
        is AhuError.Timeout -> "请求超时，请稍后重试"
        is AhuError.Unauthorized -> error.message
        is AhuError.ProtocolChanged -> "账单服务接口已变更，请更新应用"
        is AhuError.Server -> error.message
        is AhuError.Unknown -> error.message
    }

    /** 应用筛选（时间/类型/金额区间，元输入已换算成分传入），重置分页重拉。 */
    fun applyFilters(
        allTime: Boolean,
        expense: Boolean?,
        amountMinFen: Long?,
        amountMaxFen: Long?
    ) {
        _allTime.value = allTime
        _typeFilter.value = expense
        _amountRange.value = amountMinFen to amountMaxFen
        refresh()
    }

    fun refresh() {
        currentPage = 1
        totalPages = Int.MAX_VALUE
        _hasMore.value = true
        _listState.value = ListState.Loading
        _records.value = emptyList()
        loadSummary()
        loadPage(1)
    }

    fun loadNextPage() {
        if (_loadingMore.value || !_hasMore.value || _listState.value is ListState.Loading) return
        loadPage(currentPage)
    }

    private fun loadSummary() {
        viewModelScope.launch {
            val (from, to) = currentMonthRange
            when (val result = AHURepository.getBillSummary(from, to)) {
                is AhuResult.Success -> _summary.value = result.value
                is AhuResult.Failure -> Unit // 汇总失败不阻塞列表
            }
        }
    }

    private fun loadPage(page: Int) {
        viewModelScope.launch {
            _loadingMore.value = true
            // 默认不传时间参数 → 服务端返回「服务器当月」；
            // 全部时间 → 传超早起始日，分页可达历史账单
            val from = if (_allTime.value) "2020-01-01" else null
            when (val result = AHURepository.getBillPage(
                page = page,
                size = PAGE_SIZE,
                timeFrom = from,
                type = _typeFilter.value?.let { if (it) 2 else 1 }
            )) {
                is AhuResult.Success -> {
                    val pageData = result.value
                    // 认知版商户名：「北二区食堂一楼」→「榴园食堂一楼」（学生视角）
                    val newRecords = pageData.records.orEmpty().map { record ->
                        record.copy(
                            resume = displayMerchantText(record.resume),
                            toMerchant = displayMerchantText(record.toMerchant)
                        )
                    }
                    // total 为 null = 当月无数据（服务端边界行为）
                    totalPages = pageData.pages ?: 1
                    val existing = _records.value.map { it.orderId }.toHashSet()
                    _records.value = _records.value + newRecords.filter { it.orderId !in existing }
                    _hasMore.value = page < totalPages && newRecords.isNotEmpty()
                    currentPage = page + 1
                    _listState.value = ListState.Ready
                }
                is AhuResult.Failure -> {
                    if (_records.value.isEmpty()) {
                        _listState.value = ListState.Error(billingErrorMessage(result.error))
                    }
                }
            }
            _loadingMore.value = false
        }
    }
}
