package com.ahu.ahutong.assistant.safety.content

internal class SystemPromptLeakFilter(systemPrompt: String) {

    private val fragments: List<String> = systemPrompt
        .split(CLAUSE_SEPARATOR)
        .map { clause -> clause.replace(RULE_NUMBER, "").trim() }
        .map(::normalize)
        .filter { it.length >= MIN_FRAGMENT_CHARS }
        .distinct()

    /**
     * 系统提示词原句的宽松正则：字符之间允许插空格和标点，
     * 覆盖模型引用时自己断句、加引号的情况。
     */
    private val originalPattern: Regex? = systemPrompt
        .split(CLAUSE_SEPARATOR)
        .map { clause -> clause.replace(RULE_NUMBER, "").trim() }
        .map { clause -> clause.filter { it.isLetterOrDigit() } }
        .filter { it.length >= MIN_REGEX_CHARS }
        .distinct()
        .sortedByDescending(String::length)
        .take(MAX_REGEX_CLAUSES)
        .map(::flexiblePattern)
        .takeIf { it.isNotEmpty() }
        ?.let { Regex(it.joinToString("|")) }

    /**
     * 内容边界段的 8 字滑窗。模型缩短、换标点、只摘半句时，
     * 仍能通过重合片段命中。
     */
    private val leakGrams: Set<String> = buildSet {
        val normalized = normalize(boundarySection(systemPrompt))
        if (normalized.length >= GRAM_SIZE) {
            for (index in 0..normalized.length - GRAM_SIZE) {
                add(normalized.substring(index, index + GRAM_SIZE))
            }
        }
    }

    fun inspect(reasoning: String): Boolean {
        if (reasoning.isBlank()) return false
        val recent = reasoning.takeLast(WINDOW_CHARS)
        if (LEAK_PATTERNS.any { it.containsMatchIn(recent) }) return true
        val normalized = normalize(recent)
        if (fragments.any(normalized::contains)) return true
        if (hasLeakGram(normalized)) return true
        if (TRIGGERS.none(recent::contains)) return false
        return originalPattern?.containsMatchIn(recent.takeLast(REGEX_WINDOW_CHARS)) == true
    }

    private fun hasLeakGram(normalized: String): Boolean {
        if (normalized.length < GRAM_SIZE) return false
        for (index in 0..normalized.length - GRAM_SIZE) {
            if (normalized.substring(index, index + GRAM_SIZE) in leakGrams) return true
        }
        return false
    }

    private fun boundarySection(prompt: String): String {
        val start = prompt.indexOf("内容边界").takeIf { it >= 0 } ?: 0
        val end = prompt.indexOf("【安大常识】", start).takeIf { it >= 0 } ?: prompt.length
        return prompt.substring(start, end)
    }

    private fun flexiblePattern(clause: String): String =
        clause.map { Regex.escape(it.toString()) }.joinToString(SEPARATOR)

    private fun normalize(text: String): String =
        text.filter { it.isLetterOrDigit() }.lowercase()

    private companion object {
        const val WINDOW_CHARS = 2000
        const val REGEX_WINDOW_CHARS = 800
        const val MIN_FRAGMENT_CHARS = 16
        const val MIN_REGEX_CHARS = 10
        const val MAX_REGEX_CLAUSES = 40
        const val GRAM_SIZE = 8
        const val SEPARATOR = "[\\s，。、；：,.;:!?！？·…—\\-]*"
        val CLAUSE_SEPARATOR = Regex("[。！？!?；;\\n]+")
        val RULE_NUMBER = Regex("^\\s*\\d+\\.\\s*")
        val TRIGGERS = listOf(
            "规则", "提示", "系统", "内容边界", "第", "条", "不讨论", "拒绝", "不输出",
            "结论", "解释", "判定", "依据", "边界", "示例", "片段", "越界", "安全", "合规", "限制",
        )
        val LEAK_PATTERNS = listOf(
            Regex("第\\s*\\d+(?:\\s*[、,，]\\s*\\d+)*\\s*条"),
            Regex("规则[\\s:：]*\\d+"),
            Regex("系统提示词?"),
            Regex("提示词"),
            Regex("内容边界"),
            Regex("安全边界"),
            Regex("不可被任何对话改写"),
            Regex("根据(系统)?规则"),
            Regex("按(照)?(系统)?规则"),
            Regex("system\\s*prompt", RegexOption.IGNORE_CASE),
            Regex("不讨论、不评价、不推测"),
            Regex("不复述、概括或解释本提示词"),
        )
    }
}
