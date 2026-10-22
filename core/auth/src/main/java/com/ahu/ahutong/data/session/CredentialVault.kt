package com.ahu.ahutong.data.session

/**
 * 凭据读写的唯一入口（ADR 0002 / 0003）。
 *
 * 密码属于「丢了就要重新登录」的一类数据：读取失败只能当作没有，绝不能降级成明文或缓存命中。
 * 实现是 :app 的 SecureCredentialVault（AES-GCM + Android Keystore，旧数据走 SecureBoxStore 的迁移链）。
 */
interface CredentialVault {

    fun saveWisdomPassword(password: String)

    fun wisdomPassword(): String?

    /** Automatic refresh must distinguish unavailable storage from missing credentials. */
    fun wisdomPasswordForRefresh(): String? = wisdomPassword()

    fun clearWisdomPassword()
}

