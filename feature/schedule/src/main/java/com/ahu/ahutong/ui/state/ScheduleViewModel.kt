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
import com.ahu.ahutong.data.schedule.ScheduleSource
import com.ahu.ahutong.data.schedule.ScheduleWeekConfig
import com.ahu.ahutong.data.session.SessionIdentity
import com.ahu.ahutong.ext.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * 课表页的状态。
 *
 * 四个协作方都是接口：课表数据（[ScheduleSource]）、学期与周次（[ScheduleWeekConfig]）、
 * 登录态（[SessionIdentity]）与提醒重算（[CourseReminderControl]）。
 * 协议、缓存键、提醒调度与登录态存储因此都不在这个类里——它们由 :app 的适配器接上。
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleSource: ScheduleSource,
    private val weekConfig: ScheduleWeekConfig,
    private val session: SessionIdentity,
    private val reminders: CourseReminderControl
) : ViewModel() {

    val TAG = "ScheduleViewModel"
    val schedule = MutableLiveData<AhuResult<List<Course>>>()
    val nextSchedule = MutableLiveData<AhuResult<List<Course>>>()

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
            refreshLatestSchedule()
            return
        }
        viewModelScope.launchSafe {
            if (!session.isLoggedIn() && !scheduleSource.usesMockData()) {
                schedule.value = AhuResult.Failure(AhuError.Unauthorized("请先登录！"))
                return@launchSafe
            }

            val result = scheduleSource.fetch(isRefresh = isRefresh)
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

    private fun showCachedSchedule() {
        scheduleSource.cached()?.let { cached ->
            if (schedule.value?.valueOrNull() != cached) {
                schedule.value = AhuResult.Success(cached)
            }
        }
        scheduleFetchedAt.value = scheduleSource.fetchedAt()
    }

    private fun refreshLatestSchedule() {
        if (backgroundRefreshJob?.isActive == true) return
        backgroundRefreshJob = viewModelScope.launchSafe {
            isScheduleRefreshing.value = true
            scheduleRefreshError.value = null
            try {
                val refresh = scheduleSource.refreshCache()
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
                isScheduleRefreshing.value = false
            }
        }
    }

    fun refreshNextSchedule(isRefresh: Boolean = false) {
        viewModelScope.launchSafe {
            if (!session.isLoggedIn() && !scheduleSource.usesMockData()) {
                nextSchedule.value = AhuResult.Failure(AhuError.Unauthorized("请先登录"))
                return@launchSafe
            }

            nextSchedule.value = scheduleSource.next(isRefresh = isRefresh)
        }
    }

    fun loadConfig() {
        viewModelScope.launchSafe {
            val initialConfig = weekConfig.resolveLocalFirst()
            scheduleConfig.postValue(initialConfig.config)
            reminders.reschedule()

            if (!DebugClock.isMocked() && initialConfig.source != ConfigSource.REMOTE) {
                val remoteConfig = runCatching { weekConfig.syncRemote() }.getOrNull()
                remoteConfig?.config?.let {
                    scheduleConfig.postValue(it)
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
        backgroundRefreshJob?.cancel()
        schedule.value = AhuResult.Success(emptyList())
        scheduleConfig.value = null
        scheduleFetchedAt.value = null
        scheduleRefreshError.value = null
        isScheduleRefreshing.value = false
    }
}
