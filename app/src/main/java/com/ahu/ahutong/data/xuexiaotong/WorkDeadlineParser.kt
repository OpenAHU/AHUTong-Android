package com.ahu.ahutong.data.xuexiaotong

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId

internal object WorkDeadlineParser {
    private val timestamp = Regex("""^(?:(\d{4})-)?(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?$""")

    private data class Endpoint(val year: Int?, val day: MonthDay, val time: LocalTime) {
        fun atYear(year: Int, zone: ZoneId): Long = LocalDate.of(year, day.monthValue, day.dayOfMonth)
            .atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    fun parseRange(
        startText: String,
        endText: String,
        referenceMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long>? {
        val start = parseEndpoint(startText) ?: return null
        val end = parseEndpoint(endText) ?: return null
        val crossesYear = end.day < start.day
        val referenceYear = Instant.ofEpochMilli(referenceMillis).atZone(zone).year
        val startYears = when {
            start.year != null -> listOf(start.year)
            end.year != null -> listOf(end.year - if (crossesYear) 1 else 0)
            // Yearless dates near New Year may describe December or an upcoming January.
            // A cached valid start is supplied as the reference to retain known historical years.
            else -> listOf(referenceYear - 1, referenceYear, referenceYear + 1)
        }
        return startYears.mapNotNull { year ->
            runCatching {
                val startMillis = start.atYear(year, zone)
                val endMillis = end.atYear(end.year ?: (year + if (crossesYear) 1 else 0), zone)
                (startMillis to endMillis).takeIf { endMillis >= startMillis }
            }.getOrNull()
        }.minByOrNull { (startMillis, endMillis) ->
            when {
                referenceMillis < startMillis -> startMillis - referenceMillis
                referenceMillis > endMillis -> referenceMillis - endMillis
                else -> 0L
            }
        }
    }

    private fun parseEndpoint(value: String): Endpoint? {
        val parts = timestamp.matchEntire(value.trim())?.groupValues ?: return null
        return runCatching {
            Endpoint(
                year = parts[1].takeIf(String::isNotEmpty)?.toInt(),
                day = MonthDay.of(parts[2].toInt(), parts[3].toInt()),
                time = LocalTime.of(parts[4].toInt(), parts[5].toInt(), parts[6].toIntOrNull() ?: 0)
            )
        }.getOrNull()
    }
}
