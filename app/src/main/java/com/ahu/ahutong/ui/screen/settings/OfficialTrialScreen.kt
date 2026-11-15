package com.ahu.ahutong.ui.screen.settings

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.assistant.model.AssistantModelLibrary
import com.ahu.ahutong.assistant.model.OfficialTrial
import com.ahu.ahutong.assistant.model.TrialException
import com.ahu.ahutong.data.preview.EarlyAccessIdentity
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppPageHeader
import com.ahu.ahutong.ui.components.AppTextField
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface OfficialTrialState {
    data object LoginRequired : OfficialTrialState
    data object Preparing : OfficialTrialState
    data class NewAccount(val session: OfficialTrial.RegistrationSession) : OfficialTrialState
    data class Verifying(val session: OfficialTrial.RegistrationSession) : OfficialTrialState
    data class Registered(val message: String) : OfficialTrialState
    data class Error(val message: String) : OfficialTrialState
    data class Recovery(
        val session: OfficialTrial.RecoverySession? = null,
        val retryAt: Long = 0,
        val busy: Boolean = false,
        val error: String? = null,
    ) : OfficialTrialState
}

internal class OfficialTrialViewModel : ViewModel() {
    private val identity = OfficialTrial.currentIdentity()
    private val mutableState = MutableStateFlow<OfficialTrialState>(OfficialTrialState.Preparing)
    val state = mutableState.asStateFlow()
    private var registration: Job? = null

    init {
        detectEntry()
    }

    fun detectEntry() {
        val owner = identity
        if (owner == null) {
            mutableState.value = OfficialTrialState.LoginRequired
            return
        }
        registration?.cancel()
        mutableState.value = OfficialTrialState.Preparing
        registration = viewModelScope.launch {
            try {
                if (owner != OfficialTrial.currentIdentity()) {
                    throw TrialException("账号已切换，请重新打开此页面。")
                }
                val installed = OfficialTrial.existingCredential(owner)
                mutableState.value = if (installed != null) {
                    selectOfficialTrial("已启用官方试用服务器", installed.points)
                } else {
                    when (val entry = OfficialTrial.resolveEntry(owner)) {
                        is OfficialTrial.TrialEntry.Register -> OfficialTrialState.NewAccount(entry.session)
                        OfficialTrial.TrialEntry.Recover -> OfficialTrialState.Recovery()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = OfficialTrialState.Error(
                    (error as? TrialException)?.message ?: "无法连接试用网关，请检查网络后重试。",
                )
            }
        }
    }

    fun startVerification() {
        val owner = identity ?: return
        val session = (mutableState.value as? OfficialTrialState.NewAccount)?.session ?: return
        mutableState.value = OfficialTrialState.Verifying(session)
        registration = viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(session.expiresIn * 1_000L) {
                    var pending: OfficialTrial.CredentialResult? = null
                    while (pending == null) {
                        delay(2_000)
                        try {
                            pending = OfficialTrial.pollRegistration(owner, session)
                        } catch (error: TrialException) {
                            throw error
                        } catch (_: IOException) {
                            // Verify whether the gateway finished when the connection returns.
                        }
                    }
                    pending
                } ?: throw TrialException("验证码会话已过期，请重新验证。")
                mutableState.value = selectOfficialTrial("已启用官方试用服务器", result.points)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val trialError = error as? TrialException
                mutableState.value = if (trialError?.gatewayCode == "TRIAL_ALREADY_REGISTERED") {
                    OfficialTrialState.Recovery()
                } else {
                    OfficialTrialState.Error(trialError?.message ?: "无法完成试用注册，请重试。")
                }
            }
        }
    }

    fun sendRecoveryCode() = updateRecovery { owner, current ->
        if (SystemClock.elapsedRealtime() < current.retryAt) return@updateRecovery current
        val session = OfficialTrial.sendRecoveryCode(owner)
        OfficialTrialState.Recovery(session, SystemClock.elapsedRealtime() + session.retryAfter * 1_000L)
    }

    fun recover(code: String) = updateRecovery { owner, current ->
        val session = current.session ?: return@updateRecovery current
        val result = OfficialTrial.recover(owner, session, code)
        selectOfficialTrial("已恢复官方试用账号", result.points)
    }

    private fun updateRecovery(
        action: suspend (EarlyAccessIdentity, OfficialTrialState.Recovery) -> OfficialTrialState,
    ) {
        val owner = identity ?: return
        val current = mutableState.value as? OfficialTrialState.Recovery ?: return
        if (current.busy) return
        mutableState.value = current.copy(busy = true, error = null)
        registration = viewModelScope.launch {
            try {
                mutableState.value = action(owner, current)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = current.copy(
                    error = (error as? TrialException)?.message ?: "无法连接试用网关，请检查网络后重试。",
                )
            }
        }
    }

