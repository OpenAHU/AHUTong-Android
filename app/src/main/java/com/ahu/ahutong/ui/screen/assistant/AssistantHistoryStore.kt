package com.ahu.ahutong.ui.screen.assistant

import com.ahu.ahutong.assistant.AssistantServiceEvent
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.google.gson.Gson
import java.io.File
import java.util.UUID

data class AssistantSessionSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val messageCount: Int,
)

/**
 * 多会话本地历史：每次打开对话页会接着上次的会话，新建对话时把上一段留在这里。
 * 只落本机文件，不同步、不上传；审批卡不进入历史，工具卡会保留。
 */
internal class AssistantHistoryStore {

    data class Snapshot(
        val currentId: String?,
        val items: List<AssistantChatItem>,
        val summaries: List<AssistantSessionSummary>,
    )

    private val lock = Any()
    private val gson = Gson()
    private val file by lazy {
        runCatching {
            File(AppEnvironmentHolder.context().filesDir, FILE_NAME)
        }.getOrNull()
    }

    fun load(): Snapshot = synchronized(lock) {
        val data = read()
        val sessions = data.sessions.orEmpty()
        val current = sessions.firstOrNull { it.id == data.currentId }
            ?: sessions.maxByOrNull { it.updatedAt }
        Snapshot(
            currentId = current?.id,
            items = current?.items.orEmpty().toChatItems(),
            summaries = sessions.summaries(),
        )
    }

    fun summaries(): List<AssistantSessionSummary> = synchronized(lock) {
        read().sessions.orEmpty().summaries()
    }

    fun save(id: String, items: List<AssistantChatItem>) = synchronized(lock) {
        if (id.isBlank()) return@synchronized
        val data = read()
        val existing = data.sessions.orEmpty().firstOrNull { it.id == id }
        val stored = items.toStoredItems()
        if (stored.isEmpty() && existing == null) return@synchronized
        val session = AssistantHistorySession(
            id = id,
            title = titleOf(stored).ifBlank { existing?.title ?: DEFAULT_TITLE },
            updatedAt = System.currentTimeMillis(),
            items = stored,
        )
        val sessions = if (existing == null) {
            data.sessions.orEmpty() + session
        } else {
            data.sessions.orEmpty().map { if (it.id == id) session else it }
        }
        write(data.copy(currentId = id, sessions = sessions))
    }

