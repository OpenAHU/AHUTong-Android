package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.security.SecureBoxStore
import com.ahu.ahutong.data.security.SecureReadResult
import com.ahu.ahutong.data.security.SecureStorageUnavailableException
import com.ahu.ahutong.data.security.valueOrNull

/**
 * [CredentialVault] 的生产实现：底层固定为 SecureStorage（AES-GCM + Android Keystore），
 * 旧数据按 SecureBoxStore 的迁移链读取。
 *
 * 实现留在 :app，因为迁移链要用 Rust SDK 与 MMKV——接口因此不必认识设备侧的任何东西。
 */
object SecureCredentialVault : CredentialVault {

    private const val KEY = "password_wisdom"

    override fun saveWisdomPassword(password: String) {
        com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer.rememberPassword(password)
        if (password.isEmpty()) SecureBoxStore.remove(KEY) else SecureBoxStore.put(KEY, password)
    }

    override fun wisdomPassword(): String? =
        readPassword().valueOrNull()

    /**
     * 续期专用读取：读不到密文（Keystore 短暂不可用、旧数据损坏）**不是**密码错误。
     *
     * 不可重试的读取失败以前返回 null，会话层据此判定"凭据被拒"→ 直接掉登录；
     * 现在统一抛出，交给会话层按"存储不可用"重试，绝不升级成登录过期。
     */
    override fun wisdomPasswordForRefresh(): String? =
        when (val stored = readPassword()) {
            is SecureReadResult.Value -> stored.value
            SecureReadResult.Missing -> null
            is SecureReadResult.Unavailable -> throw SecureStorageUnavailableException(stored.cause)
        }

    private fun readPassword(): SecureReadResult =
        SecureBoxStore.readOrMigrate(KEY) { SecureBoxStore.legacyString(KEY) }

    override fun clearWisdomPassword() = SecureBoxStore.remove(KEY)
}

