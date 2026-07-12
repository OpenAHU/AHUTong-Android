package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course

/**
 * Local schedule cache / preference access.
 * App module binds an implementation wrapping [com.ahu.ahutong.data.dao.AHUCache].
 */
interface ScheduleLocalStore {
    fun isMockMode(): Boolean

    fun getSchoolTerm(): String?

    fun getCachedSchedule(term: String): List<Course>?

    fun saveSchedule(term: String, courses: List<Course>)

    fun getNextSchedule(): List<Course>?

    fun saveNextSchedule(courses: List<Course>)
}
