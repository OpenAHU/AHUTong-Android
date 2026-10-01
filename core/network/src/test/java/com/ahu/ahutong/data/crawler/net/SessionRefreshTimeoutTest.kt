package com.ahu.ahutong.data.crawler.net

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * ADR 0002 的「有界刷新」：一次续期带总超时，超时即失败——不重试，也不推进代号。
 *
 * 失败记忆分两类（防惊群但不封死自愈）：
 * - 瞬时失败（TRANSIENT）：冷却窗内同代请求快速失败，窗外放行一次新尝试；
 * - 凭据被拒（REJECTED）：封禁到下一次真实登录。
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
    fun aFailedRefreshIsNotRetriedByEveryWaiter() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0

        val first = SessionRefreshCoordinator.refreshIfNeeded(generation, timeoutMillis = 5_000L) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.REJECTED
        }
        val second = SessionRefreshCoordinator.refreshIfNeeded(generation, timeoutMillis = 5_000L) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertFalse(first)
        assertFalse(second)
        assertEquals(1, attempts, "一次会话失效只许自动续期一次（ADR 0002）")
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
    fun refreshThatNeverReturnsIsCutOffAndCountedAsFailed() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()

        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            observedGeneration = generation,
            timeoutMillis = 50L
        ) {
            delay(10_000L)
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertFalse(refreshed)
        assertEquals(generation, SessionRefreshCoordinator.currentGeneration())
    }

    @Test
    fun refreshInsideTheBudgetStillReportsSuccess() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()

        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            observedGeneration = generation,
            timeoutMillis = 5_000L
        ) { SessionRefreshCoordinator.RefreshOutcome.SUCCESS }

        assertTrue(refreshed)
        assertEquals(generation + 1, SessionRefreshCoordinator.currentGeneration())
    }

    @Test
    fun aTransientFailureRetriesAfterTheCooldown() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0
        val t0 = 1_000_000L

        val first = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = t0
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.TRANSIENT
        }
        // 冷却窗内：快速失败，不再尝试
        val inCooldown = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = t0 + 5_000L
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        // 冷却窗外：放行一次新尝试，成功即推进代号
        val afterCooldown = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = t0 + 16_000L
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
    fun aRejectedFailureStaysBlockedBeyondTheCooldown() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0
        val t0 = 1_000_000L

        SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = t0
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.REJECTED
        }
        val wayPastCooldown = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = t0 + 600_000L
        ) {
            attempts += 1
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }

        assertFalse(wayPastCooldown)
        assertEquals(1, attempts, "凭据被拒只能等人工重登，冷却窗不适用")
    }

    @Test
    fun aTimeoutCountsAsTransientAndCanHealAfterCooldown() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        val t0 = 1_000_000L

        val timedOut = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 50L, transientCooldownMillis = 15_000L, nowMillis = t0
        ) {
            delay(10_000L)
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        val healed = SessionRefreshCoordinator.refreshIfNeeded(
            generation, timeoutMillis = 5_000L, transientCooldownMillis = 15_000L, nowMillis = t0 + 16_000L
        ) { SessionRefreshCoordinator.RefreshOutcome.SUCCESS }

        assertFalse(timedOut)
        assertTrue(healed, "超时算瞬时失败：冷却窗外必须允许自愈")
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
}
