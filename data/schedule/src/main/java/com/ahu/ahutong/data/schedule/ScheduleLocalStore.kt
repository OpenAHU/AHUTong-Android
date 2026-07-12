package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course

/**
 * Local schedule cache / preference access.
 * App module binds an implementation wrapping [com.ahu.ahutong.data.dao.AHUCache].
 */
interface ScheduleLocalStore {
    fun isMockMode(): Boolean

    fun isLoggedIn(): Boolean

    fun getSchoolYear(): String?

    fun getSchoolTerm(): String?

    fun saveSchoolYear(schoolYear: String)

    fun saveSchoolTerm(schoolTerm: String)

    fun isShowAllCourse(): Boolean

    fun saveSchoolTermStartTime(schoolYear: String, schoolTerm: String, date: String)

    fun getCachedSchedule(term: String): List<Course>?

    fun saveSchedule(term: String, courses: List<Course>)

    fun getNextSchedule(): List<Course>?

    fun saveNextSchedule(courses: List<Course>)
}

/** True when the user may load schedule data (logged in or mock). */
fun ScheduleLocalStore.canLoadSchedule(): Boolean = isLoggedIn() || isMockMode()

