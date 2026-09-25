package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.crawler.gmis.GmisScheduleClient
import com.ahu.ahutong.data.crawler.gmis.GmisTerm
import com.ahu.ahutong.data.crawler.gmis.GmisTimetable
import com.ahu.ahutong.data.crawler.gmis.PostgraduateScheduleSource
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PostgraduateScheduleState(
    val terms: List<GmisTerm> = emptyList(),
    val selectedTerm: GmisTerm? = null,
    val timetable: GmisTimetable? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val firstWeekMonday: LocalDate? = null,
    val today: LocalDate = LocalDate.now()
)

class PostgraduateScheduleViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(PostgraduateScheduleState())
    val state = mutableState.asStateFlow()
    private var accountId: String? = null
    private var client: GmisScheduleClient? = null
    private var job: Job? = null

    fun open(account: String?) {
        if (accountId == account && client != null) return
        job?.cancel()
        accountId = account
        client = account?.let(PostgraduateScheduleSource::forAccount)
        mutableState.value = PostgraduateScheduleState()
        refresh()
    }

    fun refresh() {
        val source = client ?: return
        val account = accountId ?: return
        job?.cancel()
        val previousCode = mutableState.value.selectedTerm?.code
        mutableState.value = mutableState.value.copy(loading = true, error = null, timetable = null)
        job = viewModelScope.launch {
            try {
                val terms = withContext(Dispatchers.IO) { source.terms() }
                if (AHUCache.getCurrentUser()?.xh != account) return@launch
                val selected = terms.firstOrNull { it.code == previousCode }
                    ?: terms.firstOrNull { it.selected } ?: terms.first()
                mutableState.value = PostgraduateScheduleState(
                    terms, selected, loading = true,
                    firstWeekMonday = storedFirstMonday(selected),
                    today = DebugClock.nowLocalDate()
                )
                val timetable = withContext(Dispatchers.IO) { source.timetable(selected) }
                if (AHUCache.getCurrentUser()?.xh != account) return@launch
                mutableState.value = mutableState.value.copy(timetable = timetable, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (AHUCache.getCurrentUser()?.xh != account) return@launch
                mutableState.value = mutableState.value.copy(loading = false, error = safeMessage(e))
            }
        }
    }

    fun selectTerm(term: GmisTerm) {
        if (mutableState.value.selectedTerm?.code == term.code && mutableState.value.timetable != null) return
        val source = client ?: return
        val account = accountId ?: return
        job?.cancel()
        mutableState.value = mutableState.value.copy(
            selectedTerm = term, timetable = null, loading = true, error = null,
            firstWeekMonday = storedFirstMonday(term), today = DebugClock.nowLocalDate()
        )
        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { source.timetable(term) }
                if (AHUCache.getCurrentUser()?.xh != account) return@launch
                mutableState.value = mutableState.value.copy(timetable = result, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (AHUCache.getCurrentUser()?.xh != account) return@launch
                mutableState.value = mutableState.value.copy(loading = false, error = safeMessage(e))
            }
        }
    }

    fun refreshCalendar() {
        if (accountId == null || AHUCache.getCurrentUser()?.xh != accountId) return
        val term = mutableState.value.selectedTerm ?: return
        mutableState.value = mutableState.value.copy(
            firstWeekMonday = storedFirstMonday(term), today = DebugClock.nowLocalDate()
        )
    }

    fun saveCurrentWeek(week: Int): Boolean {
        if (accountId == null || AHUCache.getCurrentUser()?.xh != accountId) return false
        val term = mutableState.value.selectedTerm ?: return false
        val today = DebugClock.nowLocalDate()
        val firstMonday = runCatching { PostgraduateTeachingWeek.firstMonday(week, today) }.getOrNull()
            ?: return false
        AHUCache.savePostgraduateWeekStart(term.code, firstMonday.toString())
        mutableState.value = mutableState.value.copy(firstWeekMonday = firstMonday, today = today)
        return true
    }

    private fun storedFirstMonday(term: GmisTerm): LocalDate? =
        PostgraduateTeachingWeek.parseStored(AHUCache.getPostgraduateWeekStart(term.code))

    private fun safeMessage(error: Exception): String = when (error) {
        is com.ahu.ahutong.data.crawler.gmis.GmisSessionExpiredException -> "研究生教务登录已失效，请重新登录后再试"
        is com.ahu.ahutong.data.crawler.gmis.GmisProtocolException -> error.message.orEmpty()
        is java.io.IOException -> "研究生课表连接失败，请稍后刷新"
        else -> "研究生课表读取失败，请稍后刷新"
    }
}
