package com.ahu.ahutong.ui.screen.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.attendance.AttendanceRecord
import com.ahu.ahutong.data.attendance.KqcardAttendanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AttendanceViewModel @Inject constructor() : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val records: List<AttendanceRecord> = emptyList(),
        val totalCount: Int = 0,
        val status: Int = 0,
        val error: String? = null
    ) {
        val hasMore: Boolean get() = records.size < totalCount
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var page = 0

    init {
        refresh()
    }

    fun refresh(status: Int = _state.value.status) {
        load(status, 1, reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        load(current.status, page + 1, reset = false)
    }

    private fun load(status: Int, targetPage: Int, reset: Boolean) {
        if (_state.value.loading) return
        _state.update {
            it.copy(
                loading = true,
                status = status,
                error = null,
                records = if (reset) emptyList() else it.records
            )
        }
        viewModelScope.launch {
            runCatching {
                KqcardAttendanceRepository.loadPage(status, targetPage)
            }.onSuccess { result ->
                page = targetPage
                _state.update { current ->
                    current.copy(
                        loading = false,
                        records = if (reset) result.records else current.records + result.records,
                        totalCount = result.totalCount,
                        error = null
                    )
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(loading = false, error = failure.message ?: "考勤加载失败")
                }
            }
        }
    }
}
