package com.ahu.ahutong.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.auth.AuthRepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.ext.launchSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @Author: SinkDev
 * @Date: 2021/8/14-上午8:58
 * @Email: 468766131@qq.com
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    var state by mutableStateOf(LoginState.Idle)
    var failureMessage by mutableStateOf("")
    var succeedMessage by mutableStateOf("")

    /**
     * 登录（native HTTP → JNI → crawler）
     */
    fun loginWithCrawler(userID: String, password: String) = viewModelScope.launchSafe {
        try {
            state = LoginState.InProgress
            when (
                val response = withContext(Dispatchers.IO) {
                    authRepository.login(userID, password)
                }
            ) {
                is AppResult.Success -> {
                    state = LoginState.Succeeded
                    succeedMessage = "欢迎，${response.data.name}！"
                    AHUCache.saveCurrentUser(response.data)
                    AHUCache.saveWisdomPassword(password)
                    AHUCache.setAgreementAccepted()
                    AHUCache.setBusinessAccepted()
                    AHUCache.setPrivacyAccepted()
                }
                is AppResult.Error -> {
                    state = LoginState.Failed
                    failureMessage = response.message
                }
            }
        } catch (e: Throwable) {
            state = LoginState.Failed
            failureMessage = e.message ?: "登录失败"
        }
    }
}

enum class LoginState {
    Idle, InProgress, Failed, Succeeded
}
