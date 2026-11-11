package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ahu.ahutong.assistant.AssistantServiceEvent
import com.ahu.ahutong.assistant.model.AssistantMessage
import com.ahu.ahutong.assistant.safety.AssistantApprovalGate
import com.ahu.ahutong.assistant.safety.AssistantApprovalRequest
import java.util.UUID
import kotlinx.coroutines.Job

enum class AssistantApprovalDecision { PENDING, APPROVED, REJECTED }

sealed interface AssistantChatItem {
    val id: String

    data class User(override val id: String, val text: String) : AssistantChatItem

    data class Assistant(
        override val id: String,
        val text: String,
        val streaming: Boolean = false,
        /** 这一条是用户在确认卡同意后由 AI 给出的解答，需要挂 AI 答案提示。 */
        val academicAnswer: Boolean = false,
    ) : AssistantChatItem

    data class Reasoning(
        override val id: String,
        val text: String,
        val streaming: Boolean = false,
    ) : AssistantChatItem

    data class Tool(
        override val id: String,
        val event: AssistantServiceEvent.ToolResult,
    ) : AssistantChatItem

    data class Approval(
        override val id: String,
        val request: AssistantApprovalRequest,
        val decision: AssistantApprovalDecision = AssistantApprovalDecision.PENDING,
    ) : AssistantChatItem

    data class Error(override val id: String, val text: String) : AssistantChatItem

    data class Usage(override val id: String, val totalTokens: Int) : AssistantChatItem
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

    var historyVisible by mutableStateOf(false)
        private set

    var sessions by mutableStateOf<List<AssistantSessionSummary>>(emptyList())
        private set

    var currentSessionId by mutableStateOf<String?>(null)
        private set

    private val _items = mutableStateListOf<AssistantChatItem>()
    val items: List<AssistantChatItem> get() = _items

    private val historyStore = AssistantHistoryStore()
    private var historyLoaded = false
    private var runJob: Job? = null
    private var approvalGate: AssistantApprovalGate? = null
    private var pendingHistory: List<AssistantMessage> = emptyList()

    /** 从主页输入框进入：每次都开一段新对话。 */
    fun open(question: String) {
        ensureHistoryLoaded()
        cancelRun()
        beginNewSession()
        pendingHistory = emptyList()
        _items += AssistantChatItem.User(newId(), question)
        pendingQuestion = question
        visible = true
        persist()
    }

    /** 在 AI 层上滑进入对话页：新开一段空对话，不发送问题。 */
    fun openBlank() {
        ensureHistoryLoaded()
        cancelRun()
        beginNewSession()
        visible = true
    }

