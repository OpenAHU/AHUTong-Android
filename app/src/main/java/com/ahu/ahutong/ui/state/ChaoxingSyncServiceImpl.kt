package com.ahu.ahutong.ui.state

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSyncService
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.XuexiaotongSyncMode
import com.ahu.ahutong.data.xuexiaotong.XuexiaotongSyncState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZoneId
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

@Singleton
class ChaoxingSyncServiceImpl @Inject constructor(
    private val session: ChaoxingSession,
    private val reminders: ChaoxingReminders,
    @ApplicationContext private val context: Context,
) : ChaoxingSyncService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 全量开关是设备级偏好，不随换号/重登清理。 */
    private val syncPrefs = context.getSharedPreferences("xuexiaotong_sync_prefs", Context.MODE_PRIVATE)

    private var worksJob: Job? = null
    private var progressJob: Job? = null

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
            if (automatic) toast("连接成功，正在获取作业列表...")
            try {
                session.silentRelogin()
                session.syncWorks(
                    full = mode == XuexiaotongSyncMode.FULL,
                    semesterRange = currentSemesterRange(),
                    onWorkListKnown = { count ->
                        if (automatic) {
                            val text = if (count > 0) {
                                "正在同步${count}条作业，预计需要${estimateMinutes(count)}分钟"
                            } else {
                                "作业列表已更新，暂无需要同步详情的作业"
                            }
                            toast(text)
                        }
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
                session.silentRelogin()
                session.syncCourseProgress(
                    semesterRange = currentSemesterRange(),
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

    override fun setFullSyncEnabled(enabled: Boolean) {
        _fullSyncEnabled.value = enabled
        runCatching { syncPrefs.edit().putBoolean("full_sync", enabled).apply() }
    }

    override fun cancel() {
        worksJob?.cancel()
        progressJob?.cancel()
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

    /** 教务学期区间：第一周周一到第 20 周末；拿不到时退回「未结课」判断。 */
    private fun currentSemesterRange(): LongRange? {
        val config = runCatching { CurrentWeekResolver.resolveLocalConfig()?.config }.getOrNull() ?: return null
        val startDate = config.startTime?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate() ?: return null
        val zone = ZoneId.systemDefault()
        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = startDate.plusWeeks(20).minusDays(1)
            .atTime(LocalTime.MAX)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return start..end
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
