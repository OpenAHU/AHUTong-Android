package com.ahu.ahutong.data.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomCourseTest {

    @Test
    fun `snap evening custom time to nearest sections`() {
        val span = CustomCourseMath.snapToSections(
            CustomCourseMath.parseClockMinutes("19:10")!!,
            CustomCourseMath.parseClockMinutes("20:45")!!
        )
        assertEquals(11, span.startSection)
        assertEquals(12, span.endSection)
    }

    @Test
    fun `snap early morning custom time into first section`() {
        val span = CustomCourseMath.snapToSections(
            CustomCourseMath.parseClockMinutes("07:30")!!,
            CustomCourseMath.parseClockMinutes("08:30")!!
        )
        assertEquals(1, span.startSection)
        assertEquals(1, span.endSection)
    }

    @Test
    fun `snap never returns end before start`() {
        val span = CustomCourseMath.snapToSections(
            CustomCourseMath.parseClockMinutes("18:30")!!,
            CustomCourseMath.parseClockMinutes("18:50")!!
        )
        assertEquals(11, span.startSection)
        assertEquals(11, span.endSection)
    }

    @Test
    fun `parse and format clock round trip`() {
        assertEquals(19 * 60 + 10, CustomCourseMath.parseClockMinutes("19:10"))
        assertEquals("19:10", CustomCourseMath.formatClockMinutes(19 * 60 + 10))
        assertNull(CustomCourseMath.parseClockMinutes("25:00"))
        assertNull(CustomCourseMath.parseClockMinutes("abc"))
    }

    @Test
    fun `custom course maps to course with custom clock range`() {
        val custom = CustomCourse(
            id = "abc",
            name = "晚自习",
            location = "图书馆",
            slots = listOf(
                CustomCourseSlot(
                    weekday = 2,
                    weekIndexes = listOf(1, 2, 3, 4),
                    startSection = 1,
                    endSection = 1,
                    customStartMinutes = 19 * 60 + 10,
                    customEndMinutes = 20 * 60 + 45
                )
            )
        )

        val course = CustomCourseMapper.toCourses(custom).single()
        assertEquals("晚自习", course.name)
        assertEquals(2, course.weekday)
        assertEquals(listOf(1, 2, 3, 4), course.weekIndexes)
        assertEquals(11, course.startTime)
        assertEquals(2, course.length)
        assertEquals("19:10-20:45", course.clockRange)
        assertTrue(course.isCustomCourse())
        assertEquals("abc", course.customCourseId())
    }

    @Test
    fun `merge keeps custom courses even when remote cache is missing`() {
        val custom = CustomCourse(
            id = "abc",
            name = "晚自习",
            slots = listOf(
                CustomCourseSlot(
                    weekday = 1,
                    weekIndexes = listOf(1),
                    startSection = 11,
                    endSection = 12
                )
            )
        )

        val merged = assertNotNull(CustomCourseMapper.merge(null, listOf(custom))).single()
        assertEquals(11, merged.startTime)
        assertEquals(2, merged.length)
        assertNull(merged.clockRange)
    }

    @Test
    fun `corrupt slot maps are dropped instead of crashing normalization`() {
        @Suppress("UNCHECKED_CAST")
        val course = CustomCourse(
            id = "abc",
            name = "晚自习",
            slots = listOf(mapOf("weekday" to 1)) as List<CustomCourseSlot>
        )
        assertNull(course.normalized())
    }

    @Test
    fun `valid slots survive alongside corrupt entries`() {
        @Suppress("UNCHECKED_CAST")
        val course = CustomCourse(
            id = "abc",
            name = "晚自习",
            slots = listOf(
                CustomCourseSlot(
                    weekday = 1,
                    weekIndexes = listOf(1),
                    startSection = 11,
                    endSection = 12
                ),
                mapOf("weekday" to 2)
            ) as List<CustomCourseSlot>
        )
        val normalized = assertNotNull(course.normalized())
        assertEquals(1, normalized.slots.single().weekday)
    }

    @Test
    fun `normalization drops invalid weeks and keeps valid slots`() {
        val course = CustomCourse(
            id = "abc",
            name = "  晚自习  ",
            slots = listOf(
                CustomCourseSlot(
                    weekday = 1,
                    weekIndexes = listOf(0, 1, 2, 21, 2),
                    startSection = 11,
                    endSection = 12
                )
            )
        )
        val normalized = assertNotNull(course.normalized())
        assertEquals("晚自习", normalized.name)
        assertEquals(listOf(1, 2), normalized.slots.single().weekIndexes)
    }
}
