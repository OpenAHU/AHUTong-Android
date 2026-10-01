package com.ahu.ahutong.notification

import com.ahu.ahutong.data.schedule.ScheduleHoliday
import com.ahu.ahutong.data.schedule.scheduleHolidayNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseHolidayNoticeTest {
    @Test
    fun `unknown calendar leaves the course reminder unchanged`() {
        val content = "10 分钟后上课 · 博学北楼101"
        assertEquals(content, withCourseHolidayNotice(content, null))
        assertEquals(content, withCourseHolidayNotice(content, ""))
    }

    @Test
    fun `holiday and adjusted workday both include automatic disclaimer without removing reminder`() {
        val content = "10 分钟后上课 · 博学北楼101"
        listOf(true, false).forEach { isOffDay ->
            val notice = scheduleHolidayNotice(ScheduleHoliday("国庆节", isOffDay))
            val displayed = withCourseHolidayNotice(content, notice)
            assertTrue(displayed.startsWith("$content\n"))
            assertTrue(displayed.contains(notice))
            assertTrue(displayed.contains("自动"))
            assertTrue(displayed.contains("自行确认"))
        }
    }
}
