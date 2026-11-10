package com.ahu.ahutong.ui.screen.assistant

import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code as MdCode
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image as MdImage
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link as MdLink
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MdText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.roundToInt

private val BODY_SIZE = 14.sp
private val BODY_LINE = 19.sp
private val TABLE_SIZE = 12.sp
private val TABLE_LINE = 17.sp
private val CODE_SIZE = 12.sp
private val CODE_LINE = 16.sp
private val MATH_BLOCK_SIZE = 16.sp

private val markdownParser: Parser = Parser.builder()
    .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
    .build()

private val inlineMathPattern = Regex(
    """(?<!\\)\$(?!\$)([^$\n]+?)(?<!\\)\$|\\\((.+?)\\\)"""
)

/**
 * 把 $$...$$ / \[...\] 统一成带 info 的 fenced code block，交给 Compose 块级公式渲染。
 * 代码围栏内的内容不处理。
 */
internal fun normalizeBlockMath(markdown: String): String {
    val lines = markdown.lines()
    val out = mutableListOf<String>()
    var inFence = false
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            out += line
            index++
            continue
        }
        if (inFence) {
            out += line
            index++
            continue
        }
        val open = when {
            trimmed.startsWith("$$") -> "$$"
            trimmed.startsWith("\\[") -> "\\["
            else -> null
        }
        val close = when (open) {
            "$$" -> "$$"
            "\\[" -> "\\]"
            else -> null
        }
        if (open == null || close == null) {
            out += line
            index++
            continue
        }
        val body = StringBuilder()
        val afterOpen = trimmed.substring(open.length)
        var closed = false
        if (afterOpen.contains(close)) {
            body.append(afterOpen.substringBefore(close))
            closed = true
        } else {
            if (afterOpen.isNotBlank()) body.appendLine(afterOpen)
            var probe = index + 1
            while (probe < lines.size) {
                val candidate = lines[probe]
                if (candidate.contains(close)) {
                    body.append(candidate.substringBefore(close))
                    closed = true
                    break
                }
                body.appendLine(candidate)
                probe++
            }
            if (closed) index = probe
        }
        if (closed) {
            out += "```math"
            val tex = body.toString().trim('\n', '\r')
            if (tex.isNotEmpty()) out += tex
            out += "```"
            index++
        } else {
            out += line
            index++
        }
    }
    return out.joinToString("\n")
}

internal object LatexDrawables {
    private val cache = object : LruCache<String, JLatexMathDrawable>(48) {}

    fun get(tex: String, sizePx: Float, color: Int, display: Boolean): JLatexMathDrawable? {
        val key = "$display|$sizePx|$color|$tex"
        cache.get(key)?.let { return it }
        val drawable = runCatching {
            JLatexMathDrawable.builder(tex)
                .textSize(sizePx)
                .color(color)
                .align(JLatexMathDrawable.ALIGN_LEFT)
                .build()
        }.getOrNull() ?: return null
        cache.put(key, drawable)
        return drawable
    }
}

private data class InlineResult(
    val text: AnnotatedString,
    val placeholders: Map<String, InlineTextContent>,
)

@Composable
internal fun NativeMathMarkdown(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val document = remember(text) {
        val safe = AssistantMarkdownSafety.sanitize(text)
        markdownParser.parse(normalizeBlockMath(safe))
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var child = document.firstChild
        while (child != null) {
            RenderBlock(child, colors, density)
            child = child.next
        }
    }
}

