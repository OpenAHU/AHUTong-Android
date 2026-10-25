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

    @Test
    fun `a single credential rejection is confirmed before publishing expiry`() = runTest {
        var calls = 0
        val subject = session { if (calls++ == 0) rejected else success }
        assertTrue(subject.ensureFresh(SessionRefreshCoordinator.currentGeneration()))
        assertEquals(2, calls)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }

    @Test
    fun `an inconclusive network failure after rejection does not require login`() = runTest {
        var calls = 0
        val subject = session {
            if (calls++ == 0) rejected else AhuResult.Failure(AhuError.Network)
        }
        val generation = SessionRefreshCoordinator.currentGeneration()
        assertFalse(subject.ensureFresh(generation))
        assertEquals(3, calls)
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
        var calls = 0
        val subject = session {
            if (++calls == 2) AhuResult.Failure(AhuError.Network) else rejected
        }
        assertFalse(subject.ensureFresh(SessionRefreshCoordinator.currentGeneration()))
        assertEquals(3, calls)
        assertEquals(AhuSessionState.Status.Authenticated, subject.state.value)
    }

    @Test
    fun `two consecutive credential rejections require login without a third attempt`() = runTest {
        var calls = 0
        val subject = session { calls++; rejected }
        assertFalse(subject.ensureFresh(SessionRefreshCoordinator.currentGeneration()))
        assertEquals(2, calls)
        assertEquals(AhuSessionState.Status.Expired, subject.state.value)
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
    fun `concurrent callers wait for the same confirmation without early expiry`() = runTest {
        var calls = 0
        val confirming = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val subject = session {
            if (++calls == 1) rejected else {
                confirming.complete(Unit)
                release.await()
                success
            }
        }
        val generation = SessionRefreshCoordinator.currentGeneration()
        val first = async { subject.ensureFresh(generation) }
        testScheduler.advanceTimeBy(500)
        runCurrent()
        val second = async { subject.ensureFresh(generation) }
        runCurrent()
        val firstStillPending = !first.isCompleted
        val secondStillPending = !second.isCompleted
        val statusDuringConfirmation = subject.state.value
        release.complete(Unit)
        val firstResult = first.await()
        val secondResult = second.await()
        assertTrue(confirming.isCompleted)
        assertTrue(firstStillPending)
        assertTrue(secondStillPending)
        assertEquals(AhuSessionState.Status.Authenticated, statusDuringConfirmation)
        assertTrue(firstResult)
        assertTrue(secondResult)
        assertEquals(2, calls)
        assertEquals(generation + 1, SessionRefreshCoordinator.currentGeneration())
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
