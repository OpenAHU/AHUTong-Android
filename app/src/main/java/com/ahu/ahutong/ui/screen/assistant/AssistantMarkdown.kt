package com.ahu.ahutong.ui.screen.assistant

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs

private val inlineMath = Regex("""(?<!\$)\$(?!\$)[^$\n]+\$(?!\$)""")

internal fun String.containsMath(): Boolean =
    contains("$$") ||
        contains("\\[") ||
        contains("\\(") ||
        contains("\\begin{") ||
        inlineMath.containsMatchIn(this)

/**
 * 助手回复是不可信内容：渲染前去掉图片、过滤非 http(s) 链接。
 * 围栏代码块原样保留。规则与 NEXT 一致。
 */
internal object AssistantMarkdownSafety {
    private val image = Regex("""!\[([^\]]*)]\(((?:[^()]|\([^()]*\))*)\)""")
    private val link = Regex("""\[([^\]]+)]\(((?:[^()]|\([^()]*\))*)\)""")
    private val rawImage = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val autoLink = Regex("""<([a-zA-Z][a-zA-Z0-9+.-]*:[^>\s]*)>""")

    fun sanitize(markdown: String): String {
        val out = StringBuilder(markdown.length)
        var inFence = false
        markdown.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                out.appendLine(line)
                return@forEach
            }
            if (inFence) {
                out.appendLine(line)
                return@forEach
            }
            var safe = image.replace(line) { match ->
                val alt = match.groupValues[1].ifBlank { "已省略" }
                "[图片：$alt]"
            }
            safe = rawImage.replace(safe, "[图片已省略]")
            safe = link.replace(safe) { match ->
                val text = match.groupValues[1]
                val url = match.groupValues[2].trim()
                if (isSafeExternalUri(url)) match.value else text
            }
            safe = autoLink.replace(safe) { match ->
                val url = match.groupValues[1]
                if (isSafeExternalUri(url)) match.value else url
            }
            out.appendLine(safe)
        }
        return out.toString().trimEnd()
    }

    fun isSafeExternalUri(uri: String): Boolean {
        val value = uri.trim().lowercase()
        return value.startsWith("https://") || value.startsWith("http://")
    }
}

@Composable
fun AssistantMarkdown(
    text: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    if (streaming) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    if (text.containsMath()) {
        KatexMarkdown(text = text, modifier = modifier)
    } else {
        MarkdownMarkdown(text = text, modifier = modifier)
    }
}

@Composable
private fun MarkdownMarkdown(text: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val safe = remember(text) { AssistantMarkdownSafety.sanitize(text) }
    Markdown(
        content = safe,
        colors = markdownColor(
            text = colors.onSurface,
            codeBackground = colors.surfaceContainerHigh,
            inlineCodeBackground = colors.primaryContainer,
            dividerColor = colors.outlineVariant,
            tableBackground = colors.surfaceContainerHigh.copy(alpha = 0.55f),
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
            h2 = MaterialTheme.typography.titleSmall.copy(lineHeight = 19.sp),
            h3 = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            ),
            h4 = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            ),
            h5 = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            ),
            h6 = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            ),
            text = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            code = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            inlineCode = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            quote = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            paragraph = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            ordered = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            bullet = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            list = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            table = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            textLink = TextLinkStyles(
                style = SpanStyle(
                    color = colors.primary,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
        ),
        modifier = modifier.fillMaxWidth(),
        loading = {},
        error = { errorModifier ->
            Text(
                text = text,
                modifier = errorModifier,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        },
    )
}

