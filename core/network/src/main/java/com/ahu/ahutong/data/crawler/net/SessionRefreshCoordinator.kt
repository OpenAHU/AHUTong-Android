package com.ahu.ahutong.data.crawler.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.Request

/**
 * Coordinates one first-party re-login for a burst of expired requests.
 *
 * 只负责"请求代号"：标记请求发出时的会话 generation，并保证并发失效时只真正续期一次。
 * 登录态的写入（过期 / 已认证）不在这里 —— 那是会话层的职责，网络层经 [SessionExpiryHook] 通知，
 * 会话层在自己的实现里写 AhuSessionState。
 */
object SessionRefreshCoordinator {

    /**
     * 一次续期尝试的结局。
     *
     * 失败分两类，待遇不同：
     * - [TRANSIENT]：网络抖动、超时、上游 5xx 等可自愈失败。封禁带冷却窗
     *   （[TRANSIENT_COOLDOWN_MS]），窗外第一个请求获准再试——旧实现（0492acc 之前）
     *   正是靠"下个请求再试"自愈的，永久封禁会把一次校园网抖动变成强制重新登录；
     * - [REJECTED]：凭据被明确拒绝或本机没有凭据。
     *   重试无意义且可能触发风控，封禁到下一次真实登录（[onAuthenticated]）。
     */
    enum class RefreshOutcome { SUCCESS, TRANSIENT, REJECTED }

    enum class FailureKind { TRANSIENT, REJECTED }

    // A failed academic login must not suppress Wisdom QR or campus-card CAS recovery.
    enum class Scope { ACADEMIC, WISDOM, CENTRAL_CAS }

    private val refreshMutex = Mutex()

    @Volatile
    private var generation = 0L

    /**
     * 失败记忆：防惊群（同一代的并发 401 只真正续期一次），但不再永久封禁——
     * [TRANSIENT] 失败过了冷却窗就放行一次新尝试；只有 [REJECTED] 才封到人工重登。
     */
    private data class Failure(val kind: FailureKind, val atMillis: Long)
    private val failures = mutableMapOf<Scope, Failure>()

    /** 登出后置真：匿名代号禁止自动续期，直到下一次真实登录。 */
    @Volatile
    private var refreshDisabled = false

    fun currentGeneration(): Long = generation

    /**
     * 一次真正的登录刚刚成功（用户手动登录或完成 Web 验证）：推进代号并清掉失败记忆。
     * 推进代号会让登录前发出的慢响应变成旧响应，避免它在新会话上再次触发自动续期。
     * 与自动续期共用同一把锁，防止较早启动的失败续期在新登录之后重新写回失败记忆。
     */
    suspend fun onAuthenticated(action: () -> Unit = {}) = refreshMutex.withLock {
        generation += 1
        failures.clear()
        refreshDisabled = false
        action()
    }

    /** 主动登出同样推进代号，使所有已发出的请求与在途续期立即失去提交资格。 */
    suspend fun onSignedOut(action: () -> Unit = {}) = refreshMutex.withLock {
        generation += 1
        // 退出后的匿名代号禁止自动续期；否则残留清理完成前的新请求还能拿旧凭据重登。
        // 下一次真实登录会由 onAuthenticated 推进代号并解除这道闸。
        refreshDisabled = true
        failures.clear()
        action()
    }

    /** 只让仍属于当前会话代号的结果提交副作用；与手动登录的代号推进原子互斥。 */
    suspend fun commitIfCurrent(observedGeneration: Long, action: suspend () -> Unit): Boolean =
        refreshMutex.withLock {
            if (generation != observedGeneration) return@withLock false
            action()
            true
        }

    /**
     * Pins the session generation that was current when a request actually left the client.
     * A slow response from the old session can otherwise arrive just after a successful refresh
     * and incorrectly start another full login.
     */
    fun tagRequest(request: Request): Request {
        if (request.tag(SessionRequestGeneration::class.java) != null) return request
        return request.newBuilder()
            .tag(SessionRequestGeneration::class.java, SessionRequestGeneration(generation))
            .build()
    }

    fun observedGeneration(request: Request): Long =
        request.tag(SessionRequestGeneration::class.java)?.value ?: generation

    suspend fun refreshIfNeeded(
        observedGeneration: Long,
        timeoutMillis: Long = REFRESH_TIMEOUT_MS,
        transientCooldownMillis: Long = TRANSIENT_COOLDOWN_MS,
        nowMillis: Long = System.currentTimeMillis(),
        scope: Scope = Scope.ACADEMIC,
        refresh: suspend () -> RefreshOutcome
    ): Boolean = refreshMutex.withLock {
        if (refreshDisabled) return@withLock false
        if (generation != observedGeneration) return@withLock true

        failures[scope]?.let { failure ->
            // 凭据被拒：封到人工重登；瞬时失败：冷却窗内快速失败（防惊群），窗外放行一次再试。
            if (failure.kind == FailureKind.REJECTED) return@withLock false
            if (nowMillis - failure.atMillis < transientCooldownMillis) return@withLock false
        }

        // ADR 0002 的有界刷新：单次续期带总超时；超时按瞬时失败处理（可冷却重试），不推进代号。
        val outcome = try {
            withTimeout(timeoutMillis) { refresh() }
        } catch (e: TimeoutCancellationException) {
            RefreshOutcome.TRANSIENT
        }
        when (outcome) {
            RefreshOutcome.SUCCESS -> {
                generation += 1
                failures.clear()
                true
            }
            RefreshOutcome.TRANSIENT -> {
                failures[scope] = Failure(FailureKind.TRANSIENT, nowMillis)
                false
            }
            RefreshOutcome.REJECTED -> {
                failures[scope] = Failure(FailureKind.REJECTED, nowMillis)
                false
            }
        }
    }

    /** 供会话层区分「这次失败要不要宣告过期」：只有 [FailureKind.REJECTED] 才该弹重新登录。 */
    suspend fun failureKindOf(
        observedGeneration: Long,
        scope: Scope = Scope.ACADEMIC
    ): FailureKind? = refreshMutex.withLock {
        if (generation == observedGeneration) failures[scope]?.kind else null
    }

    /** 单次续期的总预算：一次完整登录的合理上限，超了就不再等（ADR 0002）。 */
    const val REFRESH_TIMEOUT_MS = 30_000L

    /** 瞬时失败的冷却窗：窗内同代请求快速失败防惊群，窗外放行一次自愈尝试。 */
    const val TRANSIENT_COOLDOWN_MS = 15_000L
}

internal data class SessionRequestGeneration(val value: Long)

internal object SessionRefreshPolicy {
    const val EXPIRED_RESPONSE_HEADER = "X-AHUTong-Session-Expired"

    fun isMarkedExpired(responseHeader: String?): Boolean = responseHeader == "1"

    fun isFirstPartyLoginRedirect(requestUrl: HttpUrl, location: String?): Boolean {
        val target = location?.let(requestUrl::resolve) ?: return false
        val host = target.host.lowercase()
        if (host != "ahu.edu.cn" && !host.endsWith(".ahu.edu.cn")) return false

        val path = target.encodedPath.lowercase()
        val hasLoginPath = path.contains("tologin") ||
            path.contains("/login") ||
            path.contains("/cas/")
        return hasLoginPath
    }
}
