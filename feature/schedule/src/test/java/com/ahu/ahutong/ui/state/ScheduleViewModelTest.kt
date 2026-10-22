package com.ahu.ahutong.ui.state

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.schedule.ConfigSource
import com.ahu.ahutong.data.schedule.CustomCourse
import com.ahu.ahutong.data.schedule.CustomCourseStore
import com.ahu.ahutong.data.schedule.ResolvedConfig
import com.ahu.ahutong.data.schedule.ScheduleRefreshResult
import com.ahu.ahutong.data.schedule.ScheduleSource
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import com.ahu.ahutong.data.schedule.ScheduleHolidaySource
import java.time.LocalDate
import com.ahu.ahutong.data.schedule.ScheduleWeekConfig
import com.ahu.ahutong.data.schedule.SemesterKey
import com.ahu.ahutong.data.session.SessionIdentity
import com.ahu.ahutong.testing.FakeCourseReminderControl
import com.ahu.ahutong.testing.FakeScheduleSource
import com.ahu.ahutong.testing.FakeScheduleWeekConfig
import com.ahu.ahutong.testing.FakeSessionIdentity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Rule

/**
 * 课表 ViewModel 的契约测试：四个协作方都是 fake，因此不连教务、不碰缓存、不需要设备。
 *
 * 这些行为此前只有真机点一遍才能确认——尤其是"刷新失败时不要重排提醒"与
 * "本地已确认过就不再问远端"这两条，它们在界面上看不出差别。
 *
 * 用 UnconfinedTestDispatcher：ViewModel 的收集与写入都是立即完成的，断言可以直接读结果；
 * InstantTaskExecutorRule 让 LiveData 的 setValue 在 JVM 单测里也算"主线程"。
 */
