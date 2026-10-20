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

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _works = MutableStateFlow<List<Work>>(emptyList())
    val works: StateFlow<List<Work>> = _works.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _progress = MutableStateFlow<List<CourseProgress>>(emptyList())
    val progress: StateFlow<List<CourseProgress>> = _progress.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val _courseSyncing = MutableStateFlow(false)
    val courseSyncing: StateFlow<Boolean> = _courseSyncing.asStateFlow()

    private val _courseSyncProgress = MutableStateFlow(SyncProgress())
    val courseSyncProgress: StateFlow<SyncProgress> = _courseSyncProgress.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _remindSetting = MutableStateFlow(RemindSetting())
    val remindSetting: StateFlow<RemindSetting> = _remindSetting.asStateFlow()

    private val _customEvents = MutableStateFlow<List<CustomEvent>>(emptyList())
    val customEvents: StateFlow<List<CustomEvent>> = _customEvents.asStateFlow()

    private val _showDone = MutableStateFlow(false)
    val showDone: StateFlow<Boolean> = _showDone.asStateFlow()

    private val _doneGray = MutableStateFlow(false)
    val doneGray: StateFlow<Boolean> = _doneGray.asStateFlow()

    private val _showEmptyCourses = MutableStateFlow(false)
    val showEmptyCourses: StateFlow<Boolean> = _showEmptyCourses.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun consumeSnackbar() { _snackbar.value = null }

    fun showMsg(msg: String) { _snackbar.value = msg }

    private data class SavedState(
        val loggedIn: Boolean, val works: List<Work>, val courses: List<Course>,
        val progress: List<CourseProgress>, val lastSync: Long, val remind: RemindSetting,
        val events: List<CustomEvent>, val showDone: Boolean, val doneGray: Boolean, val showEmpty: Boolean
    )

    private var stateRevision = 0L
    private val restoreJob = viewModelScope.launch { applySavedState(readSavedState()) }

    private suspend fun readSavedState() = withContext(ioDispatcher) {
        SavedState(session.hasSession(), store.works(), store.courses(), store.courseProgress(),
            store.lastSync(), store.remindSetting(), store.customEvents(), store.showDone(), store.doneGray(), store.showEmptyCourses())
    }

    private fun applySavedState(saved: SavedState) {
        _loggedIn.value = saved.loggedIn
        _works.value = saved.works
        _courses.value = saved.courses
        _progress.value = saved.progress
        _lastSync.value = saved.lastSync
        _remindSetting.value = saved.remind
        _customEvents.value = saved.events
        _showDone.value = saved.showDone
        _doneGray.value = saved.doneGray
        _showEmptyCourses.value = saved.showEmpty
    }

    private fun afterRestore(action: () -> Unit) {
        if (restoreJob.isCompleted) action()
        else viewModelScope.launch { restoreJob.join(); action() }
    }

    fun refreshState() {
        if (!restoreJob.isCompleted) return
        viewModelScope.launch {
            val revision = stateRevision
            val saved = readSavedState()
            if (revision == stateRevision) applySavedState(saved)
        }
    }

    fun toggleShowDone() = afterRestore {
        stateRevision++
        val v = !_showDone.value
        store.saveShowDone(v)
        _showDone.value = v
    }

    fun toggleDoneGray() = afterRestore {
        stateRevision++
        val v = !_doneGray.value
        store.saveDoneGray(v)
        _doneGray.value = v
    }

    fun toggleShowEmptyCourses() = afterRestore {
        stateRevision++
        val v = !_showEmptyCourses.value
        store.saveShowEmptyCourses(v)
        _showEmptyCourses.value = v
    }

    fun onLoginSuccess() = afterRestore {
        stateRevision++
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
        stateRevision++
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

    fun syncWorks() = afterRestore {
        if (!_loggedIn.value || _syncing.value || _courseSyncing.value) return@afterRestore
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
                stateRevision++
                _works.value = works
                _courses.value = withContext(ioDispatcher) { store.courses() }
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

    fun syncCourseProgress() = afterRestore {
        if (!_loggedIn.value || _syncing.value || _courseSyncing.value) return@afterRestore
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
                stateRevision++
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

    fun saveRemind(setting: RemindSetting) = afterRestore {
        stateRevision++
        store.saveRemindSetting(setting)
        _remindSetting.value = setting
        reminders.rescheduleAll()
    }

    fun sendTestNotification() {
        reminders.sendTest()
    }

    fun saveCustomEvents(list: List<CustomEvent>) = afterRestore {
        stateRevision++
        store.saveCustomEvents(list)
        _customEvents.value = list
        reminders.rescheduleAll()
    }

    fun addCustomEvent(ev: CustomEvent) = afterRestore {
        stateRevision++
        val list = _customEvents.value.toMutableList()
        list.add(ev)
        saveCustomEvents(list)
    }

    fun toggleCustomEventDone(id: String) = afterRestore {
        stateRevision++
        val list = _customEvents.value.map {
            if (it.id == id) it.copy(done = !it.done) else it
        }
        saveCustomEvents(list)
    }

    fun deleteCustomEvent(id: String) = afterRestore {
        stateRevision++
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
