package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.data.ProgramCompletionGateway
import com.ahu.ahutong.data.crawler.model.jwxt.ProgramCompletion
import com.ahu.ahutong.ext.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 培养方案完成情况页的状态源。
 *
 * 低频数据：缓存优先，仅无缓存时自动拉取，之后只靠手动刷新（一学期只变几次，轮询无意义）。
 */
@HiltViewModel
class ProgramCompletionViewModel @Inject constructor() : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val data: ProgramCompletion? = null,
        val fetchedAt: Long = 0L,
        val error: String? = null,
        val incompleteOnly: Boolean = false
    )

    val state = MutableStateFlow(UiState())

    init {
        load()
    }

    private fun load() = viewModelScope.launchSafe {
        val cached = withContext(Dispatchers.IO) { ProgramCompletionGateway.loadCached() }
        if (cached != null) {
            state.value = UiState(loading = false, data = cached.first, fetchedAt = cached.second)
        } else {
            refreshInternal(showLoading = true)
        }
    }

    fun refresh() = viewModelScope.launchSafe {
        refreshInternal(showLoading = state.value.data == null)
    }

    private suspend fun refreshInternal(showLoading: Boolean) {
        state.value = state.value.copy(
            loading = showLoading,
            refreshing = !showLoading,
            error = null
        )
        when (val result = ProgramCompletionGateway.refresh()) {
            is AhuResult.Success -> state.value = UiState(
                loading = false,
                data = result.value.first,
                fetchedAt = result.value.second,
                incompleteOnly = state.value.incompleteOnly
            )
            is AhuResult.Failure -> state.value = state.value.copy(
                loading = false,
                refreshing = false,
                // 有旧缓存时刷新失败不清空旧数据，只提示
                error = result.error.toUserMessage()
            )
        }
    }

    fun toggleIncompleteOnly() {
        state.value = state.value.copy(incompleteOnly = !state.value.incompleteOnly)
    }
}
