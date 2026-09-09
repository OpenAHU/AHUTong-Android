package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleSnapshotComparatorTest {
    @Test
    fun `server ordering alone does not count as an update`() {
        val first = course("高等数学", "博学南楼101")
        val second = course("大学英语", "博学北楼202")

        assertFalse(ScheduleSnapshotComparator.hasChanged(listOf(first, second), listOf(second, first)))
    }

    @Test
    fun `course content change is detected`() {
        val cached = course("高等数学", "博学南楼101")
        val latest = course("高等数学", "博学南楼102")

        assertTrue(ScheduleSnapshotComparator.hasChanged(listOf(cached), listOf(latest)))
    }

    @Test
    fun `missing cache counts as an update`() {
        assertTrue(ScheduleSnapshotComparator.hasChanged(null, listOf(course("高等数学", "博学南楼101"))))
    }

    private fun course(name: String, location: String) = Course().apply {
        this.name = name
        this.location = location
        setWeekday("1")
        setStartWeek("1")
        setEndWeek("16")
        setStartTime("1")
        setLength("2")
        weekIndexes = (1..16).toList()
    }
}
