package com.ahu.ahutong.data.crawler.net

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * ADR 0002 的「有界刷新」：一次续期带总超时，超时即失败——不重试，也不推进代号。
 *
 * 失败记忆分两类（防惊群但不封死自愈）：
 * - 瞬时失败（TRANSIENT）：冷却窗内同代请求快速失败，窗外放行一次新尝试；
 * - 凭据被拒（REJECTED）：更长的冷却窗后允许一次重探，连续两次被拒才封到人工登录。
 *
 * 超时值与冷却窗都走参数，测试不必真等；生产调用点用默认值。
 */
class SessionRefreshTimeoutTest {

    /** 协调器是进程内的单例；每个用例从「刚登录过」的状态开始，免得失败记忆跨用例生效。 */
    @BeforeTest
    fun forgetPreviousFailures() = runBlocking {
        SessionRefreshCoordinator.onAuthenticated()
    }

    @Test
    fun aNewLoginLetsAutomaticRefreshHappenAgain() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        SessionRefreshCoordinator.refreshIfNeeded(generation, timeoutMillis = 5_000L) {
            SessionRefreshCoordinator.RefreshOutcome.REJECTED
        }
        SessionRefreshCoordinator.onAuthenticated()
        val authenticatedGeneration = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0

        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            authenticatedGeneration,
            timeoutMillis = 5_000L
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertTrue(refreshed)
        assertEquals(1, attempts)
    }

    @Test
    fun aResponseFromBeforeManualLoginCannotRefreshTheNewSession() = runBlocking {
        val oldGeneration = SessionRefreshCoordinator.currentGeneration()
        SessionRefreshCoordinator.onAuthenticated()
        var attempts = 0

        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            oldGeneration,
            timeoutMillis = 5_000L
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.REJECTED
        }

        assertTrue(refreshed)
        assertEquals(0, attempts)
        var staleCommitRan = false
        assertFalse(
            SessionRefreshCoordinator.commitIfCurrent(oldGeneration) { staleCommitRan = true }
        )
        assertFalse(staleCommitRan)
    }

    @Test
    fun signingOutDisablesRefreshUntilTheNextAuthentication() = runBlocking {
        val requestBeforeSignOut = SessionRefreshCoordinator.currentGeneration()
        SessionRefreshCoordinator.onSignedOut()
        val signedOutGeneration = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0

        val oldRequestRefreshed = SessionRefreshCoordinator.refreshIfNeeded(requestBeforeSignOut) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        val newRequestRefreshed = SessionRefreshCoordinator.refreshIfNeeded(signedOutGeneration) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertFalse(oldRequestRefreshed)
        assertFalse(newRequestRefreshed)
        assertEquals(0, attempts)
    }

    @Test
    fun aTransientFailureRetriesAfterTheCooldown() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0
        val t0 = 1_000_000L

        val first = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = { t0 }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
        }
        // 冷却窗内：快速失败，不再尝试
        val inCooldown = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = { t0 + 5_000L }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        // 冷却窗外：放行一次新尝试，成功即推进代号
        val afterCooldown = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = { t0 + 16_000L }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertFalse(first)
        assertFalse(inCooldown)
        assertTrue(afterCooldown)
        assertEquals(2, attempts, "窗内不试、窗外只试一次")
        assertEquals(generation + 1, SessionRefreshCoordinator.currentGeneration())
    }

    @Test
    fun aRejectedFailureAllowsOneRetryAfterItsCooldown() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0
        val t0 = 1_000_000L

        val rejected = SessionRefreshCoordinator.refreshIfNeeded(
            generation,
            timeoutMillis = 5_000L,
            rejectedCooldownMillis = 30_000L,
            nowMillis = { t0 }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.REJECTED
        }
        val inCooldown = SessionRefreshCoordinator.refreshIfNeeded(
            generation,
            timeoutMillis = 5_000L,
            rejectedCooldownMillis = 30_000L,
            nowMillis = { t0 + 10_000L }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        val afterCooldown = SessionRefreshCoordinator.refreshIfNeeded(
            generation,
            timeoutMillis = 5_000L,
            rejectedCooldownMillis = 30_000L,
            nowMillis = { t0 + 31_000L }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertFalse(rejected)
        assertFalse(inCooldown, "冷却窗内仍要快速失败")
        assertTrue(afterCooldown, "第一次被拒后允许一次冷却重探")
        assertEquals(2, attempts)
        assertEquals(generation + 1, SessionRefreshCoordinator.currentGeneration())
    }

    @Test
    fun aSecondRejectionIsTerminalUntilManualLogin() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0
        val t0 = 1_000_000L

        SessionRefreshCoordinator.refreshIfNeeded(
            generation,
            timeoutMillis = 5_000L,
            rejectedCooldownMillis = 30_000L,
            nowMillis = { t0 }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.REJECTED
        }
        SessionRefreshCoordinator.refreshIfNeeded(
            generation,
            timeoutMillis = 5_000L,
            rejectedCooldownMillis = 30_000L,
            nowMillis = { t0 + 31_000L }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.REJECTED
        }
        val wayPastCooldown = SessionRefreshCoordinator.refreshIfNeeded(
            generation,
            timeoutMillis = 5_000L,
            rejectedCooldownMillis = 30_000L,
            nowMillis = { t0 + 600_000L }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertFalse(wayPastCooldown)
        assertEquals(2, attempts, "连续两次被拒后只能等人工重登")
    }

    @Test
    fun aConfirmedCredentialRejectionCannotBeRetriedAfterCooldown() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0

        SessionRefreshCoordinator.refreshIfNeeded(generation) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.CREDENTIALS_REJECTED
        }
        val retried = SessionRefreshCoordinator.refreshIfNeeded(
            generation,
            nowMillis = { System.currentTimeMillis() + 600_000L }
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertFalse(retried)
        assertEquals(1, attempts)
    }

    @Test
    fun aTimeoutCountsAsTransientAndCanHealAfterCooldown() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        val t0 = 1_000_000L

        val timedOut = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 50L, transientCooldownMillis = 15_000L, nowMillis = { t0 }
        ) {
            delay(10_000L)
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        assertFalse(timedOut)
        assertEquals(generation, SessionRefreshCoordinator.currentGeneration())

        val healed = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = { t0 + 16_000L }
        ) { SessionRefreshCoordinator.RefreshOutcome.SUCCESS }

        assertTrue(healed, "超时算瞬时失败：冷却窗外必须允许自愈")
    }

    @Test
    fun aSlowFailedRefreshStartsItsCooldownWhenItFinishes() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var now = 1_000_000L
        assertFalse(SessionRefreshCoordinator.refreshIfNeeded(generation, nowMillis = { now }) {
            now += 30_000L
            SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
        })
        now += 1_000L
        assertFalse(SessionRefreshCoordinator.refreshIfNeeded(generation, nowMillis = { now }) {
            error("A queued waiter must observe the full cooldown after the slow refresh")
        })
        now += 15_000L
        assertTrue(SessionRefreshCoordinator.refreshIfNeeded(generation, nowMillis = { now }) {
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        })
    }

    @Test
    fun anAcademicRejectionDoesNotBlockWisdomOrCampusCardRecovery() = runBlocking {
        for (scope in listOf(SessionRefreshCoordinator.Scope.WISDOM, SessionRefreshCoordinator.Scope.CENTRAL_CAS)) {
            SessionRefreshCoordinator.onAuthenticated()
            val generation = SessionRefreshCoordinator.currentGeneration()
            SessionRefreshCoordinator.refreshIfNeeded(generation) {
                SessionRefreshCoordinator.RefreshOutcome.REJECTED
            }
            var attempted = false
            assertTrue(SessionRefreshCoordinator.refreshIfNeeded(generation, scope = scope) {
                attempted = true
                SessionRefreshCoordinator.RefreshOutcome.SUCCESS
            })
            assertTrue(attempted)
        }
    }

    @Test
    fun aWisdomCooldownDoesNotSuppressOtherRecoveryOrForgetItsOwnFailure() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        SessionRefreshCoordinator.refreshIfNeeded(generation, scope = SessionRefreshCoordinator.Scope.WISDOM) {
            SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
        }
        var academicAttempted = false
        SessionRefreshCoordinator.refreshIfNeeded(generation) {
            academicAttempted = true
            SessionRefreshCoordinator.RefreshOutcome.REJECTED
        }
        assertTrue(academicAttempted)
        assertEquals(SessionRefreshCoordinator.FailureKind.TRANSIENT,
            SessionRefreshCoordinator.failureKindOf(generation, SessionRefreshCoordinator.Scope.WISDOM))
        assertFalse(SessionRefreshCoordinator.refreshIfNeeded(generation, scope = SessionRefreshCoordinator.Scope.WISDOM) {
            error("Wisdom must still observe its own cooldown")
        })
    }

    @Test
    fun aHangingAcademicRefreshCannotBlockWisdomQrRecovery() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val academicGeneration = SessionRefreshCoordinator.currentGeneration()
        val wisdomGeneration = SessionRefreshCoordinator.currentGeneration(SessionRefreshCoordinator.Scope.WISDOM)
        val academic = async {
            SessionRefreshCoordinator.refreshIfNeeded(academicGeneration) {
                started.complete(Unit)
                finish.await()
                SessionRefreshCoordinator.RefreshOutcome.SUCCESS
            }
        }
        started.await()
        try {
            assertTrue(withTimeout(1_000) {
                SessionRefreshCoordinator.refreshIfNeeded(wisdomGeneration, scope = SessionRefreshCoordinator.Scope.WISDOM) {
                    SessionRefreshCoordinator.RefreshOutcome.SUCCESS
                }
            })
            assertFalse(academic.isCompleted)
        } finally { finish.complete(Unit) }
        assertTrue(academic.await())
        assertEquals(academicGeneration + 1, SessionRefreshCoordinator.currentGeneration())
        assertEquals(wisdomGeneration + 1,
            SessionRefreshCoordinator.currentGeneration(SessionRefreshCoordinator.Scope.WISDOM))
    }

    @Test
    fun signingOutKeepsTheAccountTransitionBarrierAndInvalidatesOldCommits() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val generation = SessionRefreshCoordinator.currentGeneration()
        val refresh = async {
            SessionRefreshCoordinator.refreshIfNeeded(generation) {
                started.complete(Unit)
                finish.await()
                SessionRefreshCoordinator.RefreshOutcome.SUCCESS
            }
        }
        started.await()
        val signOut = async { SessionRefreshCoordinator.onSignedOut() }
        yield()
        assertFalse(signOut.isCompleted)
        finish.complete(Unit)
        assertTrue(refresh.await())
        withTimeout(1_000) { signOut.await() }
        assertTrue(SessionRefreshCoordinator.isExplicitlySignedOut())
        assertFalse(SessionRefreshCoordinator.commitIfCurrent(generation + 1) { error("stale account commit") })
    }

    @Test
    fun anotherServicesSuccessCannotPretendToHaveRefreshedWisdom() = runBlocking {
        val wisdomGeneration = SessionRefreshCoordinator.currentGeneration(SessionRefreshCoordinator.Scope.WISDOM)
        SessionRefreshCoordinator.refreshIfNeeded(SessionRefreshCoordinator.currentGeneration()) {
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        var calls = 0
        assertTrue(SessionRefreshCoordinator.refreshIfNeeded(wisdomGeneration, scope = SessionRefreshCoordinator.Scope.WISDOM) {
            calls++
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        })
        assertEquals(1, calls)
    }
}