private const val MATH_HEIGHT_TIMEOUT_MS = 6_000L
private const val MAX_MATH_HEIGHT_DP = 20_000

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KatexMarkdown(text: String, modifier: Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val fontScale = LocalDensity.current.fontScale
    var height by remember(text) { mutableStateOf(0) }
    var failed by remember(text) { mutableStateOf(false) }
    // 只以实际参与 HTML 的字段为 key，避免主题对象实例变化带来无谓的 HTML 重建。
    val html = remember(
        text,
        dark,
        fontScale,
        colors.onSurface,
        colors.onSurfaceVariant,
        colors.surfaceContainerHigh,
        colors.surfaceContainerLow,
        colors.outlineVariant,
        colors.primary,
    ) {
        buildMathHtml(context, text, dark, colors, fontScale)
    }

    // 高度回写只接受显著变化，避免 1~2px 的布局抖动形成「改高度 → 重排 → 再上报」。
    fun applyHeight(measured: Int) {
        if (measured <= 0) return
        val next = measured.coerceIn(1, MAX_MATH_HEIGHT_DP)
        if (height == 0 || abs(next - height) >= 4) height = next
    }

    LaunchedEffect(text) {
        delay(MATH_HEIGHT_TIMEOUT_MS)
        if (height == 0) failed = true
    }

    if (failed) {
        MarkdownMarkdown(text = text, modifier = modifier)
        return
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.textZoom = 100 // 字号由 CSS 基准统一控制，避免系统缩放叠加两次
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = false
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun post(h: Int) {
                            post { applyHeight(h) }
                        }
                    },
                    "AndroidHeight",
                )
                fun measureHeight(view: WebView) {
                    view.evaluateJavascript(
                        "(function(){var d=document.documentElement;var b=document.body;" +
                            "return Math.max(d?d.scrollHeight:0,b?b.scrollHeight:0);})()"
                    ) { value ->
                        val measured = value?.trim('"')?.toFloatOrNull()?.toInt() ?: 0
                        if (measured > 0) applyHeight(measured)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.postDelayed({ measureHeight(view) }, 120)
                        view.postDelayed({ measureHeight(view) }, 800)
                        view.postDelayed({ measureHeight(view) }, 1_600)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            view.post { failed = true }
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url ?: return true
                        if (!AssistantMarkdownSafety.isSafeExternalUri(url.toString())) {
                            Toast.makeText(ctx, "已拦截不安全的链接", Toast.LENGTH_SHORT).show()
                            return true
                        }
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, url)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        return true
                    }
                }
                loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
                tag = html
            }
        },
        update = { web ->
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(if (height > 0) height.dp else 160.dp),
        onRelease = { it.destroy() },
    )
}

private fun buildMathHtml(
    context: android.content.Context,
    markdown: String,
    dark: Boolean,
    colors: androidx.compose.material3.ColorScheme,
    fontScale: Float,
): String {
    val safeMarkdown = AssistantMarkdownSafety.sanitize(markdown)
    val base64 = Base64.encodeToString(
        safeMarkdown.toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP,
    )
    val template = context.assets.open("katex/math.html")
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
    val text = if (dark) ComposeColor(0xFFF4F1EA) else colors.onSurface
    val muted = colors.onSurfaceVariant
    val codeBg = if (dark) ComposeColor(0xFF272520) else colors.surfaceContainerHigh
    val surface = if (dark) ComposeColor(0xFF1C1B18) else colors.surfaceContainerLow
    val border = colors.outlineVariant
    val accent = colors.primary
    // Compose 正文是 14sp；CSS 基准按系统字号缩放换算，两边字号/行高保持一致。
    val baseFont = String.format(Locale.US, "%.2f", 14f * fontScale)
    return template
        .replace("__SOURCE_B64__", base64)
        .replace("__BASE_FONT__", baseFont)
        .replace("__TEXT__", text.hex())
        .replace("__MUTED__", muted.hex())
        .replace("__CODE_BG__", codeBg.hex())
        .replace("__SURFACE__", surface.hex())
        .replace("__BORDER__", border.hex())
        .replace("__ACCENT__", accent.hex())
}

private fun ComposeColor.hex(): String = String.format("#%06X", 0xFFFFFF and toArgb())
