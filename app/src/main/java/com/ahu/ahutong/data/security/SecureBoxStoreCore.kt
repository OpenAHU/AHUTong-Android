package com.ahu.ahutong.data.security

/** 可信来源（AES-GCM + Android Keystore）的读写，生产实现是 [SecureStorage]。 */
internal interface SecureStringStore {
    fun getString(key: String): String?
    fun readString(key: String): SecureReadResult =
        getString(key)?.let { SecureReadResult.Value(it) } ?: SecureReadResult.Missing
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** 旧副本的读写：原生服务的 KV 与迁移期的 MMKV 明文库共用这一个形状。 */
internal interface BoxKeyValueStore {
    fun get(box: String, key: String): String?
    fun remove(box: String, key: String)
}

/**
 * 分箱读写的规则本身：可信来源优先，其次原生服务的 KV，最后旧明文库兜底。
 *
 * 边读边迁移是这里最要紧的不变式：读到旧副本就写回可信来源，并删掉旧副本。
 * 之所以把它从 Android 依赖里剥离出来，是因为这条规则以前只存在于真机路径上，
 * 从来没有测试——而迁移完成若只是幻觉，磁盘上就会同时留着密文与明文（ADR 0003）。
 */
internal class SecureBoxStoreCore(
    private val secure: SecureStringStore,
    private val nativeKv: BoxKeyValueStore,
    private val plaintextKv: BoxKeyValueStore
) {

    fun put(box: String, key: String, value: String) {
        secure.putString(secureKey(box, key), value)
        nativeKv.remove(box, key)
        plaintextKv.remove(box, key)
    }

    fun get(box: String, key: String): String? = read(box, key).valueOrNull()

    fun read(box: String, key: String): SecureReadResult = try {
        when (val stored = secure.readString(secureKey(box, key))) {
            is SecureReadResult.Unavailable -> stored
            is SecureReadResult.Value -> {
                // 上一次迁移可能在“写入密文”后、删除旧副本前被中断；命中可信副本时继续收尾。
                nativeKv.remove(box, key)
                plaintextKv.remove(box, key)
                stored
            }
            SecureReadResult.Missing -> {
                nativeKv.get(box, key)?.let { value ->
                    secure.putString(secureKey(box, key), value)
                    nativeKv.remove(box, key)
                    plaintextKv.remove(box, key)
                    SecureReadResult.Value(value)
                } ?: SecureReadResult.Missing
            }
        }
    } catch (error: Exception) {
        SecureReadResult.Unavailable(error)
    }

    fun getOrMigrate(box: String, key: String, fallback: () -> String?): String? =
        readOrMigrate(box, key, fallback).valueOrNull()

    fun readOrMigrate(box: String, key: String, fallback: () -> String?): SecureReadResult = try {
        when (val stored = read(box, key)) {
            is SecureReadResult.Unavailable, is SecureReadResult.Value -> stored
            SecureReadResult.Missing -> fallback()?.let { value ->
                // 空值不写回可信来源（写了等于凭空造一条已知为空的记录），但明文副本照样要删。
                if (value.isNotEmpty()) put(box, key, value)
                plaintextKv.remove(box, key)
                SecureReadResult.Value(value)
            } ?: SecureReadResult.Missing
        }
    } catch (error: Exception) {
        SecureReadResult.Unavailable(error)
    }

    fun remove(box: String, key: String) {
        secure.remove(secureKey(box, key))
        nativeKv.remove(box, key)
        plaintextKv.remove(box, key)
    }

    private fun secureKey(box: String, key: String) = box + "." + key
}
