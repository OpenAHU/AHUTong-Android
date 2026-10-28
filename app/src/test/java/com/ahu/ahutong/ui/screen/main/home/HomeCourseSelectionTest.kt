package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.forDate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCourseSelectionTest {
    private val firstMonday = LocalDate.of(2026, 10, 5)
    private fun course(name: String, day: Int, weeks: List<Int>) = Course().apply {
        this.name = name
        setWeekday(day.toString())
        setStartTime("1")
        setLength("2")
        setStartWeek(weeks.first().toString())
        setEndWeek(weeks.last().toString())
        weekIndexes = weeks
        clockRange = "08:00-09:35"
    }

    private fun config(week: Int = 3, day: Int = 1) = ScheduleConfigBean().apply {
        this.week = week
        weekDay = day
        isInSemester = true
        startTime = Date.from(firstMonday.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    @Test fun staleCalendarSelectsCoursesFromTheCurrentDayAndTeachingWeek() {
        for ((date, day, week) in listOf(
            Triple("2026-10-22", 4, 3),
            Triple("2026-10-26", 1, 4),
            Triple("2026-11-02", 1, 5)
        )) {
            val yesterday = course("cached", 3, listOf(3))
            val current = course("current", day, listOf(week))
            val cached = config(day = 3)
            val result = selectHomeCourses(listOf(yesterday, current), emptyList(), true,
                cached.forDate(LocalDate.parse(date)), currentMinutes = 0)
            assertEquals(date, listOf(current), result.today)
            assertEquals(date, listOf(current), result.strip)
            assertFalse(result.isTomorrow)
            assertEquals(3, cached.weekDay)
        }
    }

    @Test fun graduateHomeUsesTheGraduateTimetableEvenWhenUndergraduateCacheExists() {
        val result = selectHomeCourses(
            listOf(course("本科缓存", 1, listOf(3))),
            listOf(course("研究生课程", 1, listOf(3))),
            undergraduateEnabled = false, config(), currentMinutes = 8 * 60
        )
        assertEquals(listOf("研究生课程"), result.today.map { it.name })
        assertEquals(result.today, result.strip)
        assertFalse(result.isTomorrow)
    }

    @Test fun tomorrowDoesNotInventMissingWeeksWithTheSameParity() {
        val today = course("今日课程", 1, listOf(3))
        val tomorrow = course("明日课程", 2, listOf(3))
        val irregular = course("隔周但不在本周", 2, listOf(1, 5))
        val result = selectHomeCourses(listOf(today, tomorrow, irregular), emptyList(), true,
            config(), currentMinutes = 10 * 60)
        assertEquals(listOf(today), result.today)
        assertEquals(listOf(tomorrow), result.strip)
        assertTrue(result.isTomorrow)
    }

    @Test fun sundayTomorrowUsesTheNextTeachingWeek() {
        val sunday = course("周日", 7, listOf(3))
        val nextMonday = course("下周一", 1, listOf(4))
        val oldMonday = course("本周一", 1, listOf(3))
        val result = selectHomeCourses(listOf(sunday, nextMonday, oldMonday), emptyList(), true,
            config(day = 7), currentMinutes = 10 * 60)
        assertEquals(listOf(nextMonday), result.strip)
        assertTrue(result.isTomorrow)
    }

    @Test fun missingConfigAndSemesterBreakNeverShowFallbackMondayCourses() {
        val courses = listOf(course("周一", 1, listOf(1)))
        assertTrue(selectHomeCourses(courses, emptyList(), true, null, 8 * 60).today.isEmpty())
        val vacation = config(week = 1).apply { isInSemester = false }
        assertTrue(selectHomeCourses(courses, emptyList(), true, vacation, 8 * 60).strip.isEmpty())
    }
}
