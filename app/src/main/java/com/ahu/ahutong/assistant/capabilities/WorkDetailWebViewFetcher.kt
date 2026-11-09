package com.ahu.ahutong.assistant.capabilities

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work
import com.google.gson.JsonParser
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 原生请求命中学习通风控时，用真实 WebView 会话再取一次题目 DOM。
 *
 * 只注入现有 Cookie、不注入 JS 接口；只把 .questionLi 的 HTML 取回，
 * 答案区/编辑器/知识点在 JS 里就删掉，避免把答案带进内存和模型。
 */
internal object WorkDetailWebViewFetcher {

    suspend fun fetch(work: Work): String = withContext(Dispatchers.Main.immediate) {
        val webView = WebView(AppEnvironmentHolder.context())
        try {
            configure(webView)
            injectCookies()
            load(webView, work)

            val deadline = SystemClock.elapsedRealtime() + POLL_TIMEOUT_MS
            var lastUrl = webView.url ?: work.detailUrl
            while (SystemClock.elapsedRealtime() < deadline) {
                lastUrl = webView.url ?: lastUrl
                if (lastUrl.contains("passport2.chaoxing.com/login")) {
                    throw IOException("学习通 WebView 跳到了登录页（${pageOf(lastUrl)}），请回学习通页重新登录")
                }
                val html = extract(webView)
                if (html.contains("questionLi")) {
                    return@withContext html
                }
                delay(POLL_INTERVAL_MS)
            }

            val page = pageOf(lastUrl)
            throw if (lastUrl.contains("antispiderShowVerify")) {
                IOException("学习通 WebView 仍被风控验证页拦住（$page），请过几小时再试")
            } else {
                IOException("学习通 WebView 未识别到题目（最终页 $page）")
            }
        } finally {
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
    }

    private suspend fun load(webView: WebView, work: Work) {
        val finished = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame && continuation.isActive) continuation.resume(Unit)
                    }
                }
                webView.loadUrl(
                    work.detailUrl,
                    mapOf(
                        "Referer" to
                            "https://mooc1.chaoxing.com/mooc2/work/list?courseId=${work.courseId}"
                    ),
                )
            }
        }
        if (finished == null) throw IOException("学习通 WebView 加载超时（${LOAD_TIMEOUT_MS / 1000} 秒）")
    }

    private suspend fun extract(webView: WebView): String =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(EXTRACT_JS) { raw ->
                if (continuation.isActive) continuation.resume(decode(raw))
            }
        }

    private fun decode(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching { JsonParser.parseString(raw).asString }.getOrDefault("")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = USER_AGENT
        }
    }

    private fun injectCookies() {
        val header = Store.getCookie()
        if (header.isBlank()) return
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        header.split("; ").forEach { entry ->
            if (entry.contains("=")) {
                manager.setCookie("https://.chaoxing.com", entry)
            }
        }
        manager.flush()
    }

    private fun pageOf(url: String): String = runCatching {
        val parsed = Uri.parse(url)
        parsed.host + parsed.encodedPath
    }.getOrDefault(url.take(80))

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val LOAD_TIMEOUT_MS = 10_000L
    private const val POLL_TIMEOUT_MS = 8_000L
    private const val POLL_INTERVAL_MS = 250L

    private val EXTRACT_JS = """
        (function(){
          try {
            var nodes = document.querySelectorAll('.questionLi');
            if (!nodes.length) return '';
            var parts = [];
            for (var i = 0; i < nodes.length; i++) {
              var copy = nodes[i].cloneNode(true);
              var drop = copy.querySelectorAll(
                '.mark_answer,.aiArea,.knowledgePointBx,.stem_answer,.edui-editor'
              );
              for (var j = 0; j < drop.length; j++) { drop[j].remove(); }
              parts.push(copy.outerHTML);
            }
            return '<html><body>' + parts.join('\n') + '</body></html>';
          } catch (e) {
            return '';
          }
        })()
    """.trimIndent()
}
