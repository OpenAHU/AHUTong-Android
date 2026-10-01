package com.ahu.ahutong.ui.screen.main

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.data.mail.StudentMailWebLease
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.LiquidGlassAppHost
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import com.ahu.ahutong.ui.theme.pack.parseSlotOverrides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/** The full official mailbox has a private WebView profile, separate from all campus login WebViews. */
class StudentMailWebActivity : ComponentActivity() {
    private var web: WebView? = null
    private var lease: String? = null
    private var upload: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: Pair<String, String>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val downloadClient by lazy {
        AhuHttp.plain(readTimeoutSeconds = 60, callTimeoutSeconds = 300,
            retryOnConnectionFailure = false, followRedirects = false, followSslRedirects = false).build()
    }
    private val chooseFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        upload?.onReceiveValue(if (validLease()) WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data) else null)
        upload = null
    }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destination ->
        val download = pendingDownload
        pendingDownload = null
        if (destination != null && download != null && validLease()) {
            lifecycleScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        downloadClient.newCall(Request.Builder().url(download.first)
                            .header("Cookie", download.second).build()).execute().use { response ->
                            check(response.isSuccessful && validLease())
                            val body = response.body ?: error("missing body")
                            contentResolver.openOutputStream(destination, "wt")!!.use { output ->
                                body.byteStream().use { it.copyTo(output) }
                            }
                            check(validLease())
                        }
                    }.isSuccess
                }
                if (!saved) runCatching { contentResolver.delete(destination, null, null) }
                toast(if (saved) "附件已保存" else "附件下载未完成，请重新连接后重试")
            }
        }
    }
    private val sessionCheck = object : Runnable {
        override fun run() {
            if (!validLease()) { toast("邮箱登录已变化，请重新打开网页版"); finish() }
            else handler.postDelayed(this, 2_000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { finish(); return }
        if (!profileInitialized) {
            WebView.setDataDirectorySuffix("student_mail")
            profileInitialized = true
        }
        lease = intent.getStringExtra(StudentMailWebLease.KEY_LEASE)
        val session = call(StudentMailWebLease.METHOD_OPEN)
        val sid = session?.getString("sid")
        if (session?.getBoolean("valid") != true || sid.isNullOrBlank()) { finish(); return }

        val presentation = PreferencesManager(applicationContext).getStartupThemePreferences()
        val view = WebView(this)
        web = view
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        StudentMailWebViewport.configure(view)
        view.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft && validLease() && trustedMailUrl(view.url.orEmpty())) {
                StudentMailWebViewport.fit(view)
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!validLease()) { finish(); return true }
                if (trustedMailUrl(request.url.toString())) return false
                if (request.hasGesture() && request.url.scheme == "https") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                }
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!validLease() || !trustedMailUrl(url)) { view.stopLoading(); finish() }
            }
            override fun onPageCommitVisible(view: WebView, url: String) {
                if (validLease() && trustedMailUrl(url) && trustedMailUrl(view.url.orEmpty())) {
                    StudentMailWebViewport.fit(view)
                }
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (validLease() && trustedMailUrl(url) && trustedMailUrl(view.url.orEmpty())) {
                    StudentMailWebViewport.fit(view)
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                toast("邮箱连接证书验证失败")
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage) = true
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                if (!validLease()) { callback.onReceiveValue(null); finish(); return true }
                upload?.onReceiveValue(null)
                upload = callback
                return try { chooseFile.launch(params.createIntent()); true } catch (_: Exception) {
                    upload?.onReceiveValue(null); upload = null; false
                }
            }
        }
        view.setDownloadListener { url, _, disposition, _, _ ->
            if (!validLease() || !trustedMailUrl(url)) {
                toast("此附件下载地址暂不支持，请返回原生邮箱重新连接")
            } else {
                pendingDownload = url to CookieManager.getInstance().getCookie(url).orEmpty()
                val name = URLUtil.guessFileName(url, disposition, null).substringAfterLast('/').substringAfterLast('\\').take(160)
                saveFile.launch(name.ifBlank { "attachment" })
            }
        }
        setContent {
            AHUTheme(AhuThemeConfig(presentation?.appUiTheme ?: AppUiTheme.RADIANT,
                presentation?.themeColor, presentation?.themeMode ?: AppThemeMode.FOLLOW_SYSTEM,
                componentSlotOverrides = parseSlotOverrides(presentation?.slotOverrides.orEmpty()))) {
                StudentMailWebPage(view, onBack = { finish() }, onFit = {
                    if (validLease() && trustedMailUrl(view.url.orEmpty())) StudentMailWebViewport.fit(view)
                })
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (view.canGoBack()) view.goBack() else finish() }
        })

        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setAcceptThirdPartyCookies(view, false)
        manager.removeAllCookies {
            if (isFinishing || !validLease()) return@removeAllCookies
            WebStorage.getInstance().deleteAllData()
            val cookies = session.getStringArrayList("cookies").orEmpty()
            fun install(index: Int) {
                if (isFinishing || !validLease()) return
                if (index >= cookies.size) {
                    val url = Uri.parse("https://mail.stu.ahu.edu.cn/static/sirius-web/jump/index.html").buildUpon()
                        .appendQueryParameter("sid", sid).appendQueryParameter("from", "app")
                        .appendQueryParameter("hl", "zh").build()
                    view.loadUrl(url.toString())
                } else manager.setCookie("https://mail.stu.ahu.edu.cn/", cookies[index]) { install(index + 1) }
            }
            install(0)
        }
        handler.postDelayed(sessionCheck, 2_000)
    }

    private fun call(method: String): Bundle? = runCatching {
        contentResolver.call(Uri.parse("content://$packageName.student_mail_session"), method, lease, null)
    }.getOrNull()
    private fun validLease() = call(StudentMailWebLease.METHOD_CHECK)?.getBoolean("valid") == true
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        handler.removeCallbacks(sessionCheck)
        upload?.onReceiveValue(null)
        upload = null
        pendingDownload = null
        downloadClient.dispatcher.cancelAll()
        web?.apply { stopLoading(); clearHistory(); clearCache(true); destroy() }
        web = null
        if (profileInitialized) {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
        call(StudentMailWebLease.METHOD_CLOSE)
        super.onDestroy()
    }

    companion object {
        private var profileInitialized = false
        internal fun trustedMailUrl(value: String): Boolean = value.toHttpUrlOrNull()?.let {
            it.scheme == "https" && it.host == "mail.stu.ahu.edu.cn" && it.port == 443 &&
                it.username.isEmpty() && it.password.isEmpty()
        } == true
    }
}

@Composable
internal fun StudentMailWebPage(view: WebView, onBack: () -> Unit, onFit: () -> Unit) {
    LiquidGlassAppHost(Modifier.fillMaxSize()) {
        val actions = if (LocalAppUiTheme.current in setOf(AppUiTheme.RADIANT, AppUiTheme.LIQUID_GLASS))
            listOf(TrailingAction(Icons.AutoMirrored.Filled.ArrowBack, "返回原生邮箱", onClick = onBack)) else emptyList()
        AppPageScaffold(title = "邮箱完整版", subtitle = "双指缩放 · 横向查看", onBack = onBack,
            actions = actions, trailingContent = {
                AppButton(onFit, variant = AppButtonVariant.Secondary) { Text("适应页面") }
            }, freeContent = {
                AndroidView(factory = {
                    // WRAP_CONTENT makes Chromium resolve viewport-height units to zero in this host.
                    view.apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT) }
                }, modifier = Modifier.fillMaxSize())
            })
    }
}
