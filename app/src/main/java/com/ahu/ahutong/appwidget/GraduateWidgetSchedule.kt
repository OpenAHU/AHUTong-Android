package com.ahu.ahutong.appwidget

import androidx.annotation.Keep
import com.ahu.ahutong.data.crawler.gmis.GmisCacheCodec
import com.ahu.ahutong.data.crawler.gmis.GmisScheduleClient
import com.ahu.ahutong.data.crawler.gmis.GmisScheduleSnapshot
import com.ahu.ahutong.data.crawler.gmis.GmisSessionExpiredException
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.AcademicAccountType
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek
import com.ahu.ahutong.data.schedule.WidgetScheduleSnapshot
import com.ahu.ahutong.data.schedule.gmis.GmisTimetableAdapter
import com.ahu.ahutong.data.security.SecureBoxStore
import com.ahu.ahutong.data.session.SessionStore
import com.google.gson.Gson
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

/** Widget writes have their own keys; neither the page cache nor its revision is changed. */
internal class GraduateWidgetSchedule(
    private val currentUser: () -> User?,
    private val read: (String, String) -> String?,
    private val write: (String, String, String) -> Unit,
    private val newClient: (User) -> GmisScheduleClient,
    private val today: () -> LocalDate,
    private val now: () -> Long = System::currentTimeMillis
) {
    fun cached(user: User): WidgetScheduleSnapshot {
        val page = pageSnapshot(user.xh)
        val record = decode(read(user.xh, CACHE_KEY))
        val pageTerm = pageTerms(user.xh)?.let { terms ->
            (terms.firstOrNull { it.selected } ?: terms.first()).code
        }
        // A changed foreground cache wins over a widget response that started earlier.
        val usePage = page != null && signature(page) != record?.pageSignature
        val changedTermWithoutTable = page == null && pageTerm != null && pageTerm != record?.pageTermCode
        val snapshot = when {
            changedTermWithoutTable -> null
            usePage -> page
            else -> record?.snapshot() ?: page
        }
        val fetchedAt = record?.fetchedAt.takeUnless { usePage || changedTermWithoutTable }
        val firstMonday = snapshot?.let {
            PostgraduateTeachingWeek.parseStored(read(user.xh,
                PostgraduateTeachingWeek.storageKey(it.selectedTerm.code)))
        }
        return graduateWidgetSnapshot(snapshot, firstMonday, today(), fetchedAt,
            read(user.xh, STATUS_KEY))
    }

    suspend fun refresh(user: User) {
        requireSession(user)
        val baseline = pageSnapshot(user.xh)?.let(::signature)
        val pageTerm = pageTerms(user.xh)?.let { terms ->
            (terms.firstOrNull { it.selected } ?: terms.first()).code
        }
        try {
            val client = newClient(user)
            val terms = client.terms()
            requireSession(user)
            val selected = terms.firstOrNull { it.selected } ?: terms.first()
            val table = client.timetable(selected)
            requireSession(user)
            val record = Record(1, GmisCacheCodec.encodeTerms(terms),
                GmisCacheCodec.encodeTimetable(table), now(), baseline, pageTerm)
            write(user.xh, CACHE_KEY, gson.toJson(record))
            requireSession(user)
            write(user.xh, STATUS_KEY, "")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            requireSession(user)
            write(user.xh, STATUS_KEY, if (error is GmisSessionExpiredException)
                "登录已过期，请打开安大通后刷新" else "更新失败，显示缓存课表")
        }
    }

    private fun requireSession(user: User) {
        if (currentUser() !== user || user.academicAccountType != AcademicAccountType.POSTGRADUATE) {
            throw CancellationException("Widget account changed")
        }
    }

    private fun pageTerms(account: String) =
        GmisCacheCodec.decodeTerms(read(account, "gmis.schedule.v1.terms"))

    private fun pageSnapshot(account: String): GmisScheduleSnapshot? {
        val terms = pageTerms(account) ?: return null
        val term = terms.firstOrNull { it.selected } ?: terms.first()
        val table = GmisCacheCodec.decodeTimetable(read(account, "gmis.schedule.v1.term.${term.code}"))
            ?: return null
        return GmisScheduleSnapshot(terms, term, table)
    }

    @Keep
    private data class Record(
        val version: Int,
        val terms: String,
        val table: String,
        val fetchedAt: Long,
        val pageSignature: String?,
        val pageTermCode: String?
    ) {
        fun snapshot(): GmisScheduleSnapshot? {
            val available = GmisCacheCodec.decodeTerms(terms) ?: return null
            val selected = available.firstOrNull { it.selected } ?: available.first()
            val timetable = GmisCacheCodec.decodeTimetable(table) ?: return null
            return GmisScheduleSnapshot(available, selected, timetable)
        }
    }

    private fun decode(raw: String?): Record? = runCatching {
        if (raw.isNullOrBlank() || raw.length > 2_000_000) return@runCatching null
        gson.fromJson(raw, Record::class.java)?.takeIf {
            it.version == 1 && it.fetchedAt > 0 && it.snapshot() != null
        }
    }.getOrNull()

    private fun signature(snapshot: GmisScheduleSnapshot): String =
        MessageDigest.getInstance("SHA-256").digest(gson.toJson(snapshot).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private val gson = Gson()
        private const val CACHE_KEY = "gmis.widget.schedule.v1"
        private const val STATUS_KEY = "gmis.widget.refresh_status.v1"

        val instance by lazy {
            GraduateWidgetSchedule(
                currentUser = SessionStore::currentUser,
                read = { account, key -> SecureBoxStore.get(SecureBoxStore.userBox(account), key) },
                write = { account, key, value ->
                    SecureBoxStore.put(SecureBoxStore.userBox(account), key, value)
                },
                newClient = GraduateWidgetSession::client,
                today = DebugClock::nowLocalDate
            )
        }
    }
}

