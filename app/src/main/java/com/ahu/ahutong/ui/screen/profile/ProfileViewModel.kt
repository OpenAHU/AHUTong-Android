package com.ahu.ahutong.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.ext.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 个人信息页只补充「学院 / 专业 / 培养类型」。
 *
 * 这部分数据已经由成绩页的教务档案链路抓取并缓存（GradeStudentProfile），这里复用同一条链路，
 * 不新增接口；姓名、学号与账号类型直接来自登录会话。教务不可用时仍展示本地已有信息。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val _profiles = MutableStateFlow(AHUCache.getGradeStudentProfiles())
    val profiles: StateFlow<List<GradeStudentProfile>> = _profiles.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launchSafe {
            _refreshing.value = true
            _error.value = null
            runCatching { AHURepository.getGradeStudentProfiles() }
                .onSuccess { resolved -> _profiles.value = resolved }
                .onFailure { _error.value = "学院和专业信息暂时无法获取，请稍后重试" }
            _refreshing.value = false
        }
    }
}
