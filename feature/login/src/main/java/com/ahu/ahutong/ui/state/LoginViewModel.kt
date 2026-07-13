package com.ahu.ahutong.ui.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.auth.AuthRepository
import com.ahu.ahutong.data.auth.AuthRuntimeReset
import com.ahu.ahutong.data.auth.AuthSessionStore
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.feature.login.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val authSessionStore: AuthSessionStore,
    private val authRuntimeReset: AuthRuntimeReset,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    var state by mutableStateOf(LoginState.Idle)
    var failureMessage by mutableStateOf("")
    var succeedMessage by mutableStateOf("")

    /**
     * Clears prior session state and logs in (native HTTP → JNI → crawler).
     */
    fun loginWithCrawler(userID: String, password: String) {
        if (userID.isBlank() || password.isBlank()) {
            state = LoginState.Failed
            failureMessage = appContext.getString(R.string.please_fill_complete)
            return
        }

        authRuntimeReset.resetRuntimeCredentials()
        authSessionStore.clearPersistedSession()

        viewModelScope.launchSafe {
            try {
                state = LoginState.InProgress
                when (
                    val response = withContext(Dispatchers.IO) {
                        authRepository.login(userID, password)
                    }
                ) {
                    is AppResult.Success -> {
                        authSessionStore.persistLoginSuccess(response.data, password)
                        state = LoginState.Succeeded
                        succeedMessage = appContext.getString(
                            R.string.welcome_user,
                            response.data.name,
                        )
                    }
                    is AppResult.Error -> {
                        state = LoginState.Failed
                        failureMessage = response.message
                    }
                }
            } catch (e: Throwable) {
                state = LoginState.Failed
                failureMessage = e.message ?: appContext.getString(R.string.login_failed)
            }
        }
    }
}

enum class LoginState {
    Idle, InProgress, Failed, Succeeded
}
