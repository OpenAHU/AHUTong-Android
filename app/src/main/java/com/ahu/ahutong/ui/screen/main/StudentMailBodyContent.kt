package com.ahu.ahutong.ui.screen.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ahu.ahutong.data.mail.*
import com.ahu.ahutong.ui.components.*
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun StudentMailBodyContent(detail: MailDetail,
    images: (MailDetail, Boolean) -> StudentMailBodyImages?) {
    val context = LocalContext.current
    var format by rememberSaveable(detail.id) { mutableStateOf(MailBodyFormat.AUTO) }
    var autoExternal by remember { mutableStateOf(StudentMailDisplayPreferences.autoLoadExternalImages(context)) }
    var allowExternal by rememberSaveable(detail.id) { mutableStateOf(autoExternal) }
    var showFormats by remember { mutableStateOf(false) }
    var renderFailed by remember(detail.id, format) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val theme = MailBodyTheme(colors.onSurface.cssColor(), colors.surface.cssColor(),
        colors.primary.cssColor(), colors.outlineVariant.cssColor())
    val document by produceState<MailBodyDocument?>(null, detail, format, theme) {
        value = null
        val result = withContext(Dispatchers.Default) { runCatching {
            // Authored HTML often specifies dark text without a background; a light canvas preserves readability.
            val html = format == MailBodyFormat.HTML || format == MailBodyFormat.AUTO && StudentMailBody.detect(detail) == MailBodyFormat.HTML
            StudentMailBody.render(detail, format, if (html) MailBodyTheme() else theme)
        } }
        if (result.isFailure) renderFailed = true
        value = result.getOrNull()
    }
    val ready = document
    val loader = remember(detail, allowExternal, images) { images(detail, allowExternal) }
    DisposableEffect(loader) { onDispose { loader?.close() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("正文", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text((ready?.format ?: format).label(), style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant)
            AppHeaderIconButton(imageVector = Icons.Outlined.Tune, contentDescription = "切换正文显示格式",
                onClick = { showFormats = true })
        }
        if (renderFailed) {
            Text("正文排版暂时不可用，已显示文字内容。", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            SelectionContainer { Text(detail.text.ifBlank { "（无正文）" }) }
        } else if (ready == null) {
            AppStateCard.Loading("正在排版正文…")
        } else {
            key(detail.id, ready.html, allowExternal) {
                MailBodyWebView(ready, loader) { renderFailed = true }
            }
        }
        if (ready?.hasRemoteImages == true && !allowExternal) {
            Text("已关闭自动加载外部图片。", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            AppButton({ allowExternal = true }, variant = AppButtonVariant.Secondary) { Text("显示外部图片") }
        }
    }
    if (showFormats) {
        AppModalBottomSheet("正文显示", { showFormats = false }) {
            listOf(MailBodyFormat.AUTO, MailBodyFormat.HTML, MailBodyFormat.MARKDOWN, MailBodyFormat.TEXT).forEach { choice ->
                AppButton({ format = choice; showFormats = false }, Modifier.fillMaxWidth(),
                    variant = if (format == choice) AppButtonVariant.Primary else AppButtonVariant.Secondary) {
                    Text(choice.label())
                    if (format == choice) {
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("自动显示外部图片", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                AppToggle(autoExternal, { enabled ->
                    autoExternal = enabled
                    allowExternal = enabled
                    StudentMailDisplayPreferences.setAutoLoadExternalImages(context, enabled)
                }, contentDescription = "自动显示外部图片")
            }
            Text("设置会记住，图片请求不携带邮箱登录态。", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun MailBodyFormat.label() = when (this) {
    MailBodyFormat.AUTO -> "自动识别"
    MailBodyFormat.HTML -> "HTML 富文本"
    MailBodyFormat.MARKDOWN -> "Markdown"
    MailBodyFormat.TEXT -> "纯文本"
}

private fun Color.cssColor() = String.format(Locale.ROOT, "#%06x", toArgb() and 0xffffff)

/** No scripts, JS bridge, filesystem or normal network access: only intercepted image bytes. */
@Composable
private fun MailBodyWebView(document: MailBodyDocument, images: StudentMailBodyImages?, failed: () -> Unit) {
    val context = LocalContext.current
    var height by remember { mutableStateOf(220.dp) }
    var view by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            view?.apply { stopLoading(); webViewClient = WebViewClient(); removeAllViews(); destroy() }
            view = null
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(height),
        factory = { ctx ->
            object : WebView(ctx) {
                private var reportedHeight = 0
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    @Suppress("DEPRECATION")
                    val pixels = (contentHeight * scale).toInt()
                    if (pixels > 0 && pixels != reportedHeight) {
                        reportedHeight = pixels
                        post { height = (pixels / resources.displayMetrics.density).coerceIn(80f, 16_000f).dp }
                    }
                }
            }.apply {
                view = this
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    blockNetworkLoads = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    textZoom = (resources.configuration.fontScale * 100).toInt()
                }
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse {
                        val source = document.images[request.url.toString()]
                        val image = if (request.method == "GET" && source != null) images?.load(source) else null
                        return if (image != null) WebResourceResponse(image.mimeType, null, ByteArrayInputStream(image.bytes))
                        else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame || !request.hasGesture()) return true
                        val target = request.url
                        if (target.host == "ahu-mail-body.invalid") return target.fragment == null
                        if (target.scheme?.lowercase(Locale.ROOT) in setOf("https", "http", "mailto", "tel")) {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, target).addCategory(Intent.CATEGORY_BROWSABLE)) }
                            catch (_: ActivityNotFoundException) { Toast.makeText(context, "没有可以打开此链接的应用", Toast.LENGTH_SHORT).show() }
                            catch (_: SecurityException) { Toast.makeText(context, "无法打开此链接", Toast.LENGTH_SHORT).show() }
                        }
                        return true
                    }
                    override fun onRenderProcessGone(web: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                        failed()
                        return true
                    }
                }
                loadDataWithBaseURL("https://ahu-mail-body.invalid/", document.html, "text/html", "UTF-8", null)
            }
        }
    )
}
