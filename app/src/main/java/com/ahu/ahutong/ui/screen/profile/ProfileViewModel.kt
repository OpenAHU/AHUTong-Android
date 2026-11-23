package com.ahu.ahutong.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.onFailure
import com.ahu.ahutong.core.common.onSuccess
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.model.jwxt.StudentInfo
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.DormitoryInfo
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.ext.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 个人信息页：主数据来自教务「学籍信息」页，成绩档案只作为抓取失败时的培养信息兜底。
 *
 * 姓名、学号与账号类型仍在界面层直接读登录会话；这里只负责学籍页的缓存与刷新。
 * 宿舍信息来自智慧安大 getQinshiInfo，仅在缓存为空或手动刷新时拉取，可手动修改。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val _studentInfo = MutableStateFlow(AHUCache.getStudentInfo())
    val studentInfo: StateFlow<StudentInfo?> = _studentInfo.asStateFlow()

    private val _profiles = MutableStateFlow(AHUCache.getGradeStudentProfiles())
    val profiles: StateFlow<List<GradeStudentProfile>> = _profiles.asStateFlow()

    private val _dormitoryInfo = MutableStateFlow(AHUCache.getDormitoryInfo())
    val dormitoryInfo: StateFlow<DormitoryInfo?> = _dormitoryInfo.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launchSafe {
            _refreshing.value = true
            _error.value = null
            AHURepository.getStudentInfo(force)
                .onSuccess { _studentInfo.value = it }
                .onFailure { failure ->
                    _error.value = when (failure) {
                        is AhuError.Unauthorized -> failure.message
                        else -> "学籍信息暂时无法获取，请稍后重试"
                    }
                }
            if (_studentInfo.value == null) {
                _profiles.value = AHUCache.getGradeStudentProfiles()
            }
            // 宿舍信息：缓存为空或手动刷新时才发起网络请求（策略在 Repository 内）
            AHURepository.getDormitoryInfo(force)
                .onSuccess { _dormitoryInfo.value = it }
            _refreshing.value = false
        }
    }

    /** 手动修改宿舍信息：直接写入全局缓存，不触发网络请求。 */
    fun updateDormitory(building: String, room: String) {
        val current = _dormitoryInfo.value
        val updated = DormitoryInfo(
            campus = current?.campus?.ifBlank { "磬苑校区" } ?: "磬苑校区",
            building = building.trim(),
            room = room.trim(),
            resident = current?.resident ?: true,
            cachedAt = System.currentTimeMillis()
        )
        AHUCache.setDormitoryInfo(updated)
        _dormitoryInfo.value = updated
    }
}
