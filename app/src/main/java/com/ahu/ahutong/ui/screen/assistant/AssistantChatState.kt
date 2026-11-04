package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ahu.ahutong.assistant.AssistantServiceEvent
import kotlinx.coroutines.Job

sealed interface AssistantChatItem {
    val id: String

    data class User(override val id: String, val text: String) : AssistantChatItem

    data class Assistant(
        override val id: String,
        val text: String,
        val streaming: Boolean = false,
    ) : AssistantChatItem

    data class Tool(
        override val id: String,
        val event: AssistantServiceEvent.ToolResult,
    ) : AssistantChatItem

    data class Error(override val id: String, val text: String) : AssistantChatItem
}

/**
 * AI 全屏问答页的状态：消息、流式文本、工具卡、状态胶囊与取消。
 * 只保存展示状态，不参与能力执行。
 */
object AssistantChatState {

    var visible by mutableStateOf(false)
        private set

    var pendingQuestion by mutableStateOf<String?>(null)
        private set

    var status by mutableStateOf<String?>(null)
        private set

    var running by mutableStateOf(false)
        private set

    private val _items = mutableStateListOf<AssistantChatItem>()
    val items: List<AssistantChatItem> get() = _items

    private var nextId = 0
    private var runJob: Job? = null

    fun open(question: String) {
        cancelRun()
        _items.clear()
        _items += AssistantChatItem.User(newId(), question)
        pendingQuestion = question
        visible = true
    }

    fun send(question: String) {
        if (running) return
        _items += AssistantChatItem.User(newId(), question)
        pendingQuestion = question
    }

    fun consumePending(): String? {
        val question = pendingQuestion
        pendingQuestion = null
        return question
    }

    fun attachJob(job: Job) {
        runJob = job
    }

    fun updateRunning(value: Boolean) {
        running = value
    }

    fun updateStatus(text: String?) {
        status = text
    }

    fun startAssistant(): String {
        val id = newId()
        _items += AssistantChatItem.Assistant(id = id, text = "", streaming = true)
        return id
    }

    fun appendDelta(id: String, delta: String) {
        val index = _items.indexOfFirst { it.id == id }
        if (index < 0) return
        val item = _items[index] as? AssistantChatItem.Assistant ?: return
        _items[index] = item.copy(text = item.text + delta)
    }

    fun finishAssistant(id: String, text: String?) {
        val index = _items.indexOfFirst { it.id == id }
        if (index < 0) return
        val item = _items[index] as? AssistantChatItem.Assistant ?: return
        if (text == null && item.text.isBlank()) {
            _items.removeAt(index)
        } else {
            _items[index] = item.copy(text = text ?: item.text, streaming = false)
        }
    }

    fun appendMessage(text: String) {
        _items += AssistantChatItem.Assistant(newId(), text, streaming = false)
    }

    fun appendTool(event: AssistantServiceEvent.ToolResult) {
        _items += AssistantChatItem.Tool(newId(), event)
    }

    fun appendError(text: String) {
        _items += AssistantChatItem.Error(newId(), text)
    }

    fun cancelRun() {
        runJob?.cancel()
        runJob = null
        running = false
        status = null
        val streaming = _items.lastOrNull() as? AssistantChatItem.Assistant
        if (streaming?.streaming == true) {
            if (streaming.text.isBlank()) {
                _items.removeAt(_items.lastIndex)
            } else {
                _items[_items.lastIndex] = streaming.copy(streaming = false)
            }
        }
    }

    fun close() {
        cancelRun()
        visible = false
        pendingQuestion = null
    }

    private fun newId(): String = (++nextId).toString()
}
