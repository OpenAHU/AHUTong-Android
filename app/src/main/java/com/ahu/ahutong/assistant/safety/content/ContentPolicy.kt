package com.ahu.ahutong.assistant.safety.content

enum class ContentCategory(val id: String) {
    ILLEGAL("illegal"),
    POLITICAL("political"),
    SELF_HARM("self_harm"),
    JAILBREAK("jailbreak"),
    VENDOR("vendor"),
    SCOPE("scope"),
}

enum class ContentStage {
    INPUT,
    OUTPUT,
}

sealed interface ContentVerdict {

    data object Pass : ContentVerdict

    data class Block(
        val category: ContentCategory,
        val stage: ContentStage,
        val message: String,
        val terminate: Boolean = false,
    ) : ContentVerdict

    /** 不拦截，只提高本轮门槛；P0 先记录，流式策略在 P1 使用。 */
    data class Harden(val reasons: Set<ContentCategory>) : ContentVerdict

    /** 不拦截回答，但本轮不展示思维链。 */
    data object MuteReasoning : ContentVerdict
}

internal object ContentCopy {

    const val TERMINATED: String = "这类内容我无法提供。请尝试新开对话继续聊天吧。"

    const val RULE_VIOLATION_NOTICE: String = "该对话内容违反了我们的使用规则，请尝试新建对话继续。"

    const val SELF_HARM_INPUT: String =
        "听起来你现在可能很难受。可以先联系学校心理中心，或拨打全国心理援助热线 12356；如果有立即危险，请拨打 120。"

    const val SELF_HARM_OUTPUT: String = SELF_HARM_INPUT
}
