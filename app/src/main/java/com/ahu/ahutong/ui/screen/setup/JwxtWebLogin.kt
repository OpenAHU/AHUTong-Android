package com.ahu.ahutong.ui.screen.setup

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.login.isCasPostLoginUrl
import com.google.gson.Gson
import org.json.JSONObject

private const val JWXT_LOGIN_URL = "https://jw.ahu.edu.cn/student/sso/login"
private const val JWXT_HOST = "jw.ahu.edu.cn"
private const val CAS_HOST = "one.ahu.edu.cn"
private const val JWXT_HOME_PATH = "/student/home"
private const val JWXT_LOGIN_PATH = "/student/sso/login"
private const val CAS_LOGIN_PATH = "/cas/login"
private const val INTERACTION_FALLBACK_DELAY_MS = 12_000L
private const val JWXT_LOGIN_TIMEOUT_MS = 45_000L

private val WEB_LOGIN_COOKIE_URLS = listOf(
    "https://jw.ahu.edu.cn/",
    "https://jw.ahu.edu.cn/student/",
    "https://jw.ahu.edu.cn/student/home",
    "https://one.ahu.edu.cn/",
    "https://one.ahu.edu.cn/cas/",
    "https://one.ahu.edu.cn/cas/login"
)

@Composable
fun JwxtWebLogin(
    userID: String,
    password: String,
    onInteractionRequired: () -> Unit,
    onVerified: (String) -> Unit,
    onFailed: (String) -> Unit
) {
    val webViewState = remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewState.value?.stopLoading()
            webViewState.value?.destroy()
            webViewState.value = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            createJwxtLoginWebView(
                context = context,
                userID = userID,
                password = password,
                onInteractionRequired = onInteractionRequired,
                onVerified = {
                    val cookiesJson = captureWebLoginCookies()
                    if (cookiesJson == "[]") {
                        onFailed("未获取到教务登录会话，请重试")
                    } else {
                        onVerified(cookiesJson)
                    }
                },
                onFailed = onFailed
            ).also { created ->
                webViewState.value = created
                clearWebLoginCookies {
                    created.post {
                        runCatching {
                            created.loadUrl(JWXT_LOGIN_URL)
                        }
                    }
                }
            }
        },
        update = { webViewState.value = it }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createJwxtLoginWebView(
    context: android.content.Context,
    userID: String,
    password: String,
    onInteractionRequired: () -> Unit,
    onVerified: () -> Unit,
    onFailed: (String) -> Unit
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString = AHURepository.jwxtBrowserUserAgent

        val webCookieManager = CookieManager.getInstance()
        webCookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webCookieManager.setAcceptThirdPartyCookies(this, true)
        }

        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            private var autoSubmitAttempted = false
            private var interactionFallbackScheduled = false
            private var completed = false
            private var mainFrameNavigationId = 0
            private var ssoFallbackNavigationId = -1
            private val postLoginSsoStages = mutableSetOf<String>()

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                mainFrameNavigationId++
                super.onPageStarted(view, url, favicon)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url ?: return false
                if (target.scheme != "https" || !isAhuHost(target.host)) {
                    fail("教务登录跳转到了非学校页面，已停止验证")
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (completed || view == null || url.isNullOrBlank()) return

                val uri = Uri.parse(url)
                when {
                    uri.host.equals(JWXT_HOST, ignoreCase = true) &&
                        uri.path?.trimEnd('/') == JWXT_HOME_PATH -> {
                        completed = true
                        CookieManager.getInstance().flush()
                        onVerified()
                    }

                    uri.host.equals(JWXT_HOST, ignoreCase = true) &&
                        uri.path?.trimEnd('/') == JWXT_LOGIN_PATH -> {
                        scheduleSsoFallback(view)
                    }

                    isCasPostLoginUrl(url) -> {
                        val stage = if (uri.path.orEmpty().startsWith("/tp_up/")) "portal" else "guide"
                        if (postLoginSsoStages.add(stage)) {
                            view.loadUrl(JWXT_LOGIN_URL)
                        } else {
                            // 学校仍强制要求设置手机号时，把页面交给用户。
                            onInteractionRequired()
                        }
                    }

                    uri.host.equals(CAS_HOST, ignoreCase = true) &&
                        uri.path?.trimEnd('/') == CAS_LOGIN_PATH -> {
                        if (autoSubmitAttempted) {
                            onInteractionRequired()
                        } else {
                            autoSubmitAttempted = true
                            submitCasLogin(view, userID, password) { submitted ->
                                if (!submitted) {
                                    onInteractionRequired()
                                } else if (!interactionFallbackScheduled) {
                                    interactionFallbackScheduled = true
                                    view.postDelayed({
                                        if (!completed && isCasLoginUrl(view.url)) {
                                            onInteractionRequired()
                                        }
                                    }, INTERACTION_FALLBACK_DELAY_MS)
                                }
                            }
                        }
                    }
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                val isExpectedChallenge = request?.isForMainFrame == true &&
                    request.url.host.equals(JWXT_HOST, ignoreCase = true) &&
                    errorResponse?.statusCode == 412
                if (isExpectedChallenge) {
                    if (view != null && isJwxtSsoUrl(request.url.toString())) {
                        scheduleSsoFallback(view)
                    }
                } else if (request?.isForMainFrame == true) {
                    fail("教务页面返回异常（${errorResponse?.statusCode ?: "未知状态"}）")
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    fail(error?.description?.toString() ?: "教务页面加载失败，请重试")
                }
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
                fail("教务页面证书校验失败，已停止验证")
            }

            private fun scheduleSsoFallback(view: WebView) {
                val navigationId = mainFrameNavigationId
                if (ssoFallbackNavigationId == navigationId) return
                ssoFallbackNavigationId = navigationId

                view.postDelayed({
                    if (isCurrentSsoNavigationStalled(view, navigationId)) {
                        Log.w("JwxtWebLogin", "JWXT SSO navigation stalled; showing WebView")
                        onInteractionRequired()
                    }
                }, INTERACTION_FALLBACK_DELAY_MS)

                view.postDelayed({
                    if (isCurrentSsoNavigationStalled(view, navigationId)) {
                        view.stopLoading()
                        fail("教务安全验证超时，请检查网络后重试")
                    }
                }, JWXT_LOGIN_TIMEOUT_MS)
            }

            private fun isCurrentSsoNavigationStalled(
                view: WebView,
                navigationId: Int
            ): Boolean {
                return !completed &&
                    view.isAttachedToWindow &&
                    mainFrameNavigationId == navigationId &&
                    isJwxtSsoUrl(view.url)
            }

            private fun fail(message: String) {
                if (completed) return
                completed = true
                onFailed(message)
            }
        }
    }
}

