package com.ahu.ahutong.testing

import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.ChaoxingStore
import com.ahu.ahutong.data.xuexiaotong.Course
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 学习通三个端口的 fake，供 feature 自己的单测驱动 ViewModel：
 * 会话（登录态与同步动作）、本地存储（十六个读写）、提醒（四条命令）。
 */
class FakeChaoxingSession(
    var hasSession: Boolean = true,
    var works: List<Work> = emptyList(),
    var courseProgress: List<CourseProgress> = emptyList(),
    var syncFailure: Exception? = null,
    var loginFailure: Exception? = null
) : ChaoxingSession {

    val loginCalls = mutableListOf<Pair<String, String>>()

    var clearSessionCount = 0
        private set
    var silentReloginCount = 0
        private set
    var syncWorksCount = 0
        private set

    override fun hasSession(): Boolean = hasSession

    override fun cookieHeader(): String = ""

    override suspend fun loginByPassword(account: String, password: String) {
        loginCalls += account to password
        loginFailure?.let { throw it }
    }

    override fun clearSession() {
        clearSessionCount++
        hasSession = false
    }

    override suspend fun silentRelogin() {
        silentReloginCount++
    }

    override suspend fun syncWorks(
        full: Boolean,
        semesterRange: LongRange?,
        onCourseListKnown: ((Int) -> Unit)?,
        onWorkListKnown: ((Int) -> Unit)?,
        onProgress: (Int, Int, String) -> Unit
    ): List<Work> {
        syncWorksCount++
        syncFailure?.let { throw it }
        onCourseListKnown?.invoke(1)
        onWorkListKnown?.invoke(works.size)
        onProgress(1, 1, "同步完成")
        return works
    }

    override suspend fun syncCourseProgress(
        semesterRange: LongRange?,
        onProgress: (Int, Int, String) -> Unit
    ): List<CourseProgress> {
        syncFailure?.let { throw it }
        onProgress(1, 1, "同步完成")
        return courseProgress
    }
}

class FakeChaoxingSyncService(
    private val session: ChaoxingSession,
    private val store: ChaoxingStore,
    private val reminders: ChaoxingReminders
) : com.ahu.ahutong.data.xuexiaotong.ChaoxingSyncService {

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(
        com.ahu.ahutong.data.xuexiaotong.XuexiaotongSyncState()
    )
    override val state = mutableState.asStateFlow()
    private val fullEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val fullSyncEnabled = fullEnabled.asStateFlow()

    var connectedCount = 0
        private set

    override fun onConnected() {
        connectedCount++
    }

    override fun startWorksSync(mode: com.ahu.ahutong.data.xuexiaotong.XuexiaotongSyncMode): Boolean {
        mutableState.update { it.copy(worksRunning = true, worksMessage = "正在获取作业列表...") }
        scope.launch {
            try {
                session.silentRelogin()
                val works = session.syncWorks(
                    full = mode == com.ahu.ahutong.data.xuexiaotong.XuexiaotongSyncMode.FULL,
                    semesterRange = null,
                    onCourseListKnown = null,
                    onWorkListKnown = null,
                    onProgress = { done, total, message ->
                        mutableState.update {
                            it.copy(worksDone = done, worksTotal = total, worksMessage = message)
                        }
                    }
                )
                (store as? FakeChaoxingStore)?.let {
                    it.works = works
                    it.lastSync = System.currentTimeMillis()
                }
                mutableState.update {
                    it.copy(
                        worksRunning = false,
                        worksMessage = "同步完成",
                        worksError = null,
                        worksRevision = it.worksRevision + 1
                    )
                }
                reminders.rescheduleAll()
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(worksRunning = false, worksMessage = "同步失败", worksError = failure.message)
                }
                reminders.rescheduleAll()
            }
        }
        return true
    }

    override fun startCourseProgressSync(): Boolean {
        return true
    }

    override fun setFullSyncEnabled(enabled: Boolean) {
        fullEnabled.value = enabled
    }

    override fun cancel() {
        mutableState.update { it.copy(worksRunning = false, progressRunning = false) }
    }
}

class FakeChaoxingStore(
    var works: List<Work> = emptyList(),
    var courses: List<Course> = emptyList(),
    var courseProgress: List<CourseProgress> = emptyList(),
    var lastSync: Long = 0L,
    var remindSetting: RemindSetting = RemindSetting(),
    var customEvents: List<CustomEvent> = emptyList(),
    var showDone: Boolean = false,
    var doneGray: Boolean = false,
    var showEmptyCourses: Boolean = false
) : ChaoxingStore {

    var clearLoginDataCount = 0
        private set
    var savedRemindSetting: RemindSetting? = null
        private set

    override fun works(): List<Work> = works
    override fun courses(): List<Course> = courses
    override fun courseProgress(): List<CourseProgress> = courseProgress
    override fun lastSync(): Long = lastSync
    override fun remindSetting(): RemindSetting = remindSetting
    override fun customEvents(): List<CustomEvent> = customEvents
    override fun showDone(): Boolean = showDone
    override fun doneGray(): Boolean = doneGray
    override fun showEmptyCourses(): Boolean = showEmptyCourses

    override fun saveShowDone(value: Boolean) {
        showDone = value
    }

    override fun saveDoneGray(value: Boolean) {
        doneGray = value
    }

    override fun saveShowEmptyCourses(value: Boolean) {
        showEmptyCourses = value
    }

    override fun saveLastSync(at: Long) {
        lastSync = at
    }

    override fun saveRemindSetting(setting: RemindSetting) {
        savedRemindSetting = setting
        remindSetting = setting
    }

    override fun saveCustomEvents(events: List<CustomEvent>) {
        customEvents = events
    }

    override fun clearLoginData() {
        clearLoginDataCount++
        works = emptyList()
        courses = emptyList()
        courseProgress = emptyList()
        lastSync = 0L
        customEvents = emptyList()
    }
}

class FakeChaoxingReminders : ChaoxingReminders {

    var cancelCount = 0
        private set
    var scheduleCount = 0
        private set
    var rescheduleCount = 0
        private set

    override fun cancelAll() {
        cancelCount++
    }

    override fun scheduleAll() {
        scheduleCount++
    }

    override fun rescheduleAll() {
        rescheduleCount++
    }

    override fun sendTest() = Unit
}
