package com.ahu.ahutong.assistant.capabilities

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal data class WorkQuestion(
    val number: Int,
    val type: String,
    val score: String?,
    val stem: String,
    val options: List<String>,
    val hasImages: Boolean,
)

/**
 * 只解析题目区：题干取 h3.mark_name，选项取答案区之外的 li。
 * 已交作业的 .mark_answer / .aiArea / 知识点区一律跳过，避免把答案喂给模型。
 */
internal object WorkQuestionParser {

    private val LEADING_NUMBER = Regex("""^\s*(\d{1,3})\s*[.、．]\s*""")
    private val SCORE = Regex("""(\d+(?:\.\d+)?)\s*分""")
    private val META_SEPARATOR = Regex("""[,，]""")
    private val EXCLUDED_CLASSES = listOf(
        "mark_answer",
        "aiArea",
        "knowledgePointBx",
        "stem_answer",
        "edui-editor",
    )

    fun parse(html: String, maxQuestions: Int = 30): List<WorkQuestion> {
        val doc = Jsoup.parse(html)
        return doc.select(".questionLi")
            .take(maxQuestions)
            .mapIndexedNotNull { index, question -> parseQuestion(question, index) }
    }

    private fun parseQuestion(question: Element, index: Int): WorkQuestion? {
        val heading = question.selectFirst("h3.mark_name")
            ?: question.selectFirst("h3")
            ?: return null
        val metaText = heading.selectFirst("span.colorShallow")?.text()?.trim().orEmpty()
        val metaType = parseMetaType(metaText)
        val type = question.attr("typename").trim().ifBlank { metaType.ifBlank { "题目" } }
        val score = parseMetaScore(metaText) ?: SCORE.find(metaText)?.value

        val stemClone = heading.clone()
        stemClone.select("span.colorShallow").remove()
        val hasImages = stemClone.select("img").isNotEmpty()
        val rawStem = normalize(extractText(stemClone))
        val numberMatch = LEADING_NUMBER.find(rawStem)
        val number = numberMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
        val stem = (numberMatch?.let { rawStem.substring(it.range.last + 1) } ?: rawStem).trim()

        val options = question.select("li")
            .filter { li -> li.parents().none { parent -> EXCLUDED_CLASSES.any(parent::hasClass) } }
            .map { li -> normalize(extractText(li)).takeIf { it.length <= MAX_OPTION_LENGTH } }
            .filterNot { it.isNullOrBlank() }
            .distinct()
            .take(MAX_OPTIONS)
            .map { it.orEmpty() }

        if (stem.isBlank() && options.isEmpty()) return null
        return WorkQuestion(
            number = number,
            type = type,
            score = score,
            stem = stem,
            options = options,
            hasImages = hasImages,
        )
    }

    private fun extractText(element: Element): String {
        val builder = StringBuilder()
        fun walk(node: Node) {
            when (node) {
                is TextNode -> builder.append(node.text())
                is Element -> when (node.normalName()) {
                    "br" -> builder.append('\n')
                    "img" -> {
                        val marker = if (node.attr("src").contains("/latex/")) " [公式] " else " [图片] "
                        builder.append(marker)
                    }
                    "p", "div", "li", "tr" -> {
                        builder.append('\n')
                        node.childNodes().forEach(::walk)
                        builder.append('\n')
                    }
                    else -> node.childNodes().forEach(::walk)
                }
            }
        }
        element.childNodes().forEach(::walk)
        return builder.toString()
    }

    /** 括号里的内容形如「简答题, 20分」，题型与分值以第一个逗号分隔。 */
    private fun parseMetaType(metaText: String): String =
        metaInner(metaText).split(META_SEPARATOR, limit = 2).firstOrNull()?.trim().orEmpty()

    private fun parseMetaScore(metaText: String): String? =
        metaInner(metaText).split(META_SEPARATOR, limit = 2)
            .getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun metaInner(metaText: String): String = metaText.trim()
        .removePrefix("(")
        .removePrefix("（")
        .removeSuffix(")")
        .removeSuffix("）")
        .trim()

    private fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .replace('\u3000', ' ')
        .replace(Regex("""[ \t\u000B\f\r]+"""), " ")
        .replace(Regex(""" *\n *"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    private const val MAX_OPTIONS = 8
    private const val MAX_OPTION_LENGTH = 300
}