@Composable
private fun RenderBlock(
    node: Node,
    colors: androidx.compose.material3.ColorScheme,
    density: Density,
) {
    when (node) {
        is Paragraph -> InlineText(
            nodes = collectChildren(node),
            style = bodyStyle(),
            color = colors.onSurface,
            colors = colors,
            density = density,
            modifier = Modifier.fillMaxWidth(),
        )
        is Heading -> {
            val isH1 = node.level == 1
            InlineText(
                nodes = collectChildren(node),
                style = if (isH1) {
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = BODY_SIZE,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                color = colors.onSurface,
                colors = colors,
                density = density,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isH1) 6.dp else 4.dp),
            )
        }
        is FencedCodeBlock -> {
            if (node.info.orEmpty().trim().lowercase() == "math") {
                BlockMath(node.literal)
            } else {
                CodeBlock(node.literal)
            }
        }
        is IndentedCodeBlock -> CodeBlock(node.literal)
        is BulletList -> MarkdownList(node, ordered = false, colors = colors, density = density)
        is OrderedList -> MarkdownList(node, ordered = true, colors = colors, density = density)
        is BlockQuote -> BlockQuoteBlock(node, colors, density)
        is ThematicBreak -> HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
        is TableBlock -> MarkdownTable(node, colors, density)
        is HtmlBlock -> Text(
            text = node.literal.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
            style = bodyStyle(),
            color = colors.onSurface,
        )
        else -> {
            var child = node.firstChild
            while (child != null) {
                RenderBlock(child, colors, density)
                child = child.next
            }
        }
    }
}

@Composable
private fun bodyStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontSize = BODY_SIZE,
    lineHeight = BODY_LINE,
    fontWeight = FontWeight.Light,
)

@Composable
private fun InlineText(
    nodes: List<Node>,
    style: TextStyle,
    color: Color,
    colors: androidx.compose.material3.ColorScheme,
    density: Density,
    modifier: Modifier = Modifier,
) {
    val inline = remember(nodes, style, color, colors, density) {
        buildInline(nodes, style.fontSize, color, colors, density)
    }
    Text(
        text = inline.text,
        inlineContent = inline.placeholders,
        style = style,
        color = color,
        modifier = modifier,
    )
}

private fun collectChildren(node: Node): List<Node> {
    val result = mutableListOf<Node>()
    var child = node.firstChild
    while (child != null) {
        result += child
        child = child.next
    }
    return result
}

private fun buildInline(
    nodes: List<Node>,
    fontSize: TextUnit,
    color: Color,
    colors: androidx.compose.material3.ColorScheme,
    density: Density,
): InlineResult {
    val builder = AnnotatedString.Builder()
    val placeholders = mutableMapOf<String, InlineTextContent>()
    var mathIndex = 0
    val textSizePx = with(density) {
        if (fontSize.isSp) fontSize.toPx() else BODY_SIZE.toPx()
    }
    val formulaColor = color.toArgb()

    fun appendFormula(tex: String) {
        val drawable = LatexDrawables.get(tex, textSizePx, formulaColor, display = false)
        if (drawable == null || drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            builder.append(tex)
            return
        }
        val id = "math-${mathIndex++}"
        val widthSp = (drawable.intrinsicWidth / (density.density * density.fontScale)).sp
        val heightSp = (drawable.intrinsicHeight / (density.density * density.fontScale)).sp
        builder.appendInlineContent(id, tex)
        placeholders[id] = InlineTextContent(
            Placeholder(
                width = widthSp,
                height = heightSp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
            )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                    drawable.draw(canvas.nativeCanvas)
                }
            }
        }
    }

    fun appendTextWithMath(raw: String) {
        var cursor = 0
        for (match in inlineMathPattern.findAll(raw)) {
            if (match.range.first > cursor) {
                builder.append(raw.substring(cursor, match.range.first).replace("\\$", "$"))
            }
            val tex = (match.groupValues[1].takeIf { it.isNotEmpty() }
                ?: match.groupValues[2]).trim()
            if (tex.isNotEmpty()) {
                appendFormula(tex)
            }
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) {
            builder.append(raw.substring(cursor).replace("\\$", "$"))
        }
    }

    fun walk(node: Node) {
        when (node) {
            is MdText -> appendTextWithMath(node.literal)
            is MdCode -> {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurface,
                        background = colors.primaryContainer,
                    )
                )
                builder.append(node.literal)
                builder.pop()
            }
            is Emphasis -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                walkChildren(node, ::walk)
                builder.pop()
            }
            is StrongEmphasis -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                walkChildren(node, ::walk)
                builder.pop()
            }
            is Strikethrough -> {
                builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                walkChildren(node, ::walk)
                builder.pop()
            }
            is MdLink -> {
                val url = node.destination
                if (AssistantMarkdownSafety.isSafeExternalUri(url)) {
                    builder.withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = colors.primary,
                                    textDecoration = TextDecoration.Underline,
                                )
                            ),
                        )
                    ) {
                        walkChildren(node, ::walk)
                    }
                } else {
                    walkChildren(node, ::walk)
                }
            }
            is MdImage -> builder.append("[图片：${node.title.orEmpty().ifBlank { "已省略" }}]")
            is SoftLineBreak -> builder.append('\n')
            is HardLineBreak -> builder.append('\n')
            else -> walkChildren(node, ::walk)
        }
    }

    nodes.forEach(::walk)
    return InlineResult(builder.toAnnotatedString(), placeholders)
}

