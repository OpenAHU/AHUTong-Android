package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface AssistantChatMessage {
    data class User(val text: String) : AssistantChatMessage
    data class Assistant(
        val summary: String,
        val lines: List<String> = emptyList(),
        val route: String? = null,
    ) : AssistantChatMessage
}

/**
 * AI 全屏问答页的状态：一旦发送消息就进入该页，后续对话都在这里累积。
 */
object AssistantChatState {

    var visible by mutableStateOf(false)
        private set

    var pendingQuestion by mutableStateOf<String?>(null)
        private set

    private val _messages = mutableStateListOf<AssistantChatMessage>()
    val messages: List<AssistantChatMessage> get() = _messages

    fun open(question: String) {
        _messages.clear()
        _messages += AssistantChatMessage.User(question)
        pendingQuestion = question
        visible = true
    }

    fun consumePending(): String? {
        val question = pendingQuestion
        pendingQuestion = null
        return question
    }

    fun append(message: AssistantChatMessage) {
        _messages += message
    }

    fun close() {
        visible = false
        pendingQuestion = null
    }
}