    private fun beginNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        _items.clear()
    }

    fun send(question: String) {
        if (running) return
        ensureHistoryLoaded()
        pendingHistory = buildHistory()
        _items += AssistantChatItem.User(newId(), question)
        pendingQuestion = question
        persist()
    }

    fun openHistory() {
        ensureHistoryLoaded()
        refreshSessions()
        historyVisible = true
    }

    fun closeHistory() {
        historyVisible = false
    }

    fun startNewSession() {
        ensureHistoryLoaded()
        cancelRun()
        if (_items.isNotEmpty()) persist()
        beginNewSession()
        refreshSessions()
        historyVisible = false
        visible = true
    }

    fun switchTo(sessionId: String) {
        ensureHistoryLoaded()
        if (sessionId == currentSessionId) {
            historyVisible = false
            return
        }
        cancelRun()
        persist()
        val items = runCatching { historyStore.switchTo(sessionId) }.getOrNull() ?: return
        currentSessionId = sessionId
        _items.clear()
        _items += items
        historyVisible = false
        visible = true
        refreshSessions()
    }

    fun deleteSession(sessionId: String) {
        ensureHistoryLoaded()
        if (sessionId == currentSessionId) {
            cancelRun()
            val snapshot = runCatching { historyStore.delete(sessionId) }.getOrNull()
            currentSessionId = snapshot?.currentId ?: UUID.randomUUID().toString()
            _items.clear()
            if (snapshot != null) _items += snapshot.items
        } else {
            runCatching { historyStore.delete(sessionId) }
        }
        refreshSessions()
    }

    fun persist() {
        val id = currentSessionId ?: return
        runCatching { historyStore.save(id, _items.toList()) }
        refreshSessions()
    }

    private fun ensureHistoryLoaded() {
        if (historyLoaded) return
        historyLoaded = true
        val snapshot = runCatching { historyStore.load() }.getOrNull()
        currentSessionId = snapshot?.currentId ?: UUID.randomUUID().toString()
        sessions = snapshot?.summaries.orEmpty()
        _items.clear()
        if (snapshot != null) _items += snapshot.items
    }

    private fun refreshSessions() {
        sessions = runCatching { historyStore.summaries() }.getOrDefault(emptyList())
    }

    /** 重新回答某一轮：丢弃该用户消息之后的全部内容，再按同一问题重跑。 */
    fun regenerate(userItemId: String) {
        if (running || pendingQuestion != null) return
        ensureHistoryLoaded()
        val index = _items.indexOfFirst { it.id == userItemId }
        if (index < 0) return
        val user = _items[index] as? AssistantChatItem.User ?: return
        val history = historyOf(_items.take(index))
        while (_items.size > index + 1) {
            _items.removeAt(_items.lastIndex)
        }
        pendingHistory = history
        pendingQuestion = user.text
        status = null
        persist()
    }

    /** 只回放用户与助手文本；工具卡、审批卡和错误不进入模型上下文。 */
    private fun buildHistory(): List<AssistantMessage> = historyOf(_items)

    private fun historyOf(items: List<AssistantChatItem>): List<AssistantMessage> =
        items.mapNotNull { item ->
            when (item) {
                is AssistantChatItem.User -> AssistantMessage(role = "user", content = item.text)
                is AssistantChatItem.Assistant -> item.text
                    .takeIf { it.isNotBlank() }
                    ?.let { AssistantMessage(role = "assistant", content = it) }
                else -> null
            }
        }.takeLast(24)

    fun consumeHistory(): List<AssistantMessage> {
        val history = pendingHistory
        pendingHistory = emptyList()
        return history
    }

    fun consumePending(): String? {
        val question = pendingQuestion
        pendingQuestion = null
        return question
    }

    fun attachJob(job: Job) {
        runJob = job
    }

    fun attachGate(gate: AssistantApprovalGate) {
        approvalGate = gate
    }

    fun appendApproval(request: AssistantApprovalRequest) {
        _items += AssistantChatItem.Approval(newId(), request)
    }

    fun approve(paymentPassword: String?) {
        val pending = _items.lastOrNull { it is AssistantChatItem.Approval && it.decision == AssistantApprovalDecision.PENDING }
            as? AssistantChatItem.Approval ?: return
        updateApprovalDecision(pending.id, AssistantApprovalDecision.APPROVED)
        approvalGate?.approve(paymentPassword)
    }

    fun reject() {
        val pending = _items.lastOrNull { it is AssistantChatItem.Approval && it.decision == AssistantApprovalDecision.PENDING }
            as? AssistantChatItem.Approval ?: return
        updateApprovalDecision(pending.id, AssistantApprovalDecision.REJECTED)
        approvalGate?.reject()
    }

    private fun updateApprovalDecision(id: String, decision: AssistantApprovalDecision) {
        val index = _items.indexOfFirst { it.id == id }
        if (index < 0) return
        val item = _items[index] as? AssistantChatItem.Approval ?: return
        _items[index] = item.copy(decision = decision)
    }

    fun updateRunning(value: Boolean) {
        running = value
    }

    fun updateStatus(text: String?) {
        status = text
    }

    fun startAssistant(academicAnswer: Boolean = false): String {
        val id = newId()
        _items += AssistantChatItem.Assistant(
            id = id,
            text = "",
            streaming = true,
            academicAnswer = academicAnswer,
        )
        return id
    }

    fun appendDelta(id: String, delta: String) {
        val index = _items.indexOfFirst { it.id == id }
        if (index < 0) return
        val item = _items[index] as? AssistantChatItem.Assistant ?: return
        _items[index] = item.copy(text = item.text + delta)
    }

    fun startReasoning(): String {
        val id = newId()
        _items += AssistantChatItem.Reasoning(id = id, text = "", streaming = true)
        return id
    }

    fun appendReasoning(id: String, delta: String) {
        val index = _items.indexOfFirst { it.id == id }
        if (index < 0) return
        val item = _items[index] as? AssistantChatItem.Reasoning ?: return
        _items[index] = item.copy(text = item.text + delta)
    }

    fun finishReasoning(id: String) {
        val index = _items.indexOfFirst { it.id == id }
        if (index < 0) return
        val item = _items[index] as? AssistantChatItem.Reasoning ?: return
        if (item.text.isBlank()) {
            _items.removeAt(index)
        } else {
            _items[index] = item.copy(streaming = false)
        }
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

    fun appendMessage(text: String, academicAnswer: Boolean = false) {
        _items += AssistantChatItem.Assistant(
            id = newId(),
            text = text,
            streaming = false,
            academicAnswer = academicAnswer,
        )
    }

    fun appendTool(event: AssistantServiceEvent.ToolResult) {
        _items += AssistantChatItem.Tool(newId(), event)
    }

    fun appendError(text: String) {
        _items += AssistantChatItem.Error(newId(), text)
    }

    /** 内容被拦截时，已上屏的流式文本必须整条丢弃，不能留在状态或历史里。 */
    fun discardStreaming(id: String) {
        val index = _items.indexOfFirst { it.id == id }
        if (index >= 0) _items.removeAt(index)
    }

    fun discardReasoningOfCurrentRound() {
        val lastUserIndex = _items.indexOfLast { it is AssistantChatItem.User }
        for (index in _items.lastIndex downTo lastUserIndex + 1) {
            if (_items[index] is AssistantChatItem.Reasoning) _items.removeAt(index)
        }
    }

    fun appendUsage(totalTokens: Int) {
        if (totalTokens > 0) {
            _items += AssistantChatItem.Usage(newId(), totalTokens)
        }
    }

    fun cancelRun() {
        runJob?.cancel()
        runJob = null
        approvalGate?.cancel()
        approvalGate = null
        running = false
        status = null
        _items.replaceAll { item ->
            if (item is AssistantChatItem.Approval && item.decision == AssistantApprovalDecision.PENDING) {
                item.copy(decision = AssistantApprovalDecision.REJECTED)
            } else {
                item
            }
        }
        finishStreaming()
    }

    /** 运行结束时兜底收尾，确保不会因为漏掉 Done 而一直按纯文本渲染。 */
    fun finishStreaming() {
        val last = _items.lastOrNull()
        if (last is AssistantChatItem.Reasoning && last.streaming) {
            finishReasoning(last.id)
        }
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
        persist()
        visible = false
        historyVisible = false
        pendingQuestion = null
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
