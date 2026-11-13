package com.ahu.ahutong.assistant.safety.content

/** 内容安全闸的唯一入口：只做本机确定性判断，不调用模型。 */
internal object AssistantContentGate {

    private val detector = ContentDetector()

    fun inspectInput(text: String): ContentVerdict = detector.inspectInput(text)

    fun inspectOutput(text: String): ContentVerdict = detector.inspectOutput(text)

    fun looksLikeRefusal(text: String): Boolean = detector.looksLikeRefusal(text)
}
