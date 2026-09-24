package com.ahu.ahutong.data.session

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.testing.FakeAhuSession
import com.ahu.ahutong.testing.FakeCredentialVault
import com.ahu.ahutong.testing.FakeSessionAccount
import com.ahu.ahutong.testing.FakeSessionResidue
import com.ahu.ahutong.testing.FakeSessionSignIn
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADR 0002 的契约测试：会话层的每个动作都由 fake 驱动，不再需要设备。
 *
 * 这些 fake（见 com.ahu.ahutong.testing）就是 P2 §4 要求的"每个接缝都有 fake 适配器"，
 * 它们同时把此前只能靠真机验证的 [RepositoryAhuSession] 变成可回归的：
 * 登录成功后标记已认证、登出清本机残留、续期有界且失败即过期。
 */
class AhuSessionContractTest {

    private val user = User("测试同学", "20210001")

    /**
     * [AhuSessionState] 是进程内单例（登录态的单一真相），用例之间显式复位，
     * 免得测试顺序影响断言。
     */
    @BeforeTest
    fun resetSessionState() = runBlocking {
        AhuSessionState.markAnonymous()
        // 协调器同样是进程内单例，而且现在会记住"这一代已经失败过"（ADR 0002 的一次性续期）；
        // 真实世界里由一次成功登录清掉，这里显式复位。
        SessionRefreshCoordinator.onAuthenticated()
    }

    @AfterTest
    fun leaveAnonymous() = AhuSessionState.markAnonymous()

    private fun session(
        login: FakeSessionSignIn = FakeSessionSignIn(),
        credentials: FakeCredentialVault = FakeCredentialVault(),
        account: FakeSessionAccount = FakeSessionAccount(),
        residue: FakeSessionResidue = FakeSessionResidue()
    ) = RepositoryAhuSession(login, credentials, account, residue)

    @Test
    fun `a successful sign in marks the session authenticated`() = runBlocking {
        val login = FakeSessionSignIn(AhuResult.Success(LoginOutcome.Success(user)))
        val subject = session(login = login)

        val outcome = subject.signIn("20210001", "secret")

        assertTrue(outcome.isSuccess)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
        // 首登保持"原生优先"，与仓库层默认值一致
        assertEquals(listOf(Triple("20210001", "secret", true)), login.calls)
    }

    @Test
    fun `a rejected sign in leaves the state alone`() = runBlocking {
        val subject = session(
            login = FakeSessionSignIn(AhuResult.Failure(AhuError.Unauthorized("密码错误")))
        )

        val outcome = subject.signIn("20210001", "wrong")

        assertFalse(outcome.isSuccess)
        assertEquals(AhuSessionState.Status.Anonymous, subject.state.value)
    }

    @Test
    fun `web verification is a domain state, not a failure`() = runBlocking {
        val subject = session(
            login = FakeSessionSignIn(
                AhuResult.Success(LoginOutcome.JwxtWebVerificationRequired(user))
            )
        )

        val outcome = subject.signIn("20210001", "secret")

        assertTrue(outcome.isSuccess)
    }

    @Test
    fun `sign out returns to anonymous and clears the local residue`() = runBlocking {
        val residue = FakeSessionResidue()
        val subject = session(residue = residue)
        AhuSessionState.markAuthenticated()

        subject.signOut()

        assertEquals(AhuSessionState.Status.Anonymous, subject.state.value)
        assertEquals(1, residue.clearCount)
    }

    @Test
    fun `a refresh response from before sign out cannot restore authentication`() = runBlocking {
        val subject = session()
        AhuSessionState.markAuthenticated()
        val oldGeneration = SessionRefreshCoordinator.currentGeneration()

        subject.signOut()
        val reused = subject.ensureFresh(oldGeneration)

        assertFalse(reused)
        assertEquals(AhuSessionState.Status.Anonymous, subject.state.value)
    }

    @Test
    fun `refresh without a stored account or credential does not call the gateway`() = runBlocking {
        val login = FakeSessionSignIn(AhuResult.Success(LoginOutcome.Success(user)))
        val subject = session(
            login = login,
            credentials = FakeCredentialVault(null),
            account = FakeSessionAccount(user)
        )
        AhuSessionState.markAuthenticated()

        val refreshed = subject.ensureFresh(SessionRefreshCoordinator.currentGeneration())

        assertFalse(refreshed)
        assertTrue(login.calls.isEmpty())
        assertEquals(AhuSessionState.Status.Expired, subject.state.value)
    }

    @Test
    fun `completing web verification authenticates the session and invalidates old requests`() =
        runBlocking {
            val subject = session()
            val oldGeneration = SessionRefreshCoordinator.currentGeneration()

            subject.completeWebVerification()

            assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
            assertEquals(oldGeneration + 1, SessionRefreshCoordinator.currentGeneration())
        }

