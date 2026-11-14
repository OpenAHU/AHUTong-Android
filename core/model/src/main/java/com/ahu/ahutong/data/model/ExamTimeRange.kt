package com.ahu.ahutong.data.model

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ExamTimeRange(val start: LocalDateTime, val end: LocalDateTime) {
    companion object {
        private val pattern = Regex(
            """(\d{4}-\d{2}-\d{2})[\sT]+(\d{1,2}):(\d{2})\s*[~～—–至-]\s*(\d{1,2}):(\d{2})"""
        )

        fun parse(raw: String?): ExamTimeRange? {
            val match = pattern.matchEntire(raw?.trim().orEmpty()) ?: return null
            val (date, startHour, startMinute, endHour, endMinute) = match.destructured
            return try {
                val day = LocalDate.parse(date)
                val start = day.atTime(LocalTime.of(startHour.toInt(), startMinute.toInt()))
                val end = day.atTime(LocalTime.of(endHour.toInt(), endMinute.toInt()))
                if (end.isAfter(start)) ExamTimeRange(start, end) else null
            } catch (_: DateTimeException) {
                null
            }
        }
    }
}
