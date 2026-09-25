package com.ahu.ahutong.data.crawler.gmis

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.jsoup.Jsoup

data class GmisTerm(val code: String, val name: String, val selected: Boolean)
data class GmisSection(val number: Int, val group: String, val clock: String?)
data class GmisCourse(
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val startSection: Int?,
    val endSection: Int?,
    val weeks: Set<Int>?,
    val weekLabel: String,
    val clock: String?,
    val details: String
)
data class GmisTimetable(val courses: List<GmisCourse>, val sections: List<GmisSection>) {
    val maxWeek: Int get() = courses.flatMap { it.weeks.orEmpty() }.maxOrNull() ?: 0
    fun forWeek(week: Int?): List<GmisCourse> =
        courses.filter { week == null || it.weeks == null || week in it.weeks }
}

internal object GmisTimetableParser {
    private val weekSpan = Regex("(\\d{1,2})\\s*[-—–~～至]\\s*(\\d{1,2})")
    private val clock = Regex("(\\d{1,2}:\\d{2})\\s*[-—–~～]\\s*(\\d{1,2}:\\d{2})")
    private val cell = Regex("^(.*?)\\[([^]\\n]*周[^]\\n]*)]\\s*(.*)$")
    private val room = Regex("\\[([^]]*)]\\s*$")
    private val breaks = Regex("(?i)<br\\s*/?>")

    fun terms(json: JsonElement): List<GmisTerm> {
        if (!json.isJsonArray) throw GmisProtocolException("研究生教务学期列表格式不正确")
        val terms = json.asJsonArray.map { entry ->
            if (!entry.isJsonObject) throw GmisProtocolException("研究生教务学期数据不完整")
            val obj = entry.asJsonObject
            val code = obj.text("termcode")
            val name = obj.text("termname")
            if (!Regex("[A-Za-z0-9_-]{1,40}").matches(code) || name.isBlank()) {
                throw GmisProtocolException("研究生教务学期数据不完整")
            }
            GmisTerm(code, name, obj.text("selected").lowercase() in setOf("true", "1"))
        }
        if (terms.isEmpty() || terms.map { it.code }.distinct().size != terms.size ||
            terms.count { it.selected } > 1
        ) throw GmisProtocolException("研究生教务学期列表无效")
        return terms
    }

    fun timetable(json: JsonElement): GmisTimetable {
        if (!json.isJsonObject || json.asJsonObject.get("rows")?.isJsonArray != true) {
            throw GmisProtocolException("研究生教务课表格式不正确")
        }
        val sections = linkedMapOf<Int, GmisSection>()
        val occurrences = mutableListOf<Occurrence>()
        for (rowJson in json.asJsonObject.getAsJsonArray("rows")) {
            if (!rowJson.isJsonObject) throw GmisProtocolException("研究生教务课表行格式不正确")
            val row = rowJson.asJsonObject
            val label = plain(row.text("mc"))
            val number = row.text("jcid").toIntOrNull()?.takeIf { it in 1..50 && "无节次" !in label }
            val group = row.text("sjbz")
            val period = clock.find(label)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
            if (number != null) {
                if (sections.put(number, GmisSection(number, group, period)) != null) {
                    throw GmisProtocolException("研究生教务课表节次重复")
                }
            }
            for (day in 1..7) {
                for (line in plain(row.text("z$day")).lineSequence().map(String::trim).filter(String::isNotEmpty)) {
                    val parsed = parseCell(line)
                    occurrences += Occurrence(day, number, group, parsed)
                }
            }
        }

        val courses = mutableListOf<GmisCourse>()
        occurrences.distinct().groupBy { Triple(it.day, it.group, it.cell) }.forEach { (key, values) ->
            val (day, _, entry) = key
            if (values.any { it.section == null }) courses += entry.course(day, null, null, null)
            val numbers = values.mapNotNull { it.section }.distinct().sorted()
            var start: Int? = null
            var end: Int? = null
            fun flush() {
                val from = start ?: return
                val to = end ?: return
                val startClock = sections[from]?.clock?.substringBefore('-')
                val endClock = sections[to]?.clock?.substringAfter('-')
                val time = if (startClock != null && endClock != null) "$startClock-$endClock" else null
                courses += entry.course(day, from, to, time)
            }
            for (number in numbers) {
                if (end == null || number != end!! + 1) {
                    flush()
                    start = number
                }
                end = number
            }
            flush()
        }
        // The server's root "week" is an English weekday, never a teaching-week number.
        return GmisTimetable(
            courses.sortedWith(compareBy({ it.weekday }, { it.startSection ?: Int.MAX_VALUE }, { it.name })),
            sections.values.sortedBy { it.number }
        )
    }

    internal fun parseWeeks(label: String): Set<Int>? {
        var expression = label.replace('，', ',').replace('、', ',').replace(" ", "")
        val odd = "单" in expression
        val even = "双" in expression
        if (odd && even) return null
        val weeks = sortedSetOf<Int>()
        var invalid = false
        expression = weekSpan.replace(expression) {
            val first = it.groupValues[1].toInt()
            val last = it.groupValues[2].toInt()
            if (first !in 1..60 || last !in first..60) invalid = true else weeks.addAll(first..last)
            ""
        }
        expression = expression.replace(Regex("第|周|单|双|[()（）,;；\\s]"), "")
        if (expression.isNotBlank()) {
            // Parse separate numbers from the original, with ranges removed, without
            // concatenating list entries (e.g. 1,3 must not become week 13).
            val rest = weekSpan.replace(label, "").replace(Regex("第|周|单|双|[()（）]"), "")
            val parts = rest.split(Regex("[,，、;；\\s]+")).filter(String::isNotBlank)
            if (parts.any { it.toIntOrNull() !in 1..60 }) invalid = true
            else weeks.addAll(parts.map(String::toInt))
        }
        if (invalid || weeks.isEmpty()) return null
        return weeks.filterTo(sortedSetOf()) { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }
            .takeIf { it.isNotEmpty() }
    }

    private fun parseCell(text: String): Cell {
        val normalized = text.replace('［', '[').replace('］', ']')
        val match = cell.matchEntire(normalized)
            ?: return Cell(text, "", "", null, "周次未明确", text)
        val name = match.groupValues[1].trim().ifEmpty { text }
        val weekLabel = match.groupValues[2].trim()
        val suffix = match.groupValues[3]
        val location = room.find(suffix)
        val teacher = if (location == null) suffix.trim() else suffix.substring(0, location.range.first).trim()
        return Cell(name, teacher, location?.groupValues?.get(1).orEmpty(), parseWeeks(weekLabel), weekLabel, "")
    }

    private fun plain(html: String): String =
        Jsoup.parse(breaks.replace(html, "\n")).wholeText().replace('\u00a0', ' ').trim()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private data class Cell(
        val name: String, val teacher: String, val location: String,
        val weeks: Set<Int>?, val weekLabel: String, val details: String
    ) {
        fun course(day: Int, start: Int?, end: Int?, time: String?) =
            GmisCourse(name, teacher, location, day, start, end, weeks, weekLabel, time, details)
    }
    private data class Occurrence(val day: Int, val section: Int?, val group: String, val cell: Cell)
}