    @Test
    fun `a successful refresh reuses the stored credential and marks the session authenticated`() =
        runBlocking {
            val login = FakeSessionSignIn(AhuResult.Success(LoginOutcome.Success(user)))
            val residue = FakeSessionResidue()
            val subject = session(
                login = login,
                credentials = FakeCredentialVault("stored-secret"),
                account = FakeSessionAccount(user),
                residue = residue
            )
            AhuSessionState.markExpired()

            val refreshed = subject.ensureFresh(SessionRefreshCoordinator.currentGeneration())

            assertTrue(refreshed)
            assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
            // 续期明确走爬虫（preferNative = false），与原实现一致
            assertEquals(listOf(Triple("20210001", "stored-secret", false)), login.calls)
            // 只清派生的校园卡令牌：第一方 Cookie 是刚建立的会话，登出才有权清它。
            assertEquals(1, residue.clearDerivedTokenCount)
            assertEquals(0, residue.clearCount)
        }

    @Test
    fun `a rejected refresh expires the session`() = runBlocking {
        val subject = session(
            login = FakeSessionSignIn(AhuResult.Failure(AhuError.Unauthorized("密码错误"))),
            credentials = FakeCredentialVault("stored-secret"),
            account = FakeSessionAccount(user)
        )
        AhuSessionState.markAuthenticated()

        val refreshed = subject.ensureFresh(SessionRefreshCoordinator.currentGeneration())

        assertFalse(refreshed)
        assertEquals(AhuSessionState.Status.Expired, subject.state.value)
    }

    @Test
    fun `a transient refresh failure does not expire the session`() = runBlocking {
        val subject = session(
            login = FakeSessionSignIn(AhuResult.Failure(AhuError.Network)),
            credentials = FakeCredentialVault("stored-secret"),
            account = FakeSessionAccount(user)
        )
        AhuSessionState.markAuthenticated()

        val refreshed = subject.ensureFresh(SessionRefreshCoordinator.currentGeneration())

        assertFalse(refreshed)
        // 网络抖动不该弹重新登录：保持已认证，冷却窗外下个请求会自愈
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }

    @Test
    fun `a failed refresh leaves the local residue untouched`() = runBlocking {
        val residue = FakeSessionResidue()
        val subject = session(
            login = FakeSessionSignIn(AhuResult.Failure(AhuError.Network)),
            credentials = FakeCredentialVault("stored-secret"),
            account = FakeSessionAccount(user),
            residue = residue
        )

        subject.ensureFresh(SessionRefreshCoordinator.currentGeneration())

        // 续期没成功就什么都不该清：清了令牌等于把可用的凭据也一起丢了。
        assertEquals(0, residue.clearDerivedTokenCount)
        assertEquals(0, residue.clearCount)
    }

    @Test
    fun `a stale generation reuses the refresh someone else already did`() = runBlocking {
        val login = FakeSessionSignIn(AhuResult.Success(LoginOutcome.Success(user)))
        val subject = session(
            login = login,
            credentials = FakeCredentialVault("stored-secret"),
            account = FakeSessionAccount(user)
        )

        val refreshed = subject.ensureFresh(SessionRefreshCoordinator.currentGeneration() - 1)

        assertTrue(refreshed)
        assertTrue(login.calls.isEmpty())
    }
}

/**
 * 网络层接缝到会话层的映射：这一跳决定了"被踢回登录页"是否真的会让界面进入重新登录。
 * 放在同一文件是因为它测的是同一个接缝的另一侧。
 */
class SessionExpiryHookContractTest {

    @BeforeTest
    fun resetSessionState() = AhuSessionState.markAnonymous()

    @Test
    fun `the hook forwards refresh to the session it was given`() = runBlocking {
        val session = FakeAhuSession().apply { refreshed = true }
        val hook = RepositorySessionExpiryHook(session)

        assertTrue(hook.refresh(observedGeneration = 7L))
        assertEquals(listOf(7L), session.refreshGenerations)
    }

    @Test
    fun `a network-reported expiry moves the current session state to expired`() = runBlocking {
        val hook = RepositorySessionExpiryHook(FakeAhuSession())
        AhuSessionState.markAuthenticated()

        hook.onExpired(SessionRefreshCoordinator.currentGeneration())

        assertEquals(AhuSessionState.Status.Expired, AhuSessionState.status.value)
    }

    @Test
    fun `an old response cannot expire a newly authenticated session`() = runBlocking {
        val hook = RepositorySessionExpiryHook(FakeAhuSession())
        AhuSessionState.markAuthenticated()
        val oldGeneration = SessionRefreshCoordinator.currentGeneration()
        SessionRefreshCoordinator.onAuthenticated()

        hook.onExpired(oldGeneration)

        assertEquals(AhuSessionState.Status.Authenticated, AhuSessionState.status.value)
    }
}
