package com.ahu.ahutong.data.notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArchiveResolverTest {

    private fun slot(
        name: String,
        start: Int,
        end: Int,
        weekday: Int = 3,
        weeks: Set<Int> = (1..16).toSet()
    ) = ArchiveSlot(name, weekday, weeks, start, end)

    private fun resolve(
        now: Int,
        slots: List<ArchiveSlot>,
        weekday: Int = 3,
        week: Int = 5,
        inSemester: Boolean = true,
        holiday: Boolean = false
    ) = resolveArchiveTarget(now, weekday, week, inSemester, holiday, slots)

    @Test
    fun `unique match inside the class window is archived`() {
        val target = resolve(now = 600, slots = listOf(slot("高等数学A", 590, 635)))
        assertEquals("高等数学A", target?.courseName)
        assertEquals(580..650, target?.window)
    }

    @Test
    fun `buffer windows apply on both sides`() {
        val s = listOf(slot("大学物理", 600, 645))
        // 课前 10 分钟仍算；再早一分钟就不算
        assertEquals("大学物理", resolve(590, s)?.courseName)
        assertNull(resolve(589, s))
        // 课后 15 分钟仍算；再晚一分钟就不算
        assertEquals("大学物理", resolve(660, s)?.courseName)
        assertNull(resolve(661, s))
    }

    @Test
    fun `wrong weekday or week never matches`() {
        val s = listOf(slot("大学物理", 600, 645, weekday = 3, weeks = setOf(1, 3, 5)))
        assertNull(resolve(600, s, weekday = 4))
        assertNull(resolve(600, s, week = 6))
        assertEquals("大学物理", resolve(600, s, week = 3)?.courseName)
    }

    @Test
    fun `overlapping courses fall back to unfiled`() {
        val s = listOf(
            slot("线性代数", 600, 645),
            slot("形势与政策", 620, 665)
        )
        assertNull(resolve(630, s))
    }

    @Test
    fun `same course listed twice still counts once`() {
        val s = listOf(slot("线性代数", 600, 645), slot("线性代数", 600, 645, weekday = 3))
        assertEquals("线性代数", resolve(610, s)?.courseName)
    }

    @Test
    fun `holiday and out-of-semester never archive`() {
        val s = listOf(slot("大学物理", 600, 645))
        assertNull(resolve(610, s, holiday = true))
        assertNull(resolve(610, s, inSemester = false))
    }

    @Test
    fun `course name suffix for weeks and rooms is stripped`() {
        assertEquals("高等数学A", archiveSubjectName("高等数学A（1-16周）"))
        assertEquals("大学物理", archiveSubjectName("大学物理(东区3201教室)"))
        // 真正的课程名后缀（不是周次/地点）保留
        assertEquals("高等数学（A）", archiveSubjectName("高等数学（A）"))
        assertEquals("数据结构", archiveSubjectName(" 数据结构 "))
    }
}
