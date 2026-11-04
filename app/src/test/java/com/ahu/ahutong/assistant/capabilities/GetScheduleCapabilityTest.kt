package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.google.gson.JsonObject
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetScheduleCapabilityTest {

    private val wednesday = LocalDate.of(2026, 3, 4)

    private fun config(week: Int = 3, weekday: Int = 3, inSemester: Boolean = true) =
        ScheduleConfigBean().apply {
            setWeek(week)
            setWeekDay(weekday)
            setInSemester(inSemester)
        }

    private fun course(name: String, weekday: String = "3") = Course().apply {
        setWeekday(weekday)
        setStartWeek("1")
        setEndWeek("16")
        setName(name)
        setLocation("东区3201")
        setStartTime("3")
        setLength("2")
    }

    private fun days(value: Int) = JsonObject().apply { addProperty("days", value) }

    @Test
    fun listsTodaysCoursesFromCache() = runBlocking {
        val capability = GetScheduleCapability(
            config = { config() },
            cached = { listOf(course("高等数学")) },
            today = { wednesday },
        )
        val outcome = capability.execute(days(1)) as AssistantOutcome.Success
        assertEquals("今天有 1 节课", outcome.answer.summary)
        assertTrue(outcome.answer.lines.single().contains("第 3-4 节"))
        assertTrue(outcome.answer.lines.single().contains("东区3201"))
        assertEquals("schedule", outcome.answer.route)
    }

    @Test
    fun spansTheRequestedDays() = runBlocking {
        val fridayCourse = course("大学物理", weekday = "5")
        val capability = GetScheduleCapability(
            config = { config() },
            cached = { listOf(fridayCourse) },
            today = { wednesday },
        )
        val outcome = capability.execute(JsonObject()) as AssistantOutcome.Success
        assertEquals("最近 7 天有 1 节课", outcome.answer.summary)
        assertTrue(outcome.answer.lines.single().contains("3月6日 周五"))
    }

    @Test
    fun respectsWeekIndexesFromSchedulePage() = runBlocking {
        val onlyEvenWeeks = course("大学英语").apply { setWeekIndexes(listOf(2, 4)) }
        val capability = GetScheduleCapability(
            config = { config(week = 3) },
            cached = { listOf(onlyEvenWeeks) },
            today = { wednesday },
        )
        val outcome = capability.execute(days(1)) as AssistantOutcome.Success
        assertEquals("今天没有课", outcome.answer.summary)
    }

    @Test
    fun reportsNonSemester() = runBlocking {
        val capability = GetScheduleCapability(
            config = { config(inSemester = false) },
            cached = { listOf(course("高等数学")) },
            today = { wednesday },
        )
        val outcome = capability.execute(JsonObject()) as AssistantOutcome.Success
        assertEquals("现在不在教学周，最近 7 天没有课程", outcome.answer.summary)
    }

    @Test
    fun promptsWhenCacheMissing() = runBlocking {
        val capability = GetScheduleCapability(
            config = { config() },
            cached = { null },
            today = { wednesday },
        )
        val outcome = capability.execute(JsonObject()) as AssistantOutcome.Success
        assertTrue(outcome.answer.summary.contains("课表还没缓存"))
    }
}
