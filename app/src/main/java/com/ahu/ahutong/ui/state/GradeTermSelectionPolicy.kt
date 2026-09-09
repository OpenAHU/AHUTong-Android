package com.ahu.ahutong.ui.state

import java.time.LocalDate

internal data class GradeTermKey(
    val schoolYear: String,
    val schoolTerm: String
)

internal object GradeTermSelectionPolicy {
    fun currentForDate(date: LocalDate): GradeTermKey {
        val startYear = if (date.monthValue >= 9) date.year else date.year - 1
        val term = if (date.monthValue >= 9 || date.monthValue == 1) "1" else "2"
        return GradeTermKey("$startYear-${startYear + 1}", term)
    }

    fun choose(
        current: GradeTermKey,
        cached: GradeTermKey?,
        previous: GradeTermKey?,
        preservePrevious: Boolean,
        available: List<GradeTermKey>
    ): GradeTermKey {
        if (preservePrevious && previous != null && previous in available) return previous
        if (cached != null && cached.schoolYear == current.schoolYear && cached in available) {
            return cached
        }
        if (current in available) return current
        return available.maxWithOrNull(
            compareBy<GradeTermKey> {
                it.schoolYear.substringBefore('-').toIntOrNull() ?: Int.MIN_VALUE
            }.thenBy {
                it.schoolTerm.toIntOrNull() ?: Int.MIN_VALUE
            }
        ) ?: cached?.takeIf { it.schoolYear == current.schoolYear } ?: current
    }
}
