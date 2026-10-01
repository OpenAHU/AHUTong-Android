package com.ahu.ahutong.ui.screen.main.schedule

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleAccessibilityTest {
    private val timetable = mapOf(
        1 to "08:00-08:45",
        2 to "08:50-09:35",
        13 to "20:40-21:25"
    )

    @Test
    fun `day header includes full weekday and date across a month boundary`() {
        assertEquals("星期一，8月31日", scheduleDayDescription(1, "08-31"))
        assertEquals("星期二，9月1日", scheduleDayDescription(2, "09-01"))
        assertEquals("星期日", scheduleDayDescription(7))
    }

    @Test
    fun `time axis labels expose exact start and end clocks`() {
        assertEquals("第13节，20:40至21:25", schedulePeriodDescription(13, timetable.getValue(13)))
    }

    @Test
    fun `day headers distinguish time off from adjusted workdays`() {
        assertEquals(
            "星期四，10月1日，国庆节放假",
            scheduleDayDescription(4, "10-01", ScheduleHoliday("国庆节", true))
        )
        assertEquals(
            "星期六，10月10日，国庆节调休补班，教学安排请自行确认",
            scheduleDayDescription(6, "10-10", ScheduleHoliday("国庆节", false))
        )
    }

    @Test
    fun `course exposes its date weekday periods clocks and full classroom`() {
        val course = course()

        assertEquals(
            "高等数学，星期二，9月1日，第1至2节，08:00至09:35，博学北楼 A101",
            courseScheduleDescription(course, timetable, date = "09-01")
        )
    }

    @Test
    fun `overview uses teaching weeks instead of suggesting a noncurrent date`() {
        val course = course().apply { weekIndexes = listOf(1, 3, 5) }

        assertEquals(
            "高等数学，星期二，1-5单周，第1至2节，08:00至09:35，博学北楼 A101",
            courseScheduleDescription(course, timetable, includeWeeks = true)
        )
    }

    private fun course() = Course().apply {
        name = "高等数学"
        location = "博学北楼 A101"
        setWeekday("2")
        setStartTime("1")
        setLength("2")
    }
}
