package com.ahu.ahutong.ui.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import com.ahu.ahutong.R
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
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

    val telemetryChoice = (telemetryState as? TelemetryOnboardingState.Ready)?.choice
    val bootstrapTrainingChoice =
        (bootstrapTrainingState as? BootstrapTrainingOnboardingState.Ready)?.choice

    LaunchedEffect(policyAccepted, telemetryState, bootstrapTrainingState) {
        if (policyAccepted == true &&
            telemetryChoice != null && bootstrapTrainingChoice != null
        ) {
            if (withContext(Dispatchers.IO) { SessionStore.isLoggedIn() }) {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            } else {
                navController.navigate("login") {
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
            AppCircularProgressIndicator()
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
