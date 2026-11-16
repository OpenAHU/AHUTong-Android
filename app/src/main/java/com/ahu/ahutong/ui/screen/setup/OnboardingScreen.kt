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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
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
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.haptics.HapticIconButton as IconButton
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongViewModel
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val STEP_COUNT = 5

internal val OnboardingActionHeight = 48.dp

@Composable
internal fun OnboardingScreen(
    onOpenOfficialTrial: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable {
        mutableIntStateOf(
            when {
                OnboardingState.consumeReplay() -> 0
                SessionStore.isLoggedIn() -> 2
                else -> 0
            }
        )
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
                1 -> Box(Modifier.fillMaxSize()) {
                    val loginViewModel: LoginViewModel = hiltViewModel()
                    Login(
                        loginViewModel = loginViewModel,
                        consentRequired = true,
                        onConsentAccepted = viewModel::acceptLegalConsent,
                        onBack = { step = 0 },
                        onboardingStep = 1,
                        onLoggedIn = { step = 2 },
                    )
                }
                2 -> {
                    val xuexiaotongViewModel: XuexiaotongViewModel = hiltViewModel()
                    XuexiaotongPage(
                        step = activeStep,
                        viewModel = xuexiaotongViewModel,
                        onBack = { step = 1 },
                        onDone = { step = 3 },
                    )
                }
                3 -> AssistantPage(
                    step = activeStep,
                    onBack = { step = 2 },
                    onNext = { step = 4 },
                    onOpenOfficialTrial = onOpenOfficialTrial,
                )
                4 -> AdvancedPage(
                    step = activeStep,
                    viewModel = viewModel,
                    onBack = { step = 3 },
                    onNext = { step = 5 },
                )
                else -> EarlyAccessPage(
                    step = activeStep,
                    viewModel = viewModel,
                    onBack = { step = 4 },
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
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            }
        }
        Spacer(Modifier.height(16.dp))
        AppButton(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(OnboardingActionHeight),
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
    onOpenOfficialTrial: () -> Unit,
) {
    var aiEnabled by remember { mutableStateOf(AssistantPreferences.serviceEnabled()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            OnboardingStepDots(step)
            Spacer(Modifier.height(22.dp))
            PageTitle(
                title = "AHUTong Intelligence",
                subtitle = "随口的校园问题，交给安筱桐",
            )
            Spacer(Modifier.height(22.dp))
            OnboardingCard {
                ToggleRow(
                    title = "开启 AI 服务",
                    subtitle = "主页显示 AI 入口，随时提问",
                    checked = aiEnabled,
                    onCheckedChange = { enabled ->
                        aiEnabled = enabled
                        AssistantPreferences.setServiceEnabled(enabled)
                        AssistantAccessState.refresh()
                    },
                )
                RowDivider()
                ActionRow(
                    title = "激活免费试用模型",
                    subtitle = "官方提供，完成验证即可使用",
                    trailing = "去激活",
                    onClick = onOpenOfficialTrial,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "也可以稍后在「AI 设置」里配置自己的模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        BottomActions(
            nextLabel = "下一步",
            onBack = onBack,
            onNext = onNext,
        )
    }
}

@Composable
private fun XuexiaotongPage(
    step: Int,
    viewModel: XuexiaotongViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val loggedIn by viewModel.loggedIn.collectAsState()
    var account by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var password by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        if (busy) return
        error = null
        if (account.text.isBlank() || password.text.isBlank()) {
            error = "请填写账号和密码"
            return
        }
        busy = true
        scope.launch {
            try {
                viewModel.login(account.text.trim(), password.text)
                viewModel.onLoginSuccess()
                onDone()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message?.takeIf { it.isNotBlank() } ?: "连接失败，请检查账号密码"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            OnboardingStepDots(step)
            Spacer(Modifier.height(22.dp))
            PageTitle(
                title = "连接学习通",
                subtitle = "用于作业、课程进度与截止提醒，可以跳过",
            )
            Spacer(Modifier.height(20.dp))
            if (loggedIn) {
                Text(
                    text = "已连接学习通，可以直接继续。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                CapsuleInput(
                    value = account,
                    onValueChange = { account = it },
                    placeholder = "手机号 / 超星号 / 邮箱",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                )
                Spacer(Modifier.height(12.dp))
                CapsuleInput(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "学习通密码",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    passwordVisible = passwordVisible,
                )
            }
            Spacer(Modifier.height(10.dp))
            LoginStatusLine(
                error?.let { LoginMessage(it, LoginMessageTone.Error) }
            )
        }
        Spacer(Modifier.height(16.dp))
        if (loggedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OnboardingActionHeight),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingBackButton(onClick = onBack)
                AppButton(
                    onClick = onDone,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Text("下一步")
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OnboardingActionHeight),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingBackButton(onClick = onBack)
                AppButton(
                    onClick = onDone,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    variant = AppButtonVariant.Secondary,
                ) {
                    Text("跳过")
                }
                AppButton(
                    onClick = { connect() },
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    enabled = !busy,
                ) {
                    if (busy) {
                        AppCircularProgressIndicator(
                            size = 20.dp,
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("连接中…")
                    } else {
                        Text("连接")
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTogglePassword: (() -> Unit)? = null,
    passwordVisible: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .appLiquidGlassSurface(
                shape = ContinuousCapsule,
                fallbackColor = 100.n1 withNight 20.n1,
                level = LiquidGlassSurfaceLevel.Control
            ),
        textStyle = LocalTextStyle.current.copy(
            color = LocalContentColor.current,
            fontFamily = FontFamily.Monospace
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(LocalContentColor.current)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(64.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(end = if (onTogglePassword != null) 44.dp else 0.dp)
            ) {
                it()
            }
            if (value.text.isBlank()) {
                Text(
                    text = placeholder,
                    color = 30.n1 withNight 80.n1,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (onTogglePassword != null) {
                IconButton(
                    onClick = onTogglePassword,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Default.Visibility
                        } else {
                            Icons.Default.VisibilityOff
                        },
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedPage(
    step: Int,
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val personalization by viewModel.personalizationEnabled.collectAsState()
    val predictive by viewModel.predictivePrefetchEnabled.collectAsState()
    val canteen by viewModel.canteenConsent.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            OnboardingStepDots(step)
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
            }
        }
        Spacer(Modifier.height(16.dp))
        BottomActions(
            nextLabel = "下一步",
            onBack = onBack,
            onNext = onNext,
        )
    }
}

@Composable
private fun EarlyAccessPage(
    step: Int,
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val earlyAccess by EarlyAccessManager.state.collectAsState()
    val earlyAccessBusy by viewModel.earlyAccessBusy.collectAsState()
    val earlyAccessError by viewModel.earlyAccessError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            OnboardingStepDots(step)
            Spacer(Modifier.height(22.dp))
            PageTitle(
                title = "抢先体验计划",
                subtitle = "提前收到新功能，可能包含未完成的内容",
            )
            Spacer(Modifier.height(22.dp))
            OnboardingCard {
                ToggleRow(
                    title = "加入抢先体验计划",
                    subtitle = "加入后可以提前收到测试版本与新功能",
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
        }
        Spacer(Modifier.height(16.dp))
        BottomActions(
            nextLabel = "完成",
            onBack = onBack,
            onNext = onFinish,
        )
    }
}

@Composable
internal fun OnboardingStepDots(step: Int) {
    val activeIndex = (step - 1).coerceIn(0, STEP_COUNT - 1)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEP_COUNT) { index ->
            val active = index == activeIndex
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
private fun ActionRow(
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
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
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OnboardingActionHeight),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingBackButton(onClick = onBack)
        AppButton(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Text(nextLabel)
        }
    }
}

@Composable
private fun OnboardingBackButton(onClick: () -> Unit) {
    AppButton(
        onClick = onClick,
        modifier = Modifier.size(OnboardingActionHeight),
        variant = AppButtonVariant.Secondary,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "上一步",
            modifier = Modifier.size(18.dp),
        )
    }
}
