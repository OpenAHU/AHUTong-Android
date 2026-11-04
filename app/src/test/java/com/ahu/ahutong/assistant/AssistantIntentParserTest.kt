package com.ahu.ahutong.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantIntentParserTest {

    @Test
    fun routesFreeClassroomQuestions() {
        assertEquals(AssistantIntent.FreeClassroom, AssistantIntentParser.parse("哪有空教室"))
        assertEquals(AssistantIntent.FreeClassroom, AssistantIntentParser.parse("帮我找空闲教室"))
        assertEquals(AssistantIntent.FreeClassroom, AssistantIntentParser.parse("现在有没课的教室吗"))
    }

    @Test
    fun routesBalanceQuestions() {
        assertEquals(AssistantIntent.CardBalance, AssistantIntentParser.parse("校园卡余额还有多少"))
        assertEquals(AssistantIntent.CardBalance, AssistantIntentParser.parse("饭卡里还有钱吗"))
    }

    @Test
    fun routesScheduleQuestions() {
        assertEquals(AssistantIntent.TodaySchedule, AssistantIntentParser.parse("今天有什么课"))
        assertEquals(AssistantIntent.TodaySchedule, AssistantIntentParser.parse("下节课在哪"))
    }

    @Test
    fun unknownForUnrelatedText() {
        assertEquals(AssistantIntent.Unknown, AssistantIntentParser.parse(""))
        assertEquals(AssistantIntent.Unknown, AssistantIntentParser.parse("帮我写作业"))
    }
}
