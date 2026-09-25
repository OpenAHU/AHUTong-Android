package com.ahu.ahutong.data.schedule.gmis

data class GmisTerm(val code: String, val name: String, val selected: Boolean)
data class GmisSection(val number: Int, val group: String, val clock: String?)
data class GmisCourse(
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val startSection: Int?,
    val endSection: Int?,
    val weeks: Set<Int>?,
    val weekLabel: String,
    val clock: String?,
    val details: String
)

data class GmisTimetable(val courses: List<GmisCourse>, val sections: List<GmisSection>) {
    val maxWeek: Int get() = courses.flatMap { it.weeks.orEmpty() }.maxOrNull() ?: 0
    fun forWeek(week: Int?): List<GmisCourse> =
        courses.filter { week == null || it.weeks == null || week in it.weeks }
}
