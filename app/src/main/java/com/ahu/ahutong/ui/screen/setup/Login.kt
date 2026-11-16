package com.ahu.ahutong.ui.screen.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import com.ahu.ahutong.ui.haptics.HapticIconButton as IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import com.ahu.ahutong.ui.markdown.AppMarkdown
import com.ahu.ahutong.ui.state.LoginState
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Login(
    loginViewModel: LoginViewModel = viewModel(),
    initialUserId: String = "",
    initialPassword: String = "",
    autoLogin: Boolean = false,
    onBack: ((Boolean) -> Unit)? = null,
    consentRequired: Boolean = false,
    onConsentAccepted: () -> Unit = {},
    onboardingStep: Int? = null,
    onLoggedIn: () -> Unit
) {
    var userID by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialUserId))
    }
    var password by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialPassword))
    }
    var focusIndex by rememberSaveable { mutableStateOf(0) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showWebLoginContent by rememberSaveable { mutableStateOf(false) }
    var legalConsent by rememberSaveable(consentRequired) { mutableStateOf(!consentRequired) }
    var consentHint by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val policyMarkdown by produceState<String?>(null, showPolicy) {
        if (!showPolicy) return@produceState
        value = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
        }
    }
    val activity = LocalActivity.current
    var initialLoginStarted by rememberSaveable { mutableStateOf(false) }
    var loginAttempted by rememberSaveable { mutableStateOf(false) }
    val sessionStatus by AhuSessionState.status.collectAsState()
    LaunchedEffect(Unit) {
        if (autoLogin && !initialLoginStarted && loginViewModel.state == LoginState.Idle) {
            initialLoginStarted = true
            loginAttempted = true
            LoginNavigationGate.beginAuthentication()
            loginViewModel.loginWithCrawler(userID.text, password.text)
        }
    }
    LaunchedEffect(loginViewModel.state) {
        if (loginViewModel.state != LoginState.WebVerification) {
            showWebLoginContent = false
        }
        if (loginViewModel.state == LoginState.Failed) {
            delay(1000)
            loginViewModel.state = LoginState.Idle
        } else if (loginViewModel.state == LoginState.Succeeded) {
            delay(500)
            loginViewModel.state = LoginState.Idle
            LoginNavigationGate.completeAuthentication()
            onLoggedIn()
        }
    }

    val handleBack: () -> Unit = {
        if (showWebLoginContent) {
            loginViewModel.failWebVerification("已取消教务安全验证")
        } else if (loginViewModel.state == LoginState.InProgress ||
            loginViewModel.state == LoginState.Succeeded
        ) {
            // 认证与身份落盘完成前，不能返回旧账号的页面。
            Unit
        } else if (onBack != null) {
            onBack(loginAttempted)
        } else {
            activity?.finish()
        }
    }
    BackHandler(onBack = handleBack)

    val submitLogin: () -> Unit = {
        if (consentRequired && !legalConsent) {
            consentHint = true
        } else {
            consentHint = false
            if (consentRequired) onConsentAccepted()
            logIn(loginViewModel, userID.text, password.text) { loginAttempted = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        if (loginViewModel.state == LoginState.WebVerification) {
            JwxtWebLogin(
                userID = userID.text,
                password = password.text,
                onInteractionRequired = { showWebLoginContent = true },
                onVerified = { cookiesJson ->
                    loginViewModel.completeWebVerification(
                        cookiesJson = cookiesJson,
                        password = password.text
                    )
                },
                onFailed = loginViewModel::failWebVerification
            )
        }

        if (!showWebLoginContent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appLiquidGlassSceneBackground(MaterialTheme.colorScheme.background)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                onboardingStep?.let { OnboardingStepDots(it) }
                Spacer(Modifier.height(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "登录安大通",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        text = "使用智慧安大账号登录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sessionStatus == AhuSessionState.Status.Expired) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "登录已过期，请重新登录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(20.dp))
                LoginEmoji(focusIndex = focusIndex)
                Spacer(Modifier.height(20.dp))

                BasicTextField(
                    value = userID,
                    onValueChange = { userID = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .appLiquidGlassSurface(
                            shape = ContinuousCapsule,
                            fallbackColor = 100.n1 withNight 20.n1,
                            level = LiquidGlassSurfaceLevel.Control
                        )
                        .onFocusChanged { if (it.isFocused) focusIndex = 0 }
                        .autofill(AutofillType.Username) { userID = userID.copy(text = it) },
                    textStyle = LocalTextStyle.current.copy(
                        color = LocalContentColor.current,
                        fontFamily = FontFamily.Monospace
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(LocalContentColor.current)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(64.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        it()
                        if (userID.text.isBlank()) {
                            Text(
                                text = stringResource(id = R.string.hint_userid),
                                color = 30.n1 withNight 80.n1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .appLiquidGlassSurface(
                            shape = ContinuousCapsule,
                            fallbackColor = 100.n1 withNight 20.n1,
                            level = LiquidGlassSurfaceLevel.Control
                        )
                        .onFocusChanged { if (it.isFocused) focusIndex = 1 }
                        .autofill(AutofillType.Password) { password = password.copy(text = it) },
                    textStyle = LocalTextStyle.current.copy(
                        color = LocalContentColor.current,
                        fontFamily = FontFamily.Monospace
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    cursorBrush = SolidColor(LocalContentColor.current)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(64.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(Modifier.fillMaxWidth().padding(end = 44.dp)) { it() }
                        if (password.text.isBlank()) {
                            Text(
                                text = stringResource(id = R.string.hint_wisdom_password),
                                color = 30.n1 withNight 80.n1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
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

                if (consentRequired) {
                    Spacer(Modifier.height(12.dp))
                    ConsentRow(
                        checked = legalConsent,
                        showHint = consentHint,
                        onToggle = {
                            legalConsent = !legalConsent
                            if (legalConsent) consentHint = false
                        },
                        onOpenPolicy = { showPolicy = true },
                    )
                }

                val errorText = loginViewModel.failureMessage
                    .takeIf { loginViewModel.state == LoginState.Failed }
                if (errorText != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
                val successText = loginViewModel.succeedMessage
                    .takeIf { loginViewModel.state == LoginState.Succeeded }
                if (successText != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = successText,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(20.dp))
                val busy = loginViewModel.state == LoginState.InProgress ||
                    loginViewModel.state == LoginState.Succeeded
                AppButton(
                    onClick = submitLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !busy
                ) {
                    if (busy) {
                        AppCircularProgressIndicator(
                            size = 20.dp,
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("登录中…", fontSize = 16.sp)
                    } else {
                        Text("登录", fontSize = 16.sp)
                    }
                }
            }
        }

        if (showPolicy) {
            AppModalBottomSheet(title = "隐私政策", onDismissRequest = { showPolicy = false }) {
                if (policyMarkdown == null) {
                    Text("政策加载中…", modifier = Modifier.padding(24.dp))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        AppMarkdown(markdown = policyMarkdown.orEmpty(), lazy = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginEmoji(focusIndex: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier.height(128.dp)
        ) {
            Image(
                painter = if (focusIndex == 0) {
                    painterResource(id = R.mipmap.emoji_username)
                } else {
                    painterResource(id = R.mipmap.emoji_password)
                },
                contentDescription = null
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = focusIndex == 1,
                modifier = Modifier.offset(y = 64.dp),
                enter = fadeIn() + expandIn(),
                exit = shrinkOut() + fadeOut()
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.emoji_left_hand),
                    contentDescription = null
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = focusIndex == 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset((-16).dp, 64.dp),
                enter = fadeIn() + expandIn(),
                exit = shrinkOut() + fadeOut()
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.emoji_right_hand),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    showHint: Boolean,
    onToggle: () -> Unit,
    onOpenPolicy: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val toggleInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val policyInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = toggleInteraction,
                        indication = null,
                        onClick = onToggle,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (checked) colors.primary else androidx.compose.ui.graphics.Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (checked) colors.primary else colors.outline,
                            shape = RoundedCornerShape(5.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = colors.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "我已阅读并同意",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                text = "《隐私政策》",
                modifier = Modifier
                    .clickable(
                        interactionSource = policyInteraction,
                        indication = null,
                        onClick = onOpenPolicy,
                    )
                    .padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
            )
        }
        if (showHint && !checked) {
            Text(
                text = "请先勾选同意《隐私政策》",
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }
    }
}

private fun logIn(
    loginViewModel: LoginViewModel,
    userID: String,
    password: String,
    onAuthenticationStarted: () -> Unit
) {
    if (userID.isBlank() || password.isBlank()) {
        loginViewModel.state = LoginState.Failed
        loginViewModel.failureMessage = "请将信息填写完整"
    } else {
        onAuthenticationStarted()
        LoginNavigationGate.beginAuthentication()
        loginViewModel.loginWithCrawler(userID = userID, password = password)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.autofill(
    autofillTypes: AutofillType,
    onFill: ((String) -> Unit)
) = composed {
    val autofill = LocalAutofill.current
    val autofillNode = AutofillNode(
        onFill = onFill,
        autofillTypes = listOf(autofillTypes)
    )
    LocalAutofillTree.current += autofillNode

    this
        .onGloballyPositioned {
            autofillNode.boundingBox = it.boundsInWindow()
        }
        .onFocusChanged { focusState ->
            autofill?.run {
                if (focusState.isFocused) {
                    requestAutofillForNode(autofillNode)
                } else {
                    cancelAutofillForNode(autofillNode)
                }
            }
        }
}