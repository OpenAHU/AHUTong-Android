package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.model.User
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
}
