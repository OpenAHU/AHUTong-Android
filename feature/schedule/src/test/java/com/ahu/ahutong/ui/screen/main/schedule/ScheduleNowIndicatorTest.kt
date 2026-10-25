package com.ahu.ahutong.ui.screen.main.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ScheduleNowIndicatorTest {
    private val timetable = mapOf(
        1 to "08:00-08:45",
        2 to "08:50-09:35",
        3 to "09:50-10:35",
        4 to "10:40-11:25",
        5 to "11:30-12:15",
        6 to "14:00-14:45",
        12 to "19:50-20:35"
    )

    @Test
    fun `before first class hovers above section 1`() {
        val position = nowIndicatorPosition(6 * 60 + 30, timetable)
        val inGap = assertIs<NowIndicatorPosition.InGap>(position)
        assertEquals(0, inGap.afterSection)
        assertEquals(390f / 480f, inGap.fraction, 1e-6f)
    }

    @Test
    fun `after last class returns null`() {
        assertNull(nowIndicatorPosition(20 * 60 + 35, timetable))
        assertNull(nowIndicatorPosition(23 * 60, timetable))
    }

    @Test
    fun `midnight maps to the very start of the pre-class band`() {
        val position = nowIndicatorPosition(0, timetable)
        val inGap = assertIs<NowIndicatorPosition.InGap>(position)
        assertEquals(0, inGap.afterSection)
        assertEquals(0f, inGap.fraction, 1e-6f)
    }

    @Test
    fun `short gap between classes maps to gap with fraction`() {
        val position = nowIndicatorPosition(8 * 60 + 47, timetable)
        val inGap = assertIs<NowIndicatorPosition.InGap>(position)
        assertEquals(1, inGap.afterSection)
        assertEquals(2f / 5f, inGap.fraction, 1e-6f)
    }

    @Test
    fun `mid lunch break at 13h maps to gap after section 5`() {
        val position = nowIndicatorPosition(13 * 60 + 7, timetable)
        val inGap = assertIs<NowIndicatorPosition.InGap>(position)
        assertEquals(5, inGap.afterSection)
        // 12:15 → 14:00 共 105 分钟，13:07 处于 52/105
        assertEquals(52f / 105f, inGap.fraction, 1e-6f)
    }

    @Test
    fun `last minute of a class still maps inside the section`() {
        val position = nowIndicatorPosition(8 * 60 + 44, timetable)
        val inSection = assertIs<NowIndicatorPosition.InSection>(position)
        assertEquals(1, inSection.section)
        assertEquals(44f / 45f, inSection.fraction, 1e-6f)
    }

    @Test
    fun `empty timetable returns null`() {
        assertNull(nowIndicatorPosition(600, emptyMap()))
    }

    @Test
    fun `invalid clock entries are skipped`() {
        val broken = mapOf(
            1 to "08:00-08:45",
            2 to "garbage",
            3 to ""
        )
        // 第 2、3 节无效被跳过；8:47 仍在第 1 节内（但已接近末尾），课间无下一节 → 边界安全
        val position = nowIndicatorPosition(8 * 60 + 44, broken)
        val inSection = assertIs<NowIndicatorPosition.InSection>(position)
        assertEquals(1, inSection.section)
        // 第 1 节后无有效节次，45 分钟及之后应返回 null（无下一锚点）
        assertNull(nowIndicatorPosition(8 * 60 + 46, broken))
    }

    @Test
    fun `gap merges visually for consecutive combined sections`() {
        // 第 1、2 节合并成一张卡时，两节各自映射行高，线在卡内连续前进；
        // 第 2 节开始瞬间（09:35 边界后的 08:50 开始点）应处于第 2 节而非间隙
        val position = nowIndicatorPosition(8 * 60 + 50, timetable)
        val inSection = assertIs<NowIndicatorPosition.InSection>(position)
        assertEquals(2, inSection.section)
        assertEquals(0f, inSection.fraction, 1e-6f)
    }

}
