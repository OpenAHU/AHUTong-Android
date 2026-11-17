package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.data.model.Course
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroTextTest {
    private fun course(name: String, clock: String) = Course().apply {
        this.name = name
        clockRange = clock
        location = "博学北楼"
    }

    private fun exam(name: String, start: Int, end: Int, location: String = "博学北楼A101") =
        HomeExam(name = name, location = location, startMinutes = start, endMinutes = end)

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 统一以 2026-11-17 为“今天”，避免测试依赖运行时的真实日期。 */
    private fun at(hour: Int, minute: Int, dayOffset: Long = 0): Long =
        LocalDateTime.of(2026, 11, 17, hour, minute)
            .plusDays(dayOffset)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun assignment(
        title: String = "第三章作业",
        courseName: String = "高等数学",
        endTs: Long
    ) = HomeAssignment(title = title, courseName = courseName, endTs = endTs)

    @Test fun ongoingExamTakesPriorityOverOngoingCourse() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "08:00-09:35")),
            todayExams = listOf(exam("高等数学", 8 * 60 + 30, 10 * 60)),
            currentMinutes = 8 * 60 + 45,
            isInSemester = true
        )
        assertEquals("正在考试 · 高等数学", hero.headline)
        assertEquals("距考试结束还有 1小时15分钟", hero.subtitle)
    }

    @Test fun earlierExamWinsOverTheNextCourse() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "08:00-09:35")),
            todayExams = listOf(exam("高等数学", 7 * 60 + 30, 9 * 60)),
            currentMinutes = 7 * 60,
            isInSemester = true
        )
        assertEquals("下一场考试是 高等数学", hero.headline)
        assertEquals("还有 30分钟，在 博学北楼A101", hero.subtitle)
    }

    @Test fun earlierCourseWinsOverTheNextExam() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "08:00-09:35")),
            todayExams = listOf(exam("高等数学", 10 * 60, 11 * 60 + 30)),
            currentMinutes = 7 * 60,
            isInSemester = true
        )
        assertEquals("下节课是 大学物理", hero.headline)
        assertEquals(HomeHeroTarget.SCHEDULE, hero.target)
    }

    @Test fun nextExamShowsWhenAllCoursesAreDone() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "08:00-09:35")),
            todayExams = listOf(exam("高等数学", 14 * 60, 15 * 60)),
            currentMinutes = 12 * 60,
            isInSemester = true
        )
        assertEquals("下一场考试是 高等数学", hero.headline)
        assertEquals("还有 2小时整，在 博学北楼A101", hero.subtitle)
    }

    @Test fun examTimeEdgesDoNotLeakIntoTheNextSlot() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = listOf(exam("高等数学", 8 * 60, 9 * 60)),
            currentMinutes = 9 * 60,
            isInSemester = true
        )
        assertEquals("今日空闲", hero.headline)
    }

    @Test fun blankExamLocationIsOmitted() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = listOf(exam("高等数学", 8 * 60, 9 * 60, location = " ")),
            currentMinutes = 7 * 60,
            isInSemester = true
        )
        assertEquals("下一场考试是 高等数学", hero.headline)
        assertEquals("还有 1小时整", hero.subtitle)
    }

    @Test fun holidayStillReportsAnOngoingExam() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = listOf(exam("高等数学", 8 * 60, 9 * 60)),
            currentMinutes = 8 * 60 + 20,
            isInSemester = false
        )
        assertEquals("正在考试 · 高等数学", hero.headline)
    }

    @Test fun withoutExamsTheCourseCopyIsUnchanged() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "08:00-09:35")),
            todayExams = emptyList(),
            currentMinutes = 8 * 60 + 10,
            isInSemester = true
        )
        assertEquals("正在上课 · 大学物理", hero.headline)
        assertEquals("距下课还有 1小时25分钟", hero.subtitle)
    }

    @Test fun assignmentDueSoonerThanTheNextCourseWins() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "12:00-13:35")),
            todayExams = emptyList(),
            currentMinutes = 10 * 60,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(11, 0))),
            nowMillis = at(10, 0)
        )
        assertEquals("最近的作业还有\n1小时整截止", hero.headline)
        assertEquals("第三章作业", hero.subtitle)
        assertEquals(HomeHeroTarget.XUEXIAOTONG_SCHEDULE, hero.target)
    }

    @Test fun nextCourseSoonerThanTheAssignmentWins() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "10:30-12:00")),
            todayExams = emptyList(),
            currentMinutes = 10 * 60,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(14, 0))),
            nowMillis = at(10, 0)
        )
        assertEquals("下节课是 大学物理", hero.headline)
        assertEquals("还有 30分钟，在 博学北楼", hero.subtitle)
    }

    @Test fun ongoingCourseBeatsAnUpcomingAssignment() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "08:00-09:35")),
            todayExams = emptyList(),
            currentMinutes = 8 * 60 + 30,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(9, 0))),
            nowMillis = at(8, 30)
        )
        assertEquals("正在上课 · 大学物理", hero.headline)
    }

    @Test fun ongoingExamBeatsAnUpcomingAssignment() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = listOf(exam("高等数学", 8 * 60, 10 * 60)),
            currentMinutes = 8 * 60 + 30,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(9, 0))),
            nowMillis = at(8, 30)
        )
        assertEquals("正在考试 · 高等数学", hero.headline)
    }

    @Test fun overdueAssignmentsNeverShowUp() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = emptyList(),
            currentMinutes = 10 * 60,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(9, 0))),
            nowMillis = at(10, 0)
        )
        assertEquals("今日空闲", hero.headline)
    }

    @Test fun assignmentWinsWhenTheDeadlineTiesTheCourseStart() {
        val hero = resolveHomeHeroText(
            todayCourses = listOf(course("大学物理", "11:00-12:00")),
            todayExams = emptyList(),
            currentMinutes = 10 * 60,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(11, 0))),
            nowMillis = at(10, 0)
        )
        assertEquals("最近的作业还有\n1小时整截止", hero.headline)
    }

    @Test fun nextExamSoonerThanTheAssignmentWins() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = listOf(exam("高等数学", 10 * 60 + 30, 12 * 60)),
            currentMinutes = 10 * 60,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(11, 0))),
            nowMillis = at(10, 0)
        )
        assertEquals("下一场考试是 高等数学", hero.headline)
    }

    @Test fun tomorrowAssignmentStillShowsWhenTodayIsOver() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = emptyList(),
            currentMinutes = 20 * 60,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(8, 0, dayOffset = 1))),
            nowMillis = at(20, 0)
        )
        assertEquals("最近的作业还有\n12小时整截止", hero.headline)
        assertEquals("第三章作业", hero.subtitle)
    }

    @Test fun multiDayDeadlineIsExpressedInDays() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = emptyList(),
            currentMinutes = 10 * 60,
            isInSemester = true,
            assignments = listOf(assignment(endTs = at(10, 0, dayOffset = 3))),
            nowMillis = at(10, 0)
        )
        assertEquals("最近的作业还有\n3天截止", hero.headline)
        assertEquals("第三章作业", hero.subtitle)
    }

    @Test fun holidayStillReportsAnAssignment() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = emptyList(),
            currentMinutes = 10 * 60,
            isInSemester = false,
            assignments = listOf(assignment(endTs = at(11, 0))),
            nowMillis = at(10, 0)
        )
        assertEquals("最近的作业还有\n1小时整截止", hero.headline)
    }

    @Test fun blankTitleFallsBackToTheCourseName() {
        val hero = resolveHomeHeroText(
            todayCourses = emptyList(),
            todayExams = emptyList(),
            currentMinutes = 10 * 60,
            isInSemester = true,
            assignments = listOf(assignment(title = "", endTs = at(11, 0))),
            nowMillis = at(10, 0)
        )
        assertEquals("高等数学", hero.subtitle)
    }
}
