package com.ahu.ahutong.data.exam

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.model.Exam
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import org.jsoup.Jsoup

/**
 * Exam crawler sink (Jwxt exam-arrange HTML).
 */
@Singleton
class CrawlerExamSource @Inject constructor() : ExamCrawlerSource {
    override suspend fun fetchExams(studentId: String, studentName: String): AppResult<List<Exam>> {
        return try {
            val res = JwxtApi.API.fetchExamArrangePage()
            if (!res.isSuccessful || res.body() == null) {
                return AppResult.error("请求失败", code = res.code())
            }
            val html = res.body()!!.string()

            val tableExams = parseExamTableHtml(html)
            if (tableExams.isNotEmpty()) {
                return AppResult.success(tableExams)
            }

            // Fallback: old format with studentExamInfoVms JS variable
            val regex = Regex("(?s)studentExamInfoVms\\s*=\\s*(\\[.*?]);")
            val match = regex.find(html)
                ?: return AppResult.success(emptyList())

            val jsonStr = match.groupValues[1]
            val fixedJson = jsonStr.replace("'", "\"")
            val jsonArray = JsonParser.parseString(fixedJson).asJsonArray
            val list = mutableListOf<Exam>()
            jsonArray.forEach { elem ->
                val obj = elem.asJsonObject
                val courseObj = obj.getAsJsonObject("course")
                val examTypeObj = obj.getAsJsonObject("examType")
                val courseName = courseObj?.get("nameZh")?.asString ?: ""
                val examTypeName = examTypeObj?.get("nameZh")?.asString ?: ""
                val courseDisplay =
                    if (examTypeName.isNotEmpty()) "$courseName($examTypeName)" else courseName
                val time = obj.get("examTime")?.asString ?: ""
                val seatVal = obj.get("seatNo")
                val seatNum = when {
                    seatVal == null || seatVal.isJsonNull -> ""
                    seatVal.isJsonPrimitive && seatVal.asJsonPrimitive.isNumber ->
                        seatVal.asNumber.toString()
                    else -> seatVal.asString
                }
                val campus =
                    obj.getAsJsonObject("requiredCampus")?.get("nameZh")?.asString ?: ""
                val room = obj.get("room")?.asString ?: ""
                val location =
                    if (campus.isNotEmpty() && room.isNotEmpty()) "$campus-$room" else campus + room
                val finished = obj.get("finished")?.asBoolean ?: false
                list.add(
                    Exam().apply {
                        setCourse(courseDisplay)
                        setTime(time)
                        setSeatNum(seatNum)
                        setLocation(location)
                        setFinished(finished)
                    },
                )
            }
            AppResult.success(list)
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取考试信息失败", t)
        }
    }

    /**
     * Parse exam info from the new server-rendered HTML table format.
     */
    private fun parseExamTableHtml(html: String): List<Exam> {
        val seatMap = mutableMapOf<String, String>()
        val seatListRegex = Regex("(?s)var\\s+studentExamList\\s*=\\s*(\\[.+?\\]);")
        seatListRegex.find(html)?.let { match ->
            val jsonStr = match.groupValues[1].replace("'", "\"")
            try {
                val arr = JsonParser.parseString(jsonStr).asJsonArray
                arr.forEach {
                    val obj = it.asJsonObject
                    val id = obj.get("id")?.asString ?: obj.get("id")?.asLong?.toString() ?: ""
                    val seat =
                        obj.get("seatNo")?.asString ?: obj.get("seatNo")?.asLong?.toString() ?: ""
                    if (id.isNotEmpty()) seatMap[id] = seat
                }
            } catch (_: Exception) {
            }
        }

        val doc = Jsoup.parse(html)
        val rows = doc.select("tr[data-finished]")
        if (rows.isEmpty()) return emptyList()

        return rows.map { row ->
            val finished = row.attr("data-finished") == "true"
            val time = row.select("div.time").first()?.text()?.trim() ?: ""
            val course = row.select("span[style*=font-weight]").firstOrNull { el ->
                el.attr("style").contains("bold")
            }?.text()?.trim() ?: ""
            val examType = row.select("span.tag-span").first()?.text()?.trim() ?: ""
            val seatId = row.select("span[id^=seat-]").first()?.id()?.removePrefix("seat-") ?: ""
            val seatNum = seatMap[seatId] ?: ""
            val firstTd = row.select("td").first()
            val locationSpans = firstTd?.select("span")?.filter {
                !it.id().startsWith("seat-") && it.text().trim().isNotEmpty()
            } ?: emptyList()
            val location = locationSpans.joinToString("-") { it.text().trim() }
            val courseDisplay = if (examType.isNotEmpty()) "$course($examType)" else course

            Exam().apply {
                setCourse(courseDisplay)
                setTime(time)
                setSeatNum(seatNum)
                setLocation(location)
                setFinished(finished)
            }
        }
    }
}
