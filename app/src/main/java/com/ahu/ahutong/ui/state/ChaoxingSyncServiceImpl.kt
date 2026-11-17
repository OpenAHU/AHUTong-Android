package com.ahu.ahutong.ui.state

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSyncService
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.XuexiaotongSemesterRangeProvider
import com.ahu.ahutong.data.xuexiaotong.WorkStatusRefresh
import com.ahu.ahutong.data.xuexiaotong.XuexiaotongSyncMode
import com.ahu.ahutong.data.xuexiaotong.XuexiaotongSyncState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MINI_REFRESH_INTERVAL_MS = 10 * 60_000L

@Singleton
class ChaoxingSyncServiceImpl @Inject constructor(
    private val session: ChaoxingSession,
    private val reminders: ChaoxingReminders,
    private val semesterRangeProvider: XuexiaotongSemesterRangeProvider,
    @ApplicationContext private val context: Context,
) : ChaoxingSyncService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 全量开关是设备级偏好，不随换号/重登清理。 */
    private val syncPrefs = context.getSharedPreferences("xuexiaotong_sync_prefs", Context.MODE_PRIVATE)

    private var worksJob: Job? = null
    private var progressJob: Job? = null
    private var miniJob: Job? = null
    /** 首页 Hero 的静默校准是低频动作，10 分钟内不重复请求。 */
    private var lastMiniRefreshAt = 0L

    private val _state = MutableStateFlow(XuexiaotongSyncState())
    override val state: StateFlow<XuexiaotongSyncState> = _state.asStateFlow()

    private val _fullSyncEnabled = MutableStateFlow(syncPrefs.getBoolean("full_sync", false))
    override val fullSyncEnabled: StateFlow<Boolean> = _fullSyncEnabled.asStateFlow()

    override fun onConnected() {
        if (runCatching { Store.isFirstFullSyncDone() }.getOrDefault(false)) return
        startWorksSyncInternal(XuexiaotongSyncMode.FULL, automatic = true)
    }

    override fun startWorksSync(mode: XuexiaotongSyncMode): Boolean =
        startWorksSyncInternal(mode, automatic = false)

    private fun startWorksSyncInternal(mode: XuexiaotongSyncMode, automatic: Boolean): Boolean {
        if (worksJob?.isActive == true) return false
        worksJob = scope.launch {
            _state.update {
                it.copy(
                    worksRunning = true,
                    worksDone = 0,
                    worksTotal = 0,
                    worksMessage = "正在获取作业列表...",
                    worksError = null,
                )
            }
            toast(
                if (automatic) "连接成功，正在获取作业列表..." else "正在获取作业列表..."
            )
            try {
                val semesterRange = semesterRangeProvider.currentSemesterRange()
                session.silentRelogin()
                session.syncWorks(
                    full = mode == XuexiaotongSyncMode.FULL,
                    semesterRange = semesterRange,
                    onCourseListKnown = { count ->
                        toast("正在同步${count}门课程的作业列表")
                    },
                    onWorkListKnown = { count ->
                        val text = if (count > 0) {
                            "正在同步${count}条作业，预计需要${estimateMinutes(count)}分钟"
                        } else {
                            "作业列表已更新，暂无需要同步详情的作业"
                        }
                        toast(text)
                    },
                    onProgress = { done, total, message ->
                        _state.update {
                            it.copy(worksDone = done, worksTotal = total, worksMessage = message)
                        }
                    },
                )
                if (mode == XuexiaotongSyncMode.FULL) {
                    runCatching { Store.saveFirstFullSyncDone() }
                }
                _state.update {
                    it.copy(
                        worksRunning = false,
                        worksMessage = "同步完成",
                        worksError = null,
                        worksRevision = it.worksRevision + 1,
                    )
                }
                runCatching { reminders.rescheduleAll() }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(worksRunning = false, worksMessage = "", worksError = null) }
                throw cancelled
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        worksRunning = false,
                        worksMessage = "同步失败",
                        worksError = failure.message?.takeIf(String::isNotBlank) ?: "同步失败",
                    )
                }
                runCatching { reminders.rescheduleAll() }
            }
        }
        return true
    }

    override fun startCourseProgressSync(): Boolean {
        if (progressJob?.isActive == true) return false
        progressJob = scope.launch {
            _state.update {
                it.copy(
                    progressRunning = true,
                    progressDone = 0,
                    progressTotal = 0,
                    progressMessage = "正在获取课程进度...",
                    progressError = null,
                )
            }
            try {
                val semesterRange = semesterRangeProvider.currentSemesterRange()
                session.silentRelogin()
                session.syncCourseProgress(
                    semesterRange = semesterRange,
                    onProgress = { done, total, message ->
                        _state.update {
                            it.copy(progressDone = done, progressTotal = total, progressMessage = message)
                        }
                    },
                )
                _state.update {
                    it.copy(
                        progressRunning = false,
                        progressMessage = "同步完成",
                        progressError = null,
                        progressRevision = it.progressRevision + 1,
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(progressRunning = false, progressMessage = "", progressError = null) }
                throw cancelled
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        progressRunning = false,
                        progressMessage = "同步失败",
                        progressError = failure.message?.takeIf(String::isNotBlank) ?: "同步失败",
                    )
                }
            }
        }
        return true
    }

    override fun refreshMostUrgentWorkStatus() {
        if (worksJob?.isActive == true || miniJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - lastMiniRefreshAt < MINI_REFRESH_INTERVAL_MS) return
        lastMiniRefreshAt = now
        miniJob = scope.launch {
            try {
                val candidate = WorkStatusRefresh.pickMostUrgent(Store.getWorks(), now) ?: return@launch
                if (!session.hasSession()) return@launch
                val course = Store.getCourses().firstOrNull { it.courseId == candidate.courseId }
                    ?: return@launch
                val refreshed = session.refreshWorkStatus(course, candidate.workId) ?: return@launch
                // 全量同步正好在跑时让位，避免两侧各写一份缓存
                if (_state.value.worksRunning) return@launch
                val current = Store.getWorks()
                val merged = WorkStatusRefresh.merge(current, refreshed)
                if (merged == current) return@launch
                Store.saveWorks(merged)
                _state.update { it.copy(worksRevision = it.worksRevision + 1) }
                runCatching { reminders.rescheduleAll() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 静默失败：保留缓存状态，下次进入首页再试
            }
        }
    }

    override fun setFullSyncEnabled(enabled: Boolean) {
        _fullSyncEnabled.value = enabled
        runCatching { syncPrefs.edit().putBoolean("full_sync", enabled).apply() }
    }

    override fun cancel() {
        worksJob?.cancel()
        progressJob?.cancel()
        miniJob?.cancel()
        _state.update {
            it.copy(
                worksRunning = false,
                worksMessage = "",
                worksError = null,
                progressRunning = false,
                progressMessage = "",
                progressError = null,
            )
        }
    }

    private fun estimateMinutes(count: Int): Int {
        if (count <= 0) return 1
        val seconds = 20.0 + count * 0.8
        return ceil(seconds / 60.0).toInt().coerceAtLeast(1)
    }

    private fun toast(text: String) {
        mainHandler.post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}
