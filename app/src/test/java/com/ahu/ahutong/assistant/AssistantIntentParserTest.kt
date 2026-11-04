package com.ahu.ahutong.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantIntentParserTest {

    @Test
    fun routesFreeClassroomQuestions() {
        assertEquals("get_free_rooms", AssistantIntentParser.parse("哪有空教室").capabilityId)
        assertEquals("get_free_rooms", AssistantIntentParser.parse("帮我找空闲教室").capabilityId)
        assertEquals("get_free_rooms", AssistantIntentParser.parse("现在有没课的教室吗").capabilityId)
    }

    @Test
    fun extractsFreeClassroomArguments() {
        val intent = AssistantIntentParser.parse("明天龙河校区第 3-5 节的空教室")
        assertEquals("get_free_rooms", intent.capabilityId)
        assertEquals(2, intent.arguments.get("campusId").asInt)
        assertEquals(listOf("3", "4", "5"), intent.arguments.getAsJsonArray("units").map { it.asString })
    }

    @Test
    fun routesBalanceQuestions() {
        assertEquals("get_balance", AssistantIntentParser.parse("校园卡余额还有多少").capabilityId)
        assertEquals("get_balance", AssistantIntentParser.parse("饭卡里还有钱吗").capabilityId)
    }

    @Test
    fun routesScheduleQuestions() {
        assertEquals("get_schedule", AssistantIntentParser.parse("今天有什么课").capabilityId)
        assertEquals("get_schedule", AssistantIntentParser.parse("下节课在哪").capabilityId)
    }

    @Test
    fun extractsScheduleDays() {
        val intent = AssistantIntentParser.parse("未来三天有什么课")
        assertEquals("get_schedule", intent.capabilityId)
        assertEquals(3, intent.arguments.get("days").asInt)
    }

    @Test
    fun routesAcademicQueries() {
        assertEquals("get_grades", AssistantIntentParser.parse("上学期成绩怎么样").capabilityId)
        assertEquals("get_exams", AssistantIntentParser.parse("考试安排出来了吗").capabilityId)
        assertEquals("get_gpa_rank", AssistantIntentParser.parse("我在专业排第几").capabilityId)
        assertEquals("get_attendance", AssistantIntentParser.parse("最近有缺勤吗").capabilityId)
        assertEquals("get_program_completion", AssistantIntentParser.parse("毕业学分还差多少").capabilityId)
        assertEquals("get_evaluation_tasks", AssistantIntentParser.parse("评教还有哪些老师").capabilityId)
    }

    @Test
    fun extractsGradeTerm() {
        val intent = AssistantIntentParser.parse("上学期成绩怎么样")
        assertEquals("get_grades", intent.capabilityId)
        assertEquals("上学期", intent.arguments.get("term").asString)
    }

    @Test
    fun routesCampusDataQueries() {
        assertEquals("get_electricity", AssistantIntentParser.parse("宿舍电费还有多少").capabilityId)
        assertEquals("get_bills", AssistantIntentParser.parse("这个月消费记录").capabilityId)
        assertEquals("get_notices", AssistantIntentParser.parse("最近有什么通知").capabilityId)
        assertEquals("get_canteen_ranking", AssistantIntentParser.parse("必吃榜第一是谁").capabilityId)
        assertEquals("get_lost_found", AssistantIntentParser.parse("有人捡到校园卡吗").capabilityId)
        assertEquals("get_mail", AssistantIntentParser.parse("看看收件箱").capabilityId)
        assertEquals("get_assignments", AssistantIntentParser.parse("最近有什么作业").capabilityId)
        assertEquals("get_course_tasks", AssistantIntentParser.parse("章节任务点进度").capabilityId)
        assertEquals("get_weather", AssistantIntentParser.parse("明天天气怎么样").capabilityId)
    }

    @Test
    fun extractsNoticeQuery() {
        val intent = AssistantIntentParser.parse("关于奖学金的通知")
        assertEquals("get_notices", intent.capabilityId)
        assertEquals("奖学金", intent.arguments.get("query").asString)
    }

    @Test
    fun extractsPhoneBookQuery() {
        val intent = AssistantIntentParser.parse("教务处的电话是多少")
        assertEquals("get_phone_book", intent.capabilityId)
        assertEquals("教务处", intent.arguments.get("query").asString)
    }

    @Test
    fun routesNavigationActionsWithoutExecutingThem() {
        val recharge = AssistantIntentParser.parse("帮我充值50元")
        assertEquals("recharge_card", recharge.capabilityId)
        assertEquals("50", recharge.arguments.get("amount").asString)

        assertEquals("recharge_electricity", AssistantIntentParser.parse("交电费").capabilityId)
        assertEquals("send_mail", AssistantIntentParser.parse("发邮件给老师").capabilityId)
        assertEquals("publish_lost_found", AssistantIntentParser.parse("发布失物招领").capabilityId)
        assertEquals("submit_evaluation", AssistantIntentParser.parse("帮我提交评教").capabilityId)
    }

    @Test
    fun unknownForUnrelatedText() {
        assertEquals(null, AssistantIntentParser.parse("").capabilityId)
        assertEquals(null, AssistantIntentParser.parse("帮我写作业").capabilityId)
        assertEquals(null, AssistantIntentParser.parse("讲个笑话").capabilityId)
    }

    @Test
    fun freshDataFlagIsExtracted() {
        val intent = AssistantIntentParser.parse("刷新一下学习通的作业")
        assertEquals("get_assignments", intent.capabilityId)
        assertTrue(intent.arguments.get("refresh").asBoolean)
    }
}
