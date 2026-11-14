package com.ahu.ahutong.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.assistant.model.AssistantModelLibrary
import com.ahu.ahutong.assistant.model.OfficialTrial
import com.ahu.ahutong.assistant.model.TrialException
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppPageHeader
import java.io.IOException
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
    data class Waiting(val ticket: String, val openBrowser: Boolean = true) : OfficialTrialState
    data class Registered(val message: String) : OfficialTrialState
    data class Error(val message: String) : OfficialTrialState
}

internal class OfficialTrialViewModel : ViewModel() {
    private val identity = OfficialTrial.currentIdentity()
    private val mutableState = MutableStateFlow<OfficialTrialState>(OfficialTrialState.Preparing)
    val state = mutableState.asStateFlow()
    private var registration: Job? = null

    init {
        startRegistration()
    }

    fun startRegistration() {
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
                var message = "已启用官方试用服务器。"
                if (OfficialTrial.apiKey() == null) {
                    val session = OfficialTrial.prepareBrowserRegistration(owner)
                    mutableState.value = OfficialTrialState.Waiting(session.browserTicket)
                    val result = withTimeoutOrNull(session.expiresIn * 1_000L) {
                        var pending: OfficialTrial.BrowserResult? = null
                        while (pending == null) {
                            delay(2_000)
                            try {
                                pending = OfficialTrial.pollBrowserRegistration(owner, session)
                            } catch (error: TrialException) {
                                throw error
                            } catch (_: IOException) {
                                // Keep polling through brief connection interruptions.
                            }
                        }
                        pending
                    } ?: throw TrialException("浏览器验证已过期，请重新验证。")
                    result.points?.let { message = "已启用官方试用服务器，当前积分 ${it.toPlainString()}。" }
                }
                if (owner != OfficialTrial.currentIdentity() || !AssistantModelLibrary.selectOfficialTrial()) {
                    throw TrialException("当前校园账号不可用，请返回后重新登录。")
                }
                mutableState.value = OfficialTrialState.Registered(message)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = OfficialTrialState.Error(
                    (error as? TrialException)?.message ?: "无法连接试用网关，请检查网络后重试。",
                )
            }
        }
    }

    fun browserOpened(waiting: OfficialTrialState.Waiting) {
        mutableState.compareAndSet(waiting, waiting.copy(openBrowser = false))
    }

    fun browserUnavailable() {
        if (mutableState.value is OfficialTrialState.Waiting) {
            registration?.cancel()
            mutableState.value = OfficialTrialState.Error("无法打开系统浏览器，请确认已安装浏览器后重试。")
        }
    }
}

@Composable
internal fun OfficialTrialScreen(onBack: () -> Unit, model: OfficialTrialViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val theme = if (isSystemInDarkTheme()) "dark" else "light"

    fun openBrowser(waiting: OfficialTrialState.Waiting) {
        val uri = Uri.parse("${OfficialTrial.ORIGIN}/v1/client/captcha").buildUpon()
            .appendQueryParameter("flow", "trial")
            .appendQueryParameter("theme", theme)
            .appendQueryParameter("lang", "cn")
            .encodedFragment("ticket=${waiting.ticket}")
            .build()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            model.browserOpened(waiting)
        } catch (_: ActivityNotFoundException) {
            model.browserUnavailable()
        } catch (_: SecurityException) {
            model.browserUnavailable()
        }
    }

    LaunchedEffect(state) {
        val waiting = state as? OfficialTrialState.Waiting
        if (waiting?.openBrowser == true) openBrowser(waiting)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        AppPageHeader(title = "官方试用服务器", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val current = state) {
                OfficialTrialState.LoginRequired -> Text("登录安大通后即可开启试用。")
                OfficialTrialState.Preparing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在准备注册…")
                }
                is OfficialTrialState.Waiting -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("请在浏览器完成验证，然后返回安大通。")
                    AppButton(modifier = Modifier.fillMaxWidth(), onClick = { openBrowser(current) }) {
                        Text("打开浏览器")
                    }
                    AppButton(modifier = Modifier.fillMaxWidth(), onClick = model::startRegistration) {
                        Text("重新验证")
                    }
                }
                is OfficialTrialState.Registered -> {
                    Text(current.message)
                    AppButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回 AI 设置") }
                }
                is OfficialTrialState.Error -> {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    AppButton(modifier = Modifier.fillMaxWidth(), onClick = model::startRegistration) {
                        Text("重新验证")
                    }
                }
            }
        }
    }
}
