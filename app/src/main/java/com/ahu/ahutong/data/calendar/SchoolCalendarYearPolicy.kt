package com.ahu.ahutong.data.calendar

object SchoolCalendarYearPolicy {
    private val academicYearPattern = Regex("^(\\d{4})-(\\d{4})$")

    fun normalize(years: List<String>): List<String> = years
        .mapNotNull { value ->
            val match = academicYearPattern.matchEntire(value) ?: return@mapNotNull null
            val startYear = match.groupValues[1].toInt()
            val endYear = match.groupValues[2].toInt()
            value.takeIf { endYear == startYear + 1 }
        }
        .distinct()
        .sortedByDescending { it.substringBefore('-').toInt() }

    fun select(
        years: List<String>,
        latestYear: String?,
        currentSelection: String?
    ): String? = when {
        currentSelection in years -> currentSelection
        latestYear in years -> latestYear
        else -> years.firstOrNull()
    }

    fun cacheFileName(year: String): String = "school_calendar_$year.jpg"

    fun displayName(year: String): String = "${year.replace('-', '—')} 学年"
}
