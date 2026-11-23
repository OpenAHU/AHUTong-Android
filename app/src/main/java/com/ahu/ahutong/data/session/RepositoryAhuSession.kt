package com.ahu.ahutong.data.session

import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.network.AppStatusReporting
import com.ahu.ahutong.data.network.StatusEndpoint
import com.ahu.ahutong.data.network.StatusReportingPolicy
import com.ahu.ahutong.data.security.SecureStorageUnavailableException
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
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
    private val refreshScope: SessionRefreshCoordinator.Scope = SessionRefreshCoordinator.Scope.ACADEMIC,
    private val now: () -> Long = System::currentTimeMillis
) : AhuSession {

    override val state: StateFlow<AhuSessionState.Status> = AhuSessionState.status

    /**
     * 只有学业教务这条 lane 能改写全局登录态。付款码/余额（WISDOM）与校园卡
     * （CENTRAL_CAS）是派生服务：它们失败时只影响各自页面，不能把整个 App 踢到登录页。
     */
    private val canChangeGlobalStatus =
        refreshScope == SessionRefreshCoordinator.Scope.ACADEMIC

    override suspend fun signIn(username: String, password: String): AhuResult<LoginOutcome> {
        FeedbackDiagnostics.clear()
        com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer.rememberPassword(password)
        FeedbackDiagnostics.recordRuntime("开始登录")
        val startedAt = now()
        val result = login.signIn(username, password, preferNative = true)
        AppStatusReporting.reportLoginResult("signIn", result, (now() - startedAt).coerceAtLeast(0L))
        FeedbackDiagnostics.recordRuntime("登录结果：${if (result.valueOrNull() is LoginOutcome.Success) "成功" else "未完成"}")
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
        FeedbackDiagnostics.clear()
        FeedbackDiagnostics.recordRuntime("退出账号")
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
            nowMillis = now,
            scope = refreshScope
        ) { refreshWithRetry() }
        // 续期成功即回到已认证：协调器只负责并发与代号，登录态的写入留在会话层。
        val hasAccount = hasRecoverableAccount()
        if (refreshed) {
            SessionRefreshCoordinator.commitIfCurrent(observedGeneration + 1, refreshScope) {
                // A restarted process begins Anonymous even when its account was persisted.
                if (canChangeGlobalStatus &&
                    (AhuSessionState.status.value != AhuSessionState.Status.Anonymous || hasAccount)
                ) {
                    AhuSessionState.markAuthenticated()
                }
            }
        } else {
            // 只有**跨轮确认**过的拒绝才宣告过期（ADR 0002：连续两次明确拒绝）。
            // 冷却窗内协调器返回 false，上一次的失败记录仍然是 REJECTED；
            // 若只看 failureKindOf，"一次拒绝 + 一个并发 401"就能把用户踢出去。
            val confirmed = SessionRefreshCoordinator.isRejectionConfirmed(observedGeneration, refreshScope)
            SessionRefreshCoordinator.commitIfCurrent(observedGeneration, refreshScope) {
                // 手动登录可能已经推进代号；旧失败无权覆盖那个新会话。
                // 瞬时失败（网络抖动/超时/存储不可用）保持静默——冷却窗外下个请求会自动再试。
                if (confirmed && canChangeGlobalStatus &&
                    (AhuSessionState.status.value != AhuSessionState.Status.Anonymous || hasAccount)
                ) {
                    FeedbackDiagnostics.recordRuntime("会话判定过期：连续两轮被校方拒绝")
                    AhuSessionState.markExpired()
                }
            }
        }
        return refreshed
    }

    private fun hasRecoverableAccount(): Boolean = try {
        account.currentUser() != null
    } catch (_: SecureStorageUnavailableException) {
        // 读不出来不等于有账号：不能凭一次读取失败把匿名/过期状态改写成已认证。
        false
    }

    /** Runs inside the coordinator's service lock and its single 30-second budget. */
    private suspend fun refreshWithRetry(): SessionRefreshCoordinator.RefreshOutcome {
        var lastReportableError: AhuError? = null
        val refreshStartedAt = now()
        for (attempt in 0..RETRY_DELAYS_MS.size) {
            val loginResult = try {
                val user = account.currentUser()
                    ?: return SessionRefreshCoordinator.RefreshOutcome.CREDENTIALS_REJECTED
                val password = credentials.wisdomPasswordForRefresh()?.takeIf { it.isNotBlank() }
                    ?: return SessionRefreshCoordinator.RefreshOutcome.CREDENTIALS_REJECTED
                com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer.rememberPassword(password)
                Log.i(TAG, "Refreshing first-party session (attempt=${attempt + 1})")
                refreshLogin.signIn(user.xh.toString(), password, preferNative = false)
            } catch (_: SecureStorageUnavailableException) {
                // An unreadable Keystore is not evidence that a credential is absent or wrong.
                if (attempt == RETRY_DELAYS_MS.size) break
                delay(RETRY_DELAYS_MS[attempt])
                continue
            }

            if (loginResult.valueOrNull() is LoginOutcome.Success) {
                residue.clearDerivedToken()
                AppStatusReporting.reportLoginResult(
                    "sessionRefresh",
                    loginResult,
                    (now() - refreshStartedAt).coerceAtLeast(0L)
                )
                return SessionRefreshCoordinator.RefreshOutcome.SUCCESS
            }
            val error = loginResult.errorOrNull()
            Log.w(TAG, "Session refresh did not succeed (${error?.javaClass?.simpleName})")
            if (error is AhuError.Unauthorized) {
                // 一次拒绝只是"疑似"：同一轮里立刻再提交一次密码，只会把一次
                // OCR/风控/校方抖动放大成"连续两次被拒"从而当场判死。
                // 真正的确认交给协调器跨轮完成（冷却窗 30s 之外的第二次拒绝）。
                FeedbackDiagnostics.recordRuntime("自动续期被校方拒绝：${error.message}")
                return SessionRefreshCoordinator.RefreshOutcome.REJECTED
            }
            if (error != null && StatusReportingPolicy.shouldReportLoginFailure(isMockMode = false, error = error)) {
                lastReportableError = error
            }
            // Human verification, WAF and protocol changes need user/server action.
            // Retrying those would only submit the same password unnecessarily.
            if (!error.isRetryableRefreshFailure()) {
                lastReportableError?.let {
                    AppStatusReporting.reportLoginResult(
                        "sessionRefresh",
                        AhuResult.Failure(it),
                        (now() - refreshStartedAt).coerceAtLeast(0L)
                    )
                }
                return SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
            }
            if (attempt < RETRY_DELAYS_MS.size) delay(RETRY_DELAYS_MS[attempt])
        }
        lastReportableError?.let {
            AppStatusReporting.reportLoginResult(
                "sessionRefresh",
                AhuResult.Failure(it),
                (now() - refreshStartedAt).coerceAtLeast(0L)
            )
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
