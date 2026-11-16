package com.ahu.ahutong.ui.screen.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.assistant.AssistantAccessState
import com.ahu.ahutong.assistant.AssistantPreferences
import com.ahu.ahutong.data.preview.EarlyAccessChoice
import com.ahu.ahutong.data.preview.EarlyAccessManager
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.state.LoginViewModel

private const val STEP_COUNT = 4

@Composable
internal fun OnboardingScreen(
    onOpenAiSettings: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable {
        mutableIntStateOf(if (SessionStore.isLoggedIn()) 2 else 0)
    }
    BackHandler(enabled = step >= 2) { step-- }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 5 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "onboardingStep",
        ) { activeStep ->
            when (activeStep) {
                0 -> WelcomePage(onStart = { step = 1 })
                1 -> {
                    val loginViewModel: LoginViewModel = hiltViewModel()
                    Login(
                        loginViewModel = loginViewModel,
                        consentRequired = true,
                        onConsentAccepted = viewModel::acceptLegalConsent,
                        onBack = { step = 0 },
                        onLoggedIn = { step = 2 },
                    )
                }
                2 -> AssistantPage(
                    step = activeStep,
                    onBack = { step = 1 },
                    onNext = { step = 3 },
                    onOpenAiSettings = onOpenAiSettings,
                )
                else -> AdvancedPage(
                    step = activeStep,
                    viewModel = viewModel,
                    onBack = { step = 2 },
                    onFinish = { viewModel.finish(onFinished) },
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(92.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "欢迎使用安大通",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "AHUTong · 安大人的校园生活助手",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "课程表、成绩、考试、校园卡都在这里",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "还有 AHUTong Intelligence，随时回答校园问题",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.weight(1f))
        AppButton(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开始")
        }
    }
}

@Composable
private fun AssistantPage(
    step: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    var aiEnabled by remember { mutableStateOf(AssistantPreferences.serviceEnabled()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        StepDots(step)
        Spacer(Modifier.height(22.dp))
        PageTitle(
            title = "AHUTong Intelligence",
            subtitle = "随口的校园问题，交给安筱桐",
        )
        Spacer(Modifier.height(22.dp))
        OnboardingCard {
            ToggleRow(
                title = "启用 AI 助手",
                subtitle = "主页显示 AI 入口，随时提问",
                checked = aiEnabled,
                onCheckedChange = { enabled ->
                    aiEnabled = enabled
                    AssistantPreferences.setServiceEnabled(enabled)
                    AssistantAccessState.refresh()
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        AppButton(
            onClick = onOpenAiSettings,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.Secondary,
        ) {
            Text("配置模型")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "也可以稍后在「AI 设置」里配置模型或开启官方试用模型。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        BottomActions(
            backLabel = "上一步",
            nextLabel = "下一步",
            onBack = onBack,
            onNext = onNext,
        )
    }
}

@Composable
private fun AdvancedPage(
    step: Int,
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val personalization by viewModel.personalizationEnabled.collectAsState()
    val predictive by viewModel.predictivePrefetchEnabled.collectAsState()
    val canteen by viewModel.canteenConsent.collectAsState()
    val earlyAccess by EarlyAccessManager.state.collectAsState()
    val earlyAccessBusy by viewModel.earlyAccessBusy.collectAsState()
    val earlyAccessError by viewModel.earlyAccessError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        StepDots(step)
        Spacer(Modifier.height(22.dp))
        PageTitle(
            title = "高级选项",
            subtitle = "这些功能都可以稍后在设置里修改",
        )
        Spacer(Modifier.height(22.dp))
        OnboardingCard {
            ToggleRow(
                title = "必吃榜数据共建",
                subtitle = "上传去标识的食堂消费记录，帮助生成窗口热度榜",
                checked = canteen == true,
                onCheckedChange = viewModel::setCanteenConsent,
            )
            RowDivider()
            ToggleRow(
                title = "智能预测",
                subtitle = "提前加载你下一步可能要看的内容",
                checked = predictive,
                onCheckedChange = viewModel::setPredictivePrefetchEnabled,
            )
            RowDivider()
            ToggleRow(
                title = "猜你想用",
                subtitle = "根据本机使用习惯显示常用入口",
                checked = personalization,
                onCheckedChange = viewModel::setPersonalizationEnabled,
            )
            RowDivider()
            ToggleRow(
                title = "抢先体验计划",
                subtitle = "提前收到新功能，可能包含未完成的功能",
                checked = earlyAccess.joined == true || earlyAccess.choice == EarlyAccessChoice.Yes,
                enabled = !earlyAccessBusy,
                onCheckedChange = viewModel::setEarlyAccess,
            )
        }
        earlyAccessError?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))
        BottomActions(
            backLabel = "上一步",
            nextLabel = "完成",
            onBack = onBack,
            onNext = onFinish,
        )
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEP_COUNT) { index ->
            val active = index == step
            Box(
                modifier = Modifier
                    .size(if (active) 18.dp else 7.dp, 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        AppToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            contentDescription = title,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun BottomActions(
    backLabel: String,
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppButton(
            onClick = onBack,
            modifier = Modifier.weight(1f),
            variant = AppButtonVariant.Secondary,
        ) {
            Text(backLabel)
        }
        AppButton(
            onClick = onNext,
            modifier = Modifier.weight(1.4f),
        ) {
            Text(nextLabel)
        }
    }
}
