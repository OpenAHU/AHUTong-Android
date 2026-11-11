package com.ahu.ahutong.assistant.safety.content

/**
 * 按句释放思维链：命中系统提示词泄漏的句子替换为省略号，其余内容照常显示。
 */
internal class ReasoningRedactor(
    private val leakFilter: SystemPromptLeakFilter,
    private val onRelease: suspend (String) -> Unit,
) {

    private val buffer = StringBuilder()
    private var lastRedacted = false

    suspend fun accept(delta: String) {
        if (delta.isEmpty()) return
        buffer.append(delta)
        while (true) {
            val boundary = lastBoundary()
            if (boundary <= 0) {
                if (buffer.length > FORCE_RELEASE_CHARS) {
                    val end = buffer.length - TAIL_CHARS
                    if (end > 0) release(end)
                }
                return
            }
            release(boundary)
        }
    }

    suspend fun finish() {
        if (buffer.isEmpty()) return
        release(buffer.length)
    }

    private suspend fun release(end: Int) {
        if (end <= 0) return
        val segment = buffer.substring(0, end)
        buffer.delete(0, end)
        val text = redact(segment)
        if (text == REDACTION) {
            if (lastRedacted) return
            lastRedacted = true
        } else {
            lastRedacted = false
        }
        onRelease(text)
    }

    private fun lastBoundary(): Int {
        for (index in buffer.lastIndex downTo 0) {
            if (buffer[index] in BOUNDARIES) return index + 1
        }
        return 0
    }

    private fun redact(segment: String): String =
        if (segment.isNotBlank() && leakFilter.inspect(segment)) REDACTION else segment

    private companion object {
        val BOUNDARIES = charArrayOf('。', '！', '？', '!', '?', '；', ';', '\n')
        const val REDACTION = "（⋯）"
        const val FORCE_RELEASE_CHARS = 800
        const val TAIL_CHARS = 120
    }
}
