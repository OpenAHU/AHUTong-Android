package com.ahu.ahutong.assistant.safety

import com.google.gson.JsonObject
import java.math.BigDecimal
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AssistantWriteOutcome {
    SUCCESS,
    KNOWN_FAILURE,

    /** 请求发出去了但结果不明（超时/断网）：再次执行可能重复扣款。 */
    UNKNOWN,
}

/**
 * 写操作的本地幂等护栏：执行前登记 PENDING，执行后登记结果。
 * 上一次结果未知时，下一次相同操作会被拦下；只保存"工具名 + 参数"的摘要，不保存参数原文与支付密码。
 */
interface AssistantWriteJournal {
    /** 返回非 null 表示应拦截，并把这个说明交给模型/用户。 */
    suspend fun check(toolId: String, arguments: JsonObject): String?

    suspend fun begin(toolId: String, arguments: JsonObject)

    suspend fun finish(toolId: String, arguments: JsonObject, outcome: AssistantWriteOutcome)

    suspend fun clear()
}

class InMemoryAssistantWriteJournal : AssistantWriteJournal {

    private val mutex = Mutex()
    private val states = mutableMapOf<String, AssistantWriteOutcome>()

    override suspend fun check(toolId: String, arguments: JsonObject): String? = mutex.withLock {
        // 只有结果未知时拦截；SUCCESS / KNOWN_FAILURE 都已收尾，允许再次发起。
        if (states[fingerprint(toolId, arguments)] == AssistantWriteOutcome.UNKNOWN) {
            "上一次相同操作的结果未知，为避免重复扣款，本次已拦下；请先在原页面确认结果。"
        } else {
            null
        }
    }

    override suspend fun begin(toolId: String, arguments: JsonObject) {
        mutex.withLock { states[fingerprint(toolId, arguments)] = AssistantWriteOutcome.UNKNOWN }
    }

    override suspend fun finish(toolId: String, arguments: JsonObject, outcome: AssistantWriteOutcome) {
        mutex.withLock {
            val key = fingerprint(toolId, arguments)
            if (outcome == AssistantWriteOutcome.UNKNOWN) {
                states[key] = AssistantWriteOutcome.UNKNOWN
            } else {
                states.remove(key)
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock { states.clear() }
    }

    private fun fingerprint(toolId: String, arguments: JsonObject): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$toolId|${canonicalWriteArguments(arguments)}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

internal fun canonicalWriteArguments(arguments: JsonObject): String =
    arguments.entrySet()
        .filterNot { (key, _) -> key in SENSITIVE_KEYS }
        .sortedBy { it.key }
        .joinToString("&") { (key, value) ->
            val canonical = if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                runCatching {
                    BigDecimal(value.asString).stripTrailingZeros().toPlainString()
                }.getOrDefault(value.asString)
            } else {
                value.toString().trim()
            }
            "$key=$canonical"
        }

private val SENSITIVE_KEYS = setOf("password", "paymentPassword", "payPassword", "pwd")
