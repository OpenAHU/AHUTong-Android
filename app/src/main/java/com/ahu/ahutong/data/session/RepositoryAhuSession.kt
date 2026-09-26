package com.ahu.ahutong.data.session

import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.model.LoginOutcome
import kotlinx.coroutines.flow.StateFlow

/**
 * [AhuSession] 的生产实现：行为与原流程逐条对应，只是把散落的会话操作收进一处。
 *
 * 协作方全部经构造注入（[SessionSignIn] / [CredentialVault] / [SessionAccount] / [SessionResidue]），
 * 因此这个类可以在 JVM 单测里被完整驱动（见 AhuSessionContractTest）；生产装配见 [DefaultAhuSession]。
 *
 * - `signIn`：登录（爬虫 / 原生 / 回退策略不变），成功后标记已登录；
 * - `signOut`：登录态置为未登录 + 清理本机会话残留；
 * - `ensureFresh`：沿用 [SessionRefreshCoordinator] 的 generation 机制与"用凭据重登"策略，
 *   失败即标记过期并返回 false —— 有界刷新，不做后台无限重试。
 *   成功时只清派生的校园卡令牌（[SessionResidue.clearDerivedToken]）：刚刚建立的 Cookie 会话
 *   正是重试要用的东西，清掉它会让重试立刻再次失败。
 */
class RepositoryAhuSession(
    private val login: SessionSignIn,
    private val credentials: CredentialVault,
    private val account: SessionAccount,
    private val residue: SessionResidue
) : AhuSession {

    override val state: StateFlow<AhuSessionState.Status> = AhuSessionState.status

    override suspend fun signIn(username: String, password: String): AhuResult<LoginOutcome> {
        val result = login.signIn(username, password, preferNative = true)
        if (result.valueOrNull() is LoginOutcome.Success) {
            // 先在协调器里推进代号，再发布已认证状态：并发中的旧续期不能覆盖这次新登录。
            SessionRefreshCoordinator.onAuthenticated {
                AhuSessionState.markAuthenticated()
            }
        }
        return result
    }

    override suspend fun completeWebVerification() {
        SessionRefreshCoordinator.onAuthenticated {
            AhuSessionState.markAuthenticated()
        }
    }

    override suspend fun signOut() {
        SessionRefreshCoordinator.onSignedOut {
            AhuSessionState.markAnonymous()
        }
        residue.clear()
    }

    override suspend fun ensureFresh(observedGeneration: Long): Boolean {
        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(observedGeneration) {
            val user = account.currentUser()
                ?: return@refreshIfNeeded SessionRefreshCoordinator.RefreshOutcome.REJECTED
            val password = credentials.wisdomPassword()?.takeIf { it.isNotBlank() }
                ?: return@refreshIfNeeded SessionRefreshCoordinator.RefreshOutcome.REJECTED

            Log.i(TAG, "Refreshing expired first-party session")
            val loginResult = login.signIn(
                username = user.xh.toString(),
                password = password,
                preferNative = false
            )
            if (loginResult.valueOrNull() is LoginOutcome.Success) {
                residue.clearDerivedToken()
                SessionRefreshCoordinator.RefreshOutcome.SUCCESS
            } else {
                Log.w(TAG, "Session refresh failed: ${loginResult.errorOrNull()}")
                loginResult.errorOrNull().toRefreshOutcome()
            }
        }
        // 续期成功即回到已认证：协调器只负责并发与代号，登录态的写入留在会话层。
        if (refreshed) {
            SessionRefreshCoordinator.commitIfCurrent(observedGeneration + 1) {
                if (AhuSessionState.status.value != AhuSessionState.Status.Anonymous) {
                    AhuSessionState.markAuthenticated()
                }
            }
        } else {
            // failureKindOf 和 commitIfCurrent 共用非可重入锁，先读取失败类型再提交状态。
            val rejected = SessionRefreshCoordinator.failureKindOf(observedGeneration) ==
                SessionRefreshCoordinator.FailureKind.REJECTED
            SessionRefreshCoordinator.commitIfCurrent(observedGeneration) {
                // 手动登录可能已经推进代号；旧失败无权覆盖那个新会话。
                // 只有凭据被明确拒绝（REJECTED）才宣告过期、触发重新登录引导；
                // 瞬时失败（网络抖动/超时）保持静默——冷却窗外下个请求会自动再试，
                // 这是 0492acc 之前旧实现的自愈体验，不该退化成「抖一下就逼用户重登」。
                if (rejected && AhuSessionState.status.value != AhuSessionState.Status.Anonymous) {
                    AhuSessionState.markExpired()
                }
            }
        }
        return refreshed
    }

    private fun AhuError?.toRefreshOutcome(): SessionRefreshCoordinator.RefreshOutcome =
        when (this) {
            is AhuError.Unauthorized -> SessionRefreshCoordinator.RefreshOutcome.REJECTED
            is AhuError.Server ->
                if (code in 400..499) {
                    SessionRefreshCoordinator.RefreshOutcome.REJECTED
                } else {
                    SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
                }
            // Network / Timeout / ProtocolChanged / Unknown / null：一律按瞬时可自愈处理。
            else -> SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
        }

    private companion object {
        const val TAG = "AhuSession"
    }
}
