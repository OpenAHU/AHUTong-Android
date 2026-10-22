package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.security.SecureStorageUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedAccountStoreTest {
    private var encryptedPayload: String? = null
    private fun store() = SavedAccountStore(
        read = { encryptedPayload },
        write = { encryptedPayload = it },
        remove = { encryptedPayload = null }
    )

    @Test
    fun `three accounts survive store recreation and password changes do not duplicate accounts`() {
        val subject = store()
        subject.save(User("甲", "test-a"), "password-a")
        subject.save(User("乙", "test-b"), "password-b")
        subject.save(User("丙", "test-c"), "password-c")
        subject.save(User("甲的新姓名", "test-a"), "updated-a")

        val reopened = store()
        assertEquals(listOf("test-a", "test-c", "test-b"), reopened.accounts().map { it.userId })
        assertEquals("甲的新姓名", reopened.accounts().first().name)
        assertEquals("updated-a", reopened.password("test-a"))
        assertEquals("password-b", reopened.password("test-b"))
        assertEquals("password-c", reopened.password("test-c"))
    }

    @Test
    fun `forgetting one account preserves others and clearing removes all credentials`() {
        val subject = store()
        subject.save(User("甲", "test-a"), "password-a")
        subject.save(User("乙", "test-b"), "password-b")
        subject.forget("test-a")
        assertNull(subject.password("test-a"))
        assertEquals("password-b", subject.password("test-b"))
        subject.clear()
        assertTrue(subject.accounts().isEmpty())
        assertNull(encryptedPayload)
    }

    @Test
    fun `unreadable or malformed records never produce usable credentials`() {
        assertTrue(store().accounts().isEmpty())
        encryptedPayload = "broken-json"
        assertTrue(store().accounts().isEmpty())
        encryptedPayload = "[{}, {\"userId\":\"test-a\",\"password\":\"\"}]"
        assertNull(store().password("test-a"))
    }

    @Test
    fun `failed secure write preserves previous account credentials`() {
        store().save(User("甲", "test-a"), "password-a")
        val failing = SavedAccountStore(
            read = { encryptedPayload },
            write = { error("Keystore unavailable") },
            remove = { encryptedPayload = null }
        )
        assertFailsWith<IllegalStateException> { failing.save(User("乙", "test-b"), "password-b") }
        assertEquals(listOf("test-a"), store().accounts().map { it.userId })
    }

    @Test
    fun `temporary read failure cannot overwrite or forget existing account credentials`() {
        store().save(User("甲", "test-a"), "password-a")
        store().save(User("乙", "test-b"), "password-b")
        val originalPayload = encryptedPayload
        var unavailable = true
        val recovering = SavedAccountStore(
            read = {
                if (unavailable) throw SecureStorageUnavailableException(IllegalStateException("Keystore busy"))
                encryptedPayload
            },
            write = { encryptedPayload = it },
            remove = { encryptedPayload = null }
        )

        // Synchronous UI reads remain safe; mutations must not treat that failure as an empty list.
        assertTrue(recovering.accounts().isEmpty())
        assertNull(recovering.password("test-a"))
        assertFailsWith<SecureStorageUnavailableException> {
            recovering.save(User("丙", "test-c"), "password-c")
        }
        assertFailsWith<SecureStorageUnavailableException> { recovering.forget("test-a") }
        assertEquals(originalPayload, encryptedPayload)

        unavailable = false

        assertEquals(listOf("test-b", "test-a"), recovering.accounts().map { it.userId })
        assertEquals("password-a", recovering.password("test-a"))
        assertEquals("password-b", recovering.password("test-b"))
    }
}
