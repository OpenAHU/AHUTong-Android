package com.ahu.ahutong.assistant.safety.content

enum class ContentCategory(val id: String) {
    ILLEGAL("illegal"),
    POLITICAL("political"),
    SELF_HARM("self_harm"),
    JAILBREAK("jailbreak"),
    VENDOR("vendor"),
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
    ) : ContentVerdict

    /** 不拦截，只提高本轮门槛；P0 先记录，流式策略在 P1 使用。 */
    data class Harden(val reasons: Set<ContentCategory>) : ContentVerdict

    /** 不拦截回答，但本轮不展示思维链。 */
    data object MuteReasoning : ContentVerdict
}

internal object ContentCopy {

    const val REDIRECT: String = "你好，我们换个话题聊聊吧。"

    const val ILLEGAL_INPUT: String = REDIRECT

    const val ILLEGAL_OUTPUT: String = REDIRECT

    const val POLITICAL_INPUT: String = REDIRECT

    const val POLITICAL_OUTPUT: String = REDIRECT

    const val SELF_HARM_INPUT: String =
        "听起来你现在可能很难受。可以先联系学校心理中心，或拨打全国心理援助热线 12356；如果有立即危险，请拨打 120。"

    const val SELF_HARM_OUTPUT: String = SELF_HARM_INPUT

    const val MODEL_REJECTED: String = REDIRECT
}
