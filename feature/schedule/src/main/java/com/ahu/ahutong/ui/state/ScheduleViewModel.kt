package com.ahu.ahutong.ui.state

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.ScheduleConfigSource
import com.ahu.ahutong.data.schedule.ScheduleLocalStore
import com.ahu.ahutong.data.schedule.ScheduleReminderCoordinator
import com.ahu.ahutong.data.schedule.ScheduleRepository
import com.ahu.ahutong.data.schedule.ScheduleWeekResolver
import com.ahu.ahutong.data.schedule.canLoadSchedule
import com.ahu.ahutong.ext.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @Author SinkDev
 * @Date 2021/7/27-19:16
 * @Email 468766131@qq.com
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleLocalStore: ScheduleLocalStore,
    private val scheduleWeekResolver: ScheduleWeekResolver,
    private val scheduleReminderCoordinator: ScheduleReminderCoordinator,
) : ViewModel() {
    val TAG = "ScheduleViewModel"
    val schedule = MutableLiveData<Result<List<Course>>>()
    val nextSchedule = MutableLiveData<Result<List<Course>>>()

    val schoolYear: String
        get() = scheduleWeekResolver.getCachedSemesterKey()?.schoolYear
            ?: scheduleLocalStore.getSchoolYear()
            ?: "2022-2023"

    val schoolTerm: String
        get() = scheduleWeekResolver.getCachedSemesterKey()?.schoolTerm ?: "1"

    val scheduleConfig = MutableLiveData<ScheduleConfigBean?>()

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
        viewModelScope.launchSafe {
            withContext(Dispatchers.Main) {
                if (!scheduleLocalStore.canLoadSchedule()) {
                    schedule.value = Result.failure(Throwable("请先登录！"))
                    return@withContext
                }

                val result = scheduleRepository.getSchedule(isRefresh = isRefresh).toKotlinResult()
                schedule.value = result
                if (result.isSuccess) {
                    scheduleReminderCoordinator.reschedule()
                }
            }
        }
    }

    fun refreshNextSchedule(isRefresh: Boolean = false) {
        viewModelScope.launchSafe {
            withContext(Dispatchers.Main) {
                if (!scheduleLocalStore.canLoadSchedule()) {
                    nextSchedule.value = Result.failure(Throwable("请先登录"))
                    return@withContext
                }

                nextSchedule.value =
                    scheduleRepository.getNextSchedule(isRefresh = isRefresh).toKotlinResult()
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launchSafe {
            val initialConfig = withContext(Dispatchers.IO) {
                scheduleWeekResolver.resolveLocalFirst()
            }
            scheduleConfig.postValue(initialConfig.config)
            scheduleReminderCoordinator.reschedule()

            if (!scheduleWeekResolver.isDebugClockMocked() &&
                initialConfig.source != ScheduleConfigSource.REMOTE
            ) {
                val remoteConfig = withContext(Dispatchers.IO) {
                    runCatching { scheduleWeekResolver.syncRemoteConfig() }.getOrNull()
                }
                remoteConfig?.config?.let {
                    scheduleConfig.postValue(it)
                    scheduleReminderCoordinator.reschedule()
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
        val semesterKey = scheduleWeekResolver.buildSemesterKey(schoolYear, schoolTerm)
        scheduleLocalStore.saveSchoolYear(schoolYear)
        scheduleLocalStore.saveSchoolTerm(semesterKey)
        // 推算开学日期
        val instance = scheduleWeekResolver.nowCalendar(Locale.CHINA)
        instance.add(Calendar.DATE, (week - 1) * -7)
        instance.firstDayOfWeek = Calendar.MONDAY
        instance.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        // 修改当前的开学时间和周数
        val configBean = (scheduleConfig.value ?: ScheduleConfigBean()).apply {
            isShowAll = scheduleLocalStore.isShowAllCourse()
            startTime = instance.time
            this.week = week
            weekDay = scheduleWeekResolver.getCurrentWeekDay()
        }
        scheduleConfig.value = configBean
        scheduleLocalStore.saveSchoolTermStartTime(
            schoolYear,
            schoolTerm,
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(instance.time)
        )
        scheduleReminderCoordinator.reschedule()
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

        /**
         * @param from "HH:mm-HH:mm"
         * @param to "HH:mm-HH:mm"
         */
        private fun getTimeRangeInMinutes(
            from: String,
            to: String = from
        ): IntRange {
            val format = SimpleDateFormat("HH:mm", Locale.CHINA)
            val start = format.parse(from.take(5)).let {
                val calendar = Calendar.getInstance(Locale.CHINA)
                calendar.time = it!!
                calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            }
            val end = format.parse(to.takeLast(5)).let {
                val calendar = Calendar.getInstance(Locale.CHINA)
                calendar.time = it!!
                calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            }
            return start..end
        }

        fun getCourseTimeRangeInMinutes(course: Course): IntRange {
            return getTimeRangeInMinutes(
                from = timetable.getValue(course.startTime),
                to = timetable.getValue(course.startTime + course.length - 1)
            )
        }
    }

    fun clear() {
        schedule.value = Result.success(emptyList())
        scheduleConfig.value = null
    }
}
