package com.ahu.ahutong.data.calendar

import com.ahu.ahutong.data.schedule.ScheduleHoliday
import com.google.gson.JsonParser
import java.time.LocalDate

/** 拒绝空占位文件及格式错误，无法获取有效日历时不标注。 */
internal fun parseHolidayCalendar(json: String, year: Int): Map<LocalDate, ScheduleHoliday> {
    val root = JsonParser.parseString(json).asJsonObject
    require(root.get("year").asInt == year)
    require(root.getAsJsonArray("papers").size() > 0)
    val days = root.getAsJsonArray("days")
    require(days.size() > 0)
    return buildMap {
        days.forEach { element ->
            val day = element.asJsonObject
            val date = LocalDate.parse(day.get("date").asString)
            // 公告允许跨到前一年十二月或次年一月。
            require(date.year == year ||
                (date.year == year - 1 && date.monthValue == 12) ||
                (date.year == year + 1 && date.monthValue == 1))
            val name = day.get("name").asString.trim()
            require(name.isNotBlank() && name.length <= 40)
            val offDay = day.get("isOffDay").asJsonPrimitive
            require(offDay.isBoolean)
            require(date !in this)
            put(date, ScheduleHoliday(name, offDay.asBoolean))
        }
    }
}
