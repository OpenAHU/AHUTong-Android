package com.ahu.ahutong.data.mail

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.Locale

enum class MailBodyFormat { AUTO, HTML, MARKDOWN, TEXT }

data class MailBodyTheme(
    val text: String = "#202124",
    val background: String = "#ffffff",
    val link: String = "#3868d9",
    val outline: String = "#d9dce3",
    val textSize: Int = 16
)

data class MailBodyDocument(
    val format: MailBodyFormat,
    val html: String,
    /** Original image sources stay outside the document and are loaded by the native client. */
    val images: Map<String, String>,
    val hasRemoteImages: Boolean
)

/** Formats untrusted mail content without granting it network access or executable capabilities. */
object StudentMailBody {
    const val ORIGIN = "https://ahu-mail-body.invalid"
    private const val MAX_DATA_IMAGE_BYTES = 2 * 1024 * 1024
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val markdownParser = Parser.builder().extensions(extensions).build()
    private val markdownRenderer = HtmlRenderer.builder().extensions(extensions).build()
    private val htmlTag = Regex(
        "</?(?:html|body|head|p|div|span|br|h[1-6]|table|tr|td|th|ul|ol|li|a|img|b|strong|i|em|pre|code|blockquote|style|hr|font|u|s|del|ins|section|article|header|footer|main|address|dl|dt|dd|figure|figcaption)(?:\\s[^<>]*|\\s*)/?>",
        RegexOption.IGNORE_CASE
    )
    private val allowedTags = setOf(
        "a", "abbr", "b", "bdi", "bdo", "blockquote", "br", "caption", "center", "code", "col", "colgroup",
        "dd", "del", "details", "div", "dl", "dt", "em", "figcaption", "figure", "font", "h1", "h2", "h3",
        "h4", "h5", "h6", "hr", "i", "img", "ins", "kbd", "li", "mark", "ol", "p", "pre", "q", "s",
        "samp", "small", "span", "strong", "style", "sub", "summary", "sup", "table", "tbody", "td", "tfoot",
        "th", "thead", "tr", "tt", "u", "ul", "var", "wbr", "section", "article", "header", "footer", "main", "address"
    )
    private val droppedTags = setOf(
        "script", "iframe", "frame", "frameset", "object", "embed", "applet", "svg", "math", "template",
        "link", "meta", "base", "input", "button", "textarea", "select", "option", "audio", "video", "source",
        "track", "canvas", "noscript"
    )
    private val cssProperties = setOf(
        "color", "background", "background-color", "background-image", "background-size", "background-repeat",
        "background-position", "background-position-x", "background-position-y", "font", "font-family", "font-size", "font-style", "font-weight",
        "font-variant", "line-height", "letter-spacing", "word-spacing", "text-align", "text-decoration",
        "text-decoration-color", "text-decoration-line", "text-decoration-style", "text-indent", "text-transform",
        "text-overflow", "white-space", "word-break", "word-wrap", "overflow-wrap", "vertical-align",
        "width", "min-width", "max-width", "height", "min-height", "max-height", "margin", "margin-top",
        "margin-right", "margin-bottom", "margin-left", "padding", "padding-top", "padding-right", "padding-bottom",
        "padding-left", "border", "border-top", "border-right", "border-bottom", "border-left", "border-width",
        "border-style", "border-color", "border-radius", "border-collapse", "border-spacing", "table-layout",
        "display", "box-sizing", "float", "clear", "overflow", "overflow-x", "overflow-y", "list-style",
        "list-style-type", "list-style-position", "opacity", "position", "flex", "flex-direction", "flex-wrap",
        "flex-basis", "flex-grow", "flex-shrink", "align-items", "align-content", "align-self", "justify-content",
        "gap", "row-gap", "column-gap", "box-shadow"
    )

    fun detect(detail: MailDetail): MailBodyFormat {
        if (htmlTag.containsMatchIn(detail.html)) {
            val html = Jsoup.parse(detail.html)
            val genericWrapper = html.select("style").isEmpty() && html.body().getAllElements().all {
                it.normalName() in setOf("body", "div", "p", "br")
            }
            fun normalized(text: String) = text.replace("\r\n", "\n").replace('\r', '\n').trim()
            // Mail editors wrap plain Markdown in div/br tags. Only unwrap when the MIME text
            // alternative agrees, so an independently formatted HTML alternative keeps priority.
            if (genericWrapper && detail.text.isNotBlank() && !htmlTag.containsMatchIn(detail.text) &&
                normalized(detail.text) == normalized(html.body().wholeText()) && isMarkdown(detail.text)
            ) return MailBodyFormat.MARKDOWN
            return MailBodyFormat.HTML
        }
        if (htmlTag.containsMatchIn(detail.text)) return MailBodyFormat.HTML
        return if (isMarkdown(plainSource(detail))) MailBodyFormat.MARKDOWN else MailBodyFormat.TEXT
    }

