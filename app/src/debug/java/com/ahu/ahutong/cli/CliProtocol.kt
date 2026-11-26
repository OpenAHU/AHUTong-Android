package com.ahu.ahutong.cli

import java.security.SecureRandom

/** One-shot approval bound to the exact request and account generation, never persisted. */
internal class CliApprovals(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Approval(val request: String, val owner: Long, val expiresAt: Long)
    private val pending = linkedMapOf<String, Approval>()
    private val random = SecureRandom()

    @Synchronized
    fun prepare(request: String, owner: Long): String {
        pending.entries.removeAll { it.value.expiresAt <= clock() }
        check(pending.size < 32) { "Too many pending confirmations" }
        val token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        pending[token] = Approval(request, owner, clock() + 120_000)
        return token
    }

    @Synchronized
    fun consume(token: String, request: String, owner: Long): Boolean {
        val entry = pending.remove(token) ?: return false
        return entry.request == request && entry.owner == owner && entry.expiresAt > clock()
    }
}

internal object CliAccess {
    fun permitted(uid: Int): Boolean = uid == 2000 || uid == 0
}
