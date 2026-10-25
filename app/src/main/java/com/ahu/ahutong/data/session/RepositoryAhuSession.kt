package com.ahu.ahutong.data.session

import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.security.SecureStorageUnavailableException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * [AhuSession] 的生产实现：统一手动登录、退出和有界续期。
 *
 * 协作方全部经构造注入（[SessionSignIn] / [CredentialVault] / [SessionAccount] / [SessionResidue]），
 * 因此这个类可以在 JVM 单测里被完整驱动（见 AhuSessionContractTest）；生产装配见 [DefaultAhuSession]。
 *
 * - `signIn`：登录（爬虫 / 原生 / 回退策略不变），成功后标记已登录；
 * - `signOut`：登录态置为未登录 + 清理本机会话残留；
 * - `ensureFresh`：按服务范围协调续期；凭据被明确拒绝才标记过期，
 *   网络或上游临时失败保留会话，冷却后允许新的请求再次尝试。
 *   成功时只清派生的校园卡令牌（[SessionResidue.clearDerivedToken]）：刚刚建立的 Cookie 会话
 *   正是重试要用的东西，清掉它会让重试立刻再次失败。
 */
class RepositoryAhuSession(
    private val login: SessionSignIn,
    private val credentials: CredentialVault,
    private val account: SessionAccount,
    private val residue: SessionResidue,
    private val refreshLogin: SessionSignIn = login,
    private val refreshScope: SessionRefreshCoordinator.Scope = SessionRefreshCoordinator.Scope.ACADEMIC
) : AhuSession {

    override val state: StateFlow<AhuSessionState.Status> = AhuSessionState.status

    /**
     * 只有学业教务这条 lane 能改写全局登录态。付款码/余额（WISDOM）与校园卡
     * （CENTRAL_CAS）是派生服务：它们失败时只影响各自页面，不能把整个 App 踢到登录页。
     */
    private val canChangeGlobalStatus =
        refreshScope == SessionRefreshCoordinator.Scope.ACADEMIC

    override suspend fun signIn(username: String, password: String): AhuResult<LoginOutcome> {
        val result = login.signIn(username, password, preferNative = true)
        if (result.valueOrNull() is LoginOutcome.Success) {
            // 先在协调器里推进代号，再发布已认证状态：并发中的旧续期不能覆盖这次新登录。
            SessionRefreshCoordinator.onAuthenticated {
                residue.clearRetainedServiceSessions()
                AhuSessionState.markAuthenticated()
            }
        }
        return result
    }

    override suspend fun completeWebVerification() {
        SessionRefreshCoordinator.onAuthenticated {
            residue.clearRetainedServiceSessions()
            AhuSessionState.markAuthenticated()
        }
    }

    override suspend fun signOut() {
        SessionRefreshCoordinator.onSignedOut {
            AhuSessionState.markAnonymous()
        }
        residue.clear()
    }

    override suspend fun ensureFresh(observedGeneration: Long): Boolean =
        refresh(observedGeneration)

    private suspend fun refresh(observedGeneration: Long): Boolean {
        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            observedGeneration,
            scope = refreshScope
        ) { refreshWithRetry() }
        // 续期成功即回到已认证：协调器只负责并发与代号，登录态的写入留在会话层。
        if (refreshed) {
            SessionRefreshCoordinator.commitIfCurrent(observedGeneration + 1, refreshScope) {
                // A restarted process begins Anonymous even when its account was persisted.
                if (canChangeGlobalStatus &&
                    (AhuSessionState.status.value != AhuSessionState.Status.Anonymous ||
                        account.currentUser() != null)
                ) {
                    AhuSessionState.markAuthenticated()
                }
            }
        } else {
            // failureKindOf 和 commitIfCurrent 共用非可重入锁，先读取失败类型再提交状态。
            val rejected = SessionRefreshCoordinator.failureKindOf(observedGeneration, refreshScope) ==
                SessionRefreshCoordinator.FailureKind.REJECTED
            SessionRefreshCoordinator.commitIfCurrent(observedGeneration, refreshScope) {
                // 手动登录可能已经推进代号；旧失败无权覆盖那个新会话。
                // 只有凭据被明确拒绝（REJECTED）才宣告过期、触发重新登录引导；
                // 瞬时失败（网络抖动/超时）保持静默——冷却窗外下个请求会自动再试，
                // 这是 0492acc 之前旧实现的自愈体验，不该退化成「抖一下就逼用户重登」。
                if (rejected && canChangeGlobalStatus &&
                    (AhuSessionState.status.value != AhuSessionState.Status.Anonymous ||
                        account.currentUser() != null)
                ) {
                    AhuSessionState.markExpired()
                }
            }
        }
        return refreshed
    }

    /** Runs inside the coordinator's service lock and its single 30-second budget. */
    private suspend fun refreshWithRetry(): SessionRefreshCoordinator.RefreshOutcome {
        var consecutiveRejections = 0
        for (attempt in 0..RETRY_DELAYS_MS.size) {
            val loginResult = try {
                val user = account.currentUser()
                    ?: return SessionRefreshCoordinator.RefreshOutcome.CREDENTIALS_REJECTED
                val password = credentials.wisdomPasswordForRefresh()?.takeIf { it.isNotBlank() }
                    ?: return SessionRefreshCoordinator.RefreshOutcome.CREDENTIALS_REJECTED
                Log.i(TAG, "Refreshing first-party session (attempt=${attempt + 1})")
                refreshLogin.signIn(user.xh.toString(), password, preferNative = false)
            } catch (_: SecureStorageUnavailableException) {
                // An unreadable Keystore is not evidence that a credential is absent or wrong.
                consecutiveRejections = 0
                if (attempt == RETRY_DELAYS_MS.size) break
                delay(RETRY_DELAYS_MS[attempt])
                continue
            }

            if (loginResult.valueOrNull() is LoginOutcome.Success) {
                residue.clearDerivedToken()
                return SessionRefreshCoordinator.RefreshOutcome.SUCCESS
            }
            val error = loginResult.errorOrNull()
            Log.w(TAG, "Session refresh did not succeed (${error?.javaClass?.simpleName})")
            if (error is AhuError.Unauthorized) {
                consecutiveRejections++
                if (consecutiveRejections >= 2) {
                    return SessionRefreshCoordinator.RefreshOutcome.CREDENTIALS_REJECTED
                }
            } else {
                consecutiveRejections = 0
                // Human verification, WAF and protocol changes need user/server action.
                // Retrying those would only submit the same password unnecessarily.
                if (!error.isRetryableRefreshFailure()) {
                    return SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
                }
            }
            if (attempt < RETRY_DELAYS_MS.size) delay(RETRY_DELAYS_MS[attempt])
        }
        // A final single rejection separated by transport failures remains unconfirmed.
        return SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
    }

    private fun AhuError?.isRetryableRefreshFailure(): Boolean = when (this) {
        AhuError.Network, AhuError.Timeout -> true
        is AhuError.Server -> code == 408 || code == 429 || code in 500..599
        else -> false
    }

    private companion object {
        const val TAG = "AhuSession"

        val RETRY_DELAYS_MS = longArrayOf(500L, 1_500L)
    }
}