private inline fun walkChildren(node: Node, walk: (Node) -> Unit) {
    var child = node.firstChild
    while (child != null) {
        walk(child)
        child = child.next
    }
}

@Composable
private fun MarkdownList(
    node: Node,
    ordered: Boolean,
    colors: androidx.compose.material3.ColorScheme,
    density: Density,
) {
    val start = (node as? OrderedList)?.startNumber ?: 1
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var child = node.firstChild
        var index = start
        while (child != null) {
            if (child is ListItem) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (ordered) "$index." else "•",
                        style = bodyStyle(),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.width(22.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        var item = child.firstChild
                        while (item != null) {
                            RenderBlock(item, colors, density)
                            item = item.next
                        }
                    }
                }
            }
            child = child.next
            index++
        }
    }
}

@Composable
private fun BlockQuoteBlock(
    node: Node,
    colors: androidx.compose.material3.ColorScheme,
    density: Density,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = colors.primary.copy(alpha = 0.55f),
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        var child = node.firstChild
        while (child != null) {
            RenderBlock(child, colors, density)
            child = child.next
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = code.trimEnd('\n'),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = CODE_SIZE,
                lineHeight = CODE_LINE,
            ),
            color = colors.onSurface,
        )
    }
}

@Composable
private fun BlockMath(tex: String) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val drawable = remember(tex, colors.onSurface, density) {
        LatexDrawables.get(
            tex = tex,
            sizePx = with(density) { MATH_BLOCK_SIZE.toPx() },
            color = colors.onSurface.toArgb(),
            display = true,
        )
    }
    if (drawable == null) {
        Text(
            text = tex,
            modifier = Modifier.fillMaxWidth(),
            style = bodyStyle(),
            color = colors.onSurface,
        )
        return
    }
    val width = with(density) { drawable.intrinsicWidth.toDp() }
    val height = with(density) { drawable.intrinsicHeight.toDp() }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.size(width, height)) {
                drawIntoCanvas { canvas ->
                    drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                    drawable.draw(canvas.nativeCanvas)
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    node: Node,
    colors: androidx.compose.material3.ColorScheme,
    density: Density,
) {
    val rows = remember(node) { collectTableRows(node) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.outlineVariant),
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.cells.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (row.header) colors.surfaceContainer else Color.Transparent
                            )
                            .border(0.5.dp, colors.outlineVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        InlineText(
                            nodes = cell,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = TABLE_SIZE,
                                lineHeight = TABLE_LINE,
                                fontWeight = FontWeight.Light,
                            ),
                            color = colors.onSurface,
                            colors = colors,
                            density = density,
                        )
                    }
                }
            }
        }
    }
}

private data class TableRowData(val header: Boolean, val cells: List<List<Node>>)

private fun collectTableRows(table: Node): List<TableRowData> {
    val rows = mutableListOf<TableRowData>()
    var section = table.firstChild
    while (section != null) {
        val header = section is TableHead
        if (section is TableHead || section is TableBody) {
            var row = section.firstChild
            while (row != null) {
                if (row is TableRow) {
                    val cells = mutableListOf<List<Node>>()
                    var cell = row.firstChild
                    while (cell != null) {
                        if (cell is TableCell) cells += collectChildren(cell)
                        cell = cell.next
                    }
                    rows += TableRowData(header, cells)
                }
                row = row.next
            }
        }
        section = section.next
    }
    return rows
}
