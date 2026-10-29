package com.ahu.ahutong.ui.screen.main.schedule

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.scheduleVisibleWeekdays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleCourseGroupsTest {
    @Test
    fun `weekday layout hides weekend courses in weekly and overview modes without changing data`() {
        val weekday = course("周五", day = 5)
        val saturday = course("周六", day = 6)
        val sunday = course("周日", day = 7)
        val courses = listOf(weekday, saturday, sunday)
        val visibleDaySets = mapOf(
            5 to (1..5).toSet(),
            6 to scheduleVisibleWeekdays(showSaturday = true, showSunday = false).toSet(),
            7 to (1..7).toSet()
        )
        visibleDaySets.forEach { (dayCount, visibleDays) ->
            val expected = courses.filter { it.weekday in visibleDays }.map { listOf(it) }
            assertEquals(expected, weeklyCourseGroups(courses, 1, visibleDays))
            assertEquals(expected, overlappingCourseGroups(courses, visibleDays))
        }
        // 只隐藏周六时，周日课程仍要留在最右列（非连续可见天）。
        val saturdayHidden = scheduleVisibleWeekdays(showSaturday = false, showSunday = true).toSet()
        assertEquals(
            listOf(listOf(weekday), listOf(sunday)),
            weeklyCourseGroups(courses, 1, saturdayHidden)
        )
        assertEquals(listOf(weekday, saturday, sunday), courses)
    }

    @Test
    fun `same slot courses are retained in one group`() {
        val first = course("数学")
        val second = course("英语")
        assertEquals(listOf(listOf(first, second)), weeklyCourseGroups(listOf(first, second), 1))
        assertTrue(hasCourseConflict(listOf(first, second)))
    }

    @Test
    fun `overview retains courses when one time range contains another`() {
        val shorter = course("数学", start = 1, length = 2)
        val longer = course("英语", start = 1, length = 3)
        val courses = listOf(shorter, longer)
        assertEquals(listOf(courses), overlappingCourseGroups(courses))
        assertEquals(listOf(courses), weeklyCourseGroups(courses, 1))
        assertTrue(hasCourseConflict(courses))
    }

    @Test
    fun `partial and transitive overlaps form one group regardless of input order`() {
        val first = course("数学", start = 1, length = 2)
        val second = course("英语", start = 2, length = 2)
        val third = course("物理", start = 3, length = 2)
        val courses = listOf(third, first, second)
        val expected = listOf(listOf(first, second, third))
        assertEquals(expected, overlappingCourseGroups(courses))
        assertEquals(expected, weeklyCourseGroups(courses, 1))
    }

    @Test
    fun `adjacent periods and different days are separate groups`() {
        val first = course("数学")
        val adjacent = course("英语", start = 3)
        val nextDay = course("物理", day = 2)
        val courses = listOf(first, adjacent, nextDay)
        val expected = listOf(listOf(first), listOf(adjacent), listOf(nextDay))
        assertEquals(expected, overlappingCourseGroups(courses))
        assertEquals(expected, weeklyCourseGroups(courses, 1))
        assertFalse(hasCourseConflict(courses))
    }

    @Test
    fun `different teaching weeks do not cause a conflict or join weekly groups`() {
        val oddWeeks = course("数学", weeks = listOf(1, 3))
        val evenWeeks = course("英语", weeks = listOf(2, 4))
        val courses = listOf(oddWeeks, evenWeeks)
        assertEquals(listOf(listOf(oddWeeks)), weeklyCourseGroups(courses, 1))
        assertEquals(listOf(listOf(evenWeeks)), weeklyCourseGroups(courses, 2))
        assertEquals(emptyList(), weeklyCourseGroups(courses, 5))
        assertFalse(hasCourseConflict(courses))
        assertTrue(hasCourseConflict(courses + course("物理", weeks = listOf(3))))
    }

    @Test
    fun `overview groups overlapping odd and even week courses without a conflict`() {
        val oddWeeks = course("数学", start = 1, length = 2, weeks = listOf(1, 3))
        val evenWeeks = course("英语", start = 1, length = 3, weeks = listOf(2, 4))
        val courses = listOf(oddWeeks, evenWeeks)
        assertEquals(listOf(courses), overlappingCourseGroups(courses))
        assertEquals(listOf(listOf(oddWeeks)), weeklyCourseGroups(courses, 1))
        assertEquals(listOf(listOf(evenWeeks)), weeklyCourseGroups(courses, 2))
        assertFalse(hasCourseConflict(courses))
    }

    @Test
    fun `overview detects a shared teaching week in partially overlapping ranges`() {
        val first = course("数学", start = 1, length = 2, weeks = listOf(1, 3))
        val second = course("英语", start = 2, length = 2, weeks = listOf(3, 5))
        val courses = listOf(first, second)
        assertEquals(listOf(courses), overlappingCourseGroups(courses))
        assertTrue(hasCourseConflict(courses))
    }

    private fun course(
        title: String,
        day: Int = 1,
        start: Int = 1,
        length: Int = 2,
        weeks: List<Int> = listOf(1)
    ) = Course().apply {
        name = title
        setWeekday(day.toString())
        setStartTime(start.toString())
        setLength(length.toString())
        weekIndexes = weeks
    }
}
