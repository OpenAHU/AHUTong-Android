package com.ahu.ahutong.ui.screen.setup

import com.ahu.ahutong.data.session.AhuSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 登录提交后，即使旧身份清理异常，也要等完整登录成功才重新放行业务页面。 */
internal object LoginNavigationGate {
    private val pending = MutableStateFlow(false)
    val authenticationRequired: StateFlow<Boolean> = pending

    fun beginAuthentication() { pending.value = true }
    fun completeAuthentication() { pending.value = false }
}

/** 冷启动可沿用已持久化的身份；明确过期的会话不能继续进入业务页面。 */
internal fun canUseAuthenticatedPages(
    hasPersistedAccount: Boolean,
    status: AhuSessionState.Status,
    authenticationRequired: Boolean = false
): Boolean = hasPersistedAccount && status != AhuSessionState.Status.Expired && !authenticationRequired

internal fun canCancelLogin(
    originalAccountId: String?,
    currentAccountId: String?,
    status: AhuSessionState.Status,
    loginAttempted: Boolean
): Boolean = !loginAttempted && !originalAccountId.isNullOrBlank() &&
    originalAccountId == currentAccountId &&
    canUseAuthenticatedPages(hasPersistedAccount = true, status = status)
