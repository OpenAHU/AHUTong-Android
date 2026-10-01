package com.ahu.ahutong.ui.state

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.schedule.ConfigSource
import com.ahu.ahutong.data.schedule.ResolvedConfig
import com.ahu.ahutong.data.schedule.ScheduleRefreshResult
import com.ahu.ahutong.data.schedule.ScheduleSource
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
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
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
        holidays: ScheduleHolidaySource = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(emptyMap<LocalDate, ScheduleHoliday>())
            override suspend fun load(years: Set<Int>, refresh: Boolean) =
                AhuResult.Success(emptyMap<LocalDate, ScheduleHoliday>())
        }
    ) = ScheduleViewModel(source, config, session, reminders, holidays)

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

        assertEquals(cached, subject.schedule.value?.valueOrNull())
        assertEquals(0, source.refreshCount)
        runCurrent()
        assertEquals(1, source.refreshCount)
        assertEquals(emptyList(), subject.schedule.value?.valueOrNull())
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
}
