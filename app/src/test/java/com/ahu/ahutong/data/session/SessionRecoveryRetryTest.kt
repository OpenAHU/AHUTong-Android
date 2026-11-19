package com.ahu.ahutong.data.session

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.security.SecureStorageUnavailableException
import com.ahu.ahutong.testing.FakeCredentialVault
import com.ahu.ahutong.testing.FakeSessionAccount
import com.ahu.ahutong.testing.FakeSessionResidue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionRecoveryRetryTest {
    private val user = User("测试同学", "fixture-account")
    private val success = AhuResult.Success(LoginOutcome.Success(user))
    private val rejected = AhuResult.Failure(AhuError.Unauthorized("fixture rejection"))

    @Before
    fun reset() = runBlocking {
        SessionRefreshCoordinator.onAuthenticated()
        AhuSessionState.markAuthenticated()
    }

    private fun session(login: suspend () -> AhuResult<LoginOutcome>) = RepositoryAhuSession(
        login = SessionSignIn { _, _, _ -> login() },
        credentials = FakeCredentialVault("fixture-only"),
        account = FakeSessionAccount(user),
        residue = FakeSessionResidue()
    )

    private fun sessionWithClock(
        clock: () -> Long,
        login: suspend () -> AhuResult<LoginOutcome>
    ) = RepositoryAhuSession(
        login = SessionSignIn { _, _, _ -> login() },
        credentials = FakeCredentialVault("fixture-only"),
        account = FakeSessionAccount(user),
        residue = FakeSessionResidue(),
        now = clock
    )

    @Test
    fun `a single credential rejection is not published as expiry`() = runTest {
        var calls = 0
        val subject = session { calls++; rejected }
        val generation = SessionRefreshCoordinator.currentGeneration()

        assertFalse(subject.ensureFresh(generation))

        // 同一轮内不再连打第二次密码：一次拒绝只是"疑似"，等冷却窗外再确认。
        assertEquals(1, calls)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
        assertEquals(SessionRefreshCoordinator.FailureKind.REJECTED,
            SessionRefreshCoordinator.failureKindOf(generation))
        assertFalse(SessionRefreshCoordinator.isRejectionConfirmed(generation))
    }

    @Test
    fun `two rejections confirmed across the cooldown expire the session`() = runTest {
        var clock = 1_000_000L
        var calls = 0
        val subject = sessionWithClock({ clock }) { calls++; rejected }
        val generation = SessionRefreshCoordinator.currentGeneration()

        assertFalse(subject.ensureFresh(generation))
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)

        clock += SessionRefreshCoordinator.REJECTED_RETRY_DELAY_MS + 1
        assertFalse(subject.ensureFresh(generation))

        assertEquals(2, calls)
        assertEquals(AhuSessionState.Status.Expired, subject.state.value)
    }

    @Test
    fun `a rejection healed by the next round keeps the session`() = runTest {
        var clock = 1_000_000L
        var calls = 0
        val subject = sessionWithClock({ clock }) { if (calls++ == 0) rejected else success }
        val generation = SessionRefreshCoordinator.currentGeneration()

        assertFalse(subject.ensureFresh(generation))
        clock += SessionRefreshCoordinator.REJECTED_RETRY_DELAY_MS + 1
        assertTrue(subject.ensureFresh(generation))

        assertEquals(2, calls)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
        assertEquals(generation + 1, SessionRefreshCoordinator.currentGeneration())
    }

    @Test
    fun `an inconclusive network failure after rejection does not require login`() = runTest {
        var clock = 1_000_000L
        var calls = 0
        val subject = sessionWithClock({ clock }) {
            if (calls++ == 0) rejected else AhuResult.Failure(AhuError.Network)
        }
        val generation = SessionRefreshCoordinator.currentGeneration()

        assertFalse(subject.ensureFresh(generation))
        clock += SessionRefreshCoordinator.REJECTED_RETRY_DELAY_MS + 1
        assertFalse(subject.ensureFresh(generation))

        assertEquals(4, calls)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
        assertEquals(SessionRefreshCoordinator.FailureKind.TRANSIENT,
            SessionRefreshCoordinator.failureKindOf(generation))
    }

    @Test
    fun `temporary transport failures retry at most twice with backoff`() = runTest {
        var calls = 0
        val subject = session {
            if (++calls < 3) AhuResult.Failure(AhuError.Timeout) else success
        }
        assertTrue(subject.ensureFresh(SessionRefreshCoordinator.currentGeneration()))
        assertEquals(3, calls)
        assertEquals(2_000L, testScheduler.currentTime)
    }

    @Test
    fun `exhausted retries preserve the account and share the cooldown`() = runTest {
        var calls = 0
        val subject = session { calls++; AhuResult.Failure(AhuError.Network) }
        val generation = SessionRefreshCoordinator.currentGeneration()
        assertFalse(subject.ensureFresh(generation))
        assertFalse(subject.ensureFresh(generation))
        assertEquals(3, calls)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }

    @Test
    fun `all retry attempts share the original total timeout budget`() = runTest {
        var calls = 0
        val subject = session {
            calls++
            delay(20_000L)
            AhuResult.Failure(AhuError.Network)
        }
        assertFalse(subject.ensureFresh(SessionRefreshCoordinator.currentGeneration()))
        assertEquals(2, calls)
        assertEquals(30_000L, testScheduler.currentTime)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }

    @Test
    fun `transport failures between rejections cannot confirm a bad password`() = runTest {
        var clock = 1_000_000L
        var calls = 0
        val subject = sessionWithClock({ clock }) {
            when (calls++) {
                // 第二轮整轮都是传输失败（三次尝试全部 Network），整体判为 TRANSIENT。
                1, 2, 3 -> AhuResult.Failure(AhuError.Network)
                else -> rejected
            }
        }
        val generation = SessionRefreshCoordinator.currentGeneration()

        assertFalse(subject.ensureFresh(generation))
        clock += SessionRefreshCoordinator.REJECTED_RETRY_DELAY_MS + 1
        assertFalse(subject.ensureFresh(generation))
        clock += SessionRefreshCoordinator.TRANSIENT_COOLDOWN_MS + 1
        assertFalse(subject.ensureFresh(generation))

        // 两次拒绝之间夹了一次传输失败，说明不了密码被拒。
        assertFalse(SessionRefreshCoordinator.isRejectionConfirmed(generation))
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }

    @Test
    fun `a confirmed rejection stops automatic recovery until the next sign in`() = runTest {
        var clock = 1_000_000L
        var calls = 0
        val subject = sessionWithClock({ clock }) { calls++; rejected }
        val generation = SessionRefreshCoordinator.currentGeneration()

        assertFalse(subject.ensureFresh(generation))
        clock += SessionRefreshCoordinator.REJECTED_RETRY_DELAY_MS + 1
        assertFalse(subject.ensureFresh(generation))
        assertEquals(AhuSessionState.Status.Expired, subject.state.value)

        // 判死后不再自动尝试续期，避免把校方风控打成死循环。
        clock += SessionRefreshCoordinator.REJECTED_RETRY_DELAY_MS + 1
        assertFalse(subject.ensureFresh(generation))
        assertEquals(2, calls)
    }

    @Test
    fun `human verification and protocol failures do not retry password submission`() = runTest {
        for (result in listOf(
            AhuResult.Success(LoginOutcome.JwxtWebVerificationRequired(user)),
            AhuResult.Failure(AhuError.ProtocolChanged("fixture protocol change")),
            AhuResult.Failure(AhuError.Server(403, "fixture challenge"))
        )) {
            SessionRefreshCoordinator.onAuthenticated()
            var calls = 0
            val subject = session { calls++; result }
            assertFalse(subject.ensureFresh(SessionRefreshCoordinator.currentGeneration()))
            assertEquals(1, calls)
            assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
        }
    }

    @Test
    fun `temporarily unreadable credentials recover within the same bounded attempt`() = runTest {
        var reads = 0
        var logins = 0
        val vault = object : CredentialVault by FakeCredentialVault("fixture-only") {
            override fun wisdomPasswordForRefresh(): String {
                if (++reads < 3) throw SecureStorageUnavailableException(java.io.IOException("fixture unavailable"))
                return "fixture-only"
            }
        }
        val subject = RepositoryAhuSession(
            SessionSignIn { _, _, _ -> logins++; success }, vault,
            FakeSessionAccount(user), FakeSessionResidue()
        )
        assertTrue(subject.ensureFresh(SessionRefreshCoordinator.currentGeneration()))
        assertEquals(3, reads)
        assertEquals(1, logins)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }

    @Test
    fun `concurrent callers during a rejection cooldown fail fast without expiry`() = runTest {
        var calls = 0
        val subject = session { calls++; rejected }
        val generation = SessionRefreshCoordinator.currentGeneration()

        val first = async { subject.ensureFresh(generation) }
        runCurrent()
        val second = async { subject.ensureFresh(generation) }
        runCurrent()

        assertFalse(first.await())
        assertFalse(second.await())
        // 冷却窗内的并发调用者不重复提交密码，也不把一次拒绝升级成过期。
        assertEquals(1, calls)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }

    @Test
    fun `cancelling an old caller cannot fail the replacement while network cleanup is pending`() = runTest {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val subject = session {
            if (++calls == 1) withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
            }
            success
        }
        val generation = SessionRefreshCoordinator.currentGeneration()
        val old = async { subject.ensureFresh(generation) }
        started.await()
        old.cancel()
        val replacement = async { subject.ensureFresh(generation) }
        runCurrent()
        val replacementWaited = !replacement.isCompleted
        release.complete(Unit)
        old.join()
        assertTrue(replacement.await())
        assertTrue(replacementWaited)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }
}
