package com.ahu.ahutong.ui.state

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.core.common.onFailure
import com.ahu.ahutong.core.common.onSuccess
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.ConfigSource
import com.ahu.ahutong.data.schedule.CustomCourse
import com.ahu.ahutong.data.schedule.CustomCourseStore
import com.ahu.ahutong.data.schedule.ScheduleSource
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import com.ahu.ahutong.data.schedule.ScheduleHolidaySource
import com.ahu.ahutong.data.schedule.scheduleHolidayYears
import com.ahu.ahutong.data.schedule.scheduleWeekDates
import com.ahu.ahutong.data.schedule.ScheduleWeekConfig
import com.ahu.ahutong.data.session.SessionIdentity
import com.ahu.ahutong.ext.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.drop

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScheduleDispatcher

/**
 * 课表页的状态。
 *
 * 协作方都是接口：课表数据（[ScheduleSource]）、学期与周次（[ScheduleWeekConfig]）、
 * 登录态（[SessionIdentity]）、提醒重算（[CourseReminderControl]）与国家放假安排（[ScheduleHolidaySource]）。
 * 协议、缓存键、提醒调度与登录态存储因此都不在这个类里——它们由 :app 的适配器接上。
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleSource: ScheduleSource,
    private val weekConfig: ScheduleWeekConfig,
    private val session: SessionIdentity,
    private val reminders: CourseReminderControl,
    private val holidaySource: ScheduleHolidaySource,
    private val customCourseStore: CustomCourseStore,
    @ScheduleDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    val TAG = "ScheduleViewModel"
    val schedule = MutableLiveData<AhuResult<List<Course>>>()
    /** 自定义课程列表：课表配色需要它拿到用户选的颜色。 */
    val customCourses = MutableLiveData<List<CustomCourse>>(emptyList())
    val nextSchedule = MutableLiveData<AhuResult<List<Course>>>()
    val isNextScheduleLoading = MutableLiveData(false)
    private var nextScheduleJob: Job? = null
    private var nextRequestRevision = 0L
    val semesters = MutableLiveData<AhuResult<List<ScheduleSemester>>>()
    val isSemestersLoading = MutableLiveData(false)
    val semesterSchedule = MutableLiveData<AhuResult<List<Course>>>()
    val previewedSemesterId = MutableLiveData<Int?>()
    val isSemesterScheduleLoading = MutableLiveData(false)
    private var semestersJob: Job? = null
    private var catalogueRequestRevision = 0L
    private var semesterScheduleJob: Job? = null
    private var semesterRequestRevision = 0L
    private val semesterSchedules = mutableMapOf<Int, List<Course>>()
    private var previewCacheIdentity: Triple<Boolean, String?, Boolean>? = null

    val currentAccountId: String?
        get() = session.currentUser()?.xh

    private fun previewIdentity() =
        Triple(session.isLoggedIn(), currentAccountId, scheduleSource.usesMockData())

    private fun ensurePreviewIdentity(): Triple<Boolean, String?, Boolean> {
        val identity = previewIdentity()
        if (identity != previewCacheIdentity) {
            catalogueRequestRevision++
            nextRequestRevision++
            semestersJob?.cancel()
            nextScheduleJob?.cancel()
            closeSemesterPreview()
            semesterSchedules.clear()
            semesters.value = null
            nextSchedule.value = null
            isSemestersLoading.value = false
            isNextScheduleLoading.value = false
            previewCacheIdentity = identity
        }
        return identity
    }

    val schoolYear: String
        get() = weekConfig.cachedSemesterKey()?.schoolYear
            ?: weekConfig.storedSchoolYear()
            ?: "2022-2023"

    val schoolTerm: String
        get() = weekConfig.cachedSemesterKey()?.schoolTerm ?: "1"

    val scheduleConfig = MutableLiveData<ScheduleConfigBean?>()
    val scheduleFetchedAt = MutableLiveData<Long?>()
    val isScheduleRefreshing = MutableLiveData(false)
    /** 最近一次后台刷新失败的原因；界面只判断有没有失败（文案由界面自己写，见 ADR 0001 规则 3）。 */
    val scheduleRefreshError = MutableLiveData<AhuError?>(null)
    private var backgroundRefreshJob: Job? = null
    private var backgroundRefreshIdentity: Triple<Boolean, String?, Boolean>? = null
    private var backgroundRefreshRevision = 0L
    private var scheduleRequestRevision = 0L
    private var configRequestRevision = 0L
    private var configLoadJob: Job? = null
    private var cacheLoadJob: Job? = null
    private var cacheLoadIdentity: Triple<Boolean, String?, Boolean>? = null
    val scheduleHolidays = MutableLiveData<Map<LocalDate, ScheduleHoliday>>(emptyMap())
    private var holidayStartDate: LocalDate? = null
    private var holidayWeekCount: Int = 20

    init {
        viewModelScope.launchSafe {
            holidaySource.holidays.collect { scheduleHolidays.value = it }
        }
        // 自定义课程在别的页面增删后，课表页只重读一次缓存（含合并）即可，不必打扰教务。
        viewModelScope.launchSafe {
            customCourseStore.revision.drop(1).collect {
                showCachedSchedule(force = true)
            }
        }
    }

    fun loadHolidays(startDate: LocalDate?, refresh: Boolean = false, weekCount: Int = 20) {
        holidayStartDate = startDate
        holidayWeekCount = weekCount
        if (startDate != null) loadHolidaysForDates(scheduleWeekDates(startDate, weekCount).flatten().toSet(), refresh)
    }

    fun loadHolidaysForDates(dates: Set<LocalDate>, refresh: Boolean = false) {
        if (dates.isEmpty()) return
        viewModelScope.launchSafe {
            // 所有界面共享缓存；后台刷新取得不同的安排后由数据源统一更新。
            holidaySource.load(scheduleHolidayYears(listOf(dates.toList())), refresh)
        }
    }

    // 更新周
    fun changeWeek(week: Int) {
        val configBean = scheduleConfig.value!!
        configBean.week = week
        scheduleConfig.value = configBean
    }

    /**
     * 刷新课表
     */
    fun refreshSchedule(isRefresh: Boolean = false) {
        if (isRefresh) {
            val start = holidayStartDate ?: scheduleConfig.value?.startTime
                ?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
            val today = DebugClock.nowLocalDate()
            val dates = start?.let { scheduleWeekDates(it, holidayWeekCount).flatten().toSet() }.orEmpty() +
                setOf(today, today.plusDays(1))
            loadHolidaysForDates(dates, refresh = true)
            refreshLatestSchedule()
            return
        }
        val identity = previewIdentity()
        val revision = ++scheduleRequestRevision
        viewModelScope.launchSafe {
            if (!session.isLoggedIn() && !scheduleSource.usesMockData()) {
                schedule.value = AhuResult.Failure(AhuError.Unauthorized("请先登录！"))
                return@launchSafe
            }

            val result = scheduleSource.fetch(isRefresh = isRefresh)
            if (revision != scheduleRequestRevision || identity != previewIdentity()) return@launchSafe
            schedule.value = result
            scheduleFetchedAt.value = scheduleSource.fetchedAt()
            if (result.isSuccess) {
                reminders.reschedule()
            }
        }
    }

    fun onScheduleEntered() {
        if (!session.isLoggedIn() && !scheduleSource.usesMockData()) {
            schedule.value = AhuResult.Failure(AhuError.Unauthorized("请先登录！"))
            return
        }

        showCachedSchedule()
        refreshLatestSchedule()
    }

    fun onHomeEntered() {
        if (!session.isLoggedIn() && !scheduleSource.usesMockData()) return
        showCachedSchedule()
    }

    private fun showCachedSchedule(force: Boolean = false) {
        val owner = previewIdentity()
        if (!force && cacheLoadJob?.isActive == true && cacheLoadIdentity == owner) return
        cacheLoadJob?.cancel()
        cacheLoadIdentity = owner
        cacheLoadJob = viewModelScope.launchSafe {
            val (cached, fetchedAt, custom) = withContext(ioDispatcher) {
                Triple(
                    scheduleSource.cached(),
                    scheduleSource.fetchedAt(),
                    customCourseStore.currentCourses()
                )
            }
            if (owner != previewIdentity()) return@launchSafe
            customCourses.value = custom
            if (cached != null && schedule.value?.valueOrNull() != cached) {
                schedule.value = AhuResult.Success(cached)
            }
            scheduleFetchedAt.value = fetchedAt
        }
    }

    private fun refreshLatestSchedule() {
        val identity = previewIdentity()
        if (backgroundRefreshJob?.isActive == true && backgroundRefreshIdentity == identity) return
        backgroundRefreshJob?.cancel()
        backgroundRefreshIdentity = identity
        val revision = ++backgroundRefreshRevision
        val scheduleRevision = ++scheduleRequestRevision
        backgroundRefreshJob = viewModelScope.launchSafe {
            try {
                cacheLoadJob?.join()
                if (revision != backgroundRefreshRevision || scheduleRevision != scheduleRequestRevision ||
                    identity != previewIdentity()
                ) return@launchSafe
                isScheduleRefreshing.value = true
                scheduleRefreshError.value = null
                val refresh = scheduleSource.refreshCache()
                if (revision != backgroundRefreshRevision || scheduleRevision != scheduleRequestRevision ||
                    identity != previewIdentity()
                ) return@launchSafe
                refresh.onSuccess { result ->
                    scheduleFetchedAt.value = result.fetchedAt
                    val displayedSchedule = schedule.value?.valueOrNull()
                    if (displayedSchedule == null ||
                        (result.changed && displayedSchedule != result.schedule)
                    ) {
                        schedule.value = AhuResult.Success(result.schedule)
                    }
                    if (result.changed || displayedSchedule == null) {
                        reminders.reschedule()
                    }
                }.onFailure { error ->
                    scheduleRefreshError.value = error
                    if (schedule.value?.valueOrNull() == null) {
                        schedule.value = AhuResult.Failure(error)
                    }
                }
            } finally {
                if (revision == backgroundRefreshRevision) isScheduleRefreshing.value = false
            }
        }
    }

    fun refreshNextSchedule(isRefresh: Boolean = false) {
        val identity = ensurePreviewIdentity()
        val revision = ++nextRequestRevision
        nextScheduleJob?.cancel()
        nextScheduleJob = viewModelScope.launchSafe {
            isNextScheduleLoading.value = true
            try {
                val result = if (!session.isLoggedIn() && !scheduleSource.usesMockData()) {
                    AhuResult.Failure(AhuError.Unauthorized("请先登录"))
                } else {
                    scheduleSource.next(isRefresh = isRefresh)
                }
                if (revision == nextRequestRevision && identity == previewIdentity()) nextSchedule.value = result
            } finally {
                if (revision == nextRequestRevision) isNextScheduleLoading.value = false
            }
        }
    }

    fun loadSemesters(isRefresh: Boolean = false) {
        val identity = ensurePreviewIdentity()
        if (semestersJob?.isActive == true || (!isRefresh && semesters.value?.isSuccess == true)) return
        val revision = ++catalogueRequestRevision
        semestersJob = viewModelScope.launchSafe {
            isSemestersLoading.value = true
            try {
                val result = if (!session.isLoggedIn() && !scheduleSource.usesMockData()) {
                    AhuResult.Failure(AhuError.Unauthorized("请先登录"))
                } else {
                    scheduleSource.semesters()
                }
                if (revision == catalogueRequestRevision && identity == previewIdentity()) semesters.value = result
            } finally {
                if (revision == catalogueRequestRevision) isSemestersLoading.value = false
            }
        }
    }

    fun loadSemester(semesterId: Int, isRefresh: Boolean = false) {
        val identity = ensurePreviewIdentity()
        val revision = ++semesterRequestRevision
        semesterScheduleJob?.cancel()
        previewedSemesterId.value = semesterId
        val cached = semesterSchedules[semesterId]
        semesterSchedule.value = cached?.let { AhuResult.Success(it) }
        isSemesterScheduleLoading.value = false
        if (cached != null && !isRefresh) return
        semesterScheduleJob = viewModelScope.launchSafe {
            isSemesterScheduleLoading.value = true
            try {
                val result = if (!session.isLoggedIn() && !scheduleSource.usesMockData()) {
                    AhuResult.Failure(AhuError.Unauthorized("请先登录"))
                } else {
                    scheduleSource.semester(semesterId)
                }
                if (revision == semesterRequestRevision && identity == previewIdentity()) {
                    result.valueOrNull()?.let { semesterSchedules[semesterId] = it }
                    semesterSchedule.value = result
                }
            } finally {
                if (revision == semesterRequestRevision) isSemesterScheduleLoading.value = false
            }
        }
    }

    fun closeSemesterPreview() {
        semesterRequestRevision++
        semesterScheduleJob?.cancel()
        previewedSemesterId.value = null
        semesterSchedule.value = null
        isSemesterScheduleLoading.value = false
    }

    fun loadConfig() {
        val identity = previewIdentity()
        val revision = ++configRequestRevision
        configLoadJob?.cancel()
        configLoadJob = viewModelScope.launchSafe {
            val initialConfig = weekConfig.resolveLocalFirst()
            if (revision != configRequestRevision || identity != previewIdentity()) return@launchSafe
            scheduleConfig.value = initialConfig.config
            reminders.reschedule()

            if (!DebugClock.isMocked() && initialConfig.source != ConfigSource.REMOTE) {
                val remoteConfig = runCatching { weekConfig.syncRemote() }.getOrNull()
                if (revision != configRequestRevision || identity != previewIdentity()) return@launchSafe
                remoteConfig?.config?.let {
                    scheduleConfig.value = it
                    reminders.reschedule()
                }
            }
        }
    }

    /**
     * 保存时间
     * @param schoolYear String
     * @param schoolTerm String
     * @param week Int
     */
    fun saveTime(schoolYear: String, schoolTerm: String, week: Int) {
        val semesterKey = weekConfig.buildSemesterKey(schoolYear, schoolTerm)
        // 推算开学日期
        val instance = DebugClock.nowCalendar(Locale.CHINA)
        instance.add(Calendar.DATE, (week - 1) * -7)
        instance.firstDayOfWeek = Calendar.MONDAY
        instance.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        // 修改当前的开学时间和周数
        val configBean = (scheduleConfig.value ?: ScheduleConfigBean()).apply {
            isShowAll = weekConfig.isShowAllCourse()
            isInSemester = true
            startTime = instance.time
            this.week = week
            weekDay = weekConfig.currentWeekDay()
        }
        scheduleConfig.value = configBean

        val startTime = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(instance.time)
        val observedOn = DebugClock.nowLocalDate().toString()
        viewModelScope.launchSafe {
            weekConfig.saveSchoolYearAndTerm(schoolYear, semesterKey)
            weekConfig.saveTermPosition(
                schoolYear = schoolYear,
                schoolTerm = schoolTerm,
                startTime = startTime,
                isInSemester = true,
                observedOn = observedOn
            )
            reminders.reschedule()
        }
    }

    fun clear() {
        scheduleRequestRevision++
        configRequestRevision++
        backgroundRefreshRevision++
        configLoadJob?.cancel()
        cacheLoadJob?.cancel()
        cacheLoadIdentity = null
        backgroundRefreshJob?.cancel()
        backgroundRefreshIdentity = null
        catalogueRequestRevision++
        nextRequestRevision++
        semestersJob?.cancel()
        nextScheduleJob?.cancel()
        closeSemesterPreview()
        semesterSchedules.clear()
        previewCacheIdentity = null
        semesters.value = null
        isSemestersLoading.value = false
        nextSchedule.value = null
        isNextScheduleLoading.value = false
        holidayStartDate = null
        holidayWeekCount = 20
        scheduleHolidays.value = emptyMap()
        schedule.value = AhuResult.Success(emptyList())
        customCourses.value = emptyList()
        scheduleConfig.value = null
        scheduleFetchedAt.value = null
        scheduleRefreshError.value = null
        isScheduleRefreshing.value = false
    }
}
