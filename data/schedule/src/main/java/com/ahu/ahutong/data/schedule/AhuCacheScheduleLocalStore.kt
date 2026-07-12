package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Course
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheScheduleLocalStore @Inject constructor() : ScheduleLocalStore {
    override fun isMockMode(): Boolean = AHUCache.getMockData()

    override fun isLoggedIn(): Boolean = AHUCache.isLogin()

    override fun getSchoolYear(): String? = AHUCache.getSchoolYear()

    override fun getSchoolTerm(): String? = AHUCache.getSchoolTerm()

    override fun saveSchoolYear(schoolYear: String) {
        AHUCache.saveSchoolYear(schoolYear)
    }

    override fun saveSchoolTerm(schoolTerm: String) {
        AHUCache.saveSchoolTerm(schoolTerm)
    }

    override fun isShowAllCourse(): Boolean = AHUCache.isShowAllCourse()

    override fun saveSchoolTermStartTime(schoolYear: String, schoolTerm: String, date: String) {
        AHUCache.saveSchoolTermStartTime(schoolYear, schoolTerm, date)
    }

    override fun getCachedSchedule(term: String): List<Course>? = AHUCache.getSchedule(term)

    override fun saveSchedule(term: String, courses: List<Course>) {
        AHUCache.saveSchedule(term, courses)
    }

    override fun getNextSchedule(): List<Course>? = AHUCache.getNextSchedule()

    override fun saveNextSchedule(courses: List<Course>) {
        AHUCache.saveNextSchedule(courses)
    }
}
