package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course

/** GMIS provides exact week sets, including non-contiguous and odd/even schedules. */
object GraduateTodayCoursePolicy {
    fun filter(courses: List<Course>, teachingWeek: Int, weekday: Int): List<Course> =
        courses.filter {
            it.weekday == weekday && teachingWeek in it.weekIndexes.orEmpty()
        }.sortedBy { it.startTime }
}
