package com.ahu.ahutong.data.attendance

import org.json.JSONArray
import org.json.JSONObject

/** 纯 JSON 解析，便于在 JVM 单测里锁字段契约。 */
object AttendanceJsonParser {

    fun parsePage(raw: String): AttendancePage {
        val root = JSONObject(raw)
        val data = root.optJSONObject("data") ?: root
        val list = data.optJSONArray("list") ?: JSONArray()
        val records = buildList {
            for (i in 0 until list.length()) {
                list.optJSONObject(i)?.let { add(parseRecord(it)) }
            }
        }
        return AttendancePage(
            totalCount = data.optInt("totalCount", records.size),
            records = records
        )
    }

    fun parseRecord(record: JSONObject): AttendanceRecord {
        val subject = record.optJSONObject("subjectBean")
        val water = record.optJSONObject("classWaterBean")
        val room = record.optJSONObject("roomBean")
        val build = record.optJSONObject("buildBean")
        val detail = record.optJSONObject("accountDetailEntity")

        val punchTime = water?.optString("operdate").orEmpty()
        val classDate = punchTime.substringBefore(' ').takeIf { it.length >= 10 }
            ?: detail?.optString("timingdate_startdate").orEmpty()
        val section = water?.optString("jc").orEmpty()

        return AttendanceRecord(
            courseName = subject?.optString("sName").orEmpty(),
            courseCode = subject?.optString("sCode").orEmpty(),
            teacher = record.optString("teachNameList").orEmpty(),
            room = room?.optString("roomnum").orEmpty()
                .ifBlank { build?.optString("name").orEmpty() },
            building = build?.optString("name").orEmpty(),
            section = if (section.isBlank()) "" else "第${section}节",
            classDate = classDate,
            startTime = detail?.optString("lessonstime_start_starttime").orEmpty(),
            endTime = detail?.optString("lessonstime_end_endtime").orEmpty(),
            punchTime = punchTime,
            status = AttendanceStatus.of(water?.optInt("status", 0) ?: 0),
            canAppeal = record.optString("isSupportAppeal") == "1"
        )
    }
}
