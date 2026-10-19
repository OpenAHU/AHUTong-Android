package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.data.crawler.model.jwxt.GradeResponse
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.time.LocalDate

internal data class ScheduleSemesterCatalog(
    val current: ScheduleSemester,
    val semesters: List<ScheduleSemester>
)

/** 只接受页面/成绩返回的正整数 ID，忽略无 ID 的名称和占位选项。 */
internal object ScheduleSemesterParser {
    fun parse(html: String): ScheduleSemesterCatalog {
        val document = Jsoup.parse(html)
        val scripts = document.select("script").map { it.data() }
        val current = scripts.asSequence()
            .mapNotNull(::extractCurrentSemesterJson)
            .mapNotNull(::parseSemesterJson)
            .firstOrNull()
            ?.copy(isCurrent = true)
            ?: throw IllegalStateException("无法识别教务当前学期")
        val semesters = linkedMapOf<Int, ScheduleSemester>()

        document.select("select").filter { select ->
            listOf(select.id(), select.attr("name"), select.attr("data-name"))
                .any { it.contains("semester", ignoreCase = true) }
        }.flatMap { it.select("option") }.forEach { option ->
            val id = option.attr("value").toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            val name = option.text().trim().ifBlank { "学期 $id" }
            semesters[id] = ScheduleSemester(
                id, name,
                startDate = normalizeDate(option.attr("data-start-date")),
                isCurrent = id == current.id
            )
        }
        scripts.forEach { script ->
            listOf("semesters", "semesterList").forEach directory@ { variable ->
                val json = extractAssignedSemesterJson(script, variable) ?: return@directory
                val element = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return@directory
                val entries = when {
                    element.isJsonArray -> element.asJsonArray.toList()
                    element.isJsonObject -> element.asJsonObject.entrySet()
                        .flatMap { (_, value) -> if (value.isJsonArray) value.asJsonArray.toList() else emptyList() }
                    else -> emptyList()
                }
                entries.mapNotNull(::toSemester).forEach { semester ->
                    // 脚本可能携带选单没有的开学日期，优先保存完整条目。
                    val existing = semesters[semester.id]
                    semesters[semester.id] = semester.copy(startDate = semester.startDate ?: existing?.startDate)
                }
            }
        }
        val currentWithDate = current.copy(startDate = current.startDate ?: semesters[current.id]?.startDate)
        semesters[current.id] = currentWithDate
        return ScheduleSemesterCatalog(currentWithDate, sort(semesters.values.toList()))
    }

    fun fromGrades(response: GradeResponse): List<ScheduleSemester> =
        response.semesterId2studentGrades.orEmpty().flatMap { (key, grades) ->
            val entries = grades.mapNotNull { grade ->
                val id = grade.semesterId?.takeIf { it > 0 } ?: return@mapNotNull null
                ScheduleSemester(id, grade.semesterName?.takeIf { it.isNotBlank() } ?: "学期 $id")
            }.toMutableList()
            key.toIntOrNull()?.takeIf { it > 0 }?.let { id ->
                if (entries.none { it.id == id }) {
                    entries += ScheduleSemester(id, "学期 $id")
                }
            }
            entries
        }.distinctBy { it.id }

    fun sort(semesters: List<ScheduleSemester>): List<ScheduleSemester> =
        semesters.sortedWith(compareByDescending<ScheduleSemester> { it.name }.thenByDescending { it.id })

    private fun parseSemesterJson(json: String): ScheduleSemester? =
        runCatching { toSemester(JsonParser.parseString(json)) }.getOrNull()

    private fun toSemester(element: JsonElement): ScheduleSemester? {
        if (!element.isJsonObject) return null
        val value = element.asJsonObject
        val id = value.string("id")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val name = sequenceOf("nameZh", "name", "nameEn")
            .mapNotNull { value.string(it) }.firstOrNull { it.isNotBlank() } ?: "学期 $id"
        val start = value.get("startDate")?.let { date ->
            when {
                date.isJsonPrimitive -> normalizeDate(date.asString)
                date.isJsonObject -> {
                    val parts = date.asJsonObject
                    runCatching {
                        LocalDate.of(
                            parts.string("year")!!.toInt(),
                            parts.string("monthOfYear")!!.toInt(),
                            parts.string("dayOfMonth")!!.toInt()
                        ).toString()
                    }.getOrNull()
                }
                else -> null
            }
        }
        return ScheduleSemester(id, name, startDate = start)
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun normalizeDate(value: String): String? =
        runCatching { LocalDate.parse(value.take(10)).toString() }.getOrNull()
}
