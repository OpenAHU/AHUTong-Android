package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.schedule.gmis.GmisTerm
import com.ahu.ahutong.data.schedule.gmis.GmisTimetable
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

data class PostgraduateScheduleState(
    val terms: List<GmisTerm> = emptyList(),
    val selectedTerm: GmisTerm? = null,
    val timetable: GmisTimetable? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val firstWeekMonday: LocalDate? = null,
    val today: LocalDate = LocalDate.now()
)

interface PostgraduateScheduleController {
    val state: StateFlow<PostgraduateScheduleState>
    fun open(account: String?)
    fun refresh()
    fun selectTerm(term: GmisTerm)
    fun refreshCalendar()
    fun saveCurrentWeek(week: Int): Boolean
}
