package com.ahu.ahutong.testing

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.schedule.ConfigSource
import com.ahu.ahutong.data.schedule.ResolvedConfig
import com.ahu.ahutong.data.schedule.ScheduleRefreshResult
import com.ahu.ahutong.data.schedule.ScheduleSource
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.data.schedule.ScheduleWeekConfig
import com.ahu.ahutong.data.schedule.SemesterKey
import com.ahu.ahutong.data.session.SessionIdentity

/**
 * 课表 feature 的 fake 适配器：内存里的一份课表、一份学期配置与一份提醒计数。
 *
 * 它们让【「刷新失败时不要重排提醒」「本地已确认过就不再问远端」】这类行为第一次可被断言——
 * 这些差别在界面上看不出来，只有真机点一遍才能发现。
 */
class FakeScheduleSource : ScheduleSource {

    var mockData = false
    var cachedSchedule: List<Course>? = null
    var fetchedAtValue: Long? = null
    var fetchResult: AhuResult<List<Course>> = AhuResult.Success(emptyList())
    var refreshResult: AhuResult<ScheduleRefreshResult> =
        AhuResult.Success(
            ScheduleRefreshResult(schedule = emptyList(), changed = false, fetchedAt = 0L)
        )
    var nextResult: AhuResult<List<Course>> = AhuResult.Success(emptyList())
    var semestersResult: AhuResult<List<ScheduleSemester>> = AhuResult.Success(emptyList())
    var semesterResult: AhuResult<List<Course>> = AhuResult.Success(emptyList())
    var fetchLoader: (suspend () -> AhuResult<List<Course>>)? = null
    var refreshLoader: (suspend () -> AhuResult<ScheduleRefreshResult>)? = null
    var semesterLoader: (suspend (Int) -> AhuResult<List<Course>>)? = null
    val semesterRequests = mutableListOf<Int>()
    var semestersCount = 0
        private set

    var fetchCount = 0
        private set
    var refreshCount = 0
        private set
    var nextCount = 0
        private set

    override fun usesMockData(): Boolean = mockData

    override fun cached(): List<Course>? = cachedSchedule

    override fun fetchedAt(): Long? = fetchedAtValue

    override suspend fun fetch(isRefresh: Boolean): AhuResult<List<Course>> {
        fetchCount++
        return fetchLoader?.invoke() ?: fetchResult
    }

    override suspend fun refreshCache(): AhuResult<ScheduleRefreshResult> {
        refreshCount++
        return refreshLoader?.invoke() ?: refreshResult
    }

    override suspend fun next(isRefresh: Boolean): AhuResult<List<Course>> {
        nextCount++
        return nextResult
    }

    override suspend fun semesters(): AhuResult<List<ScheduleSemester>> {
        semestersCount++
        return semestersResult
    }

    override suspend fun semester(semesterId: Int): AhuResult<List<Course>> {
        semesterRequests += semesterId
        return semesterLoader?.invoke(semesterId) ?: semesterResult
    }
}

/** [ScheduleWeekConfig] 的 fake：[calls] 按发生顺序记下每一步，顺序本身也是被测行为。 */
class FakeScheduleWeekConfig : ScheduleWeekConfig {

    var semesterKey: SemesterKey? = null
    var schoolYear: String? = null
    var showAllCourse = true
    var localResult: ResolvedConfig = ResolvedConfig(ScheduleConfigBean(), ConfigSource.LOCAL)
    var remoteResult: ResolvedConfig? = null
    var mockedTime = false
    var localLoader: (suspend () -> ResolvedConfig)? = null
    var remoteLoader: (suspend () -> ResolvedConfig?)? = null

    val calls = mutableListOf<String>()

    override fun cachedSemesterKey(): SemesterKey? = semesterKey

    override fun storedSchoolYear(): String? = schoolYear

    override fun currentWeekDay(): Int = 3

    override fun buildSemesterKey(schoolYear: String, schoolTerm: String): String {
        calls += "build"
        return "$schoolYear-$schoolTerm"
    }

    override fun isShowAllCourse(): Boolean = showAllCourse

    override suspend fun resolveLocalFirst(): ResolvedConfig {
        calls += "resolve"
        return localLoader?.invoke() ?: localResult
    }

    override suspend fun syncRemote(): ResolvedConfig? {
        calls += "remote"
        return remoteLoader?.invoke() ?: remoteResult
    }

    override suspend fun saveSchoolYearAndTerm(schoolYear: String, semesterKey: String) {
        calls += "year"
        calls += "term"
    }

    override suspend fun saveTermPosition(
        schoolYear: String,
        schoolTerm: String,
        startTime: String,
        isInSemester: Boolean,
        observedOn: String
    ) {
        calls += "position"
    }
}

/** [SessionIdentity] 的 fake：登录态可推着走。 */
class FakeSessionIdentity(
    var loggedIn: Boolean = true,
    var user: User? = null
) : SessionIdentity {

    override fun isLoggedIn(): Boolean = loggedIn

    override fun currentUser(): User? = user
}

/** [CourseReminderControl] 的 fake：只记下命令。 */
class FakeCourseReminderControl : CourseReminderControl {

    var rescheduleCount = 0
        private set

    var cancelCount = 0
        private set

    var cancelActiveCount = 0
        private set

    var openSettingsCount = 0
        private set

    override fun reschedule() {
        rescheduleCount++
    }

    override fun cancel() {
        cancelCount++
    }

    override fun cancelActiveReminder() {
        cancelActiveCount++
    }

    override fun openSystemSettings() {
        openSettingsCount++
    }

    override fun canScheduleExactReminders(): Boolean = true

    override fun openExactAlarmSettings() = Unit
}
