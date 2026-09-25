package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.designsystem.RefreshState

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.grade.ExamSource
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.ext.launchSafe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.map
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.data.session.SessionIdentity
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

internal object ExamRefreshPolicy {
    const val AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1_000L

    fun shouldRefresh(cachedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return cachedAtMillis <= 0L ||
            nowMillis < cachedAtMillis ||
            nowMillis - cachedAtMillis >= AUTO_REFRESH_INTERVAL_MS
    }
}

internal fun List<Exam>.hasSameExamContents(other: List<Exam>): Boolean {
    if (size != other.size) return false
    return indices.all { index ->
        val left = this[index]
        val right = other[index]
        left.course == right.course &&
            left.location == right.location &&
            left.time == right.time &&
            left.seatNum == right.seatNum &&
            left.finished == right.finished
    }
}

@HiltViewModel
class ExamViewModel @Inject constructor(
    private val exams: ExamSource,
    private val session: SessionIdentity,
    private val behavior: BehaviorRecorder
) : ViewModel() {
    val data = MutableLiveData<AhuResult<List<Exam>>>()

    /** mock 场景被刷新过几次（0 = 从未）：界面据此重新拉一次。 */
    val mockRefreshRevisions = exams.mockRefreshRevisions()

    /** 调试用的假数据开关：为真时不必登录也能看到考试。 */
    val usesMockData: Boolean get() = exams.usesMockData()

    /** 用户手动刷新考试：界面只发出意图，上报由这里负责（见 BehaviorRecorder）。 */
    fun onManualRefresh() = behavior.recordOrganicAction(AppActionId.MANUAL_REFRESH_EXAM)

    /** 界面看到下一场考试在这个距离内。 */
    fun onExamDistance(bucket: ExamDistanceBucket) = behavior.recordExamDistance(bucket)
    val isLoading = MutableStateFlow<Boolean?>(null)
    val errorMessage = MutableStateFlow<String?>(null)

    private val _refreshState = MutableStateFlow(RefreshState.IDLE)
    val refreshState = _refreshState.asStateFlow()

    // 防止重复刷新
    private var refreshJob: Job? = null

    fun loadExam(isRefresh: Boolean = false) {
        if (refreshJob?.isActive == true) {
            if (!isRefresh) return
            refreshJob?.cancel()
        }
        // 正在刷新中则忽略新请求
        if (_refreshState.value == RefreshState.LOADING) return
        // 首次自动后台加载也跳过重复
        if (!isRefresh && isLoading.value == true) return

        refreshJob = viewModelScope.launchSafe {
            val user = session.currentUser()
            if (user == null && !exams.usesMockData()) {
                data.value = AhuResult.Failure(AhuError.Unauthorized("账户未登录"))
                errorMessage.value = "账户未登录"
                return@launchSafe
            }

            // 1. 优先展示缓存数据，首屏秒出
            val cached = exams.cachedExams()
            val cachedAt = exams.cachedAt()
            val hasCachedSnapshot = cachedAt > 0L
            if (!isRefresh && (cached.isNotEmpty() || hasCachedSnapshot)) {
                data.value = AhuResult.Success(cached)
            }

            if (!isRefresh && !ExamRefreshPolicy.shouldRefresh(cachedAt)) {
                isLoading.value = false
                errorMessage.value = null
                return@launchSafe
            }

            // Refresh feedback starts immediately; never delay the actual request for animation.
            if (isRefresh) {
                _refreshState.value = RefreshState.LOADING
            }

            // 仅无缓存时显示全屏加载动画
            if (!hasCachedSnapshot && cached.isEmpty()) {
                isLoading.value = true
            }
            errorMessage.value = null

            val result = exams.fetchExams(
                isRefresh = true,
                studentId = user?.xh ?: "mock-student",
                studentName = user?.name ?: "Mock 用户"
            )

            if (result.isSuccess) {
                    val newExams = result.valueOrNull().orEmpty()

                // 与缓存比对，有差异才更新 UI
                if (!hasCachedSnapshot || !cached.hasSameExamContents(newExams)) {
                            data.value = AhuResult.Success(newExams)
                }

                // Keep acknowledgement visible without holding up data delivery or navigation.
                if (isRefresh) {
                    _refreshState.value = RefreshState.UPDATED
                    viewModelScope.launch {
                        delay(700)
                        if (_refreshState.value == RefreshState.UPDATED) {
                            _refreshState.value = RefreshState.IDLE
                        }
                    }
                }
            } else {
                // 网络失败：手动刷新时立即恢复 IDLE
                if (isRefresh) {
                    _refreshState.value = RefreshState.IDLE
                }
                if (cached.isEmpty()) {
                    data.value = result
                errorMessage.value = result.errorOrNull()?.toUserMessage() ?: "获取考试信息失败"
                }
            }

            isLoading.value = false
        }
    }
}
