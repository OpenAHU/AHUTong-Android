package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Shared AHU course period → clock-time mapping used by schedule UI, widgets, and reminders.
 */
object CourseTimetable {
    val timetable by lazy {
        mapOf(
            1 to "08:00-08:45",
            2 to "08:50-09:35",
            3 to "09:50-10:35",
            4 to "10:40-11:25",
            5 to "11:30-12:15",
            6 to "14:00-14:45",
            7 to "14:50-15:35",
            8 to "15:50-16:35",
            9 to "16:40-17:25",
            10 to "17:30-18:15",
            11 to "19:00-19:45",
            12 to "19:50-20:35",
            13 to "20:40-21:25",
        )
    }

    fun getCourseTimeRangeInMinutes(course: Course): IntRange {
        return getTimeRangeInMinutes(
            from = timetable.getValue(course.startTime),
            to = timetable.getValue(course.startTime + course.length - 1),
        )
    }

    /**
     * @param from "HH:mm-HH:mm"
     * @param to "HH:mm-HH:mm"
     */
    private fun getTimeRangeInMinutes(
        from: String,
        to: String = from,
    ): IntRange {
        val format = SimpleDateFormat("HH:mm", Locale.CHINA)
        val start = format.parse(from.take(5)).let {
            val calendar = Calendar.getInstance(Locale.CHINA)
            calendar.time = it!!
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        }
        val end = format.parse(to.takeLast(5)).let {
            val calendar = Calendar.getInstance(Locale.CHINA)
            calendar.time = it!!
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        }
        return start..end
    }
}
