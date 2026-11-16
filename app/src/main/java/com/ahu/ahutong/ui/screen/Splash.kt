package com.ahu.ahutong.ui.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.session.CurrentUserRestoreResult
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.screen.setup.OnboardingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun Splash(navController: NavController) {
    val activity = LocalActivity.current
    var restoreFailure by remember { mutableStateOf<CurrentUserRestoreResult.Unavailable?>(null) }
    var restoreAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(restoreAttempt) {
        restoreFailure = null
        val restored = withContext(Dispatchers.IO) {
            var result = SessionStore.restoreCurrentUser()
            for (retryDelay in listOf(150L, 350L)) {
                if (result is CurrentUserRestoreResult.Unavailable && result.retryable) {
                    delay(retryDelay)
                    result = SessionStore.restoreCurrentUser()
                } else break
            }
            result
        }
        when (restored) {
            is CurrentUserRestoreResult.Unavailable -> restoreFailure = restored
            is CurrentUserRestoreResult.Ready -> {
                val user = restored.user
                if (user != null) (activity as? MainActivity)?.onSessionStorageReady()
                val needsOnboarding = withContext(Dispatchers.IO) {
                    OnboardingState.shouldShowOnboarding()
                }
                navController.navigate(
                    when {
                        needsOnboarding -> "onboarding"
                        user != null -> "home"
                        else -> "login"
                    }
                ) {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val failure = restoreFailure
        if (failure == null) {
            AppCircularProgressIndicator()
        } else {
            AppStateCard.Error(
                title = "登录信息读取失败",
                message = if (failure.retryable) {
                    "暂时无法读取已保存的登录信息，请稍后重试。"
                } else {
                    "无法读取已保存的登录信息，请重新登录。"
                },
                retryLabel = if (failure.retryable) "重试" else "重新登录",
                onRetry = {
                    if (failure.retryable) {
                        restoreFailure = null
                        restoreAttempt++
                    } else {
                        navController.navigate("login") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
