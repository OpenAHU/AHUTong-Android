package com.ahu.ahutong.data.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** User-confirmed graduate calendar; independent of all undergraduate semester settings. */
object PostgraduateTeachingWeek {
    const val MAX_WEEK = 60

    fun firstMonday(currentWeek: Int, today: LocalDate): LocalDate {
        require(currentWeek in 1..MAX_WEEK)
        return today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks((currentWeek - 1).toLong())
    }

    fun weekOn(firstMonday: LocalDate, today: LocalDate): Int =
        (Math.floorDiv(ChronoUnit.DAYS.between(firstMonday, today), 7) + 1).toInt()

    fun parseStored(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.takeIf { it.dayOfWeek == DayOfWeek.MONDAY }

    fun storageKey(termCode: String): String {
        require(Regex("[A-Za-z0-9_-]{1,40}").matches(termCode))
        return "gmis.$termCode.first_week_monday"
    }
}
