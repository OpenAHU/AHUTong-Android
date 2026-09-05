package com.ahu.ahutong.ui.screen.xuexiaotong

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.xuexiaotong.ChaoxingApi
import com.ahu.ahutong.data.xuexiaotong.Course
import com.ahu.ahutong.data.xuexiaotong.CourseProgress
import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.reminder.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyncProgress(val done: Int = 0, val total: Int = 0, val message: String = "")

class XuexiaotongViewModel(val api: ChaoxingApi, private val appContext: Context) : ViewModel() {

    private val _loggedIn = MutableStateFlow(api.hasSession())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _works = MutableStateFlow<List<Work>>(Store.getWorks())
    val works: StateFlow<List<Work>> = _works.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(Store.getCourses())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _progress = MutableStateFlow<List<CourseProgress>>(Store.getCourseProgress())
    val progress: StateFlow<List<CourseProgress>> = _progress.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val _courseSyncing = MutableStateFlow(false)
    val courseSyncing: StateFlow<Boolean> = _courseSyncing.asStateFlow()

    private val _courseSyncProgress = MutableStateFlow(SyncProgress())
    val courseSyncProgress: StateFlow<SyncProgress> = _courseSyncProgress.asStateFlow()

    private val _lastSync = MutableStateFlow(Store.getLastSync())
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _remindSetting = MutableStateFlow(Store.getRemindSetting())
    val remindSetting: StateFlow<RemindSetting> = _remindSetting.asStateFlow()

    private val _customEvents = MutableStateFlow<List<CustomEvent>>(Store.getCustomEvents())
    val customEvents: StateFlow<List<CustomEvent>> = _customEvents.asStateFlow()

    private val _showDone = MutableStateFlow(Store.getShowDone())
    val showDone: StateFlow<Boolean> = _showDone.asStateFlow()

    private val _doneGray = MutableStateFlow(Store.getDoneGray())
    val doneGray: StateFlow<Boolean> = _doneGray.asStateFlow()

    private val _showEmptyCourses = MutableStateFlow(Store.getShowEmptyCourses())
    val showEmptyCourses: StateFlow<Boolean> = _showEmptyCourses.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    fun consumeSnackbar() { _snackbar.value = null }

    fun showMsg(msg: String) { _snackbar.value = msg }

    fun refreshState() {
        _loggedIn.value = api.hasSession()
        _works.value = Store.getWorks()
        _courses.value = Store.getCourses()
        _progress.value = Store.getCourseProgress()
        _lastSync.value = Store.getLastSync()
        _remindSetting.value = Store.getRemindSetting()
        _customEvents.value = Store.getCustomEvents()
        _showDone.value = Store.getShowDone()
        _doneGray.value = Store.getDoneGray()
        _showEmptyCourses.value = Store.getShowEmptyCourses()
    }

    fun toggleShowDone() {
        val v = !_showDone.value
        Store.saveShowDone(v)
        _showDone.value = v
    }

    fun toggleDoneGray() {
        val v = !_doneGray.value
        Store.saveDoneGray(v)
        _doneGray.value = v
    }

    fun toggleShowEmptyCourses() {
        val v = !_showEmptyCourses.value
        Store.saveShowEmptyCourses(v)
        _showEmptyCourses.value = v
    }

    fun onLoginSuccess() {
        _loggedIn.value = true
        refreshState()
        syncWorks()
    }

    fun logout() {
        _loggedIn.value = false
        viewModelScope.coroutineContext.cancelChildren()
        ReminderScheduler.cancelAll(appContext)
        api.clearSession()
        Store.clearLoginData()
        ReminderScheduler.scheduleAll(appContext)
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
                val works = withContext(Dispatchers.IO) {
                    api.silentRelogin()
                    api.syncAllWorks(object : ChaoxingApi.ProgressListener {
                        override fun onProgress(done: Int, total: Int, message: String) {
                            _syncProgress.value = SyncProgress(done, total, message)
                        }
                    })
                }
                _works.value = works
                _courses.value = Store.getCourses()
                _syncProgress.value = SyncProgress(message = "同步完成")
                Store.saveLastSync(System.currentTimeMillis())
                _lastSync.value = Store.getLastSync()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _syncProgress.value = SyncProgress(message = exception.message ?: "同步失败")
            } finally {
                _syncing.value = false
                if (_loggedIn.value) {
                    ReminderScheduler.rescheduleAll(appContext)
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
                val list = withContext(Dispatchers.IO) {
                    api.silentRelogin()
                    api.syncCourseProgress(object : ChaoxingApi.ProgressListener {
                        override fun onProgress(done: Int, total: Int, message: String) {
                            _courseSyncProgress.value = SyncProgress(done, total, message)
                        }
                    })
                }
                _progress.value = list
                _courseSyncProgress.value = SyncProgress(message = "同步完成")
                Store.saveLastSync(System.currentTimeMillis())
                _lastSync.value = Store.getLastSync()
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
        Store.saveRemindSetting(setting)
        _remindSetting.value = setting
        ReminderScheduler.rescheduleAll(appContext)
    }

    fun sendTestNotification() {
        ReminderScheduler.sendTest(appContext)
    }

    fun saveCustomEvents(list: List<CustomEvent>) {
        Store.saveCustomEvents(list)
        _customEvents.value = list
        ReminderScheduler.rescheduleAll(appContext)
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
        ReminderScheduler.cancelAll(appContext)
        api.clearSession()
        Store.clearLoginData()
        ReminderScheduler.scheduleAll(appContext)
    }

    class Factory(private val api: ChaoxingApi, private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return XuexiaotongViewModel(api, appContext) as T
        }
    }
}