    private fun selectOfficialTrial(message: String, points: BigDecimal?): OfficialTrialState.Registered {
        if (identity != OfficialTrial.currentIdentity() || !AssistantModelLibrary.selectOfficialTrial()) {
            throw TrialException("当前校园账号不可用，请返回后重新登录。")
        }
        val balance = points?.let { "，当前积分 ${it.toPlainString()}" }.orEmpty()
        return OfficialTrialState.Registered("$message$balance。")
    }

    fun verificationUnavailable(session: OfficialTrial.RegistrationSession) {
        if ((mutableState.value as? OfficialTrialState.Verifying)?.session !== session) return
        registration?.cancel()
        mutableState.value = OfficialTrialState.Error("无法加载验证页面，请检查网络后重试。")
    }
}

@Composable
internal fun OfficialTrialScreen(onBack: () -> Unit, model: OfficialTrialViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val current = state

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        AppPageHeader(title = "官方试用服务器", onBack = onBack)
        if (current is OfficialTrialState.Verifying) {
            TrialCaptcha(
                session = current.session,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onUnavailable = { model.verificationUnavailable(current.session) },
            )
            AppButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                onClick = model::detectEntry,
            ) { Text("重新验证") }
            return@Column
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (current) {
                OfficialTrialState.LoginRequired -> Text("登录安大通后即可开启试用。")
                OfficialTrialState.Preparing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在确认试用账号状态…")
                }
                is OfficialTrialState.NewAccount -> {
                    AppButton(modifier = Modifier.fillMaxWidth(), onClick = model::startVerification) {
                        Text("开始验证并开通试用")
                    }
                }
                is OfficialTrialState.Registered -> {
                    Text(current.message)
                    AppButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回 AI 设置") }
                }
                is OfficialTrialState.Error -> {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    AppButton(modifier = Modifier.fillMaxWidth(), onClick = model::detectEntry) {
                        Text("重试")
                    }
                }
                is OfficialTrialState.Recovery -> TrialRecoveryForm(current, model)
                is OfficialTrialState.Verifying -> Unit
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TrialCaptcha(
    session: OfficialTrial.RegistrationSession,
    modifier: Modifier,
    onUnavailable: () -> Unit,
) {
    val theme = if (isSystemInDarkTheme()) "dark" else "light"
    val url = remember(session, theme) {
        Uri.parse("${OfficialTrial.ORIGIN}/v1/client/captcha").buildUpon()
            .appendQueryParameter("flow", "trial")
            .appendQueryParameter("embed", "1")
            .appendQueryParameter("theme", theme)
            .appendQueryParameter("lang", "cn")
            .encodedFragment("ticket=${session.ticket}")
            .build()
    }
    val onError by rememberUpdatedState(onUnavailable)
    key(url) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url
                            if (!request.isForMainFrame) return false
                            return target.scheme != url.scheme || target.host != url.host ||
                                target.port != url.port || target.path != url.path
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) onError()
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                            if (request.isForMainFrame) onError()
                        }
                    }
                    loadUrl(url.toString())
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            },
        )
    }
}

@Composable
private fun TrialRecoveryForm(current: OfficialTrialState.Recovery, model: OfficialTrialViewModel) {
    var code by remember(current.session) { mutableStateOf("") }
    var remaining by remember(current.retryAt) { mutableIntStateOf(0) }
    LaunchedEffect(current.retryAt) {
        do {
            remaining = ((current.retryAt - SystemClock.elapsedRealtime() + 999) / 1_000)
                .coerceAtLeast(0).toInt()
            if (remaining > 0) delay(250)
        } while (remaining > 0)
    }

    if (current.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (current.session == null) {
        Text("该账号已开通试用。验证码将发送到当前账号的安徽大学学生邮箱。")
    } else {
        Text("请打开学生邮箱查收验证码。验证码 10 分钟内有效。")
        AppTextField(
            value = code,
            onValueChange = { code = it.filter { digit -> digit in '0'..'9' }.take(8) },
            label = "8 位邮箱验证码",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !current.busy,
        )
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { model.recover(code) },
            enabled = code.length == 8 && !current.busy,
        ) {
            Text("恢复账号")
        }
    }
    current.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    AppButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = model::sendRecoveryCode,
        enabled = !current.busy && remaining == 0,
    ) {
        Text(when {
            remaining > 0 -> "${remaining} 秒后可重发"
            current.session == null -> "发送验证码"
            else -> "重新发送验证码"
        })
    }
}
