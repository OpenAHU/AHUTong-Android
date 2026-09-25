package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.xuexiaotong.ChaoxingApi
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.ChaoxingStore
import com.ahu.ahutong.data.xuexiaotong.Course
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.reminder.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学习通三个端口的生产适配器：会话与同步转给 ChaoxingApi，本地数据转给 Store，
 * 提醒转给 ReminderScheduler。
 *
 * 三个实现都在 :app 里（协议客户端、静态存储、提醒调度），学习通界面因此只认识端口。
 */
@Singleton
class AppChaoxingSession @Inject constructor() : ChaoxingSession {

    /** Cookie 持久化在磁盘上，因此共享一个客户端实例是安全的（原先是每次组合新建）。 */
    private val api by lazy { ChaoxingApi(AppEnvironmentHolder.context()) }

    override fun hasSession(): Boolean = api.hasSession()

    override fun cookieHeader(): String = Store.getCookie()

    override suspend fun loginByPassword(account: String, password: String) {
        // 登录成功后把凭据记在本机：静默重登要用它（原先由登录界面自己调 Store.saveCredential）。
        api.loginByPassword(account, password)
        Store.saveCredential(account, password)
    }

    override fun clearSession() = api.clearSession()

    override suspend fun silentRelogin() {
        api.silentRelogin()
    }

    override suspend fun syncWorks(onProgress: (Int, Int, String) -> Unit): List<Work> =
        api.syncAllWorks(object : ChaoxingApi.ProgressListener {
            override fun onProgress(done: Int, total: Int, message: String) {
                onProgress(done, total, message)
            }
        })

    override suspend fun syncCourseProgress(
        onProgress: (Int, Int, String) -> Unit
    ): List<CourseProgress> =
        api.syncCourseProgress(object : ChaoxingApi.ProgressListener {
            override fun onProgress(done: Int, total: Int, message: String) {
                onProgress(done, total, message)
            }
        })
}

@Singleton
class AppChaoxingStore @Inject constructor() : ChaoxingStore {

    override fun works(): List<Work> = Store.getWorks()

    override fun courses(): List<Course> = Store.getCourses()

    override fun courseProgress(): List<CourseProgress> = Store.getCourseProgress()

    override fun lastSync(): Long = Store.getLastSync()

    override fun remindSetting(): RemindSetting = Store.getRemindSetting()

    override fun customEvents(): List<CustomEvent> = Store.getCustomEvents()

    override fun showDone(): Boolean = Store.getShowDone()

    override fun doneGray(): Boolean = Store.getDoneGray()

    override fun showEmptyCourses(): Boolean = Store.getShowEmptyCourses()

    override fun saveShowDone(value: Boolean) = Store.saveShowDone(value)

    override fun saveDoneGray(value: Boolean) = Store.saveDoneGray(value)

    override fun saveShowEmptyCourses(value: Boolean) = Store.saveShowEmptyCourses(value)

    override fun saveLastSync(at: Long) = Store.saveLastSync(at)

    override fun saveRemindSetting(setting: RemindSetting) = Store.saveRemindSetting(setting)

    override fun saveCustomEvents(events: List<CustomEvent>) = Store.saveCustomEvents(events)

    override fun clearLoginData() = Store.clearLoginData()
}

@Singleton
class AppChaoxingReminders @Inject constructor() : ChaoxingReminders {

    private val context get() = AppEnvironmentHolder.context()

    override fun cancelAll() = ReminderScheduler.cancelAll(context)

    override fun scheduleAll() = ReminderScheduler.scheduleAll(context)

    override fun rescheduleAll() = ReminderScheduler.rescheduleAll(context)

    override fun sendTest() {
        ReminderScheduler.sendTest(context)
    }
}