private fun submitCasLogin(
    webView: WebView,
    userID: String,
    password: String,
    onResult: (Boolean) -> Unit
) {
    val quotedUserID = JSONObject.quote(userID)
    val quotedPassword = JSONObject.quote(password)
    val script = """
        (function() {
            var username = document.getElementById('un');
            var password = document.getElementById('pd');
            var submit = document.getElementById('index_login_btn');
            if (!username || !password || !submit) return 'missing';
            username.value = $quotedUserID;
            password.value = $quotedPassword;
            username.dispatchEvent(new Event('input', { bubbles: true }));
            password.dispatchEvent(new Event('input', { bubbles: true }));
            submit.click();
            return 'submitted';
        })();
    """.trimIndent()

    webView.evaluateJavascript(script) { result ->
        onResult(result == "\"submitted\"")
    }
}

private fun clearWebLoginCookies(onComplete: () -> Unit) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.removeAllCookies {
        cookieManager.flush()
        onComplete()
    }
}

private fun captureWebLoginCookies(): String {
    val cookieManager = CookieManager.getInstance()
    cookieManager.flush()

    val cookies = linkedMapOf<String, Map<String, Any>>()
    WEB_LOGIN_COOKIE_URLS.forEach { url ->
        val domain = Uri.parse(url).host ?: return@forEach
        parseWebLoginCookiePairs(cookieManager.getCookie(url)).forEach { (name, value) ->
            cookies["$domain|$name"] = mapOf(
                "name" to name,
                "value" to value,
                "domain" to domain,
                "path" to "/",
                "secure" to true,
                "http_only" to false
            )
        }
    }
    Log.d("JwxtWebLogin", "Captured ${cookies.size} WebView cookies; names and values suppressed")
    return Gson().toJson(cookies.values)
}

private fun isAhuHost(host: String?): Boolean {
    val normalizedHost = host.orEmpty().lowercase()
    return normalizedHost == "ahu.edu.cn" || normalizedHost.endsWith(".ahu.edu.cn")
}

private fun isCasLoginUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = Uri.parse(url)
    return uri.host.equals(CAS_HOST, ignoreCase = true) &&
        uri.path?.trimEnd('/') == CAS_LOGIN_PATH
}