    fun startSession(id: String) = synchronized(lock) {
        val data = read()
        val sessions = data.sessions.orEmpty()
        val next = if (sessions.any { it.id == id }) {
            data.copy(currentId = id)
        } else {
            data.copy(
                currentId = id,
                sessions = sessions + AssistantHistorySession(
                    id = id,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        write(next)
    }

    fun switchTo(id: String): List<AssistantChatItem>? = synchronized(lock) {
        val data = read()
        val session = data.sessions.orEmpty().firstOrNull { it.id == id } ?: return@synchronized null
        write(data.copy(currentId = id))
        session.items.orEmpty().toChatItems()
    }

    fun delete(id: String): Snapshot = synchronized(lock) {
        val data = read()
        val remaining = data.sessions.orEmpty().filterNot { it.id == id }
        val nextId = when {
            data.currentId != id -> data.currentId
            else -> remaining.maxByOrNull { it.updatedAt }?.id
        }
        val next = data.copy(currentId = nextId, sessions = remaining)
        write(next)
        val current = remaining.firstOrNull { it.id == nextId }
        Snapshot(
            currentId = current?.id,
            items = current?.items.orEmpty().toChatItems(),
            summaries = remaining.summaries(),
        )
    }

    private fun read(): AssistantHistorySnapshot {
        val target = file
        if (target == null || !target.exists()) return AssistantHistorySnapshot()
        return runCatching {
            val decoded = gson.fromJson(
                target.readText(Charsets.UTF_8),
                AssistantHistorySnapshot::class.java,
            ) ?: return@runCatching AssistantHistorySnapshot()
            AssistantHistorySnapshot(
                currentId = decoded.currentId.orEmpty(),
                sessions = decoded.sessions.orEmpty().filter { !it.id.isNullOrBlank() },
            )
        }.getOrDefault(AssistantHistorySnapshot())
    }

    private fun write(data: AssistantHistorySnapshot) {
        val target = file ?: return
        runCatching {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(gson.toJson(data), Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    private fun List<AssistantHistorySession>.summaries(): List<AssistantSessionSummary> =
        sortedByDescending { it.updatedAt }.map { session ->
            AssistantSessionSummary(
                id = session.id.orEmpty(),
                title = session.title.orEmpty().ifBlank { DEFAULT_TITLE },
                preview = previewOf(session.items.orEmpty()),
                updatedAt = session.updatedAt,
                messageCount = session.items.orEmpty().count {
                    it.type == TYPE_USER || it.type == TYPE_ASSISTANT
                },
            )
        }

    private fun titleOf(items: List<AssistantHistoryItem>): String {
        val first = items.firstOrNull { it.type == TYPE_USER && !it.text.isNullOrBlank() }
            ?.text
            ?.trim()
            .orEmpty()
        return first.take(TITLE_LIMIT)
    }

    private fun previewOf(items: List<AssistantHistoryItem>): String {
        val last = items.lastOrNull {
            (it.type == TYPE_USER || it.type == TYPE_ASSISTANT) && !it.text.isNullOrBlank()
        }?.text?.replace('\n', ' ')?.trim().orEmpty()
        return last.take(PREVIEW_LIMIT)
    }

    private fun List<AssistantChatItem>.toStoredItems(): List<AssistantHistoryItem> = mapNotNull { item ->
        when (item) {
            is AssistantChatItem.User -> AssistantHistoryItem(TYPE_USER, item.id, text = item.text)
            is AssistantChatItem.Assistant -> item.text.takeIf { it.isNotBlank() }?.let {
                AssistantHistoryItem(TYPE_ASSISTANT, item.id, text = it)
            }
            is AssistantChatItem.Tool -> AssistantHistoryItem(TYPE_TOOL, item.id, tool = item.event)
            is AssistantChatItem.Error -> AssistantHistoryItem(TYPE_ERROR, item.id, text = item.text)
            is AssistantChatItem.Approval -> null
        }
    }

    private fun List<AssistantHistoryItem>.toChatItems(): List<AssistantChatItem> = mapNotNull { item ->
        val id = item.id.orEmpty().ifBlank { UUID.randomUUID().toString() }
        when (item.type) {
            TYPE_USER -> AssistantChatItem.User(id, item.text.orEmpty())
            TYPE_ASSISTANT -> AssistantChatItem.Assistant(id, item.text.orEmpty())
            TYPE_TOOL -> item.tool?.let { AssistantChatItem.Tool(id, it) }
            TYPE_ERROR -> AssistantChatItem.Error(id, item.text.orEmpty())
            else -> null
        }
    }

    private companion object {
        const val FILE_NAME = "assistant_chat_sessions.json"
        const val DEFAULT_TITLE = "新对话"
        const val TITLE_LIMIT = 24
        const val PREVIEW_LIMIT = 60
        const val TYPE_USER = "user"
        const val TYPE_ASSISTANT = "assistant"
        const val TYPE_TOOL = "tool"
        const val TYPE_ERROR = "error"
    }
}

internal data class AssistantHistorySnapshot(
    val currentId: String? = "",
    val sessions: List<AssistantHistorySession>? = emptyList(),
)

internal data class AssistantHistorySession(
    val id: String? = "",
    val title: String? = null,
    val updatedAt: Long = 0L,
    val items: List<AssistantHistoryItem>? = emptyList(),
)

internal data class AssistantHistoryItem(
    val type: String? = "",
    val id: String? = "",
    val text: String? = null,
    val tool: AssistantServiceEvent.ToolResult? = null,
)
