package com.ahu.ahutong.ui.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.ext.launchSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * @Author: SinkDev
 * @Date: 2021/8/14-上午8:58
 * @Email: 468766131@qq.com
 */
class LoginViewModel : ViewModel() {
    var state by mutableStateOf(LoginState.Idle)
    var failureMessage by mutableStateOf("")
    var succeedMessage by mutableStateOf("")
    private var pendingWebLoginUser: User? = null

    /**
     * 爬虫登录
     */
    fun loginWithCrawler(userID: String, password: String) = viewModelScope.launchSafe {
        try {
            state = LoginState.InProgress
            val response = withContext(Dispatchers.IO) {
                AHURepository.loginWithCrawler(userID, password)
            }

            when {
                response.isSuccessful -> completeLogin(response.data, password)
                response.code == AHURepository.WEB_VERIFICATION_REQUIRED_CODE &&
                    response.data != null -> {
                    pendingWebLoginUser = response.data
                    state = LoginState.WebVerification
                }
                else -> {
                    state = LoginState.Failed
                    failureMessage = response.msg
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Login failed unexpectedly", e)
            pendingWebLoginUser = null
            state = LoginState.Failed
            failureMessage = e.message ?: "登录失败，请稍后重试"
        }
    }

    fun completeWebVerification(cookiesJson: String, password: String) =
        viewModelScope.launchSafe {
            state = LoginState.InProgress
            val user = pendingWebLoginUser
            if (user == null) {
                failWebVerification("登录信息已失效，请重新登录")
                return@launchSafe
            }

            val importResult = withContext(Dispatchers.IO) {
                AHURepository.importWebLoginCookies(cookiesJson)
            }
            importResult.fold(
                onSuccess = {
                    pendingWebLoginUser = null
                    completeLogin(user, password)
                },
                onFailure = {
                    Log.e(TAG, "Failed to import WebView session", it)
                    failWebVerification(it.message ?: "教务安全验证失败，请重试")
                }
            )
        }

    fun failWebVerification(message: String) {
        pendingWebLoginUser = null
        state = LoginState.Failed
        failureMessage = message
    }

    private fun completeLogin(user: User, password: String) {
        AHUCache.saveCurrentUser(user)
        AHUCache.saveWisdomPassword(password)
        AHUCache.setAgreementAccepted()
        AHUCache.setBusinessAccepted()
        AHUCache.setPrivacyAccepted()
        if (!AHUCache.canUseUndergraduateAcademics()) {
            com.ahu.ahutong.notification.CourseReminderScheduler.cancel(com.ahu.ahutong.AHUApplication.getApp())
        }
        succeedMessage = "欢迎，${user.name}！"
        state = LoginState.Succeeded
    }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}

enum class LoginState {
    Idle, InProgress, WebVerification, Failed, Succeeded
}