    private fun isMarkdown(source: String): Boolean {
        val lines = source.lines()
        return lines.any { Regex("^ {0,3}#{1,6}\\s+\\S").containsMatchIn(it) } ||
            lines.any { Regex("^ {0,3}(?:`{3,}|~{3,})").containsMatchIn(it) } ||
            lines.zipWithNext().any { (heading, underline) -> heading.isNotBlank() &&
                Regex("^ {0,3}(?:={3,}|-{3,})\\s*$").matches(underline) } ||
            lines.count { Regex("^ {0,3}(?:[-+*]|\\d+[.)])\\s+\\S").containsMatchIn(it) } >= 2 ||
            lines.count { Regex("^ {0,3}>\\s?\\S").containsMatchIn(it) } >= 2 ||
            lines.any { Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*\\|.*-.*$").containsMatchIn(it) } ||
            Regex("(?:\\*\\*[^\\n*]+\\*\\*|__[^\\n_]+__|\\[[^\\n]+]\\([^)]+\\))").findAll(source).count() >= 2
    }

    fun render(
        detail: MailDetail,
        format: MailBodyFormat = MailBodyFormat.AUTO,
        theme: MailBodyTheme = MailBodyTheme()
    ): MailBodyDocument {
        val chosen = if (format == MailBodyFormat.AUTO) detect(detail) else format
        val source = when (chosen) {
            MailBodyFormat.HTML -> detail.html.takeIf { htmlTag.containsMatchIn(it) }
                ?: detail.text.ifBlank { detail.html }
            MailBodyFormat.MARKDOWN -> markdownRenderer.render(markdownParser.parse(plainSource(detail)))
            else -> "<div class=\"mail-plain\">${linkify(plainSource(detail))}</div>"
        }
        val parsed = Jsoup.parse(source)
        val images = linkedMapOf<String, String>()
        sanitize(parsed.head(), images, detail)
        sanitize(parsed.body(), images, detail)

        val document = Jsoup.parse("<!doctype html><html><head></head><body></body></html>")
        document.outputSettings().prettyPrint(false)
        document.head().appendElement("meta").attr("charset", "utf-8")
        document.head().appendElement("meta").attr("name", "viewport")
            .attr("content", "width=device-width, initial-scale=1.0")
        document.head().appendElement("meta").attr("http-equiv", "Content-Security-Policy").attr(
            "content", "default-src 'none'; script-src 'none'; style-src 'unsafe-inline'; " +
                "img-src $ORIGIN data:; base-uri 'none'; form-action 'none'; frame-src 'none'; connect-src 'none'"
        )
        document.head().appendElement("style").appendChild(DataNode(baseStyle(theme, chosen)))
        parsed.head().select("style").forEach { document.head().appendChild(it.clone()) }
        val wrapper = document.body().appendElement("div").attr("class", "mail-body")
        parsed.body().attributes().forEach { wrapper.attr(it.key, it.value) }
        wrapper.addClass("mail-body")
        parsed.body().childNodes().toList().forEach { wrapper.appendChild(it.clone()) }
        return MailBodyDocument(chosen, document.outerHtml(), images,
            images.values.any { isRemoteImage(it) && !isInlineImage(it, detail) })
    }

    fun replyText(detail: MailDetail): String {
        if (detect(detail) != MailBodyFormat.HTML) return plainSource(detail)
        val source = detail.html.ifBlank { detail.text }
        val document = Jsoup.parse(source)
        document.select("head,script,style,noscript,template,iframe,object,embed").remove()
        document.body().select("p,div,section,article,tr,blockquote,pre,h1,h2,h3,h4,h5,h6,li").forEach {
            it.before(TextNode("\n"))
            it.after(TextNode("\n"))
        }
        return document.body().wholeText().replace(Regex("\n[\\t ]*(?:\n[\\t ]*){2,}"), "\n\n").trim()
    }

    private fun plainSource(detail: MailDetail): String = detail.text.ifBlank {
        if (htmlTag.containsMatchIn(detail.html)) Jsoup.parse(detail.html).wholeText() else detail.html
    }

    private fun sanitize(root: Element, images: MutableMap<String, String>, detail: MailDetail) {
        root.getAllElements().toList().asReversed().forEach { element ->
            val tag = element.normalName()
            if (element !== root && tag in droppedTags) {
                element.remove()
                return@forEach
            }
            if (element !== root && tag !in allowedTags) {
                // Form controls are removed, while readable form labels and unknown layout containers survive.
                element.unwrap()
                return@forEach
            }
            if (tag == "style") {
                val safeCss = sanitizeStylesheet(element.data(), images, detail)
                element.clearAttributes()
                element.empty()
                if (safeCss.isBlank()) element.remove() else element.appendChild(DataNode(safeCss))
                return@forEach
            }
            val original = element.attributes().associate { it.key.lowercase(Locale.ROOT) to it.value }
            element.clearAttributes()
            original.forEach { (name, value) ->
                when {
                    name == "style" -> sanitizeDeclarations(value, images, detail).takeIf(String::isNotBlank)?.let { element.attr(name, it) }
                    name in setOf("class", "id", "title", "lang") && value.length <= 2048 -> element.attr(name, value)
                    name == "dir" && value.lowercase(Locale.ROOT) in setOf("ltr", "rtl", "auto") -> element.attr(name, value)
                    name == "href" && tag == "a" && safeLink(value) -> {
                        element.attr(name, value.trim()).attr("rel", "noopener noreferrer")
                    }
                    name == "src" && tag == "img" -> safeImage(value, detail)?.let { image ->
                        element.attr(name, imageProxy(image, images))
                    }
                    name == "alt" && tag == "img" -> element.attr(name, value.take(4096))
                    name in setOf("width", "height", "cellpadding", "cellspacing", "border", "colspan", "rowspan", "span", "start", "size") &&
                        value.matches(Regex("\\d{1,4}%?")) -> element.attr(name, value)
                    name in setOf("align", "valign") && value.lowercase(Locale.ROOT) in
                        setOf("left", "right", "center", "justify", "top", "middle", "bottom", "baseline") -> element.attr(name, value)
                    name in setOf("bgcolor", "color") && value.matches(Regex("(?:#[0-9a-fA-F]{3,8}|[a-zA-Z]{1,20})")) -> element.attr(name, value)
                    name == "face" && tag == "font" && value.matches(Regex("[\\p{L}\\p{N} ,'-]{1,160}")) -> element.attr(name, value)
                    name == "type" && tag in setOf("ol", "ul", "li") && value in setOf("1", "a", "A", "i", "I", "disc", "circle", "square") -> element.attr(name, value)
                }
            }
            if (tag in setOf("body", "table", "td", "th") &&
                !Regex("(?:^|;)background(?:-image)?:").containsMatchIn(element.attr("style"))) {
                original["background"]?.let { safeImage(it, detail) }?.let { image ->
                    val prefix = element.attr("style").takeIf(String::isNotBlank)?.plus(";").orEmpty()
                    element.attr("style", prefix + "background-image:url(\"${imageProxy(image, images)}\")")
                }
            }
            if (tag == "img") element.attr("loading", "lazy").attr("referrerpolicy", "no-referrer")
        }
    }

    private fun safeLink(value: String): Boolean {
        if (value.length > 8192 || value.any { it.isISOControl() }) return false
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "http", "https" -> !uri.host.isNullOrBlank() && uri.rawUserInfo == null
            "mailto", "tel" -> !uri.schemeSpecificPart.isNullOrBlank()
            else -> false
        }
    }

