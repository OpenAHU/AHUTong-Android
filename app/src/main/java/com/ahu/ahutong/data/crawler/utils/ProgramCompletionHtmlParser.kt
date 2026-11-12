package com.ahu.ahutong.data.crawler.utils

import com.ahu.ahutong.data.crawler.model.jwxt.ProgramCompletion
import com.ahu.ahutong.data.crawler.model.jwxt.RawProgramCompletionModel
import com.ahu.ahutong.data.crawler.model.jwxt.toDomain
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * 培养方案完成情况页（`/student/for-std/program-completion-preview/info/{stdId}`）的
 * HTML 解析器：核心数据是内嵌 `<script>` 里的单引号风格 JS 字面量 `var model = {...}`。
 *
 * 解析链（调研报告 §1.3，实测可行）：
 * 1. 多正则变体定位 `var model =`（教务前端改版时容忍空格/声明差异，竞品 AHU Plus 同思路）；
 * 2. 从其后第一个 `{` 起做括号配对扫描（跳过字符串与转义），比正则耐字段变化；
 * 3. JS 字面量 → JSON 规整（引号与转义序列、裸 `undefined` → null）；
 * 4. Gson 反序列化后映射为领域模型 [ProgramCompletion]。
 *
 * 纯函数，不碰网络与登录态。任何解析失败抛 [ParseException]（带 detail），
 * 由调用方归 [com.ahu.ahutong.core.common.AhuError.ProtocolChanged]——校方改版要明确报错，不当网络错误。
 */
object ProgramCompletionHtmlParser {

    class ParseException(message: String) : Exception(message)

    /** 定位变体：`var model =` / `var model=` / 裸 `model =`（含 window.model）。 */
    private val assignmentPatterns = listOf(
        Regex("""(?i)(?:\bvar\b|\blet\b|\bconst\b)\s+(?:window\s*\.\s*)?model\s*="""),
        Regex("""(?i)(?:window\s*\.\s*)?model\s*:\s*"""),   // 对象字面量内嵌形式
        Regex("""(?i)(?:window\s*\.\s*)?model\s*=\s*""")
    )

    fun hasModelAssignment(html: String): Boolean =
        assignmentPatterns.any { it.containsMatchIn(html) }

    fun parse(html: String): ProgramCompletion {
        val raw = parseRaw(html)
        val domain = raw.toDomain()
        // 改版哨兵：关键字段全空基本是结构变了，宁可抛错也不展示全零数据
        if (domain.modules.isEmpty() && domain.requiredCredits == 0.0) {
            throw ParseException("model 解析结果为空（modules=0, requiredCredits=0），疑似教务改版")
        }
        return domain
    }

    internal fun parseRaw(html: String): RawProgramCompletionModel {
        val literal = extractModelLiteral(html)
        val json = jsLiteralToJson(literal)
        try {
            return Gson().fromJson(json, RawProgramCompletionModel::class.java)
                ?: throw ParseException("model 反序列化为 null")
        } catch (e: JsonSyntaxException) {
            throw ParseException("model JSON 解析失败：${e.message}")
        }
    }

    /** 括号配对提取 `var model = {...}` 的完整对象字面量（附录 A 算法的 Kotlin 移植）。 */
    internal fun extractModelLiteral(html: String): String {
        val match = assignmentPatterns.firstNotNullOfOrNull { it.find(html) }
            ?: throw ParseException("未找到 var model 赋值（所有变体均不匹配）")
        val objectStart = html.indexOf('{', match.range.last + 1)
        if (objectStart < 0) throw ParseException("var model 后未找到对象起始 '{'")

        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in objectStart until html.length) {
            val char = html[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(objectStart, index + 1)
                }
            }
        }
        throw ParseException("var model 对象字面量不完整（括号未闭合）")
    }

    /**
     * JS 单引号对象字面量 → JSON。
     * - 单引号串 → 双引号串：转义串内的双引号，还原转义的单引号；
     * - 字符串内的 JS 十六进制转义 `\xHH` 转为 JSON 的 `\u00HH`；
     * - 串外裸标识符 `undefined` → `null`。
     */
    internal fun jsLiteralToJson(src: String): String {
        val out = StringBuilder(src.length + 64)
        var i = 0
        while (i < src.length) {
            when (val c = src[i]) {
                '"', '\'' -> {
                    out.append('"')
                    i++
                    while (i < src.length) {
                        val d = src[i++]
                        when {
                            d == c -> break
                            d == '\\' && i < src.length -> {
                                val escaped = src[i++]
                                when {
                                    escaped == 'x' && i + 1 < src.length &&
                                        src[i] in HEX_DIGITS && src[i + 1] in HEX_DIGITS -> {
                                        out.append("\\u00").append(src, i, i + 2)
                                        i += 2
                                    }
                                    c == '\'' && escaped == '\'' -> out.append('\'')
                                    else -> out.append('\\').append(escaped)
                                }
                            }
                            d == '"' -> out.append("\\\"")
                            else -> out.append(d)
                        }
                    }
                    out.append('"')
                }
                else -> {
                    // 串外裸 undefined → null（Gson 不认识 undefined）
                    if ((c == 'u') && src.startsWith("undefined", i) &&
                        (i == 0 || !src[i - 1].isJavaIdentifierPart()) &&
                        (i + 9 >= src.length || !src[i + 9].isJavaIdentifierPart())
                    ) {
                        out.append("null")
                        i += 9
                    } else {
                        out.append(c)
                        i++
                    }
                }
            }
        }
        return out.toString()
    }

    private const val HEX_DIGITS = "0123456789abcdefABCDEF"
}
