package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.security.SecureReadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrentUserRestoreTest {
    @Test
    fun `a transient read failure is not cached as an absent account`() {
        val user = User("测试", "test-user")
        var stored: SecureReadResult = SecureReadResult.Unavailable(IllegalStateException("Keystore busy"))
        var reads = 0
        val restore = CurrentUserRestore(read = { reads++; stored }, decode = { user })

        assertTrue(restore.restore() is CurrentUserRestoreResult.Unavailable)
        assertTrue(restore.unavailable)
        stored = SecureReadResult.Value("valid-user-json")

        assertEquals(user, (restore.restore() as CurrentUserRestoreResult.Ready).user)
        assertFalse(restore.unavailable)
        assertEquals(user, (restore.restore() as CurrentUserRestoreResult.Ready).user)
        assertEquals(2, reads)
    }

    @Test
    fun `a genuinely missing account is cached and explicit login replaces it`() {
        var reads = 0
        val restore = CurrentUserRestore(read = { reads++; SecureReadResult.Missing }, decode = { error("absent") })

        assertNull((restore.restore() as CurrentUserRestoreResult.Ready).user)
        assertNull((restore.restore() as CurrentUserRestoreResult.Ready).user)
        assertEquals(1, reads)

        val user = User("测试", "test-user")
        restore.update(user)
        assertEquals(user, (restore.restore() as CurrentUserRestoreResult.Ready).user)
        restore.update(null)
        assertNull((restore.restore() as CurrentUserRestoreResult.Ready).user)
        assertEquals(1, reads)
    }

    @Test
    fun `corrupt user JSON cannot authenticate or become a cached missing account`() {
        var stored: SecureReadResult = SecureReadResult.Value("invalid-user-json")
        val user = User("测试", "test-user")
        val restore = CurrentUserRestore(read = { stored }, decode = { if (it == "valid-user-json") user else null })

        val failure = restore.restore() as CurrentUserRestoreResult.Unavailable
        assertFalse(failure.retryable)
        assertTrue(restore.unavailable)

        stored = SecureReadResult.Value("valid-user-json")
        assertEquals(user, (restore.restore() as CurrentUserRestoreResult.Ready).user)
    }
}
