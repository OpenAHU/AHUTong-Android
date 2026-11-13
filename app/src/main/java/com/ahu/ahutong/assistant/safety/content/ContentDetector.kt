package com.ahu.ahutong.assistant.safety.content

import kotlin.math.abs

internal class ContentDetector(
    private val pack: ContentRulePack = ContentRulePack.default(),
) {

    fun inspectInput(raw: String): ContentVerdict {
        val text = ContentNormalizer.normalize(raw)
        if (text.isBlank()) return ContentVerdict.Pass
        blockForSelfHarm(text, ContentStage.INPUT)?.let { return it }
        blockForIllegal(text, ContentStage.INPUT)?.let { return it }
        blockForPolitical(text, ContentStage.INPUT)?.let { return it }

        val reasons = mutableSetOf<ContentCategory>()
        if (pack.jailbreak.any { it.containsMatchIn(text) }) {
            return ContentVerdict.Block(
                ContentCategory.JAILBREAK,
                ContentStage.INPUT,
                ContentCopy.TERMINATED,
                terminate = true,
            )
        }
        if (pack.promptExtractionHard.any { it.containsMatchIn(text) }) {
            return ContentVerdict.Block(
                ContentCategory.JAILBREAK,
                ContentStage.INPUT,
                ContentCopy.TERMINATED,
                terminate = true,
            )
        }
        if (pack.encoding.any { it.containsMatchIn(text) }) reasons += ContentCategory.JAILBREAK
        if (reasons.isNotEmpty()) return ContentVerdict.Harden(reasons)
        if (pack.promptExtraction.any { it.containsMatchIn(text) }) return ContentVerdict.MuteReasoning
        return ContentVerdict.Pass
    }

    /** 模型自己拒绝时的文本兜底；只认短回答，避免误伤正常的“无法提供实时数据”。 */
    fun looksLikeRefusal(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > REFUSAL_MAX_CHARS) return false
        return REFUSAL_PATTERNS.any { it.containsMatchIn(trimmed) }
    }

    fun inspectOutput(raw: String): ContentVerdict {
        val text = ContentNormalizer.normalize(raw)
        if (text.isBlank()) return ContentVerdict.Pass
        blockForSelfHarm(text, ContentStage.OUTPUT)?.let { return it }
        blockForIllegal(text, ContentStage.OUTPUT)?.let { return it }
        blockForPolitical(text, ContentStage.OUTPUT)?.let { return it }
        return ContentVerdict.Pass
    }

    private fun blockForSelfHarm(text: String, stage: ContentStage): ContentVerdict.Block? {
        return when (stage) {
            ContentStage.INPUT -> {
                if (pack.selfHarm.any { it.containsMatchIn(text) }) {
                    ContentVerdict.Block(ContentCategory.SELF_HARM, stage, ContentCopy.SELF_HARM_INPUT)
                } else {
                    null
                }
            }
            ContentStage.OUTPUT -> sentences(text).firstNotNullOfOrNull { sentence ->
                val risky = pack.selfHarmOutput.any { it.containsMatchIn(sentence) }
                if (risky && !isCareContext(sentence)) {
                    ContentVerdict.Block(ContentCategory.SELF_HARM, stage, ContentCopy.SELF_HARM_OUTPUT)
                } else {
                    null
                }
            }
        }
    }

    private fun blockForIllegal(text: String, stage: ContentStage): ContentVerdict.Block? {
        for (sentence in sentences(text)) {
            for (rule in pack.illegal) {
                if (containsComposition(sentence, rule, exemptTargets = true)) {
                    return ContentVerdict.Block(
                        ContentCategory.ILLEGAL,
                        stage,
                        ContentCopy.TERMINATED,
                        terminate = true,
                    )
                }
            }
        }
        return null
    }

    private fun blockForPolitical(text: String, stage: ContentStage): ContentVerdict.Block? {
        for (sentence in sentences(text)) {
            val requiresRequest = stage == ContentStage.INPUT
            if (requiresRequest && !hasRequestVerb(sentence)) continue
            val composed = pack.political.any { rule ->
                containsComposition(sentence, rule, exemptTargets = false)
            }
            val phrased = pack.politicalPhrases.any { it.containsMatchIn(sentence) }
            if (composed || phrased) {
                return ContentVerdict.Block(
                    ContentCategory.POLITICAL,
                    stage,
                    ContentCopy.TERMINATED,
                    terminate = true,
                )
            }
        }
        return null
    }

    private fun containsComposition(
        sentence: String,
        rule: ContentRule,
        exemptTargets: Boolean,
    ): Boolean {
        val targetHits = rule.targets.flatMap { target ->
            indexesOf(sentence, target).map { index -> index to target.length }
        }
        if (targetHits.isEmpty()) return false
        val actionHits = rule.actions.flatMap { action -> indexesOf(sentence, action) }
        if (actionHits.isEmpty()) return false
        return targetHits.any { (targetIndex, targetLength) ->
            !(exemptTargets && isExempted(sentence, targetIndex, targetLength)) &&
                actionHits.any { actionIndex -> abs(actionIndex - targetIndex) <= rule.maxGap }
        }
    }

    private fun indexesOf(text: String, needle: String): List<Int> {
        if (needle.isEmpty()) return emptyList()
        val result = mutableListOf<Int>()
        var index = text.indexOf(needle)
        while (index >= 0) {
            result += index
            index = text.indexOf(needle, index + 1)
        }
        return result
    }

    private fun isExempted(sentence: String, index: Int, length: Int): Boolean {
        val before = sentence.substring(maxOf(0, index - 4), index)
        val after = sentence.substring(
            minOf(sentence.length, index + length),
            minOf(sentence.length, index + length + 4),
        )
        return pack.exemptions.any { before.contains(it) || after.contains(it) }
    }

    private fun isCareContext(sentence: String): Boolean =
        CARE_CONTEXT.any(sentence::contains)

    private fun hasRequestVerb(sentence: String): Boolean =
        pack.requestVerbs.any(sentence::contains)

    private fun sentences(text: String): List<String> =
        text.split(SENTENCE_SEPARATOR).map(String::trim).filter(String::isNotEmpty)

    private companion object {
        const val REFUSAL_MAX_CHARS = 300
        val SENTENCE_SEPARATOR = Regex("[。！？!?；;\\n]+")
        val CARE_CONTEXT = listOf("干预", "预防", "识别", "帮助", "救助", "保护", "热线", "求助")
        val REFUSAL_PATTERNS = listOf(
            Regex("这类(内容|话题|问题).{0,12}(无法提供|不能提供|不(会|做|展开|讨论)|不表态)"),
            Regex("(无法|不能)提供(这类|该|相关)(内容|话题|问题)"),
            Regex("不在(我|本助手)?(能|可以)?(回答|服务|处理)?的?范围"),
            Regex("我(不|不会)(对|就)?(这类|该|相关)?话题?(做|展开)?(讨论|表态)"),
            Regex("我没有站哪一方"),
        )
    }
}