internal fun graduateWidgetSnapshot(
    snapshot: GmisScheduleSnapshot?,
    firstMonday: LocalDate?,
    today: LocalDate,
    fetchedAt: Long?,
    status: String?
): WidgetScheduleSnapshot {
    val week = firstMonday?.let { PostgraduateTeachingWeek.weekOn(it, today) }
    val config = ScheduleConfigBean().apply {
        this.week = week ?: 0
        weekDay = today.dayOfWeek.value
        isInSemester = week != null && week in 1..PostgraduateTeachingWeek.MAX_WEEK
    }
    val grid = snapshot?.let {
        // Damaged cache clocks must never fall back to the undergraduate section clock.
        val validated = it.timetable.copy(courses = it.timetable.courses.map { course ->
            val startSection = course.startSection
            val endSection = course.endSection
            val sectionsValid = startSection != null && endSection != null && startSection <= endSection
            if (sectionsValid && validGraduateClock(course.clock)) course else course.copy(clock = null)
        })
        GmisTimetableAdapter.adapt(validated)
    }
    val unavailable = when {
        snapshot == null -> "请打开安大通获取研究生课表"
        firstMonday == null -> "请在课表页设置当前教学周"
        grid?.courses.isNullOrEmpty() && !grid?.unplaced.isNullOrEmpty() -> "课程时间待确认，请查看课表页"
        else -> null
    }
    val notice = listOfNotNull(status?.takeIf(String::isNotBlank),
        grid?.unplaced?.size?.takeIf { it > 0 }?.let { "另有 $it 门课程时间待确认" })
        .joinToString(" · ").ifBlank { null }
    return WidgetScheduleSnapshot(
        courses = if (unavailable == null) grid?.courses.orEmpty() else emptyList(),
        config = config,
        fetchedAt = fetchedAt,
        unavailableMessage = unavailable,
        notice = notice
    )
}

private fun validGraduateClock(value: String?): Boolean {
    val parts = value?.split('-')?.takeIf { it.size == 2 } ?: return false
    fun minutes(clock: String): Int? {
        val match = Regex("(\\d{1,2}):(\\d{2})").matchEntire(clock) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
    }
    val start = minutes(parts[0]) ?: return false
    val end = minutes(parts[1]) ?: return false
    return start <= end
}
