package com.ahu.ahutong.appwidget

import com.ahu.ahutong.data.crawler.gmis.GmisCacheCodec
import com.ahu.ahutong.data.crawler.gmis.GmisScheduleClient
import com.ahu.ahutong.data.crawler.gmis.GmisScheduleSnapshot
import com.ahu.ahutong.data.crawler.gmis.GmisSessionExpiredException
import com.ahu.ahutong.data.crawler.login.PortalPage
import com.ahu.ahutong.data.model.AcademicAccountType
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.ahu.ahutong.data.schedule.gmis.GmisCourse
import com.ahu.ahutong.data.schedule.gmis.GmisTerm
import com.ahu.ahutong.data.schedule.gmis.GmisTimetable
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class GraduateWidgetScheduleTest {
    private val user = User("测试", "graduate-A").apply {
        academicAccountType = AcademicAccountType.POSTGRADUATE
    }
    private var active: User? = user
    private val cache = mutableMapOf<Pair<String, String>, String>()
    private val date = LocalDate.of(2026, 9, 21)
    private val firstMonday = LocalDate.of(2026, 8, 24)
    private val term = GmisTerm("54", "本学期", true)
    private val course = GmisCourse("示例课程", "教师", "教室", 1, 14, 14,
        setOf(1, 5), "1,5周", "21:30-22:15", "")
    private val page = GmisScheduleSnapshot(listOf(term), term, GmisTimetable(listOf(course), emptyList()))
    private var onQuery: () -> Unit = {}
    private var failure: Exception? = null
    private var reads = 0
    private var fetchedAt = 1000L

    private fun repository() = GraduateWidgetSchedule(
        currentUser = { active },
        read = { account, key -> cache[account to key] },
        write = { account, key, value -> cache[account to key] = value },
        newClient = { GmisScheduleClient(
            openSession = { "https://gmis.ahu.edu.cn/gmis5/(S(test))/student/default/index" },
            query = { url, fields, _ ->
                reads++
                onQuery()
                failure?.let { throw it }
                PortalPage(url, 200, if (fields == null)
                    """[{"termcode":"54","termname":"本学期","selected":true}]"""
                else """{"rows":[{"jcid":14,"sjbz":"晚上","mc":"第14节<br/>(21:30-22:15)",
                    "z1":"新课程[1,5周] 教师 [教室]"}]}""")
            }) },
        today = { date },
        now = { fetchedAt }
    )

    private fun savePage(snapshot: GmisScheduleSnapshot = page) {
        cache[user.xh to "gmis.schedule.v1.terms"] = GmisCacheCodec.encodeTerms(snapshot.terms)
        cache[user.xh to "gmis.schedule.v1.term.${snapshot.selectedTerm.code}"] =
            GmisCacheCodec.encodeTimetable(snapshot.timetable)
    }

    private fun saveWeek() {
        cache[user.xh to PostgraduateTeachingWeek.storageKey("54")] = firstMonday.toString()
    }

    @Test fun cacheRenderingNeverRequestsNetworkOrInventsFetchTime() {
        savePage()
        saveWeek()
        val before = cache.toMap()
        val result = repository().cached(user)
        assertEquals(5, result.config.week)
        assertEquals(listOf(1, 5), result.courses.single().weekIndexes)
        assertEquals(1290..1335, ScheduleSectionTimes.getCourseTimeRangeInMinutes(result.courses.single()))
        assertNull(result.fetchedAt)
        assertEquals(0, reads)
        assertEquals(before, cache)
    }

    @Test fun unknownTeachingWeekShowsSetupMessageInsteadOfNoClasses() {
        savePage()
        val result = repository().cached(user)
        assertTrue(result.courses.isEmpty())
        assertFalse(result.config.isInSemester)
        assertEquals("请在课表页设置当前教学周", result.unavailableMessage)
    }

    @Test fun absentAndCorruptCacheShowAcquisitionMessage() {
        assertNotNull(repository().cached(user).unavailableMessage)
        cache[user.xh to "gmis.widget.schedule.v1"] = "{\"version\":1}"
        assertNotNull(repository().cached(user).unavailableMessage)
        assertTrue(repository().cached(user).courses.isEmpty())
    }

    @Test fun unknownTimeCoursesStayVisibleAsWarningWithoutGuessingUndergraduateTime() {
        val incomplete = page.copy(timetable = GmisTimetable(listOf(course.copy(clock = null)), emptyList()))
        val result = graduateWidgetSnapshot(incomplete, firstMonday, date, null, null)
        assertTrue(result.courses.isEmpty())
        assertNotNull(result.unavailableMessage)
        assertTrue(result.notice!!.contains("1 门"))
    }

    @Test fun datesBeforeAndAfterTeachingCalendarNeverShowClasses() {
        assertFalse(graduateWidgetSnapshot(page, firstMonday, firstMonday.minusDays(1), null, null).config.isInSemester)
        assertFalse(graduateWidgetSnapshot(page, firstMonday, firstMonday.plusWeeks(60), null, null).config.isInSemester)
    }

    @Test fun damagedGraduateClockCannotUseUndergraduateFallback() {
        listOf("broken", "25:00-26:00", "10:70-11:00", "22:15-21:30").forEach { clock ->
            val damaged = page.copy(timetable = GmisTimetable(
                listOf(course.copy(startSection = 1, endSection = 2, clock = clock)), emptyList()))
            val result = graduateWidgetSnapshot(damaged, firstMonday, date, null, null)
            assertTrue(result.courses.isEmpty())
            assertNotNull(result.unavailableMessage)
        }
    }

    @Test fun successfulRefreshReplacesWidgetCacheAndPreservesPageCacheAndWeek() = runTest {
        savePage()
        saveWeek()
        val before = cache.toMap()
        val repository = repository()
        repository.refresh(user)
        val result = repository.cached(user)
        assertEquals("新课程", result.courses.single().name)
        assertEquals(1000L, result.fetchedAt)
        assertEquals(2, reads)
        before.forEach { (key, value) -> assertEquals(value, cache[key]) }
        fetchedAt = 2000
        repository.refresh(user)
        assertEquals(2000L, repository.cached(user).fetchedAt)
    }

    @Test fun failedRefreshRetainsCoursesAndOriginalAcquisitionTime() = runTest {
        saveWeek()
        val repository = repository()
        repository.refresh(user)
        failure = IOException("offline")
        fetchedAt = 2000
        repository.refresh(user)
        val result = repository.cached(user)
        assertEquals("新课程", result.courses.single().name)
        assertEquals(1000L, result.fetchedAt)
        assertTrue(result.notice!!.contains("更新失败"))
    }

    @Test fun expiredLoginRetainsCacheAndPromptsOpeningApp() = runTest {
        saveWeek()
        val repository = repository()
        repository.refresh(user)
        failure = GmisSessionExpiredException()
        repository.refresh(user)
        assertEquals(1000L, repository.cached(user).fetchedAt)
        assertTrue(repository.cached(user).notice!!.contains("登录已过期"))
    }

    @Test fun accountSwitchDuringRequestPreventsAllWrites() = runTest {
        onQuery = { active = User("其他账号", "graduate-B") }
        assertFailsWith<CancellationException> { repository().refresh(user) }
        assertTrue(cache.isEmpty())
    }

    @Test fun logoutAndLoginSameAccountStillCancelsOldRequest() = runTest {
        onQuery = { active = User(user.name, user.xh).apply { academicAccountType = AcademicAccountType.POSTGRADUATE } }
        assertFailsWith<CancellationException> { repository().refresh(user) }
        assertTrue(cache.isEmpty())
    }

    @Test fun changedForegroundCacheWinsOverOlderWidgetCache() = runTest {
        savePage()
        saveWeek()
        val repository = repository()
        repository.refresh(user)
        savePage(page.copy(timetable = GmisTimetable(listOf(course.copy(name = "App更新课程")), emptyList())))
        assertEquals("App更新课程", repository.cached(user).courses.single().name)
        assertNull(repository.cached(user).fetchedAt)
    }

    @Test fun foregroundRefreshDuringRequestWinsUntilNextWidgetRefresh() = runTest {
        savePage()
        saveWeek()
        onQuery = { savePage(page.copy(timetable = GmisTimetable(listOf(course.copy(name = "同时更新")), emptyList()))) }
        val repository = repository()
        repository.refresh(user)
        assertEquals("同时更新", repository.cached(user).courses.single().name)
        onQuery = {}
        repository.refresh(user)
        assertEquals("新课程", repository.cached(user).courses.single().name)
    }

    @Test fun newForegroundTermWithoutTableCannotDisplayPreviousSemesterCourses() = runTest {
        savePage()
        saveWeek()
        val repository = repository()
        repository.refresh(user)
        cache[user.xh to "gmis.schedule.v1.terms"] =
            GmisCacheCodec.encodeTerms(listOf(GmisTerm("55", "新学期", true)))
        val result = repository.cached(user)
        assertTrue(result.courses.isEmpty())
        assertNotNull(result.unavailableMessage)
        assertNull(result.fetchedAt)
    }

    @Test fun genuinelyEmptyTimetableIsNotReportedAsFailedAcquisition() {
        savePage(page.copy(timetable = GmisTimetable(emptyList(), emptyList())))
        saveWeek()
        assertNull(repository().cached(user).unavailableMessage)
        assertTrue(repository().cached(user).courses.isEmpty())
    }

    @Test fun differentAccountCannotReadPreviousWidgetCache() = runTest {
        saveWeek()
        val repository = repository()
        repository.refresh(user)
        val other = User("另一账号", "graduate-B").apply { academicAccountType = AcademicAccountType.POSTGRADUATE }
        active = other
        assertTrue(repository.cached(other).courses.isEmpty())
        assertNull(repository.cached(other).fetchedAt)
    }
}
