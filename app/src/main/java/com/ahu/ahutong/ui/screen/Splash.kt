package com.ahu.ahutong.ui.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import com.ahu.ahutong.R
import com.ahu.ahutong.MainActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.SplashViewModel
import com.ahu.ahutong.ui.state.BootstrapTrainingOnboardingState
import com.ahu.ahutong.ui.state.TelemetryOnboardingState
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.session.CurrentUserRestoreResult

@Composable
fun Splash(
    navController: NavController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val policyAccepted by viewModel.privacyPolicyAccepted.collectAsState()
    val telemetryState by viewModel.telemetryOnboardingState.collectAsState()
    val bootstrapTrainingState by viewModel.bootstrapTrainingOnboardingState.collectAsState()
    val activity = LocalActivity.current
    val context = LocalContext.current
    var restoreAttempt by remember { mutableIntStateOf(0) }
    var restoreFailure by remember { mutableStateOf<CurrentUserRestoreResult.Unavailable?>(null) }

    val telemetryChoice = (telemetryState as? TelemetryOnboardingState.Ready)?.choice
    val bootstrapTrainingChoice =
        (bootstrapTrainingState as? BootstrapTrainingOnboardingState.Ready)?.choice

    LaunchedEffect(policyAccepted, telemetryState, bootstrapTrainingState, restoreAttempt) {
        if (policyAccepted == true &&
            telemetryChoice != null && bootstrapTrainingChoice != null
        ) {
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
            if (restored is CurrentUserRestoreResult.Unavailable) {
                restoreFailure = restored
            } else {
                val user = (restored as CurrentUserRestoreResult.Ready).user
                if (user != null) (activity as? MainActivity)?.onSessionStorageReady()
                navController.navigate(if (user != null) "home" else "login") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    val onboardingReady = policyAccepted != null && telemetryState is TelemetryOnboardingState.Ready &&
        bootstrapTrainingState is BootstrapTrainingOnboardingState.Ready
    val requiresAcceptance = policyAccepted != true ||
        telemetryChoice == null || bootstrapTrainingChoice == null
    if (!onboardingReady || !requiresAcceptance) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val failure = restoreFailure
            if (failure == null) {
                AppCircularProgressIndicator()
            } else {
                com.ahu.ahutong.ui.components.AppStateCard.Error(
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
    } else if (requiresAcceptance) {
        val policyMarkdown by produceState<String?>(null) {
            value = withContext(Dispatchers.IO) {
                context.resources.openRawResource(R.raw.privacy_policy)
                    .bufferedReader().use { it.readText() }
            }
        }
        UnifiedPrivacyPolicyDialog(
            policyMarkdown = policyMarkdown,
            onAgree = {
                viewModel.acceptUnifiedPrivacyPolicy()
            },
            onDisagree = { activity?.finish() }
        )
    }
}

@Composable
private fun UnifiedPrivacyPolicyDialog(
    policyMarkdown: String?,
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    OnboardingDialogTemplate(
        title = "隐私政策",
        content = {
            // 弹窗滚动容器给无限高度约束，禁 LazyColumn → lazy=false 逐块渲染
            if (policyMarkdown == null) {
                com.ahu.ahutong.ui.components.AppStateCard.Loading(message = "政策加载中…")
            } else {
                com.ahu.ahutong.ui.markdown.AppMarkdown(
                    markdown = policyMarkdown,
                    lazy = false
                )
            }
        },
        confirmText = "同意并继续",
        dismissText = "拒绝",
        onConfirm = onAgree,
        onDismiss = onDisagree
    )
}

@Composable
private fun OnboardingDialogTemplate(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDismissRequest: () -> Unit = {}
) {
    AppDialog(
        title = title,
        onDismiss = onDismissRequest,
        titleStyle = MaterialTheme.typography.headlineSmall,
        titleFontWeight = null,
        contentScrollable = true,
        actions = listOf(
            AppDialogAction(dismissText, onClick = onDismiss),
            AppDialogAction(confirmText, onClick = onConfirm, style = AppDialogActionStyle.Primary)
        ),
        content = content
    )
}
