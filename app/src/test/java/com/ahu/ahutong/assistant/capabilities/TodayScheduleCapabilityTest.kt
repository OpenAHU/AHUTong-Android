package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayScheduleCapabilityTest {

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

    @Test
    fun listsTodaysCoursesFromCache() = runBlocking {
        val capability = TodayScheduleCapability(
            config = { config() },
            cached = { listOf(course("高等数学")) },
        )
        val outcome = capability.execute(JsonObject())
        assertTrue(outcome is AssistantOutcome.Success)
        val answer = (outcome as AssistantOutcome.Success).answer
        assertEquals("今天有 1 节课", answer.summary)
        assertTrue(answer.lines.single().contains("第 3-4 节"))
        assertTrue(answer.lines.single().contains("东区3201"))
        assertEquals("schedule", answer.route)
    }

    @Test
    fun respectsWeekIndexesFromSchedulePage() = runBlocking {
        val onlyEvenWeeks = course("大学英语").apply { setWeekIndexes(listOf(2, 4)) }
        val capability = TodayScheduleCapability(
            config = { config(week = 3) },
            cached = { listOf(onlyEvenWeeks) },
        )
        val outcome = capability.execute(JsonObject()) as AssistantOutcome.Success
        assertEquals("今天没有课", outcome.answer.summary)
    }

    @Test
    fun reportsNonSemester() = runBlocking {
        val capability = TodayScheduleCapability(
            config = { config(inSemester = false) },
            cached = { listOf(course("高等数学")) },
        )
        val outcome = capability.execute(JsonObject()) as AssistantOutcome.Success
        assertEquals("现在不在教学周，今天没有课程", outcome.answer.summary)
    }

    @Test
    fun promptsWhenCacheMissing() = runBlocking {
        val capability = TodayScheduleCapability(config = { config() }, cached = { null })
        val outcome = capability.execute(JsonObject()) as AssistantOutcome.Success
        assertTrue(outcome.answer.summary.contains("课表还没缓存"))
    }
}
