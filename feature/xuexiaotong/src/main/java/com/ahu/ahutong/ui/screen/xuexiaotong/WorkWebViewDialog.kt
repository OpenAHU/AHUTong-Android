package com.ahu.ahutong.ui.screen.xuexiaotong

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator

/**
 * 作业题目查看（WebView 全屏弹窗）。
 *
 * 纪律（work-webview-dev-doc §4 红线）：
 * - 只读：不注入 JS 接口、不引导提交；
 * - 只在用户点击这一刻加载一次，不预取、单实例；
 * - 命中风控验证页（antispiderShowVerify）或登录页即熔断提示，绝不自动重试。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorkWebViewDialog(
    work: Work,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf<String?>(null) }
    var pageError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    val webView = remember {
        // Cookie 注入（真实格式是纯 name=value; ...，全部按 .chaoxing.com 域落盘——
        // 开发文档 §3.3 的 name|domain 格式是错的，按本实现为准）
        val cm = android.webkit.CookieManager.getInstance()
        cm.setAcceptCookie(true)
        Store.getCookie().split("; ").forEach { entry ->
            if (entry.contains("=")) {
                cm.setCookie("https://.chaoxing.com", entry)
            }
        }
        cm.flush()

        WebView(context).apply {
            settings.javaScriptEnabled = true // 页面渲染需要（UEditor 等），但不注入任何 JS 接口
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // 题图是 http 图床
            webViewClient = object : WebViewClient() {
                /** 熔断检测：302 重定向不回调 shouldOverrideUrlLoading，必须在 Started/Finished 查最终 URL。 */
                private fun circuitBreak(view: WebView, url: String) {
                    when {
                        url.contains("antispiderShowVerify") -> {
                            view.stopLoading()
                            blocked = "超星风控验证已触发，请过几小时再试"
                        }
                        url.contains("passport2.chaoxing.com/login") -> {
                            view.stopLoading()
                            blocked = "学习通登录态已失效，请回学习通页重新登录"
                        }
                    }
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    loading = true
                    circuitBreak(view, url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading = false
                    circuitBreak(view, url)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        loading = false
                        pageError = true
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val host = request.url.host ?: return true
                    // 域白名单：仅放行超星站内导航；外链交系统浏览器
                    if (host == "chaoxing.com" || host.endsWith(".chaoxing.com")) return false
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    }
                    return true
                }
            }
            loadUrl(
                work.detailUrl,
                mapOf(
                    "Referer" to
                        "https://mooc1.chaoxing.com/mooc2/work/list?courseId=${work.courseId}"
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = work.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (loading) {
                        AppCircularProgressIndicator(
                            size = 18.dp,
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }
                when {
                    blocked != null -> WebViewNotice(
                        message = blocked.orEmpty(),
                        onDismiss = onDismiss
                    )
                    pageError -> WebViewNotice(
                        message = "页面加载失败，请检查网络后重试",
                        onDismiss = onDismiss
                    )
                    else -> AndroidView(
                        factory = { webView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WebViewNotice(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        AppButton(onClick = onDismiss, variant = AppButtonVariant.Secondary) {
            Text("关闭")
        }
    }
}
