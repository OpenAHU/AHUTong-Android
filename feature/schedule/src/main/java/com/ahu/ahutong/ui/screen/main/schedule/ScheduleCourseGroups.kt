package com.ahu.ahutong.ui.screen.main.schedule

import com.ahu.ahutong.data.model.Course

private val ALL_WEEKDAYS = (1..7).toSet()

/** Groups connected time ranges after filtering to the displayed week. */
internal fun weeklyCourseGroups(
    courses: List<Course>,
    week: Int,
    visibleDays: Set<Int> = ALL_WEEKDAYS
): List<List<Course>> =
    overlappingCourseGroups(courses.filter { week in it.weekIndexes }, visibleDays)

/** Groups visually overlapping ranges, including courses taught in different weeks. */
internal fun overlappingCourseGroups(
    courses: List<Course>,
    visibleDays: Set<Int> = ALL_WEEKDAYS
): List<List<Course>> =
    courses.filter { it.weekday in visibleDays }.groupBy { it.weekday }
        .values
        .flatMap { dayCourses ->
            val groups = mutableListOf<MutableList<Course>>()
            var endExclusive = 0
            dayCourses.sortedBy { it.startTime }.forEach { course ->
                if (groups.isEmpty() || course.startTime >= endExclusive) {
                    groups.add(mutableListOf(course))
                    endExclusive = course.startTime + course.length
                } else {
                    groups.last().add(course)
                    endExclusive = maxOf(endExclusive, course.startTime + course.length)
                }
            }
            groups
        }

/** Overview groups may share a slot while teaching in entirely different weeks. */
internal fun hasCourseConflict(courses: List<Course>): Boolean =
    courses.indices.any { index ->
        val course = courses[index]
        courses.drop(index + 1).any { other ->
            course.weekday == other.weekday &&
                course.startTime < other.startTime + other.length &&
                other.startTime < course.startTime + course.length &&
                course.weekIndexes.any { it in other.weekIndexes }
        }
    }
