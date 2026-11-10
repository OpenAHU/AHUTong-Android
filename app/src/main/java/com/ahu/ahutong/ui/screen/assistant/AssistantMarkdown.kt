package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

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
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 19.sp,
                fontWeight = FontWeight.Light,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    if (text.containsMath()) {
        NativeMathMarkdown(text = text, modifier = modifier)
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
            text = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 19.sp,
                fontWeight = FontWeight.Light,
            ),
            code = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            inlineCode = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            quote = MaterialTheme.typography.bodySmall.copy(
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
            ),
            paragraph = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 19.sp,
                fontWeight = FontWeight.Light,
            ),
            ordered = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 19.sp,
                fontWeight = FontWeight.Light,
            ),
            bullet = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 19.sp,
                fontWeight = FontWeight.Light,
            ),
            list = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 19.sp,
                fontWeight = FontWeight.Light,
            ),
            table = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 17.sp,
                fontWeight = FontWeight.Light,
            ),
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
