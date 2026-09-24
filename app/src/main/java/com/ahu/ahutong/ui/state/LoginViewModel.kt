package com.ahu.ahutong.ui.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.session.AhuSession
import com.ahu.ahutong.data.session.CredentialVault
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.ext.launchSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.core.common.toUserMessage
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * @Author: SinkDev
 * @Date: 2021/8/14-上午8:58
 * @Email: 468766131@qq.com
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val session: AhuSession,
    private val credentials: CredentialVault
) : ViewModel() {
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
            // 切换账号前只经会话接缝清理旧 token/Cookie，再清旧账号的缓存与持久身份。
            session.signOut()
            // 换号清理不碰设备级法律同意（否则每次登录后隐私政策重弹）
            AHUCache.clearAll(preserveLegalConsent = true)
            val response = withContext(Dispatchers.IO) {
                session.signIn(userID, password)
            }

            when (val outcome = response.valueOrNull()) {
                is LoginOutcome.Success -> completeLogin(outcome.user, password)
                is LoginOutcome.JwxtWebVerificationRequired -> {
                    pendingWebLoginUser = outcome.user
                    state = LoginState.WebVerification
                }
                null -> {
                    state = LoginState.Failed
                    failureMessage = response.errorOrNull()?.toUserMessage().orEmpty()
                }
            }
        } catch (e: Throwable) {
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
            val importError = importResult.errorOrNull()
            if (importError == null) {
                pendingWebLoginUser = null
                completeLogin(user, password) {
                    session.completeWebVerification()
                }
            } else {
                Log.e(
                    TAG,
                    "Failed to import WebView session: ${importError::class.java.simpleName}"
                )
                failWebVerification(importError.toUserMessage().ifBlank { "教务安全验证失败，请重试" })
            }
        }

    fun failWebVerification(message: String) {
        pendingWebLoginUser = null
        state = LoginState.Failed
        failureMessage = message
    }

    private suspend fun completeLogin(
        user: User,
        password: String,
        afterPersist: suspend () -> Unit = {}
    ) {
        try {
            // 凭据先落到 Keystore；任何一步失败都不能把界面推进到“登录成功”。
            credentials.saveWisdomPassword(password)
            AHUCache.saveCurrentUser(user)
            AHUCache.setAgreementAccepted()
            AHUCache.setBusinessAccepted()
            AHUCache.setPrivacyAccepted()
            afterPersist()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Failed to persist authenticated session", error)
            runCatching { session.signOut() }
            runCatching { AHUCache.clearCurrentUser() }
            runCatching { credentials.clearWisdomPassword() }
            pendingWebLoginUser = null
            state = LoginState.Failed
            failureMessage = "无法安全保存登录信息，请重试"
            return
        }

        state = LoginState.Succeeded
        succeedMessage = "欢迎，${user.name}！"
    }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}

enum class LoginState {
    Idle, InProgress, WebVerification, Failed, Succeeded
}