class ScheduleViewModelTest {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        source: FakeScheduleSource = FakeScheduleSource(),
        config: FakeScheduleWeekConfig = FakeScheduleWeekConfig(),
        session: FakeSessionIdentity = FakeSessionIdentity(),
        reminders: FakeCourseReminderControl = FakeCourseReminderControl(),
        customCourses: CustomCourseStore = FakeCustomCourseStore(),
        holidays: ScheduleHolidaySource = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(emptyMap<LocalDate, ScheduleHoliday>())
            override suspend fun load(years: Set<Int>, refresh: Boolean) =
                AhuResult.Success(emptyMap<LocalDate, ScheduleHoliday>())
        }
    ) = ScheduleViewModel(source, config, session, reminders, holidays, customCourses, Dispatchers.Main)

    private class FakeCustomCourseStore : CustomCourseStore {
        private val revisions = MutableStateFlow(0L)
        override val revision: MutableStateFlow<Long> = revisions
        override fun currentSemesterKey(): String? = "2024-2025-1"
        override fun currentCourses(): List<CustomCourse> = emptyList()
        override fun coursesFor(semesterKey: String): List<CustomCourse> = emptyList()
        override suspend fun saveCurrent(courses: List<CustomCourse>) {
            revisions.value += 1
        }
    }

    @Test
    fun `holiday refresh failure removes markings and does not change reminders`() {
        val date = LocalDate.of(2026, 10, 1)
        val cached = mapOf(date to ScheduleHoliday("国庆节", true))
        val calls = mutableListOf<Boolean>()
        val holidays = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(emptyMap<LocalDate, ScheduleHoliday>())
            override suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
                calls.add(refresh)
                assertEquals(setOf(2026, 2027), years)
                holidays.value = if (refresh) emptyMap() else cached
                return if (refresh) AhuResult.Failure(AhuError.Network) else AhuResult.Success(cached)
            }
        }
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(holidays = holidays, reminders = reminders)

        subject.loadHolidays(LocalDate.of(2026, 9, 1))

        assertEquals(listOf(false), calls)
        assertEquals(cached, subject.scheduleHolidays.value)
        subject.loadHolidays(LocalDate.of(2026, 9, 1), refresh = true)
        assertEquals(listOf(false, true), calls)
        assertEquals(emptyMap(), subject.scheduleHolidays.value)
        assertEquals(0, reminders.rescheduleCount)
    }

    @Test
    fun `fresh holiday arrangements replace the cache and clear removes them`() {
        val date = LocalDate.of(2026, 10, 1)
        val latest = mapOf(date to ScheduleHoliday("国庆节", true))
        val holidays = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(emptyMap<LocalDate, ScheduleHoliday>())
            override suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
                holidays.value = latest
                return AhuResult.Success(latest)
            }
        }
        val subject = viewModel(holidays = holidays)

        subject.loadHolidays(LocalDate.of(2026, 9, 1))
        assertEquals(latest, subject.scheduleHolidays.value)
        subject.clear()
        assertEquals(emptyMap(), subject.scheduleHolidays.value)
    }

    @Test
    fun `refresh button requests both timetable and holidays and source revocation reaches screen`() {
        val calls = mutableListOf<Boolean>()
        val date = LocalDate.of(2026, 10, 1)
        val holidays = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(mapOf(date to ScheduleHoliday("国庆节", true)))
            override suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
                calls.add(refresh)
                return AhuResult.Success(holidays.value)
            }
        }
        val source = FakeScheduleSource()
        val subject = viewModel(source = source, holidays = holidays)

        subject.refreshSchedule(isRefresh = true)

        assertEquals(listOf(true), calls)
        assertEquals(1, source.refreshCount)
        assertTrue(subject.scheduleHolidays.value.orEmpty().containsKey(date))
        holidays.holidays.value = emptyMap()
        assertEquals(emptyMap(), subject.scheduleHolidays.value)
    }

    @Test
    fun `holiday range follows the supplied semester length`() {
        val requested = mutableListOf<Set<Int>>()
        val holidays = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(emptyMap<LocalDate, ScheduleHoliday>())
            override suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
                requested.add(years)
                return AhuResult.Success(emptyMap())
            }
        }
        val subject = viewModel(holidays = holidays)
        subject.loadHolidays(LocalDate.of(2026, 1, 1), weekCount = 60)
        assertEquals(listOf(setOf(2026, 2027)), requested)
    }

    @Test
    fun `refreshing without a session and without mock data reports unauthorized`() {
        val source = FakeScheduleSource()
        val subject = viewModel(source = source, session = FakeSessionIdentity(loggedIn = false))

        subject.refreshSchedule()

        assertEquals(0, source.fetchCount)
        assertEquals(AhuError.Unauthorized("请先登录！"), subject.schedule.value?.errorOrNull())
    }

    @Test
    fun `mock data is enough to load a timetable without a session`() {
        val source = FakeScheduleSource().apply { mockData = true }
        val subject = viewModel(source = source, session = FakeSessionIdentity(loggedIn = false))

        subject.refreshSchedule()

        assertEquals(1, source.fetchCount)
    }

    @Test
    fun `a successful refresh stores the timetable and recomputes reminders`() {
        val courses = listOf(Course())
        val source = FakeScheduleSource().apply {
            fetchResult = AhuResult.Success(courses)
            fetchedAtValue = 42L
        }
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(source = source, reminders = reminders)

        subject.refreshSchedule()

        assertEquals(courses, subject.schedule.value?.valueOrNull())
        assertEquals(42L, subject.scheduleFetchedAt.value)
        assertEquals(1, reminders.rescheduleCount)
    }

    @Test
    fun `a failed refresh does not recompute reminders`() {
        val source = FakeScheduleSource().apply {
            fetchResult = AhuResult.Failure(AhuError.Network)
        }
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(source = source, reminders = reminders)

        subject.refreshSchedule()

        assertEquals(0, reminders.rescheduleCount)
    }

    @Test
    fun `entering the screen seeds from the cache before refreshing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cached = listOf(Course())
        val source = FakeScheduleSource().apply { cachedSchedule = cached }
        val subject = viewModel(source = source)

        subject.onScheduleEntered()

        assertNull(subject.schedule.value)
        assertEquals(0, source.refreshCount)
        runCurrent()
        assertEquals(1, source.refreshCount)
        assertEquals(cached, subject.schedule.value?.valueOrNull())
    }

    @Test
    fun `a background refresh that changed the timetable replaces it and recomputes reminders`() {
        val latest = listOf(Course())
        val source = FakeScheduleSource().apply {
            refreshResult = AhuResult.Success(
                ScheduleRefreshResult(schedule = latest, changed = true, fetchedAt = 7L)
            )
        }
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(source = source, reminders = reminders)

        subject.refreshSchedule(isRefresh = true)

        assertEquals(latest, subject.schedule.value?.valueOrNull())
        assertEquals(7L, subject.scheduleFetchedAt.value)
        assertEquals(1, reminders.rescheduleCount)
        assertEquals(false, subject.isScheduleRefreshing.value)
    }

    @Test
    fun `an unchanged background refresh keeps the screen and does not recompute reminders`() {
        val onScreen = listOf(Course())
        val source = FakeScheduleSource().apply {
            refreshResult = AhuResult.Success(
                ScheduleRefreshResult(schedule = listOf(Course()), changed = false, fetchedAt = 9L)
            )
        }
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(source = source, reminders = reminders)
        subject.schedule.value = AhuResult.Success(onScreen)

        subject.refreshSchedule(isRefresh = true)

        assertEquals(onScreen, subject.schedule.value?.valueOrNull())
        assertEquals(0, reminders.rescheduleCount)
    }

    @Test
    fun `a failed background refresh keeps what is on screen and records the failure`() {
        val onScreen = listOf(Course())
        val source = FakeScheduleSource().apply {
            refreshResult = AhuResult.Failure(AhuError.Network)
        }
        val subject = viewModel(source = source)
        subject.schedule.value = AhuResult.Success(onScreen)

        subject.refreshSchedule(isRefresh = true)

        assertEquals(onScreen, subject.schedule.value?.valueOrNull())
        assertEquals(AhuError.Network, subject.scheduleRefreshError.value)
    }

    @Test
    fun `loadConfig uses the local answer without asking the remote when it is authoritative`() {
        val config = FakeScheduleWeekConfig().apply {
            localResult = ResolvedConfig(ScheduleConfigBean(), ConfigSource.REMOTE)
        }
        val subject = viewModel(config = config)

        subject.loadConfig()

        assertEquals(listOf("resolve"), config.calls)
    }

    @Test
    fun `loadConfig asks the remote when the local answer is not authoritative`() {
        val remote = ScheduleConfigBean().apply { week = 8 }
        val config = FakeScheduleWeekConfig().apply {
            localResult = ResolvedConfig(ScheduleConfigBean(), ConfigSource.LOCAL)
            remoteResult = ResolvedConfig(remote, ConfigSource.REMOTE)
        }
        val subject = viewModel(config = config)

        subject.loadConfig()

        assertEquals(listOf("resolve", "remote"), config.calls)
        assertEquals(8, subject.scheduleConfig.value?.week)
    }

    @Test
    fun `saving a term writes the year and the term before the position`() {
        val config = FakeScheduleWeekConfig()
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(config = config, reminders = reminders)

        subject.saveTime(schoolYear = "2024-2025", schoolTerm = "1", week = 3)

        assertEquals("build", config.calls.first())
        assertEquals("year", config.calls[1])
        assertEquals("term", config.calls[2])
        assertEquals("position", config.calls.last())
        assertEquals(1, reminders.rescheduleCount)
        assertEquals(true, subject.scheduleConfig.value?.isInSemester)
        assertEquals(3, subject.scheduleConfig.value?.week)
    }
    @Test
    fun `the semester label falls back to the stored year and then to the built-in default`() {
        val config = FakeScheduleWeekConfig()
        val subject = viewModel(config = config)
        assertEquals("2022-2023", subject.schoolYear)
        assertEquals("1", subject.schoolTerm)

        config.schoolYear = "2023-2024"
        assertEquals("2023-2024", subject.schoolYear)

        config.semesterKey = SemesterKey("2024-2025-2", "2024-2025", "2")
        assertEquals("2024-2025", subject.schoolYear)
        assertEquals("2", subject.schoolTerm)
    }

    @Test
    fun `historical preview keeps current timetable and reminders and reuses its own cache`() {
        val current = listOf(Course().apply { name = "当前课程" })
        val historical = listOf(Course().apply { name = "历史课程" })
        val source = FakeScheduleSource().apply { semesterResult = AhuResult.Success(historical) }
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(source = source, reminders = reminders)
        subject.schedule.value = AhuResult.Success(current)

        subject.loadSemester(100)
        assertEquals(historical, subject.semesterSchedule.value?.valueOrNull())
        assertEquals(current, subject.schedule.value?.valueOrNull())
        assertEquals(0, reminders.rescheduleCount)
        subject.closeSemesterPreview()
        subject.loadSemester(100)
        assertEquals(listOf(100), source.semesterRequests)
        subject.loadSemester(100, isRefresh = true)
        assertEquals(listOf(100, 100), source.semesterRequests)
    }

    @Test
    fun `changing preview cancels an older semester response`() {
        val first = CompletableDeferred<AhuResult<List<Course>>>()
        val second = CompletableDeferred<AhuResult<List<Course>>>()
        val source = FakeScheduleSource().apply {
            semesterLoader = { if (it == 100) first.await() else second.await() }
        }
        val subject = viewModel(source = source)
        subject.loadSemester(100)
        subject.loadSemester(200)
        val latest = listOf(Course().apply { name = "另一个学期" })
        second.complete(AhuResult.Success(latest))
        first.complete(AhuResult.Success(listOf(Course())))

        assertEquals(200, subject.previewedSemesterId.value)
        assertEquals(latest, subject.semesterSchedule.value?.valueOrNull())
        assertEquals(false, subject.isSemesterScheduleLoading.value)
    }

    @Test
    fun `semester catalogue retries failures and clearing discards all preview state`() {
        val source = FakeScheduleSource().apply {
            semestersResult = AhuResult.Failure(AhuError.Network)
        }
        val subject = viewModel(source = source)
        subject.loadSemesters()
        assertTrue(subject.semesters.value?.isFailure == true)
        source.semestersResult = AhuResult.Success(listOf(ScheduleSemester(100, "2024-2025-1")))
        subject.loadSemesters()
        subject.loadSemesters()
        assertEquals(2, source.semestersCount)
        subject.loadSemester(100)
        subject.clear()

        assertEquals(null, subject.semesters.value)
        assertEquals(null, subject.previewedSemesterId.value)
        assertEquals(null, subject.semesterSchedule.value)
        subject.loadSemester(100)
        assertEquals(listOf(100, 100), source.semesterRequests)
    }

    @Test
    fun `signed out session cannot reuse historical timetable or catalogue cache`() {
        val session = FakeSessionIdentity()
        val source = FakeScheduleSource().apply {
            semestersResult = AhuResult.Success(listOf(ScheduleSemester(100, "2024-2025-1")))
            semesterResult = AhuResult.Success(listOf(Course()))
        }
        val subject = viewModel(source = source, session = session)
        subject.loadSemesters()
        subject.loadSemester(100)
        session.loggedIn = false
        subject.loadSemesters()
        subject.loadSemester(100)

        assertTrue(subject.semesters.value?.isFailure == true)
        assertTrue(subject.semesterSchedule.value?.isFailure == true)
        assertEquals(1, source.semestersCount)
        assertEquals(listOf(100), source.semesterRequests)
    }

    @Test
    fun `an old account background refresh cannot replace the active account timetable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val response = CompletableDeferred<AhuResult<ScheduleRefreshResult>>()
        val source = FakeScheduleSource().apply { refreshLoader = { response.await() } }
        val session = FakeSessionIdentity(user = User().apply { xh = "account-a" })
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(source = source, session = session, reminders = reminders)
        subject.refreshSchedule(isRefresh = true)
        runCurrent()
        session.user = User().apply { xh = "account-b" }
        val current = listOf(Course().apply { name = "当前账号" })
        subject.schedule.value = AhuResult.Success(current)

        response.complete(AhuResult.Success(ScheduleRefreshResult(
            listOf(Course().apply { name = "旧账号" }), changed = true, fetchedAt = 123L
        )))
        runCurrent()

        assertEquals(current, subject.schedule.value?.valueOrNull())
        assertNull(subject.scheduleFetchedAt.value)
        assertEquals(0, reminders.rescheduleCount)
    }

    @Test
    fun `an old foreground request cannot repopulate the timetable after clear`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val response = CompletableDeferred<AhuResult<List<Course>>>()
        val source = FakeScheduleSource().apply { fetchLoader = { response.await() } }
        val reminders = FakeCourseReminderControl()
        val subject = viewModel(source = source, reminders = reminders)
        subject.refreshSchedule()
        runCurrent()
        subject.clear()
        response.complete(AhuResult.Success(listOf(Course())))
        runCurrent()

        assertEquals(emptyList(), subject.schedule.value?.valueOrNull())
        assertNull(subject.scheduleFetchedAt.value)
        assertEquals(0, reminders.rescheduleCount)
    }

    @Test
    fun `an old account remote config cannot replace the active account config`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val response = CompletableDeferred<ResolvedConfig?>()
        val config = FakeScheduleWeekConfig().apply { remoteLoader = { response.await() } }
        val session = FakeSessionIdentity(user = User().apply { xh = "account-a" })
        val subject = viewModel(config = config, session = session)
        subject.loadConfig()
        runCurrent()
        session.user = User().apply { xh = "account-b" }
        val current = ScheduleConfigBean().apply { week = 4 }
        subject.scheduleConfig.value = current
        response.complete(ResolvedConfig(ScheduleConfigBean().apply { week = 12 }, ConfigSource.REMOTE))
        runCurrent()

        assertEquals(current, subject.scheduleConfig.value)
    }

    @Test
    fun `a new account can refresh while the previous account request is still pending`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val first = CompletableDeferred<AhuResult<ScheduleRefreshResult>>()
        val second = CompletableDeferred<AhuResult<ScheduleRefreshResult>>()
        var calls = 0
        val source = FakeScheduleSource().apply {
            refreshLoader = { if (++calls == 1) first.await() else second.await() }
        }
        val session = FakeSessionIdentity(user = User().apply { xh = "account-a" })
        val subject = viewModel(source = source, session = session)
        subject.refreshSchedule(isRefresh = true)
        runCurrent()
        session.user = User().apply { xh = "account-b" }
        subject.refreshSchedule(isRefresh = true)
        runCurrent()
        first.complete(AhuResult.Failure(AhuError.Network))
        runCurrent()

        assertEquals(2, source.refreshCount)
        assertTrue(subject.isScheduleRefreshing.value == true)
        val current = listOf(Course().apply { name = "当前账号" })
        second.complete(AhuResult.Success(ScheduleRefreshResult(current, true, 456L)))
        runCurrent()
        assertEquals(current, subject.schedule.value?.valueOrNull())
        assertEquals(456L, subject.scheduleFetchedAt.value)
        assertEquals(false, subject.isScheduleRefreshing.value)
    }

    @Test
    fun `a slower old foreground request cannot replace a newer result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val first = CompletableDeferred<AhuResult<List<Course>>>()
        val second = CompletableDeferred<AhuResult<List<Course>>>()
        var calls = 0
        val source = FakeScheduleSource().apply {
            fetchLoader = { if (++calls == 1) first.await() else second.await() }
        }
        val subject = viewModel(source = source)
        subject.refreshSchedule()
        runCurrent()
        subject.refreshSchedule()
        runCurrent()
        val latest = listOf(Course().apply { name = "最新课程" })
        second.complete(AhuResult.Success(latest))
        runCurrent()
        first.complete(AhuResult.Success(listOf(Course().apply { name = "旧课程" })))
        runCurrent()
        assertEquals(latest, subject.schedule.value?.valueOrNull())
    }

    @Test
    fun `a startup fetch cannot replace a newer background refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val startup = CompletableDeferred<AhuResult<List<Course>>>()
        val latest = listOf(Course().apply { name = "最新课程" })
        val source = FakeScheduleSource().apply {
            fetchLoader = { startup.await() }
            refreshResult = AhuResult.Success(ScheduleRefreshResult(latest, true, 789L))
        }
        val subject = viewModel(source = source)
        subject.refreshSchedule()
        runCurrent()
        subject.refreshSchedule(isRefresh = true)
        runCurrent()
        startup.complete(AhuResult.Success(listOf(Course().apply { name = "启动时旧课程" })))
        runCurrent()

        assertEquals(latest, subject.schedule.value?.valueOrNull())
        assertEquals(789L, subject.scheduleFetchedAt.value)
    }
}
