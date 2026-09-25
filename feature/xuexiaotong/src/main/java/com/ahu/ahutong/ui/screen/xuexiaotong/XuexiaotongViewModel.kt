package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Qualifier
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.ChaoxingStore
import com.ahu.ahutong.data.xuexiaotong.Course
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Work
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyncProgress(val done: Int = 0, val total: Int = 0, val message: String = "")

/** IO 调度器限定符：生产注入 Dispatchers.IO，测试注入测试调度器，让同步路径可被用例驱动。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class XuexiaotongDispatcher

@HiltViewModel
class XuexiaotongViewModel @Inject constructor(
    private val session: ChaoxingSession,
    private val store: ChaoxingStore,
    private val reminders: ChaoxingReminders,
    @XuexiaotongDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    fun cookieHeader(): String = session.cookieHeader()

    private val _loggedIn = MutableStateFlow(session.hasSession())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _works = MutableStateFlow<List<Work>>(store.works())
    val works: StateFlow<List<Work>> = _works.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(store.courses())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _progress = MutableStateFlow<List<CourseProgress>>(store.courseProgress())
    val progress: StateFlow<List<CourseProgress>> = _progress.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val _courseSyncing = MutableStateFlow(false)
    val courseSyncing: StateFlow<Boolean> = _courseSyncing.asStateFlow()

    private val _courseSyncProgress = MutableStateFlow(SyncProgress())
    val courseSyncProgress: StateFlow<SyncProgress> = _courseSyncProgress.asStateFlow()

    private val _lastSync = MutableStateFlow(store.lastSync())
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _remindSetting = MutableStateFlow(store.remindSetting())
    val remindSetting: StateFlow<RemindSetting> = _remindSetting.asStateFlow()

    private val _customEvents = MutableStateFlow<List<CustomEvent>>(store.customEvents())
    val customEvents: StateFlow<List<CustomEvent>> = _customEvents.asStateFlow()

    private val _showDone = MutableStateFlow(store.showDone())
    val showDone: StateFlow<Boolean> = _showDone.asStateFlow()

    private val _doneGray = MutableStateFlow(store.doneGray())
    val doneGray: StateFlow<Boolean> = _doneGray.asStateFlow()

    private val _showEmptyCourses = MutableStateFlow(store.showEmptyCourses())
    val showEmptyCourses: StateFlow<Boolean> = _showEmptyCourses.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun consumeSnackbar() { _snackbar.value = null }

    fun showMsg(msg: String) { _snackbar.value = msg }

    fun refreshState() {
        _loggedIn.value = session.hasSession()
        _works.value = store.works()
        _courses.value = store.courses()
        _progress.value = store.courseProgress()
        _lastSync.value = store.lastSync()
        _remindSetting.value = store.remindSetting()
        _customEvents.value = store.customEvents()
        _showDone.value = store.showDone()
        _doneGray.value = store.doneGray()
        _showEmptyCourses.value = store.showEmptyCourses()
    }

    fun toggleShowDone() {
        val v = !_showDone.value
        store.saveShowDone(v)
        _showDone.value = v
    }

    fun toggleDoneGray() {
        val v = !_doneGray.value
        store.saveDoneGray(v)
        _doneGray.value = v
    }

    fun toggleShowEmptyCourses() {
        val v = !_showEmptyCourses.value
        store.saveShowEmptyCourses(v)
        _showEmptyCourses.value = v
    }

    fun onLoginSuccess() {
        _loggedIn.value = true
        refreshState()
        syncWorks()
    }

    /**
     * 登录一次：失败时抛异常，文案由界面展示（与迁移前一致）。
     *
     * 界面因此不再认识 [com.ahu.ahutong.data.xuexiaotong.Store]：凭据的保存是登录的一部分，
     * 已随端口实现（见 :app 的 AppChaoxingSession）。
     */
    suspend fun login(account: String, password: String) {
        session.loginByPassword(account, password)
    }

    fun logout() {
        _loggedIn.value = false
        viewModelScope.coroutineContext.cancelChildren()
        reminders.cancelAll()
        session.clearSession()
        store.clearLoginData()
        reminders.scheduleAll()
        _works.value = emptyList()
        _courses.value = emptyList()
        _progress.value = emptyList()
        _lastSync.value = 0L
        _syncProgress.value = SyncProgress()
        _courseSyncProgress.value = SyncProgress()
        _syncing.value = false
        _courseSyncing.value = false
    }

    fun syncWorks() {
        if (!_loggedIn.value || _syncing.value || _courseSyncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncProgress.value = SyncProgress(message = "正在同步作业...")
            try {
                val works = withContext(ioDispatcher) {
                    session.silentRelogin()
                    session.syncWorks { done, total, message ->
                        _syncProgress.value = SyncProgress(done, total, message)
                    }
                }
                _works.value = works
                _courses.value = store.courses()
                _syncProgress.value = SyncProgress(message = "同步完成")
                store.saveLastSync(System.currentTimeMillis())
                _lastSync.value = store.lastSync()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _syncProgress.value = SyncProgress(message = exception.message ?: "同步失败")
            } finally {
                _syncing.value = false
                if (_loggedIn.value) {
                    reminders.rescheduleAll()
                } else {
                    clearRemoteStateAfterLogout()
                }
            }
        }
    }

    fun syncCourseProgress() {
        if (!_loggedIn.value || _syncing.value || _courseSyncing.value) return
        viewModelScope.launch {
            _courseSyncing.value = true
            _courseSyncProgress.value = SyncProgress(message = "正在同步课程进度...")
            try {
                val list = withContext(ioDispatcher) {
                    session.silentRelogin()
                    session.syncCourseProgress { done, total, message ->
                        _courseSyncProgress.value = SyncProgress(done, total, message)
                    }
                }
                _progress.value = list
                _courseSyncProgress.value = SyncProgress(message = "同步完成")
                store.saveLastSync(System.currentTimeMillis())
                _lastSync.value = store.lastSync()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _courseSyncProgress.value = SyncProgress(message = exception.message ?: "同步失败")
            } finally {
                _courseSyncing.value = false
                if (!_loggedIn.value) clearRemoteStateAfterLogout()
            }
        }
    }

    fun saveRemind(setting: RemindSetting) {
        store.saveRemindSetting(setting)
        _remindSetting.value = setting
        reminders.rescheduleAll()
    }

    fun sendTestNotification() {
        reminders.sendTest()
    }

    fun saveCustomEvents(list: List<CustomEvent>) {
        store.saveCustomEvents(list)
        _customEvents.value = list
        reminders.rescheduleAll()
    }

    fun addCustomEvent(ev: CustomEvent) {
        val list = _customEvents.value.toMutableList()
        list.add(ev)
        saveCustomEvents(list)
    }

    fun toggleCustomEventDone(id: String) {
        val list = _customEvents.value.map {
            if (it.id == id) it.copy(done = !it.done) else it
        }
        saveCustomEvents(list)
    }

    fun deleteCustomEvent(id: String) {
        val list = _customEvents.value.filter { it.id != id }
        saveCustomEvents(list)
    }

    fun clearCustomEvents() {
        saveCustomEvents(emptyList())
    }

    private fun clearRemoteStateAfterLogout() {
        reminders.cancelAll()
        session.clearSession()
        store.clearLoginData()
        reminders.scheduleAll()
    }

}
