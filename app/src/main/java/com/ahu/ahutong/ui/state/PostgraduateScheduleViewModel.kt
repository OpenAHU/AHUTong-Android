package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.crawler.gmis.PostgraduateScheduleRepository
import com.ahu.ahutong.data.schedule.gmis.GmisTerm
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.schedule.PostgraduateScheduleController
import com.ahu.ahutong.data.schedule.PostgraduateScheduleState
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

class PostgraduateScheduleViewModel : ViewModel(), PostgraduateScheduleController {
    private val mutableState = MutableStateFlow(PostgraduateScheduleState())
    override val state = mutableState.asStateFlow()
    private var accountId: String? = null
    private val repository = PostgraduateScheduleRepository.instance
    private var job: Job? = null

    override fun open(account: String?) {
        if (accountId == account && (mutableState.value.timetable != null || mutableState.value.loading)) return
        job?.cancel()
        accountId = account
        val cached = account?.let(repository::cachedCurrent)
        mutableState.value = if (cached == null) PostgraduateScheduleState() else
            PostgraduateScheduleState(
                cached.terms, cached.selectedTerm, cached.timetable,
                firstWeekMonday = storedFirstMonday(cached.selectedTerm),
                today = DebugClock.nowLocalDate()
            )
        if (account != null && cached == null) load(force = false)
    }

    override fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        val account = accountId ?: return
        job?.cancel()
        val previousCode = mutableState.value.selectedTerm?.code
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    if (force) repository.refresh(account, previousCode) else repository.current(account)
                }
                if (AHUCache.getCurrentUser()?.xh != account) return@launch
                mutableState.value = PostgraduateScheduleState(
                    snapshot.terms, snapshot.selectedTerm, snapshot.timetable,
                    firstWeekMonday = storedFirstMonday(snapshot.selectedTerm),
                    today = DebugClock.nowLocalDate()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (AHUCache.getCurrentUser()?.xh != account) return@launch
                mutableState.value = mutableState.value.copy(loading = false, error = safeMessage(e))
            }
        }
    }

    override fun selectTerm(term: GmisTerm) {
        if (mutableState.value.selectedTerm?.code == term.code && mutableState.value.timetable != null) return
        val account = accountId ?: return
        job?.cancel()
        val cached = repository.cachedForTerm(account, term.code)
        mutableState.value = mutableState.value.copy(
            selectedTerm = term, timetable = cached, loading = cached == null, error = null,
            firstWeekMonday = storedFirstMonday(term), today = DebugClock.nowLocalDate()
        )
        if (cached != null) return
        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.forTerm(account, term) }
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

    override fun refreshCalendar() {
        if (accountId == null || AHUCache.getCurrentUser()?.xh != accountId) return
        val term = mutableState.value.selectedTerm ?: return
        mutableState.value = mutableState.value.copy(
            firstWeekMonday = storedFirstMonday(term), today = DebugClock.nowLocalDate()
        )
    }

    override fun saveCurrentWeek(week: Int): Boolean {
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
