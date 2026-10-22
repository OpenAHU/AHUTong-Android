package com.ahu.ahutong.data.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 迁移链的契约（P2.3 明确要求的一条："迁移完成即清除明文"）。
 *
 * 三档存储里最危险的不变式是：旧副本只允许往可信来源方向迁移，
 * 读到之后必须立刻删除——否则"迁移完成"只是幻觉，磁盘上同时留着密文与明文。
 * 这条规则以前以私有方法的形式散在 AHUCache 里，只有真机才能验证，因此从来没有测试。
 */
class SecureBoxStoreCoreTest {

    private val secure = FakeSecureStore()
    private val native = FakeBoxStore()
    private val plaintext = FakeBoxStore()
    private val core = SecureBoxStoreCore(secure, native, plaintext)

    @Test
    fun `the secure copy wins and finishes deleting interrupted migration copies`() {
        secure.values["user_1.token"] = "secure"
        native.put("user_1", "token", "stale-native")
        plaintext.put("user_1", "token", "stale-plaintext")

        assertEquals("secure", core.get("user_1", "token"))
        assertTrue(native.reads.isEmpty())
        assertTrue(native.values.isEmpty())
        assertTrue(plaintext.values.isEmpty())
    }

    @Test
    fun `unavailable encrypted data does not fall back and recovers on the next read`() {
        secure.values["init.current_user"] = "trusted-user"
        native.put("init", "current_user", "stale-native-user")
        plaintext.put("init", "current_user", "stale-plaintext-user")
        val failure = SecureReadResult.Unavailable(IllegalStateException("Keystore unavailable"))
        secure.readFailure = failure

        assertEquals(failure, core.readOrMigrate("init", "current_user") { error("must not read plaintext") })
        assertNull(core.getOrMigrate("init", "current_user") { error("must not read plaintext") })
        assertEquals("trusted-user", secure.values["init.current_user"])
        assertEquals("stale-native-user", native.values["init/current_user"])
        assertEquals("stale-plaintext-user", plaintext.values["init/current_user"])
        assertTrue(native.reads.isEmpty())
        assertTrue(plaintext.reads.isEmpty())

        secure.readFailure = null

        assertEquals("trusted-user", core.getOrMigrate("init", "current_user") { error("must not read plaintext") })
        assertTrue(native.values.isEmpty())
        assertTrue(plaintext.values.isEmpty())
    }

    @Test
    fun `permanently unreadable encrypted data remains fail closed`() {
        secure.values["init.current_user"] = "unreadable"
        native.put("init", "current_user", "stale-user")
        val failure = SecureReadResult.Unavailable(IllegalArgumentException("corrupt ciphertext"), retryable = false)
        secure.readFailure = failure

        assertEquals(failure, core.readOrMigrate("init", "current_user") { error("must not use legacy identity") })
        assertEquals("unreadable", secure.values["init.current_user"])
        assertTrue(native.reads.isEmpty())
    }

    @Test
    fun `a native copy is promoted to the secure store and then removed`() {
        native.put("user_1", "token", "from-native")
        plaintext.put("user_1", "token", "stale-plaintext")

        assertEquals("from-native", core.get("user_1", "token"))
        assertEquals("from-native", secure.values["user_1.token"])
        assertTrue(native.values.isEmpty())
        assertTrue(plaintext.values.isEmpty())
    }

    @Test
    fun `a plaintext copy is migrated and the plaintext is deleted`() {
        plaintext.put("user_1", "token", "from-plaintext")

        val migrated = core.getOrMigrate("user_1", "token") { plaintext.get("user_1", "token") }

        assertEquals("from-plaintext", migrated)
        assertEquals("from-plaintext", secure.values["user_1.token"])
        assertTrue(plaintext.values.isEmpty())
        // 第二次读命中可信来源，明文库不该再被访问。
        assertEquals("from-plaintext", core.getOrMigrate("user_1", "token") { error("不该再读明文库") })
    }

    @Test
    fun `an empty legacy value is not promoted but its plaintext copy still goes away`() {
        plaintext.put("user_1", "preset", "")

        val value = core.getOrMigrate("user_1", "preset") { plaintext.get("user_1", "preset") }

        assertEquals("", value)
        assertTrue(secure.values.isEmpty())
        assertTrue(plaintext.values.isEmpty())
    }

    @Test
    fun `a missing legacy value leaves every store untouched`() {
        assertNull(core.getOrMigrate("user_1", "nothing") { plaintext.get("user_1", "nothing") })

        assertTrue(secure.values.isEmpty())
        assertTrue(plaintext.values.isEmpty())
    }

    @Test
    fun `writing a value never leaves older copies behind`() {
        native.put("user_1", "token", "stale-native")
        plaintext.put("user_1", "token", "stale-plaintext")

        core.put("user_1", "token", "fresh")

        assertEquals("fresh", secure.values["user_1.token"])
        assertTrue(native.values.isEmpty())
        assertTrue(plaintext.values.isEmpty())
    }

    @Test
    fun `removing a value clears all three stores`() {
        core.put("user_1", "token", "x")
        native.put("user_1", "token", "x")
        plaintext.put("user_1", "token", "x")

        core.remove("user_1", "token")

        assertTrue(secure.values.isEmpty())
        assertTrue(native.values.isEmpty())
        assertTrue(plaintext.values.isEmpty())
    }

    @Test
    fun `the box name is one rule shared by the cache and the security layer`() {
        assertEquals("user_20210001", SecureBoxStore.userBox("20210001"))
        assertEquals("user_guest", SecureBoxStore.userBox(null))
        assertEquals("user_guest", SecureBoxStore.userBox(""))
        assertEquals("user_a_b", SecureBoxStore.userBox("a/b"))
    }

    private class FakeSecureStore : SecureStringStore {
        val values = linkedMapOf<String, String>()
        var readFailure: SecureReadResult.Unavailable? = null
        override fun getString(key: String): String? = values[key]
        override fun readString(key: String): SecureReadResult =
            readFailure ?: super.readString(key)
        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private class FakeBoxStore : BoxKeyValueStore {
        val values = linkedMapOf<String, String>()
        val reads = mutableListOf<String>()

        override fun get(box: String, key: String): String? {
            reads.add(box + "/" + key)
            return values[box + "/" + key]
        }

        override fun remove(box: String, key: String) {
            values.remove(box + "/" + key)
        }

        fun put(box: String, key: String, value: String) {
            values[box + "/" + key] = value
        }
    }
}
