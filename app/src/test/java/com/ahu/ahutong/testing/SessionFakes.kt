package com.ahu.ahutong.testing

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.net.SessionExpiryHook
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.session.AhuSession
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.session.CredentialVault
import com.ahu.ahutong.data.session.SessionAccount
import com.ahu.ahutong.data.session.SessionResidue
import com.ahu.ahutong.data.session.SessionSignIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * P2 §4 要求的"每个接缝都有 fake 适配器"，集中在一处，界面与 feature 的单测共用。
 *
 * 它们只依赖接口，因此不碰设备、不联网；`DataStore` 与 Keystore 之类的东西留在了端口后面。
 */

/** [CredentialVault] 的 fake：内存里的一个密码槽（null 表示"没存过"）。 */
class FakeCredentialVault(var password: String? = null) : CredentialVault {

    override fun saveWisdomPassword(password: String) {
        this.password = password
    }

    override fun wisdomPassword(): String? = password

    override fun clearWisdomPassword() {
        password = null
    }
}

/** [SessionAccount] 的 fake：当前登录用户。 */
class FakeSessionAccount(var user: User? = null) : SessionAccount {

    override fun currentUser(): User? = user
}

/** [SessionResidue] 的 fake：两种语义各记各的次数。 */
class FakeSessionResidue : SessionResidue {

    var clearCount = 0
        private set

    var clearDerivedTokenCount = 0
        private set

    override fun clearDerivedToken() {
        clearDerivedTokenCount++
    }

    override suspend fun clear() {
        clearCount++
    }
}

/** [SessionSignIn] 的 fake：固定答案，并记下每次收到的参数。 */
class FakeSessionSignIn(
    var result: AhuResult<LoginOutcome> = AhuResult.Failure(AhuError.Network)
) : SessionSignIn {

    val calls = mutableListOf<Triple<String, String, Boolean>>()

    override suspend fun signIn(
        username: String,
        password: String,
        preferNative: Boolean
    ): AhuResult<LoginOutcome> {
        calls += Triple(username, password, preferNative)
        return result
    }
}

/** [SessionExpiryHook] 的 fake：网络层只经它通知会话层。 */
class FakeSessionExpiryHook : SessionExpiryHook {

    var expiredCount = 0
        private set

    var refreshResult = false

    val refreshCalls = mutableListOf<Long>()

    override suspend fun refresh(observedGeneration: Long): Boolean {
        refreshCalls += observedGeneration
        return refreshResult
    }

    override suspend fun onExpired(observedGeneration: Long) {
        expiredCount++
    }
}

/** [AhuSession] 的 fake：界面层的测试推着登录态走，不必有真实仓库。 */
class FakeAhuSession : AhuSession {

    private val _state = MutableStateFlow(AhuSessionState.Status.Anonymous)

    override val state: StateFlow<AhuSessionState.Status> = _state

    var signInResult: AhuResult<LoginOutcome> = AhuResult.Failure(AhuError.Network)

    var refreshed = false

    var signedOut = false

    val refreshGenerations = mutableListOf<Long>()

    override suspend fun signIn(username: String, password: String): AhuResult<LoginOutcome> =
        signInResult

    override suspend fun completeWebVerification() {
        _state.value = AhuSessionState.Status.Authenticated
    }

    override suspend fun signOut() {
        signedOut = true
        _state.value = AhuSessionState.Status.Anonymous
    }

    override suspend fun ensureFresh(observedGeneration: Long): Boolean {
        refreshGenerations += observedGeneration
        return refreshed
    }
}
