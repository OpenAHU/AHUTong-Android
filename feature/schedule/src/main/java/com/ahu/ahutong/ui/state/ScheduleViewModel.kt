package com.ahu.ahutong.ui.state

import android.content.Context
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
import com.ahu.ahutong.feature.schedule.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
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
                    schedule.value = Result.failure(
                        Throwable(appContext.getString(R.string.please_login_exclamation))
                    )
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
                    nextSchedule.value = Result.failure(
                        Throwable(appContext.getString(R.string.please_login))
                    )
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
        val timetable get() = com.ahu.ahutong.data.schedule.CourseTimetable.timetable

        fun getCourseTimeRangeInMinutes(course: Course): IntRange =
            com.ahu.ahutong.data.schedule.CourseTimetable.getCourseTimeRangeInMinutes(course)
    }

    fun clear() {
        schedule.value = Result.success(emptyList())
        scheduleConfig.value = null
    }
}
