package com.ahu.ahutong.data.schedule

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.model.jwxt.CourseTable
import com.ahu.ahutong.data.crawler.model.jwxt.CurrentSemester
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Course
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import org.jsoup.Jsoup

/**
 * Schedule crawler sink (Jwxt HTML/JSON).
 */
@Singleton
class CrawlerScheduleSource @Inject constructor() : ScheduleCrawlerSource {
    private val tag = "CrawlerScheduleSource"
    private val gson = Gson()

    override suspend fun fetchSchedule(): AppResult<List<Course>> {
        return try {
            val semester = getCurrentSemester()
            val courseTable = JwxtApi.API.getCourse(semester.id, semester.id)
            AHUCache.saveSchoolTerm(semester.name)
            AppResult.success(courseTable.toCourseList())
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取课表失败", t)
        }
    }

    override suspend fun fetchNextSchedule(): AppResult<List<Course>> {
        return try {
            val semester = getCurrentSemester()
            val nextCourseTable = JwxtApi.API.getCourse(semester.id + 20, semester.id)
            AppResult.success(nextCourseTable.toCourseList())
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取下学期课表失败", t)
        }
    }

    private suspend fun getCurrentSemester(): CurrentSemester {
        val basicInfo = JwxtApi.API.fetchCourseTableBasicInfo()
        val doc = Jsoup.parse(basicInfo.body()!!.string())

        val element = doc.select("script")
            .map { it.data() }
            .firstOrNull {
                it.contains("var semesters = JSON.parse") && it.contains("var currentSemester")
            } ?: throw IllegalStateException("Cannot find current semester script")

        val currentSemesterPattern = Regex("var\\s+currentSemester\\s*=\\s*(\\{.*?\\});")
        val currentSemester = currentSemesterPattern.find(element)
            ?: throw IllegalStateException("Cannot parse current semester")

        return gson.fromJson(
            currentSemester.groups[1]!!.value.replace("\\\"", "\""),
            CurrentSemester::class.java,
        )
    }

    private fun CourseTable.toCourseList(): List<Course> {
        val courseList = ArrayList<Course>()
        studentTableVms.firstOrNull()?.activities.orEmpty().forEach {
            val sortedWeekIndexes = it.weekIndexes.sorted()
            if (sortedWeekIndexes.isEmpty()) {
                return@forEach
            }

            val course = Course()
            course.name = it.courseName
            course.setStartWeek(sortedWeekIndexes.first().toString())
            course.setLength((it.endUnit - it.startUnit + 1).toString())
            course.setWeekday(it.weekday.toString())
            course.setEndWeek(sortedWeekIndexes.last().toString())
            course.setStartTime(it.startUnit.toString())
            course.location = it.room ?: "未知"
            course.teacher = it.teacherNames.joinToString(", ")
            course.weekIndexes = sortedWeekIndexes
            course.courseId = it.lessonId.toString()

            Log.e(tag, "getSchedule: $course")
            courseList.add(course)
        }
        return courseList
    }
}
