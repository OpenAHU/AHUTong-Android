package com.ahu.ahutong.data.security

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.sdk.RustSDK
import com.tencent.mmkv.MMKV

/**
 * 分箱存储的统一读写点。
 *
 * 可信来源只有 [SecureStorage]（AES-GCM + Android Keystore）；读取时依次向 Rust SDK 与旧 MMKV 明文兜底，
 * 并且**边读边迁移**（读到旧值就写回 SecureStorage 并删除旧副本）。规则本身在 [SecureBoxStoreCore]，
 * 这里只负责把三个生产实现装进去——规则与 Android 依赖分开之后，迁移链才第一次有了 JVM 测试。
 *
 * 这套逻辑原先以 private 方法的形式散在 AHUCache 内部，抽出后由 AHUCache、凭据保险箱与会话层共用，
 * 保证迁移链只有一份实现（见 ADR 0003：凭据丢了就要重新登录，不能降级）。
 */
object SecureBoxStore {

    private const val INIT_BOX = "init"

    private val core = SecureBoxStoreCore(
        secure = SecureStorageAdapter,
        nativeKv = RustSdkBoxKv,
        plaintextKv = LegacyMmkv
    )

    /** 用户分箱名：与缓存层共用同一条命名规则，避免多处各自 sanitize。 */
    fun userBox(userId: String?): String {
        val stableUserId = userId?.takeIf { it.isNotEmpty() } ?: "guest"
        return "user_" + stableUserId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }

    fun put(box: String, key: String, value: String) = core.put(box, key, value)

    fun get(box: String, key: String): String? = core.get(box, key)

    fun getOrMigrate(box: String, key: String, fallback: () -> String?): String? =
        core.getOrMigrate(box, key, fallback)

    fun readOrMigrate(box: String, key: String, fallback: () -> String?): SecureReadResult =
        core.readOrMigrate(box, key, fallback)

    fun remove(box: String, key: String) = core.remove(box, key)

    // 初始化箱的便捷重载（历史调用点保持原样）
    fun put(key: String, value: String) = put(INIT_BOX, key, value)

    fun get(key: String): String? = get(INIT_BOX, key)

    fun getOrMigrate(key: String, fallback: () -> String?): String? =
        getOrMigrate(INIT_BOX, key, fallback)

    fun readOrMigrate(key: String, fallback: () -> String?): SecureReadResult =
        readOrMigrate(INIT_BOX, key, fallback)

    fun remove(key: String) = remove(INIT_BOX, key)

    /** 旧 MMKV 明文值，仅用于迁移兜底。 */
    fun legacyString(key: String): String? = LegacyMmkv.get(INIT_BOX, key)

    fun legacyString(box: String, key: String): String? = LegacyMmkv.get(box, key)

    private object SecureStorageAdapter : SecureStringStore {
        override fun getString(key: String): String? = SecureStorage.getString(key)
        override fun readString(key: String): SecureReadResult = SecureStorage.readString(key)
        override fun putString(key: String, value: String) = SecureStorage.putString(key, value)
        override fun remove(key: String) = SecureStorage.remove(key)
    }

    private object RustSdkBoxKv : BoxKeyValueStore {
        override fun get(box: String, key: String): String? = RustSDK.kvGetStringSafe(box, key)
        override fun remove(box: String, key: String) {
            RustSDK.kvRemoveSafe(box, key)
        }
    }

    /**
     * 旧 MMKV 明文库：只读兜底，读到即删。
     * 实例名与分箱名的对应关系：init → ahu，user_x → ahu_x。
     */
    private object LegacyMmkv : BoxKeyValueStore {

        private val initKv: MMKV by lazy {
            MMKV.initialize(AppEnvironmentHolder.context())
            MMKV.mmkvWithID("ahu")
        }

        private fun kvFor(box: String): MMKV =
            if (box == INIT_BOX) initKv
            else MMKV.mmkvWithID("ahu_" + box.removePrefix("user_"))

        override fun get(box: String, key: String): String? = kvFor(box).decodeString(key)

        override fun remove(box: String, key: String) {
            kvFor(box).removeValueForKey(key)
        }
    }
}
