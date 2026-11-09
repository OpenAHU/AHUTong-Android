package com.ahu.ahutong.ui.screen.assistant

internal data class RoundNotices(
    val usedWorkDetail: Boolean = false,
    val academicAnswer: Boolean = false,
)

/** 按用户轮统计提示条条件；审批卡会把一轮拆成多个 Turn，所以不能只看单个 Turn。 */
internal fun buildRoundNotices(items: List<AssistantChatItem>): Map<String, RoundNotices> {
    val result = mutableMapOf<String, RoundNotices>()
    var currentUser: String? = null
    var usedWorkDetail = false
    var academicAnswer = false

    fun flush() {
        currentUser?.let { result[it] = RoundNotices(usedWorkDetail, academicAnswer) }
    }

    items.forEach { item ->
        when (item) {
            is AssistantChatItem.User -> {
                flush()
                currentUser = item.id
                usedWorkDetail = false
                academicAnswer = false
            }
            is AssistantChatItem.Tool -> {
                if (item.event.ok && item.event.toolId == "get_work_detail") usedWorkDetail = true
            }
            is AssistantChatItem.Assistant -> if (item.academicAnswer) academicAnswer = true
            else -> Unit
        }
    }
    flush()
    return result
}

internal data class ToolSegment(
    val assistant: AssistantChatItem.Assistant?,
    val reasonings: List<AssistantChatItem.Reasoning>,
    val tools: List<AssistantChatItem.Tool>,
) {
    val key: String get() = assistant?.id ?: reasonings.lastOrNull()?.id ?: tools.firstOrNull()?.id.orEmpty()
}

/**
 * 按「每段 AI 文本回复」切分工具调用；工具跟随它前面最近的那段文字。
 * 没有前文时（模型第一步直接调工具）工具单独成段，保证显示顺序与发生顺序一致。
 */
internal fun toolSegments(items: List<AssistantChatItem>): List<ToolSegment> {
    val result = mutableListOf<ToolSegment>()
    var assistant: AssistantChatItem.Assistant? = null
    val reasonings = mutableListOf<AssistantChatItem.Reasoning>()
    val tools = mutableListOf<AssistantChatItem.Tool>()

    fun flush() {
        if (assistant != null || reasonings.isNotEmpty() || tools.isNotEmpty()) {
            result += ToolSegment(assistant, reasonings.toList(), tools.toList())
            assistant = null
            reasonings.clear()
            tools.clear()
        }
    }

    items.forEach { item ->
        when (item) {
            is AssistantChatItem.Reasoning -> {
                // 新的思考段意味着上一段（文字/工具）已经结束。
                if (assistant != null || tools.isNotEmpty()) flush()
                reasonings += item
            }
            is AssistantChatItem.Assistant -> {
                if (assistant != null || tools.isNotEmpty()) flush()
                assistant = item
            }
            is AssistantChatItem.Tool -> tools += item
            else -> Unit
        }
    }
    flush()
    return result
}
