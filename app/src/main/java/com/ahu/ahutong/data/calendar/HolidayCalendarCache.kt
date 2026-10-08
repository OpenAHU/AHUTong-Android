package com.ahu.ahutong.data.calendar

import com.ahu.ahutong.data.schedule.ScheduleHoliday
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

internal interface HolidayCalendarCache {
    fun read(year: Int): Map<LocalDate, ScheduleHoliday>?
    fun write(year: Int, days: Map<LocalDate, ScheduleHoliday>)
}

/** 按年保存经过校验的日历；原子替换避免中途退出破坏上一份缓存。 */
internal class HolidayCalendarDiskCache(private val directory: File) : HolidayCalendarCache {
    override fun read(year: Int): Map<LocalDate, ScheduleHoliday>? = runCatching {
        val file = File(directory, "$year.json")
        require(file.length() in 1..MAX_BYTES.toLong())
        val root = JsonParser.parseString(file.readText()).asJsonObject
        require(root.get("formatVersion").asInt == 1 && root.get("year").asInt == year)
        parseHolidayCalendarDays(root.getAsJsonArray("days"), year)
    }.getOrNull()

    override fun write(year: Int, days: Map<LocalDate, ScheduleHoliday>) {
        val entries = JsonArray().apply {
            days.toSortedMap().forEach { (date, holiday) ->
                add(JsonObject().apply {
                    addProperty("date", date.toString())
                    addProperty("name", holiday.name)
                    addProperty("isOffDay", holiday.isOffDay)
                })
            }
        }
        parseHolidayCalendarDays(entries, year)
        val bytes = JsonObject().apply {
            addProperty("formatVersion", 1)
            addProperty("year", year)
            add("days", entries)
        }.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        check(directory.isDirectory || directory.mkdirs())
        val pending = File(directory, "$year.json.tmp")
        try {
            pending.writeBytes(bytes)
            try {
                Files.move(pending.toPath(), File(directory, "$year.json").toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending.toPath(), File(directory, "$year.json").toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            pending.delete()
        }
    }

    private companion object {
        const val MAX_BYTES = 128 * 1024
    }
}
