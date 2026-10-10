package com.ahu.ahutong

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ahu.ahutong.data.session.SavedAccounts
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.ui.screen.setup.Login
import com.ahu.ahutong.ui.screen.setup.canCancelLogin
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 取消时保留原页面；登录成功后重建主界面，隔离不同账号的业务 ViewModel。 */
@AndroidEntryPoint
class AccountLoginActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val preferencesViewModel: PreferencesViewModel by viewModels()
    private var ready by mutableStateOf(false)
    private var initialUserId = ""
    private var initialPassword = ""
    private var originalAccountId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        originalAccountId = if (savedInstanceState == null) SessionStore.currentUser()?.xh
            else savedInstanceState.getString(STATE_ORIGINAL_ACCOUNT_ID)
        initialUserId = intent.getStringExtra(EXTRA_ACCOUNT_ID).orEmpty()
        initialPassword = SavedAccounts.password(initialUserId).orEmpty()
        if (initialUserId.isNotEmpty() && initialPassword.isEmpty()) {
            notice("登录信息不可用，请重新输入密码")
        }
        lifecycleScope.launch {
            clearWebViewCookies()
            ready = true
        }
        setContent {
            val uiTheme by preferencesViewModel.appUiTheme.collectAsState()
            val themeColor by preferencesViewModel.themeColor.collectAsState()
            val themeMode by preferencesViewModel.appThemeMode.collectAsState()
            val themeReady by preferencesViewModel.isUiThemePreferenceReady.collectAsState()
            val overrides by preferencesViewModel.componentSlotOverrides.collectAsState()
            AHUTheme(AhuThemeConfig(
                appUiTheme = uiTheme,
                themeColorHex = themeColor,
                themeMode = themeMode,
                isPreferenceReady = themeReady,
                componentSlotOverrides = overrides
            )) {
                if (ready) {
                    Login(
                        loginViewModel = loginViewModel,
                        initialUserId = initialUserId,
                        initialPassword = initialPassword,
                        autoLogin = initialUserId.isNotEmpty() && initialPassword.isNotEmpty(),
                        onBack = { attempted ->
                            if (canCancelLogin(
                                    originalAccountId,
                                    SessionStore.currentUser()?.xh,
                                    AhuSessionState.status.value,
                                    attempted
                                )
                            ) {
                                finish()
                            } else {
                                notice("请先完成登录，再进入应用")
                            }
                        },
                        onLoggedIn = {
                            // 原登录流程完成 Keystore 与身份落盘后，才纳入快速切换列表。
                            runCatching { SavedAccounts.rememberCurrent() }.onFailure {
                                notice("登录成功，但无法保存快速切换信息，请稍后重试")
                            }
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    private suspend fun clearWebViewCookies() {
        val cookies = android.webkit.CookieManager.getInstance()
        suspendCancellableCoroutine<Unit> { continuation ->
            cookies.removeAllCookies {
                cookies.flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_ORIGINAL_ACCOUNT_ID, originalAccountId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        initialPassword = ""
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "saved_account_id"
        private const val STATE_ORIGINAL_ACCOUNT_ID = "original_account_id"
    }
}
