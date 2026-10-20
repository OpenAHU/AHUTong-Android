package com.ahu.ahutong.data.notice

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONTokener

/**
 * 用隔离的 WebView 引擎渲染受 WAF 保护的公开公告列表。
 *
 * 教务处与研究生院都返回 412 + JS 挑战，裸 HTTP 拿不到内容；这里让页面脚本自己跑完挑战，
 * 再把列表 DOM 交给调用方解析。进程与登录 WebView 分离，不读取也不导出任何 Cookie。
 */
class NoticeWebProvider : ContentProvider() {
    private val fetchLock = Any()

    override fun onCreate(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("notice_web")
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = synchronized(fetchLock) {
        val target = when {
            method == METHOD_FETCH && PostgraduateNoticeParser.isTrustedListUrl(arg) ->
                WebTarget("研究生院", EXTRACT_POSTGRADUATE_JS)
            method == METHOD_FETCH && AcademicAffairsNoticeParser.isTrustedListUrl(arg) ->
                WebTarget("教务处", EXTRACT_ACADEMIC_AFFAIRS_JS)
            method == METHOD_FETCH_HTML && CollegeNoticeRegistry.configForListUrl(arg) != null ->
                WebTarget("学院", EXTRACT_COLLEGE_HTML_JS)
            else -> null
        }
        if (target == null) {
            return@synchronized result(error = "公告请求地址不在受信任栏目内")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return@synchronized result(error = "当前系统版本不支持隔离网页数据")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return@synchronized result(error = "网页请求不能在主线程执行")
        }

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val active = AtomicBoolean(true)
        val html = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        val mainFrameHttpCode = AtomicReference<Int?>(null)
        val webView = AtomicReference<WebView?>(null)

        fun finish(message: String? = null, page: String? = null) {
            if (!active.compareAndSet(true, false)) return
            error.set(message)
            html.set(page)
            latch.countDown()
        }

        handler.post {
            runCatching {
                val view = WebView(ContextThemeWrapper(context, android.R.style.Theme_Material_Light_NoActionBar))
                webView.set(view)
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        val address = Uri.parse(url)
                        val sameOfficialHost = address.scheme == "https" &&
                            address.host in TRUSTED_HOSTS && address.port == -1
                        if (!sameOfficialHost && url != "about:blank") {
                            view.stopLoading()
                            finish(message = "${target.label}公告页跳转到非受信任地址")
                        }
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame) mainFrameHttpCode.set(response.statusCode)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, received: WebResourceError) {
                        if (request.isForMainFrame) finish(message = "${target.label}公告网页加载失败")
                    }
                }
                val check = object : Runnable {
                    override fun run() {
                        if (!active.get()) return
                        view.evaluateJavascript(target.extractScript) { raw ->
                            val extracted = runCatching { JSONTokener(raw).nextValue() as String }.getOrNull()
                            if (!extracted.isNullOrBlank()) {
                                if (extracted.length > MAX_HTML_LENGTH) finish(message = "${target.label}公告页面过大")
                                else finish(page = extracted)
                            } else if (active.get()) {
                                handler.postDelayed(this, POLL_INTERVAL_MS)
                            }
                        }
                    }
                }
                view.loadUrl(arg!!)
                handler.postDelayed(check, POLL_INTERVAL_MS)
            }.onFailure { finish(message = "${target.label}公告网页初始化失败") }
        }

        val completed = latch.await(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            finish(message = if (mainFrameHttpCode.get() == 412) {
                "访问受限（HTTP 412）"
            } else {
                "${target.label}公告网页加载超时"
            })
        }
        handler.post { webView.getAndSet(null)?.destroy() }
        result(html = html.get(), error = error.get())
    }

    private fun result(html: String? = null, error: String? = null): Bundle = Bundle().apply {
        putString(KEY_HTML, html)
        putString(KEY_ERROR, error)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private data class WebTarget(val label: String, val extractScript: String)

    companion object {
        const val METHOD_FETCH = "fetch_list"
        const val METHOD_FETCH_HTML = "fetch_html"
        const val KEY_HTML = "html"
        const val KEY_ERROR = "error"
        private const val FETCH_TIMEOUT_SECONDS = 25L
        private const val POLL_INTERVAL_MS = 500L
        private const val MAX_HTML_LENGTH = 500_000
        private val TRUSTED_HOSTS = setOf("graschool.ahu.edu.cn", "jwc.ahu.edu.cn") + CollegeNoticeRegistry.hosts

        private val EXTRACT_POSTGRADUATE_JS = """
            (function() {
              var list = document.querySelector('ul.wp_article_list');
              if (!list || !list.querySelector('li.list_item')) return '';
              var next = document.querySelector('a.next');
              return '<html><body>' + list.outerHTML + (next ? next.outerHTML : '') + '</body></html>';
            })()
        """.trimIndent()

        private val EXTRACT_COLLEGE_HTML_JS = """
            (function() {
              var selectors = [
                'ul.news_list li.news',
                'ul.wp_article_list li.list_item',
                '.list_box li',
                'div.inew > div.inewi',
                '.science-r-m div[id^=wp_news_] ul.fixed > li',
                'div.newsList ul li',
                'div.col_news_list div.jzlb'
              ];
              for (var i = 0; i < selectors.length; i++) {
                if (document.querySelector(selectors[i])) {
                  return document.documentElement.outerHTML;
                }
              }
              return '';
            })()
        """.trimIndent()

        private val EXTRACT_ACADEMIC_AFFAIRS_JS = """
            (function() {
              var list = document.querySelector('ul.news_list.list2');
              if (!list || !list.querySelector('li.news')) return '';
              var next = document.querySelector('li.page_nav a.next');
              return '<html><body>' + list.outerHTML + (next && next.parentElement ? next.parentElement.outerHTML : '') + '</body></html>';
            })()
        """.trimIndent()
    }
}
