package com.ahu.ahutong.ui.state

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.exam.ExamLocalStore
import com.ahu.ahutong.data.exam.ExamRepository
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.feature.exam.R
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RefreshState { IDLE, LOADING, UPDATED }

@HiltViewModel
class ExamViewModel @Inject constructor(
    private val examRepository: ExamRepository,
    private val examLocalStore: ExamLocalStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val data = MutableLiveData<Result<List<Exam>>>()
    val isLoading = MutableStateFlow<Boolean?>(null)
    val errorMessage = MutableStateFlow<String?>(null)

    private val _refreshState = MutableStateFlow(RefreshState.IDLE)
    val refreshState = _refreshState.asStateFlow()

    private var refreshJob: Job? = null

    fun isMockMode(): Boolean = examLocalStore.isMockMode()

    fun loadExam(isRefresh: Boolean = false) {
        if (_refreshState.value == RefreshState.LOADING) return
        if (!isRefresh && isLoading.value == true) return

        refreshJob?.cancel()
        refreshJob = viewModelScope.launchSafe {
            val userId = examLocalStore.getCurrentUserId()
            val userName = examLocalStore.getCurrentUserName()
            if (userId == null && !examLocalStore.isMockMode()) {
                val notLoggedIn = appContext.getString(R.string.account_not_logged_in)
                data.value = Result.failure(Throwable(notLoggedIn))
                errorMessage.value = notLoggedIn
                return@launchSafe
            }

            val cached = examLocalStore.getCachedExams()
            if (cached.isNotEmpty() && !isRefresh) {
                data.value = Result.success(cached)
            }

            if (isRefresh) {
                _refreshState.value = RefreshState.LOADING
                delay(800)
            }

            if (cached.isEmpty()) {
                isLoading.value = true
            }
            errorMessage.value = null

            val result = examRepository.getExamInfo(
                isRefresh = true,
                studentId = userId ?: "mock-student",
                studentName = userName ?: "Mock 用户",
            )

            when (result) {
                is AppResult.Success -> {
                    val newExams = result.data
                    val cachedJson = Gson().toJson(cached)
                    val newJson = Gson().toJson(newExams)
                    if (cachedJson != newJson) {
                        examLocalStore.saveExams(newExams)
                        data.value = Result.success(newExams)
                    }

                    if (isRefresh) {
                        _refreshState.value = RefreshState.UPDATED
                        delay(2000)
                        _refreshState.value = RefreshState.IDLE
                    }
                }
                is AppResult.Error -> {
                    if (isRefresh) {
                        _refreshState.value = RefreshState.IDLE
                    }
                    if (cached.isEmpty()) {
                        data.value = Result.failure(
                            result.cause ?: IllegalStateException(result.message),
                        )
                        errorMessage.value = result.message
                    }
                }
            }

            isLoading.value = false
        }
    }
}
