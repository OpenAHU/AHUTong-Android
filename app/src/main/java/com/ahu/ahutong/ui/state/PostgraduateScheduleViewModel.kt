package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.schedule.ScheduleCalendarSync
import com.ahu.ahutong.data.crawler.gmis.PostgraduateScheduleRepository
import com.ahu.ahutong.data.schedule.gmis.GmisTerm
import com.ahu.ahutong.data.schedule.gmis.GmisTimetable
import com.ahu.ahutong.data.schedule.gmis.GmisTimetableAdapter
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.schedule.PostgraduateScheduleController
import com.ahu.ahutong.data.schedule.PostgraduateScheduleState
import com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PostgraduateScheduleViewModel @Inject constructor(
    private val calendarSync: ScheduleCalendarSync
) : ViewModel(), PostgraduateScheduleController {
    private val mutableState = MutableStateFlow(PostgraduateScheduleState())
    override val state = mutableState.asStateFlow()
    private var accountId: String? = null
    private val repository = PostgraduateScheduleRepository.instance
    private var job: Job? = null
    private var refreshedTimetable: GmisTimetable? = null

    override fun onScheduleEntered(account: String?) {
        if (accountId != account) {
            job?.cancel()
            accountId = account
            refreshedTimetable = null
            mutableState.value = PostgraduateScheduleState()
        }
        if (account == null) return
        val syncEnabled = calendarSync.isEnabled() && !AHUCache.getMockData()
        if (syncEnabled) {
            if (job?.isActive != true) load(force = true, currentTerm = true)
        } else if (mutableState.value.timetable == null && !mutableState.value.loading) {
            // current() already prefers the account cache; its decoding belongs on IO too.
            load(force = false)
        }
    }

    override fun cleanPreviousCalendarWeek() {
        val account = accountId ?: return
        viewModelScope.launch { calendarSync.cleanPreviousWeek { isCalendarOwner(account) } }
    }

    override fun refresh() = load(force = true)

    private fun load(force: Boolean, currentTerm: Boolean = false) {
        val account = accountId ?: return
        job?.cancel()
        val previousCode = mutableState.value.selectedTerm?.code?.takeUnless { currentTerm }
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
                if (force && (snapshot.selectedTerm.selected || snapshot.terms.none { it.selected })) {
                    refreshedTimetable = snapshot.timetable
                    syncCourses(account)
                }
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
        refreshedTimetable = null
        mutableState.value = mutableState.value.copy(
            selectedTerm = term, timetable = null, loading = true, error = null,
            firstWeekMonday = storedFirstMonday(term), today = DebugClock.nowLocalDate()
        )
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
        val account = accountId ?: return true
        viewModelScope.launch { syncCourses(account) }
        return true
    }

    private suspend fun syncCourses(account: String) {
        if (!calendarSync.isEnabled() || !isCalendarOwner(account)) return
        val state = mutableState.value
        val table = refreshedTimetable?.takeIf { it === state.timetable } ?: return
        val firstMonday = state.firstWeekMonday ?: return
        val courses = GmisTimetableAdapter.adapt(table).courses
        calendarSync.syncCourses(courses, firstMonday) {
            isCalendarOwner(account) && mutableState.value.timetable === table &&
                mutableState.value.firstWeekMonday == firstMonday
        }
    }

    private fun isCalendarOwner(account: String): Boolean =
        AHUCache.getCurrentUser()?.xh == account && !AHUCache.getMockData()

    private fun storedFirstMonday(term: GmisTerm): LocalDate? =
        PostgraduateTeachingWeek.parseStored(AHUCache.getPostgraduateWeekStart(term.code))

    private fun safeMessage(error: Exception): String = when (error) {
        is com.ahu.ahutong.data.crawler.gmis.GmisSessionExpiredException -> "研究生教务登录已失效，请重新登录后再试"
        is com.ahu.ahutong.data.crawler.gmis.GmisProtocolException -> error.message.orEmpty()
        is java.io.IOException -> "研究生课表连接失败，请稍后刷新"
        else -> "研究生课表读取失败，请稍后刷新"
    }
}