    private fun safeImage(value: String, detail: MailDetail): String? {
        val trimmed = value.trim()
        val source = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        if (source.any { it.isISOControl() }) return null
        if (source.startsWith("cid:", ignoreCase = true)) {
            return source.takeIf { it.length in 5..1024 && !it.drop(4).isBlank() }
        }
        if (source.startsWith("data:", ignoreCase = true)) {
            if (source.length > MAX_DATA_IMAGE_BYTES * 4 / 3 + 128) return null
            val match = Regex("^data:image/(png|jpeg|jpg|gif|webp);base64,([A-Za-z0-9+/=]+)$", RegexOption.IGNORE_CASE)
                .matchEntire(source) ?: return null
            val bytes = runCatching { Base64.getDecoder().decode(match.groupValues[2]) }.getOrNull() ?: return null
            if (bytes.isEmpty() || bytes.size > MAX_DATA_IMAGE_BYTES) return null
            val type = match.groupValues[1].lowercase(Locale.ROOT)
            val valid = when (type) {
                "png" -> bytes.size >= 8 && bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)
                "jpeg", "jpg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
                "gif" -> bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
                "webp" -> bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
                else -> false
            }
            return source.takeIf { valid }
        }
        if (source.length > 8192) return null
        if (isRemoteImage(source) && safeLink(source)) return source
        val uri = runCatching { URI(source) }.getOrNull() ?: return null
        // Relative content locations are meaningful only when readMessage binds them to a MIME part.
        return source.takeIf { uri.scheme == null && uri.rawAuthority == null && isInlineImage(it, detail) }
    }

    private fun isRemoteImage(source: String) = source.startsWith("https://", true) || source.startsWith("http://", true)

    private fun imageProxy(source: String, images: MutableMap<String, String>): String =
        images.entries.firstOrNull { it.value == source }?.key
            ?: "$ORIGIN/image/${images.size}".also { images[it] = source }

    private fun isInlineImage(source: String, detail: MailDetail): Boolean {
        val candidates = detail.attachments.filter {
            it.messageId == detail.id && it.partId?.matches(Regex("[0-9]+(?:\\.[0-9]+)*")) == true &&
                it.contentType.startsWith("image/", true)
        }
        if (candidates.any { !it.contentLocation.isNullOrBlank() && source == it.contentLocation }) return true
        val uri = runCatching { URI(source) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") ||
            uri.host != StudentMailSession.MAIL_HOST || uri.port !in setOf(-1, 80, 443) ||
            uri.rawUserInfo != null || uri.path != "/js6/s") return false
        val parameters = runCatching {
            uri.rawQuery.orEmpty().split('&').associate { item ->
                val pair = item.split('=', limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }
        }.getOrNull() ?: return false
        return parameters["func"] == "mbox:getMessageData" && parameters["mid"] == detail.id &&
            candidates.any { it.partId == parameters["part"] }
    }

    private val cssImageUrl = Regex("url\\(\\s*(?:\"([^\"\\\\]*)\"|'([^'\\\\]*)'|([^\\s()\"'\\\\]+))\\s*\\)", RegexOption.IGNORE_CASE)

    private fun sanitizeDeclarations(source: String, images: MutableMap<String, String>, detail: MailDetail): String {
        val css = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return splitDeclarations(css).mapNotNull { declaration ->
            val parts = declaration.split(':', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val property = parts[0].trim().lowercase(Locale.ROOT)
            val value = parts[1].trim()
            val imageProperty = property in setOf("background", "background-image")
            val maxLength = if (imageProperty) MAX_DATA_IMAGE_BYTES * 4 / 3 + 1024 else 512
            if (property !in cssProperties || value.length > maxLength ||
                value.any { it in "\\{}<>" || (it.isISOControl() && !it.isWhitespace()) }
            ) return@mapNotNull null
            val matches = if (imageProperty) cssImageUrl.findAll(value).toList() else emptyList()
            val masked = if (imageProperty) cssImageUrl.replace(value, "safe-image") else value
            val compact = masked.lowercase(Locale.ROOT).filterNot(Char::isWhitespace)
            if (masked.contains('@') ||
                listOf("url(", "expression(", "behavior", "-moz-binding", "javascript:", "data:", "http:", "https:",
                    "image-set(", "image(", "element(", "paint(", "cross-fade(").any(compact::contains) ||
                (property == "position" && compact !in setOf("static", "relative"))
            ) return@mapNotNull null
            val sources = matches.map { match ->
                val url = match.groupValues.drop(1).firstOrNull(String::isNotEmpty) ?: return@mapNotNull null
                safeImage(url, detail) ?: return@mapNotNull null
            }
            var index = 0
            val rewritten = if (matches.isEmpty()) value else cssImageUrl.replace(value) {
                "url(\"${imageProxy(sources[index++], images)}\")"
            }
            "$property:$rewritten"
        }.joinToString(";")
    }

    private fun splitDeclarations(css: String): List<String> {
        val declarations = mutableListOf<String>()
        var quote: Char? = null
        var parentheses = 0
        var start = 0
        css.forEachIndexed { index, character ->
            if (quote != null) {
                if (character == quote) quote = null
            } else when (character) {
                '\'', '"' -> quote = character
                '(' -> parentheses++
                ')' -> if (--parentheses < 0) return emptyList()
                ';' -> if (parentheses == 0) {
                    declarations.add(css.substring(start, index))
                    start = index + 1
                }
            }
        }
        if (quote != null || parentheses != 0) return emptyList()
        declarations.add(css.substring(start))
        return declarations
    }

    private fun sanitizeStylesheet(source: String, images: MutableMap<String, String>, detail: MailDetail, depth: Int = 0): String {
        if (depth > 3 || source.length > 128 * 1024) return ""
        val css = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        val result = StringBuilder()
        var offset = 0
        while (offset < css.length) {
            val opening = css.indexOf('{', offset)
            if (opening < 0) break
            val selector = css.substring(offset, opening).trim()
            var nesting = 1
            var closing = opening + 1
            while (closing < css.length && nesting > 0) {
                when (css[closing]) { '{' -> nesting++; '}' -> nesting-- }
                closing++
            }
            if (nesting != 0) break
            val body = css.substring(opening + 1, closing - 1)
            if (selector.startsWith("@media", ignoreCase = true)) {
                val condition = selector.drop(6).trim()
                if (condition.matches(Regex("[a-zA-Z0-9\\s():.,%-]{1,200}"))) {
                    val nested = sanitizeStylesheet(body, images, detail, depth + 1)
                    if (nested.isNotBlank()) result.append("@media ").append(condition).append('{').append(nested).append('}')
                }
            } else if (selector.matches(Regex("[\\p{L}\\p{N}\\s.#,:>+~*\\[\\]\\\"'=()|_-]{1,1024}"))) {
                val declarations = sanitizeDeclarations(body, images, detail)
                if (declarations.isNotBlank()) result.append(selector).append('{').append(declarations).append('}')
            }
            offset = closing
        }
        return result.toString()
    }

    private fun linkify(source: String): String {
        val links = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)
        val output = StringBuilder()
        var previous = 0
        links.findAll(source).forEach { match ->
            val target = match.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '}')
            output.append(escape(source.substring(previous, match.range.first)))
            if (safeLink(target)) output.append("<a href=\"").append(escape(target)).append("\">").append(escape(target)).append("</a>")
            else output.append(escape(target))
            previous = match.range.first + target.length
        }
        return output.append(escape(source.substring(previous))).toString()
    }

    private fun escape(source: String) = source.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun baseStyle(theme: MailBodyTheme, format: MailBodyFormat): String {
        fun color(value: String, fallback: String) = value.takeIf {
            it.matches(Regex("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})"))
        } ?: fallback
        val text = color(theme.text, "#202124")
        val background = color(theme.background, "#ffffff")
        val link = color(theme.link, "#3868d9")
        val outline = color(theme.outline, "#d9dce3")
        val tableStyle = if (format == MailBodyFormat.MARKDOWN) {
            "table{border-collapse:collapse}th,td{padding:6px 10px;border:1px solid $outline}"
        } else ""
        return """
            html,body{margin:0;padding:0;background:$background;color:$text;font-family:system-ui,sans-serif;font-size:${theme.textSize.coerceIn(12, 24)}px;line-height:1.65;overflow-wrap:anywhere}
            .mail-body{padding:4px 0;min-width:0;max-width:100%;overflow-x:auto}
            .mail-plain{white-space:pre-wrap;overflow-wrap:anywhere}
            a{color:$link;text-decoration:underline}
            img{max-width:100%;height:auto;vertical-align:middle}
            table{max-width:100%}
            $tableStyle
            pre{white-space:pre;overflow-x:auto;padding:12px;border:1px solid $outline;border-radius:8px}
            code,kbd,samp{font-family:monospace}code{white-space:pre-wrap}pre code{white-space:pre}
            blockquote{margin:12px 0;padding:0 12px;border-left:3px solid $outline}
            hr{border:0;border-top:1px solid $outline}h1,h2,h3,h4,h5,h6{line-height:1.35}
        """.trimIndent()
    }
}
