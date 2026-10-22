package com.ahu.ahutong.data.mail

import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortalSessionStampTest {
    @Before fun reset() = runBlocking { SessionRefreshCoordinator.onAuthenticated() }

    @Test fun `academic refresh does not invalidate a mailbox portal request`() = runBlocking {
        val stamp = PortalSessionStamp()
        SessionRefreshCoordinator.refreshIfNeeded(SessionRefreshCoordinator.currentGeneration()) {
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        assertTrue(stamp.isCurrent())
        var calls = 0
        assertTrue(stamp.refresh { calls++; SessionRefreshCoordinator.RefreshOutcome.SUCCESS })
        assertEquals(1, calls)
        assertTrue(stamp.isCurrent())
    }

    @Test fun `successful CAS recovery can commit and continue the portal request`() = runBlocking {
        val stamp = PortalSessionStamp()
        assertTrue(stamp.refresh { SessionRefreshCoordinator.RefreshOutcome.SUCCESS })
        assertTrue(stamp.isCurrent())
        var committed = false
        stamp.commit { committed = true }
        assertTrue(committed)
    }

    @Test fun `a campus card CAS recovery is reused without a second password login`() = runBlocking {
        val stamp = PortalSessionStamp()
        val scope = SessionRefreshCoordinator.Scope.CENTRAL_CAS
        SessionRefreshCoordinator.refreshIfNeeded(SessionRefreshCoordinator.currentGeneration(scope), scope = scope) {
            SessionRefreshCoordinator.RefreshOutcome.SUCCESS
        }
        assertTrue(stamp.refresh { error("must reuse the existing CAS refresh") })
        assertTrue(stamp.isCurrent())
    }

    @Test fun `account transitions invalidate portal stamps and prevent state commits`() = runBlocking {
        val stamp = PortalSessionStamp()
        SessionRefreshCoordinator.onSignedOut()
        assertFalse(stamp.isCurrent())
        assertFalse(stamp.refresh { error("signed out accounts must not refresh") })
        var committed = false
        stamp.commit { committed = true }
        assertFalse(committed)
    }
}
