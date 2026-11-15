package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.data.model.Course
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
}
