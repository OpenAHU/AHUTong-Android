package com.ahu.ahutong.assistant

/**
 * 第一版入口的确定性意图识别：不调用模型，把常见问句映射到能力 id。
 *
 * 顺序有意义：先判"空教室"，再判余额，最后才用泛化的"课"兜底。
 */
sealed interface AssistantIntent {
    data object FreeClassroom : AssistantIntent
    data object TodaySchedule : AssistantIntent
    data object CardBalance : AssistantIntent
    data object Unknown : AssistantIntent

    val capabilityId: String?
        get() = when (this) {
            FreeClassroom -> "free_classroom"
            TodaySchedule -> "today_schedule"
            CardBalance -> "card_balance"
            Unknown -> null
        }
}

object AssistantIntentParser {

    private val freeClassroomWords = listOf("空教室", "空闲教室", "没课的教室", "自习室", "教室空")
    private val balanceWords = listOf("余额", "校园卡", "饭卡", "一卡通", "卡里还有")
    private val scheduleWords = listOf("课表", "上课", "有课", "下节", "几节课", "什么课")

    fun parse(raw: String): AssistantIntent {
        val text = raw.trim()
        if (text.isEmpty()) return AssistantIntent.Unknown
        return when {
            freeClassroomWords.any { it in text } -> AssistantIntent.FreeClassroom
            balanceWords.any { it in text } -> AssistantIntent.CardBalance
            scheduleWords.any { it in text } -> AssistantIntent.TodaySchedule
            else -> AssistantIntent.Unknown
        }
    }
}
