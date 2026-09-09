package com.ahu.ahutong.ui.state

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.notification.CourseReminderScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * @Author SinkDev
 * @Date 2021/7/27-19:16
 * @Email 468766131@qq.com
 */
class ScheduleViewModel () : ViewModel() {
    val TAG = "ScheduleViewModel"
    val schedule = MutableLiveData<Result<List<Course>>>()
    val nextSchedule = MutableLiveData<Result<List<Course>>>()

    val schoolYear: String
        get() = CurrentWeekResolver.getCachedSemesterKey()?.schoolYear
            ?: AHUCache.getSchoolYear()
            ?: "2022-2023"

    val schoolTerm: String
        get() = CurrentWeekResolver.getCachedSemesterKey()?.schoolTerm ?: "1"

    val scheduleConfig = MutableLiveData<ScheduleConfigBean?>()
    val scheduleFetchedAt = MutableLiveData<Long?>()
    val isScheduleRefreshing = MutableLiveData(false)
    val scheduleRefreshError = MutableLiveData<String?>(null)
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
    fun refreshSchedule(isRefresh:Boolean = false) {
        if (isRefresh) {
            refreshLatestSchedule()
            return
        }
        viewModelScope.launchSafe {
            if (!AHUCache.isLogin() && !AHUCache.getMockData()) {
                schedule.value = Result.failure(Throwable("请先登录！"))
                return@launchSafe
            }

            val result = AHURepository.getSchedule(isRefresh = isRefresh)
            schedule.value = result
            scheduleFetchedAt.value = AHURepository.getScheduleFetchedAt()
            if (result.isSuccess) {
                CourseReminderScheduler.reschedule(AHUApplication.getApp())
            }
        }
    }

    fun onScheduleEntered() {
        if (!AHUCache.isLogin() && !AHUCache.getMockData()) {
            schedule.value = Result.failure(Throwable("请先登录！"))
            return
        }

        AHURepository.getCachedSchedule()?.let { cached ->
            if (schedule.value?.getOrNull() != cached) {
                schedule.value = Result.success(cached)
            }
        }
        scheduleFetchedAt.value = AHURepository.getScheduleFetchedAt()
        refreshLatestSchedule()
    }

    private fun refreshLatestSchedule() {
        if (backgroundRefreshJob?.isActive == true) return
        backgroundRefreshJob = viewModelScope.launchSafe {
            isScheduleRefreshing.value = true
            scheduleRefreshError.value = null
            try {
                val refresh = AHURepository.refreshScheduleCache()
                refresh.onSuccess { result ->
                    scheduleFetchedAt.value = result.fetchedAt
                    if (result.changed || schedule.value?.getOrNull() == null) {
                        schedule.value = Result.success(result.schedule)
                        CourseReminderScheduler.reschedule(AHUApplication.getApp())
                    }
                }.onFailure { error ->
                    scheduleRefreshError.value = error.message ?: "获取最新课表失败"
                    if (schedule.value?.getOrNull() == null) {
                        schedule.value = Result.failure(error)
                    }
                }
            } finally {
                isScheduleRefreshing.value = false
            }
        }
    }

    fun refreshNextSchedule(isRefresh: Boolean = false) {
        viewModelScope.launchSafe {
            if (!AHUCache.isLogin() && !AHUCache.getMockData()) {
                nextSchedule.value = Result.failure(Throwable("请先登录"))
                return@launchSafe
            }

            nextSchedule.value = AHURepository.getNextSchedule(isRefresh = isRefresh)
        }
    }

    fun loadConfig() {
        viewModelScope.launchSafe {
            val initialConfig = withContext(Dispatchers.IO) {
                CurrentWeekResolver.resolveLocalFirst()
            }
            scheduleConfig.postValue(initialConfig.config)
            CourseReminderScheduler.reschedule(AHUApplication.getApp())

            if (!DebugClock.isMocked() && initialConfig.source != CurrentWeekResolver.Source.REMOTE) {
                val remoteConfig = withContext(Dispatchers.IO) {
                    runCatching { CurrentWeekResolver.syncRemoteConfig() }.getOrNull()
                }
                remoteConfig?.config?.let {
                    scheduleConfig.postValue(it)
                    CourseReminderScheduler.reschedule(AHUApplication.getApp())
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
        val semesterKey = CurrentWeekResolver.buildSemesterKey(schoolYear, schoolTerm)
        AHUCache.saveSchoolYear(schoolYear)
        AHUCache.saveSchoolTerm(semesterKey)
        // 推算开学日期
        val instance = DebugClock.nowCalendar(Locale.CHINA)
        instance.add(Calendar.DATE, (week - 1) * -7)
        instance.firstDayOfWeek = Calendar.MONDAY
        instance.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        // 修改当前的开学时间和周数
        val configBean = (scheduleConfig.value ?: ScheduleConfigBean()).apply {
            isShowAll = AHUCache.isShowAllCourse()
            isInSemester = true
            startTime = instance.time
            this.week = week
            weekDay = CurrentWeekResolver.getCurrentWeekDay()
        }
        scheduleConfig.value = configBean
        AHUCache.saveSchoolTermStartTime(
            schoolYear,
            schoolTerm,
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(instance.time)
        )
        AHUCache.saveSchoolTermInSemester(
            schoolYear = schoolYear,
            schoolTerm = schoolTerm,
            isInSemester = true,
            observedOn = DebugClock.nowLocalDate().toString()
        )
        CourseReminderScheduler.reschedule(AHUApplication.getApp())
    }

    companion object {
        val timetable by lazy {
            mapOf(
                1 to "08:00-08:45",
                2 to "08:50-09:35",
                3 to "09:50-10:35",
                4 to "10:40-11:25",
                5 to "11:30-12:15",
                6 to "14:00-14:45",
                7 to "14:50-15:35",
                8 to "15:50-16:35",
                9 to "16:40-17:25",
                10 to "17:30-18:15",
                11 to "19:00-19:45",
                12 to "19:50-20:35",
                13 to "20:40-21:25"
            )
        }

        /** Pre-parsed once because the home timeline reads these ranges during composition. */
        private val timetableMinuteRanges by lazy {
            timetable.mapValues { (_, range) ->
                parseClockMinutes(range.substringBefore('-'))..
                    parseClockMinutes(range.substringAfter('-'))
            }
        }

        private fun parseClockMinutes(clock: String): Int {
            val separator = clock.indexOf(':')
            require(separator > 0 && separator < clock.lastIndex) { "Invalid clock: $clock" }
            return clock.substring(0, separator).toInt() * 60 +
                clock.substring(separator + 1).toInt()
        }

        fun getCourseTimeRangeInMinutes(course: Course): IntRange {
            val firstSection = timetableMinuteRanges.getValue(course.startTime)
            val lastSection = timetableMinuteRanges.getValue(course.startTime + course.length - 1)
            return firstSection.first..lastSection.last
        }
    }

    fun clear() {
        backgroundRefreshJob?.cancel()
        schedule.value = Result.success(emptyList())
        scheduleConfig.value = null
        scheduleFetchedAt.value = null
        scheduleRefreshError.value = null
        isScheduleRefreshing.value = false
    }
}
