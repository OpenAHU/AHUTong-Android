package com.ahu.ahutong.assistant.safety

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.session.SessionStore
import com.google.gson.JsonObject
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 跨进程的写操作幂等护栏：只记录"结果未知"的指纹，进程被杀也不丢。
 * 指纹不含参数原文和支付密码，只保存 SHA-256 摘要。
 */
class PersistentAssistantWriteJournal : AssistantWriteJournal {

    private val prefs = AppEnvironmentHolder.context()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun check(toolId: String, arguments: JsonObject): String? = mutex.withLock {
        if (key(toolId, arguments) in blocked()) {
            "上一次相同操作的结果未知，为避免重复扣款，本次已拦下；请先在原页面确认结果。"
        } else {
            null
        }
    }

    override suspend fun begin(toolId: String, arguments: JsonObject) {
        mutex.withLock {
            val key = key(toolId, arguments)
            prefs.edit().putStringSet(KEY_BLOCKED, blocked() + key).commit()
        }
    }

    override suspend fun finish(toolId: String, arguments: JsonObject, outcome: AssistantWriteOutcome) {
        mutex.withLock {
            val key = key(toolId, arguments)
            val current = blocked()
            val next = if (outcome == AssistantWriteOutcome.UNKNOWN) current + key else current - key
            prefs.edit().putStringSet(KEY_BLOCKED, next).commit()
        }
    }

    override suspend fun clear() {
        mutex.withLock { prefs.edit().remove(KEY_BLOCKED).commit() }
    }

    private fun blocked(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED, emptySet()).orEmpty().toSet()

    private fun key(toolId: String, arguments: JsonObject): String {
        val account = runCatching { SessionStore.currentUser()?.xh.orEmpty() }.getOrDefault("")
        val canonical = arguments.entrySet()
            .filterNot { (name, _) -> name in SENSITIVE_KEYS }
            .sortedBy { it.key }
            .joinToString("&") { (name, value) -> "$name=${value}" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$account|$toolId|$canonical".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS = "assistant_write_journal"
        const val KEY_BLOCKED = "blocked_v1"
        val SENSITIVE_KEYS = setOf("password", "paymentPassword", "payPassword", "pwd")
    }
}
