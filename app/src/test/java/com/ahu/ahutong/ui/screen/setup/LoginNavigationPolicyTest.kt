package com.ahu.ahutong.ui.screen.setup

import com.ahu.ahutong.data.session.AhuSessionState.Status
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginNavigationPolicyTest {
    @Test
    fun persistedLoginCanBeRestoredAfterProcessRestart() {
        assertTrue(canUseAuthenticatedPages(true, Status.Anonymous))
        assertTrue(canCancelLogin("account-a", "account-a", Status.Anonymous, false))
    }

    @Test
    fun missingIdentityOrExpiredSessionCannotOpenBusinessPages() {
        Status.entries.forEach { assertFalse(canUseAuthenticatedPages(false, it)) }
        assertFalse(canUseAuthenticatedPages(true, Status.Expired))
        assertFalse(canCancelLogin("account-a", "account-a", Status.Expired, false))
    }

    @Test
    fun cancelBeforeSubmittingReturnsOnlyToTheOriginalAuthenticatedAccount() {
        assertTrue(canCancelLogin("account-a", "account-a", Status.Authenticated, false))
        assertFalse(canCancelLogin(null, null, Status.Anonymous, false))
        assertFalse(canCancelLogin("account-a", null, Status.Anonymous, false))
        assertFalse(canCancelLogin("account-a", "account-b", Status.Authenticated, false))
    }

    @Test
    fun attemptedLoginCannotReturnToOldAccountEvenIfCleanupFails() {
        assertFalse(canCancelLogin("account-a", "account-a", Status.Anonymous, true))
        assertFalse(canCancelLogin("account-a", "account-a", Status.Authenticated, true))
        assertFalse(canUseAuthenticatedPages(true, Status.Anonymous, authenticationRequired = true))
        assertFalse(canUseAuthenticatedPages(true, Status.Authenticated, authenticationRequired = true))
    }
}
