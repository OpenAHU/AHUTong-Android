package com.ahu.ahutong.data.session

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.LoginOutcome
import kotlinx.coroutines.flow.StateFlow

/**
 * 会话的唯一入口（ADR 0002）。
 *
 * 它把"登录 / 登出 / 续期"集中到一处，并让 [state] 成为登录态唯一真相：
 * - 调用方不再各自拼装 Cookie 清理与 token 重置；
 * - 续期是**有界**的：临时失败保留会话并允许冷却重试；明确的凭据拒绝才进入 Expired。
 *
 * 实现见 [RepositoryAhuSession]；接口本身也让登录流程具备被契约测试替换的可能。
 */
interface AhuSession {

    val state: StateFlow<AhuSessionState.Status>

    /** 执行一次登录。[LoginOutcome.JwxtWebVerificationRequired] 也是成功返回（域状态，不是错误）。 */
    suspend fun signIn(username: String, password: String): AhuResult<LoginOutcome>

    /** Web 安全验证完成后确认会话已建立；只有验证 Cookie 成功导入后才能调用。 */
    suspend fun completeWebVerification()

    /** 主动登出：清空会话态与本机会话残留（业务缓存是否清空由调用方决定）。 */
    suspend fun signOut()

    /**
     * 会话失效时续期一次；返回 true 表示已续期、可重试原请求。
     * [observedGeneration] 由请求发出时打点，避免旧响应触发重复登录。
     */
    suspend fun ensureFresh(observedGeneration: Long): Boolean
}
