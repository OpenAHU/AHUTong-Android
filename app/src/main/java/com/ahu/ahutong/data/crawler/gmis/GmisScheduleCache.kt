package com.ahu.ahutong.data.crawler.gmis

import com.google.gson.Gson

/** Account scoping is enforced by the backing store, including after process restart. */
internal interface GmisScheduleCache {
    fun terms(accountId: String): List<GmisTerm>?
    fun timetable(accountId: String, termCode: String): GmisTimetable?
    fun saveTerms(accountId: String, terms: List<GmisTerm>)
    fun saveTimetable(accountId: String, termCode: String, timetable: GmisTimetable)
}

internal object GmisCacheCodec {
    private const val VERSION = 1
    private val gson = Gson()
    private data class TermRecord(val version: Int, val terms: List<GmisTerm>)
    private data class TimetableRecord(val version: Int, val timetable: GmisTimetable)

    fun encodeTerms(terms: List<GmisTerm>): String = gson.toJson(TermRecord(VERSION, terms))

    fun decodeTerms(raw: String?): List<GmisTerm>? = runCatching {
        if (raw.isNullOrBlank() || raw.length > 200_000) return@runCatching null
        val record = gson.fromJson(raw, TermRecord::class.java) ?: return@runCatching null
        val terms = record.terms
        if (record.version != VERSION || terms.isEmpty() || terms.size > 200 ||
            terms.count { it.selected } > 1 || terms.map { it.code }.distinct().size != terms.size ||
            terms.any { !Regex("[A-Za-z0-9_-]{1,40}").matches(it.code) || it.name.isBlank() }
        ) null else terms
    }.getOrNull()

    fun encodeTimetable(timetable: GmisTimetable): String =
        gson.toJson(TimetableRecord(VERSION, timetable))

    fun decodeTimetable(raw: String?): GmisTimetable? = runCatching {
        if (raw.isNullOrBlank() || raw.length > 1_000_000) return@runCatching null
        val record = gson.fromJson(raw, TimetableRecord::class.java) ?: return@runCatching null
        val table = record.timetable
        if (record.version != VERSION || table.courses.size > 800 || table.sections.size > 60 ||
            table.sections.any { it.number !in 1..50 || it.group.length > 100 } ||
            table.courses.any {
                it.name.isBlank() || it.weekday !in 1..7 ||
                    it.weeks?.any { week -> week !in 1..60 } == true ||
                    (it.startSection != null && it.startSection !in 1..50) ||
                    (it.endSection != null && it.endSection !in 1..50) ||
                    it.teacher.length > 500 || it.location.length > 500
            }
        ) null else table
    }.getOrNull()
}
