package com.ahu.ahutong.testing

import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.ChaoxingStore
import com.ahu.ahutong.data.xuexiaotong.Course
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Work

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
    var syncCourseProgressCount = 0
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

    override suspend fun syncWorks(onProgress: (Int, Int, String) -> Unit): List<Work> {
        syncWorksCount++
        syncFailure?.let { throw it }
        onProgress(1, 1, "同步完成")
        return works
    }

    override suspend fun syncCourseProgress(
        onProgress: (Int, Int, String) -> Unit
    ): List<CourseProgress> {
        syncCourseProgressCount++
        syncFailure?.let { throw it }
        onProgress(1, 1, "同步完成")
        return courseProgress
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
    var sendTestCount = 0
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

    override fun sendTest() {
        sendTestCount++
    }
}
