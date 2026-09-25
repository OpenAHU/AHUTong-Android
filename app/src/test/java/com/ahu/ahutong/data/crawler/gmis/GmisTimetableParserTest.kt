package com.ahu.ahutong.data.crawler.gmis

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class GmisTimetableParserTest {
    private val course = "<br/>示例算法[2-10周] 示例教师 [（示例校区）实验楼101]"
    private fun row(section: Int, group: String = "上午", day: Int = 2, text: String = course) = JsonObject().apply {
        addProperty("jcid", section)
        addProperty("sjbz", group)
        addProperty("mc", if (section == 99) "无节次" else "第${section}节<br/>(08:00-08:45)")
        addProperty("z$day", text)
    }
    private fun parse(vararg rows: JsonObject) = GmisTimetableParser.timetable(JsonObject().apply {
        add("rows", JsonArray().apply { rows.forEach(::add) })
        addProperty("week", "Friday")
    })

    @Test fun parsesDynamicSelectedSemesterInsteadOfHardcodingItsCode() {
        val terms = GmisTimetableParser.terms(JsonParser.parseString("""
            [{"termcode":"87","termname":"未来秋学期","selected":true},
             {"termcode":"86","termname":"上一春学期","selected":false}]
        """.trimIndent()))
        assertEquals("87", terms.single { it.selected }.code)
        assertEquals("未来秋学期", terms.first().name)
    }

    @Test fun invalidOrAmbiguousTermsAreRejected() {
        for (body in listOf("[]", "{}", """[{"termcode":"54"}]""",
            """[{"termcode":"1","termname":"甲","selected":true},{"termcode":"2","termname":"乙","selected":true}]""")) {
            assertFailsWith<GmisProtocolException> { GmisTimetableParser.terms(JsonParser.parseString(body)) }
        }
    }

    @Test fun mergesRepeatedCellsIntoOneMultiSectionCourse() {
        val data = parse(row(2), row(3), row(4), row(5))
        val result = data.courses.single()
        assertEquals("示例算法", result.name)
        assertEquals("示例教师", result.teacher)
        assertEquals("（示例校区）实验楼101", result.location)
        assertEquals(2, result.weekday)
        assertEquals(2, result.startSection)
        assertEquals(5, result.endSection)
        assertEquals((2..10).toSet(), result.weeks)
        assertEquals("08:00-08:45", result.clock)
    }

    @Test fun multipleCoursesInOneCellStaySeparateAndMergeIndependently() {
        val mixed = course + "<br/><br/>示例网络[11-14周] 另一教师 [示例楼202]"
        val data = parse(row(2, text = mixed), row(3, text = mixed), row(4, text = course))
        assertEquals(2, data.courses.size)
        assertEquals(4, data.courses.first { it.name == "示例算法" }.endSection)
        assertEquals(3, data.courses.first { it.name == "示例网络" }.endSection)
        assertEquals(1, data.forWeek(12).size)
        assertEquals("示例网络", data.forWeek(12).single().name)
    }

    @Test fun gapsDaysAndMiddayBreaksDoNotMerge() {
        val data = parse(row(2), row(4), row(5), row(6, "下午"), row(7, "下午", day = 3))
        assertEquals(4, data.courses.size)
        assertEquals(listOf(2, 4, 6, 7), data.courses.map { it.startSection })
    }

    @Test fun readsFourteenthPeriodAndUntimedRowsWithoutUsingUndergraduateTimeMap() {
        val last = row(14, "晚上").apply { addProperty("mc", "第14节<br/>(21:30-22:15)") }
        val data = parse(last, row(99, "", text = "研究活动，时间另行通知"))
        assertEquals("21:30-22:15", data.courses.first().clock)
        assertEquals(14, data.courses.first().startSection)
        assertNull(data.courses.last().startSection)
        assertNull(data.courses.last().weeks)
        assertEquals("研究活动，时间另行通知", data.courses.last().details)
    }

    @Test fun understandsRangesExplicitWeeksAndOddEvenWeeks() {
        assertEquals(setOf(1, 2, 3, 6, 8, 9, 10), GmisTimetableParser.parseWeeks("1-3,6,8-10周"))
        assertEquals(setOf(1, 3, 5), GmisTimetableParser.parseWeeks("1-6周(单)"))
        assertEquals(setOf(2, 4, 6), GmisTimetableParser.parseWeeks("1-6(双)周"))
        assertEquals(setOf(2, 4, 6), GmisTimetableParser.parseWeeks("2、4、6周"))
        assertEquals(setOf(15), GmisTimetableParser.parseWeeks("15-15周"))
        assertNull(GmisTimetableParser.parseWeeks("10-2周"))
        assertNull(GmisTimetableParser.parseWeeks("待定周"))
    }

    @Test fun weekdayFieldNeverBecomesTeachingWeekAndUnknownWeeksStayVisible() {
        val data = parse(row(2, text = "专题活动，安排待定"))
        assertEquals(0, data.maxWeek)
        assertEquals(1, data.forWeek(9).size)
    }

    @Test fun distinguishesValidEmptyRowsFromMalformedResponses() {
        assertTrue(parse().courses.isEmpty())
        assertFailsWith<GmisProtocolException> { GmisTimetableParser.timetable(JsonParser.parseString("{}")) }
        assertFailsWith<GmisProtocolException> { parse(row(2), row(2)) }
    }

    @Test fun htmlEntitiesAreDecodedAndDuplicateEntriesInACellAreNotDuplicated() {
        val data = parse(row(1, text = "<b>示例&amp;课程</b>[1-2周] 教师 [教室]<br/><br/><b>示例&amp;课程</b>[1-2周] 教师 [教室]"))
        assertEquals(1, data.courses.size)
        assertEquals("示例&课程", data.courses.single().name)
    }

    @Test fun graduateLocationIsCompactInTheCourseDataUsedByCardsAndDetails() {
        val data = parse(row(2, text = "<br/>示例课程[2-10周] 示例教师 [（江淮）教学主楼北阶208]"))
        assertEquals("主楼北阶208", data.courses.single().location)
    }
}
